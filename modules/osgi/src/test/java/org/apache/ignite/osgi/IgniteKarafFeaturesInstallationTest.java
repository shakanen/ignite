/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.osgi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.karaf.features.Feature;
import org.junit.Test;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.karaf.options.KarafDistributionOption;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Pax Exam test class to check if all features could be resolved and installed.
 */
public class IgniteKarafFeaturesInstallationTest extends AbstractIgniteKarafTest {

    /**
     * Container configuration.
     *
     * @return The configuration.
     */
    @Configuration
    public Option[] config() {
        List<Option> options = new ArrayList<>(Arrays.asList(baseConfig()));

        return CoreOptions.options(options.toArray(new Option[0]));
    }

    /**
     * @throws Exception
     */
    @Test
    public void testAllBundlesActiveAndFeaturesInstalled() throws Exception {
        Bundle igniteOsgiBundle = null;

        // Assert ignite-osgi bundle is installed
        for (final Bundle b : bundleCtx.getBundles()) {
            if ("org.apache.ignite.ignite-osgi".equals(b.getSymbolicName())) {
                igniteOsgiBundle = b;
            }
        }

        assertNotNull("Expecting ignite-osgi bundle to be found as installed bundle but was not", igniteOsgiBundle);
    }

    @Override protected List<String> featuresToInstall() {
        return Arrays.asList("ignite-core");
    }
}
