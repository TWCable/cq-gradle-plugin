package com.twcable.gradle.cqpackage

import org.apache.jackrabbit.vault.packaging.impl.PackageManagerImpl
import spock.lang.Unroll

import static com.twcable.gradle.cqpackage.PackageStatus.NO_PACKAGE
import static com.twcable.gradle.cqpackage.PackageStatus.OK

@SuppressWarnings("GroovyAssignabilityCheck")
class UploadPackageSpec extends AbstractPackageCommandSpec {

    @Unroll
    def "upload package for \"#msg\""() {
        setupSlingSupport(success, msg, installedPackage)
        def packageFile = new File(CqPackageHelperSpec.class.classLoader.getResource("testpackage-1.0.1.zip").getFile())

        when:
        def retStatus = UploadPackage.upload(packageFile, false, slingServerConfiguration, 20, 10, new PackageManagerImpl())

        then:
        retStatus == status

        where:
        installedPackage | success | msg                 | status
        "testpackage"    | true    | "Package installed" | OK
//        "froble"         | null    | "no package"        | NO_PACKAGE
    }

}
