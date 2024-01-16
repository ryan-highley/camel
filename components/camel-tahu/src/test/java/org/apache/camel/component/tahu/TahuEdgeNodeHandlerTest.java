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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.camel.component.tahu.SparkplugTCKService.SparkplugTCKMessageListener;
import org.apache.camel.component.tahu.TahuEdgeNodeHandler.PayloadBuilder;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.tahu.edge.sim.DataSimulator;
import org.eclipse.tahu.edge.sim.RandomDataSimulator;
import org.eclipse.tahu.message.BdSeqManager;
import org.eclipse.tahu.message.model.DeviceDescriptor;
import org.eclipse.tahu.message.model.EdgeNodeDescriptor;
import org.eclipse.tahu.message.model.MessageType;
import org.eclipse.tahu.message.model.SparkplugBPayload;
import org.eclipse.tahu.message.model.SparkplugDescriptor;
import org.eclipse.tahu.message.model.SparkplugMeta;
import org.eclipse.tahu.message.model.Topic;
import org.eclipse.tahu.model.MqttServerDefinition;
import org.eclipse.tahu.mqtt.MqttClientId;
import org.eclipse.tahu.mqtt.MqttServerName;
import org.eclipse.tahu.mqtt.MqttServerUrl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@SuppressWarnings("unused")
public class TahuEdgeNodeHandlerTest {

    private static final Logger LOG = LoggerFactory.getLogger(TahuEdgeNodeHandlerTest.class);

    @Order(1)
    @RegisterExtension
    public static HiveMQService hiveMQService = new LocalHiveMQService();

    @Order(2)
    @RegisterExtension
    public static SparkplugTCKService spTckService = new SparkplugTCKService(hiveMQService);

    private static final String GROUP_ID = "G2";
    private static final String EDGE_NODE_ID = "E2";
    private static final EdgeNodeDescriptor EDGE_NODE_DESCRIPTOR = new EdgeNodeDescriptor(GROUP_ID, EDGE_NODE_ID);
    private static final List<String> DEVICE_IDS = Arrays.asList("D2");
    private static final List<DeviceDescriptor> DEVICE_DESCRIPTORS
            = Arrays.asList(new DeviceDescriptor(EDGE_NODE_DESCRIPTOR, "D2"));
    private static final String PRIMARY_HOST_ID = "IamHost";
    private static final boolean USE_ALIASES = false;
    private static final Long REBIRTH_DEBOUNCE_DELAY = 5000L;

    private static final MqttServerName MQTT_SERVER_NAME_1 = new MqttServerName("Mqtt Server One");
    private static final String MQTT_CLIENT_ID_1 = "Sparkplug-Tahu-Compatible-Impl-One";
    // private static final MqttServerUrl MQTT_SERVER_URL_1
    //         = MqttServerUrl.getMqttServerUrlSafe(hiveMQService.getMqttHostAddress());
    private static final String USERNAME_1 = "admin";
    private static final String PASSWORD_1 = "changeme";
    private static final MqttServerName MQTT_SERVER_NAME_2 = new MqttServerName("Mqtt Server Two");
    private static final String MQTT_CLIENT_ID_2 = "Sparkplug-Tahu-Compatible-Impl-Two";
    // private static final MqttServerUrl MQTT_SERVER_URL_2 = MqttServerUrl.getMqttServerUrlSafe("tcp://localhost:1884");
    private static final String USERNAME_2 = "admin";
    private static final String PASSWORD_2 = "changeme";
    private static final int KEEP_ALIVE_TIMEOUT = 30;
    private static final Topic NDEATH_TOPIC
            = new Topic(SparkplugMeta.SPARKPLUG_B_TOPIC_PREFIX, GROUP_ID, EDGE_NODE_ID, MessageType.NDEATH);

    private static final List<MqttServerDefinition> mqttServerDefinitions = new ArrayList<>();

    private static DataSimulator dataSimulator = new RandomDataSimulator(
            10,
            new HashMap<SparkplugDescriptor, Integer>() {

                private static final long serialVersionUID = 1L;

                {
                    for (DeviceDescriptor deviceDescriptor : DEVICE_DESCRIPTORS) {
                        put(deviceDescriptor, 50);
                    }
                }
            });
    private static Map<String, Map<String, Object>> metricDataTypeMap;

    private TahuEdgeNodeHandler tahuEdgeNodeHandler;

