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

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.direct.DirectEndpoint;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
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

    @EndpointInject("mock:result")
    MockEndpoint mock;

    @EndpointInject("direct:node-birth")
    DirectEndpoint nodeBirthEndpoint;

    @EndpointInject("direct:device-birth")
    DirectEndpoint deviceBirthEndpoint;

    @EndpointInject("direct:node-data")
    DirectEndpoint nodeDataEndpoint;

    @EndpointInject("direct:device-data")
    DirectEndpoint deviceDataEndpoint;

    @Test
    @Disabled
    public void testTahu() throws Exception {
        mock.expectedMinimumMessageCount(5);

        mock.await();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                TahuComponent tahuComponent = getContext().getComponent("tahu", TahuComponent.class);

                TahuEndpoint hostAppEndpoint = (TahuEndpoint) tahuComponent.createEndpoint(
                        "tahu-host://H1?clientId=TestConsumerId&servers=TahuComponentTestServer:" + service.serviceAddress());

                TahuEndpoint edgeNodeEndpoint = (TahuEndpoint) tahuComponent
                        .createEndpoint("tahu://G2/E2&clientId=TestProducerId&servers=TahuComponentTestServer:"
                                        + service.serviceAddress());

                TahuEndpoint deviceEndpoint = (TahuEndpoint) tahuComponent.createEndpoint("tahu://G2/E2/D2");

                from(nodeBirthEndpoint).to(edgeNodeEndpoint).to(mock);
                from(nodeDataEndpoint).to(edgeNodeEndpoint).to(mock);
                from(deviceBirthEndpoint).to(deviceEndpoint).to(mock);
                from(deviceDataEndpoint).to(deviceEndpoint).to(mock);
            }
        };
    }

    @Override
    public CamelContextExtension getCamelContextExtension() {
        return camelContextExtension;
    }

}
