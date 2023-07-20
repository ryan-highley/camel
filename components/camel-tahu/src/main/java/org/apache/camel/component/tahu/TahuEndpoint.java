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
import java.util.concurrent.ExecutorService;

import org.apache.camel.*;
import org.apache.camel.spi.*;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.DefaultHeaderFilterStrategy;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.eclipse.tahu.message.model.EdgeNodeDescriptor;
import org.eclipse.tahu.message.model.MetricDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sparkplug B Edge Node and Host Application support over MQTT using Eclipse Tahu
 */
@UriEndpoint(firstVersion = "4.0.0", scheme = TahuConstants.COMPONENT_SCHEME, title = "Tahu",
             syntax = TahuConstants.DEVICE_ENDPOINT_URL_SYNTAX, alternativeSyntax = TahuConstants.HOST_APP_ENDPOINT_URL_SYNTAX,
             category = { Category.MESSAGING, Category.IOT, Category.MONITORING }, headersClass = TahuConstants.class)
public class TahuEndpoint extends DefaultEndpoint implements HeaderFilterStrategyAware {

    private static final Logger LOG = LoggerFactory.getLogger(TahuEndpoint.class);
    @UriParam
    private final TahuConfiguration configuration;
    @UriPath(label = "producer", description = "ID of the group")
    @Metadata(required = true)
    private String groupId;
    @UriPath(label = "producer", description = "ID of the edge node")
    @Metadata(required = true)
    private String edgeNode;
    @UriPath(label = "producer (device only)",
             description = "ID of this edge node device - must also be listed in the corresponding edge node endpoint's \"deviceIds\" list")
    private String deviceId;
    @UriPath(label = "consumer", description = "ID for the host application")
    @Metadata(required = true)
    private String hostId;
    @UriParam(label = "producer (edge node only)", description = "Host ID of the primary host application for this edge node")
    private String primaryHostId;
    @UriParam(label = "producer (edge node only)",
              description = "IDs of all devices attached to this edge node as a comma-separated list")
    private String deviceIds;
    @UriParam(label = "producer",
              description = "Metric names and types for this edge node or device, as \"metric.<metricName> = <metricDataType>\"",
              prefix = "metric.", multiValue = true)
    @Metadata(required = true)
    private Map<String, MetricDataType> metricConfigs;
    @UriParam(label = "advanced",
              description = "To use a custom HeaderFilterStrategy to filter headers used as Sparkplug metrics",
              defaultValueNote = "Defaults to sending all Camel Message headers with names starting with \""
                                 + TahuConstants.METRIC_HEADER_PREFIX + "\", including those with null values")
    private HeaderFilterStrategy headerFilterStrategy;

    public TahuEndpoint(TahuConfiguration configuration) {
        this(null, null, configuration, null);
    }

    public TahuEndpoint(String groupId, String edgeNode, TahuConfiguration configuration) {
        this(null, null, configuration, groupId + TahuConstants.MAJOR_SEPARATOR + edgeNode);
    }

    public TahuEndpoint(String groupId, String edgeNode, String deviceId, TahuConfiguration configuration) {
        this(null, null, configuration,
             groupId + TahuConstants.MAJOR_SEPARATOR + edgeNode + TahuConstants.MAJOR_SEPARATOR + deviceId);
    }

    public TahuEndpoint(String hostId, TahuConfiguration configuration) {
        this(null, null, configuration, hostId);
    }

    TahuEndpoint(String uri, TahuComponent component, TahuConfiguration configuration, String remaining) {
        super(UnsafeUriCharactersEncoder.encode(uri), component);

        this.configuration = configuration;

        if (ObjectHelper.isNotEmpty(remaining)) {
            parseRemaining(remaining);
        }
    }

