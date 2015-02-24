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
            final task = new ClassSCRDescriptorTask(srcdir: classesDir, destdir: classesDir,
                classpath: new Path(antProject, runtimePath), strictMode: true, project: antProject)
            task.execute()

            addToManifest(project, classesDir)
        }
    }


    void addToManifest(Project project, File resourcesDir) throws InvalidUserDataException {
        final osgiInfDir = new File(resourcesDir, 'OSGI-INF')
        final scFile = new File(osgiInfDir, 'serviceComponents.xml')
        if (scFile.exists()) {
            project.logger.info "Created ${scFile}"
            final jar = (Jar)project.tasks.getByName('jar')
            final osgiManifest = (OsgiManifest)jar.manifest
            osgiManifest.instruction('Service-Component', 'OSGI-INF/serviceComponents.xml')
            validateReferences(project, scFile)
        }
        else {
            project.logger.warn "${scFile} was not created"
        }
    }


    private Collection<Map> allComponents(File scFile) {
        return new XmlSlurper().parse(scFile).children().findAll { NodeChild node ->
            node.name() == 'component'
        } as Collection<Map>
    }


    @TypeChecked(TypeCheckingMode.SKIP)
    private void validateReferences(Project project, File scFile) {
        def allComponents = allComponents(scFile)
        def errorMessage = ""

        if (!allComponents) {
            project.logger.warn "No components found in ${scFile}."
            return
        }

        for (component in allComponents) {
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
