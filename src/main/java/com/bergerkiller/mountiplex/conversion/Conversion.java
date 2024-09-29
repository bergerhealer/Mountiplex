package com.bergerkiller.mountiplex.conversion;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.conversion.annotations.ConverterMethod;
import com.bergerkiller.mountiplex.conversion.annotations.ProviderMethod;
import com.bergerkiller.mountiplex.conversion.builtin.ArrayConversion;
import com.bergerkiller.mountiplex.conversion.builtin.BooleanConversion;
import com.bergerkiller.mountiplex.conversion.builtin.CollectionConversion;
import com.bergerkiller.mountiplex.conversion.builtin.EnumConversion;
import com.bergerkiller.mountiplex.conversion.builtin.MapConversion;
import com.bergerkiller.mountiplex.conversion.builtin.NumberConversion;
import com.bergerkiller.mountiplex.conversion.builtin.StreamConversion;
import com.bergerkiller.mountiplex.conversion.builtin.ToStringConversion;
import com.bergerkiller.mountiplex.conversion.builtin.VoidTypeConverter;
import com.bergerkiller.mountiplex.conversion.type.AnnotatedConverter;
import com.bergerkiller.mountiplex.conversion.type.AnnotatedProvider;
import com.bergerkiller.mountiplex.conversion.type.CastingConverter;
import com.bergerkiller.mountiplex.conversion.type.ChainConverter;
import com.bergerkiller.mountiplex.conversion.type.DuplexConverter;
import com.bergerkiller.mountiplex.conversion.type.InputConverter;
import com.bergerkiller.mountiplex.conversion.type.NullConverter;
import com.bergerkiller.mountiplex.reflection.declarations.ClassResolver;
import com.bergerkiller.mountiplex.reflection.declarations.FieldDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.MethodDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.Template;
import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;
import com.bergerkiller.mountiplex.reflection.resolver.Resolver;
import com.bergerkiller.mountiplex.reflection.util.BoxedType;
import com.bergerkiller.mountiplex.reflection.util.InputTypeMap;

public class Conversion {
    private static final DeferLock deferLock = new DeferLock();
    private static final Map<TypeTuple, Converter<Object, Object>> converters = new HashMap<TypeTuple, Converter<Object, Object>>();
    private static final ArrayList<ConverterProvider> providers = new ArrayList<ConverterProvider>();

    static {
        // These null converters ensure double <> Double works correctly in the conversion tree
        for (Class<?> unboxedType : BoxedType.getUnboxedTypes()) {
            Class<?> boxedType = BoxedType.getBoxedType(unboxedType);
            registerConverter(new NullConverter(unboxedType, boxedType));
            registerConverter(new NullConverter(boxedType, unboxedType));
        }

        // Register other builtin converters
        NumberConversion.register();
        ToStringConversion.register();
        EnumConversion.register();
        CollectionConversion.register();
        MapConversion.register();
        ArrayConversion.register();
        VoidTypeConverter.register();
        BooleanConversion.register();
        StreamConversion.register();

        // We can not remove registered converters and providers for safety reasons
        // We will clear all generated data, though
        MountiplexUtil.registerUnloader(new Runnable() {
            @Override
            public void run() {
                OutputConverterTree.trees = new HashMap<TypeDeclaration, OutputConverterTree>(0);
                OutputConverterList.mapping = new HashMap<TypeDeclaration, OutputConverterList>(0);
            }
        });
    }

    /**
     * Registers a new converter that can convert from one input type to one output type.
     * This function will replace existing converters registered to convert between the same input/output.
     * 
     * @param converter to register
     */
    public static void registerConverter(Converter<?, ?> converter) {
        try {
            verifyConverter(converter);
            registerConverterImpl(converter);
            if (converter instanceof DuplexConverter) {
                registerConverterImpl(((DuplexConverter<?, ?>) converter).reverse());
            }
        } catch (InvalidConverterException ex) {
            MountiplexUtil.LOGGER.warning(ex.getMessage() + ": " + converter);
        } catch (Throwable t) {
            MountiplexUtil.LOGGER.log(Level.SEVERE, "An error occurred registering " + converter, t);
        }
    }

