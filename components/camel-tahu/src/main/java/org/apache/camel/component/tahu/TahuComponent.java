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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.camel.CamelContext;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.PropertiesHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component("tahu")
public class TahuComponent extends DefaultComponent implements SSLContextParametersAware {

    private static final Logger LOG = LoggerFactory.getLogger(TahuComponent.class);

    @Metadata(label = "advanced")
    private TahuConfiguration configuration = new TahuConfiguration();

    @Metadata(label = "security", defaultValue = "false")
    private boolean useGlobalSslContextParameters;

    public TahuComponent() {
    }

    public TahuComponent(CamelContext context) {
        super(context);
    }

    private final ConcurrentMap<String, TahuEndpoint> endpoints = new ConcurrentHashMap<>();

    @Override
    protected TahuEndpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters)
            throws Exception {

        LOG.trace("Camel createEndpoint called: uri {} remaining {}", uri, remaining);

        try {
            if (ObjectHelper.isEmpty(remaining)) {
                throw new IllegalArgumentException(
                        "Group Id and Edge Node Id must be configured on endpoint for edge nodes using syntax "
                                                   + TahuConstants.EDGE_NODE_ENDPOINT_URI_SYNTAX
                                                   + " ; Group Id, Edge Node Id, and Device Id must be configured on endpoint for edge node devices using syntax "
                                                   + TahuConstants.DEVICE_ENDPOINT_URI_SYNTAX
                                                   + " ; or Host ID must be configured on endpoint for host applications using syntax "
                                                   + TahuConstants.HOST_APP_ENDPOINT_URI_SYNTAX);
            }

            TahuEndpoint answer = endpoints.get(remaining);
            if (answer == null) {
                // Each endpoint can have its own configuration so make a copy of the
                // configuration
                TahuConfiguration tahuConfig = getConfiguration().copy();

                if (tahuConfig.getSslContextParameters() == null) {
                    tahuConfig.setSslContextParameters(retrieveGlobalSslContextParameters());
                }

                answer = new TahuEndpoint(uri, this, tahuConfig, remaining);

                TahuEndpoint existingAnswer = endpoints.putIfAbsent(remaining, answer);
                if (existingAnswer == null) {
                    Map<String, Object> metricDataTypes = PropertiesHelper.extractProperties(parameters, "metric.");
                    answer.setMetricDataTypes(metricDataTypes);

                    setProperties(answer, parameters);
                } else {
                    answer = existingAnswer;
                }
            }

            return answer;
        } finally {
            LOG.trace("Camel createEndpoint complete");
        }
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
