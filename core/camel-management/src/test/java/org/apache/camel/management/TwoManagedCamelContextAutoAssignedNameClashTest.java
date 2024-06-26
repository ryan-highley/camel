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
package org.apache.camel.management;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.TestSupport;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultCamelContextNameStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
public class TwoManagedCamelContextAutoAssignedNameClashTest extends TestSupport {

    private CamelContext camel1;
    private CamelContext camel2;

    protected CamelContext createCamelContext() {
        DefaultCamelContext context = new DefaultCamelContext();
        return context;
    }

    @Test
    public void testTwoManagedCamelContextClash() throws Exception {
        camel1 = createCamelContext();
        camel1.start();
        assertTrue(camel1.getStatus().isStarted(), "Should be started");

        MBeanServer mbeanServer = camel1.getManagementStrategy().getManagementAgent().getMBeanServer();
        ObjectName on = getContextObjectName(camel1);
        assertTrue(mbeanServer.isRegistered(on), "Should be registered");

        // now cheat and reset the counter so we can test for a clash
        DefaultCamelContextNameStrategy.setCounter(0);

        camel2 = createCamelContext();
        camel2.start();
        ObjectName on2 = getContextObjectName(camel2);
        assertTrue(mbeanServer.isRegistered(on2), "Should be registered");

        assertTrue(mbeanServer.isRegistered(on), "Should still be registered after name clash");
        assertTrue(mbeanServer.isRegistered(on2), "Should still be registered after name clash");
    }

    private static ObjectName getContextObjectName(CamelContext context) throws MalformedObjectNameException {
        return ObjectName
                .getInstance("org.apache.camel:context=" + context.getManagementName() + ",type=context,name=\""
                             + context.getName() + "\"");
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        if (camel1 != null) {
            camel1.stop();
        }
        if (camel2 != null) {
            camel2.stop();
        }
        super.tearDown();
    }

}