    private SparkplugTCKMessageListener spTckMessageListener;

    @BeforeAll
    public static void beforeAll() throws Exception {
        mqttServerDefinitions
                .add(new MqttServerDefinition(
                        MQTT_SERVER_NAME_1, new MqttClientId(MQTT_CLIENT_ID_1, false),
                        MqttServerUrl.getMqttServerUrlSafe(hiveMQService.getMqttHostAddress()), USERNAME_1, PASSWORD_1,
                        KEEP_ALIVE_TIMEOUT, NDEATH_TOPIC));
        // mqttServerDefinitions
        // .add(new MqttServerDefinition(MQTT_SERVER_NAME_2, new
        // MqttClientId(MQTT_CLIENT_ID_2, false),
        // MQTT_SERVER_URL_2, USERNAME_2, PASSWORD_2, KEEP_ALIVE_TIMEOUT,
        // NDEATH_TOPIC));

        System.out.println("Starting the Sparkplug Edge Node");
        System.out.println("\tGroup ID: " + GROUP_ID);
        System.out.println("\tEdge Node ID: " + EDGE_NODE_ID);
        System.out.println("\tDevice IDs: " + DEVICE_IDS);
        System.out.println("\tPrimary Host ID: " + PRIMARY_HOST_ID);
        System.out.println("\tUsing Aliases: " + USE_ALIASES);
        System.out.println("\tRebirth Debounce Delay: " + REBIRTH_DEBOUNCE_DELAY);

        for (MqttServerDefinition mqttServerDefinition : mqttServerDefinitions) {
            System.out.println("\tMQTT Server Name: " + mqttServerDefinition.getMqttServerName());
            System.out.println("\tMQTT Client ID: " + mqttServerDefinition.getMqttClientId());
            System.out.println("\tMQTT Server URL: " + mqttServerDefinition.getMqttServerUrl());
            System.out.println("\tUsername: " + mqttServerDefinition.getUsername());
            System.out.println("\tPassword: ********");
            System.out.println("\tKeep Alive Timeout: " + mqttServerDefinition.getKeepAliveTimeout());
        }

        metricDataTypeMap = new HashMap<>();

        Map<String, Object> nodeMetricDataTypes = dataSimulator.getNodeBirthPayload(EDGE_NODE_DESCRIPTOR)
                .getMetrics().stream().collect(Collectors.toMap(m -> m.getName(), m -> m.getDataType()));

        metricDataTypeMap.put(EDGE_NODE_ID, nodeMetricDataTypes);

        for (DeviceDescriptor deviceDescriptor : DEVICE_DESCRIPTORS) {
            Map<String, Object> deviceMetricDataTypes = dataSimulator.getDeviceBirthPayload(deviceDescriptor)
                    .getMetrics().stream().collect(Collectors.toMap(m -> m.getName(), m -> m.getDataType()));

            metricDataTypeMap.put(deviceDescriptor.getDeviceId(), deviceMetricDataTypes);
        }
    }

    @BeforeEach
    public void beforeEach() throws Exception {
        BdSeqManager bdSeqManager = new CamelBdSeqManager(EDGE_NODE_DESCRIPTOR);
        ExecutorService handlerExecutorService = Executors.newSingleThreadExecutor();

        tahuEdgeNodeHandler = new TahuEdgeNodeHandler(
                EDGE_NODE_DESCRIPTOR, mqttServerDefinitions, PRIMARY_HOST_ID,
                USE_ALIASES, REBIRTH_DEBOUNCE_DELAY, metricDataTypeMap, handlerExecutorService, bdSeqManager);

        tahuEdgeNodeHandler.init();

        spTckMessageListener = spTckService.getSpTckMessageListener();

        spTckMessageListener.getLogMessages().clear();
        spTckMessageListener.getResultMessages().clear();
    }

    @AfterEach
    public void afterEach() throws Exception {
        tahuEdgeNodeHandler.stop();

        spTckMessageListener.getLogMessages().clear();
        spTckMessageListener.getResultMessages().clear();
    }

    @AfterAll
    public static void afterAll() throws Exception {
    }

    private String createEdgeTestParams(String testName) {
        return List.of("edge", testName, PRIMARY_HOST_ID, GROUP_ID, EDGE_NODE_ID, DEVICE_IDS.get(0)).stream()
                .collect(Collectors.joining(" "));
    }

