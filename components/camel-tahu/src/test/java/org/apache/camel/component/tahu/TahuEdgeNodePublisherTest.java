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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
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
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.test.infra.core.annotations.ContextFixture;
import org.eclipse.tahu.edge.sim.DataSimulator;
import org.eclipse.tahu.edge.sim.RandomDataSimulator;
import org.eclipse.tahu.message.model.DeviceDescriptor;
import org.eclipse.tahu.message.model.EdgeNodeDescriptor;
import org.eclipse.tahu.message.model.SparkplugBPayload;
import org.eclipse.tahu.message.model.SparkplugDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.Assert.assertTrue;

@Testcontainers
public class TahuEdgeNodePublisherTest extends TahuTestSupport {

    @EndpointInject("mock:direct:sparkplug-tck-result")
    MockEndpoint spTckResultMockEndpoint;

    @EndpointInject("mock:direct:sparkplug-tck-log")
    MockEndpoint spTckLogMockEndpoint;

    @EndpointInject("direct:node-birth")
    DirectEndpoint nodeBirthEndpoint;

    @EndpointInject("direct:device-birth")
    DirectEndpoint deviceBirthEndpoint;

    @EndpointInject("direct:node-data")
    DirectEndpoint nodeDataEndpoint;

    @EndpointInject("direct:device-data")
    DirectEndpoint deviceDataEndpoint;

    @EndpointInject("log:org.apache.camel.component.tahu.TahuEdgeNodePublisherTest?showAll=true&multiline=true&level=DEBUG&skipBodyLineSeparator=false")
    LogEndpoint logEndpoint;

    private PahoEndpoint spTckTestControlEndpoint;
    private PahoEndpoint spTckLogEndpoint;
    private PahoEndpoint spTckResultEndpoint;

    private EdgeNodeDescriptor edgeNodeDescriptor;
    private DeviceDescriptor deviceDescriptor;

    @ContextFixture
    public void configureContext(CamelContext context) throws Exception {

        final String containerAddress = service.getMqttHostAddress();

        TahuConfiguration tahuConfig = new TahuConfiguration();

        tahuConfig.setServers("Mqtt Server One:" + containerAddress);
        tahuConfig.setClientId("Sparkplug-Tahu-Compatible-Impl-One");
        tahuConfig.setCheckClientIdLength(false);
        tahuConfig.setUsername("admin");
        tahuConfig.setPassword("changeme");

        TahuComponent tahuComponent = context.getComponent("tahu", TahuComponent.class);
        tahuComponent.setConfiguration(tahuConfig);

        PahoConfiguration pahoConfiguration = new PahoConfiguration();
        pahoConfiguration.setBrokerUrl(containerAddress);

        PahoComponent pahoComponent = context.getComponent("paho", PahoComponent.class);
        pahoComponent.setConfiguration(pahoConfiguration);

        spTckTestControlEndpoint = context.getEndpoint("paho:SPARKPLUG_TCK/TEST_CONTROL", PahoEndpoint.class);
        spTckLogEndpoint = context.getEndpoint("paho:SPARKPLUG_TCK/LOG", PahoEndpoint.class);
        spTckResultEndpoint = context.getEndpoint("paho:SPARKPLUG_TCK/RESULT", PahoEndpoint.class);

    }

    private ProducerTemplate template;

    @BeforeEach
    public void beforeEach() {
        MockEndpoint.resetMocks(camelContextExtension.getContext());

        template = camelContextExtension.getProducerTemplate();
    }

    private enum TestProfile {

        SESSION_ESTABLISHMENT_TEST("SessionEstablishmentTest", false, false),
        SESSION_TERMINATION_TEST("SessionTerminationTest", false, true),
        // SEND_DATA_TEST("SendDataTest", true, false),
        // SEND_COMPLEX_DATA_TEST("SendComplexDataTest", true, false),
        // RECEIVE_COMMAND_TEST("ReceiveCommandTest", false, false),
        // PRIMARY_HOST_TEST("PrimaryHostTest", false, false)
        ;

