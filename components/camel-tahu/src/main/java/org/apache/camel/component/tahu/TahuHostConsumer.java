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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.service.ServiceHelper;
import org.eclipse.tahu.message.model.EdgeNodeDescriptor;
import org.eclipse.tahu.message.model.MessageType;
import org.eclipse.tahu.message.model.Metric;
import org.eclipse.tahu.message.model.SparkplugBPayload;
import org.eclipse.tahu.message.model.Topic;
import org.eclipse.tahu.model.MqttServerDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public class TahuHostConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(TahuHostConsumer.class);

    private static final ConcurrentMap<String, TahuHostApplicationHandler> hostHandlers = new ConcurrentHashMap<>();

    @SuppressWarnings("unused")
    private final TahuEndpoint endpoint;

    @SuppressWarnings("unused")
    private final String hostId;

    private final TahuHostApplicationHandler tahuHostApplicationHandler;

    private final Marker loggingMarker;

    TahuHostConsumer(TahuEndpoint endpoint, Processor processor, String hostId) {
        super(endpoint, processor);

        this.endpoint = endpoint;

        loggingMarker = MarkerFactory.getMarker(hostId);

        LOG.trace(loggingMarker, "TahuHostAppConsumer constructor called endpoint {} hostId {}", endpoint, hostId);

        TahuConfiguration configuration = endpoint.getConfiguration();

        tahuHostApplicationHandler = hostHandlers.computeIfAbsent(hostId, hId -> {
            List<MqttServerDefinition> serverDefinitions = configuration.getServerDefinitionList();

            TahuHostApplicationHandler thah = new TahuHostApplicationHandler(
                    hId, serverDefinitions, this::onMessageConsumer, this::onMetricConsumer);

            return thah;
        });

        this.hostId = hostId;

        LOG.trace(loggingMarker, "TahuHostAppConsumer constructor complete");
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        LOG.trace(loggingMarker, "Camel doStart called");

        if (!tahuHostApplicationHandler.isStarted()) {
            ServiceHelper.startService(tahuHostApplicationHandler);
        }

        LOG.trace(loggingMarker, "Camel doStart complete");
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        LOG.trace(loggingMarker, "Camel doStop called");

        ServiceHelper.stopAndShutdownService(tahuHostApplicationHandler);

        LOG.trace(loggingMarker, "Camel doStop complete");
    }

    private static final List<MessageType> HANDLED_MESSAGE_TYPES = List.of(MessageType.NBIRTH, MessageType.NDATA,
            MessageType.NDEATH, MessageType.DBIRTH, MessageType.DDATA, MessageType.DDEATH);

    void onMessageConsumer(EdgeNodeDescriptor edgeNodeDescriptor, org.eclipse.tahu.message.model.Message tahuMessage) {
        LOG.trace(loggingMarker, "TahuHostAppConsumer onMessageConsumer called: edgeNodeDescriptor {} tahuMessage {}",
                edgeNodeDescriptor, tahuMessage);

        Exchange exchange = null;
        try {
            Topic topic = tahuMessage.getTopic();
            SparkplugBPayload payload = tahuMessage.getPayload();

            if (HANDLED_MESSAGE_TYPES.contains(topic.getType())) {
                exchange = createExchange(true);

                Message camelMessage = exchange.getMessage();
                camelMessage.setHeader(TahuConstants.MESSAGE_TYPE, topic.getType().name());
                camelMessage.setHeader(TahuConstants.EDGE_NODE_DESCRIPTOR, edgeNodeDescriptor.getDescriptorString());

                if (payload.getTimestamp() != null) {
                    camelMessage.setHeader(TahuConstants.MESSAGE_TIMESTAMP, payload.getTimestamp().getTime());
                }

                if (payload.getSeq() != null) {
                    camelMessage.setHeader(TahuConstants.MESSAGE_SEQUENCE_NUMBER, payload.getSeq());
                }

                if (payload.getUuid() != null) {
                    try {
                        camelMessage.setHeader(TahuConstants.MESSAGE_UUID, UUID.fromString(payload.getUuid()));
                    } catch (IllegalArgumentException iae) {
                        LOG.warn(loggingMarker, "Exception caught parsing Sparkplug message UUID {} - skipping",
                                payload.getUuid());
                    }
                }

                if (payload.getBody() != null) {
                    camelMessage.setBody(payload.getBody(), byte[].class);
                }

                Map<String, Object> payloadMetrics = payload.getMetrics().stream()
                        .map(m -> new Object[] { TahuConstants.METRIC_HEADER_PREFIX + m.getName(), m })
                        .collect(Collectors.toMap(arr -> (String) arr[0], arr -> arr[1]));

                if (!payloadMetrics.isEmpty()) {
                    camelMessage.setHeaders(payloadMetrics);
                }

                getProcessor().process(exchange);

            } else {
                LOG.warn(loggingMarker,
                        "TahuHostAppConsumer onMessageConsumer: Unknown Message Type {} from {} - ignoring",
                        topic.getType(), edgeNodeDescriptor);
            }

        } catch (Exception e) {
            // Debug (not Error) for extra logging regardless of configured Camel
            // ExceptionHandler
            LOG.debug(loggingMarker, "Exception caught processing exchange from Sparkplug Message", e);

            if (exchange != null) {
                exchange.setException(e);
            }
        } finally {
            if (exchange != null && exchange.getException() != null) {
                getExceptionHandler().handleException("Exception caught processing exchange from Sparkplug Message",
                        exchange, exchange.getException());
            }

            LOG.trace(loggingMarker, "TahuHostAppConsumer onMessageConsumer complete");
        }
    }

    void onMetricConsumer(EdgeNodeDescriptor edgeNodeDescriptor, Metric metric) {
        LOG.trace(loggingMarker, "TahuHostAppConsumer onMetricConsumer called: edgeNodeDescriptor {} metric {}",
                edgeNodeDescriptor, metric);

        Exchange exchange = null;
        try {
            exchange = createExchange(true);

            Message camelMessage = exchange.getMessage();
            camelMessage.setHeader(TahuConstants.EDGE_NODE_DESCRIPTOR, edgeNodeDescriptor.getDescriptorString());

            camelMessage.setHeader(TahuConstants.METRIC_HEADER_PREFIX + metric.getName(), metric);

            getProcessor().process(exchange);

        } catch (Exception e) {
            // Debug (not Error) for extra logging regardless of configured Camel
            // ExceptionHandler
            LOG.debug(loggingMarker, "Exception caught processing exchange from Sparkplug Metric", e);

            if (exchange != null) {
                exchange.setException(e);
            }
        } finally {
            if (exchange != null && exchange.getException() != null) {
                getExceptionHandler().handleException("Exception caught processing exchange from Sparkplug Metric",
                        exchange, exchange.getException());
            }

            LOG.trace(loggingMarker, "TahuHostAppConsumer onMetricConsumer complete");
        }
    }
}
