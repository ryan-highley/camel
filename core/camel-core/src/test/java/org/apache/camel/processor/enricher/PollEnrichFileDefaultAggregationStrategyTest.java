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
package org.apache.camel.processor.enricher;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class PollEnrichFileDefaultAggregationStrategyTest extends ContextTestSupport {

    @Test
    public void testPollEnrichDefaultAggregationStrategyBody() throws Exception {
        getMockEndpoint("mock:start").expectedBodiesReceived("Start");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Big file");
        mock.expectedFileExists(testFile("enrich/.done/AAA.fin"));
        mock.expectedFileExists(testFile("enrichdata/.done/AAA.dat"));

        template.sendBodyAndHeader(fileUri("enrich"), "Start",
                Exchange.FILE_NAME, "AAA.fin");

        context.getRouteController().startAllRoutes();

        log.info("Sleeping for 0.25 sec before writing enrichdata file");
        Thread.sleep(250);
        template.sendBodyAndHeader(fileUri("enrichdata"), "Big file",
                Exchange.FILE_NAME, "AAA.dat");
        log.info("... write done");

        assertMockEndpointsSatisfied();

        assertFileNotExists(testFile("enrichdata/AAA.dat.camelLock"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri("enrich?initialDelay=0&delay=10&move=.done")).autoStartup(false)
                        .to("mock:start")
                        .pollEnrich(
                                fileUri("enrichdata?initialDelay=0&delay=10&readLock=markerFile&move=.done"),
                                10000)
                        .to("mock:result");
            }
        };
    }

}
