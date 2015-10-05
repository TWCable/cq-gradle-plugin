package com.twcable.gradle.cqpackage

import spock.lang.Unroll

import static com.twcable.gradle.cqpackage.PackageStatus.NO_PACKAGE
import static com.twcable.gradle.cqpackage.PackageStatus.OK

@SuppressWarnings("GroovyAssignabilityCheck")
class DeletePackageSpec extends AbstractPackageCommandSpec {

    @Unroll
    def "uninstall package for \"#msg\""() {
        setupSlingSupport(success, msg, installedPackage)

        when:
        def retStatus = DeletePackage.delete("fakepackage", slingServerConfiguration, 20, 10)

        then:
        retStatus == status

        where:
        installedPackage | success | msg               | status
        "fakepackage"    | true    | "Package deleted" | OK
        "froble"         | null    | "no package"      | NO_PACKAGE
    }

}
