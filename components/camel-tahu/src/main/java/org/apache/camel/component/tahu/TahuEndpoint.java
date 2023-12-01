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
import java.util.HashMap;
import java.util.Map;

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
              description = "Metric names and types for this edge node or device ID(s), as \"metric.[edgeNode,]deviceId[,deviceId...])"
                            + TahuConstants.MAJOR_SEPARATOR
                            + "<metricName> = <metricDataType>\"  NOTE: Uses only the FIRST '" + TahuConstants.MAJOR_SEPARATOR
                            + "' character to separate the edge node ID or device ID(s) from the metric name, allowing the '"
                            + TahuConstants.MAJOR_SEPARATOR
                            + "' character to appear any number of times in the metric name. Comma characters also are only evaluated before the first '"
                            + TahuConstants.MAJOR_SEPARATOR
                            + "' character, meaning commas are allowed in metric names but NOT edge node or device IDs. The edge node ID and device IDs can be intermixed in the list to support configuring the same metric name and data type across both the edge node and devices.",
              prefix = "metric.", multiValue = true,
              enums = "Int8,Int16,Int32,Int64,UInt8,UInt16,UInt32,UInt64,Float,Double,Boolean,"
                      + "String,DateTime,Text,UUID,DataSet,Bytes,File,Template,Int8Array,"
                      + "Int16Array,Int32Array,Int64Array,UInt8Array,UInt16Array,UInt32Array,"
                      + "UInt64Array,FloatArray,DoubleArray,BooleanArray,StringArray,"
                      + "DateTimeArray,Unknown")
    @Metadata(applicableFor = TahuConstants.EDGE_NODE_SCHEME)
    private Map<String, Object> metricDataTypes = Map.of();

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

    private Map<String, Map<String, Object>> metricDataTypeMap = Map.of();

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

            TahuEdgeNodeProducer producer = new TahuEdgeNodeProducer(this, groupId, edgeNode, deviceId);
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

            TahuHostAppConsumer consumer = new TahuHostAppConsumer(this, processor, hostId);
            configureConsumer(consumer);
            return consumer;
        } finally {
            LOG.trace(loggingMarker, "Camel createConsumer complete");
        }
    }

    @Override
    public void doStart() {
        LOG.trace(loggingMarker, "Camel doStart called");

        try {
            if (hostId != null) {
                return;
            }

            Map<String, Map<String, Object>> newMetricDataTypeMap = new HashMap<>();

            getMetricDataTypes().forEach((configName, metricDataTypeObj) -> {
                int metricNameIdx = configName.indexOf(TahuConstants.MAJOR_SEPARATOR);
                if (metricNameIdx <= 0) {
                    LOG.error(loggingMarker, "Invalid metricDataType configuration found: {}", configName);
                    return;
                }

                if (metricDataTypeObj == null) {
                    LOG.error(loggingMarker, "Null metricDataType configuration found for {}", configName);
                    return;
                }

                String metricName = configName.substring(metricNameIdx + TahuConstants.MAJOR_SEPARATOR.length());
                String[] descriptorNames = configName.substring(0, metricNameIdx)
                        .split(TahuConstants.CONFIG_LIST_SEPARATOR);

                Arrays.stream(descriptorNames)
                        .forEach(descriptorName -> {
                            Map<String, Object> metricTypes = newMetricDataTypeMap.computeIfAbsent(descriptorName,
                                    _x -> new HashMap<>());

                            metricTypes.put(metricName, metricDataTypeObj);
                        });
            });

            newMetricDataTypeMap.replaceAll((descriptorName, typeMap) -> Map.copyOf(typeMap));

            metricDataTypeMap = Map.copyOf(newMetricDataTypeMap);
        } finally {
            LOG.trace(loggingMarker, "Camel doStart complete");
        }

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

    public Map<String, Object> getMetricDataTypes() {
        return metricDataTypes;
    }

    public void setMetricDataTypes(Map<String, Object> metricDataTypes) {
        this.metricDataTypes = Map.copyOf(metricDataTypes);
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

    public Map<String, Map<String, Object>> getMetricDataTypeMap() {
        return metricDataTypeMap;
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

            strategy.setOutFilter(null);
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
