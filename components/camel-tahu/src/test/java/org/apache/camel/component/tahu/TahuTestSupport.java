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
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.tahu.SparkplugTCKService.SparkplugTCKMessageListener;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.apache.camel.test.infra.core.annotations.ContextFixture;
import org.apache.camel.test.infra.core.api.ConfigurableContext;
import org.apache.camel.test.infra.core.api.ConfigurableRoute;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;

public abstract class TahuTestSupport implements ConfigurableContext, ConfigurableRoute {

    private static final Logger LOG = LoggerFactory.getLogger(TahuTestSupport.class);

    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();

    private static final SparkplugTCKMessageListener spTckMessageListener = new TahuTestMockEndpointListener();

    @RegisterExtension
    public static SparkplugTCKService spTckService = new SparkplugTCKService(spTckMessageListener);

    CamelContext context = camelContextExtension.getContext();
    ProducerTemplate template = camelContextExtension.getProducerTemplate();

    MockEndpoint spTckLogMockEndpoint
            = camelContextExtension.getMockEndpoint("mock:" + SparkplugTCKService.SPARKPLUG_TCK_LOG_TOPIC);
    MockEndpoint spTckResultMockEndpoint
            = camelContextExtension.getMockEndpoint("mock:" + SparkplugTCKService.SPARKPLUG_TCK_RESULT_TOPIC);

    @ContextFixture
    @Override
    public void configureContext(CamelContext context) throws Exception {
        LOG.trace("configureContext called");

        final String containerAddress = spTckService.getMqttHostAddress();

        TahuConfiguration tahuConfig = new TahuConfiguration();

        tahuConfig.setServers("Mqtt Server One:" + containerAddress);
        tahuConfig.setClientId("Sparkplug-Tahu-Compatible-Impl-One");
        tahuConfig.setCheckClientIdLength(false);
        tahuConfig.setUsername("admin");
        tahuConfig.setPassword("changeme");

        TahuComponent tahuComponent = context.getComponent("tahu", TahuComponent.class);
        tahuComponent.setConfiguration(tahuConfig);

        LOG.trace("configureContext complete");
    }

    private static class TahuTestMockEndpointListener implements SparkplugTCKMessageListener {

        private final ConcurrentMap<String, BlockingQueue<MqttMessage>> queuedMessages = new ConcurrentHashMap<>();

        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
            queuedMessages.computeIfAbsent(topic, __ -> new LinkedBlockingQueue<>()).add(message);

            String payload = new String(message.getPayload(), StandardCharsets.UTF_8);

            LOG.debug("TahuTestMockEndpointListener: topic {} payload {}", topic, payload);

            camelContextExtension.getProducerTemplate().sendBody("mock:" + topic, payload);
        }

        @Override
        public BlockingQueue<MqttMessage> getMessages(String topic) {
            return queuedMessages.getOrDefault(topic, new LinkedBlockingQueue<>());
        }
    }

    void pollForTestFailureLogs() throws Exception {
        BlockingQueue<MqttMessage> logMessages = spTckMessageListener.getLogMessages();

        MqttMessage logMessage = null;
        do {
            logMessage = logMessages.poll(1L, TimeUnit.SECONDS);
            if (logMessage == null)
                break;

            String logMessageText = new String(logMessage.getPayload(), StandardCharsets.UTF_8);
            assertThat("No unexpected log messages received for test failures", logMessageText,
                    anyOf(
                            containsString("Creating simulated host application"),
                            containsString("Waiting for the Edge and Device to come online"),
                            containsString("Edge Send Complex Data"),
                            containsString("Host Application is online, so using that")));
        } while (logMessage != null);
    }

    boolean pollForTestResults() throws Exception {
        BlockingQueue<MqttMessage> resultMessages = spTckMessageListener.getResultMessages();

        MqttMessage resultMessage = resultMessages.poll(1L, TimeUnit.SECONDS);
        if (resultMessage == null) {
            return false;
        }

        String resultMessageText = new String(resultMessage.getPayload(), StandardCharsets.UTF_8);
        assertThat("Test passed", resultMessageText, containsString("OVERALL: PASS"));

        return true;
    }

    void startHandlers() {
        ServiceHelper.startService(context.hasServices(TahuEdgeClientHandler.class));
    }

    void suspendHandlers() {
        ServiceHelper.suspendServices(context.hasServices(TahuEdgeClientHandler.class));
    }

    void resumeHandlers() {
        ServiceHelper.resumeServices(context.hasServices(TahuEdgeClientHandler.class));
    }

    void stopHandlers() {
        ServiceHelper.stopService(context.hasServices(TahuEdgeClientHandler.class));
    }

    static interface TestProfile {
        String getTestConfig();

        default void initiateTckTest() throws Exception {
            spTckService.sendTestControlMessage("NEW_TEST " + getTestConfig());
        }

        default void resetTckTest() throws Exception {
            spTckService.sendTestControlMessage("END_TEST");
        }
    }
}
