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

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration

import javax.annotation.Nonnull

/**
 * Constructs an instance of {@link FilterXmlWriter} using a "fluent interface".
 *
 * @see FilterXmlWriterBuilder#build()
 */
@Slf4j
@CompileStatic
@SuppressWarnings("GroovyUnusedDeclaration")
class FilterXmlWriterBuilder {
    private Project project
    private File inFile
    private Reader inReader
    private Writer outWriter
    private File outFile
    private Configuration configuration
    private boolean includeProjectBundles = true
    private boolean includeNonProjectBundles = true
    private String bundleInstallRoot

    /**
     * The Project to get default information from.
     * Only used if another property needs to compute a default value.
     */
    @Nonnull
    FilterXmlWriterBuilder project(Project project) {
        this.project = project
        return this
    }

    /**
     * The {@link Configuration} to get dependencies from.
     * Defaults to {@link CqPackagePlugin#cqPackageDependencies(Project)} if "project" has been set.
     */
    @Nonnull
    FilterXmlWriterBuilder configuration(Configuration configuration) {
        this.configuration = configuration
        return this
    }

    /**
     * Should the filter.xml include bundles that are project-dependencies?
     * Defaults to true.
     */
    @Nonnull
    FilterXmlWriterBuilder includeProjectBundles(boolean includeProjectBundles) {
        this.includeProjectBundles = includeProjectBundles
        return this
    }

    /**
     * Should the filter.xml include bundles that are not project-dependencies?
     * Defaults to true.
     */
    @Nonnull
    FilterXmlWriterBuilder includeNonProjectBundles(boolean includeNonProjectBundles) {
        this.includeNonProjectBundles = includeNonProjectBundles
        return this
    }

    /**
     * The path under which to install the bundles in the JCR.
     * Defaults to {@link CreatePackageTask#getBundleInstallRoot()} if "project" has been set.
     */
    @Nonnull
    FilterXmlWriterBuilder bundleInstallRoot(String bundleInstallRoot) {
        this.bundleInstallRoot = bundleInstallRoot
        return this
    }

    /**
     * The File to get the source XML from.
     * Defaults to the file "src/main/content/META-INF/vault/filter.xml" if "project" has been set.
     * Exclusive with {@link #inReader(Reader)}.
     */
    @Nonnull
    FilterXmlWriterBuilder inFile(File inFile) {
        if (inFile != null && this.inReader != null) throw new IllegalArgumentException("Already set inReader")
        this.inFile = inFile
        return this
    }

    /**
     * The File to write the resulting XML to.
     * Defaults to the file "build/tmp/filter.xml" if "project" has been set.
     * Exclusive with {@link #outWriter(Writer)}.
     */
    @Nonnull
    FilterXmlWriterBuilder outFile(File outFile) {
        if (outFile != null && this.outWriter != null) throw new IllegalArgumentException("Already set outWriter")
        this.outFile = outFile
        return this
    }

    /**
     * The Reader to get the source XML from.
     * Defaults to the file "src/main/content/META-INF/vault/filter.xml" if "project" has been set.
     * Exclusive with {@link #inFile(File)}.
     */
    @Nonnull
    FilterXmlWriterBuilder inReader(Reader inReader) {
        if (inReader != null && inFile != null) throw new IllegalArgumentException("Already set inFile")
        this.inReader = inReader
        return this
    }

    /**
     * The Writer to write the resulting XML to.
     * Defaults to the file "build/tmp/filter.xml" if "project" has been set.
     * Exclusive with {@link #outFile(File)}.
     */
    @Nonnull
    FilterXmlWriterBuilder outWriter(Writer outWriter) {
        if (outWriter != null && outFile != null) throw new IllegalArgumentException("Already set outFile")
        this.outWriter = outWriter
        return this
    }

    /**
     * Builds an instance of {@link FilterXmlWriter} based on the properties set.
     */
    @Nonnull
    FilterXmlWriter build() {
        ensureInReader()
        ensureOutWriter()
        ensureConfiguration()
        ensureBundleInstallRoot()

        def filterDefinition = createFilterDescription(configuration)

        return new FilterXmlWriter(this.inReader, filterDefinition, this.bundleInstallRoot, this.outWriter)
    }


    private void ensureBundleInstallRoot() {
        if (bundleInstallRoot == null) {
            if (project != null) {
                bundleInstallRoot = project.tasks.withType(CreatePackageTask).first().bundleInstallRoot
            }
            else {
                throw new IllegalStateException("Could not find bundle install root: Missing Project")
            }
        }
    }


    private void ensureConfiguration() {
        if (configuration == null) {
            if (project != null) {
                configuration = CqPackagePlugin.cqPackageDependencies(project)
            }
            else {
                throw new IllegalStateException("Could not read configuration: Missing Project")
            }
        }
    }


    private void ensureOutWriter() {
        if (this.outWriter != null) return

        if (outFile == null) {
            if (project != null) {
                outFile = new File(project.buildDir, "/tmp/filter.xml").canonicalFile
            }
            else {
                throw new IllegalStateException("Could not create outFile: Missing Project")
            }
        }

        if (!outFile.exists()) {
            // make sure the parent directory exists
            outFile.parentFile.mkdirs()
        }

        outFile = outFile.canonicalFile

        this.outWriter = new FileWriter(outFile)
    }


    private void ensureInReader() {
        if (this.inReader != null) return

        if (inFile == null) {
            if (project != null) {
                inFile = project.file('src/main/content/META-INF/vault/filter.xml')
            }
            else {
                throw new IllegalStateException("Could not read inFile: Missing Project")
            }
        }
        inFile = inFile.canonicalFile

        if (inFile.exists()) {
            this.inReader = new FileReader(inFile)
        }
        else {
            log.warn("Missing input filer.xml: ${inFile}")
            this.inReader = new StringReader("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<workspaceFilter version=\"1.0\"/>\n")
        }
    }


    @Nonnull
    private FilterDefinition createFilterDescription(Configuration configuration) {
        FilterDefinition filterDefinition
        if (includeProjectBundles) {
            if (includeNonProjectBundles)
                filterDefinition = FilterDefinition.createAllBundles(project, configuration)
            else
                filterDefinition = FilterDefinition.createProjectBundles(project, configuration)
        }
        else { // includeProjectBundles == false
            if (includeNonProjectBundles)
                filterDefinition = FilterDefinition.createNonProjectBundles(configuration)
            else
                filterDefinition = FilterDefinition.createNoBundles()
        }
        return filterDefinition
    }

}
