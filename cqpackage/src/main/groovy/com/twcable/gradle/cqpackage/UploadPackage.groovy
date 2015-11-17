package com.twcable.gradle.cqpackage

import com.twcable.gradle.sling.SlingServerConfiguration
import com.twcable.gradle.sling.SlingServersConfiguration
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.apache.http.entity.mime.content.FileBody
import org.apache.jackrabbit.vault.packaging.PackageManager
import org.apache.jackrabbit.vault.packaging.impl.PackageManagerImpl
import org.gradle.api.GradleException
import org.gradle.api.Project

import javax.annotation.Nonnull

import static com.twcable.gradle.cqpackage.PackageStatus.NO_PACKAGE
import static com.twcable.gradle.cqpackage.PackageStatus.UNKNOWN
import static com.twcable.gradle.cqpackage.PackageStatus.UNRESOLVED_DEPENDENCIES
import static com.twcable.gradle.cqpackage.Status.OK
import static com.twcable.gradle.cqpackage.Status.SERVER_INACTIVE
import static com.twcable.gradle.cqpackage.Status.SERVER_TIMEOUT

@Slf4j
@CompileStatic
class UploadPackage {

    private static final CqPackageCommand.SuccessFalseHandler falseStatusHandler =
        { SlingServerConfiguration sc, String jsonMsg ->
            switch (jsonMsg) {
                case 'no package':
                case '':
                    // neither of these should happen since we check for the existence of the package first, but...
                    return NO_PACKAGE
                default:
                    return UNKNOWN
            }
        } as CqPackageCommand.SuccessFalseHandler

    /**
     * Iterates through all of the servers in "slingServersConfiguration" and installs the given package on them.
     *
     * @see #upload(File, boolean, SlingServerConfiguration, long, long, PackageManager)
     * @see #consumeStatus(Status, String, SlingServerConfiguration)
     */
    static void upload(File packageFile,
                       SlingServersConfiguration slingServersConfiguration,
                       PackageManager packageManager) {
        long maxWaitMs = slingServersConfiguration.maxWaitValidateBundlesMs
        long retryWaitMs = slingServersConfiguration.retryWaitMs

        if (packageManager == null) packageManager = new PackageManagerImpl()

        slingServersConfiguration.each { serverConfig ->
            def status = upload(packageFile, false, serverConfig, maxWaitMs, retryWaitMs, packageManager)
            consumeStatus(status, packageFile.name, serverConfig)
        }
    }

    /**
     * "Consumes" the status, mostly to appropriately log what happened.
     */
    static void consumeStatus(Status status, String packageName, SlingServerConfiguration serverConfig) {
        switch (status) {
            case OK:
                log.info("\"${packageName}\" is installed on ${serverConfig.name}"); break
            case NO_PACKAGE:
                throw new GradleException("\"${packageName}\" is not currently on ${serverConfig.name}, so can not install it")
            case SERVER_INACTIVE:
            case SERVER_TIMEOUT:
                break // ignore
            default:
                throw new IllegalArgumentException("Unknown status ${status} when trying to install ${packageName} on ${serverConfig.name}")
        }
    }

