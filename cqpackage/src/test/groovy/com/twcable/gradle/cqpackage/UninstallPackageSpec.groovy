package com.twcable.gradle.cqpackage

import spock.lang.Unroll

import static com.twcable.gradle.cqpackage.PackageStatus.NOT_INSTALLED
import static com.twcable.gradle.cqpackage.PackageStatus.NO_PACKAGE
import static com.twcable.gradle.cqpackage.PackageStatus.OK

@SuppressWarnings("GroovyAssignabilityCheck")
class UninstallPackageSpec extends AbstractPackageCommandSpec {

    @Unroll
    def "uninstall package for \"#msg\""() {
        setupSlingSupport(success, msg, installedPackage)

        when:
        def retStatus = UninstallPackage.uninstall("fakepackage", slingServerConfiguration, 20, 10)

        then:
        retStatus == status

        where:
        installedPackage | success | msg                                                 | status
        "fakepackage"    | true    | "Package uninstalled"                               | OK
        "fakepackage"    | false   | "Unable to uninstall package. No snapshot present." | NOT_INSTALLED
        "froble"         | null    | "no package"                                        | NO_PACKAGE
    }

}
