/*
 * Copyright 2015 Time Warner Cable, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twcable.gradle.sling

import com.twcable.gradle.sling.osgi.BundleState
import groovy.json.JsonBuilder

class SlingServerFixture extends FixtureBase {
    private SlingServerConfiguration _slingServerConfiguration
    String installPath = '/apps/install'
    String name = 'testserver'
    String machineName = 'unittest'
    String protocol = 'http'
    int port = 9797
    String username = 'admin'
    String password = 'admin'

    Collection<SlingBundleFixture> bundles = []


    private SlingServerFixture() {}


    static SlingServerFixture make(Closure closure) {
        make(SlingServerFixture, closure)
    }


    void add(SlingBundleFixture bundle) {
        bundles.add(bundle)
    }


    SlingServerConfiguration getSlingServerConfiguration() {
        if (!_slingServerConfiguration) {
            _slingServerConfiguration = new SlingServerConfiguration(protocol: protocol, machineName: machineName, port: port, username: username, password: password)
        }
        _slingServerConfiguration
    }


    Map bundlesInformation() {
        Collection<Map> data = bundles.collect { SlingBundleFixture bundle ->
            [
                category    : '',
                symbolicName: bundle.symbolicName,
                version     : bundle.version,
                state       : bundle.bundleState.stateString,
                stateRaw    : bundle.bundleState.stateRaw,
                fragment    : false,
                name        : bundle.bundleName,
                id          : bundle.osgiBundleConfiguration.felixId

            ]
        }

        def actCount = bundles.count { SlingBundleFixture bundle -> bundle.bundleState.stateString == BundleState.ACTIVE.stateString }
        def fraCount = bundles.count { SlingBundleFixture bundle -> bundle.bundleState.stateString == BundleState.FRAGMENT.stateString }
        def resCount = bundles.count { SlingBundleFixture bundle -> bundle.bundleState.stateString == BundleState.RESOLVED.stateString }
        def insCount = bundles.count { SlingBundleFixture bundle -> bundle.bundleState.stateString == BundleState.INSTALLED.stateString }

        [data  : data,
         s     : [
             bundles.size(),
             actCount,
             fraCount,
             resCount,
             insCount
         ],
         status: "Bundle information: ${bundles.size()} bundles in total - all 279 bundles active."
        ]
    }


    String bundlesInformationJson() {
        new JsonBuilder(bundlesInformation()).toPrettyString()
    }


    static def createBundlesJsonStr() {
        '''
{
  "data": [
    {
      "category": "",
      "symbolicName": "org.springframework.ws.xml",
      "version": "2.0.5.RELEASE",
      "state": "Active",
      "stateRaw": 32,
      "fragment": false,
      "name": "Spring XML",
      "id": 275
    },
    {
      "category": "",
      "symbolicName": "com.test.services",
      "version": "1.0.1",
      "state": "Installed",
      "stateRaw": 2,
      "fragment": false,
      "name": "webcms-services",
      "id": 289
    },
    {
      "category": "",
      "symbolicName": "com.test.servlets",
      "version": "1.0.1",
      "state": "Installed",
      "stateRaw": 2,
      "fragment": false,
      "name": "webcms-servlets",
      "id": 277
    }
  ],
  "s": [
    280,
    271,
    7,
    0,
    2
  ],
  "status": "Bundle information: 280 bundles in total, 271 bundles active, 7 bundles active fragments, 2 bundles installed."
}
'''
    }

}
