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

// import java.util.Map;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.FailedToCreateConsumerException;
import org.apache.camel.FailedToCreateProducerException;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.DefaultHeaderFilterStrategy;
import org.apache.camel.util.ObjectHelper;
import org.eclipse.tahu.message.BdSeqManager;
import org.eclipse.tahu.message.model.SparkplugBPayloadMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/**
 * Sparkplug B Edge Node and Host Application support over MQTT using Eclipse Tahu
 */
@UriEndpoint(firstVersion = "4.0.0",
             scheme = TahuConstants.BASE_SCHEME,
             title = "Tahu",
             syntax = TahuConstants.DEVICE_ENDPOINT_URI_SYNTAX,
             alternativeSyntax = TahuConstants.HOST_APP_ENDPOINT_URI_SYNTAX,
             category = { Category.MESSAGING, Category.IOT, Category.MONITORING },
             headersClass = TahuConstants.class)
public class TahuEndpoint extends DefaultEndpoint implements HeaderFilterStrategyAware {

    private static final Logger LOG = LoggerFactory.getLogger(TahuEndpoint.class);

    @UriPath(label = "producer", description = "ID of the group")
    @Metadata(applicableFor = { TahuConstants.EDGE_NODE_SCHEME, TahuConstants.DEVICE_SCHEME }, required = true)
    private final String groupId;

    @UriPath(label = "producer", description = "ID of the edge node")
    @Metadata(applicableFor = { TahuConstants.EDGE_NODE_SCHEME, TahuConstants.DEVICE_SCHEME }, required = true)
    private final String edgeNode;

    @UriPath(label = "producer (device only)", description = "ID of this edge node device")
    @Metadata(applicableFor = TahuConstants.DEVICE_SCHEME, required = true)
    private final String deviceId;

    @UriParam(label = "producer (edge node only)", description = "Host ID of the primary host application for this edge node")
    @Metadata(applicableFor = TahuConstants.EDGE_NODE_SCHEME)
    private String primaryHostId;

    @UriParam(label = "producer (edge node only)",
              description = "ID of each device connected to this edge node, as a comma-separated list")
    @Metadata(applicableFor = TahuConstants.EDGE_NODE_SCHEME, required = true)
    private String deviceIds;

    @UriParam(label = "producer",
              description = "Tahu SparkplugBPayloadMap to configure metric data types for this edge node or device  NOTE: This payload is used exclusively as a Sparkplug B spec-compliant configuration for all possible edge node or device metric names, aliases, and data types. This configuration is required to publish proper Sparkplug B NBIRTH and DBIRTH payloads.")
    @Metadata(applicableFor = { TahuConstants.EDGE_NODE_SCHEME, TahuConstants.DEVICE_SCHEME }, required = true)
    private SparkplugBPayloadMap metricDataTypePayloadMap;

    @UriParam
    private final TahuConfiguration configuration;

    @UriParam(label = "producer (edge node only),advanced", description = "Flag enabling support for metric aliases",
              defaultValue = "false")
    @Metadata(applicableFor = TahuConstants.EDGE_NODE_SCHEME)
    private boolean useAliases = false;

    @UriParam(label = "producer,advanced",
              description = "To use a custom HeaderFilterStrategy to filter headers used as Sparkplug metrics",
              defaultValueNote = "Defaults to sending all Camel Message headers with name prefixes of \""
                                 + TahuConstants.METRIC_HEADER_PREFIX + "\", including those with null values")
    @Metadata(applicableFor = { TahuConstants.EDGE_NODE_SCHEME, TahuConstants.DEVICE_SCHEME })
    private volatile HeaderFilterStrategy headerFilterStrategy;

    @UriParam(label = "producer,advanced",
              description = "To use a specific org.eclipse.tahu.message.BdSeqManager implementation to manage edge node birth-death sequence numbers",
              defaultValue = "org.apache.camel.component.tahu.CamelBdSeqManager")
    private volatile BdSeqManager bdSeqManager;

    @UriPath(label = "consumer", description = "ID for the host application")
    @Metadata(applicableFor = TahuConstants.HOST_APP_SCHEME, required = true)
    private final String hostId;

    private final Marker loggingMarker;

    TahuEndpoint(String uri, TahuComponent component, TahuConfiguration configuration,
                 String groupId, String edgeNode) {
        this(uri, component, configuration, groupId, edgeNode, null);
    }

