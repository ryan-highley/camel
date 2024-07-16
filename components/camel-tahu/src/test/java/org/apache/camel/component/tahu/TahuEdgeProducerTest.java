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

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TahuEdgeProducerTest extends TahuTestSupport {

    enum EdgeNodeTestProfile implements TahuTestSupport.TestProfile {

        SESSION_ESTABLISHMENT_TEST("edge SessionEstablishmentTest IamHost G2 E2 D2", true),
        SESSION_TERMINATION_TEST("edge SessionTerminationTest IamHost G2 E2 D2", true),
        SEND_DATA_TEST("edge SendDataTest IamHost G2 E2 D2", true),
        // SEND_COMPLEX_DATA_TEST("edge SendComplexDataTest IamHost G2 E2 D2", true),
        RECEIVE_COMMAND_TEST("edge ReceiveCommandTest IamHost G2 E2 D2", true),
        PRIMARY_HOST_TEST("edge PrimaryHostTest IamHost G2 E2 D2", false);
        ;

        private EdgeNodeTestProfile(String testConfig, boolean startHandlerBeforeTCKInitiate) {
            this.testConfig = testConfig;
            this.startHandlerBeforeTCKInitiate = startHandlerBeforeTCKInitiate;
        }

        public String getTestConfig() {
            return testConfig;
        }

        final String testConfig;
        final boolean startHandlerBeforeTCKInitiate;
    }

    private static final Logger LOG = LoggerFactory.getLogger(TahuEdgeProducerTest.class);

    @ParameterizedTest
    @EnumSource
    public void tckSessionTest(EdgeNodeTestProfile profile) throws Exception {
        spTckResultMockEndpoint.expectedBodyReceived().body(String.class).matches().simple("${body.contains('OVERALL: PASS')}");

        if (!profile.startHandlerBeforeTCKInitiate) {
            startHandlers();
        }

        profile.initiateTckTest();

        if (profile.startHandlerBeforeTCKInitiate) {
            startHandlers();
        }

        context.getRouteController().startRoute("node-data-test-route");
        context.getRouteController().startRoute("device-data-test-route");

        NotifyBuilder notify = new NotifyBuilder(context)
                .fromRoute("node-data-test-route").whenCompleted(1)
                .and()
                .fromRoute("device-data-test-route").whenCompleted(1)
                .create();

        Instant timeout = Instant.now().plusSeconds(15L);
        do {
            template.sendBody(TahuTestRouteBuilder.NODE_DATA_URI, null);
            template.sendBody(TahuTestRouteBuilder.DEVICE_DATA_URI, null);

            // pollForTestFailureLogs();
        } while (!pollForTestResults() && Instant.now().isBefore(timeout));

        assertTrue(notify.matchesWaitTime());

        stopHandlers();

        MockEndpoint.assertIsSatisfied(5, TimeUnit.SECONDS, spTckResultMockEndpoint);

        profile.resetTckTest();

    }

    @RouteFixture
    @Override
    public void createRouteBuilder(CamelContext context) throws Exception {
        LOG.trace("createRouteBuilder called");

        context.addRoutes(new TahuTestRouteBuilder());

        LOG.trace("createRouteBuilder complete");
    }

}
