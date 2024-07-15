package org.apache.camel.component.tahu;

import java.util.HashMap;
import java.util.Optional;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.eclipse.tahu.edge.sim.DataSimulator;
import org.eclipse.tahu.edge.sim.RandomDataSimulator;
import org.eclipse.tahu.message.model.DeviceDescriptor;
import org.eclipse.tahu.message.model.EdgeNodeDescriptor;
import org.eclipse.tahu.message.model.SparkplugBPayload;
import org.eclipse.tahu.message.model.SparkplugBPayloadMap;
import org.eclipse.tahu.message.model.SparkplugDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TahuHostAppConsumerRouteBuilder extends RouteBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(TahuHostAppConsumerRouteBuilder.class);

    static final String NODE_DATA_URI = "direct:node-data";
    static final String DEVICE_DATA_URI = "direct:device-data";

    private DataSimulator dataSimulator;

    private EdgeNodeDescriptor edgeNodeDescriptor;
    private DeviceDescriptor deviceDescriptor;

    @Override
    public void configure() throws Exception {
        LOG.trace("RouteBuilder.configure called");

        CamelContext context = getCamelContext();

        // MockEndpoint spTckLogMockEndpoint = context.getEndpoint("mock:" + SparkplugTCKService.SPARKPLUG_TCK_LOG_TOPIC, MockEndpoint.class);
        MockEndpoint spTckResultMockEndpoint = context.getEndpoint("mock:" + SparkplugTCKService.SPARKPLUG_TCK_RESULT_TOPIC, MockEndpoint.class);
    
        TahuEndpoint tahuHostAppEndpoint = context.getEndpoint("tahu:IamHost", TahuEndpoint.class);

        TahuEndpoint tahuEdgeNodeEndpoint = context.getEndpoint("tahu:G2/E2?clientId=TestProducerId",
                TahuEndpoint.class);

        TahuEndpoint tahuDeviceEndpoint = context.getEndpoint("tahu:G2/E2/D2", TahuEndpoint.class);

        edgeNodeDescriptor
                = new EdgeNodeDescriptor(tahuEdgeNodeEndpoint.getGroupId(), tahuEdgeNodeEndpoint.getEdgeNode());
        deviceDescriptor = new DeviceDescriptor(edgeNodeDescriptor, tahuDeviceEndpoint.getDeviceId());

        dataSimulator = new RandomDataSimulator(10, new HashMap<SparkplugDescriptor, Integer>() {
            {
                put(deviceDescriptor, 50);
            }
        });

        tahuEdgeNodeEndpoint.setMetricDataTypePayloadMap(dataSimulator.getNodeBirthPayload(edgeNodeDescriptor));

        SparkplugBPayloadMap deviceMetricPayloadMap = new SparkplugBPayloadMap();
        deviceMetricPayloadMap.setMetrics(dataSimulator.getDeviceBirthPayload(deviceDescriptor).getMetrics());
        tahuDeviceEndpoint.setMetricDataTypePayloadMap(deviceMetricPayloadMap);

        from(tahuHostAppEndpoint)
                .to(spTckResultMockEndpoint);

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

    private void processPayload(Exchange exch, String messageType, SparkplugBPayload payload, EdgeNodeDescriptor edgeNodeDescriptor) {
        org.apache.camel.Message message = exch.getMessage();

        message.setHeader(TahuConstants.MESSAGE_TYPE, messageType);
        message.setHeader(TahuConstants.EDGE_NODE_DESCRIPTOR, edgeNodeDescriptor);

        Optional.ofNullable(payload.getUuid())
                .ifPresent(uuid -> message.setHeader(TahuConstants.MESSAGE_UUID, uuid));
        Optional.ofNullable(payload.getTimestamp())
                .ifPresent(timestamp -> message.setHeader(TahuConstants.MESSAGE_TIMESTAMP, timestamp));
        Optional.ofNullable(payload.getSeq())
                .ifPresent(seq -> message.setHeader(TahuConstants.MESSAGE_SEQUENCE_NUMBER, seq));

        // payload.getMetrics().forEach(m -> {
        //     message.setHeader(TahuConstants.METRIC_HEADER_PREFIX + m.getName(), m.getValue());
        // });

        // Optional.ofNullable(payload.getBody()).ifPresent(body -> message.setBody(body, byte[].class));

        message.setBody(payload);
    }

    private Processor getNodeDataPayload = (exch) -> {
        processPayload(exch, "NDATA", dataSimulator.getNodeDataPayload(edgeNodeDescriptor), edgeNodeDescriptor);
    };

    private Processor getDeviceDataPayload = (exch) -> {
        processPayload(exch, "DDATA", dataSimulator.getDeviceDataPayload(deviceDescriptor), deviceDescriptor);
    };

}
