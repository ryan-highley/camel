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
import org.apache.camel.EndpointInject;
import org.apache.camel.component.direct.DirectEndpoint;
import org.apache.camel.component.log.LogEndpoint;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Disabled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.hamcrest.Matchers.*;

@Disabled
@SuppressWarnings("unused")
public class TahuHostAppConsumerTest extends TahuTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(TahuHostAppConsumerTest.class);

    @EndpointInject("mock:sparkplug-tck-result")
    MockEndpoint sparkplugTckResultEndpoint;

    @EndpointInject("direct:node-birth")
    DirectEndpoint nodeBirthEndpoint;

    @EndpointInject("direct:device-birth")
    DirectEndpoint deviceBirthEndpoint;

    @EndpointInject("direct:node-data")
    DirectEndpoint nodeDataEndpoint;

    @EndpointInject("direct:device-data")
    DirectEndpoint deviceDataEndpoint;

    @EndpointInject("log:org.apache.camel.component.tahu.TahuHostAppConsumerTest?showAll=true&multiline=true&level=DEBUG&skipBodyLineSeparator=false")
    LogEndpoint logEndpoint;

    @Override
    public void configureContext(CamelContext context) throws Exception {
    }

    @Override
    public void createRouteBuilder(CamelContext context) throws Exception {
        LOG.trace("createRouteBuilder called");

        context.addRoutes(new TahuHostAppConsumerRouteBuilder());

        LOG.trace("createRouteBuilder complete");
    }

}
