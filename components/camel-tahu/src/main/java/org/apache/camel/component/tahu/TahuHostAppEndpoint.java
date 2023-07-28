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

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.FailedToCreateConsumerException;
import org.apache.camel.FailedToCreateProducerException;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/**
 * Sparkplug B Host Application support over MQTT using Eclipse Tahu
 */
@UriEndpoint(firstVersion = "4.0.0", scheme = TahuConstants.HOST_APP_SCHEME,
             title = "Tahu Host App", extendsScheme = TahuConstants.BASE_SCHEME,
             syntax = TahuConstants.HOST_APP_ENDPOINT_URI_SYNTAX,
             category = { Category.MESSAGING, Category.IOT, Category.MONITORING },
             headersClass = TahuConstants.class, consumerOnly = true)
public class TahuHostAppEndpoint extends TahuEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(TahuEdgeNodeEndpoint.class);

    @UriPath(label = "consumer", description = "ID for the host application")
    @Metadata(applicableFor = TahuConstants.HOST_APP_SCHEME, required = true)
    private final String hostId;

    private final Marker loggingMarker;

    public TahuHostAppEndpoint(String uri, TahuComponent component, TahuConfiguration configuration, String hostId) {
        super(uri, component, configuration);

        this.hostId = hostId;

        loggingMarker = MarkerFactory.getMarker(hostId);
    }

    @Override
    public Producer createProducer() throws Exception {
        LOG.trace(loggingMarker, "Camel createProducer called");

        try {
            throw new FailedToCreateProducerException(
                    this, new IllegalStateException("Producer creation is not supported for Host App endpoints"));
        } finally {
            LOG.trace(loggingMarker, "Camel createProducer complete");
        }
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        LOG.trace(loggingMarker, "Camel createConsumer called");

        try {
            if (ObjectHelper.isEmpty(hostId)) {
                throw new FailedToCreateConsumerException(
                        this, new IllegalArgumentException("No hostId configured for this Endpoint"));
            }

            TahuHostAppConsumer consumer = new TahuHostAppConsumer(this, processor, hostId);
            configureConsumer(consumer);
            return consumer;
        } finally {
            LOG.trace(loggingMarker, "Camel createConsumer complete");
        }
    }

    public String getHostId() {
        return hostId;
    }

}
