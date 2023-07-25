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

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.camel.ConsumerTemplate;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.hamcrest.Matchers.*;

@SuppressWarnings("unused")
public class TahuComponentTest extends TahuTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(TahuComponentTest.class);

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    private final EventBusHelper eventBusHelper = EventBusHelper.getInstance();
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;
    @EndpointInject("mock:result")
    MockEndpoint mock;

    @Test
    @Disabled
    public void testTahu() throws Exception {
        mock.expectedMinimumMessageCount(5);

        // Trigger events to subscribers
        simulateEventTrigger();

        mock.await();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                TahuComponent tahuComponent = getContext().getComponent("tahu", TahuComponent.class);

                TahuEndpoint consumerEndpoint = (TahuEndpoint) tahuComponent.createEndpoint(
                        "tahu://H1?clientId=TestConsumerId&servers=TahuComponentTestServer:" + service.serviceAddress());

                TahuEndpoint producerEndpoint = (TahuEndpoint) tahuComponent
                        .createEndpoint("tahu://G2/E2?deviceIds=D2&clientId=TestProducerId&servers=TahuComponentTestServer:"
                                        + service.serviceAddress());

                from(consumerEndpoint).to(producerEndpoint).to("mock:result");
            }
        };
    }

    private void simulateEventTrigger() {
        final TimerTask task = new TimerTask() {
            @Override
            public void run() {
                final Date now = new Date();
                // publish events to the event bus
                eventBusHelper.publish(now);
            }
        };

        new Timer().scheduleAtFixedRate(task, 1000L, 1000L);
    }

    @Override
    public CamelContextExtension getCamelContextExtension() {
        return camelContextExtension;
    }

    @BeforeEach
    void setUpRequirements() {
        template = getCamelContextExtension().getProducerTemplate();
        consumer = getCamelContextExtension().getConsumerTemplate();
    }
}
