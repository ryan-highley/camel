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
import javax.management.ObjectName;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_ENDPOINT;
import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_PROCESSOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@DisabledOnOs(OS.AIX)
public class ManagedMulticastTest extends ManagementTestSupport {

    @Test
    public void testMulticast() throws Exception {
        getMockEndpoint("mock:a").expectedMessageCount(3);
        getMockEndpoint("mock:b").expectedMessageCount(3);

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", "Bye World");
        template.sendBody("direct:start", "Hi World");

        assertMockEndpointsSatisfied();

        MBeanServer mbeanServer = getMBeanServer();

        ObjectName name = getCamelObjectName(TYPE_ENDPOINT, "mock://a");
        Integer queueSize = (Integer) mbeanServer.invoke(name, "queueSize", null, null);
        assertEquals(3, queueSize.intValue());

        name = getCamelObjectName(TYPE_ENDPOINT, "mock://b");
        queueSize = (Integer) mbeanServer.invoke(name, "queueSize", null, null);
        assertEquals(3, queueSize.intValue());

        name = getCamelObjectName(TYPE_PROCESSOR, "myMulticast");
        mbeanServer.isRegistered(name);

        Long total = (Long) mbeanServer.getAttribute(name, "ExchangesTotal");
        assertEquals(3, total.intValue());

        Boolean parallel = (Boolean) mbeanServer.getAttribute(name, "ParallelProcessing");
        assertFalse(parallel.booleanValue());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("foo")
                        .multicast().id("myMulticast")
                        .to("mock:a").to("mock:b");
            }
        };
    }

}
