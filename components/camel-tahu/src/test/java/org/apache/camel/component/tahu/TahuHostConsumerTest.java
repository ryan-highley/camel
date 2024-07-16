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

import org.apache.camel.CamelContext;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Disabled
public class TahuHostConsumerTest extends TahuTestSupport {

    enum HostAppTestProfile implements TahuTestSupport.TestProfile {
        SESSION_ESTABLISHMENT_TEST("host SessionEstablishmentTest", false),
        SESSION_TERMINATION_TEST("host SessionTerminationTest TestHostApp", true),
        // SEND_COMMAND_TEST("host SendCommandTest G2 E2 D2", false),
        // EDGE_SESSION_TERMINATION_TEST("host EdgeSessionTerminationTest G2 E2 D2", false),
        // MESSAGE_ORDERING_TEST("host MessageOrderingTest G2 E2 D2 5000", false),
        ;

        private HostAppTestProfile(String testConfig, boolean disconnect) {
            this.testConfig = testConfig;
            this.disconnect = disconnect;
        }

        public String getTestConfig() {
            return testConfig;
        }

        final String testConfig;
        final boolean disconnect;
    }

    private static final Logger LOG = LoggerFactory.getLogger(TahuHostConsumerTest.class);

    @ParameterizedTest
    @EnumSource
    public void tckSessionTest(HostAppTestProfile profile) throws Exception {
        spTckResultMockEndpoint.expectedBodyReceived().body(String.class).matches().simple("${body.contains('OVERALL: PASS')}");

        profile.initiateTckTest();

        context.getRouteController().startRoute("node-data-test-route");
        context.getRouteController().startRoute("device-data-test-route");

    }

    @RouteFixture
    @Override
    public void createRouteBuilder(CamelContext context) throws Exception {
        LOG.trace("createRouteBuilder called");

        context.addRoutes(new TahuTestRouteBuilder());
        context.addRoutes(new TahuHostConsumerRouteBuilder());

        LOG.trace("createRouteBuilder complete");
    }

}
