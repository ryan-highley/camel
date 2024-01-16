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
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.direct.DirectEndpoint;
import org.apache.camel.component.log.LogEndpoint;
import org.apache.camel.component.mock.MockEndpoint;
import org.eclipse.tahu.edge.sim.DataSimulator;
import org.eclipse.tahu.edge.sim.RandomDataSimulator;
import org.eclipse.tahu.message.model.DeviceDescriptor;
import org.eclipse.tahu.message.model.EdgeNodeDescriptor;
import org.eclipse.tahu.message.model.SparkplugBPayload;
import org.eclipse.tahu.message.model.SparkplugBPayloadMap;
import org.eclipse.tahu.message.model.SparkplugDescriptor;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
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

    @EndpointInject("log:org.apache.camel.component.tahu.TahuEdgeNodePublisherTest?showAll=true&multiline=true&level=DEBUG&skipBodyLineSeparator=false")
    LogEndpoint logEndpoint;

    @Test
    public void testNodeBirth() throws Exception {
        sparkplugTckResultEndpoint.expectedMessageCount(1);

        getCamelContextExtension().getProducerTemplate().sendBody(nodeBirthEndpoint, null);

        sparkplugTckResultEndpoint.assertIsSatisfied();
    }

    // @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            private EdgeNodeDescriptor edgeNodeDescriptor;
            private DeviceDescriptor deviceDescriptor;
            private DataSimulator dataSimulator;

            @Override
            public void configure() throws Exception {

                TahuEndpoint tahuHostAppEndpoint = getCamelContext().getEndpoint("tahu:IamHost", TahuEndpoint.class);

                TahuEndpoint tahuEdgeNodeEndpoint = getCamelContext().getEndpoint("tahu:G2/E2?clientId=TestProducerId",
                        TahuEndpoint.class);

                TahuEndpoint tahuDeviceEndpoint = getCamelContext().getEndpoint("tahu:G2/E2/D2", TahuEndpoint.class);

                edgeNodeDescriptor
                        = new EdgeNodeDescriptor(tahuEdgeNodeEndpoint.getGroupId(), tahuEdgeNodeEndpoint.getEdgeNode());
                deviceDescriptor = new DeviceDescriptor(edgeNodeDescriptor, tahuDeviceEndpoint.getDeviceId());

                dataSimulator = new RandomDataSimulator(10, new HashMap<SparkplugDescriptor, Integer>() {
                    {
                        put(deviceDescriptor, 50);
                    }
                });

                SparkplugBPayloadMap nBirthPayload = dataSimulator.getNodeBirthPayload(edgeNodeDescriptor);

                Map<String, Object> nodeMetricDataTypes = nBirthPayload.getMetrics().stream()
                        .map(m -> new Object[] {
                                tahuEdgeNodeEndpoint.getEdgeNode() + TahuConstants.MAJOR_SEPARATOR + m.getName(),
                                m.getDataType() })
                        .collect(Collectors.toMap(arr -> (String) arr[0], arr -> arr[1]));

                SparkplugBPayload dBirthPayload = dataSimulator.getDeviceBirthPayload(deviceDescriptor);

                Map<String, Object> deviceMetricDataTypes = dBirthPayload.getMetrics().stream()
                        .map(m -> new Object[] {
                                tahuDeviceEndpoint.getDeviceId() + TahuConstants.MAJOR_SEPARATOR + m.getName(),
                                m.getDataType() })
                        .collect(Collectors.toMap(arr -> (String) arr[0], arr -> arr[1]));

                Map<String, Object> metricDataTypes = new HashMap<>();
                metricDataTypes.putAll(nodeMetricDataTypes);
                metricDataTypes.putAll(deviceMetricDataTypes);
                tahuEdgeNodeEndpoint.setMetricDataTypes(Map.copyOf(metricDataTypes));

                from(tahuHostAppEndpoint)
                        .to(logEndpoint)
                        .to(sparkplugTckResultEndpoint);

                from(nodeDataEndpoint)
                        .id("node-data-test-route")
                        .process(getNodeDataPayload)
                        .to(tahuEdgeNodeEndpoint);

                from(deviceDataEndpoint)
                        .id("device-data-test-route")
                        .process(getDeviceDataPayload)
                        .to(tahuDeviceEndpoint);

            }

            private void processPayload(Exchange exch, String messageType, SparkplugBPayload payload) {
                Message message = exch.getMessage();

                message.setHeader(TahuConstants.MESSAGE_TYPE, messageType);
                message.setHeader(TahuConstants.MESSAGE_UUID, payload.getUuid());
                message.setHeader(TahuConstants.MESSAGE_TIMESTAMP, payload.getTimestamp());
                message.setHeader(TahuConstants.MESSAGE_SEQUENCE_NUMBER, payload.getSeq());

                payload.getMetrics().forEach(m -> {
                    message.setHeader(TahuConstants.METRIC_HEADER_PREFIX + m.getName(), m.getValue());
                });

                message.setBody(payload.getBody(), byte[].class);
            }

            private Processor getNodeDataPayload = (exch) -> {
                processPayload(exch, "NDATA", dataSimulator.getNodeDataPayload(edgeNodeDescriptor));
            };

            private Processor getDeviceDataPayload = (exch) -> {
                processPayload(exch, "DDATA", dataSimulator.getDeviceDataPayload(deviceDescriptor));
            };

        };
    }

    @Override
    public void configureContext(CamelContext context) throws Exception {
        // TODO Auto-generated method stub
        // throw new UnsupportedOperationException("Unimplemented method 'configureContext'");
    }

    @Override
    public void createRouteBuilder(CamelContext context) throws Exception {
        // TODO Auto-generated method stub
        // throw new UnsupportedOperationException("Unimplemented method 'createRouteBuilder'");
    }

}
