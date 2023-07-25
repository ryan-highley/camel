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
package org.apache.camel.component.tahu;

import java.util.List;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.eclipse.tahu.message.model.MetricDataType;
import org.eclipse.tahu.model.MqttServerDefinition;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@SuppressWarnings("unused")
public class TahuConfigurationTest extends TahuTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(TahuConfigurationTest.class);

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();

    @Test
    public void checkBasicEdgeNodeOptions() throws Exception {
        String uri
                = "tahu://Basic/EdgeNode?clientId=client1&primaryHostId=app1&username=amq&password=amq&useAliases=true&rebirthDebounceDelay=2000&keepAliveTimeout=20";

        TahuConfiguration configuration;
        try (TahuEndpoint endpoint = resolveMandatoryEndpoint(uri, TahuEndpoint.class)) {

            assertThat(endpoint, is(notNullValue()));
            assertThat(endpoint,
                    allOf(hasProperty("groupId", is("Basic")),
                            hasProperty("edgeNode", is("EdgeNode")),
                            hasProperty("deviceId", is(nullValue())),
                            hasProperty("hostId", is(nullValue())),
                            hasProperty("primaryHostId", is("app1")),
                            hasProperty("useAliases", is(true))));

            configuration = endpoint.getConfiguration();

            assertThat(configuration, is(notNullValue()));
            assertThat(configuration,
                    allOf(hasProperty("clientId", is("client1")),
                            hasProperty("username", is("amq")),
                            hasProperty("password", is("amq")),
                            hasProperty("rebirthDebounceDelay", is(2000L)),
                            hasProperty("keepAliveTimeout", is(20))));
        }
    }

    @Test
    public void checkBasicDeviceOptions() throws Exception {
        String uri
                = "tahu://Basic/EdgeNodeDevice/Device?clientId=client1&primaryHostId=app1&username=amq&password=amq&useAliases=true&rebirthDebounceDelay=2000&keepAliveTimeout=20";

        TahuConfiguration configuration;
        try (TahuEndpoint endpoint = resolveMandatoryEndpoint(uri, TahuEndpoint.class)) {

            assertThat(endpoint, is(notNullValue()));
            assertThat(endpoint,
                    allOf(hasProperty("groupId", is("Basic")),
                            hasProperty("edgeNode", is("EdgeNodeDevice")),
                            hasProperty("deviceId", is("Device")),
                            hasProperty("hostId", is(nullValue())),
                            hasProperty("primaryHostId", is("app1")),
                            hasProperty("useAliases", is(true))));

            configuration = endpoint.getConfiguration();

            assertThat(configuration, is(notNullValue()));
            assertThat(configuration,
                    allOf(hasProperty("clientId", is("client1")),
                            hasProperty("username", is("amq")),
                            hasProperty("password", is("amq")),
                            hasProperty("rebirthDebounceDelay", is(2000L)),
                            hasProperty("keepAliveTimeout", is(20))));
        }
    }

    @Test
    public void checkEdgeNodeMetricsOptions() throws Exception {
        String uri
                = "tahu://Basic/EdgeNodeMetrics?metric.EdgeNodeMetrics/NT-1/int8=Int8&metric.EdgeNodeMetrics/NT-1/string=String&metric.EdgeNodeMetrics/NT-1/int64=Int64&metric.EdgeNodeMetrics/NT-2/int16=Int16&metric.EdgeNodeMetrics/NT-2/text=Text&metric.EdgeNodeMetrics/NT-2/uint32=UInt32&clientId=client1&primaryHostId=app1&username=amq&password=amq&useAliases=true&rebirthDebounceDelay=2000&keepAliveTimeout=20";

        TahuConfiguration configuration;
        try (TahuEndpoint endpoint = resolveMandatoryEndpoint(uri, TahuEndpoint.class)) {

            assertThat(endpoint, is(notNullValue()));
            assertThat(endpoint,
                    allOf(hasProperty("groupId", is("Basic")),
                            hasProperty("edgeNode", is("EdgeNodeMetrics")),
                            hasProperty("deviceId", is(nullValue())),
                            hasProperty("hostId", is(nullValue())),
                            hasProperty("primaryHostId", is("app1")),
                            hasProperty("useAliases", is(true))));

            Map<String, Object> metricDataTypes = endpoint.getMetricDataTypes();
            assertThat(metricDataTypes, hasEntry("EdgeNodeMetrics/NT-1/int8", MetricDataType.Int8.name()));
            assertThat(metricDataTypes, hasEntry("EdgeNodeMetrics/NT-1/string", MetricDataType.String.name()));
            assertThat(metricDataTypes, hasEntry("EdgeNodeMetrics/NT-1/int64", MetricDataType.Int64.name()));
            assertThat(metricDataTypes, hasEntry("EdgeNodeMetrics/NT-2/int16", MetricDataType.Int16.name()));
            assertThat(metricDataTypes, hasEntry("EdgeNodeMetrics/NT-2/text", MetricDataType.Text.name()));
            assertThat(metricDataTypes, hasEntry("EdgeNodeMetrics/NT-2/uint32", MetricDataType.UInt32.name()));
            assertThat(metricDataTypes.size(), is(6));

            configuration = endpoint.getConfiguration();

            assertThat(configuration, is(notNullValue()));
            assertThat(configuration,
                    allOf(hasProperty("clientId", is("client1")),
                            hasProperty("username", is("amq")),
                            hasProperty("password", is("amq")),
                            hasProperty("rebirthDebounceDelay", is(2000L)),
                            hasProperty("keepAliveTimeout", is(20))));
        }
    }

    @Test
    public void checkBasicHostAppOptions() throws Exception {
        String uri
                = "tahu://BasicHostApp?clientId=client1&username=amq&password=amq&useAliases=true&rebirthDebounceDelay=2000&keepAliveTimeout=20";

        TahuConfiguration configuration;
        try (TahuEndpoint endpoint = resolveMandatoryEndpoint(uri, TahuEndpoint.class)) {

            assertThat(endpoint, is(notNullValue()));
            assertThat(endpoint,
                    allOf(hasProperty("groupId", is(nullValue())),
                            hasProperty("edgeNode", is(nullValue())),
                            hasProperty("deviceId", is(nullValue())),
                            hasProperty("hostId", is("BasicHostApp")),
                            hasProperty("primaryHostId", is(nullValue())),
                            hasProperty("useAliases", is(true))));

            configuration = endpoint.getConfiguration();

            assertThat(configuration, is(notNullValue()));
            assertThat(configuration,
                    allOf(hasProperty("clientId", is("client1")),
                            hasProperty("username", is("amq")),
                            hasProperty("password", is("amq")),
                            hasProperty("rebirthDebounceDelay", is(2000L)),
                            hasProperty("keepAliveTimeout", is(20))));
        }
    }

    @Test
    public void checkEndpointUriServerDefs() {
        String uri
                = "tahu://EndpointUri/ServerDefs?servers=serverName1:clientId1:tcp://localhost:1883,serverName2:clientId1:tcp://localhost:1884";

        TahuEndpoint endpoint = getMandatoryEndpoint(uri, TahuEndpoint.class);

        assertThat(endpoint, is(notNullValue()));
        assertThat(endpoint, allOf(hasProperty("groupId", is("EndpointUri")),
                hasProperty("edgeNode", is("ServerDefs"))));

        TahuConfiguration configuration = endpoint.getConfiguration();

        assertThat(configuration, is(notNullValue()));

        List<MqttServerDefinition> serverDefs = configuration.getServerDefinitionList();
        assertThat(serverDefs, hasSize(2));

        MqttServerDefinition serverDef = serverDefs.get(0);
        assertThat(serverDef.getMqttServerName(), hasProperty("mqttServerName", is("serverName1")));
        assertThat(serverDef.getMqttServerUrl(), hasProperty("mqttServerUrl", is("tcp://localhost:1883")));

        serverDef = serverDefs.get(1);
        assertThat(serverDef.getMqttServerName(), hasProperty("mqttServerName", is("serverName2")));
        assertThat(serverDef.getMqttServerUrl(), hasProperty("mqttServerUrl", is("tcp://localhost:1884")));

        assertThat(serverDefs,
                hasItems(allOf(hasProperty("mqttClientId",
                        hasProperty("mqttClientId", is("clientId1"))),
                        hasProperty("username", is(nullValue())),
                        hasProperty("password", is(nullValue())),
                        hasProperty("keepAliveTimeout",
                                is(configuration.getKeepAliveTimeout())),
                        hasProperty("ndeathTopic", is(nullValue())))));
    }

    @Test
    public void checkEndpointUriServerDefsSharedClientId() {
        String uri
                = "tahu://EndpointUri/ServerDefsSharedClientId?clientId=clientId2&username=user1&password=mysecretpassw0rd&keepAliveTimeout=45&servers=serverName1:tcp://localhost:1883,serverName2:tcp://localhost:1884";

        TahuEndpoint endpoint = getMandatoryEndpoint(uri, TahuEndpoint.class);

        assertThat(endpoint, is(notNullValue()));
        assertThat(endpoint,
                allOf(hasProperty("groupId", is("EndpointUri")),
                        hasProperty("edgeNode", is("ServerDefsSharedClientId"))));

        TahuConfiguration configuration = endpoint.getConfiguration();

        assertThat(configuration, is(notNullValue()));
        assertThat(configuration,
                allOf(hasProperty("clientId", is("clientId2")), hasProperty("username", is("user1")),
                        hasProperty("password", is("mysecretpassw0rd")),
                        hasProperty("keepAliveTimeout", is(45))));

        List<MqttServerDefinition> serverDefs = configuration.getServerDefinitionList();
        assertThat(serverDefs, hasSize(2));

        MqttServerDefinition serverDef = serverDefs.get(0);
        assertThat(serverDef.getMqttServerName(), hasProperty("mqttServerName", is("serverName1")));
        assertThat(serverDef.getMqttServerUrl(), hasProperty("mqttServerUrl", is("tcp://localhost:1883")));

        serverDef = serverDefs.get(1);
        assertThat(serverDef.getMqttServerName(), hasProperty("mqttServerName", is("serverName2")));
        assertThat(serverDef.getMqttServerUrl(), hasProperty("mqttServerUrl", is("tcp://localhost:1884")));

        assertThat(serverDefs,
                hasItems(allOf(hasProperty("mqttClientId",
                        hasProperty("mqttClientId", is("clientId2"))),
                        hasProperty("username", is("user1")),
                        hasProperty("password", is("mysecretpassw0rd")),
                        hasProperty("keepAliveTimeout", is(45)),
                        hasProperty("ndeathTopic", is(nullValue())))));
    }

    @Test
    public void checkEndpointUriServerDefsNoClientId() {
        String uri
                = "tahu://EndpointUri/ServerDefsNoClientId?servers=serverName1:tcp://localhost:1883,serverName2:tcp://localhost:1884";

        TahuEndpoint endpoint = getMandatoryEndpoint(uri, TahuEndpoint.class);

        assertThat(endpoint, is(notNullValue()));
        assertThat(endpoint,
                allOf(hasProperty("groupId", is("EndpointUri")),
                        hasProperty("edgeNode", is("ServerDefsNoClientId"))));

        TahuConfiguration configuration = endpoint.getConfiguration();

        assertThat(configuration, is(notNullValue()));

        List<MqttServerDefinition> serverDefs = configuration.getServerDefinitionList();
        assertThat(serverDefs, hasSize(2));

        MqttServerDefinition serverDef = serverDefs.get(0);
        assertThat(serverDef.getMqttServerName(), hasProperty("mqttServerName", is("serverName1")));
        assertThat(serverDef.getMqttServerUrl(), hasProperty("mqttServerUrl", is("tcp://localhost:1883")));

        serverDef = serverDefs.get(1);
        assertThat(serverDef.getMqttServerName(), hasProperty("mqttServerName", is("serverName2")));
        assertThat(serverDef.getMqttServerUrl(), hasProperty("mqttServerUrl", is("tcp://localhost:1884")));

        assertThat(serverDefs,
                hasItems(allOf(hasProperty("mqttClientId",
                        hasProperty("mqttClientId", startsWith("Camel"))),
                        hasProperty("username", is(nullValue())),
                        hasProperty("password", is(nullValue())),
                        hasProperty("keepAliveTimeout",
                                is(configuration.getKeepAliveTimeout())),
                        hasProperty("ndeathTopic", is(nullValue())))));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        // No routes for configuration tests
        return null;
    }

    @Override
    public CamelContextExtension getCamelContextExtension() {
        return camelContextExtension;
    }
}
