package com.bergerkiller.mountiplex.reflection.util.asm;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

public class AnnotationRemapTask {
    public final Path inputFile;
    public final Path outputFile;
    public final Map<String, String> variables;

    public AnnotationRemapTask(File inputFile, File outputFile, Map<String, String> variables) {
        this.inputFile = inputFile.toPath();
        this.outputFile = outputFile.toPath();
        this.variables = variables;
    }

    public void remap() {
        try {
            byte[] original = Files.readAllBytes(this.inputFile);
            byte[] replaced = AnnotationReplacer.replace(original, (annotationName, annotationValue) -> {
                if (annotationValue.length() > 2 && annotationValue.startsWith("%") && annotationValue.endsWith("%")) {
                    String name = annotationValue.substring(1, annotationValue.length() - 1);
                    return this.variables.getOrDefault(name, annotationValue);
                } else {
                    return annotationValue;
                }
            });
            Files.createDirectories(this.outputFile.getParent());
            Files.write(this.outputFile, replaced, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