    /**
     * Registers a new converter provider that can dynamically provide converters for multiple
     * output types.
     * 
     * @param provider to register
     */
    public static void registerProvider(ConverterProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("Provider is null");
        }

        deferLock.schedule(() -> {
            providers.add(provider);
            OutputConverterList.resetAll();
            OutputConverterTree.resetAll();
            converters.clear();
        });
    }

    /**
     * Registers all annotated converter methods and converter constants,
     * declared statically in a Class
     * 
     * @param converterListClass containing the annotated static methods
     */
    public static void registerConverters(Class<?> converterListClass) {
        for (Method method : converterListClass.getDeclaredMethods()) {
            if (method.getAnnotation(ConverterMethod.class) != null) {
                try {
                    TypeDeclaration input = AnnotatedConverter.parseType(method, true);
                    TypeDeclaration output = AnnotatedConverter.parseType(method, false);
                    if (input == null || output == null) {
                        continue; // ignore optional types that can not be resolved
                    }
                    MethodDeclaration methodDec = new MethodDeclaration(ClassResolver.DEFAULT, method);
                    if (input.hasTypeVariables() || output.hasTypeVariables()) {
                        registerProvider(new AnnotatedConverter.GenericProvider(methodDec, input, output));
                    } else {
                        registerConverter(new AnnotatedConverter(methodDec, input, output));
                    }
                } catch (Throwable t) {
                    MethodDeclaration m = new MethodDeclaration(ClassResolver.DEFAULT, method);
                    MountiplexUtil.LOGGER.log(Level.SEVERE, "Failed to register static converter method " + m.toString(), t);
                }
            }
            if (method.getAnnotation(ProviderMethod.class) != null) {
                try {
                    registerProvider(new AnnotatedProvider(method));
                } catch (Throwable t) {
                    MethodDeclaration m = new MethodDeclaration(ClassResolver.DEFAULT, method);
                    MountiplexUtil.LOGGER.log(Level.SEVERE, "Failed to register static provider method " + m.toString(), t);
                }
            }
        }
        for (Field field : converterListClass.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (Converter.class.isAssignableFrom(field.getType())) {
                Converter<?, ?> converter = null;
                try {
                    field.setAccessible(true);
                    converter = (Converter<?, ?>) field.get(null);
                } catch (Throwable t) {
                    FieldDeclaration f = new FieldDeclaration(ClassResolver.DEFAULT, field);
                    MountiplexUtil.LOGGER.log(Level.WARNING, "Failed to register static converter field " + f.toString(), t);
                    continue;
                }
                registerConverter(converter);
            }
            if (ConverterProvider.class.isAssignableFrom(field.getType())) {
                ConverterProvider provider = null;
                try {
                    field.setAccessible(true);
                    provider = (ConverterProvider) field.get(null);
                } catch (Throwable t) {
                    FieldDeclaration f = new FieldDeclaration(ClassResolver.DEFAULT, field);
                    MountiplexUtil.LOGGER.log(Level.WARNING, "Failed to register static provider field " + f.toString(), t);
                    continue;
                }
                registerProvider(provider);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> InputConverter<T> find(Class<T> output) {
        return (InputConverter<T>) find(TypeDeclaration.fromClass(output));
    }

    @SuppressWarnings("unchecked")
    public static <I, O> Converter<I, O> find(Class<I> input, Class<O> output) {
        return (Converter<I, O>) find(TypeDeclaration.fromClass(input), TypeDeclaration.fromClass(output));
    }

    public static InputConverter<?> find(TypeDeclaration output) {
        deferLock.lock();
        try {
            return OutputConverterTree.get(output).converter;
        } finally {
            deferLock.unlock();
        }
    }

    /**
     * Looks up the Converter from the input type to output type
     * 
     * @param input Input type declaration
     * @param output Output type declaration
     * @return Converter from input to output, or null if it is not found
     */
    public static Converter<Object, Object> find(TypeDeclaration input, TypeDeclaration output) {
        TypeTuple key;
        try {
            key = new TypeTuple(input, output);
        } catch (RuntimeException ex) {
            if (input == null) {
                throw new IllegalArgumentException("Input type is null");
            }
            if (output == null) {
                throw new IllegalArgumentException("Output type is null");
            }
            throw ex;
        }

        // Try to look it up first, which most commonly will succeed
        OutputConverterTree outputTree = null;
        boolean inputTypeIsInMapping = false;

        deferLock.lock();
        try {
            Converter<Object, Object> result = converters.get(key);
            if (result != null) {
                return result;
            }

            // Check if the input type can be assigned to the output type
            // In that case, simply return a null converter
            // Otherwise use the conversion tree to find it, later
            if (input.isInstanceOf(output)) {
                result = new NullConverter(input, output);
                converters.put(key, result);
                return result;
            }

            // Since we're already locked, look up an existing output node in the tree
            // This helps us see whether or not to initialize the type
            outputTree = OutputConverterTree.getIfExists(output);
            if (outputTree != null) {
                inputTypeIsInMapping = outputTree.isInMapping(input);
            }

        } finally {
            deferLock.unlock();
        }

        // Before doing a converter tree search operation, ensure the input AND output type
        // have been initialized. This must be done outside of a lock, as this could cause
        // converters to be registered. This happening at the same time as the class loader
        // being locked could result in a deadlock.
        if (outputTree == null) {
            initType(output);
        }
        if (!inputTypeIsInMapping) {
            initType(input);
        }

        deferLock.lock();
        try {
            // Converter may have been registered between the lock-unlocks, so check again
            Converter<Object, Object> result = converters.get(key);
            if (result != null) {
                return result;
            }

            // Find or create output converter tree for the output for the first time
            if (outputTree == null) {
                outputTree = OutputConverterTree.get(output);
            }

            // Use the conversion tree to find it
            result = outputTree.find(input);

            // If no converter was found, attempt to cast to the type using an upcast if possible
            if (result == null && output.isInstanceOf(input)) {
                result = new CastingConverter<Object>(input, output);
            }

            // Store in the map for quick future lookup
            // Special case for duplex converters; they can be stored twice!
            converters.put(key, result);
            if (result instanceof DuplexConverter) {
                converters.put(key.reverse(), ((DuplexConverter<Object, Object>) result).reverse());
            }

            return result;
        } finally {
            deferLock.unlock();
        }
    }

    /**
     * Creates a duplex converter between two types, allowing both input -> output and output -> input conversion
     * 
     * @param inputType
     * @param outputType
     * @return duplex converter
     */
    public static <I, O> DuplexConverter<I, O> findDuplex(Class<I> inputType, Class<O> outputType) {
        return DuplexConverter.pair(find(inputType, outputType), find(outputType, inputType));
    }

    /**
     * Creates a duplex converter between two types, allowing both input -> output and output -> input conversion
     * 
     * @param input type
     * @param output type
     * @return duplex converter
     */
    public static DuplexConverter<Object, Object> findDuplex(TypeDeclaration input, TypeDeclaration output) {
        return DuplexConverter.pair(find(input, output), find(output, input));
    }

    /**
     * Prints the input&lt;&gt;output conversion tree to a given output Class type
     * 
     * @param output Class type to debug print
     */
    public static void debugTree(Class<?> input, Class<?> output) {
        debugTree(TypeDeclaration.fromClass(input), TypeDeclaration.fromClass(output));
    }

    /**
     * Prints the input&lt;&gt;output conversion tree to a given output type
     * 
     * @param output type to debug print
     */
    public static void debugTree(TypeDeclaration input, TypeDeclaration output) {
        OutputConverterTree tree = OutputConverterTree.get(output);
        StringBuilder str = new StringBuilder();
        String header = "====== Converting " + input + " -> " + output + " ======";
        str.append(header).append('\n');
        tree.root.debugPrint(tree.mapping.get(input), str, 2);
        for (int i = 0; i < header.length(); i++) {
            str.append('=');
        }
        str.append('\n');
        System.out.println(str);
    }

    /**
     * Writes a very detailed listing of all converter combinations that have been used so far.
     * This enables fine-tuned checking of the conversion paths taken to make sure there are no
     * crazy inefficiencies being used. Converter cost can be adjusted to control this path.
     * 
     * @param filePath of the log file to write the results to
     */
    public static void debugExportConverterTree(String filePath) {
        try {
            OutputStreamWriter logFile = new OutputStreamWriter(new FileOutputStream(filePath));
            try {
                deferLock.lock();
                try {
                    for (Converter<Object, Object> converter : converters.values()) {
                        logFile.write(converter.toString());
                        logFile.write("\r\n\r\n");
                    }
                } finally {
                    deferLock.unlock();
                }
            } finally {
                logFile.close();
            }
        } catch (Throwable t) {
            MountiplexUtil.LOGGER.log(Level.SEVERE, "[Debug] Failed to export converter tree", t);
        }
    }

    // registers a converter (implementation, called from elsewhere)
    private static void registerConverterImpl(Converter<?, ?> converter) {
        deferLock.schedule(() -> {
            addConverterToMapping(converter);

            // When registering a raw type output when in actuality it has a generic type,
            // register the same converter for all the unset rawtypes. For example,
            // when converter is 'String -> List', it also registers 'String -> List<?>'
            if (converter.output.genericTypes.length == 0) {
                int num_typeVariables = converter.output.type.getTypeParameters().length;
                if (num_typeVariables > 0) {
                    TypeDeclaration[] genericTypes = new TypeDeclaration[num_typeVariables];
                    Arrays.fill(genericTypes, TypeDeclaration.ANY);
                    TypeDeclaration any_output = converter.output.setGenericTypes(genericTypes);
                    addConverterToMapping(new ChainConverter<Object, Object>(
                            Arrays.asList(converter,
                            new NullConverter(converter.output, any_output))));
                }
            }
        });
    }

    // Resets cached mappings for an input and output, then adds a new converter for doing the conversion
    // Note: not possible to specify input/output types, they must match what the converter says
    @SuppressWarnings("unchecked")
    private static void addConverterToMapping(Converter<?, ?> converter) {
        OutputConverterList.get(converter.output).addConverter(converter);
        OutputConverterTree.resetTypeChange(converter.input);
        OutputConverterTree.resetTypeChange(converter.output);
        converters.put(new TypeTuple(converter), (Converter<Object, Object>) converter);
    }

    private static void initType(TypeDeclaration type) {
        if (type.type != null && Template.Handle.class.isAssignableFrom(type.type)) {
            Resolver.initializeClass(type.type);

            // Class Initialization may have registered new converters
            // This stuff will get processed right away if WE loaded the class,
            // however another thread could also have done so and we were merely
            // sync-waiting for it. If that case happens, it's a good idea to
            // process pending registrations right now ourselves, as we own the lock.
            deferLock.processPending();
        }
    }

    // verifies the converter input and output are properly defined
    private static void verifyConverter(Converter<?, ?> converter) throws InvalidConverterException {
        if (converter == null) {
            throw new InvalidConverterException("Converter is null");
        }
        if (!converter.input.isValid()) {
            throw new InvalidConverterException("Converter has invalid input");
        }
        if (!converter.output.isValid()) {
            throw new InvalidConverterException("Converter has invalid output");
        }
        if (!converter.input.isResolved()) {
            throw new InvalidConverterException("Converter has unresolved input");
        }
        if (!converter.output.isResolved()) {
            throw new InvalidConverterException("Converter has unresolved output");
        }
    }

    private static class InvalidConverterException extends IllegalStateException {
        private static final long serialVersionUID = 2555039929011231357L;

        public InvalidConverterException(String message) {
            super(message);
        }
    }

    // maintains the converter tree from all input types that can be converted to the output type
    // this tree is created as large as is needed to find the input type requested
    private static final class OutputConverterTree {
        private static HashMap<TypeDeclaration, OutputConverterTree> trees = new HashMap<TypeDeclaration, OutputConverterTree>();
        private final Node root;
        private final InputTypeMap<Node> mapping = new InputTypeMap<Node>();
        private final ArrayList<Node> lastNodes = new ArrayList<Node>();
        private final ArrayList<Node> nextNodes = new ArrayList<Node>();
        private Converter<?, Object> nullConverter = null;
        private boolean nullConverterSearched = false;
        private boolean isReset = false;
        public final InputConverter<Object> converter;
        private int stepStuckCounter = 0;

        public OutputConverterTree(TypeDeclaration output) {
            this.root = new Node(null, new NullConverter(output, output));
            this.reset();
            this.converter = new InputConverter<Object>(output) {
                @Override
                public Converter<Object, Object> getConverter(TypeDeclaration input) {
                    deferLock.lock();
                    try {
                        return (Converter<Object, Object>) find(input);
                    } finally {
                        deferLock.unlock();
                    }
                }

                @Override
                @SuppressWarnings("unchecked")
                public Converter<?, Object> getNullConverter() {
                    deferLock.lock();
                    try {
                        if (!nullConverterSearched) {
                            nullConverterSearched = true;
                            nullConverter = null;
                            for (Converter<?, ?> converter : OutputConverterList.get(output).getConverters()) {
                                if (converter.acceptsNullInput()) {
                                    nullConverter = (Converter<?, Object>) converter;
                                    break;
                                }
                            }
                        }
                        return nullConverter;
                    } finally {
                        deferLock.unlock();
                    }
                }
            };
        }

        public final boolean isInMapping(TypeDeclaration input) {
            if (input.type == null) {
                throw new IllegalArgumentException("Unresolved input type: " + input.toString());
            }

            // 'Object' inputs can have any real object assigned to it
            if (input.type.equals(Object.class)) {
                return true;
            }

            return findInMapping(input) != null;
        }

        // finds the (chain) converter for a particular input type
        public final Converter<Object, Object> find(TypeDeclaration input) {
            if (input.type == null) {
                throw new IllegalArgumentException("Unresolved input type: " + input.toString());
            }

            // 'Object' inputs can have any real object assigned to it
            // Just return our very own input converter in that case
            // This resolves the incoming object type into a proper converter at runtime
            if (input.type.equals(Object.class)) {
                return this.converter;
            }

            Node node = findInMapping(input);
            if (node == null) {
                initType(input);
            }

            // generate more layers deeper into the tree
            stepStuckCounter = 0;
            isReset = false;
            while (!nextNodes.isEmpty()) {
                lastNodes.clear();
                lastNodes.addAll(nextNodes);
                nextNodes.clear();
                for (Node nextNode : lastNodes) {
                    nextNode.step();
                    if (this.isReset) {
                        return find(input);
                    }
                    nextNodes.addAll(nextNode.children);
                }
                if (nextNodes.isEmpty()) {
                    lastNodes.clear();
                    lastNodes.trimToSize();
                    nextNodes.trimToSize();
                }
            }

            // find the converter in the mapping generated by the operation before
            node = findInMapping(input);

            // input type could not be found in the tree
            if (node == null) {
                return null; // not found
            } else if (node == this.root || node.previous == this.root) {
                return resolveInput(node.converter, input); // direct neighbor or self does not need a chain converter
            }

            // work down the chain of nodes to create a chain converter
            TypeDeclaration currInput = input;
            ArrayList<Converter<?, ?>> converters = new ArrayList<Converter<?, ?>>();
            do {
                Converter<?, ?> nextConv = resolveInput(node.converter, currInput);
                converters.add(nextConv);
                currInput = nextConv.output;
                node = node.previous;
            } while (node != this.root);

            if (converters.isEmpty()) {
                return null;
            }

            return new ChainConverter<Object, Object>(converters);
        }

        private Node findInMapping(TypeDeclaration input) {
            Node n = mapping.get(input);
            if (n != null && n.converter instanceof InputConverter) {
                InputConverter<Object> inputConv = (InputConverter<Object>) n.converter;
                if (inputConv.getConverter(input) == null) {
                    return null;
                }
            }
            return n;
        }

        /*
         * We want to make sure that InputConverters in the chain are resolved immediately
         * This allows for performance benefits, and enables use of converters without specifying input
         * type when the input type was specified in find()
         */
        @SuppressWarnings("unchecked")
        private static final Converter<Object, Object> resolveInput(Converter<?, ?> converter, TypeDeclaration input) {
            if (converter instanceof InputConverter) {
                Converter<?, ?> forInput = ((InputConverter<?>) converter).getConverter(input);
                if (forInput != null) {
                    return (Converter<Object, Object>) forInput;
                }
            }
            return (Converter<Object, Object>) converter;
        }

        // resets, causing regeneration at a later time
        public final void reset() {
            this.nullConverterSearched = false;
            this.mapping.clear();
            this.mapping.put(this.root.converter.input, this.root);
            this.lastNodes.clear();
            this.nextNodes.clear();
            this.nextNodes.add(this.root);
            this.isReset = true; // to avoid concurrent modification errors
        }

        private final class Node {
            private static final int MAX_STEPS = 10000;
            public final Node previous;
            public final Converter<Object, Object> converter;
            public final ArrayList<Node> children = new ArrayList<Node>();
            public final int cost;

            @SuppressWarnings("unchecked")
            public Node(Node previous, Converter<?, ?> converter) {
                this.converter = (Converter<Object, Object>) converter;
                this.previous = previous;
                this.cost = (previous == null) ? 0 : (previous.cost + converter.getCost() + 1); 
            }

            // generates the next series of input conversion steps
            public final void step() {
                // This here mechanic protects and reports endless step() calls, instead of freezing
                if (stepStuckCounter >= (MAX_STEPS + 10)) {
                    return;
                }
                for (Converter<?, ?> next : OutputConverterList.get(this.converter.input).getConverters()) {
                    Node n = new Node(this, next);
                    Node old = mapping.get(next.input);
                    if (old == null || old.cost > n.cost) {
                        mapping.put(next.input, n);
                        this.children.add(n);
                        ++stepStuckCounter;
                        if (stepStuckCounter == MAX_STEPS) {
                            MountiplexUtil.LOGGER.severe("Cyclical Conversion step() detected!");
                            if (this.previous != null) {
                                MountiplexUtil.LOGGER.severe("Previous: " + this.previous.converter);
                            }
                        }
                        if (stepStuckCounter >= MAX_STEPS) {
                            MountiplexUtil.LOGGER.severe("Next [" + n.cost + "]: " + n.converter);
                        }
                    }
                }
            }

            // debug-prints this node and its children to a StringBuilder
            public final void debugPrint(Node highlighted, StringBuilder str, int indent) {
                StringBuilder childStr = new StringBuilder();

                boolean found = false;
                Node n = highlighted;
                while (n != null) {
                    if (n == this) {
                        found = true;
                        break;
                    }
                    n = n.previous;
                }

                for (Node child : this.children) {
                    child.debugPrint(highlighted, childStr, indent + 1);
                }
                if (found) {
                    for (int i = 1; i < indent; i++) {
                        str.append("  ");
                    }
                    str.append(">>");
                } else {
                    for (int i = 0; i < indent; i++) {
                        str.append("  ");
                    }
                }
                str.append(this.converter.input.toString());
                if (found) {
                    str.append("<<");
                }
                str.append('\n');
                str.append(childStr);
            }
        }

        public static void resetTypeChange(TypeDeclaration type) {
            for (OutputConverterTree tree : trees.values()) {
                if (tree.mapping.containsKey(type)) {
                    tree.reset();
                }
            }
        }

        public static void resetAll() {
            for (OutputConverterTree tree : trees.values()) {
                tree.reset();
            }
        }

        public static OutputConverterTree getIfExists(TypeDeclaration output) {
            return trees.get(output);
        }

        public static OutputConverterTree get(TypeDeclaration output) {
            OutputConverterTree tree = trees.get(output);
            if (tree == null) {
                tree = new OutputConverterTree(output);
                trees.put(output, tree);
                initType(output);
            }
            return tree;
        }
    }

    // maintains information about direct converters from one type to another
    private static final class OutputConverterList {
        private static Map<TypeDeclaration, OutputConverterList> mapping = new HashMap<TypeDeclaration, OutputConverterList>();
        private final TypeDeclaration output;
        private final HashMap<TypeDeclaration, Converter<?, ?>> single = new HashMap<TypeDeclaration, Converter<?, ?>>();
        private final LinkedHashMap<TypeDeclaration, Converter<?, ?>> converters = new LinkedHashMap<TypeDeclaration, Converter<?, ?>>();
        private final ArrayList<OutputConverterList> parents = new ArrayList<OutputConverterList>();
        private final HashSet<OutputConverterList> children = new HashSet<OutputConverterList>();
        private boolean regen;

        public OutputConverterList(TypeDeclaration output) {
            this.output = output;
            this.reset();
        }

        public final void addConverter(Converter<?, ?> converter) {
            this.single.put(converter.input, converter);
            this.reset();
        }

        // sets a parent class for another class (e.g. Long is a parent of Number)
        public final void makeParent(OutputConverterList parent) {
            if (this.children.add(parent)) {
                parent.parents.add(this);
                this.reset();
            }
        }

        // regenerates the input-output converter listing by querying the providers
        // this occurs the very next time getConverters() is requested
        public final void reset() {
            this.regen = true;
            this.converters.clear();
            for (OutputConverterList child : children) {
                child.reset();
            }
        }

        // gets a collection of possible converters to convert to an output type
        // each input type is guaranteed to only have a single converter
        public final Collection<Converter<?, ?>> getConverters() {
            this.genConverters();
            return this.converters.values();
        }

        // generates the converter mapping
        private final void genConverters() {
            if (this.regen) {
                this.regen = false;
                this.converters.putAll(this.single);
                ArrayList<Converter<?, ?>> tmp = new ArrayList<Converter<?, ?>>();
                for (ConverterProvider provider : providers) {
                    provider.getConverters(this.output, tmp);
                    if (!tmp.isEmpty()) {
                        for (Converter<?, ?> converter : tmp) {
                            if (this.converters.putIfAbsent(converter.input, converter) == null) {
                                // No value was stored, and now is stored.
                                // Verify the converter. If this fails, remove the converter again.
                                // This way we can leverage contains + put
                                try {
                                    verifyConverter(converter);
                                } catch (InvalidConverterException ex) {
                                    this.converters.remove(converter.input);
                                    MountiplexUtil.LOGGER.warning(ex.getMessage() + ": " + converter);
                                } catch (Throwable t) {
                                    this.converters.remove(converter.input);
                                    MountiplexUtil.LOGGER.log(Level.SEVERE, "An error occurred registering " + converter, t);
                                }
                            }
                        }
                        tmp.clear();
                    }
                }
                for (OutputConverterList parent : this.parents) {
                    parent.genConverters();
                    for (Converter<?, ?> converter : parent.converters.values()) {
                        this.converters.putIfAbsent(converter.input, converter);
                    }
                }
            }
        }

        // gets the mapping for a particular output type
        public static OutputConverterList get(TypeDeclaration output) {
            if (!output.isResolved()) {
                throw new IllegalArgumentException("Requested type is not resolved: " + output);
            }
            OutputConverterList list = mapping.get(output);
            if (list == null) {
                list = new OutputConverterList(output);
                mapping.put(output, list);

                // Automatically register parent classes
                Class<?> superType = output.type.getSuperclass();
                if (superType != null) {
                    list.makeParent(get(TypeDeclaration.fromClass(superType)));
                }
                for (Class<?> iif : output.type.getInterfaces()) {
                    list.makeParent(get(TypeDeclaration.fromClass(iif)));
                }
            }
            return list;
        }

        // resets all output converter listing, regenerating the known converters on first use
        public static void resetAll() {
            for (OutputConverterList list : mapping.values()) {
                list.reset();
            }
        }
    }

    private static final class TypeTuple {
        public final TypeDeclaration t1, t2;
        private final int hashcode;

        public TypeTuple(Converter<?, ?> converter) {
            this(converter.input, converter.output);
        }

        public TypeTuple(TypeDeclaration t1, TypeDeclaration t2) {
            this.t1 = t1.getBoxedType();
            this.t2 = t2.getBoxedType();
            this.hashcode = (961 + (31 * this.t1.hashCode()) + this.t2.hashCode());
        }

        public final TypeTuple reverse() {
            return new TypeTuple(t2, t1);
        }

        @Override
        public final boolean equals(Object other) {
            if (other == this) {
                return true;
            } else if (other instanceof TypeTuple) {
                TypeTuple tuple = (TypeTuple) other;
                return tuple.t1.equals(this.t1) && tuple.t2.equals(this.t2); 
            } else {
                return false;
            }
        }

        @Override
        public final int hashCode() {
            return this.hashcode;
        }
    }

    /**
     * Lock that adds a mechanism to run certain tasks delayed at the end
     * of another thread's lock, instead of waiting for that thread to release
     * the lock. Prevents deadlock situations.
     */
    private static final class DeferLock extends ReentrantLock {
        private static final long serialVersionUID = 3302331820484906636L;
        private final ArrayList<Runnable> pending = new ArrayList<>();
        private volatile boolean hasPending = false;

        @Override
        public void lock() {
            super.lock();
            processPending();
        }

        @Override
        public void unlock() {
            try {
                processPending();
            } finally {
                super.unlock();
            }
        }

        /**
         * Schedules a task to be run right now, or when someone in the future locks/unlocks
         * this lock.
         *
         * @param runnable Runnable to be run
         */
        public void schedule(Runnable runnable) {
            if (super.tryLock()) {
                try {
                    processPending();
                    runnable.run();
                    processPending();
                } finally {
                    super.unlock();
                }
            } else {
                synchronized (pending) {
                    pending.add(runnable);
                    hasPending = true;
                }

                // Just in case we can now access the lock anyway, run the pending tasks
                // This is to prevent an item remaining inside the pending list, which would
                // be kind of sad.
                if (super.tryLock()) {
                    try {
                        processPending();
                    } finally {
                        super.unlock();
                    }
                }
            }
        }

        public void processPending() {
            while (hasPending) {
                ArrayList<Runnable> pendingCopy;
                synchronized (pending) {
                    if (!hasPending) {
                        return;
                    }

                    pendingCopy = new ArrayList<>(pending);
                    pending.clear();
                    hasPending = false;
                }
                pendingCopy.forEach(Runnable::run);
            }
        }
    }
}
