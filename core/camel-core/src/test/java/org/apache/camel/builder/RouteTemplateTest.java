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
package org.apache.camel.builder;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Route;
import org.apache.camel.model.RouteTemplateDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RouteTemplateTest extends ContextTestSupport {

    @Test
    public void testDefineRouteTemplate() {
        assertEquals(1, context.getRouteTemplateDefinitions().size());

        RouteTemplateDefinition routeTemplate = context.getRouteTemplateDefinition("myTemplate");
        assertEquals("foo", routeTemplate.getTemplateParameters().get(0).getName());
        assertEquals("bar", routeTemplate.getTemplateParameters().get(1).getName());
    }

    @Test
    public void testCreateRouteFromRouteTemplate() throws Exception {
        assertEquals(1, context.getRouteTemplateDefinitions().size());

        RouteTemplateDefinition routeTemplate = context.getRouteTemplateDefinition("myTemplate");
        assertEquals("foo", routeTemplate.getTemplateParameters().get(0).getName());
        assertEquals("bar", routeTemplate.getTemplateParameters().get(1).getName());

        getMockEndpoint("mock:cheese").expectedBodiesReceived("Hello Cheese");
        getMockEndpoint("mock:cake").expectedBodiesReceived("Hello Cake");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("foo", "one");
        parameters.put("bar", "cheese");
        context.addRouteFromTemplate("first", "myTemplate", parameters);

        parameters.put("foo", "two");
        parameters.put("bar", "cake");
        context.addRouteFromTemplate("second", "myTemplate", parameters);

        assertEquals(2, context.getRouteDefinitions().size());
        assertEquals(2, context.getRoutes().size());
        assertEquals("Started", context.getRouteController().getRouteStatus("first").name());
        assertEquals("Started", context.getRouteController().getRouteStatus("second").name());
        assertEquals("true", context.getRoute("first").getProperties().get(Route.TEMPLATE_PROPERTY));
        assertEquals("true", context.getRoute("second").getProperties().get(Route.TEMPLATE_PROPERTY));

        template.sendBody("direct:one", "Hello Cheese");
        template.sendBody("direct:two", "Hello Cake");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testCreateRouteFromRouteTemplateFluent() throws Exception {
        assertEquals(1, context.getRouteTemplateDefinitions().size());

        RouteTemplateDefinition routeTemplate = context.getRouteTemplateDefinition("myTemplate");
        assertEquals("foo", routeTemplate.getTemplateParameters().get(0).getName());
        assertEquals("bar", routeTemplate.getTemplateParameters().get(1).getName());

        getMockEndpoint("mock:cheese").expectedBodiesReceived("Hello Cheese");
        getMockEndpoint("mock:cake").expectedBodiesReceived("Hello Cake");

        TemplatedRouteBuilder.builder(context, "myTemplate")
                .routeId("first")
                .parameter("foo", "one")
                .parameter("bar", "cheese")
                .add();

        TemplatedRouteBuilder.builder(context, "myTemplate")
                .routeId("second")
                .parameter("foo", "two")
                .parameter("bar", "cake")
                .add();

        assertEquals(2, context.getRouteDefinitions().size());
        assertEquals(2, context.getRoutes().size());
        assertEquals("Started", context.getRouteController().getRouteStatus("first").name());
        assertEquals("Started", context.getRouteController().getRouteStatus("second").name());
        assertEquals("true", context.getRoute("first").getProperties().get(Route.TEMPLATE_PROPERTY));
        assertEquals("true", context.getRoute("second").getProperties().get(Route.TEMPLATE_PROPERTY));

        template.sendBody("direct:one", "Hello Cheese");
        template.sendBody("direct:two", "Hello Cake");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testCreateRouteFromRouteTemplateAutoAssignedRouteId() throws Exception {
        assertEquals(1, context.getRouteTemplateDefinitions().size());

        RouteTemplateDefinition routeTemplate = context.getRouteTemplateDefinition("myTemplate");
        assertEquals("foo", routeTemplate.getTemplateParameters().get(0).getName());
        assertEquals("bar", routeTemplate.getTemplateParameters().get(1).getName());

        getMockEndpoint("mock:cheese").expectedBodiesReceived("Hello Cheese");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("foo", "one");
        parameters.put("bar", "cheese");
        String routeId = context.addRouteFromTemplate(null, "myTemplate", parameters);

        assertNotNull(routeId);
        assertEquals(1, context.getRouteDefinitions().size());
        assertEquals(1, context.getRoutes().size());
        assertEquals("Started", context.getRouteController().getRouteStatus(routeId).name());
        assertEquals("true", context.getRoute(routeId).getProperties().get(Route.TEMPLATE_PROPERTY));

        template.sendBody("direct:one", "Hello Cheese");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testCreateRouteFromRouteTemplateAutoAssignedRouteIdClash() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                // use a route id that can clash with auto assigned
                from("direct:hello").to("mock:hello").routeId("route1");
            }
        });

        assertEquals(1, context.getRouteDefinitions().size());
        assertEquals(1, context.getRouteTemplateDefinitions().size());

        RouteTemplateDefinition routeTemplate = context.getRouteTemplateDefinition("myTemplate");
        assertEquals("foo", routeTemplate.getTemplateParameters().get(0).getName());
        assertEquals("bar", routeTemplate.getTemplateParameters().get(1).getName());

        getMockEndpoint("mock:cheese").expectedBodiesReceived("Hello Cheese");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("foo", "one");
        parameters.put("bar", "cheese");
        String routeId = context.addRouteFromTemplate(null, "myTemplate", parameters);

        assertNotNull(routeId);
        assertNotEquals("route1", routeId, "Should not be named route1");
        assertEquals(2, context.getRouteDefinitions().size());
        assertEquals(2, context.getRoutes().size());
        assertEquals("Started", context.getRouteController().getRouteStatus(routeId).name());
        assertEquals("true", context.getRoute(routeId).getProperties().get(Route.TEMPLATE_PROPERTY));

        template.sendBody("direct:one", "Hello Cheese");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testCreateRouteFromRouteTemplateMissingParameter() throws Exception {
        assertEquals(1, context.getRouteTemplateDefinitions().size());

        RouteTemplateDefinition routeTemplate = context.getRouteTemplateDefinition("myTemplate");
        assertEquals("foo", routeTemplate.getTemplateParameters().get(0).getName());
        assertEquals("bar", routeTemplate.getTemplateParameters().get(1).getName());

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("foo", "one");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> context.addRouteFromTemplate(null, "myTemplate", parameters),
                "Should throw exception");

        assertEquals("Route template myTemplate the following mandatory parameters must be provided: bar", e.getMessage());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("myTemplate").templateParameter("foo").templateParameter("bar")
                        .from("direct:{{foo}}")
                        .to("mock:{{bar}}");
            }
        };
    }
}
