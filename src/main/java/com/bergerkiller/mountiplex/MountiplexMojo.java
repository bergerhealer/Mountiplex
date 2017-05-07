package com.bergerkiller.mountiplex;

import java.io.File;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import com.bergerkiller.mountiplex.reflection.declarations.ClassDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.SourceDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.TemplateGenerator;

/**
 * Goal which generates source files from template files
 *
 * @goal create-reflection
 *
 * @phase generate-sources
 */
public class MountiplexMojo extends AbstractMojo {

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
    
    /**
     * Source-relative path to the template file which will be loaded
     * 
     * @parameter
     */
    private String source;

    /**
     * Target-relative path to where generated source files will be placed
     * 
     * @parameter
     */
    private String target;

    /**
     * Download the background, load image, paint labels and save
     */
    public void execute() throws MojoExecutionException {
        // List all template files
        System.out.println("--- Generating reflection source code ---");
        System.out.println("Source Root: " + source_root.getAbsolutePath());
        System.out.println("Target Root: " + target_root.getAbsolutePath());
        System.out.println("Source: " + source);
        System.out.println("Target: " + target);

        SourceDeclaration dec = SourceDeclaration.loadFromDisk(source_root, source);
        for (ClassDeclaration classDec : dec.classes) {
            TemplateGenerator gen = new TemplateGenerator();
            gen.setRootDirectory(this.target_root);
            gen.setPath(this.target);
            gen.setClass(classDec);
            gen.generate();
        }
    }

}