    /**
     * Installs the given package using the provided server configuration.
     *
     * @param packageName the name of the package to install
     * @param force should this overwrite an existing package of exactly the same filename?
     * @param serverConfig the configuration of the server to install on
     * @param maxWaitMs the maximum amount of time, in milliseconds, to wait for the install to finish
     * @param retryWaitMs the amount of time to wait while polling for status updates
     *
     * @return the {@link PackageStatus} of doing the install
     */
    @Nonnull
    static Status upload(File packageFile, boolean force, SlingServerConfiguration serverConfig,
                         long maxWaitMs, long retryWaitMs, PackageManager packageManager) {
        if (packageManager == null) packageManager = new PackageManagerImpl()

        /*
        force = true, package exists, same version


        10:54:01.065 [main] DEBUG org.apache.http.wire - << "{"success":true,"msg":"Package uploaded","path":"/etc/packages/testing/testpackage-1.0.1.zip"}"
10:54:01.066 [main] DEBUG c.t.g.http.DefaultSimpleHttpClient - status code: 200; {"success":true,"msg":"Package uploaded","path":"/etc/packages/testing/testpackage-1.0.1.zip"}
10:54:01.121 [main] INFO  c.t.g.cqpackage.CqPackageCommand - 'null': Package uploaded
         */

        /*
        force = false, package exists, same version


10:56:12.402 [main] DEBUG org.apache.http.wire - << "{"success":false,"msg":"Package already exists: /etc/packages/testing/testpackage-1.0.1.zip"}"
10:56:12.402 [main] DEBUG c.t.g.http.DefaultSimpleHttpClient - status code: 200; {"success":false,"msg":"Package already exists: /etc/packages/testing/testpackage-1.0.1.zip"}
         */

        /*
        force = false, package does not exist, missing dependency


11:12:36.505 [main] DEBUG org.apache.http.wire - << "{"success":true,"msg":"Package uploaded","path":"/etc/packages/testing/testpackage-1.0.1.zip"}"
11:12:36.506 [main] DEBUG c.t.g.http.DefaultSimpleHttpClient - status code: 200; {"success":true,"msg":"Package uploaded","path":"/etc/packages/testing/testpackage-1.0.1.zip"}

in other words, there's no indication it's missing its dependency at this point
         */

        /*
        force = false, package exists, different version


10:56:12.402 [main] DEBUG org.apache.http.wire - << "{"success":false,"msg":"Package already exists: /etc/packages/testing/testpackage-1.0.1.zip"}"
10:56:12.402 [main] DEBUG c.t.g.http.DefaultSimpleHttpClient - status code: 200; {"success":false,"msg":"Package already exists: /etc/packages/testing/testpackage-1.0.1.zip"}
         */

        /*
        force = false, not a valid package file


12:14:14.978 [main] DEBUG org.apache.http.wire - << "{"success":false,"msg":"Zip File is not a content package. Missing 'jcr_root'."}"
12:14:14.979 [main] DEBUG c.t.g.http.DefaultSimpleHttpClient - status code: 200; {"success":false,"msg":"Zip File is not a content package. Missing 'jcr_root'."}
         */

        /*
        force = false, not a zip file


12:16:34.254 [main] DEBUG org.apache.http.wire - << "{"success":false,"msg":"error in opening zip file"}"
12:16:34.254 [main] DEBUG c.t.g.http.DefaultSimpleHttpClient - status code: 200; {"success":false,"msg":"error in opening zip file"}
         */
        final packageName = packageManager.open(packageFile).id.name
        final postParams = ['force': Boolean.toString(force), 'package': new FileBody(packageFile, 'application/zip')]
        final uploadStatus = CqPackageCommand.doCommand("upload", packageName, serverConfig, maxWaitMs, retryWaitMs, postParams, falseStatusHandler)
        if (uploadStatus == OK) {
            final packageInfoSF = RuntimePackageProperties.packageProperties(serverConfig, maxWaitMs, retryWaitMs, packageName)
            if (packageInfoSF.failed()) return packageInfoSF.error
            return packageInfoSF.value.hasUnresolvedDependencies() ? UNRESOLVED_DEPENDENCIES : OK
        }
        return uploadStatus
    }

    /**
     * Returns the CQ Package file to use.
     *
     * If a System Property of "package" is set, that is used. Otherwise the output of
     * the 'createPackage' task is used.
     */
    @Nonnull
    static File getThePackageFile(Project project) {
        def packageProperty = System.getProperty('package')
        if (packageProperty != null) {
            return new File(packageProperty)
        }

        def file = CreatePackageTask.from(project).archivePath
        log.info("No remote package passed in. Using createPackage zip: ${file}")
        return file
    }

}