    public Producer createProducer() throws Exception {
        LOG.trace("{}: Camel createProducer called", getEndpointLoggingString(LOG.isTraceEnabled()));

        if (ObjectHelper.isEmpty(groupId) || ObjectHelper.isEmpty(edgeNode)) {
            throw new FailedToCreateProducerException(
                    this, new IllegalArgumentException("No groupId and/or edgeNode configured for this Endpoint"));
        }

        return new TahuProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        LOG.trace("{}: Camel createConsumer called", getEndpointLoggingString(LOG.isTraceEnabled()));

        if (ObjectHelper.isEmpty(hostId)) {
            throw new FailedToCreateConsumerException(
                    this, new IllegalArgumentException("No hostId configured for this Endpoint"));
        }

        Consumer consumer = new TahuConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    private String getEndpointLoggingString(boolean isLoggingEnabled) {
        if (!isLoggingEnabled)
            return null;

        return String.format("groupId %1$s edgeNode %2$s deviceId %3$s hostId %4$s", groupId, edgeNode, deviceId, hostId);
    }

    @Override
    public void doStart() {
        LOG.trace("{}: Camel doStart called", getEndpointLoggingString(LOG.isTraceEnabled()));
    }

    @Override
    public void doStop() {
        LOG.trace("{}: Camel doStop called", getEndpointLoggingString(LOG.isTraceEnabled()));
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getEdgeNode() {
        return edgeNode;
    }

    public void setEdgeNode(String edgeNode) {
        this.edgeNode = edgeNode;
    }

    public EdgeNodeDescriptor getEdgeNodeDescriptor() {
        return new EdgeNodeDescriptor(groupId, edgeNode);
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getHostId() {
        return hostId;
    }

    public void setHostId(String hostId) {
        this.hostId = hostId;
    }

    public String getPrimaryHostId() {
        return primaryHostId;
    }

    public void setPrimaryHostId(String primaryHostId) {
        this.primaryHostId = primaryHostId;
    }

    public String getDeviceIds() {
        return deviceIds;
    }

    public void setDeviceIds(String deviceIds) {
        this.deviceIds = deviceIds;
    }

    public List<String> getDeviceIdList() {
        List<String> deviceIdList;
        if (ObjectHelper.isEmpty(deviceIds)) {
            deviceIdList = List.of();
        } else {
            deviceIdList = List.of(deviceIds.split(","));
        }
        return deviceIdList;
    }

    public Map<String, MetricDataType> getMetricConfigs() {
        return metricConfigs;
    }

    public void setMetricConfigs(Map<String, MetricDataType> metricConfigs) {
        this.metricConfigs = Map.copyOf(metricConfigs);
    }

    public TahuConfiguration getConfiguration() {
        return configuration;
    }

    protected void parseRemaining(String remaining) {
        List<String> splitRemainingSegments
                = Arrays.stream(remaining.split(TahuConstants.MAJOR_SEPARATOR)).map(String::trim).toList();

        if (ObjectHelper.isEmpty(splitRemainingSegments) || splitRemainingSegments.stream().anyMatch(ObjectHelper::isEmpty)) {
            throw new IllegalArgumentException(
                    "Empty uri remaining segment found parsing \"" + remaining + "\" -- unable to continue");
        }

        if (splitRemainingSegments.size() > 3) {
            throw new IllegalArgumentException(
                    "Too many uri remaining segments found parsing \"" + remaining + "\" -- unable to continue");
        }

        if (splitRemainingSegments.size() == 1 && ObjectHelper.isEmpty(getHostId())) {

            setHostId(remaining);

        } else {

            if (ObjectHelper.isEmpty(getGroupId())) {
                setGroupId(splitRemainingSegments.get(0));
            }

            if (ObjectHelper.isEmpty(getEdgeNode())) {
                setEdgeNode(splitRemainingSegments.get(1));
            }

            if (splitRemainingSegments.size() == 3 && ObjectHelper.isEmpty(getDeviceId())) {
                setDeviceId(splitRemainingSegments.get(2));
            }

        }
    }

    @Override
    public HeaderFilterStrategy getHeaderFilterStrategy() {
        if (headerFilterStrategy == null) {
            DefaultHeaderFilterStrategy defaultHeaderFilterStrategy = new DefaultHeaderFilterStrategy();
            headerFilterStrategy = defaultHeaderFilterStrategy;

            defaultHeaderFilterStrategy.setFilterOnMatch(false);

            defaultHeaderFilterStrategy.setOutFilter(null);
            defaultHeaderFilterStrategy.setOutFilterPattern((String) null);
            defaultHeaderFilterStrategy.setOutFilterStartsWith(TahuConstants.METRIC_HEADER_PREFIX);

            defaultHeaderFilterStrategy.setAllowNullValues(true);
        }

        return headerFilterStrategy;
    }

    @Override
    public void setHeaderFilterStrategy(HeaderFilterStrategy strategy) {
        this.headerFilterStrategy = strategy;
    }

    protected ExecutorService createConsumerExecutor() {
        return getCamelContext().getExecutorServiceManager().newSingleThreadExecutor(this, "TahuConsumer");
    }

    protected ExecutorService createProducerExecutor() {
        return getCamelContext().getExecutorServiceManager().newSingleThreadExecutor(this, "TahuProducer");
    }
}
