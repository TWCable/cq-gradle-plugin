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

package com.twcable.gradle.scr

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import groovy.util.slurpersupport.NodeChild
import org.apache.felix.scrplugin.ant.SCRDescriptorTask
import org.apache.tools.ant.types.Path
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.plugins.osgi.OsgiManifest
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Jar

@TypeChecked
@SuppressWarnings("GrMethodMayBeStatic")
class ScrPlugin implements Plugin<Project> {
    @SuppressWarnings("GroovyUnusedDeclaration")
    final static String SCR_ANT_VERSION = '1.3.0'

    @SuppressWarnings("GroovyUnusedDeclaration")
    final static String BND_LIB_VERSION = '1.50.0'


    @Override
    void apply(Project project) {
        project.apply(plugin: 'osgi')

        addScrTask(project)
    }


    protected void addScrTask(Project project) {
        final processScrAnnotations = project.tasks.create('processScrAnnotations')
        processScrAnnotations.with {
            group = 'Build'
            description = 'Processes the Felix SCR service annotations'
            dependsOn 'classes'
            outputs.file new File(mainSourceSet(project).output.classesDir, 'OSGI-INF/serviceComponents.xml')
            inputs.source mainSourceSet(project).output.classesDir

            doLast {
                configureAction(project)
            }
        }
        project.tasks.getByName('jar').dependsOn processScrAnnotations
    }


    void configureAction(Project project) {
        SourceSet mainSourceSet = mainSourceSet(project)
        final antProject = project.ant.project
        final classesDir = mainSourceSet.output.classesDir
        final runtimeClasspath = mainSourceSet.runtimeClasspath
        final runtimePath = runtimeClasspath.asPath

        project.logger.info "Running SCR for ${classesDir}"
        if (classesDir.exists()) {
            final task = new SCRDescriptorTask(srcdir: classesDir, destdir: classesDir,
                classpath: new Path(antProject, runtimePath), strictMode: false, project: antProject, scanClasses: true)
            task.execute()

            addToManifest(project, classesDir)
        }
    }


    void addToManifest(Project project, File resourcesDir) throws InvalidUserDataException {
        final osgiInfDir = new File(resourcesDir, 'OSGI-INF')

        def files = osgiInfDir.listFiles({ File dir, String name ->
            name.endsWith(".xml")
        } as FilenameFilter) as List<File>

        if (!files.isEmpty()) {
            def relFiles = files.collect { file -> 'OSGI-INF/' + file.name }
            project.logger.info "Created ${relFiles}"
            final jar = (Jar)project.tasks.getByName('jar')
            final osgiManifest = (OsgiManifest)jar.manifest
            osgiManifest.instruction('Service-Component', relFiles.join(','))
            validateReferences(project, files)
        }
        else {
            project.logger.warn "${osgiInfDir}/*.xml was not created"
        }
    }


    @TypeChecked(TypeCheckingMode.SKIP)
    private void validateReferences(Project project, List<File> files) {
        def errorMessage = ""

        for (file in files) {
            def component = new XmlSlurper().parse(file)
            for (references in component.reference) {
                errorMessage = generateErrorMsg(errorMessage, references.@interface.text(), component.@name.text(),
                    loadClassPaths(project))
            }
        }

        if (errorMessage != "") {
            throw new InvalidUserDataException(errorMessage)
        }
    }


    private String generateErrorMsg(String errorMessage, String interfaceName, String className, ClassLoader classLoader) {
        try {
            def clas = classLoader.loadClass(interfaceName)

            if (!clas.isInterface()) {
                errorMessage += "\n${className} has an @Reference to ${interfaceName}, but it is not an interface."
            }
        }
        catch (ClassNotFoundException ignored) {
            errorMessage += "\n${className} has an @Reference to ${interfaceName} that could not be found by the class loader."
        }
        errorMessage
    }

    /**
     * load classPaths into class loader
     */
    private ClassLoader loadClassPaths(Project project) throws InvalidUserDataException {
        def classpathURLs = mainSourceSet(project).runtimeClasspath.collect { File f -> f.toURI().toURL() }
        if (!classpathURLs) throw new InvalidUserDataException("Runtime class path empty.")
        new URLClassLoader(classpathURLs as URL[])
    }


    public SourceSet mainSourceSet(Project project) {
        return project.convention.findPlugin(JavaPluginConvention)?.sourceSets?.getByName('main')
    }

}
