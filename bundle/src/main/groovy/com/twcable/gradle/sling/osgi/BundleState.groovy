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

package com.twcable.gradle.sling.osgi

import groovy.transform.TypeChecked

import javax.annotation.Nonnull

/**
 * The list of states that a bundle may be in.
 */
@TypeChecked
enum BundleState {
    UNINSTALLED(1, "Uninstalled"),
    INSTALLED(2, "Installed"),
    FRAGMENT(4, "Fragment"),
    RESOLVED(4, "Resolved"),
    STARTING(8, "Starting"),
    STOPPING(16, "Stopping"),
    ACTIVE(32, "Active"),
    MISSING(-1, "Missing"),
    UNKNOWN(0, "Unknown")

    final int stateRaw
    final String stateString


    private BundleState(int stateRaw, String stateString) {
        this.stateRaw = stateRaw
        this.stateString = stateString
    }

    /**
     * Returns the BundleState for the given numeric state code.
     */
    @Nonnull
    static BundleState state(String stringState) {
        values().find { it.stateString == stringState }
    }

    /**
     * "Capitalize" the state name.
     *
     * For example, "INSTALLED" becomes "Installed"
     */
    @Nonnull
    String capName() {
        name().toLowerCase().capitalize()
    }

}
