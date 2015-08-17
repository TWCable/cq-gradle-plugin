# RELEASE NOTES

## 3.0.2

* Made createPackage.contentSrc more flexible ( GH-26 )

## 3.0.1

* Consolidated determining list of bundles between CQ Package tasks ( GH-22 )

## 3.0.0

* The deprecated `native_cq_package` configuration has been removed ( GH-19 )
* `CqPackageConfiguration` has been removed
* Gets rid of NPE when uninstalling ( GH-9 )
* The `CreatePackageTask` and `AddBundlesToFilterXmlTask` of `CqPackagePlugin` has been greatly reworked. See
  [the expanded CQ Package Plugin documentation](docs/CqPackagePlugin.adoc)
* `CqPackageCommand.getPackageName()` and `CqPackageHelper.getPackageName()` both simply uses the project's name
  instead of trying to get it from `CqPackageConfiguration`
* Promoted the bundles that are in the package to top-level in filter.xml
  Instead of doing something like

  ```xml
  <filter root="/apps/install">
    <exclude pattern="/apps/install/mybundle.jar"/>
  </filter>
  ```

  filter.xml is now generated to have
  ```xml
  <filter root="/apps/install/mybundle.jar"/>
  ```

* Removed CqPackageConfiguration since all the properties were moved
  to CreatePackageTask

#### Migration Notes

* The `native_cq_package` configuration has been removed. The use of `add*Bundles()` and setting
  `bundleInstallRoot` in `CreatePackageTask` gives simpler and more fine-grained control.
* `CreatePackageTask` no longer tries to automatically exclude AEM-provided bundles. It's utility before was
  questionable and obfuscated important dependency information.

## 2.1.0

* Gets projects from dependencies and not just all sibling projects ( GH-17 )

## 2.0.3

* Fixed issue with installPackage task ( GH-16 )

## 2.0.2

* Exclude group com.adobe.aem for AEM 6.0 ( GH-12 )
* Added commons-io dependency

## 2.0.1

* Fixed checkBundleStatus getting confused if a server is "missing" ( GH-8 )
* Added a basic .travis.yml file for TravisCI testing

## 2.0.0

Initial public release
