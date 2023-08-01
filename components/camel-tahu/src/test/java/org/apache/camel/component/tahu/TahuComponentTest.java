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

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.direct.DirectEndpoint;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.eclipse.tahu.edge.sim.DataSimulator;
import org.eclipse.tahu.edge.sim.RandomDataSimulator;
import org.eclipse.tahu.message.model.DeviceDescriptor;
import org.eclipse.tahu.message.model.EdgeNodeDescriptor;
import org.eclipse.tahu.message.model.SparkplugBPayload;
import org.eclipse.tahu.message.model.SparkplugBPayloadMap;
import org.eclipse.tahu.message.model.SparkplugDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.hamcrest.Matchers.*;

@SuppressWarnings("unused")
public class TahuComponentTest extends TahuTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(TahuComponentTest.class);

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

    private ProducerTemplate template;

    @BeforeEach
    void setupTemplate() {
        template = camelContextExtension.getProducerTemplate();
    }

    @Test
    public void testNodeBirth() throws Exception {
        mock.expectedMessageCount(1);

        template.sendBody(nodeBirthEndpoint, null);

        mock.assertIsSatisfied();
    }

    @Test
    public void testNodeData() throws Exception {
        mock.expectedMessageCount(1);

        template.sendBody(nodeDataEndpoint, null);

        mock.assertIsSatisfied();
    }

    @Test
    public void testDeviceBirth() throws Exception {
        mock.expectedMessageCount(1);

        template.sendBody(deviceBirthEndpoint, null);

        mock.assertIsSatisfied();
    }

    @Test
    public void testDeviceData() throws Exception {
        mock.expectedMessageCount(1);

        template.sendBody(deviceDataEndpoint, null);

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            private EdgeNodeDescriptor edgeNodeDescriptor;
            private DeviceDescriptor deviceDescriptor;
            private DataSimulator dataSimulator;

            @Override
            public void configure() throws Exception {

                // TahuHostAppEndpoint hostAppEndpoint = getCamelContext().getEndpoint(
                //         "tahu-host:H1?clientId=TestConsumerId&servers=TahuComponentTestServer:" + service.serviceAddress(),
                //         TahuHostAppEndpoint.class);

                TahuEdgeNodeEndpoint tahuEdgeNodeEndpoint = getCamelContext().getEndpoint(
                        "tahu-node:G2/E2?clientId=TestProducerId&servers=TahuComponentTestServer:" + service.serviceAddress(),
                        TahuEdgeNodeEndpoint.class);

                TahuEdgeNodeEndpoint tahuDeviceEndpoint
                        = getCamelContext().getEndpoint("tahu-device:G2/E2/D2", TahuEdgeNodeEndpoint.class);

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

                from(nodeBirthEndpoint)
                        .process((exch) -> processPayload(exch, nBirthPayload))
                        .to(tahuEdgeNodeEndpoint)
                        .to("log:node-birth?showAll=true&multiline=true")
                        .to(mock);

                from(nodeDataEndpoint)
                        .process(getNodeDataPayload)
                        .to(tahuEdgeNodeEndpoint)
                        .to("log:node-data?showAll=true&multiline=true")
                        .to(mock);

                from(deviceBirthEndpoint)
                        .process((exch) -> processPayload(exch, dBirthPayload))
                        .to(tahuDeviceEndpoint)
                        .to("log:device-birth?showAll=true&multiline=true")
                        .to(mock);

                from(deviceDataEndpoint)
                        .process(getDeviceDataPayload)
                        .to(tahuDeviceEndpoint)
                        .to("log:device-data?showAll=true&multiline=true")
                        .to(mock);
            }

            private void processPayload(Exchange exch, SparkplugBPayload payload) {
                Message message = exch.getMessage();

                payload.getMetrics().forEach(m -> {
                    message.setHeader(TahuConstants.METRIC_HEADER_PREFIX + m.getName(), m.getValue());
                });

                message.setBody(payload.getBody(), byte[].class);
            }

            private Processor getNodeDataPayload = (exch) -> {
                processPayload(exch, dataSimulator.getNodeDataPayload(edgeNodeDescriptor));
            };

            private Processor getDeviceDataPayload = (exch) -> {
                processPayload(exch, dataSimulator.getDeviceDataPayload(deviceDescriptor));
            };

        };
    }

    @Override
    public CamelContextExtension getCamelContextExtension() {
        return camelContextExtension;
    }

}
