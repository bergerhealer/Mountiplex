package com.bergerkiller.mountiplex.gradle;

import com.bergerkiller.mountiplex.reflection.util.asm.AnnotationRemapTask;
import com.bergerkiller.mountiplex.reflection.util.asm.SourceFileProcessor;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.*;
import org.gradle.work.ChangeType;
import org.gradle.work.FileChange;
import org.gradle.work.Incremental;
import org.gradle.work.InputChanges;

import java.io.File;
import java.util.*;

public abstract class RemapAnnotations extends DefaultTask {
    private final SourceFileProcessor sourceFileProcessor = new SourceFileProcessor();

    private static String getClassName(String classFileName) {
        if (!classFileName.endsWith(".class")) {
            return null;
        }
        classFileName = classFileName.substring(0, classFileName.length() - 6);
        int end = classFileName.indexOf('$');
        if (end != -1) {
            classFileName = classFileName.substring(0, end);
        }
        return classFileName;
    }

    @Incremental
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSourceDirectory();

    @Incremental
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getInputDirectory();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void generate(InputChanges inputChanges) throws Exception {
        Map<String, Entry> changes = new HashMap<>();

        // Collect changed .class files
        for (FileChange change : inputChanges.getFileChanges(getInputDirectory())) {
            String name = change.getNormalizedPath();
            if (change.getChangeType() == ChangeType.REMOVED) {
                // Input class was removed, remove it from the output
                getOutputDirectory().file(name).get().getAsFile().delete();
                continue;
            }

            String className = getClassName(name);
            if (className != null) {
                changes.put(className, new Entry());
            }
        }

        // Collect changed .java files
        for (FileChange change : inputChanges.getFileChanges(getSourceDirectory())) {
            String name = change.getNormalizedPath();
            if (name.endsWith(".java")) {
                changes.put(name.substring(0, name.length() - 5), new Entry());
            }
        }

        // Assign .java files to class names
        getSourceDirectory().getAsFileTree().visit(details -> {
            String name = details.getPath();
            if (name.endsWith(".java")) {
                Entry entry = changes.get(name.substring(0, name.length() - 5));
                if (entry != null) {
                    entry.setSourceFile(details.getFile());
                }
            }
        });

        // Assign .class files to class names
        getInputDirectory().getAsFileTree().visit(details -> {
            String name = details.getPath();
            String className = getClassName(name);
            if (className != null) {
                Entry entry = changes.get(className);
                if (entry != null) {
                    entry.addClassFilePath(name);
                }
            }
        });

        // Process all changed classes
        File inputDirectory = getInputDirectory().get().getAsFile();
        File outputDirectory = getOutputDirectory().get().getAsFile();
        for (Map.Entry<String, Entry> change : changes.entrySet()) {
            processChange(change.getKey(), change.getValue(), inputDirectory, outputDirectory);
        }
    }

    private void processChange(String className, Entry entry, File inputDirectory, File outputDirectory)
            throws Exception {
        getLogger().info("Processing {}", className);

        // Read variables from the source file
        Map<String, String> variables;
        if (entry.sourceFile != null) {
            variables = sourceFileProcessor.process(entry.sourceFile);
        } else {
            getLogger().warn("Source file not found for class: {}", className);
            variables = Collections.emptyMap();
        }

        // Replace variables in class files
        for (String classFilePath : entry.getClassFilePaths()) {
            getLogger().info("Remapping {}", classFilePath);
            File inputFile = new File(inputDirectory, classFilePath);
            File outputFile = new File(outputDirectory, classFilePath);
            AnnotationRemapTask remapTask = new AnnotationRemapTask(inputFile, outputFile, variables);
            remapTask.remap();
        }
    }

    private static class Entry {
        private final Set<String> classFilePaths = new HashSet<>();
        private File sourceFile;

        public void setSourceFile(File sourceFile) {
            this.sourceFile = sourceFile;
        }

        public void addClassFilePath(String path) {
            classFilePaths.add(path);
        }

        public Set<String> getClassFilePaths() {
            return classFilePaths;
        }
    }
}
