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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.tahu.SparkplugTCKService.SparkplugTCKMessageListener;
import org.apache.camel.test.infra.common.services.TestService;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
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
        implements TestService, ConfigurableContext, ConfigurableRoute, BeforeEachCallback, AfterEachCallback {

    private static final Logger LOG = LoggerFactory.getLogger(TahuTestSupport.class);

    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();

    @RegisterExtension
    public static SparkplugTCKService spTckService = new SparkplugTCKService(new TahuTestMockEndpointListener());

    MockEndpoint spTckLogMockEndpoint = camelContextExtension.getMockEndpoint("mock:" + SparkplugTCKService.SPARKPLUG_TCK_LOG_TOPIC);
    MockEndpoint spTckResultMockEndpoint = camelContextExtension.getMockEndpoint("mock:" + SparkplugTCKService.SPARKPLUG_TCK_RESULT_TOPIC);

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

            spTckService.sendTestControlMessage("NEW_TEST " + profile.testConfig);
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

    @Override
    public void initialize() {
    }

    @Override
    public void registerProperties() {
    }

    @Override
    public void shutdown() {
    }

    private static class TahuTestMockEndpointListener implements SparkplugTCKMessageListener {

        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
            camelContextExtension.getProducerTemplate().sendBody("mock:" + topic, message);
        }

        @Override
        public BlockingQueue<MqttMessage> getMessages(String topic) {
            MockEndpoint mockEndpoint = camelContextExtension.getMockEndpoint("mock:" + topic, false);

            List<MqttMessage> receivedMsgs = mockEndpoint.getReceivedExchanges().stream().map(
                    exch -> exch.getMessage().getBody(MqttMessage.class)).collect(Collectors.toList());

            return new LinkedBlockingQueue<>(receivedMsgs);
        }
    }

    enum TestProfile {

        SESSION_ESTABLISHMENT_TEST("edge SessionEstablishmentTest IamHost G2 E2 D2", false, false),
        SESSION_TERMINATION_TEST("edge SessionTerminationTest IamHost G2 E2 D2", false, true),
        // SEND_DATA_TEST("edge SendDataTest IamHost G2 E2 D2", true, false),
        // SEND_COMPLEX_DATA_TEST("edge SendComplexDataTest IamHost G2 E2 D2", true, false),
        // RECEIVE_COMMAND_TEST("edge ReceiveCommandTest IamHost G2 E2 D2", false, false),
        // PRIMARY_HOST_TEST("edge PrimaryHostTest IamHost G2 E2 D2", false, false)
        ;

        private TestProfile(String testConfig, boolean sendData, boolean disconnect) {
            this.testConfig = testConfig;
            this.sendData = sendData;
            this.disconnect = disconnect;
        }

        final String testConfig;
        final boolean sendData;
        final boolean disconnect;
    }
}
