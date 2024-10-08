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
package org.apache.camel.impl.cloud;

import java.util.List;
import java.util.UUID;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultChannel;
import org.apache.camel.model.cloud.ServiceCallConfigurationDefinition;
import org.apache.camel.model.cloud.ServiceCallDefinitionConstants;
import org.apache.camel.model.cloud.ServiceCallExpressionConfiguration;
import org.apache.camel.model.language.SimpleExpression;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ServiceCallConfigurationTest {

    // ****************************************
    // test default resolution
    // ****************************************

    @Test
    public void testDynamicUri() throws Exception {
        StaticServiceDiscovery sd = new StaticServiceDiscovery();
        sd.addServer("scall@127.0.0.1:8080");
        sd.addServer("scall@127.0.0.1:8081");

        ServiceCallConfigurationDefinition conf = new ServiceCallConfigurationDefinition();
        conf.setServiceDiscovery(sd);
        conf.setComponent("mock");

        DefaultCamelContext context = new DefaultCamelContext();
        context.setServiceCallConfiguration(conf);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .routeId("default")
                        .serviceCall("scall", "scall/api/${header.customerId}");
            }
        });

        context.start();

        MockEndpoint mock = context.getEndpoint("mock:127.0.0.1:8080/api/123", MockEndpoint.class);
        mock.expectedMessageCount(1);

        DefaultServiceCallProcessor proc = findServiceCallProcessor(context.getRoute("default"));

        assertNotNull(proc);
        assertInstanceOf(DefaultServiceLoadBalancer.class, proc.getLoadBalancer());

        DefaultServiceLoadBalancer loadBalancer = (DefaultServiceLoadBalancer) proc.getLoadBalancer();
        assertEquals(sd, loadBalancer.getServiceDiscovery());

        // call the route
        context.createFluentProducerTemplate().to("direct:start").withHeader("customerId", "123").send();

        // the service should call the mock
        mock.assertIsSatisfied();

        context.stop();
    }

    @Test
    public void testDefaultConfigurationFromCamelContext() throws Exception {
        StaticServiceDiscovery sd = new StaticServiceDiscovery();
        sd.addServer("service@127.0.0.1:8080");
        sd.addServer("service@127.0.0.1:8081");

        BlacklistServiceFilter sf = new BlacklistServiceFilter();
        sf.addServer("*@127.0.0.1:8080");

        ServiceCallConfigurationDefinition conf = new ServiceCallConfigurationDefinition();
        conf.setServiceDiscovery(sd);
        conf.setServiceFilter(sf);

        DefaultCamelContext context = new DefaultCamelContext();
        context.setServiceCallConfiguration(conf);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .routeId("default")
                        .serviceCall()
                            .name("scall")
                            .component("file")
                        .end();
            }
        });

        context.start();

        DefaultServiceCallProcessor proc = findServiceCallProcessor(context.getRoute("default"));

        assertNotNull(proc);
        assertInstanceOf(DefaultServiceLoadBalancer.class, proc.getLoadBalancer());

        DefaultServiceLoadBalancer loadBalancer = (DefaultServiceLoadBalancer) proc.getLoadBalancer();
        assertEquals(sd, loadBalancer.getServiceDiscovery());
        assertEquals(sf, loadBalancer.getServiceFilter());

        context.stop();
    }

    @Test
    public void testDefaultConfigurationFromRegistryWithDefaultName() throws Exception {
        StaticServiceDiscovery sd = new StaticServiceDiscovery();
        sd.addServer("service@127.0.0.1:8080");
        sd.addServer("service@127.0.0.1:8081");

        BlacklistServiceFilter sf = new BlacklistServiceFilter();
        sf.addServer("*@127.0.0.1:8080");

        ServiceCallConfigurationDefinition conf = new ServiceCallConfigurationDefinition();
        conf.setServiceDiscovery(sd);
        conf.serviceFilter(sf);

        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .routeId("default")
                            .serviceCall()
                            .name("scall")
                            .component("file")
                        .end();
            }
        });

        context.getRegistry().bind(ServiceCallDefinitionConstants.DEFAULT_SERVICE_CALL_CONFIG_ID, conf);

        context.start();

        DefaultServiceCallProcessor proc = findServiceCallProcessor(context.getRoute("default"));

        assertNotNull(proc);
        assertInstanceOf(DefaultServiceLoadBalancer.class, proc.getLoadBalancer());

        DefaultServiceLoadBalancer loadBalancer = (DefaultServiceLoadBalancer) proc.getLoadBalancer();
        assertEquals(sd, loadBalancer.getServiceDiscovery());
        assertEquals(sf, loadBalancer.getServiceFilter());

        context.stop();
    }

    @Test
    public void testDefaultConfigurationFromRegistryWithNonDefaultName() throws Exception {
        StaticServiceDiscovery sd = new StaticServiceDiscovery();
        sd.addServer("service@127.0.0.1:8080");
        sd.addServer("service@127.0.0.1:8081");

        BlacklistServiceFilter sf = new BlacklistServiceFilter();
        sf.addServer("*@127.0.0.1:8080");

        ServiceCallConfigurationDefinition conf = new ServiceCallConfigurationDefinition();
        conf.setServiceDiscovery(sd);
        conf.serviceFilter(sf);

        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .routeId("default")
                            .serviceCall()
                            .name("scall")
                            .component("file")
                        .end();
            }
        });

        context.getRegistry().bind(UUID.randomUUID().toString(), conf);

        context.start();

        DefaultServiceCallProcessor proc = findServiceCallProcessor(context.getRoute("default"));

        assertNotNull(proc);
        assertInstanceOf(DefaultServiceLoadBalancer.class, proc.getLoadBalancer());

        DefaultServiceLoadBalancer loadBalancer = (DefaultServiceLoadBalancer) proc.getLoadBalancer();
        assertEquals(sd, loadBalancer.getServiceDiscovery());
        assertEquals(sf, loadBalancer.getServiceFilter());

        context.stop();
    }

    // ****************************************
    // test mixed resolution
    // ****************************************

    @Test
    public void testMixedConfiguration() throws Exception {
        // Default
        StaticServiceDiscovery defaultServiceDiscovery = new StaticServiceDiscovery();
        defaultServiceDiscovery.addServer("service@127.0.0.1:8080");
        defaultServiceDiscovery.addServer("service@127.0.0.1:8081");
        defaultServiceDiscovery.addServer("service@127.0.0.1:8082");

        BlacklistServiceFilter defaultServiceFilter = new BlacklistServiceFilter();
        defaultServiceFilter.addServer("*@127.0.0.1:8080");

        ServiceCallConfigurationDefinition defaultConfiguration = new ServiceCallConfigurationDefinition();
        defaultConfiguration.setServiceDiscovery(defaultServiceDiscovery);
        defaultConfiguration.serviceFilter(defaultServiceFilter);

        // Named
        BlacklistServiceFilter namedServiceFilter = new BlacklistServiceFilter();
        namedServiceFilter.addServer("*@127.0.0.1:8081");

        ServiceCallConfigurationDefinition namedConfiguration = new ServiceCallConfigurationDefinition();
        namedConfiguration.serviceFilter(namedServiceFilter);

        // Local
        StaticServiceDiscovery localServiceDiscovery = new StaticServiceDiscovery();
        localServiceDiscovery.addServer("service@127.0.0.1:8080");
        localServiceDiscovery.addServer("service@127.0.0.1:8081");
        localServiceDiscovery.addServer("service@127.0.0.1:8082");
        localServiceDiscovery.addServer("service@127.0.0.1:8084");

        // Camel context
        DefaultCamelContext context = new DefaultCamelContext();
        context.setServiceCallConfiguration(defaultConfiguration);
        context.addServiceCallConfiguration("named", namedConfiguration);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:default")
                        .id("default")
                        .serviceCall()
                            .name("default-scall")
                            .component("file")
                        .end();
                from("direct:named")
                        .id("named")
                        .serviceCall()
                            .serviceCallConfiguration("named")
                            .name("named-scall")
                            .component("file")
                        .end();
                from("direct:local")
                        .id("local")
                        .serviceCall()
                            .serviceCallConfiguration("named")
                            .name("local-scall")
                            .component("file")
                            .serviceDiscovery(localServiceDiscovery)
                        .end();
            }
        });

        context.start();

        {
            // Default
            DefaultServiceCallProcessor proc = findServiceCallProcessor(context.getRoute("default"));

            assertNotNull(proc);
            assertInstanceOf(DefaultServiceLoadBalancer.class, proc.getLoadBalancer());

            DefaultServiceLoadBalancer loadBalancer = (DefaultServiceLoadBalancer) proc.getLoadBalancer();
            assertEquals(defaultServiceDiscovery, loadBalancer.getServiceDiscovery());
            assertEquals(defaultServiceFilter, loadBalancer.getServiceFilter());
        }

        {
            // Named
            DefaultServiceCallProcessor proc = findServiceCallProcessor(context.getRoute("named"));

            assertNotNull(proc);
            assertInstanceOf(DefaultServiceLoadBalancer.class, proc.getLoadBalancer());

            DefaultServiceLoadBalancer loadBalancer = (DefaultServiceLoadBalancer) proc.getLoadBalancer();
            assertEquals(defaultServiceDiscovery, loadBalancer.getServiceDiscovery());
            assertEquals(namedServiceFilter, loadBalancer.getServiceFilter());
        }

        {
            // Local
            DefaultServiceCallProcessor proc = findServiceCallProcessor(context.getRoute("local"));

            assertNotNull(proc);
            assertInstanceOf(DefaultServiceLoadBalancer.class, proc.getLoadBalancer());

            DefaultServiceLoadBalancer loadBalancer = (DefaultServiceLoadBalancer) proc.getLoadBalancer();
            assertEquals(localServiceDiscovery, loadBalancer.getServiceDiscovery());
            assertEquals(namedServiceFilter, loadBalancer.getServiceFilter());
        }

        context.stop();
    }

    // **********************************************
    // test placeholders
    // **********************************************

    @Test
    public void testPlaceholders() throws Exception {
        DefaultCamelContext context = null;

        try {
            System.setProperty("scall.name", "service-name");
            System.setProperty("scall.scheme", "file");
            System.setProperty("scall.servers1", "hello-service@localhost:8081,hello-service@localhost:8082");
            System.setProperty("scall.servers2", "hello-svc@localhost:8083,hello-svc@localhost:8084");
            System.setProperty("scall.filter", "hello-svc@localhost:8083");

            ServiceCallConfigurationDefinition global = new ServiceCallConfigurationDefinition();
            global.blacklistFilter().servers("{{scall.filter}}");

            context = new DefaultCamelContext();
            context.setServiceCallConfiguration(global);
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start")
                            .routeId("default")
                            .serviceCall()
                                .name("{{scall.name}}")
                                .component("{{scall.scheme}}")
                                .uri("direct:{{scall.name}}")
                                .staticServiceDiscovery()
                                    .servers("{{scall.servers1}}")
                                    .servers("{{scall.servers2}}")
                                .end()
                            .end();
                }
            });

            context.start();

            DefaultServiceCallProcessor proc = findServiceCallProcessor(context.getRoute("default"));

            assertNotNull(proc);
            assertInstanceOf(DefaultServiceLoadBalancer.class, proc.getLoadBalancer());
            assertEquals("service-name", proc.getName());
            assertEquals("file", proc.getScheme());
            assertEquals("direct:service-name", proc.getUri());

            DefaultServiceLoadBalancer lb = (DefaultServiceLoadBalancer) proc.getLoadBalancer();

            assertInstanceOf(BlacklistServiceFilter.class, lb.getServiceFilter());
            BlacklistServiceFilter filter = (BlacklistServiceFilter) lb.getServiceFilter();
            List<ServiceDefinition> blacklist = filter.getBlacklistedServices();
            assertEquals(1, blacklist.size());

            assertInstanceOf(StaticServiceDiscovery.class, lb.getServiceDiscovery());

            Exchange exchange = new DefaultExchange(context);
            List<ServiceDefinition> services1 = lb.getServiceDiscovery().getServices("hello-service");
            assertEquals(2, filter.apply(exchange, services1).size());

            List<ServiceDefinition> services2 = lb.getServiceDiscovery().getServices("hello-svc");
            assertEquals(1, filter.apply(exchange, services2).size());

        } finally {
            if (context != null) {
                context.stop();
            }

            // Cleanup system properties
            System.clearProperty("scall.name");
            System.clearProperty("scall.scheme");
            System.clearProperty("scall.servers1");
            System.clearProperty("scall.servers2");
            System.clearProperty("scall.filter");
        }

        context.stop();
    }

    // **********************************************
    // test placeholders
    // **********************************************

    @Test
    public void testExpression() throws Exception {
        DefaultCamelContext context = null;

        try {
            ServiceCallConfigurationDefinition config = new ServiceCallConfigurationDefinition();
            config.setServiceDiscovery(new StaticServiceDiscovery());
            config.setExpressionConfiguration(
                    new ServiceCallExpressionConfiguration().expression(
                            new SimpleExpression(
                                    "file:${header.CamelServiceCallServiceHost}:${header.CamelServiceCallServicePort}")));

            context = new DefaultCamelContext();
            context.setServiceCallConfiguration(config);
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start")
                            .routeId("default")
                            .serviceCall("scall");
                }
            });

            context.start();

            DefaultServiceCallProcessor proc = findServiceCallProcessor(context.getRoute("default"));

            assertNotNull(proc);
            assertEquals("file:${header.CamelServiceCallServiceHost}:${header.CamelServiceCallServicePort}",
                    proc.getExpression().toString());

        } finally {
            if (context != null) {
                context.stop();
            }
        }

        context.stop();
    }

    // **********************************************
    // Helper
    // **********************************************

    private DefaultServiceCallProcessor findServiceCallProcessor(Route route) {

        for (Processor processor : route.navigate().next()) {
            if (processor instanceof DefaultChannel defaultChannel) {
                processor = defaultChannel.getNextProcessor();
            }
            if (processor instanceof DefaultServiceCallProcessor defaultServiceCallProcessor) {
                return defaultServiceCallProcessor;
            }
        }

        throw new IllegalStateException("Unable to find a ServiceCallProcessor");
    }

}
