package com.bergerkiller.mountiplex.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.JavaCompile;

/**
 * Main Mountiplex Gradle plugin instance which defines the tasks that can be done
 */
public class MountiplexPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getExtensions().create("mountiplex", MountiplexExtension.class);

        TaskProvider<GenerateTemplateHandles> generateTemplateHandles = project.getTasks().register("generateTemplateHandles", GenerateTemplateHandles.class, task -> {
            task.getSourceDirectory().set(project.file("src/main/templates"));
            task.getOutputDirectory().set(project.file("src/main/generated"));
        });

        project.getPlugins().withType(JavaPlugin.class, javaPlugin -> {
            SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
            sourceSets.configureEach(sourceSet -> {
                TaskProvider<JavaCompile> compile = project.getTasks().named(sourceSet.getCompileJavaTaskName(), JavaCompile.class);
                project.getTasks().register(RemapAnnotationStrings.getTaskName(sourceSet), RemapAnnotationStrings.class, task -> {
                    task.dependsOn(compile);
                    task.getSourceDirectory().from(sourceSet.getJava().getSourceDirectories());
                    task.getInputDirectory().set(compile.flatMap(AbstractCompile::getDestinationDirectory));
                });
            });
        });
    }
}
