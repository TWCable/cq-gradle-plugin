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

import com.twcable.gradle.http.SimpleHttpClient
import com.twcable.gradle.sling.SlingServerConfiguration
import com.twcable.gradle.sling.SlingServersConfiguration
import com.twcable.gradle.sling.osgi.SlingBundleConfiguration
import com.twcable.gradle.sling.osgi.SlingOsgiBundle
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.apache.commons.io.IOUtils
import org.apache.http.HttpResponse
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.auth.BasicScheme
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.jackrabbit.vault.packaging.PackageId
import org.apache.jackrabbit.vault.packaging.PackageManager
import org.apache.jackrabbit.vault.packaging.PackageProperties
import org.apache.jackrabbit.vault.packaging.impl.PackageManagerImpl
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedConfiguration

import javax.annotation.Nonnull
import javax.annotation.Nullable
import java.util.jar.JarFile
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile

import static com.twcable.gradle.cqpackage.Status.SERVER_TIMEOUT

/**
 * Has external dependencies on {@link SlingServersConfiguration}
 */
@Slf4j
@CompileStatic
@SuppressWarnings("GrMethodMayBeStatic")
class CqPackageHelper {
    public static final String NAME = 'cqPkgHelper'

    @SuppressWarnings("GrFinalVariableAccess")
    final Project project

    PackageManager packageManager = new PackageManagerImpl()


    CqPackageHelper(Project project) {
        if (project == null) throw new GradleException('project == null')
        this.project = project
    }


    @Deprecated
    private SlingBundleConfiguration slingBundleConfiguration() {
        return project.extensions.getByType(SlingBundleConfiguration)
    }


    @Deprecated
    private SlingServersConfiguration slingServersConfiguration() {
        project.extensions.getByType(SlingServersConfiguration)
    }


    @Deprecated
    long getRetryWaitMs() {
        return slingServersConfiguration().retryWaitMs
    }


    @Deprecated
    long getMaxWaitMs() {
        return slingServersConfiguration().maxWaitValidateBundlesMs
    }

    /**
     * Returns the name of the project, which is used as the name of the package.
     */
    @Deprecated
    String getPackageName() {
        project.name
    }


    void installPackage() {
        InstallPackage.install(packageName, slingServersConfiguration())
    }


    void uninstallPackage() {
        UninstallPackage.uninstall(packageName, slingServersConfiguration())
    }


    void deletePackage() {
        DeletePackage.delete(packageName, slingServersConfiguration())
    }

    /**
     * Asks the given server for all of the CQ Packages that it has, returning their information.
     *
     * @return null only if the server timed out (in which case "active" is disabled on the passed serverConfig)
     */
    @Nullable
    Collection<RuntimePackageProperties> listPackages(SlingServerConfiguration serverConfig) {
        if (!serverConfig.active) return null

        def packagesSF = ListPackages.listPackages(serverConfig, maxWaitMs, retryWaitMs)
        if (packagesSF.error != null) {
            switch (packagesSF.error) {
                case Status.SERVER_TIMEOUT: serverConfig.active = false; return null
                default: throw new GradleException("Unknown status from listing packages: ${packagesSF.error}")
            }
        }
        return packagesSF.value
    }

    /**
     * Asks the given server for its information for the package identified by {@link #getPackageName()}.
     *
     * @return null if the package is not found on the server
     *
     * @see #getPackageName()
     * @deprecated use {@link RuntimePackageProperties#packageProperties(SlingServerConfiguration, long, long, java.lang.String)}
     */
    @Nullable
    RuntimePackageProperties getPackageInfo(SlingServerConfiguration serverConfig) {
        if (!serverConfig.active) return null

        def packageInfoSF = RuntimePackageProperties.packageProperties(serverConfig, maxWaitMs, retryWaitMs, packageName)
        if (packageInfoSF.failed()) {
            switch (packageInfoSF.error) {
                case Status.SERVER_TIMEOUT: serverConfig.active = false; return null
                default: return null
            }
        }
        return packageInfoSF.value
    }


    void uploadPackage() {
        File sourceFile = UploadPackage.getThePackageFile(project)

        final slingServersConfiguration = slingServersConfiguration()
        final maxWaitMs = slingServersConfiguration.maxWaitValidateBundlesMs
        final retryWaitMs = slingServersConfiguration.retryWaitMs

        slingServersConfiguration.each { SlingServerConfiguration serverConfig ->
            UploadPackage.upload(sourceFile, false, serverConfig,
                maxWaitMs, retryWaitMs, packageManager)
        }
    }

    /**
     * Returns the properties (name, version, dependencies, etc.) for the provided VLT package file
     *
     * @throws IOException if it can't read the file
     */
    @Nonnull
    static PackageProperties packageProperties(File packageFile) throws IOException {
        final packageManager = new PackageManagerImpl()
        final vltPck = packageManager.open(packageFile)

        return vltPck.properties
    }

    /**
     * Returns the properties (name, version, dependencies, etc.) for the provided VLT package file
     *
     * @see #packageProperties(File)
     * @throws IOException if it can't read the file
     */
    @Nonnull
    static PackageId packageId(File packageFile) {
        return packageProperties(packageFile).getId()
    }

    /**
     * Returns the properties (name, version, dependencies, etc.) for the provided VLT package file
     *
     * @see #packageId(File)
     * @throws IOException if it can't read the file
     */
    @Nonnull
    static String packageName(File packageFile) {
        return packageId(packageFile).name
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
            if (packageInfo != null) { // package is installed
                slingServerConfiguration.slingSupport.doHttp { httpClient ->
                    def namesFromDownloadedPackage = symbolicNamesFromDownloadedPackage(slingServerConfiguration)
                    slingBundle.uninstallAllBundles(namesFromDownloadedPackage, httpClient,
                        slingServerConfiguration, slingServersConfiguration.uninstallBundlesPredicate)
                }
            }
            else {
                log.info("${packageName} is not on ${slingServerConfiguration.name}")
            }
        }
    }


    List<String> symbolicNamesFromDownloadedPackage(SlingServerConfiguration serverConfig) {
        try {
            File downloadDir = new File("${project.buildDir}/tmp")
            downloadDir.mkdirs()
            def packageInfo = getPackageInfo(serverConfig)
            String packageFilename = packageInfo.downloadName
            packageFilename = "${downloadDir}/${packageFilename}".replace("downloadName=", "")
            final path = packageInfo.path
            final zipUri = URI.create("${serverConfig.downloadUri}?_charset_=utf8&${path}")

            log.info("Filename from package list: $packageFilename")
            log.info("Filepath from package list: $path")
            log.info("Zip URI from package list: $zipUri")

            def file = downloadFile(packageFilename, zipUri, serverConfig)
            def zipFile = new ZipFile(file)
            def zipEntries = zipFile.entries().findAll { ZipEntry entry -> entry.name.endsWith(".jar") } as List

            def symbolicNames = []
            zipEntries.each {
                def filename = it.toString()
                def actualFileName = filename.split("/").last()
                def entry = zipFile.getEntry(filename)
                log.debug("Unzipping to ${project.buildDir}/tmp/${actualFileName}...")
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
                    log.warn "${file} contains a non-OSGi jar file: ${jarFile}"
                }
                log.debug("Cleaning up. Deleting $jarFile...")
                jarFile.delete()
            }
            log.debug("Cleaning up. Deleting $file")
            file.delete()
            log.info("Bundles from downloaded zipfile: $symbolicNames")
            return symbolicNames
        }
        catch (Exception exp) {
            log.error "There was a problem getting symbolic names from downloaded package \"${serverConfig}\""
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
