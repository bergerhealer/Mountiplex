package com.bergerkiller.mountiplex.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.JavaCompile;

import java.io.File;

public class MountiplexPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        MountiplexExtension extension = project.getExtensions().create("mountiplex", MountiplexExtension.class);

        TaskProvider<GenerateReflection> generateReflection = project.getTasks().register("generateReflection", GenerateReflection.class, task -> {
            task.getSourceDirectory().set(project.file("src/main/templates"));
            task.getOutputDirectory().set(project.file("src/main/generated"));
            task.getSource().set(extension.getSource());
            task.getTarget().set(extension.getGenerated());
            task.getVariables().set(extension.getVariables());
        });

        project.getPlugins().withType(JavaPlugin.class, javaPlugin -> {
            SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
            sourceSets.configureEach(sourceSet -> {
                sourceSet.getJava().srcDir(generateReflection);

                TaskProvider<JavaCompile> compile = project.getTasks().named(sourceSet.getCompileJavaTaskName(), JavaCompile.class);

                TaskProvider<RemapAnnotations> remapAnnotations = project.getTasks().register(sourceSet.getTaskName("remap", "annotations"), RemapAnnotations.class, task -> {
                    task.dependsOn(compile);
                    task.getSourceDirectory().from(sourceSet.getJava().getSourceDirectories());
                    task.getInputDirectory().set(compile.flatMap(AbstractCompile::getDestinationDirectory));
                });

                compile.configure(task -> {
                    task.getDestinationDirectory().set(new File(project.getBuildDir(), "mountiplex-classes/" + sourceSet.getName()));
                });

                sourceSet.compiledBy(remapAnnotations);
                sourceSet.getJava().compiledBy(remapAnnotations, RemapAnnotations::getOutputDirectory);
            });
        });
    }
}
