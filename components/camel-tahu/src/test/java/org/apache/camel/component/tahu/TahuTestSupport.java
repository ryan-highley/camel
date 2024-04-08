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
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.camel.Exchange;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.tahu.SparkplugTCKService.SparkplugTCKMessageListener;
import org.apache.camel.component.tahu.TahuEdgeNodePublisherTest.TestProfile;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.apache.camel.test.infra.core.api.CamelTestSupportHelper;
import org.apache.camel.test.infra.core.api.ConfigurableContext;
import org.apache.camel.test.infra.core.api.ConfigurableRoute;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class TahuTestSupport
        implements CamelTestSupportHelper, ConfigurableContext, ConfigurableRoute, BeforeEachCallback, AfterEachCallback {

    private static final Logger LOG = LoggerFactory.getLogger(TahuTestSupport.class);

    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();

    @RegisterExtension
    public static SparkplugTCKService spTckService = new SparkplugTCKService(new TahuTestMockEndpointListener());

    @Override
    public CamelContextExtension getCamelContextExtension() {
        return camelContextExtension;
    }

    MockEndpoint spTckLogMockEndpoint = getMockEndpoint("mock:" + SparkplugTCKService.SPARKPLUG_TCK_LOG_TOPIC);
    MockEndpoint spTckResultMockEndpoint = getMockEndpoint("mock:" + SparkplugTCKService.SPARKPLUG_TCK_RESULT_TOPIC);

    public void initiateTckTest(TestProfile profile) throws MqttException, InterruptedException {
        LOG.trace("initiateTckTest called");

        LOG.debug("Initiating TCK Test with profile {}", profile);

        try {
            spTckLogMockEndpoint.expectedBodyReceived().body(String.class)
                    .contains("Test started successfully: " + profile.testConfig);

            spTckService.sendTestControlMessage("NEW_TEST " + profile.testConfig);

            spTckLogMockEndpoint.assertIsSatisfied();
        } finally {
            LOG.trace("initiateTckTest complete");
        }
    }

    private static final Pattern profilePattern = Pattern.compile("^.*profile=([A-Z_]+).*$");

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        LOG.trace("beforeEach called: extensionContext.getDisplayName() = {}", extensionContext.getDisplayName());

        Matcher profileMatcher = profilePattern.matcher(extensionContext.getDisplayName());
        if (profileMatcher.matches()) {
            String profileParam = profileMatcher.group(1);

            TestProfile profile = TestProfile.valueOf(profileParam);
            LOG.debug("Found TCK Test with profile {}", profile);

            extensionContext.getStore(Namespace.create(this.getClass())).put(TestProfile.class,
                    profile);

            spTckService.sendTestControlMessage(profile.testConfig);
        }

        LOG.trace("beforeEach complete");
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        LOG.trace("afterEach called: extensionContext.getDisplayName() = {}", extensionContext.getDisplayName());

        TestProfile profile = extensionContext.getStore(Namespace.create(this.getClass()))
                .get(TestProfile.class, TestProfile.class);
        LOG.debug("Found TCK Test with profile {}", profile);

        spTckService.sendTestControlMessage("END_TEST");

        LOG.trace("afterEach complete");
    }

    private static class TahuTestMockEndpointListener implements SparkplugTCKMessageListener, CamelTestSupportHelper {

        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {

            MockEndpoint mockEndpoint = resolveMandatoryEndpoint("mock:" + topic, MockEndpoint.class);

            Exchange exch = createExchangeWithBody(new String(message.getPayload(), StandardCharsets.UTF_8));

            org.apache.camel.Message camelMessage = exch.getMessage();

            camelMessage.setHeader("mqttMessage.Id", message.getId());
            camelMessage.setHeader("mqttMessage.Qos", message.getQos());
            camelMessage.setHeader("mqttMessage.Retained", message.isRetained());
            camelMessage.setHeader("mqttMessage.Duplicate", message.isDuplicate());

            mockEndpoint.handle(exch);
        }

        @Override
        public BlockingQueue<MqttMessage> getMessages(String topic) {
            MockEndpoint mockEndpoint = resolveMandatoryEndpoint("mock:" + topic, MockEndpoint.class);

            List<MqttMessage> receivedMsgs = mockEndpoint.getReceivedExchanges().stream().map(exch -> {
                MqttMessage message = new MqttMessage();

                org.apache.camel.Message camelMessage = exch.getMessage();

                message.setPayload(camelMessage.getBody(String.class).getBytes(StandardCharsets.UTF_8));

                message.setId(camelMessage.getHeader("mqttMessage.Id", Integer.class));
                message.setQos(camelMessage.getHeader("mqttMessage.Qos", Integer.class));
                message.setRetained(camelMessage.getHeader("mqttMessage.Retained", Boolean.class));

                return message;
            })
                    .collect(Collectors.toList());

            return new LinkedBlockingQueue<>(receivedMsgs);
        }

        @Override
        public CamelContextExtension getCamelContextExtension() {
            return camelContextExtension;
        }
    }
}