    TahuEndpoint(String uri, TahuComponent component, TahuConfiguration configuration,
                 String groupId, String edgeNode, String deviceId) {
        super(uri, component);

        this.configuration = configuration;

        this.groupId = groupId;
        this.edgeNode = edgeNode;
        this.deviceId = deviceId;
        this.hostId = null;

        if (ObjectHelper.isNotEmpty(deviceId)) {
            loggingMarker = MarkerFactory
                    .getMarker(groupId + TahuConstants.MAJOR_SEPARATOR + edgeNode + TahuConstants.MAJOR_SEPARATOR + deviceId);
        } else {
            loggingMarker = MarkerFactory.getMarker(groupId + TahuConstants.MAJOR_SEPARATOR + edgeNode);
        }

    }

    TahuEndpoint(String uri, TahuComponent component, TahuConfiguration configuration, String hostId) {
        super(uri, component);

        this.configuration = configuration;

        this.groupId = null;
        this.edgeNode = null;
        this.deviceId = null;
        this.hostId = hostId;

        loggingMarker = MarkerFactory.getMarker(hostId);
    }

    @Override
    public Producer createProducer() throws Exception {
        LOG.trace(loggingMarker, "Camel createProducer called");

        try {
            if (ObjectHelper.isEmpty(getGroupId()) || ObjectHelper.isEmpty(getEdgeNode())) {
                throw new FailedToCreateProducerException(
                        this, new IllegalArgumentException("No groupId and/or edgeNode configured for this Endpoint"));
            }

            TahuEdgeProducer producer = new TahuEdgeProducer(this, groupId, edgeNode, deviceId);
            return producer;
        } finally {
            LOG.trace(loggingMarker, "Camel createProducer complete");
        }
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        LOG.trace(loggingMarker, "Camel createConsumer called");

        try {
            if (ObjectHelper.isEmpty(getHostId())) {
                throw new FailedToCreateConsumerException(
                        this, new IllegalArgumentException("No hostId configured for this Endpoint"));
            }

            TahuHostConsumer consumer = new TahuHostConsumer(this, processor, hostId);
            configureConsumer(consumer);
            return consumer;
        } finally {
            LOG.trace(loggingMarker, "Camel createConsumer complete");
        }
    }

    @Override
    public void doStart() {
        LOG.trace(loggingMarker, "Camel doStart called");

        LOG.trace(loggingMarker, "Camel doStart complete");
    }

    public String getGroupId() {
        return groupId;
    }

    public String getEdgeNode() {
        return edgeNode;
    }

    public String getDeviceId() {
        return deviceId;
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
        return Arrays.asList(deviceIds.split(","));
    }

    public SparkplugBPayloadMap getMetricDataTypePayloadMap() {
        return metricDataTypePayloadMap;
    }

    public void setMetricDataTypePayloadMap(SparkplugBPayloadMap metricDataTypePayloadMap) {
        this.metricDataTypePayloadMap = metricDataTypePayloadMap;
    }

    public boolean isUseAliases() {
        return useAliases;
    }

    public void setUseAliases(boolean useAliases) {
        this.useAliases = useAliases;
    }

    public BdSeqManager getBdSeqManager() {
        return bdSeqManager;
    }

    public void setBdSeqManager(BdSeqManager bdSeqManager) {
        this.bdSeqManager = bdSeqManager;
    }

    public String getHostId() {
        return hostId;
    }

    @Override
    public HeaderFilterStrategy getHeaderFilterStrategy() {
        HeaderFilterStrategy existingStrategy = this.headerFilterStrategy;
        if (existingStrategy == null) {
            DefaultHeaderFilterStrategy strategy = new DefaultHeaderFilterStrategy();
            this.headerFilterStrategy = existingStrategy = strategy;

            strategy.setFilterOnMatch(false);

            strategy.setOutFilter((String) null);
            strategy.setOutFilterPattern((String) null);
            strategy.setOutFilterStartsWith(TahuConstants.METRIC_HEADER_PREFIX);

            strategy.setAllowNullValues(true);
        }

        return existingStrategy;
    }

    @Override
    public void setHeaderFilterStrategy(HeaderFilterStrategy strategy) {
        this.headerFilterStrategy = strategy;
    }

    public TahuConfiguration getConfiguration() {
        return configuration;
    }

}
