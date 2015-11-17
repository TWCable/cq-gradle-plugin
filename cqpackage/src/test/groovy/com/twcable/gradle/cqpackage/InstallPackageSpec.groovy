package com.twcable.gradle.cqpackage

import spock.lang.Unroll

import static com.twcable.gradle.cqpackage.PackageStatus.NO_PACKAGE
import static com.twcable.gradle.cqpackage.PackageStatus.OK

@SuppressWarnings("GroovyAssignabilityCheck")
class InstallPackageSpec extends AbstractPackageCommandSpec {

    @Unroll
    def "install package for \"#msg\""() {
        setupSlingSupport(success, msg, installedPackage)

        when:
        def retStatus = InstallPackage.install("fakepackage", slingServerConfiguration, 20, 10)

        then:
        retStatus == status

        where:
        installedPackage | success | msg                 | status
        "fakepackage"    | true    | "Package installed" | OK
        "froble"         | null    | "no package"        | NO_PACKAGE
    }

}
