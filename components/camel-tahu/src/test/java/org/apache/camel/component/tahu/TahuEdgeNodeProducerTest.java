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
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.test.infra.core.annotations.ContextFixture;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

// @Disabled
public class TahuEdgeNodeProducerTest extends TahuTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(TahuEdgeNodeProducerTest.class);

    @ParameterizedTest
    @EnumSource
    public void tckSessionTest(TahuTestSupport.TestProfile profile) throws Exception {
        CamelContext context = camelContextExtension.getContext();

        ProducerTemplate template = camelContextExtension.getProducerTemplate();

        spTckResultMockEndpoint.expectedBodyReceived().body(String.class).contains("OVERALL: PASS");

        if (profile.sendData) {
            NotifyBuilder notify = new NotifyBuilder(context)
                    .fromRoute("node-data-test-route").whenCompleted(1)
                    .and()
                    .fromRoute("device-data-test-route").whenCompleted(1)
                    .create();

            Instant timeout = Instant.now().plus(5L, ChronoUnit.SECONDS);

            do {
                template.sendBody(TahuEdgeNodeProducerRouteBuilder.NODE_DATA_URI, null);
                template.sendBody(TahuEdgeNodeProducerRouteBuilder.DEVICE_DATA_URI, null);
            } while (Instant.now().isBefore(timeout)
                    && !spTckResultMockEndpoint.await(1L, TimeUnit.SECONDS));

            assertTrue(notify.matchesWaitTime());
        }

        if (profile.disconnect) {
            context.hasServices(TahuEdgeNodeHandler.class).stream().forEach(tenh -> {
                LOG.debug("Suspending service {}", tenh);
                ServiceHelper.suspendService(tenh);
            });
        }

        MockEndpoint.assertIsSatisfied(5, TimeUnit.SECONDS, spTckResultMockEndpoint);

        if (profile.disconnect) {
            ServiceHelper.resumeServices(context.hasServices(TahuEdgeNodeHandler.class));
        }
    }

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

    @RouteFixture
    @Override
    public void createRouteBuilder(CamelContext context) throws Exception {
        LOG.trace("createRouteBuilder called");

        context.addRoutes(new TahuEdgeNodeProducerRouteBuilder());

        LOG.trace("createRouteBuilder complete");
    }

}
