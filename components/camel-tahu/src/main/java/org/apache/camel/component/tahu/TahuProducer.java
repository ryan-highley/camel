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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.tahu.TahuMetricHandler.PayloadBuilder;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.service.ServiceHelper;
import org.eclipse.tahu.message.model.DeviceDescriptor;
import org.eclipse.tahu.message.model.EdgeNodeDescriptor;
import org.eclipse.tahu.message.model.Metric;
import org.eclipse.tahu.model.MqttServerDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public class TahuProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(TahuProducer.class);

    private static final ConcurrentMap<EdgeNodeDescriptor, TahuMetricHandler> descriptorHandlers = new ConcurrentHashMap<>();

    private final TahuEndpoint endpoint;
    private final TahuConfiguration configuration;
    private final HeaderFilterStrategy headerFilterStrategy;

    private final EdgeNodeDescriptor edgeNodeDescriptor;

    private TahuMetricHandler tahuMetricHandler;

    private final Marker loggingMarker;

    TahuProducer(TahuEndpoint endpoint, String groupId, String edgeNode, String deviceId) {
        super(endpoint);

        LOG.trace("TahuProducer constructor called endpoint {} groupId {} edgeNode {} deviceId {}", endpoint, groupId,
                edgeNode, deviceId);

        this.endpoint = endpoint;
        configuration = endpoint.getConfiguration();
        headerFilterStrategy = endpoint.getHeaderFilterStrategy();

        EdgeNodeDescriptor end = new EdgeNodeDescriptor(groupId, edgeNode);

        // If this Producer is for a Device, the edgeNodeDescriptor will be a
        // DeviceDescriptor subclass of the EdgeNodeDescriptor describing the
        // Edge Node to which this Device is attached.
        if (deviceId != null) {
            edgeNodeDescriptor = new DeviceDescriptor(end, deviceId);
        } else {
            edgeNodeDescriptor = end;
        }
        loggingMarker = MarkerFactory.getMarker(edgeNodeDescriptor.getDescriptorString());

        LOG.trace(loggingMarker, "TahuProducer constructor complete");
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        LOG.trace(loggingMarker, "Camel doStart called");

        // A TahuMetricHandler is created for each Edge Node, not Devices
        EdgeNodeDescriptor handlerDescriptor = edgeNodeDescriptor;
        if (isDeviceProducer()) {
            handlerDescriptor = ((DeviceDescriptor) handlerDescriptor).getEdgeNodeDescriptor();
        }

        tahuMetricHandler = descriptorHandlers.computeIfAbsent(handlerDescriptor,
                end -> {
                    List<MqttServerDefinition> serverDefinitions = configuration.getServerDefinitionList();
                    long rebirthDebounceDelay = configuration.getRebirthDebounceDelay();

                    String primaryHostId = endpoint.getPrimaryHostId();
                    boolean useAliases = endpoint.isUseAliases();

                    Map<String, Map<String, Object>> metricDataTypeMap = endpoint.getMetricDataTypeMap();

                    TahuMetricHandler tmh = new TahuMetricHandler(
                            end, serverDefinitions, primaryHostId, useAliases,
                            rebirthDebounceDelay, metricDataTypeMap);

                    tmh.setCamelContext(endpoint.getCamelContext());

                    return tmh;
                });

        if (!tahuMetricHandler.isStarted()) {
            ServiceHelper.startService(tahuMetricHandler);
        }

        LOG.trace(loggingMarker, "Camel doStart complete");
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        LOG.trace(loggingMarker, "Camel doStop called");

        EdgeNodeDescriptor handlerDescriptor = edgeNodeDescriptor;
        if (isDeviceProducer()) {
            handlerDescriptor = ((DeviceDescriptor) handlerDescriptor).getEdgeNodeDescriptor();
        }

        TahuMetricHandler tahuMetricHandler = descriptorHandlers.get(handlerDescriptor);

        ServiceHelper.stopAndShutdownService(tahuMetricHandler);

        LOG.trace(loggingMarker, "Camel doStop complete");
    }

    @Override
    public void process(Exchange exchange) {
        LOG.trace(loggingMarker, "Camel process called: exchange {}", exchange);

        try {
            String toEndpointUri = exchange.getProperty(Exchange.TO_ENDPOINT, String.class);
            LOG.trace(loggingMarker, "Camel process toEndpointUri {}", toEndpointUri);

            Message message = exchange.getMessage();

            PayloadBuilder dataPayloadBuilder = tahuMetricHandler.new PayloadBuilder(edgeNodeDescriptor);
            dataPayloadBuilder.setTimestamp(message.getMessageTimestamp());

            Object body = message.getBody();
            if (body != null) {
                byte[] bodyBytes = exchange.getContext().getTypeConverter().mandatoryConvertTo(byte[].class, body);

                dataPayloadBuilder.setBody(bodyBytes);
            }

            message.getHeaders().forEach((metricName, metricValue) -> {
                // Skip headers where the headerFilterStrategy returns true, per
                // HeaderFilterStrategy.applyFilterToCamelHeaders
                if (headerFilterStrategy.applyFilterToCamelHeaders(metricName, metricValue, exchange)) {
                    return;
                }

                // If using the default headerFilterStrategy, strip off the header name prefix
                if (metricName.startsWith(TahuConstants.METRIC_HEADER_PREFIX)) {
                    metricName = metricName.substring(TahuConstants.METRIC_HEADER_PREFIX.length());
                }

                if (metricValue instanceof Metric) {
                    dataPayloadBuilder.addMetric((Metric) metricValue);
                } else {
                    dataPayloadBuilder.addMetric(metricName, metricValue);
                }

            });

            dataPayloadBuilder.publish();

        } catch (Exception e) {
            exchange.setException(e);
        }

        LOG.trace(loggingMarker, "Camel process complete");
    }

    private boolean isDeviceProducer() {
        return edgeNodeDescriptor.isDeviceDescriptor();
    }
}