        private TestProfile(String testName, boolean sendData, boolean disconnect) {
            this.testName = testName;
            this.sendData = sendData;
            this.disconnect = disconnect;
        }

        private String testName;
        private boolean sendData;
        private boolean disconnect;
    }

    @ParameterizedTest
    @EnumSource
    public void tckSessionTest(TestProfile profile) throws Exception {

        MockEndpoint resultMock = spTckResultMockEndpoint;
        // MockEndpoint resultMock = camelContextExtension.getMockEndpoint(spTckResultMockEndpoint.getEndpointUri());
        // resultMock.expectedBodyReceived().body(String.class).contains("OVERALL: PASS");

        MockEndpoint logMock = spTckLogMockEndpoint;
        // MockEndpoint logMock = camelContextExtension.getMockEndpoint(spTckLogMockEndpoint.getEndpointUri());
        // logMock.expectedBodyReceived().body(String.class)
        //         .contains("Test started successfully: edge " + profile.testName);
        // logMock.setResultWaitTime(5000L);

        template.start();
        template.sendBody(spTckTestControlEndpoint, "NEW_TEST edge " + profile.testName + " IamHost G2 E2 D2");

        NotifyBuilder notify = new NotifyBuilder(camelContextExtension.getContext())
                .fromRoute("node-birth-test-route").whenCompleted(1)
                .and()
                .fromRoute("device-birth-test-route").whenCompleted(1)
                .create();

        template.sendBody(nodeBirthEndpoint, null);
        template.sendBody(deviceBirthEndpoint, null);

        assertTrue(notify.matchesWaitTime());

        if (profile.sendData) {
            Instant timeout = Instant.now().plus(5L, ChronoUnit.SECONDS);
            do {
                template.sendBody(nodeDataEndpoint, null);
                template.sendBody(deviceDataEndpoint, null);
            } while (Instant.now().isBefore(timeout) && !resultMock.await(1, TimeUnit.SECONDS));
        }

        if (profile.disconnect) {
            camelContextExtension.getContext().hasServices(TahuEdgeNodeHandler.class).stream().forEach(tenh -> {
                LoggerFactory.getLogger(TahuEdgeNodePublisherTest.class).debug("Suspending service {}", tenh);
                ServiceHelper.suspendService(tenh);
            });
        }

        try {
            MockEndpoint.assertIsSatisfied(5, TimeUnit.SECONDS, resultMock, logMock);
        } finally {
            template.sendBody(spTckTestControlEndpoint, "END_TEST");
        }

        // if (profile.disconnect) {
        //     ServiceHelper.resumeServices(camelContextExtension.getContext().hasServices(TahuEdgeNodeHandler.class));
        // }

        template.stop();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            private DataSimulator dataSimulator;

            private TahuEndpoint tahuEdgeNodeEndpoint;
            private TahuEndpoint tahuDeviceEndpoint;

            @Override
            public void configure() throws Exception {

                tahuEdgeNodeEndpoint = getCamelContext().getEndpoint("tahu:G2/E2?primaryHostId=IamHost",
                        TahuEndpoint.class);

                // tahuEdgeNodeEndpoint.setBdSeqManager(new AtomicBdSeqManager());

                tahuDeviceEndpoint = getCamelContext().getEndpoint("tahu:G2/E2/D2", TahuEndpoint.class);

                edgeNodeDescriptor = new EdgeNodeDescriptor(
                        tahuEdgeNodeEndpoint.getGroupId(),
                        tahuEdgeNodeEndpoint.getEdgeNode());
                deviceDescriptor = new DeviceDescriptor(edgeNodeDescriptor, tahuDeviceEndpoint.getDeviceId());

                dataSimulator = new RandomDataSimulator(10, new HashMap<SparkplugDescriptor, Integer>() {
                    {
                        put(deviceDescriptor, 5);
                    }
                });

                // Create the Birth payloads to capture the random metric configuration from the
                // data simulator.
                // This would normally be set on the Edge Node endpoint via "metrics."
                // parameters.
                Map<String, Object> nodeMetricDataTypes = dataSimulator.getNodeBirthPayload(edgeNodeDescriptor)
                        .getMetrics().stream()
                        .map(m -> new Object[] {
                                tahuEdgeNodeEndpoint.getEdgeNode() + TahuConstants.MAJOR_SEPARATOR + m.getName(),
                                m.getDataType() })
                        .collect(Collectors.toMap(arr -> (String) arr[0], arr -> arr[1]));

                Map<String, Object> deviceMetricDataTypes = dataSimulator.getDeviceBirthPayload(deviceDescriptor)
                        .getMetrics().stream()
                        .map(m -> new Object[] {
                                tahuDeviceEndpoint.getDeviceId() + TahuConstants.MAJOR_SEPARATOR + m.getName(),
                                m.getDataType() })
                        .collect(Collectors.toMap(arr -> (String) arr[0], arr -> arr[1]));

                Map<String, Object> metricDataTypes = new HashMap<>();
                metricDataTypes.putAll(nodeMetricDataTypes);
                metricDataTypes.putAll(deviceMetricDataTypes);
                tahuEdgeNodeEndpoint.setMetricDataTypes(metricDataTypes);

                from(nodeBirthEndpoint)
                        .id("node-birth-test-route")
                        .process(getNodeBirthPayload)
                        .to(tahuEdgeNodeEndpoint);

                from(nodeDataEndpoint)
                        .id("node-data-test-route")
                        .process(getNodeDataPayload)
                        .to(tahuEdgeNodeEndpoint);

                from(deviceBirthEndpoint)
                        .id("device-birth-test-route")
                        .process(getDeviceBirthPayload)
                        .to(tahuDeviceEndpoint);

                from(deviceDataEndpoint)
                        .id("device-data-test-route")
                        .process(getDeviceDataPayload)
                        .to(tahuDeviceEndpoint);

                from(spTckLogEndpoint)
                        .id("sparkplug-tck-log-route")
                        .convertBodyTo(String.class, StandardCharsets.UTF_8.name())
                        .to(logEndpoint)
                        .to(spTckLogMockEndpoint);

                from(spTckResultEndpoint)
                        .id("sparkplug-tck-result-route")
                        .convertBodyTo(String.class, StandardCharsets.UTF_8.name())
                        .to(logEndpoint)
                        .to(spTckResultMockEndpoint);

            }

            private void processPayload(Exchange exch, String messageType, SparkplugBPayload payload) {
                org.apache.camel.Message message = exch.getMessage();

                message.setHeader(TahuConstants.MESSAGE_TYPE, messageType);

                Optional.ofNullable(payload.getUuid())
                        .ifPresent(uuid -> message.setHeader(TahuConstants.MESSAGE_UUID, uuid));
                Optional.ofNullable(payload.getTimestamp())
                        .ifPresent(timestamp -> message.setHeader(TahuConstants.MESSAGE_TIMESTAMP, timestamp));
                Optional.ofNullable(payload.getSeq())
                        .ifPresent(seq -> message.setHeader(TahuConstants.MESSAGE_SEQUENCE_NUMBER, seq));

                payload.getMetrics().forEach(m -> {
                    message.setHeader(TahuConstants.METRIC_HEADER_PREFIX + m.getName(), m.getValue());
                });

                Optional.ofNullable(payload.getBody()).ifPresent(body -> message.setBody(body, byte[].class));
            }

            private Processor getNodeBirthPayload = (exch) -> {
                processPayload(exch, "NBIRTH", dataSimulator.getNodeBirthPayload(edgeNodeDescriptor));
            };

            private Processor getNodeDataPayload = (exch) -> {
                processPayload(exch, "NDATA", dataSimulator.getNodeDataPayload(edgeNodeDescriptor));
            };

            private Processor getDeviceBirthPayload = (exch) -> {
                processPayload(exch, "DBIRTH", dataSimulator.getDeviceBirthPayload(deviceDescriptor));
            };

            private Processor getDeviceDataPayload = (exch) -> {
                processPayload(exch, "DDATA", dataSimulator.getDeviceDataPayload(deviceDescriptor));
            };

        };
    }

}
