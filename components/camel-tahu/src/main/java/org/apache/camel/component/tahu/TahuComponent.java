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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.camel.CamelContext;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.PropertiesHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(TahuConstants.BASE_SCHEME + "," + TahuConstants.EDGE_NODE_SCHEME + "," + TahuConstants.DEVICE_SCHEME + ","
           + TahuConstants.HOST_APP_SCHEME)
public class TahuComponent extends DefaultComponent implements SSLContextParametersAware {

    private static final Logger LOG = LoggerFactory.getLogger(TahuComponent.class);

    private final ConcurrentMap<String, TahuEndpoint> endpoints = new ConcurrentHashMap<>();

    @Metadata(label = "advanced")
    TahuConfiguration configuration = new TahuConfiguration();

    @Metadata(label = "security", defaultValue = "false")
    boolean useGlobalSslContextParameters;

    public TahuComponent() {
    }

    public TahuComponent(CamelContext camelContext) {
        super(camelContext);
    }

    public final TahuEdgeNodeEndpoint createEdgeNodeEndpoint(String groupId, String edgeNode) throws Exception {
        String sparkplugDescriptorString = groupId + TahuConstants.MAJOR_SEPARATOR + edgeNode;
        String uri = TahuConstants.EDGE_NODE_SCHEME + ":" + sparkplugDescriptorString;

        TahuEdgeNodeEndpoint endpoint = (TahuEdgeNodeEndpoint) createEndpoint(uri, sparkplugDescriptorString, Map.of());

        return endpoint;
    }

    public final TahuEdgeNodeEndpoint createDeviceEndpoint(
            String groupId, String edgeNode, String deviceId)
            throws Exception {
        String sparkplugDescriptorString = groupId + TahuConstants.MAJOR_SEPARATOR + edgeNode
                                           + TahuConstants.MAJOR_SEPARATOR + deviceId;
        String uri = TahuConstants.DEVICE_SCHEME + ":" + sparkplugDescriptorString;

        TahuEdgeNodeEndpoint endpoint = (TahuEdgeNodeEndpoint) createEndpoint(uri, sparkplugDescriptorString, Map.of());

        return endpoint;
    }

    public final TahuHostAppEndpoint createHostAppEndpoint(String hostId) throws Exception {
        String uri = TahuConstants.EDGE_NODE_SCHEME + ":" + hostId;

        TahuHostAppEndpoint endpoint = (TahuHostAppEndpoint) createEndpoint(uri, hostId, Map.of());

        return endpoint;
    }

    @Override
    protected TahuEndpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters)
            throws Exception {

        LOG.trace("Camel createEndpoint called: uri {} remaining {}", uri, remaining);

        try {
            if (ObjectHelper.isEmpty(remaining)) {
                throw new ResolveEndpointFailedException(uri, "empty remaining segments");
            }

            // Each endpoint can have its own configuration so make a copy of the
            // configuration
            TahuConfiguration endpointConfig = getConfiguration().copy();

            if (endpointConfig.getSslContextParameters() == null) {
                endpointConfig.setSslContextParameters(retrieveGlobalSslContextParameters());
            }

            TahuEndpoint answer;
            if (uri.startsWith(TahuConstants.HOST_APP_SCHEME)) {
                answer = createHostAppEndpoint(uri, remaining, endpointConfig);
            } else if (uri.startsWith(TahuConstants.EDGE_NODE_SCHEME) || uri.startsWith(TahuConstants.DEVICE_SCHEME)) {
                answer = createEdgeNodeEndpoint(uri, remaining, endpointConfig);

                Map<String, Object> metricDataTypes = PropertiesHelper.extractProperties(parameters, "metric.");
                ((TahuEdgeNodeEndpoint) answer).setMetricDataTypes(metricDataTypes);
            } else {
                throw new ResolveEndpointFailedException(uri, "unable to determine correct Endpoint type to create");
            }

            setProperties(answer, parameters);

            return answer;
        } finally {
            LOG.trace("Camel createEndpoint complete");
        }
    }

    private TahuHostAppEndpoint createHostAppEndpoint(
            String uri, String hostId, TahuConfiguration tahuConfig)
            throws Exception {
        TahuHostAppEndpoint answer = (TahuHostAppEndpoint) endpoints.get(hostId);
        if (answer == null) {
            answer = new TahuHostAppEndpoint(uri, this, tahuConfig, hostId);

            TahuHostAppEndpoint existingAnswer = (TahuHostAppEndpoint) endpoints.putIfAbsent(hostId, answer);
            if (existingAnswer != null) {
                answer = existingAnswer;
            }
        }

        return answer;
    }

    private TahuEdgeNodeEndpoint createEdgeNodeEndpoint(
            String uri, String remaining, TahuConfiguration tahuConfig)
            throws Exception {
        TahuEdgeNodeEndpoint answer = (TahuEdgeNodeEndpoint) endpoints.get(remaining);
        if (answer == null) {
            int requiredSegments = 2;
            if (uri.startsWith(TahuConstants.DEVICE_SCHEME)) {
                requiredSegments = 3;
            }

            List<String> descriptorSegments = Arrays
                    .stream(remaining.split(TahuConstants.MAJOR_SEPARATOR, requiredSegments))
                    .map(String::trim).filter(ObjectHelper::isNotEmpty).toList();

            if (descriptorSegments.size() < requiredSegments) {
                throw new ResolveEndpointFailedException(uri, "missing required remaining segments: " + remaining);
            }

            String groupId = descriptorSegments.get(0);
            String edgeNode = descriptorSegments.get(1);
            String deviceId = null;
            if (requiredSegments == 3) {
                deviceId = descriptorSegments.get(2);
            }

            answer = new TahuEdgeNodeEndpoint(uri, this, tahuConfig, groupId, edgeNode, deviceId);

            TahuEdgeNodeEndpoint existingAnswer = (TahuEdgeNodeEndpoint) endpoints.putIfAbsent(remaining, answer);
            if (existingAnswer != null) {
                answer = existingAnswer;
            }
        }

        return answer;
    }

    public TahuConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * To use a shared Tahu configuration
     */
    public void setConfiguration(TahuConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public boolean isUseGlobalSslContextParameters() {
        return this.useGlobalSslContextParameters;
    }

    /**
     * Enable/disable global SSL context parameters use
     */
    @Override
    public void setUseGlobalSslContextParameters(boolean useGlobalSslContextParameters) {
        this.useGlobalSslContextParameters = useGlobalSslContextParameters;
    }

}
