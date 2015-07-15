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
import org.apache.commons.io.IOUtils

import javax.annotation.Nonnull

/**
 * Provides a way to (re)write the filter.xml file to include the bundles in the package.
 *
 * @see FilterXmlWriter#builder()
 * @see FilterXmlWriter#run()
 */
@Slf4j
@CompileStatic
class FilterXmlWriter {
    private final Reader inReader
    private final Writer outWriter
    private final FilterDefinition filterDefinition
    private final String bundleInstallRoot


    protected FilterXmlWriter(Reader inReader, FilterDefinition filterDefinition,
                              String bundleInstallRoot, Writer outWriter) {
        this.inReader = inReader
        this.filterDefinition = filterDefinition
        this.outWriter = outWriter
        this.bundleInstallRoot = bundleInstallRoot
    }

    /**
     * Returns a {@link FilterXmlWriterBuilder} for constructing an instance of FilterXmlWriter.
     */
    @Nonnull
    static FilterXmlWriterBuilder builder() {
        return new FilterXmlWriterBuilder()
    }

    /**
     * Reads the input, transforms the XML, and writes the output. All options are set on the {@link FilterXmlWriterBuilder}.
     */
    void run() {
        def newXml = addJarsToXml(inReader, bundleInstallRoot, filterDefinition)
        writeReader(newXml, outWriter)
    }


    private static Reader addJarsToXml(Reader filterXmlReader, String bundleInstallRoot,
                                       FilterDefinition filterDefinition) {
        def xml = new XmlParser().parse(filterXmlReader)
        attachJarsToFilterNode(xml, bundleInstallRoot, filterDefinition)

        return xmlToReader(xml)
    }


    private static Reader xmlToReader(Node xml) {
        StringWriter writer = new StringWriter()
        new XmlNodePrinter(new PrintWriter(writer)).print(xml)
        return new StringReader(writer.toString())
    }


    private
    static void attachJarsToFilterNode(Node filter, String bundleInstallRoot, FilterDefinition filterDefinition) {
        def theJarPatterns = jarPatterns(filterDefinition.jarNames, bundleInstallRoot)
        log.debug "theJarPatterns: ${theJarPatterns}"
        attachFilterNodes(filter, theJarPatterns)
    }


    private static void attachFilterNodes(Node parentNode, Collection<String> filterPatterns) {
        filterPatterns.each { String filerPattern ->
            // creating the Node attaches it to its parent
            new Node(parentNode, 'filter', [root: filerPattern])
        }
    }


    private static void writeReader(Reader xmlReader, Writer xmlWriter) {
        log.info("Writing new XML")
        IOUtils.copy(xmlReader, (Writer)xmlWriter)
        xmlWriter.flush()
    }


    private static Collection<String> jarPatterns(Collection<String> jarFiles, String bundleInstallRoot) {
        jarFiles.collect { String jarFileName ->
            "${bundleInstallRoot}/${jarFileName}"
        }
    }

}
