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
package org.apache.camel.component.cxf.jaxws;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.HeaderFilterStrategyComponent;
import org.apache.camel.util.PropertiesHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Defines the <a href="http://camel.apache.org/cxf.html">CXF Component</a>
 */
@Component("cxf")
public class CxfComponent extends HeaderFilterStrategyComponent implements SSLContextParametersAware {

    private static final Logger LOG = LoggerFactory.getLogger(CxfComponent.class);

    @Metadata(label = "advanced")
    private Boolean allowStreaming;
    @Metadata(label = "security", defaultValue = "false")
    private boolean useGlobalSslContextParameters;
    @Metadata(defaultValue = "false", label = "producer,advanced",
              description = "Sets whether synchronous processing should be strictly used")
    private boolean synchronous;

    private final Map<String, BeanCacheEntry> beanCache = new HashMap<>();

    public CxfComponent() {
    }

    public CxfComponent(CamelContext context) {
        super(context);
    }

    /**
     * This option controls whether the CXF component, when running in PAYLOAD mode, will DOM parse the incoming
     * messages into DOM Elements or keep the payload as a javax.xml.transform.Source object that would allow streaming
     * in some cases.
     */
    public void setAllowStreaming(Boolean allowStreaming) {
        this.allowStreaming = allowStreaming;
    }

    public Boolean getAllowStreaming() {
        return allowStreaming;
    }

    @Override
    public boolean isUseGlobalSslContextParameters() {
        return this.useGlobalSslContextParameters;
    }

    /**
     * Enable usage of global SSL context parameters.
     */
    @Override
    public void setUseGlobalSslContextParameters(boolean useGlobalSslContextParameters) {
        this.useGlobalSslContextParameters = useGlobalSslContextParameters;
    }

    public boolean isSynchronous() {
        return synchronous;
    }

    public void setSynchronous(boolean synchronous) {
        this.synchronous = synchronous;
    }

    /**
     * Create a {@link CxfEndpoint} which, can be a Spring bean endpoint having URI format cxf:bean:<i>beanId</i> or
     * transport address endpoint having URI format cxf://<i>transportAddress</i>.
     */
    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        Object value = parameters.remove("setDefaultBus");
        if (value != null) {
            LOG.warn("The option setDefaultBus is @deprecated, use name defaultBus instead");
            if (!parameters.containsKey("defaultBus")) {
                parameters.put("defaultBus", value);
            }
        }

        if (allowStreaming != null && !parameters.containsKey("allowStreaming")) {
            parameters.put("allowStreaming", Boolean.toString(allowStreaming));
        }

        final CxfEndpoint result = createCxfEndpoint(remaining, parameters);

        result.setComponent(this);
        result.setCamelContext(getCamelContext());
        result.setSynchronous(isSynchronous());
        setEndpointHeaderFilterStrategy(result);
        setProperties(result, parameters);

        // extract the properties.xxx and set them as properties
        Map<String, Object> properties = PropertiesHelper.extractProperties(parameters, "properties.");
        result.setProperties(properties);

        // use global ssl config if set
        if (result.getSslContextParameters() == null) {
            result.setSslContextParameters(retrieveGlobalSslContextParameters());
        }

        return result;
    }

    private CxfEndpoint createCxfEndpoint(String remaining, Map<String, Object> parameters) {
        CxfEndpoint result;
        if (remaining.startsWith(CxfConstants.SPRING_CONTEXT_ENDPOINT)) {
            result = createSpringContextEndpoint(remaining, parameters);
        } else {
            // endpoint URI does not specify a bean
            result = createCxfEndpoint(remaining);
        }
        return result;
    }

    private CxfEndpoint createSpringContextEndpoint(String remaining, Map<String, Object> parameters) {
        CxfEndpoint result;
        // Get the bean from the Spring context
        String beanId = remaining.substring(CxfConstants.SPRING_CONTEXT_ENDPOINT.length());
        if (beanId.startsWith("//")) {
            beanId = beanId.substring(2);
        }

        result = createCxfSpringEndpoint(beanId);
        result.setBeanId(beanId);
        if (beanCache.containsKey(beanId)) {
            BeanCacheEntry entry = beanCache.get(beanId);
            if (entry.cxfEndpoint == result
                    && !entry.parameters.equals(parameters)) {
                /*different URI refer to the same CxfEndpoint Bean instance
                  but with different parameters. This can make stateful bean's
                  behavior uncertainty. This can be addressed by using proper
                  bean scope, such as "prototype" in Spring or "Session" in CDI
                  */
                throw new RuntimeException(
                        "Different URI refer to the same CxfEndpoint Bean instance"
                                           + " with ID : " + beanId
                                           + " but with different parameters. Please use the proper Bean scope ");
            }
        } else {
            beanCache.put(beanId, new BeanCacheEntry(result, new HashMap<>(parameters)));
        }
        return result;
    }

    protected CxfEndpoint createCxfSpringEndpoint(String beanId) {
        return CamelContextHelper.mandatoryLookup(getCamelContext(), beanId, CxfEndpoint.class);
    }

    protected CxfEndpoint createCxfEndpoint(String remaining) {
        return new CxfEndpoint(remaining, this);
    }

    @Override
    protected void afterConfiguration(String uri, String remaining, Endpoint endpoint, Map<String, Object> parameters)
            throws Exception {
        CxfEndpoint cxfEndpoint = (CxfEndpoint) endpoint;
        cxfEndpoint.updateEndpointUri(uri);
    }

    class BeanCacheEntry {
        //A snapshot of a CxfEndpoint Bean URI
        CxfEndpoint cxfEndpoint;
        Map<String, Object> parameters;

        BeanCacheEntry(CxfEndpoint cxfEndpoint, Map<String, Object> parameters) {
            this.cxfEndpoint = cxfEndpoint;
            this.parameters = parameters;
        }
    }
}
