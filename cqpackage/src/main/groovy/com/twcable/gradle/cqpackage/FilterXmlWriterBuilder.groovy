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
    private String bundleInstallRoot

    /**
     * The Project to get default information from.
     * Only used if another property needs to compute a default value.
     */
    @Nonnull
    FilterXmlWriterBuilder(Project project) {
        if (project == null) throw new IllegalArgumentException("project == null")
        this.project = project
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

        def filterDefinition = createFilterDescription()

        return new FilterXmlWriter(this.inReader, filterDefinition, this.bundleInstallRoot, this.outWriter)
    }


    private void ensureBundleInstallRoot() {
        if (bundleInstallRoot == null) {
            bundleInstallRoot = project.tasks.withType(CreatePackageTask).first().bundleInstallRoot
        }
    }


    private void ensureConfiguration() {
        if (configuration == null) {
            configuration = CqPackagePlugin.cqPackageDependencies(project)
        }
    }


    private void ensureOutWriter() {
        if (this.outWriter != null) return

        if (outFile == null) {
            outFile = new File(project.buildDir, "/tmp/filter.xml").canonicalFile
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
            inFile = project.file('src/main/content/META-INF/vault/filter.xml')
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
    private FilterDefinition createFilterDescription() {
        def bundleFiles = CreatePackageTask.from(project).bundleFiles
        return FilterDefinition.create(bundleFiles)
    }

}
