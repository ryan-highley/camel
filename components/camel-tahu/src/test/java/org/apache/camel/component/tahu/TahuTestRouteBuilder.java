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

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.eclipse.tahu.edge.sim.DataSimulator;
import org.eclipse.tahu.edge.sim.RandomDataSimulator;
import org.eclipse.tahu.message.BdSeqManager;
import org.eclipse.tahu.message.model.DeviceDescriptor;
import org.eclipse.tahu.message.model.EdgeNodeDescriptor;
import org.eclipse.tahu.message.model.SparkplugBPayload;
import org.eclipse.tahu.message.model.SparkplugBPayloadMap;

public class TahuTestRouteBuilder extends RouteBuilder {

    static final String NODE_DATA_URI = "direct:node-data";
    static final String DEVICE_DATA_URI = "direct:device-data";

    protected DataSimulator dataSimulator;

    protected EdgeNodeDescriptor edgeNodeDescriptor;
    protected DeviceDescriptor deviceDescriptor;

    protected MockEndpoint spTckLogMockEndpoint;
    protected MockEndpoint spTckResultMockEndpoint;

    protected TahuEndpoint tahuEdgeNodeEndpoint;
    protected TahuEndpoint tahuDeviceEndpoint;

    @Override
    public void configure() throws Exception {

        CamelContext context = getCamelContext();

        spTckLogMockEndpoint = context.getEndpoint("mock:" + SparkplugTCKService.SPARKPLUG_TCK_LOG_TOPIC, MockEndpoint.class);
        spTckResultMockEndpoint
                = context.getEndpoint("mock:" + SparkplugTCKService.SPARKPLUG_TCK_RESULT_TOPIC, MockEndpoint.class);

        tahuEdgeNodeEndpoint = context.getEndpoint(TahuConstants.EDGE_NODE_SCHEME + ":G2/E2?deviceIds=D2&primaryHostId=IamHost",
                TahuEndpoint.class);

        edgeNodeDescriptor = new EdgeNodeDescriptor(tahuEdgeNodeEndpoint.getGroupId(), tahuEdgeNodeEndpoint.getEdgeNode());

        // Reset next BdSeqNum to 0 for clean broker run during testing
        BdSeqManager bdSeqManager = new CamelBdSeqManager(edgeNodeDescriptor);
        bdSeqManager.storeNextDeathBdSeqNum(0L);
        tahuEdgeNodeEndpoint.setBdSeqManager(bdSeqManager);

        tahuDeviceEndpoint = context.getEndpoint(TahuConstants.DEVICE_SCHEME + ":G2/E2/D2", TahuEndpoint.class);

        deviceDescriptor = new DeviceDescriptor(edgeNodeDescriptor, tahuDeviceEndpoint.getDeviceId());

        dataSimulator = new RandomDataSimulator(10, Map.of(deviceDescriptor, 5));

        tahuEdgeNodeEndpoint.setMetricDataTypePayloadMap(dataSimulator.getNodeBirthPayload(edgeNodeDescriptor));

        SparkplugBPayloadMap deviceMetricPayloadMap = new SparkplugBPayloadMap.SparkplugBPayloadMapBuilder()
                .addMetrics(dataSimulator.getDeviceBirthPayload(deviceDescriptor).getMetrics()).createPayload();
        tahuDeviceEndpoint.setMetricDataTypePayloadMap(deviceMetricPayloadMap);

        from(NODE_DATA_URI)
                .id("node-data-test-route")
                .autoStartup(false)
                .process(populateNodeDataPayload)
                .to(tahuEdgeNodeEndpoint);

        from(DEVICE_DATA_URI)
                .id("device-data-test-route")
                .autoStartup(false)
                .process(populateDeviceDataPayload)
                .to(tahuDeviceEndpoint);

    }

    private void populateTestMessage(Exchange exch, SparkplugBPayload payload, EdgeNodeDescriptor edgeNodeDescriptor) {
        org.apache.camel.Message message = exch.getMessage();

        message.setBody(payload, SparkplugBPayload.class);
    }

    protected Processor populateNodeDataPayload = (exch) -> {
        populateTestMessage(exch, dataSimulator.getNodeDataPayload(edgeNodeDescriptor), edgeNodeDescriptor);
    };

    protected Processor populateDeviceDataPayload = (exch) -> {
        populateTestMessage(exch, dataSimulator.getDeviceDataPayload(deviceDescriptor), deviceDescriptor);
    };

}
