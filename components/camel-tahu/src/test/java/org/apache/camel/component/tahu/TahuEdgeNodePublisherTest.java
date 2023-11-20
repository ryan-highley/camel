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

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.direct.DirectEndpoint;
import org.apache.camel.component.log.LogEndpoint;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.paho.PahoComponent;
import org.apache.camel.component.paho.PahoConfiguration;
import org.apache.camel.component.paho.PahoEndpoint;
import org.apache.camel.component.paho.PahoPersistence;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.annotations.ContextFixture;
import org.eclipse.tahu.edge.sim.DataSimulator;
import org.eclipse.tahu.edge.sim.RandomDataSimulator;
import org.eclipse.tahu.host.HostApplication;
import org.eclipse.tahu.host.api.HostApplicationEventHandler;
import org.eclipse.tahu.message.model.DeviceDescriptor;
import org.eclipse.tahu.message.model.EdgeNodeDescriptor;
import org.eclipse.tahu.message.model.Message;
import org.eclipse.tahu.message.model.Metric;
import org.eclipse.tahu.message.model.SparkplugBPayload;
import org.eclipse.tahu.message.model.SparkplugBPayloadMap;
import org.eclipse.tahu.message.model.SparkplugDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNotNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SuppressWarnings("unused")
@Testcontainers
public class TahuEdgeNodePublisherTest extends TahuTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(TahuEdgeNodePublisherTest.class);

    @EndpointInject("mock:sparkplug-tck-result")
    MockEndpoint sparkplugTckResultEndpoint;

    @EndpointInject("direct:node-birth")
    DirectEndpoint nodeBirthEndpoint;

    @EndpointInject("direct:device-birth")
    DirectEndpoint deviceBirthEndpoint;

    @EndpointInject("direct:node-data")
    DirectEndpoint nodeDataEndpoint;

    @EndpointInject("direct:device-data")
    DirectEndpoint deviceDataEndpoint;

    @EndpointInject("paho:SPARKPLUG_TCK/TEST_CONTROL")
    PahoEndpoint testControlEndpoint;

    @EndpointInject("log:org.apache.camel.component.tahu.TahuEdgeNodePublisherTest?showAll=true&multiline=true&level=DEBUG&skipBodyLineSeparator=false")
    LogEndpoint logEndpoint;

    private ProducerTemplate template;

    private HostApplication hostApplication;
    private HostApplicationEventHandler camelTestHostApp = Mockito.mock(HostApplicationEventHandler.class);

    private EdgeNodeDescriptor edgeNodeDescriptor;
    private DeviceDescriptor deviceDescriptor;

    @ContextFixture
    public void configureContext(CamelContext context) {

        final String containerAddress = service.getMqttHostAddress();

        TahuConfiguration tahuConfig = new TahuConfiguration();

        tahuConfig.setServers("Mqtt Server One:" + containerAddress);
        tahuConfig.setUsername("admin");
        tahuConfig.setPassword("changeme");

        TahuComponent tahuComponent = context.getComponent("tahu", TahuComponent.class);
        tahuComponent.setConfiguration(tahuConfig);

        // hostApplication = new HostApplication(
        // camelTestHostApp, "IamHost", tahuConfig.getServerDefinitionList(), null, new
        // SparkplugBPayloadDecoder());
        // hostApplication.start();

        PahoComponent pahoComponent = context.getComponent("paho", PahoComponent.class);
        PahoConfiguration pahoConfiguration = pahoComponent.getConfiguration();
        pahoConfiguration.setBrokerUrl(containerAddress);
        pahoConfiguration.setPersistence(PahoPersistence.MEMORY);
    }

    @BeforeEach
    void setupTemplate() {
        template = camelContextExtension.getProducerTemplate();
    }

    // @AfterEach
    // void logMockInvocations() {
    // InvocationContainerImpl incon =
    // MockUtil.getInvocationContainer(camelTestHostApp);
    // incon.getInvocations().stream().forEach(i -> {
    // LOG.debug("Mock invocation: {}", i);
    // });
    // }

    // @Disabled
    @Test
    public void tckSessionEstablishmentTest() throws Exception {

        template.sendBody(testControlEndpoint, "NEW_TEST edge SessionEstablishmentTest IamHost G2 E2 D2");

        NotifyBuilder notify = new NotifyBuilder(getCamelContextExtension().getContext())
                .fromRoute("node-birth-test-route").whenCompleted(1)
                .and()
                .fromRoute("device-birth-test-route").whenCompleted(1)
                .and()
                .fromRoute("sparkplug-tck-result-route").whenCompleted(1)
                .create();

        template.sendBody(nodeBirthEndpoint, null);
        template.sendBody(deviceBirthEndpoint, null);

        template.sendBody(testControlEndpoint, "END_TEST");

        assertTrue(notify.matchesWaitTime());
    }

    @Disabled
    @Test
    public void testNodeBirth() throws Exception {

        NotifyBuilder notify = new NotifyBuilder(getCamelContextExtension().getContext())
                .fromRoute("node-birth-test-route").whenCompleted(1)
                .create();

        template.sendBody(nodeBirthEndpoint, null);

        assertTrue(notify.matchesWaitTime());

        ArgumentCaptor<Message> receivedMessage = ArgumentCaptor.forClass(Message.class);

        // Check for the "complete" call with a timeout. Other calls will be verified
        // below.
        verify(camelTestHostApp, timeout(3000)).onNodeBirthComplete(eq(edgeNodeDescriptor));

        verify(camelTestHostApp).onNodeBirthArrived(eq(edgeNodeDescriptor), receivedMessage.capture());

        verify(camelTestHostApp).onMessage(isNotNull(SparkplugDescriptor.class), isNotNull(Message.class));

        verify(camelTestHostApp, atLeastOnce()).onBirthMetric(isNotNull(SparkplugDescriptor.class),
                isNotNull(Metric.class));
    }

    @Disabled
    @Test
    public void testNodeData() throws Exception {

        template.sendBody(nodeDataEndpoint, null);

    }

    @Disabled
    @Test
    public void testDeviceBirth() throws Exception {

        template.sendBody(deviceBirthEndpoint, null);

    }

    @Disabled
    @Test
    public void testDeviceData() throws Exception {

        template.sendBody(deviceDataEndpoint, null);

    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            private DataSimulator dataSimulator;

            @Override
            public void configure() throws Exception {

                TahuEndpoint tahuEdgeNodeEndpoint = getCamelContext().getEndpoint("tahu:G2/E2", TahuEndpoint.class);

                TahuEndpoint tahuDeviceEndpoint = getCamelContext().getEndpoint("tahu:G2/E2/D2", TahuEndpoint.class);

                edgeNodeDescriptor = new EdgeNodeDescriptor(
                        tahuEdgeNodeEndpoint.getGroupId(),
                        tahuEdgeNodeEndpoint.getEdgeNode());
                deviceDescriptor = new DeviceDescriptor(edgeNodeDescriptor, tahuDeviceEndpoint.getDeviceId());

                dataSimulator = new RandomDataSimulator(10, new HashMap<SparkplugDescriptor, Integer>() {
                    {
                        put(deviceDescriptor, 50);
                    }
                });

                SparkplugBPayloadMap nBirthPayload = dataSimulator.getNodeBirthPayload(edgeNodeDescriptor);

                Map<String, Object> nodeMetricDataTypes = nBirthPayload.getMetrics().stream()
                        .map(m -> new Object[] {
                                tahuEdgeNodeEndpoint.getEdgeNode() + TahuConstants.MAJOR_SEPARATOR + m.getName(),
                                m.getDataType() })
                        .collect(Collectors.toMap(arr -> (String) arr[0], arr -> arr[1]));

                SparkplugBPayload dBirthPayload = dataSimulator.getDeviceBirthPayload(deviceDescriptor);

                Map<String, Object> deviceMetricDataTypes = dBirthPayload.getMetrics().stream()
                        .map(m -> new Object[] {
                                tahuDeviceEndpoint.getDeviceId() + TahuConstants.MAJOR_SEPARATOR + m.getName(),
                                m.getDataType() })
                        .collect(Collectors.toMap(arr -> (String) arr[0], arr -> arr[1]));

                Map<String, Object> metricDataTypes = new HashMap<>();
                metricDataTypes.putAll(nodeMetricDataTypes);
                metricDataTypes.putAll(deviceMetricDataTypes);
                tahuEdgeNodeEndpoint.setMetricDataTypes(Map.copyOf(metricDataTypes));

                from(nodeBirthEndpoint)
                        .id("node-birth-test-route")
                        .process((exch) -> processPayload(exch, nBirthPayload))
                        .to(tahuEdgeNodeEndpoint)
                        .to(logEndpoint)
                        .loop(3)
                        .to(nodeDataEndpoint)
                        .delay(1000)
                        .end();

                from(nodeDataEndpoint)
                        .id("node-data-test-route")
                        .process(getNodeDataPayload)
                        .to(tahuEdgeNodeEndpoint)
                        .to(logEndpoint);

                from(deviceBirthEndpoint)
                        .id("device-birth-test-route")
                        .process((exch) -> processPayload(exch, dBirthPayload))
                        .to(tahuDeviceEndpoint)
                        .to(logEndpoint)
                        .loop(3)
                        .to(deviceDataEndpoint)
                        .delay(1000)
                        .end();

                from(deviceDataEndpoint)
                        .id("device-data-test-route")
                        .process(getDeviceDataPayload)
                        .to(tahuDeviceEndpoint)
                        .to(logEndpoint);

                from("paho:SPARKPLUG_TCK/RESULT")
                        .id("sparkplug-tck-result-route")
                        .convertBodyTo(String.class, StandardCharsets.UTF_8.name())
                        .to(logEndpoint)
                        .to(sparkplugTckResultEndpoint);

                from("paho:SPARKPLUG_TCK/LOG")
                        .id("sparkplug-tck-log-route")
                        .to(logEndpoint);

            }

            private void processPayload(Exchange exch, SparkplugBPayload payload) {
                org.apache.camel.Message message = exch.getMessage();

                payload.getMetrics().forEach(m -> {
                    message.setHeader(TahuConstants.METRIC_HEADER_PREFIX + m.getName(), m.getValue());
                });

                message.setBody(payload.getBody(), byte[].class);
            }

            private Processor getNodeDataPayload = (exch) -> {
                processPayload(exch, dataSimulator.getNodeDataPayload(edgeNodeDescriptor));
            };

            private Processor getDeviceDataPayload = (exch) -> {
                processPayload(exch, dataSimulator.getDeviceDataPayload(deviceDescriptor));
            };

        };
    }

    @Override
    public CamelContextExtension getCamelContextExtension() {
        return camelContextExtension;
    }

}
