package com.bergerkiller.mountiplex.gradle;

import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;

import javax.inject.Inject;
import java.io.File;

public abstract class MountiplexExtension {
    @Inject
    public abstract Project getProject();

    public void generateTemplateHandles() {
        getProject().getPlugins().withType(JavaPlugin.class, javaPlugin -> {
            SourceSetContainer sourceSets = getProject().getExtensions().getByType(SourceSetContainer.class);
            sourceSets.configureEach(sourceSet -> {
                sourceSet.java(java -> {
                    java.srcDir(getProject().getTasks().named("generateTemplateHandles"));
                });
            });
        });
    }

    public void remapAnnotationStrings() {
        getProject().getPlugins().withType(JavaPlugin.class, javaPlugin -> {
            SourceSetContainer sourceSets = getProject().getExtensions().getByType(SourceSetContainer.class);
            sourceSets.configureEach(sourceSet -> {
                TaskProvider<JavaCompile> compile = getProject().getTasks().named(sourceSet.getCompileJavaTaskName(), JavaCompile.class);
                TaskProvider<RemapAnnotationStrings> remapAnnotations = getProject().getTasks().named(RemapAnnotationStrings.getTaskName(sourceSet), RemapAnnotationStrings.class);

                compile.configure(task -> {
                    task.getDestinationDirectory().set(new File(getProject().getBuildDir(), "mountiplex-classes/" + sourceSet.getName()));
                });

                sourceSet.compiledBy(remapAnnotations);
                sourceSet.getJava().compiledBy(remapAnnotations, RemapAnnotationStrings::getOutputDirectory);
            });
        });
    }
}
