# Purpose #

This contains a number of plugins for making it easier to work with Gradle and Adobe CQ/AEM.

# Installation #

```
buildscript {
    repositories {
        jcenter()
        maven {
            url "http://dl.bintray.com/twcable/aem"
        }
    }

    dependencies {
        classpath "com.twcable.gradle:cq-gradle-plugins:2.0.2"
    }
}
```

# Plugins #

## CQ Package Plugin ##

### Usage ###

`apply plugin: 'cqpackage'`

The standard Gradle 'base' plugin is automatically applied.

#### Tasks ####

* `createPackage` - Creates the CQ Package zip file.
  (Depends on the `jar` tasks of the projects that are a part of the `cq_package` configuration.)
* `install` - Issues the command to CQ to install the uploaded package.
* `uninstall` - Uninstalls the CQ Package. If the package is not on the server, nothing happens.
  (i.e., This does not fail if the package is not on the server.)
* `reinstall` - Reinstalls (removes then uploads and installs a fresh copy) the CQ Package.
  (Depends on `clean`, `uninstall`, `remove`, `install`, `verifyBundles`)
* `installPackage` - Installs the CQ Package
* `validateBundles` - Checks all the JARs that are included in the package to make sure they are installed and in an
  ACTIVE state and gives a report of any that are not. This task polls in the case the bundles are newly installed.
* `installRemote` - Issues the command to CQ to install the uploaded package to a remote server defined
  by -Denvironment, -DenvJson, and -Dpackage.
* `validateRemoteBundles` - Validates remote bundles in the CQ Package are started correctly
* `upload` - Upload the package to all the servers defined by the `slingServers` configuration.
* `uploadRemote` - Upload the package to all the servers defined by -Denvironment, -DenvJson, and -Dpackage.
* `remove` - Removes the package from CQ. Does not fail if the package was not on the server to begin with.
* `uninstallBundles` - Downloads the currently installed package .zip file if it exists, compiles list of bundles
  based off what is currently installed, then stops and uninstalls each bundles individually.  A final
  `refreshAllBundles` is performed.
* `installRemoteTask` - Installs a specified CQ Package to a remote environment
* `verifyBundles` - Checks all the JARs that are included in the package to make sure they are OSGi compliant, and
  gives a report of any that are not. Never causes the build to fail.
* `addBundlesToFilterXml` - Adds the bundles to the filter.xml
* `checkBundleStatus` - Check Bundle Status
* `startInactiveBundles` - Asynchronously attempts to start any bundle in RESOLVED state.

Added by the 'base' plugin:

* `assemble` - Calls `createPackage`.
* `clean` - Removes the build directory.


### Configuration ###

#### Convention: `slingServers` ####

By default, the plugin is initialized with the following effective configuration:

    slingServers.with {
      retryWaitMs = 1000
      maxWaitValidateBundlesMs = 10000
      clusterAuths = false
      clusterPubs = false
      uninstallBundlesPredicate = { false }
      author.with {
        protocol = 'http'
        port = 4502
        machineName = 'localhost'
        username = 'admin'
        password = 'admin'
        installPath = '/apps/install'
        active = true
      }
      publisher.with {
        protocol = 'http'
        port = 4503
        machineName = 'localhost'
        username = 'admin'
        password = 'admin'
        installPath = '/apps/install'
        active = true
      }
    }

To change the password for the author instance, for example, in the `build.gradle` file do

    slingServers.author.password = 'other_password'

The `active` property determines if it will try to interact with that server. If it has a problem connecting to
the server, it is automatically set to be `false` so it does not keep trying to attach to a server that is not running.

If `envJson` and `environment` properties are defined, the list of servers for this environment are extracted from
the JSON file.  See [EnvironmentJsonFileReader](src/main/groovy/com/twcable/gradle/sling/EnvironmentJsonFileReader.groovy)

#### Dependency Configuration: `cq_package` ####

Example usage:

    dependencies {
        cq_package project(':project-name')
        cq_package "net.tanesha:recaptcha4j:1.0.0"
    }

    configurations.cq_package {
        exclude group: 'javax.servlet', module: 'servlet-api'
    }


## SCR Plugin ##

### Usage ###

`apply plugin: 'scr'`

Applying the plugin implicitly applies the `osgi` plugin
  and adds a dependency from the `jar` task upon `processScrAnnotations`.

#### Tasks ####

* `processScrAnnotations` - Processes the x-doclet style and @SCR annotations to create
  the appropriate OSGi metadata for OSGi Declarative Services.


## Sling Bundle Plugin ##

### Usage ###

`apply plugin: 'sling-bundle'`

#### Tasks ####

* `uploadBundle` - Uploads the bundle to the CQ server. The task will fail if the bundle fails to start.
* `deleteBundle` - Removes the current bundle from CQ. This task will not fail if the bundle does not exist.
* `refreshAllBundles` - Tells CQ to refresh all the bundles. This gets added to the top-level project
  and generally a good idea to run after any of the other tasks.
* `showBundle` - Shows a JSON representation of the information CQ has about the bundle, including its status,
  what bundles it is using, and what bundles are using it to STDOUT.

### Configuration ###

#### Convention: `slingServers` ####

Creates the `slingServers` convention object. See the "CQ Package Plugin" for details.

#### Convention: `bundle` ####

By default, the plugin is initialized with the following effective configuration:

    bundle.with {
      name = project.name
      symbolicName = // computed from project.group and project.name
      installPath = project.slingServers.author.installPath
      sourceFile = project.jar.archivePath
      slingServers = project.slingServers
    }

`bundle` also has a `felixId` property that the plugin tries to determine dynamically from the server based on the
bundle's symbolic name.

# LICENSE

Copyright 2015 Time Warner Cable, Inc.

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance
with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
the specific language governing permissions and limitations under the License.
