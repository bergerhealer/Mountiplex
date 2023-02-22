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

/**
 * Parses template text files and generates the reflection Handle classes
 */
public abstract class GenerateTemplateHandles extends DefaultTask {
    private static void registerGenerators(Map<TypeDeclaration, TemplateGenerator> pool, ClassDeclaration cDec, TemplateGenerator gen) {
        pool.put(cDec.type, gen);
        for (ClassDeclaration subCDec : cDec.subclasses) {
            registerGenerators(pool, subCDec, gen);
        }
    }

    /**
     * The source directory property. This configures the location relative to which
     * source template .txt files are read.
     *
     * @return source directory property
     */
    @InputDirectory
    public abstract DirectoryProperty getSourceDirectory();

    /**
     * The source template .txt file. This is the first source .txt file read, and may
     * contain include directives for additional files found inside {@link #getSourceDirectory()}
     *
     * @return source template .txt file
     */
    @Input
    public abstract Property<String> getSource();

    /**
     * The output directory. This is where generated Handle classes are placed.
     *
     * @return output directory
     */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    /**
     * The package root where generated Handle classes are placed. Ideally the
     * {@link #getOutputDirectory()} is set to the source folder, and this is set
     * to the package inside the source folder.
     *
     * @return target package
     */
    @Input
    public abstract Property<String> getTarget();

    /**
     * Variables used while parsing the template files. Optional.
     *
     * @return variables
     */
    @Input
    public abstract MapProperty<String, String> getVariables();

    /**
     * Performs the main Handle class generating process
     */
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
