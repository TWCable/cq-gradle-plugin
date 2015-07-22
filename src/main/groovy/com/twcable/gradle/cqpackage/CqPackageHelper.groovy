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

import com.twcable.gradle.http.HttpResponse as HttpResp
import com.twcable.gradle.http.SimpleHttpClient
import com.twcable.gradle.sling.SlingServerConfiguration
import com.twcable.gradle.sling.SlingServersConfiguration
import com.twcable.gradle.sling.osgi.SlingBundleConfiguration
import com.twcable.gradle.sling.osgi.SlingOsgiBundle
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import org.apache.commons.io.IOUtils
import org.apache.http.HttpResponse
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.entity.mime.content.FileBody
import org.apache.http.impl.auth.BasicScheme
import org.apache.http.impl.client.DefaultHttpClient
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedConfiguration
import org.slf4j.Logger

import javax.annotation.Nonnull
import javax.annotation.Nullable
import java.util.jar.JarFile
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile

import static com.twcable.gradle.sling.SlingSupport.block
import static java.net.HttpURLConnection.HTTP_CLIENT_TIMEOUT
import static java.net.HttpURLConnection.HTTP_OK

/**
 * Has external dependencies on {@link SlingServersConfiguration}
 */
@CompileStatic
@SuppressWarnings("GrMethodMayBeStatic")
class CqPackageHelper {
    public static final String NAME = 'cqPkgHelper'

    @SuppressWarnings("GrFinalVariableAccess")
    final Project project

    JsonSlurper jsonSlurper = new JsonSlurper()

    CqPackageCommand uninstallCommand =
        new CqPackageCommand('uninstall', this, { SlingServerConfiguration serverConfig, String jsonMsg ->
            Logger logger = ((CqPackageCommand)delegate).logger
            if (jsonMsg.contains('No snapshot present')) {
                logger.info "${packageName} is not installed on ${serverConfig.name}"
                return true
            }

            // neither of these should happen since we check for the existence of the package first, but...
            if (jsonMsg == 'no package') {
                logger.info "${packageName} is not on ${serverConfig.name}"
                return true
            }
            if (jsonMsg == '') {
                logger.info "${packageName} is not installed on ${serverConfig.name}"
                return true
            }
            return false
        }, true)


    CqPackageHelper(Project project) {
        if (project == null) throw new GradleException('project == null')
        this.project = project
    }


    private SlingBundleConfiguration slingBundleConfiguration() {
        return project.extensions.getByType(SlingBundleConfiguration)
    }


    private SlingServersConfiguration slingServersConfiguration() {
        project.extensions.getByType(SlingServersConfiguration)
    }


    Logger getLogger() {
        return project.logger
    }


    long getRetryWaitMs() {
        return slingServersConfiguration().retryWaitMs
    }


    long getMaxWaitMs() {
        return slingServersConfiguration().maxWaitValidateBundlesMs
    }

    /**
     * Returns the name of the project, which is used as the name of the package.
     */
    String getPackageName() {
        project.name
    }


    void installPackage() {
        def command = new CqPackageCommand('install', this, { SlingServerConfiguration serverConfig, String jsonMsg ->
            return false
        }, false)

        slingServersConfiguration().each { serverConfig ->
            command.commandPackage(serverConfig)
        }
    }


    void uninstallPackage() {
        uninstallCommand.run()
    }


    void deletePackage() {
        def command = new CqPackageCommand('delete', this, { SlingServerConfiguration serverConfig, String jsonMsg ->
            if (jsonMsg == 'no package') {
                logger.info "${packageName} is not on ${serverConfig.name}"
                return true
            }
            return false
        }, true)

        slingServersConfiguration().each { serverConfig ->
            command.commandPackage(serverConfig)
        }
    }

    /**
     * Asks the given server for all of the CQ Packages that it has, returning their information.
     *
     * @return null only if the server timed out (in which case "active" is disabled on the passed serverConfig)
     */
    @Nullable
    Map listPackages(SlingServerConfiguration serverConfig) {
        if (!serverConfig.active) return null

        final uri = serverConfig.packageListUri
        final slingSupport = serverConfig.slingSupport

        HttpResp resp
        block(maxWaitMs, { ![HTTP_OK, HTTP_CLIENT_TIMEOUT].contains(resp?.code) }, {
            resp = slingSupport.doHttp { SimpleHttpClient httpClient ->
                slingSupport.doGet(uri, httpClient)
            }
        } as Closure<Void>, retryWaitMs)

        if (resp.code == HTTP_OK) {
            final String jsonStr = resp.body
            return jsonSlurper.parseText(jsonStr) as Map
        }
        else if (resp.code == HTTP_CLIENT_TIMEOUT) {
            serverConfig.active = false
            return null
        }
        else {
            throw new GradleException("Could not list the packages on ${serverConfig.name}: ${resp.code} - ${resp.body}")
        }
    }

