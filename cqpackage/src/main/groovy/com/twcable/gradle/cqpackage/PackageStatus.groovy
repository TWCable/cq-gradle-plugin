package com.twcable.gradle.cqpackage

import groovy.transform.CompileStatic

@CompileStatic
class PackageStatus extends Status {

    protected PackageStatus(String name) {
        super(name)
    }

    static final PackageStatus NOT_INSTALLED = new PackageStatus("NOT_INSTALLED")
    static final PackageStatus NO_PACKAGE = new PackageStatus("NO_PACKAGE")
    static final PackageStatus PACKAGE_EXISTS = new PackageStatus("PACKAGE_EXISTS")
    static final PackageStatus UNRESOLVED_DEPENDENCIES = new PackageStatus("UNRESOLVED_DEPENDENCIES")
}
