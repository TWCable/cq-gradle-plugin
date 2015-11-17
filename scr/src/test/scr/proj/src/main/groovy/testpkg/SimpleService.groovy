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

package testpkg

import org.apache.felix.scr.annotations.Component
import org.apache.felix.scr.annotations.Properties
import org.apache.felix.scr.annotations.Property
import org.apache.felix.scr.annotations.Reference
import org.apache.felix.scr.annotations.Service

@Component(
    label = "Simple Service Test",
    description = "Simple Service Test description",
    metatype = true,
    immediate = true
)
@Properties([
    @Property(
        name = "testProp",
        value = "testProp value",
        propertyPrivate = true
    ),
    @Property(
        label = "Vendor",
        name = "testProp2",
        value = "Time Warner Cable",
        propertyPrivate = false
    ),
    @Property(
        label = "Workflow Label",
        name = "process.label",
        value = "Publish to Stage Workflow Process",
        description = "Label which will appear in the Adobe CQ Workflow interface"
    )
])
@Service
class SimpleService implements Runnable {
    @Property(label = "Prop on constant")
    private static final String ENABLE_DATA_SYNCH = "prop.on.constant"

    @Reference(name = "myRunnableName")
    private Runnable myRunnable


    void run() {

    }

}
