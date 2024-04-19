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

import java.util.HashMap;
// import java.util.Map;
import java.util.Optional;
// import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.eclipse.tahu.edge.sim.DataSimulator;
import org.eclipse.tahu.edge.sim.RandomDataSimulator;
import org.eclipse.tahu.message.model.DeviceDescriptor;
import org.eclipse.tahu.message.model.EdgeNodeDescriptor;
import org.eclipse.tahu.message.model.SparkplugBPayload;
import org.eclipse.tahu.message.model.SparkplugBPayloadMap;
import org.eclipse.tahu.message.model.SparkplugDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TahuEdgeNodePublisherRouteBuilder extends RouteBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(TahuEdgeNodePublisherRouteBuilder.class);

    static final String NODE_DATA_URI = "direct:node-data";
    static final String DEVICE_DATA_URI = "direct:device-data";

    private DataSimulator dataSimulator;

    private EdgeNodeDescriptor edgeNodeDescriptor;
    private DeviceDescriptor deviceDescriptor;

    @Override
    public void configure() throws Exception {
        LOG.trace("RouteBuilder.configure called");

        CamelContext context = getCamelContext();

        TahuEndpoint tahuEdgeNodeEndpoint = context.getEndpoint("tahu:G2/E2?primaryHostId=IamHost&useAliases=true",
                TahuEndpoint.class);

        tahuEdgeNodeEndpoint.setBdSeqManager(new AtomicBdSeqManager());

        TahuEndpoint tahuDeviceEndpoint = context.getEndpoint("tahu:G2/E2/D2", TahuEndpoint.class);

        edgeNodeDescriptor
                = new EdgeNodeDescriptor(tahuEdgeNodeEndpoint.getGroupId(), tahuEdgeNodeEndpoint.getEdgeNode());
        deviceDescriptor = new DeviceDescriptor(edgeNodeDescriptor, tahuDeviceEndpoint.getDeviceId());

        dataSimulator = new RandomDataSimulator(10, new HashMap<SparkplugDescriptor, Integer>() {
            {
                put(deviceDescriptor, 5);
            }
        });

        // Create the Birth payloads to capture the random metric configuration from the
        // data simulator.
        // This would normally be set on the Edge Node endpoint via "metrics."
        // parameters.
        // Map<String, Object> nodeMetricDataTypes = dataSimulator.getNodeBirthPayload(edgeNodeDescriptor)
        //         .getMetrics().stream()
        //         .map(m -> new Object[] {
        //                 tahuEdgeNodeEndpoint.getEdgeNode() + TahuConstants.MAJOR_SEPARATOR + m.getName(),
        //                 m.getDataType() })
        //         .collect(Collectors.toMap(arr -> (String) arr[0], arr -> arr[1]));

        // Map<String, Object> deviceMetricDataTypes = dataSimulator.getDeviceBirthPayload(deviceDescriptor)
        //         .getMetrics().stream()
        //         .map(m -> new Object[] {
        //                 tahuDeviceEndpoint.getDeviceId() + TahuConstants.MAJOR_SEPARATOR + m.getName(),
        //                 m.getDataType() })
        //         .collect(Collectors.toMap(arr -> (String) arr[0], arr -> arr[1]));

        // Map<String, Object> metricDataTypes = new HashMap<>();
        // metricDataTypes.putAll(nodeMetricDataTypes);
        // metricDataTypes.putAll(deviceMetricDataTypes);
        // tahuEdgeNodeEndpoint.setMetricDataTypes(metricDataTypes);

        tahuEdgeNodeEndpoint.setMetricDataTypePayloadMap(dataSimulator.getNodeBirthPayload(edgeNodeDescriptor));

        SparkplugBPayloadMap deviceMetricPayloadMap = new SparkplugBPayloadMap();
        deviceMetricPayloadMap.setMetrics(dataSimulator.getDeviceBirthPayload(deviceDescriptor).getMetrics());
        tahuDeviceEndpoint.setMetricDataTypePayloadMap(deviceMetricPayloadMap);

        from(NODE_DATA_URI)
                .id("node-data-test-route")
                .process(getNodeDataPayload)
                .to(tahuEdgeNodeEndpoint);

        from(DEVICE_DATA_URI)
                .id("device-data-test-route")
                .process(getDeviceDataPayload)
                .to(tahuDeviceEndpoint);

        LOG.trace("RouteBuilder.configure complete");
    }

    private void processPayload(Exchange exch, String messageType, SparkplugBPayload payload) {
        org.apache.camel.Message message = exch.getMessage();

        message.setHeader(TahuConstants.MESSAGE_TYPE, messageType);

        Optional.ofNullable(payload.getUuid())
                .ifPresent(uuid -> message.setHeader(TahuConstants.MESSAGE_UUID, uuid));
        Optional.ofNullable(payload.getTimestamp())
                .ifPresent(timestamp -> message.setHeader(TahuConstants.MESSAGE_TIMESTAMP, timestamp));
        Optional.ofNullable(payload.getSeq())
                .ifPresent(seq -> message.setHeader(TahuConstants.MESSAGE_SEQUENCE_NUMBER, seq));

        payload.getMetrics().forEach(m -> {
            message.setHeader(TahuConstants.METRIC_HEADER_PREFIX + m.getName(), m.getValue());
        });

        Optional.ofNullable(payload.getBody()).ifPresent(body -> message.setBody(body, byte[].class));
    }

    private Processor getNodeDataPayload = (exch) -> {
        processPayload(exch, "NDATA", dataSimulator.getNodeDataPayload(edgeNodeDescriptor));
    };

    private Processor getDeviceDataPayload = (exch) -> {
        processPayload(exch, "DDATA", dataSimulator.getDeviceDataPayload(deviceDescriptor));
    };

}
