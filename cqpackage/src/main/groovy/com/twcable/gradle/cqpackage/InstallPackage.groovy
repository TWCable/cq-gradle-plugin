package com.twcable.gradle.cqpackage

import com.twcable.gradle.sling.SlingServerConfiguration
import com.twcable.gradle.sling.SlingServersConfiguration
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.gradle.api.GradleException

import javax.annotation.Nonnull

import static com.twcable.gradle.cqpackage.PackageStatus.NO_PACKAGE
import static com.twcable.gradle.cqpackage.PackageStatus.UNKNOWN
import static com.twcable.gradle.cqpackage.Status.OK
import static com.twcable.gradle.cqpackage.Status.SERVER_INACTIVE
import static com.twcable.gradle.cqpackage.Status.SERVER_TIMEOUT

@Slf4j
@CompileStatic
class InstallPackage {

    private static final CqPackageCommand.SuccessFalseHandler falseStatusHandler =
        { SlingServerConfiguration sc, String jsonMsg ->
            switch (jsonMsg) {
                case 'no package':
                case '':
                    // neither of these should happen since we check for the existence of the package first, but...
                    return PackageStatus.NO_PACKAGE
                default:
                    return Status.UNKNOWN
            }
        } as CqPackageCommand.SuccessFalseHandler

    /**
     * Iterates through all of the servers in "slingServersConfiguration" and installs the given package on them.
     *
     * @see #install(String, SlingServerConfiguration, long, long)
     * @see #consumeStatus(Status, String, SlingServerConfiguration)
     */
    static void install(String packageName, SlingServersConfiguration slingServersConfiguration) {
        slingServersConfiguration.each { serverConfig ->
            long maxWaitMs = slingServersConfiguration.maxWaitValidateBundlesMs
            long retryWaitMs = slingServersConfiguration.retryWaitMs
            def status = install(packageName, serverConfig, maxWaitMs, retryWaitMs)
            consumeStatus(status, packageName, serverConfig)
        }
    }

    /**
     * "Consumes" the status, mostly to appropriately log what happened.
     */
    static void consumeStatus(Status status, String packageName, SlingServerConfiguration serverConfig) {
        switch (status) {
            case Status.OK:
                log.info("\"${packageName}\" is installed on ${serverConfig.name}"); break
            case PackageStatus.NO_PACKAGE:
                throw new GradleException("\"${packageName}\" is not currently on ${serverConfig.name}, so can not install it")
            case Status.SERVER_INACTIVE:
            case Status.SERVER_TIMEOUT:
                break // ignore
            default:
                throw new IllegalArgumentException("Unknown status ${status} when trying to install ${packageName} on ${serverConfig.name}")
        }
    }

    /**
     * Installs the given package using the provided server configuration.
     *
     * @param packageName the name of the package to install
     * @param serverConfig the configuration of the server to install on
     * @param maxWaitMs the maximum amount of time, in milliseconds, to wait for the install to finish
     * @param retryWaitMs the amount of time to wait while polling for status updates
     *
     * @return the {@link PackageStatus} of doing the install
     */
    @Nonnull
    static Status install(String packageName, SlingServerConfiguration serverConfig, long maxWaitMs, long retryWaitMs) {
        return CqPackageCommand.doCommand("install", packageName, serverConfig, maxWaitMs, retryWaitMs, falseStatusHandler)
    }

}
