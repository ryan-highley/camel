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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

public class TahuHostConsumerRouteBuilder extends RouteBuilder {

    protected MockEndpoint spTckLogMockEndpoint;
    protected MockEndpoint spTckResultMockEndpoint;

    @Override
    public void configure() throws Exception {

        CamelContext context = getCamelContext();

        spTckLogMockEndpoint = context.getEndpoint("mock:" + SparkplugTCKService.SPARKPLUG_TCK_LOG_TOPIC, MockEndpoint.class);
        spTckResultMockEndpoint
                = context.getEndpoint("mock:" + SparkplugTCKService.SPARKPLUG_TCK_RESULT_TOPIC, MockEndpoint.class);

        TahuEndpoint tahuHostAppEndpoint = context.getEndpoint("tahu:IamHost?clientId=TestHostApp", TahuEndpoint.class);

        from(tahuHostAppEndpoint)
                .to(spTckResultMockEndpoint);

    }
}
