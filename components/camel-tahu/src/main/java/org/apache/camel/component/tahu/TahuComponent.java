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

@Component(TahuConstants.BASE_SCHEME)
public class TahuComponent extends DefaultComponent implements SSLContextParametersAware {

    private final ConcurrentMap<String, TahuEndpoint> endpoints = new ConcurrentHashMap<>();

    @Metadata(label = "advanced")
    TahuConfiguration configuration;

    @Metadata(label = "security", defaultValue = "false")
    boolean useGlobalSslContextParameters;

    public TahuComponent() {
        this.configuration = createConfiguration();
    }

    public TahuComponent(CamelContext camelContext) {
        super(camelContext);
        this.configuration = createConfiguration();
    }

    public TahuComponent(TahuConfiguration configuration) {
        this.configuration = configuration;
    }

    public final TahuEndpoint createEdgeNodeEndpoint(String groupId, String edgeNode) throws Exception {
        String sparkplugDescriptorString = groupId + TahuConstants.MAJOR_SEPARATOR + edgeNode;
        String uri = TahuConstants.EDGE_NODE_SCHEME + ":" + sparkplugDescriptorString;

        TahuEndpoint endpoint = createEndpoint(uri, sparkplugDescriptorString, Map.of());

        return endpoint;
    }

    public final TahuEndpoint createDeviceEndpoint(
            String groupId, String edgeNode, String deviceId)
            throws Exception {
        String sparkplugDescriptorString = groupId + TahuConstants.MAJOR_SEPARATOR + edgeNode
                                           + TahuConstants.MAJOR_SEPARATOR + deviceId;
        String uri = TahuConstants.DEVICE_SCHEME + ":" + sparkplugDescriptorString;

        TahuEndpoint endpoint = createEndpoint(uri, sparkplugDescriptorString, Map.of());

        return endpoint;
    }

    public final TahuEndpoint createHostAppEndpoint(String hostId) throws Exception {
        String uri = TahuConstants.HOST_APP_SCHEME + ":" + hostId;

        TahuEndpoint endpoint = createEndpoint(uri, hostId, Map.of());

        return endpoint;
    }

    @Override
    protected TahuEndpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters)
            throws Exception {

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
        if (remaining.indexOf(TahuConstants.MAJOR_SEPARATOR) == -1) {
            answer = createHostAppEndpoint(uri, remaining, endpointConfig);
        } else {
            answer = createEdgeNodeEndpoint(uri, remaining, endpointConfig);
        }

        setProperties(answer, parameters);

        return answer;
    }

    private TahuEndpoint createHostAppEndpoint(
            String uri, String hostId, TahuConfiguration tahuConfig)
            throws Exception {
        return endpoints.computeIfAbsent(hostId, h -> new TahuEndpoint(uri, this, tahuConfig, h));
    }

    private TahuEndpoint createEdgeNodeEndpoint(
            String uri, String remaining, TahuConfiguration tahuConfig)
            throws Exception {
        return endpoints.computeIfAbsent(remaining, r -> {
            List<String> descriptorSegments = Arrays
                    .stream(r.split(TahuConstants.MAJOR_SEPARATOR, 3))
                    .map(String::trim).filter(ObjectHelper::isNotEmpty).toList();

            String groupId = descriptorSegments.get(0);
            String edgeNode = descriptorSegments.get(1);
            String deviceId = null;
            if (descriptorSegments.size() == 3) {
                deviceId = descriptorSegments.get(2);
            }

            return new TahuEndpoint(uri, this, tahuConfig, groupId, edgeNode, deviceId);
        });
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

    /**
     * Factory method to create the default configuration instance
     *
     * @return a newly created configuration object which can then be further customized
     */
    TahuConfiguration createConfiguration() {
        return new TahuConfiguration();
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
