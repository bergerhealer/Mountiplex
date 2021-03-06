package com.bergerkiller.mountiplex.mojo;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import com.bergerkiller.mountiplex.reflection.declarations.ClassDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.SourceDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.TemplateGenerator;
import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;

/**
 * Goal which generates source files from template files
 *
 * @goal create-reflection
 *
 * @phase generate-sources
 */
public class MountiplexCreateReflectionMojo extends AbstractMojo {

    /**
     * Root directory of the source files being processed.
     * Paths are resolved relative to here.
     * 
     * @parameter
     */
    private File source_root;

    /**
     * Root directory of the source files where generated source files are placed.
     * Paths are resolved relative to here.
     * 
     * @parameter
     */
    private File target_root;

    public static class GenerationAction {
        /**
         * Source-relative path to the template file which will be loaded
         * 
         * @parameter
         */
        public String source;

        /**
         * Target-relative path to where generated source files will be placed
         * 
         * @parameter
         */
        public String target;

        /**
         * Variables to use to process the source files
         * 
         * @parameter
         */
        public Map<String, String> variables;
    }

    /**
     * Array of generation options to execute
     * 
     * @parameter
     */
    private List<GenerationAction> generatorActions;

    /**
     * Download the background, load image, paint labels and save
     */
    public void execute() throws MojoExecutionException {
        System.out.println("--- Generating reflection source code ---");
        System.out.println("Source Root: " + source_root.getAbsolutePath());
        System.out.println("Target Root: " + target_root.getAbsolutePath());

        if (generatorActions == null || generatorActions.size() == 0) {
            System.out.println("No generator actions specified. Nothing happened.");
            return;
        }

        try {
            for (GenerationAction opt: generatorActions) {
                System.out.println("Source: " + opt.source);
                System.out.println("Target: " + opt.target);

                // Create template generators for all found classes
                SourceDeclaration dec = SourceDeclaration.loadFromDisk(source_root, opt.source, opt.variables, true);
                HashMap<TypeDeclaration, TemplateGenerator> generators = new HashMap<TypeDeclaration, TemplateGenerator>();
                for (ClassDeclaration classDec : dec.classes) {
                    String path = classDec.getResolver().getPackage().replace('.', '/');
                    TemplateGenerator gen = new TemplateGenerator();
                    gen.setRootDirectory(this.target_root);
                    gen.setPath(opt.target + "/" + path);
                    gen.setClass(classDec);
                    gen.setPool(generators);
                    registerGenerators(generators, classDec, gen);
                }

                // Proceed with generation
                for (TemplateGenerator generator : generators.values()) {
                    generator.generate();
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private static void registerGenerators(Map<TypeDeclaration, TemplateGenerator> pool, ClassDeclaration cDec, TemplateGenerator gen) {
        pool.put(cDec.type, gen);
        for (ClassDeclaration subCDec : cDec.subclasses) {
            registerGenerators(pool, subCDec, gen);
        }
    }
}
