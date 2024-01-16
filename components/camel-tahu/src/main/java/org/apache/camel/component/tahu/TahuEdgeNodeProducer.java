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

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.tahu.TahuEdgeNodeHandler.PayloadBuilder;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
import org.eclipse.tahu.message.BdSeqManager;
import org.eclipse.tahu.message.model.DeviceDescriptor;
import org.eclipse.tahu.message.model.EdgeNodeDescriptor;
import org.eclipse.tahu.message.model.Metric;
import org.eclipse.tahu.model.MqttServerDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public class TahuEdgeNodeProducer extends DefaultProducer {  // implements CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(TahuEdgeNodeProducer.class);

    private static final ConcurrentMap<EdgeNodeDescriptor, TahuEdgeNodeHandler> descriptorHandlers = new ConcurrentHashMap<>();

    private final HeaderFilterStrategy headerFilterStrategy;

    private final TahuEdgeNodeHandler tahuEdgeNodeHandler;
    private ExecutorService clientExecutorService;
    private final EdgeNodeDescriptor edgeNodeDescriptor;

    private final Marker loggingMarker;

    TahuEdgeNodeProducer(TahuEndpoint endpoint, String groupId, String edgeNode, String deviceId) {
        super(endpoint);

        camelContext = endpoint.getCamelContext();

        // If this Producer is for a Device, the edgeNodeDescriptor will be a
        // DeviceDescriptor (subclass of the EdgeNodeDescriptor) describing both
        // the Edge Node to which the Device is attached and the Device itself.
        if (ObjectHelper.isNotEmpty(deviceId)) {
            edgeNodeDescriptor = new DeviceDescriptor(groupId, edgeNode, deviceId);
        } else {
            edgeNodeDescriptor = new EdgeNodeDescriptor(groupId, edgeNode);
        }

        loggingMarker = MarkerFactory.getMarker(edgeNodeDescriptor.getDescriptorString());

        LOG.trace(loggingMarker,
                "TahuEdgeNodeProducer constructor called endpoint {} groupId {} edgeNode {} deviceId {}", endpoint,
                groupId, edgeNode, deviceId);

        TahuConfiguration configuration = endpoint.getConfiguration();

        headerFilterStrategy = endpoint.getHeaderFilterStrategy();

        // A TahuEdgeNodeHandler is created for each Edge Node, not Devices
        EdgeNodeDescriptor handlerDescriptor = edgeNodeDescriptor;
        if (handlerDescriptor.isDeviceDescriptor()) {
            handlerDescriptor = ((DeviceDescriptor) handlerDescriptor).getEdgeNodeDescriptor();
        }

        tahuEdgeNodeHandler = descriptorHandlers.computeIfAbsent(handlerDescriptor, end -> {
            LOG.debug(loggingMarker, "Creating new TahuEdgeNodeHandler for Edge Node {}", end);

            List<MqttServerDefinition> serverDefinitions = configuration.getServerDefinitionList();
            long rebirthDebounceDelay = configuration.getRebirthDebounceDelay();

            String primaryHostId = endpoint.getPrimaryHostId();
            boolean useAliases = endpoint.isUseAliases();
            Map<String, Map<String, Object>> metricDataTypeMap = endpoint.getMetricDataTypeMap();

            clientExecutorService
                    = camelContext.getExecutorServiceManager().newSingleThreadExecutor(this, end.getDescriptorString());

            BdSeqManager bdSeqManager
                    = Optional.ofNullable(endpoint.getBdSeqManager()).orElseGet(() -> new CamelBdSeqManager(end));

            TahuEdgeNodeHandler tenh = new TahuEdgeNodeHandler(
                    end, serverDefinitions, primaryHostId, useAliases, rebirthDebounceDelay, metricDataTypeMap,
                    clientExecutorService, bdSeqManager);

            ServiceHelper.initService(tenh);

            return tenh;
        });

        LOG.trace(loggingMarker, "TahuEdgeNodeProducer constructor complete");
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        LOG.trace(loggingMarker, "Camel doStart called");

        if (!ServiceHelper.isStarted(tahuEdgeNodeHandler)) {
            ServiceHelper.startService(tahuEdgeNodeHandler);
            camelContext.addService(tahuEdgeNodeHandler);
        }

        LOG.trace(loggingMarker, "Camel doStart complete");
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        LOG.trace(loggingMarker, "Camel doStop called");

        if (!edgeNodeDescriptor.isDeviceDescriptor() && tahuEdgeNodeHandler.isStartingOrStarted()) {
            ServiceHelper.stopAndShutdownService(tahuEdgeNodeHandler);
            camelContext.removeService(tahuEdgeNodeHandler);
        }

        if (clientExecutorService != null) {
            camelContext.getExecutorServiceManager().shutdownGraceful(clientExecutorService);
        }

        LOG.trace(loggingMarker, "Camel doStop complete");
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        LOG.trace(loggingMarker, "Camel process called: exchange {}", exchange);

        try {
            Message message = exchange.getMessage();
            long messageTimestamp = message.getMessageTimestamp();

            PayloadBuilder dataPayloadBuilder = tahuEdgeNodeHandler.new PayloadBuilder(edgeNodeDescriptor);

            if (messageTimestamp != 0L) {
                dataPayloadBuilder.setTimestamp(new Date(messageTimestamp));
            }

            Object body = message.getBody();
            if (body != null) {
                byte[] bodyBytes = camelContext.getTypeConverter().mandatoryConvertTo(byte[].class, body);

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

    private CamelContext camelContext;

    // @Override
    // public CamelContext getCamelContext() {
    //     return camelContext;
    // }

    // @Override
    // public void setCamelContext(CamelContext camelContext) {
    //     this.camelContext = camelContext;
    // }

}
