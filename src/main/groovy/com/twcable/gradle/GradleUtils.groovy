/*
 * Copyright 2015 Time Warner Cable, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twcable.gradle

import groovy.transform.CompileStatic
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.tasks.CachingTaskDependencyResolveContext

@CompileStatic
class GradleUtils {

    public static <T> T extension(Project project, Class<T> confClass, Object... args) {
        def conf = project.extensions.findByType(confClass)
        if (conf != null) return conf

        def confName = confClass.getDeclaredField("NAME").get(null) as String
        return project.extensions.create(confName, confClass, args)
    }


    @TypeChecked(TypeCheckingMode.SKIP)
    public static void taskDependencyGraph(Project project, Collection<Task> theTasks = null) {
        // TODO: Tie into the "Reporting" infrastructure of Gradle
        StringBuilder sb = new StringBuilder("digraph Compile {\n")
        sb.append("  node [style=filled, color=lightgray]\n")

        if (theTasks == null) {
            theTasks = project.tasks
        }

        for (Task task : theTasks) {
            sb.append("  \"${task.path}\" [style=rounded]\n")
            def context = new CachingTaskDependencyResolveContext()
            def dependencies = context.getDependencies(task)
            dependencies.each {
                sb.append("  \"${task.path}\"->\"${it.path}\"\n")
            }

            task.getShouldRunAfter().getDependencies(task).each {
                sb.append("  \"${task.path}\"->\"${it.path}\" [style=\"dotted\"]\n")
            }

            task.getMustRunAfter().getDependencies(task).each {
                sb.append("  \"${task.path}\"->\"${it.path}\" [style=\"dashed\"]\n")
            }
        }
        sb.append("}")


        def file = new File("tasks.dot")
        println "Writing to ${file.absolutePath}"
        file.write(sb.toString())
    }

}
