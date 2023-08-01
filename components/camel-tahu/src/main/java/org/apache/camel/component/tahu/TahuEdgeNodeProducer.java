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

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.tahu.TahuEdgeNodeHandler.PayloadBuilder;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
import org.eclipse.tahu.message.model.EdgeNodeDescriptor;
import org.eclipse.tahu.message.model.Metric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public class TahuEdgeNodeProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(TahuEdgeNodeProducer.class);

    private final HeaderFilterStrategy headerFilterStrategy;

    private final TahuEdgeNodeHandler tahuEdgeNodeHandler;
    private final EdgeNodeDescriptor edgeNodeDescriptor;

    private final Marker loggingMarker;

    TahuEdgeNodeProducer(TahuEdgeNodeEndpoint endpoint, TahuEdgeNodeHandler tahuEdgeNodeHandler) {
        super(endpoint);

        this.tahuEdgeNodeHandler = tahuEdgeNodeHandler;
        edgeNodeDescriptor = tahuEdgeNodeHandler.getEdgeNodeDescriptor();

        loggingMarker = MarkerFactory.getMarker(edgeNodeDescriptor.getDescriptorString());

        LOG.trace(loggingMarker, "TahuProducer constructor called endpoint {}", endpoint);

        headerFilterStrategy = endpoint.getHeaderFilterStrategy();

        LOG.trace(loggingMarker, "TahuProducer constructor complete");
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        LOG.trace(loggingMarker, "Camel doStart called");

        if (!tahuEdgeNodeHandler.isStarted()) {
            ServiceHelper.startService(tahuEdgeNodeHandler);
        }

        LOG.trace(loggingMarker, "Camel doStart complete");
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        LOG.trace(loggingMarker, "Camel doStop called");

        if (ObjectHelper.isNotEmpty(tahuEdgeNodeHandler) && !edgeNodeDescriptor.isDeviceDescriptor()) {
            ServiceHelper.stopAndShutdownService(tahuEdgeNodeHandler);
        }

        LOG.trace(loggingMarker, "Camel doStop complete");
    }

    @Override
    public void process(Exchange exchange) {
        LOG.trace(loggingMarker, "Camel process called: exchange {}", exchange);

        try {
            String toEndpointUri = exchange.getProperty(Exchange.TO_ENDPOINT, String.class);
            LOG.trace(loggingMarker, "Camel process toEndpointUri {}", toEndpointUri);

            Message message = exchange.getMessage();

            PayloadBuilder dataPayloadBuilder = tahuEdgeNodeHandler.new PayloadBuilder(edgeNodeDescriptor);
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

}
