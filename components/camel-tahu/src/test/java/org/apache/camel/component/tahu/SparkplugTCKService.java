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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Stream;

import org.apache.camel.test.infra.common.services.TestService;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.fail;

@Testcontainers
public class SparkplugTCKService implements TestService {

    private static final Logger LOG = LoggerFactory.getLogger(SparkplugTCKService.class);

    public static final String SPARKPLUG_TCK_TEST_CONTROL_TOPIC = "SPARKPLUG_TCK/TEST_CONTROL";
    public static final String SPARKPLUG_TCK_LOG_TOPIC = "SPARKPLUG_TCK/LOG";
    public static final String SPARKPLUG_TCK_RESULT_TOPIC = "SPARKPLUG_TCK/RESULT";

    @RegisterExtension
    HiveMQService hiveMQService = HiveMQService.Factory.INSTANCE;

    private final SparkplugTCKMessageListener spTckMessageListener;

    private MqttClient mqttClient;

    public SparkplugTCKService() {
        this(new SparkplugTCKMessageListenerImpl());
    }

    public SparkplugTCKService(SparkplugTCKMessageListener spTckMessageListener) {
        this.spTckMessageListener = spTckMessageListener;
    }

    String getMqttHostAddress() {
        if (!hiveMQService.isRunning()) {
            hiveMQService.initialize();
        }

        return hiveMQService.getMqttHostAddress();
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        LOG.trace("beforeAll called");

        try {
            mqttClient = new MqttClient(
                    getMqttHostAddress(), "Tahu-Test-" + MqttClient.generateClientId(), new MemoryPersistence());

            mqttClient.connect();

            mqttClient.subscribe(SPARKPLUG_TCK_RESULT_TOPIC, spTckMessageListener);
            mqttClient.subscribe(SPARKPLUG_TCK_LOG_TOPIC, spTckMessageListener);

        } catch (MqttException e) {
            fail("Exception caught connecting MQTT test client", e);
        } finally {
            LOG.trace("beforeAll completed");
        }
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        LOG.trace("afterAll called");
        try {
            mqttClient.disconnect();
            mqttClient.close();
        } catch (MqttException e) {
            fail("Exception caught disconnecting MQTT test client", e);
        } finally {
            LOG.trace("afterAll completed");
        }
    }

    public void sendTestControlMessage(String message) throws MqttException {
        LOG.trace("sendTestControlMessage called: message = {}", message);

        mqttClient.publish(SPARKPLUG_TCK_TEST_CONTROL_TOPIC, new MqttMessage(message.getBytes(StandardCharsets.UTF_8)));

        LOG.trace("sendTestControlMessage complete");
    }

    public SparkplugTCKMessageListener getSpTckMessageListener() {
        return spTckMessageListener;
    }

    public static interface SparkplugTCKMessageListener extends IMqttMessageListener {
        default BlockingQueue<MqttMessage> getLogMessages() {
            return getMessages(SPARKPLUG_TCK_LOG_TOPIC);
        }

        default BlockingQueue<MqttMessage> getResultMessages() {
            return getMessages(SPARKPLUG_TCK_RESULT_TOPIC);
        }

        default Stream<String> getLogMessageBodies() {
            return getMessageBodies(getLogMessages());
        }

        default Stream<String> getResultMessageBodies() {
            return getMessageBodies(getResultMessages());
        }

        default Stream<String> getMessageBodies(BlockingQueue<MqttMessage> messages) {
            return messages.stream().map(msg -> new String(msg.getPayload(), StandardCharsets.UTF_8));
        }

        public BlockingQueue<MqttMessage> getMessages(String topic);
    }

    static class SparkplugTCKMessageListenerImpl implements SparkplugTCKMessageListener {

        private final ConcurrentMap<String, BlockingQueue<MqttMessage>> messages = new ConcurrentHashMap<>();

        SparkplugTCKMessageListenerImpl() {
            messages.put(SPARKPLUG_TCK_LOG_TOPIC, new LinkedBlockingQueue<>());
            messages.put(SPARKPLUG_TCK_RESULT_TOPIC, new LinkedBlockingQueue<>());
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
            LOG.debug("Received message on topic {} : {}", topic, message);

            BlockingQueue<MqttMessage> topicQueue
                    = messages.computeIfAbsent(topic, (__) -> new LinkedBlockingQueue<MqttMessage>());
            topicQueue.offer(message);
        }

        private static final BlockingQueue<MqttMessage> EMPTY = new LinkedBlockingQueue<>(1);

        @Override
        public BlockingQueue<MqttMessage> getMessages(String topic) {
            return messages.getOrDefault(topic, EMPTY);
        }
    }

    @Override
    public void initialize() {
    }

    @Override
    public void registerProperties() {
    }

    @Override
    public void shutdown() {
    }
}