    private void initiateTckTest(String testName) throws Exception {
        BlockingQueue<MqttMessage> logMessages = spTckMessageListener.getLogMessages();

        String testParams = createEdgeTestParams(testName);
        spTckService.sendTestControlMessage("NEW_TEST " + testParams);

        // assertThat(spTckMessageListener.getLogMessageBodies(), contains(containsString("Test started successfully:")));
        // MqttMessage logMessage = logMessages.poll(10L, TimeUnit.SECONDS);
        // assertThat("Test start message received", logMessage, notNullValue());
        // String logMessageText = new String(logMessage.getPayload(), StandardCharsets.UTF_8);
        // assertThat("Test start message has the expected text", logMessageText, containsString("Test started successfully:"));
    }

    private void pollForTestFailureLogs() throws Exception {
        BlockingQueue<MqttMessage> logMessages = spTckMessageListener.getLogMessages();

        MqttMessage logMessage = logMessages.poll(1L, TimeUnit.SECONDS);
        assertThat("No log messages received for test failures", logMessage, nullValue());
    }

    private void sendData() throws Exception {
        publishSimPayload(EDGE_NODE_DESCRIPTOR);

        for (DeviceDescriptor deviceDescriptor : DEVICE_DESCRIPTORS) {
            publishSimPayload(deviceDescriptor);
        }
    }

    private void publishSimPayload(EdgeNodeDescriptor end) {
        SparkplugBPayload simPayload;
        if (end.isDeviceDescriptor()) {
            simPayload = dataSimulator.getDeviceDataPayload((DeviceDescriptor) end);
        } else {
            simPayload = dataSimulator.getNodeDataPayload(end);
        }

        PayloadBuilder builder = tahuEdgeNodeHandler.new PayloadBuilder(end);

        builder.setTimestamp(simPayload.getTimestamp());
        builder.setUUID(simPayload.getUuid());
        builder.setBody(simPayload.getBody());
        builder.addMetrics(simPayload.getMetrics());

        builder.publish();
    }

    private boolean pollForTestResults() throws Exception {
        BlockingQueue<MqttMessage> resultMessages = spTckMessageListener.getResultMessages();

        MqttMessage resultMessage = resultMessages.poll(1L, TimeUnit.SECONDS);
        if (resultMessage == null) {
            return false;
        }

        assertThat("Result message received", resultMessage, notNullValue());
        String resultMessageText = new String(resultMessage.getPayload(), StandardCharsets.UTF_8);
        assertThat("Test passed", resultMessageText, containsString("OVERALL: PASS"));

        return true;
    }

    private void resetTckTest() throws Exception {
        spTckService.sendTestControlMessage("END_TEST");
    }

    @Test
    public void sessionEstablishmentTest() throws Exception {
        try {
            initiateTckTest("SessionEstablishmentTest");

            tahuEdgeNodeHandler.start();

            Instant timeout = Instant.now().plusSeconds(5L);
            do {
            } while (!pollForTestResults() && Instant.now().isBefore(timeout));
        } finally {
            resetTckTest();
        }
    }

    @Test
    public void sessionTerminationTest() throws Exception {
        try {
            initiateTckTest("SessionTerminationTest");

            tahuEdgeNodeHandler.start();

            pollForTestFailureLogs();

            tahuEdgeNodeHandler.stop();

            Instant timeout = Instant.now().plusSeconds(5L);
            do {
            } while (!pollForTestResults() && Instant.now().isBefore(timeout));
        } finally {
            resetTckTest();
        }
    }

    @Disabled
    @ParameterizedTest
    @ValueSource(strings = { "SendDataTest", "SendComplexDataTest", "ReceiveCommandTest" })
    public void sendDataTest(String testName) throws Exception {
        try {
            initiateTckTest(testName);

            tahuEdgeNodeHandler.start();

            Instant timeout = Instant.now().plusSeconds(5L);
            do {
                sendData();
            } while (!pollForTestResults() && Instant.now().isBefore(timeout));
        } finally {
            resetTckTest();
        }
    }

    @Disabled
    @Test
    public void primaryHostTest() throws Exception {
        try {
            tahuEdgeNodeHandler.start();

            initiateTckTest("PrimaryHostTest");

            Instant timeout = Instant.now().plusSeconds(5L);
            do {
                sendData();
            } while (!pollForTestResults() && Instant.now().isBefore(timeout));
        } finally {
            resetTckTest();
        }
    }
}
