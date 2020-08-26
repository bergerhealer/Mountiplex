package com.bergerkiller.mountiplex.mojo;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

import com.bergerkiller.mountiplex.reflection.util.asm.AnnotationReplacer;

/**
 * Goal which generates source files from template files
 *
 * @goal preprocess-annotations
 *
 * @phase process-classes
 */
public class MountiplexCreateGenerateAnnotationsMojo extends AbstractMojo {
    /**
     * These variable names are ignored
     */
    private static final Set<String> IGNORED_NAMES = new HashSet<String>(Arrays.asList(
            "u", "i", "b", "p", "pre", "code"));

    /**
     * @parameter default-value="${project}"
     * @required
     * @readonly
     */
    MavenProject project;

    /**
     * Download the background, load image, paint labels and save
     */
    @SuppressWarnings("unchecked")
    public void execute() throws MojoExecutionException {
        System.out.println("--- Preprocessing source code annotations ---");

        // Check directory where compiled classes are located. Should exist.
        final File classes_dir = new File(project.getBuild().getOutputDirectory());
        if (!classes_dir.exists()) {
            throw new MojoExecutionException("Compiled output 'classes' directory does not exist: " + classes_dir.toString());
        }

        // File where we cache what source files we have processed
        File psf_cache_file = new File(project.getBuild().getDirectory(), "mountiplex/psf_cache.dat");
        psf_cache_file.getParentFile().mkdirs();

        // Load the files we have processed already from the psf_cache file
        Map<File, ProcessedSourceFile> processed = new HashMap<File, ProcessedSourceFile>();
        if (psf_cache_file.exists()) {
            try {
                try (DataInputStream input = new DataInputStream(new GZIPInputStream(new FileInputStream(psf_cache_file)))) {
                    int num_processed = input.readInt();
                    for (int i = 0; i < num_processed; i++) {
                        ProcessedSourceFile psf = ProcessedSourceFile.load(input);
                        processed.put(psf.relativeFile, psf);
                    }
                }
            } catch (IOException ex) {
                processed.clear();
                ex.printStackTrace();
            }
        }

        // Go by all files in the source tree and check for changes
        // If no data could be read from the cache file, this re-initializes it all
        boolean has_changes = false;
        try {
            for (String sourceDirPath : (Iterable<String>) project.getCompileSourceRoots()) {
                File sourceDir = new File(sourceDirPath);
                if (sourceDir.isDirectory() && sourceDir.exists()) {
                    sourceDir = sourceDir.getAbsoluteFile();
                    has_changes |= loadFiles(processed, sourceDir.toPath(), sourceDir);
                }
            }
        } catch (IOException ex) {
            throw new MojoExecutionException("Failed to parse source files", ex);
        }

        // Get rid of entries in the processed map that no longer exist
        for (Iterator<ProcessedSourceFile> iter = processed.values().iterator(); iter.hasNext();) {
            if (!iter.next().found) {
                iter.remove();
                has_changes = true;
            }
        }

        // Write parsed file results to the cache file again
        if (has_changes) {
            try {
                try (DataOutputStream stream = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(psf_cache_file)))) {
                    stream.writeInt(processed.size());
                    for (ProcessedSourceFile psf : processed.values()) {
                        psf.save(stream);
                    }
                }
            } catch (IOException ex) {
                ex.printStackTrace();
                psf_cache_file.delete();
            }
        }

        // Find all directories where .class files exist that need processing, and for each
        // directory needed create a group-by mapping of source java filename to the list
        // of class files matching it
        final Map<File, Map<String, List<File>>> directories = processed.values().stream()
                .filter(p -> !p.variables.isEmpty())
                .map(p -> p.relativeFile.getParentFile())
                .distinct()
                .collect(Collectors.toMap(
                        Function.identity(),
                        f -> createClassFileMap(new File(classes_dir, f.getPath()))
                ));

        // Go by all .java files to process and find the matching .class files, using the previously
        // created directories mapping
        processed.values().stream()
                .filter(p -> !p.variables.isEmpty())
                .flatMap(psf -> {
                    List<File> classFiles = directories.getOrDefault(psf.relativeFile.getParentFile(), Collections.emptyMap())
                            .get(psf.relativeFile.getName());
                    if (classFiles == null) {
                        System.err.println("Failed to process " + psf.relativeFile + ": Directory not found");
                        return Stream.empty();
                    }
                    return classFiles.stream().map(f -> new RemapTask(f, psf.variables));
                })
                .parallel().forEach(RemapTask::remap);
    }

    // Recursively loads all the java files an entire directory tree
    private boolean loadFiles(Map<File, ProcessedSourceFile> processed, Path root, File sourceFile) throws IOException {
        if (sourceFile.isDirectory()) {
            boolean has_changes = false;
            for (File subFile : sourceFile.listFiles()) {
                has_changes |= loadFiles(processed, root, subFile);
            }
            return has_changes;
        } else if (sourceFile.getName().toLowerCase(Locale.ENGLISH).endsWith(".java")) {
            File relativeFile = root.relativize(sourceFile.toPath()).toFile();

            ProcessedSourceFile existing = processed.get(relativeFile);
            long lastModified = sourceFile.lastModified();
            if (existing == null || existing.lastModified != lastModified) {
                ProcessedSourceFile processedSourceFile = new ProcessedSourceFile(relativeFile, lastModified);
                processedSourceFile.load(sourceFile);
                processed.put(relativeFile, processedSourceFile);
                return true;
            } else {
                existing.found = true;
            }
        }
        return false;
    }

    // Creates a group-by mapping of class files matching a java source file name
    private Map<String, List<File>> createClassFileMap(File directory) {
        File[] files = directory.listFiles((dir, name) -> {
            return name.toLowerCase(Locale.ENGLISH).endsWith(".class");
        });
        if (files == null || files.length == 0) {
            return Collections.emptyMap();
        } else {
            return Stream.of(files).collect(Collectors.groupingBy(f -> {
                String sourceName = f.getName();
                int nameEnd = sourceName.indexOf('$');
                if (nameEnd != -1) {
                    return sourceName.substring(0, nameEnd) + ".java";
                } else {
                    return sourceName.substring(0, sourceName.length()-6) + ".java";
                }
            }));
        }
    }

    /**
     * Stores the state of a preprocessed Java file
     * All variables stored in block comments are mapped key - block value
     */
    public static class ProcessedSourceFile {
        public final Map<String, String> variables = new HashMap<String, String>();
        public final File relativeFile;
        public final long lastModified;
        public boolean found;

        public ProcessedSourceFile(File relativeFile, long lastModified) {
            this.relativeFile = relativeFile;
            this.lastModified = lastModified;
            this.found = false;
        }

        public void save(DataOutputStream savedDataStream) throws IOException {
            savedDataStream.writeUTF(this.relativeFile.getPath());
            savedDataStream.writeLong(this.lastModified);
            savedDataStream.writeInt(variables.size());
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                savedDataStream.writeUTF(entry.getKey());
                savedDataStream.writeUTF(entry.getValue());
            }
        }

        public static ProcessedSourceFile load(DataInputStream savedDataStream) throws IOException {
            File relativeFile = new File(savedDataStream.readUTF());
            long lastModified = savedDataStream.readLong();
            int num_variables = savedDataStream.readInt();

            ProcessedSourceFile psf = new ProcessedSourceFile(relativeFile, lastModified);
            for (int i = 0; i < num_variables; i++) {
                String name = savedDataStream.readUTF();
                String value = savedDataStream.readUTF();
                psf.variables.put(name, value);
            }
            return psf;
        }

        public void load(File sourceFile) throws IOException {
            found = true;
            variables.clear();
            String content = new String(Files.readAllBytes(sourceFile.toPath()), StandardCharsets.UTF_8);

            // State keeping
            char curr_char = ' ';
            char prev_char = ' ';
            boolean in_string = false;
            boolean in_block_comment = false;
            boolean in_block_comment_contents = false;
            int start_indent = 0;
            int min_start_indent = Integer.MAX_VALUE;
            StringBuilder block_comment = new StringBuilder();

            // State processing loop
            for (int i = 0; i < content.length(); i++) {
                prev_char = curr_char;
                curr_char = content.charAt(i);

                // Ew.
                if (curr_char == '\r') {
                    continue;
                }

                if (!in_block_comment) {
                    // Ignore all String contents
                    if (curr_char == '\n') {
                        start_indent = 0;
                    } else if (curr_char == ' ') {
                        start_indent++;
                    } else if (curr_char == '\t') {
                        start_indent += 4;
                    } else if (in_string && prev_char != '\\' && curr_char == '\"') {
                        in_string = false;
                    } else if (!in_string && curr_char == '\"') {
                        in_string = true;
                    } else if (!in_string && prev_char == '/' && curr_char == '*') {
                        in_block_comment = true;
                        in_block_comment_contents = false;
                        start_indent += 2;
                        min_start_indent = Integer.MAX_VALUE;
                    }
                    continue;
                }

                // Skip whitespace preceeding it, deal with newlines properly
                if (!in_block_comment_contents) {
                    if (curr_char == '\n') {
                        block_comment.append('\n');
                        start_indent = 0;
                        continue;
                    } else if (curr_char == ' ' || curr_char == '*') {
                        start_indent++;
                        continue;
                    } else if (curr_char == '\t') {
                        start_indent += 4;
                        continue;
                    }
                    in_block_comment_contents = true;
                    appendSpaces(block_comment, start_indent);
                } else if (curr_char == '\n') {
                    in_block_comment_contents = false;
                    start_indent = 0;
                    block_comment.append('\n');
                    continue;
                }

                // Detect end of a /* block comment */
                if (prev_char == '*' && curr_char == '/') {
                    in_block_comment = false;
                    in_block_comment_contents = false;
                    cleanupSpaces(block_comment, min_start_indent);
                    processBlockComment(block_comment);

                    // Reuse
                    block_comment.setLength(0);
                    continue;
                }

                // Build up the contents of the block comment
                min_start_indent = Math.min(min_start_indent, start_indent);
                block_comment.append(curr_char);
            }
        }

        private void appendSpaces(StringBuilder str, int n) {
            while (--n >= 0) {
                str.append(' ');
            }
        }

        private void cleanupPreceedingSpaces(StringBuilder str) {
            for (int i = 0; i < str.length();) {
                char c = str.charAt(i);
                if (c == '\n') {
                    str.replace(0, i+1, "");
                    i = 0;
                } else if (c == ' ') {
                    i++;
                } else {
                    break;
                }
            }
        }
        
        private void cleanupSpaces(StringBuilder str, int indent) {
            // Remove unused spaces from the start
            cleanupPreceedingSpaces(str);

            // Remove unused spaces at the end
            for (int i = str.length()-1; i >= 0;) {
                char c = str.charAt(i);
                if (c == ' ' || c == '\n') {
                    i--;
                } else {
                    str.replace(i+1, str.length(), "");
                    break;
                }
            }

            // Trim indent spaces from the start of each line, if possible
            int remainingSpaces = indent;
            int numSkipped = 0;
            for (int i = 0; i < str.length(); i++) {
                char c = str.charAt(i);
                if (c == ' ' && remainingSpaces > 0) {
                    remainingSpaces--;
                    numSkipped++;
                } else if (numSkipped > 0) {
                    str.replace(i-numSkipped, i, "");
                    numSkipped = 0;
                }
                if (c == '\n') {
                    remainingSpaces = indent;
                }
            }
        }

        public void processBlockComment(StringBuilder str) {
            // Check for <start_marker>
            if (str.length() < 2 || str.charAt(0) != '<') {
                return;
            }

            // Find end of marker
            int endIndex = str.indexOf(">", 1);
            if (endIndex == -1) {
                return;
            }
            int newLineIndex = str.indexOf("\n", 1);
            if (newLineIndex == -1 || newLineIndex < endIndex) {
                return;
            }

            String variableName = str.substring(1, endIndex).trim();

            // Some variable names are used for javadoc formatting, ignore those
            if (IGNORED_NAMES.contains(variableName)) {
                return;
            }
            
            str.replace(0, endIndex+1, "");
            cleanupPreceedingSpaces(str);
            String variableValue = str.toString();

            this.variables.put(variableName, variableValue);
        }
    }

    private static class RemapTask {
        public final Path classFile;
        public final Map<String, String> variables;

        public RemapTask(File classFile, Map<String, String> variables) {
            this.classFile = classFile.toPath();
            this.variables = variables;
        }

        public void remap() {
            try {
                byte[] original = Files.readAllBytes(this.classFile);
                byte[] replaced = AnnotationReplacer.replace(original, (annotationName, annotationValue) -> {
                    if (annotationValue.length() > 2 && annotationValue.startsWith("%") && annotationValue.endsWith("%")) {
                        String name = annotationValue.substring(1, annotationValue.length()-1);
                        return this.variables.getOrDefault(name, annotationValue);
                    } else {
                        return annotationValue;
                    }
                });
                Files.write(this.classFile, replaced, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }
}
