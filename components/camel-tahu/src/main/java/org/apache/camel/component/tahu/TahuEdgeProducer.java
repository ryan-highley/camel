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
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
import org.eclipse.tahu.message.BdSeqManager;
import org.eclipse.tahu.message.model.DeviceDescriptor;
import org.eclipse.tahu.message.model.EdgeNodeDescriptor;
import org.eclipse.tahu.message.model.SparkplugBPayload;
import org.eclipse.tahu.model.MqttServerDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public class TahuEdgeProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(TahuEdgeProducer.class);

    private static final ConcurrentMap<EdgeNodeDescriptor, TahuEdgeClientHandler> descriptorHandlers
            = new ConcurrentHashMap<>();

    private final CamelContext camelContext;

    private final TahuEdgeClientHandler tahuEdgeClientHandler;
    private ExecutorService clientExecutorService;
    private final EdgeNodeDescriptor edgeNodeDescriptor;

    private final Marker loggingMarker;

    TahuEdgeProducer(TahuEndpoint endpoint, String groupId, String edgeNode, String deviceId) {
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
                "TahuEdgeProducer constructor called endpoint {} groupId {} edgeNode {} deviceId {}", endpoint,
                groupId, edgeNode, deviceId);

        TahuConfiguration configuration = endpoint.getConfiguration();

        // A TahuEdgeClientHandler is created for each Edge Node, not Devices
        EdgeNodeDescriptor handlerDescriptor = edgeNodeDescriptor;
        if (handlerDescriptor.isDeviceDescriptor()) {
            handlerDescriptor = ((DeviceDescriptor) handlerDescriptor).getEdgeNodeDescriptor();
        }

        tahuEdgeClientHandler = descriptorHandlers.computeIfAbsent(handlerDescriptor, end -> {
            LOG.debug(loggingMarker, "Creating new TahuEdgeProducer for Edge Node {}", end);

            List<MqttServerDefinition> serverDefinitions = configuration.getServerDefinitionList();
            long rebirthDebounceDelay = configuration.getRebirthDebounceDelay();

            String primaryHostId = endpoint.getPrimaryHostId();
            List<String> deviceIds = endpoint.getDeviceIdList();
            boolean useAliases = endpoint.isUseAliases();

            clientExecutorService
                    = camelContext.getExecutorServiceManager().newSingleThreadExecutor(this, end.getDescriptorString());

            BdSeqManager bdSeqManager
                    = Optional.ofNullable(endpoint.getBdSeqManager()).orElseGet(() -> new CamelBdSeqManager(end));

            TahuEdgeClientHandler tenh = new TahuEdgeClientHandler(
                    end, serverDefinitions, primaryHostId, deviceIds, useAliases, rebirthDebounceDelay, clientExecutorService,
                    bdSeqManager);

            ServiceHelper.initService(tenh);

            return tenh;
        });

        // Add the SparkplugBPayloadMap Metrics configuration
        tahuEdgeClientHandler.addDeviceMetricDataPayloadMap(edgeNodeDescriptor, endpoint.getMetricDataTypePayloadMap());

        LOG.trace(loggingMarker, "TahuEdgeProducer constructor complete");
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        LOG.trace(loggingMarker, "Camel doStart called");

        camelContext.addService(tahuEdgeClientHandler);

        if (!ServiceHelper.isStarted(tahuEdgeClientHandler)) {
            ServiceHelper.startService(tahuEdgeClientHandler);
        }

        LOG.trace(loggingMarker, "Camel doStart complete");
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        LOG.trace(loggingMarker, "Camel doStop called");

        if (!edgeNodeDescriptor.isDeviceDescriptor() && tahuEdgeClientHandler.isStartingOrStarted()) {
            ServiceHelper.stopAndShutdownService(tahuEdgeClientHandler);
        }

        if (clientExecutorService != null) {
            camelContext.getExecutorServiceManager().shutdownGraceful(clientExecutorService);
        }

        camelContext.removeService(tahuEdgeClientHandler);

        LOG.trace(loggingMarker, "Camel doStop complete");
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        LOG.trace(loggingMarker, "Camel process called: exchange {}", exchange);

        try {
            Message message = exchange.getMessage();

            SparkplugBPayload payload = message.getMandatoryBody(SparkplugBPayload.class);

            tahuEdgeClientHandler.publishData(edgeNodeDescriptor, payload);

            String messageType = (edgeNodeDescriptor.isDeviceDescriptor()) ? "DDATA" : "NDATA";

            message.setHeader(TahuConstants.MESSAGE_TYPE, messageType);
            message.setHeader(TahuConstants.EDGE_NODE_DESCRIPTOR, edgeNodeDescriptor);

            Optional.ofNullable(payload.getUuid()).ifPresent(uuid -> message.setHeader(TahuConstants.MESSAGE_UUID, uuid));
            Optional.ofNullable(payload.getTimestamp())
                    .ifPresent(timestamp -> message.setHeader(TahuConstants.MESSAGE_TIMESTAMP, timestamp));
            Optional.ofNullable(payload.getSeq())
                    .ifPresent(seq -> message.setHeader(TahuConstants.MESSAGE_SEQUENCE_NUMBER, seq));

        } catch (Exception e) {
            exchange.setException(e);
        }

        LOG.trace(loggingMarker, "Camel process complete");
    }

}
