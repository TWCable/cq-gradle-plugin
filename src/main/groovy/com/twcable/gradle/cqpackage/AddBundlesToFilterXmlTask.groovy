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

package com.twcable.gradle.cqpackage

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import groovy.util.logging.Slf4j
import org.apache.commons.io.IOUtils
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Jar

@Slf4j
@TypeChecked
class AddBundlesToFilterXmlTask extends DefaultTask {
    private Reader _filterXmlReader

    private Writer _filterXmlWriter

    private Collection<String> _jarFileNames


    @TaskAction
    @SuppressWarnings("GroovyUnusedDeclaration")
    void writeFile() {
        def fileNames = jarFileNames
        addJarsToXmlAndWrite(fileNames)
    }


    Collection<String> getJarFileNames() {
        if (_jarFileNames == null) {
            final resolvedConfiguration = project.configurations.getByName('cq_package').resolvedConfiguration

            _jarFileNames = resolvedConfiguration.resolvedArtifacts.collect { ResolvedArtifact ra ->
                ra.file.name
            }
        }
        return _jarFileNames
    }


    void setJarFileNames(Collection<String> jarFileNames) {
        _jarFileNames = jarFileNames
    }


    Reader getFilterXmlReader() {
        if (_filterXmlReader == null) {
            def srcFile = ((Project)project).file('src/main/content/META-INF/vault/filter.xml')
            _filterXmlReader = new FileReader(srcFile)
        }
        return _filterXmlReader
    }


    void setFilterXmlReader(Reader filterXmlReader) {
        _filterXmlReader = filterXmlReader
    }


    Writer getFilterXmlWriter() {
        if (_filterXmlWriter == null) {
            def xmlFilename = "${project.buildDir}/tmp/filter.xml"
            _filterXmlWriter = new FileWriter(xmlFilename)
        }
        return _filterXmlWriter
    }


    void setFilterXmlWriter(Writer filterXmlWriter) {
        _filterXmlWriter = filterXmlWriter
    }


    void addJarsToXmlAndWrite(Collection<String> jarFiles) {
        FilterDefinition filterDefinition = FilterDefinition.create(project, jarFiles)
        def newXml = addJarsToXml(filterDefinition)
        writeReader(newXml, filterXmlWriter)
    }


    private Reader addJarsToXml(FilterDefinition filterDefinition) {
        def xml = new XmlParser().parse(filterXmlReader)
        def filters = xml.children()

        def oldFilterNode = removeOldFilterNode(filters, filterDefinition.bundleInstallRoot)
        def newFilterNode = createNewFilterNode(oldFilterNode, filters, xml, filterDefinition.bundleInstallRoot)

        attachJarsToFilterNode(newFilterNode, filterDefinition)

        return xmlToReader(xml)
    }


    private static void attachJarsToFilterNode(Node filter, FilterDefinition filterDefinition) {
        attachExcludeNodes(filter, jarPatterns(filterDefinition.dependencyJarFiles, filterDefinition.dependencyStartLevel, filterDefinition.bundleInstallRoot))
        attachExcludeNodes(filter, jarPatterns(filterDefinition.allNativeProjectJarNames, filterDefinition.nativeStartLevel, filterDefinition.bundleInstallRoot))
    }


    private static Reader xmlToReader(Node xml) {
        StringWriter writer = new StringWriter()
        new XmlNodePrinter(new PrintWriter(writer)).print(xml)
        return new StringReader(writer.toString())
    }


    private static void attachExcludeNodes(Node parentNode, Collection<String> excludePatterns) {
        excludePatterns.each { String excludePattern ->
            // creating the Node attaches it to its parent
            new Node(parentNode, 'exclude', [pattern: excludePattern])
        }
    }


    private static Collection<String> jarPatterns(Collection<String> jarFiles,
                                                  String startLevel,
                                                  String bundleInstallRoot) {
        if (startLevel == null) throw new IllegalArgumentException("startLevel == null")
        jarFiles.collect { String jarFileName ->
            "${bundleInstallRoot}/${startLevel}/${jarFileName}"
        }
    }


