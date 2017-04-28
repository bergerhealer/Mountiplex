package com.bergerkiller.mountiplex;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

/**
 * Goal which generates source files from template files
 *
 * @goal create-reflection
 *
 * @phase generate-sources
 */
public class MountiplexMojo extends AbstractMojo {

    /**
     * Directory containing the template files to be processed.
     * Is read recursively.
     * 
     * @parameter
     */
    private File source_dir;

    /**
     * Directory where all the generated source files will be put.
     * Is written recursively relative to the source directory.
     * 
     * @parameter
     */
    private File target_dir;

    /**
     * Download the background, load image, paint labels and save
     */
    public void execute() throws MojoExecutionException {
        // List all template files
        
        
        // Done!
        getLog().info("PLUGIN EXECUTED AYY LMAO");
        
        BufferedWriter writer = null;
        try {
            File f = new File(target_dir, "test.java");
            writer = new BufferedWriter(new FileWriter(f));
            writer.write("LMAO");
            writer.close();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private void loadTemplates(File directory) {
        
    }
}
