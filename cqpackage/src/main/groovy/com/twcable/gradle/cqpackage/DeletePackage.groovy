package com.twcable.gradle.cqpackage

import com.twcable.gradle.sling.SlingServerConfiguration
import com.twcable.gradle.sling.SlingServersConfiguration
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import javax.annotation.Nonnull

import static com.twcable.gradle.cqpackage.PackageStatus.NO_PACKAGE
import static com.twcable.gradle.cqpackage.PackageStatus.UNKNOWN
import static com.twcable.gradle.cqpackage.Status.OK
import static com.twcable.gradle.cqpackage.Status.SERVER_INACTIVE
import static com.twcable.gradle.cqpackage.Status.SERVER_TIMEOUT

@Slf4j
@CompileStatic
class DeletePackage {

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
     * Iterates through all of the servers in "slingServersConfiguration" and deletes the given package on them.
     *
     * @see #delete(String, SlingServerConfiguration, long, long)
     * @see #consumeStatus(Status, String, SlingServerConfiguration)
     */
    static void delete(String packageName, SlingServersConfiguration slingServersConfiguration) {
        slingServersConfiguration.each { serverConfig ->
            long maxWaitMs = slingServersConfiguration.maxWaitValidateBundlesMs
            long retryWaitMs = slingServersConfiguration.retryWaitMs
            def status = delete(packageName, serverConfig, maxWaitMs, retryWaitMs)
            consumeStatus(status, packageName, serverConfig)
        }
    }

    /**
     * "Consumes" the status, mostly to appropriately log what happened.
     */
    static void consumeStatus(Status status, String packageName, SlingServerConfiguration serverConfig) {
        switch (status) {
            case Status.OK:
                log.info("\"${packageName}\" is deleted on ${serverConfig.name}"); break
            case PackageStatus.NO_PACKAGE:
                log.info("\"${packageName}\" is not currently on ${serverConfig.name}, so no need to delete it"); break
            case Status.SERVER_INACTIVE:
            case Status.SERVER_TIMEOUT:
                break // ignore
            default:
                throw new IllegalArgumentException("Unknown status ${status} when trying to delete ${packageName} on ${serverConfig.name}")
        }
    }

    /**
     * Deletes the given package using the provided server configuration.
     *
     * @param packageName the name of the package to delete
     * @param serverConfig the configuration of the server to delete from
     * @param maxWaitMs the maximum amount of time, in milliseconds, to wait for the delete to finish
     * @param retryWaitMs the amount of time to wait while polling for status updates
     *
     * @return the {@link PackageStatus} of doing the delete
     */
    @Nonnull
    static Status delete(String packageName, SlingServerConfiguration serverConfig, long maxWaitMs, long retryWaitMs) {
        return CqPackageCommand.doCommand("delete", packageName, serverConfig, maxWaitMs, retryWaitMs, falseStatusHandler)
    }

}