    @TypeChecked(TypeCheckingMode.SKIP)
    private static Node createNewFilterNode(Node oldFilterNode, List filters, Node xml, String bundleInstallRoot) {
        // If it had previously existed, then we can simply recreate it anew.
        // If not, then we need to search existing filters and see if any of them have roots higher in the tree.
        Node filter
        if (!oldFilterNode) {
            def possibleParentRoots = []
            String[] folderPaths = bundleInstallRoot.split('/') - '' // remove empty space entry
            String curRoot = ''
            folderPaths.each { String curFolder ->
                curRoot = "${curRoot}/${curFolder}"
                // prepend to the list so that more specific entries are first to avoid needing to reverse the list later
                possibleParentRoots.add(0, curRoot)
            }
            filter = filters.find { possibleParentRoots.contains(it.@root) } as Node
        }
        else {
            filter = null
        }

        // if no filter already exists with a root related to the bundleInstallRoot, then we can make the new one
        if (!filter)
            filter = new Node(xml, 'filter', [root: bundleInstallRoot])

        return filter
    }


    private static void writeReader(Reader xmlReader, Writer xmlWriter) {
        IOUtils.copy(xmlReader, xmlWriter)
        xmlWriter.flush()
    }


    @TypeChecked(TypeCheckingMode.SKIP)
    private static Node removeOldFilterNode(List filters, String bundleInstallRoot) {
        // remove the bundle install root node if it exists
        Node oldFilterNode = filters.find { it.@root == bundleInstallRoot } as Node
        filters.remove(oldFilterNode)
        return oldFilterNode
    }

    // **********************************************************************
    //
    // HELPER CLASSES
    //
    // **********************************************************************

    @Slf4j
    @TypeChecked
    private static class FilterDefinition {
        final Collection<String> jarFiles
        final Collection<String> allNativeProjectJarNames
        final Collection<String> dependencyJarFiles
        final String bundleInstallRoot
        final String nativeStartLevel
        final String dependencyStartLevel


        private FilterDefinition(Collection<String> jarFiles, Collection<String> allNativeProjectJarNames, Collection<String> dependencyJarFiles, String bundleInstallRoot, String nativeStartLevel, String dependencyStartLevel) {
            this.jarFiles = jarFiles
            this.allNativeProjectJarNames = allNativeProjectJarNames
            this.dependencyJarFiles = dependencyJarFiles
            this.bundleInstallRoot = bundleInstallRoot
            this.nativeStartLevel = nativeStartLevel
            this.dependencyStartLevel = dependencyStartLevel
        }


        @SuppressWarnings("UnnecessaryQualifiedReference")
        private static FilterDefinition create(Project project, Collection<String> jarFiles) {
            CqPackageConfiguration packageConfiguration = project.extensions.findByType(CqPackageConfiguration)

            String bundleInstallRoot = packageConfiguration.bundleInstallRoot
            String nativeStartLevel = packageConfiguration.nativeStartLevel
            String dependencyStartLevel = packageConfiguration.dependencyStartLevel

            Collection<String> allNativeProjectJarNames = getAllNativeProjectJarNames(project)
            Collection<String> dependencyJarFiles = (jarFiles as Set) - (allNativeProjectJarNames as Set)

            if (!allNativeProjectJarNames.isEmpty() && nativeStartLevel == null) {
                log.warn "Have native project jars, but no 'nativeStartLevel' set"
            }

            return new FilterDefinition(jarFiles, allNativeProjectJarNames, dependencyJarFiles, bundleInstallRoot, nativeStartLevel, dependencyStartLevel)
        }


        static Collection<String> getAllNativeProjectJarNames(Project project) {
            DependencySet dependencies = project.configurations.getByName('runtime').allDependencies
            Collection<ProjectDependency> projectDependencies = dependencies.findAll{ it in ProjectDependency }.collect { it as ProjectDependency }
            Collection<Project> allNativeOsgiProjects = projectDependencies.collect { it.getDependencyProject() }.findAll { Project proj ->
                proj.plugins.findPlugin('osgi') != null
            }
            Collection<String> allNativeProjectJarNames = allNativeOsgiProjects.collect { Project proj ->
                // 'osgi' projects are guaranteed to have a 'jar' task
                ((Jar)proj.tasks.getByName('jar')).archivePath.name
            }

            log.debug "Project native Jars: ${allNativeProjectJarNames}"

            return allNativeProjectJarNames
        }

    }

}