    /**
     * Asks the given server for its information for the package identified by {@link #getPackageName()}.
     *
     * @return null if the package is not found on the server
     *
     * @see #getPackageName()
     */
    @Nullable
    Map getPackageInfo(SlingServerConfiguration serverConfig) {
        def packagesInfo = listPackages(serverConfig)
        if (packagesInfo != null) {
            return packagesInfo.results.find { Map packageInfo ->
                packageInfo.name == packageName
            } as Map
        }
        else {
            return null
        }
    }


    void uploadPackage() {
        File sourceFile = getPackageFile()

        final slingServersConfiguration = slingServersConfiguration()
        slingServersConfiguration.each { SlingServerConfiguration serverConfig ->
            uploadThePackage(sourceFile, serverConfig)
        }
    }

    /**
     * Returns the CQ Package file to use.
     *
     * If a System Property of "package" is set, that is used. Otherwise the output of
     * the 'createPackage' task is used.
     */
    @Nonnull
    File getPackageFile() {
        def packageProperty = System.getProperty('package')
        if (packageProperty != null) {
            return new File(packageProperty)
        }

        def file = CreatePackageTask.from(project).archivePath
        project.logger.info("No remote package passed in. Using createPackage zip: ${file}")
        return file
    }


    protected void uploadThePackage(@Nonnull File sourceFile, @Nonnull SlingServerConfiguration serverConfig) {
        final uri = URI.create("${serverConfig.packageControlUri}/?cmd=upload")

        def slingSupport = serverConfig.slingSupport

        HttpResp resp
        block(maxWaitMs, { ![HTTP_OK, HTTP_CLIENT_TIMEOUT].contains(resp?.code) }, {
            resp = slingSupport.doHttp { SimpleHttpClient httpClient ->
                doPost(uri, ['force': 'true', 'package': new FileBody(sourceFile, 'application/java-archive')], httpClient)
            }
        } as Closure<Void>, retryWaitMs)

        if (resp.code == HTTP_OK) {
            final String jsonStr = resp.body
            final json = jsonSlurper.parseText(jsonStr) as Map
            if (json.success == true) {
                logger.info("'${sourceFile}': ${json.msg}")
            }
            else {
                uploadFailed(sourceFile, (String)json.msg)
            }
        }
        else if (resp.code == HTTP_CLIENT_TIMEOUT) {
            logger.error(resp.body)
        }
        else {
            throw new GradleException("Could not upload '${sourceFile}': ${resp.code} - ${resp.body}")
        }
    }


    private void uploadFailed(File sourceFile, String msg) {
        if (msg.contains('Package already exists:')) {
            try {
                logger.warn("Uninstalling previous '${sourceFile.name}'")
                uninstallPackage()
            }
            catch (GradleException e) {
                logger.warn("Problem uninstalling '${sourceFile.name}': ${e.message}")
            }
            deletePackage()
            uploadPackage()
        }
        else {
            throw new GradleException("Could not upload '${sourceFile}': ${msg}")
        }
    }


    void checkActiveBundles(String groupProperty) {
        def slingBundleConfiguration = slingBundleConfiguration()
        def slingBundle = new SlingOsgiBundle(slingBundleConfiguration)
        def slingServersConfiguration = slingServersConfiguration()

        slingServersConfiguration.each { slingServerConfiguration ->
            def slingSupport = slingServerConfiguration.slingSupport

            slingSupport.doHttp { httpClient ->
                slingBundle.checkActiveBundles(groupProperty, httpClient, slingServerConfiguration)
            }
        }
    }

    /**
     * Calls {@link SlingOsgiBundle#startInactiveBundles(SimpleHttpClient, SlingServerConfiguration)} for
     * each server in {@link SlingServersConfiguration}
     */
    void startInactiveBundles() {
        SlingOsgiBundle slingBundle = new SlingOsgiBundle(slingBundleConfiguration())
        slingServersConfiguration().each { SlingServerConfiguration slingServerConfiguration ->
            slingServerConfiguration.slingSupport.doHttp { SimpleHttpClient httpClient ->
                slingBundle.startInactiveBundles(httpClient, slingServerConfiguration)
            }
        }
    }


    void validateBundles(Configuration configuration) {
        def resolvedConfiguration = configuration.resolvedConfiguration
        def slingBundleConfiguration = slingBundleConfiguration()
        def slingBundle = new SlingOsgiBundle(slingBundleConfiguration)
        def slingServersConfiguration = slingServersConfiguration()

        slingServersConfiguration.each { slingServerConfiguration ->
            def slingSupport = slingServerConfiguration.slingSupport

            slingSupport.doHttp { httpClient ->
                def symbolicNamesList = buildSymbolicNamesList(resolvedConfiguration)
                slingBundle.validateAllBundles(symbolicNamesList, httpClient, slingServerConfiguration)
            }
        }
    }


    void validateRemoteBundles() {
        def slingBundleConfiguration = slingBundleConfiguration()
        def slingBundle = new SlingOsgiBundle(slingBundleConfiguration)
        def slingServersConfiguration = slingServersConfiguration()

        slingServersConfiguration.each { slingServerConfiguration ->
            def slingSupport = slingServerConfiguration.slingSupport

            slingSupport.doHttp { httpClient ->
                def namesFromDownloadedPackage = symbolicNamesFromDownloadedPackage(slingServerConfiguration)

                slingBundle.validateAllBundles(namesFromDownloadedPackage, httpClient, slingServerConfiguration)
            }
        }
    }


