package com.bergerkiller.mountiplex.gradle;

import com.bergerkiller.mountiplex.reflection.declarations.ClassDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.SourceDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.TemplateGenerator;
import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.util.HashMap;
import java.util.Map;

public abstract class GenerateReflection extends DefaultTask {
    private static void registerGenerators(Map<TypeDeclaration, TemplateGenerator> pool, ClassDeclaration cDec, TemplateGenerator gen) {
        pool.put(cDec.type, gen);
        for (ClassDeclaration subCDec : cDec.subclasses) {
            registerGenerators(pool, subCDec, gen);
        }
    }

    @InputDirectory
    public abstract DirectoryProperty getSourceDirectory();

    @Input
    public abstract Property<String> getSource();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @Input
    public abstract Property<String> getTarget();

    @Input
    public abstract MapProperty<String, String> getVariables();

    @TaskAction
    public void generate() {
        // Create template generators for all found classes
        SourceDeclaration sourceDeclaration = SourceDeclaration.loadFromDisk(
                getSourceDirectory().get().getAsFile(), getSource().get(), getVariables().get(), true);
        HashMap<TypeDeclaration, TemplateGenerator> generators = new HashMap<>();
        for (ClassDeclaration classDec : sourceDeclaration.classes) {
            String path = classDec.getResolver().getPackage().replace('.', '/');
            TemplateGenerator gen = new TemplateGenerator();
            gen.setRootDirectory(getOutputDirectory().get().getAsFile());
            gen.setPath(getTarget().get() + "/" + path);
            gen.setClass(classDec);
            gen.setPool(generators);
            registerGenerators(generators, classDec, gen);
        }

        // Proceed with generation
        for (TemplateGenerator generator : generators.values()) {
            generator.generate();
        }
    }
}
