package com.twcable.gradle.cqpackage

import groovy.transform.CompileStatic

@CompileStatic
class Status {
    final String name


    protected Status(String name) {
        this.name = name
    }


    static final Status OK = new Status("OK")
    static final Status UNKNOWN = new Status("UNKNOWN")
    static final Status SERVER_INACTIVE = new Status("SERVER_INACTIVE")
    static final Status SERVER_TIMEOUT = new Status("SERVER_TIMEOUT")


    @Override
    public String toString() {
        return name;
    }

}