    void uninstallBundles() {
        def slingBundleConfiguration = slingBundleConfiguration()
        def slingBundle = new SlingOsgiBundle(slingBundleConfiguration)
        def slingServersConfiguration = slingServersConfiguration()

        slingServersConfiguration.each { slingServerConfiguration ->
            def packageInfo = getPackageInfo(slingServerConfiguration)
            if (packageInfo != null) {
                slingServerConfiguration.slingSupport.doHttp { httpClient ->
                    def namesFromDownloadedPackage = symbolicNamesFromDownloadedPackage(slingServerConfiguration)
                    slingBundle.uninstallAllBundles(namesFromDownloadedPackage, httpClient,
                        slingServerConfiguration, slingServersConfiguration.uninstallBundlesPredicate)
                }
            }
            else {
                logger.info("${packageName} is not on ${slingServerConfiguration.name}")
            }
        }
    }


    List<String> symbolicNamesFromDownloadedPackage(SlingServerConfiguration serverConfig) {
        try {
            File downloadDir = new File("${project.buildDir}/tmp")
            downloadDir.mkdirs()
            String filename = getPackageInfo(serverConfig).find { it.key == 'downloadName' } as String
            filename = "${downloadDir}/${filename}".replace("downloadName=", "")
            final path = getPackageInfo(serverConfig).find { it.key == 'path' }
            final zipUri = URI.create("${serverConfig.downloadUri}?_charset_=utf8&$path")

            logger.info("Filename from package list: $filename")
            logger.info("Filepath from package list: $path")
            logger.info("Zip URI from package list: $zipUri")

            def file = downloadFile(filename, zipUri, serverConfig)
            def zipFile = new ZipFile(file)
            def zipEntries = zipFile.entries().findAll { ZipEntry entry -> entry.name.endsWith(".jar") } as List

            def symbolicNames = []
            zipEntries.each {
                filename = it.toString()
                def actualFileName = filename.split("/").last()
                def entry = zipFile.getEntry(filename)
                logger.debug("Unzipping to ${project.buildDir}/tmp/${actualFileName}...")
                def is = zipFile.getInputStream(entry)
                def jarFile = new File("${project.buildDir}/tmp/${actualFileName}")
                def out = new FileOutputStream(jarFile)

                try {
                    IOUtils.copy(is, out)
                }
                finally {
                    is.close()
                    out.close()
                }

                def bundleSymbolicName = getSymbolicName(jarFile)
                if (bundleSymbolicName != null) {
                    symbolicNames.add(bundleSymbolicName)
                }
                else {
                    logger.warn "${file} contains a non-OSGi jar file: ${jarFile}"
                }
                logger.debug("Cleaning up. Deleting $jarFile...")
                jarFile.delete()
            }
            logger.debug("Cleaning up. Deleting $file")
            file.delete()
            logger.info("Bundles from downloaded zipfile: $symbolicNames")
            return symbolicNames
        }
        catch (Exception exp) {
            logger.error "There was a problem getting symbolic names from downloaded package \"${serverConfig}\""
            throw exp
        }
    }


    @Nonnull
    private File downloadFile(String filename, URI uri, SlingServerConfiguration serverConfig) {
        HttpGet httpGet = new HttpGet(uri)
        httpGet.addHeader(BasicScheme.authenticate(
            new UsernamePasswordCredentials(serverConfig.username, serverConfig.password), "UTF-8", false))

        HttpClient client = new DefaultHttpClient()
        HttpResponse httpResponse = client.execute(httpGet)
        InputStream is = httpResponse.entity.content
        File file = new File(filename)
        OutputStream out = new FileOutputStream(file)

        try {
            IOUtils.copy(is, out)
        }
        finally {
            is.close()
            out.close()
        }

        return file
    }

    /**
     * For the given "resolved configuration" (all of the artifacts in a Gradle Configuration, such as "compile"),
     * return all of the bundle symbolic names in the bundles.
     */
    @Nonnull
    private List buildSymbolicNamesList(ResolvedConfiguration resolvedConfiguration) {
        return resolvedConfiguration.resolvedArtifacts.collect { ra ->
            getSymbolicName(ra.file)
        }
    }

    /**
     * Get the OSGi bundle symbolic name from the file's metadata.
     * @return null if the file is not an OSGi bundle
     */
    @Nullable
    static String getSymbolicName(File file) {
        try {
            JarFile jar = new JarFile(file)
            Manifest manifest = jar.manifest
            final entries = manifest.mainAttributes
            return entries.getValue('Bundle-SymbolicName') as String
        }
        catch (ZipException exp) {
            throw new IllegalStateException("Trying to open '${file}'", exp)
        }
    }

    /**
     * Does the given JAR file have basic OSGi metadata? (Specifically "Bundle-SymbolicName")
     */
    static boolean isOsgiFile(File file) {
        return getSymbolicName(file) != null
    }

}
