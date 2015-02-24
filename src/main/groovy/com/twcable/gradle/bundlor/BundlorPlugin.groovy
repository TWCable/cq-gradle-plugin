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

package com.twcable.gradle.bundlor

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.CopySpec

@TypeChecked
@SuppressWarnings("GroovyUnusedDeclaration")
class BundlorPlugin implements Plugin<Project> {
    final static String BUNDLOR_VERSION = '1.1.2.RELEASE'


    @Override
    void apply(Project project) {
        addConfigurations(project)
        addBundlorAntTask(project)
        addOsgiWrapJarToProject(project)
    }


    @SuppressWarnings("UnnecessaryQualifiedReference")
    protected static void addOsgiWrapJarToProject(Project project) {
        project.convention.add('osgiWrapJar', BundlorPlugin.&wrapJar.curry(project))
    }


    protected static void addConfigurations(Project project) {
        project.configurations.create('bundlor-plugin')

        final compile = project.configurations.findByName('compile')
        if (compile)
            compile.extendsFrom(project.configurations.create('embed'))
    }


    @TypeChecked(TypeCheckingMode.SKIP)
    protected static void addBundlorAntTask(Project project) {
        project.dependencies.add('bundlor-plugin', [group: 'org.eclipse.virgo.bundlor', name: 'org.eclipse.virgo.bundlor.ant', version: BUNDLOR_VERSION])
        project.dependencies.add('bundlor-plugin', [group: 'org.eclipse.virgo.bundlor', name: 'org.eclipse.virgo.bundlor.blint', version: BUNDLOR_VERSION])
        project.dependencies.add('bundlor-plugin', [group: 'org.eclipse.virgo.bundlor', name: 'org.eclipse.virgo.bundlor', version: BUNDLOR_VERSION])

        project.ant.taskdef([resource: 'org/eclipse/virgo/bundlor/ant/antlib.xml', classpath: project.configurations.getByName('bundlor-plugin').asPath])
    }


    static wrapJar(Project project, String name, String symbolicName, String bundleVersion = null, Map options = null) {
        final confName = "${name}_file"
        project.configurations.create(confName)
        final version = bundleVersion ?: project[name + '_version'] as String
        project.dependencies.add(confName, [group: symbolicName, name: name, version: version])

        // assuming there is exactly one file in the configuration
        final path = project.configurations.getByName(confName).files.asList().first().path

        final osgiVersion = osgiVersion(version)
        wrapJar(project, path, project.file("./${name}-${osgiVersion}-osgi.jar"), name, symbolicName, osgiVersion, options)
    }


    static wrapJars(Project project, List<Map<String, Object>> jars, String bundleName, String symbolicName, String version, Map givenOptions = null, boolean transitive = false) {
        final confName = "${bundleName}_file"
        project.configurations.create(confName).transitive = transitive
        jars.each { Map<String, Object> jarDesc ->
            project.logger.info("Adding ${jarDesc} to ${confName}")
            project.dependencies.add(confName, jarDesc)
        }

        final osgiVersion = osgiVersion(version)
        final intermediateJar = project.file("./${bundleName}-${osgiVersion}.jar")
        final finalJarName = project.file("./${bundleName}-${osgiVersion}-osgi.jar")

        createIntermediateJar(project, bundleName, confName, intermediateJar)

        project.logger.info("Wrapping ${finalJarName}")
        wrapJar(project, intermediateJar, finalJarName, bundleName, symbolicName, version, givenOptions)

        intermediateJar.delete()
    }


    @TypeChecked(TypeCheckingMode.SKIP)
    static void createIntermediateJar(Project project, String bundleName, String confName, File intermediateJarName) {
        File dir = createTempDir(project, bundleName)

        final paths = project.configurations.getByName(confName).files.collect { it.path }

        project.copy { CopySpec copy ->
            paths.each { String path ->
                project.logger.debug("  adding files from ${path}")
                copy.from project.zipTree(path).matching { copy.exclude 'META-INF/MANIFEST.MF' }
                copy.into dir
            }
        }

        project.ant.jar(destfile: intermediateJarName, basedir: dir.absolutePath)
        dir.delete()
    }


    def static File createTempDir(Project project, String bundleName) {
        File baseDir = new File(project.buildDir, 'tmp/expandedJars')
        baseDir.mkdirs()
        File dir = File.createTempFile(bundleName, 'dir', baseDir)
        dir.delete()
        dir.mkdirs()
        dir
    }


    @TypeChecked(TypeCheckingMode.SKIP)
    static wrapJar(Project project, inputPath, File outputPath, String bundleName, String symbolicName, String version, Map givenOptions = null) {
        def options = [:]
        options['Bundle-ManifestVersion'] = '2'
        options['Bundle-Name'] = bundleName
        options['Bundle-SymbolicName'] = symbolicName
        options['Bundle-Version'] = osgiVersion(version)
        if (givenOptions != null) {
            options.putAll(givenOptions)
        }

        String template = options.collect { k, v ->
            "${k}: ${v}"
        }.join("\n")

        project.ant.bundlor(inputPath: inputPath, outputPath: outputPath) {
            manifestTemplate(template)
        }
    }


    @TypeChecked(TypeCheckingMode.SKIP)
    @SuppressWarnings("GroovyEmptyStatementBody")
    static String osgiVersion(String version) {
        List<String> splitVersion = version.split('\\.').toList()

        if (splitVersion.size() == 3) {
            if (splitVersion[0] ==~ /^\d+$/) {
                if (splitVersion[1] ==~ /^\d+$/) {
                    if (!(splitVersion[2] ==~ /^\d+$/)) {
                        splitVersion = [] << splitVersion[0] << splitVersion[1] << '0' << splitVersion[2]
                    }
                }
                else {
                    splitVersion = [] << splitVersion[0] << '0' << '0' << splitVersion[1] << splitVersion[2]
                }
            }
        }
        else if (splitVersion.size() == 2) {
            if (splitVersion[0] =~ /^\d+$/) {
                if (splitVersion[1] =~ /^\d+$/) {
                    splitVersion << '0'
                }
                else {
                    splitVersion = [] << splitVersion[0] << '0' << '0' << splitVersion[1]
                }
            }
        }
        else if (splitVersion.size() == 1) {
            if (splitVersion[0] =~ /^\d+$/) {
                splitVersion << '0' << '0'
            }
        }

        splitVersion.join('.')
    }


    @SuppressWarnings("GroovyEmptyStatementBody")
    def static String osgiVersionUpperLimit(String version) {
        String normalizedVersion = osgiVersion(version)
        List<String> splitVersion = normalizedVersion.split('\\.').toList()

        if (splitVersion[0] ==~ /^\d+$/) {
            splitVersion[0] = (Integer.parseInt(splitVersion[0]) + 1).toString()
            if (splitVersion.size() > 1) {
                splitVersion = [splitVersion[0], '0', '0']
            }
        }

        splitVersion.join('.')
    }

}
