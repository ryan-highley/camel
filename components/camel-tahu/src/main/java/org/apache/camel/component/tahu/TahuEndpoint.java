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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.camel.*;
import org.apache.camel.spi.*;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.DefaultHeaderFilterStrategy;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/**
 * Sparkplug B Edge Node and Host Application support over MQTT using Eclipse Tahu
 */
@UriEndpoint(firstVersion = "4.0.0", scheme = TahuConstants.COMPONENT_SCHEME, title = "Tahu",
             syntax = TahuConstants.DEVICE_ENDPOINT_URI_SYNTAX, alternativeSyntax = TahuConstants.HOST_APP_ENDPOINT_URI_SYNTAX,
             category = {
                     Category.MESSAGING, Category.IOT, Category.MONITORING },
             headersClass = TahuConstants.class)
public class TahuEndpoint extends DefaultEndpoint implements HeaderFilterStrategyAware {

    private static final Logger LOG = LoggerFactory.getLogger(TahuEndpoint.class);

    @UriParam
    private final TahuConfiguration configuration;

    @UriPath(label = "producer", description = "ID of the group")
    @Metadata(required = true)
    private final String groupId;

    @UriPath(label = "producer", description = "ID of the edge node")
    @Metadata(required = true)
    private final String edgeNode;

    @UriPath(label = "producer (device only)", description = "ID of this edge node device")
    private final String deviceId;

    @UriPath(label = "consumer", description = "ID for the host application")
    @Metadata(required = true)
    private final String hostId;

    @UriParam(label = "producer (edge node only)", description = "Host ID of the primary host application for this edge node")
    private String primaryHostId;

    @UriParam(label = "producer",
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
                      +
                      "String,DateTime,Text,UUID,DataSet,Bytes,File,Template,Int8Array," +
                      "Int16Array,Int32Array,Int64Array,UInt8Array,UInt16Array,UInt32Array," +
                      "UInt64Array,FloatArray,DoubleArray,BooleanArray,StringArray," +
                      "DateTimeArray,Unknown")
    @Metadata(required = true)
    private Map<String, Object> metricDataTypes = Map.of();

    @UriParam(label = "producer,advanced", description = "Flag enabling support for metric aliases", defaultValue = "false")
    private boolean useAliases = false;

    @UriParam(label = "advanced",
              description = "To use a custom HeaderFilterStrategy to filter headers used as Sparkplug metrics",
              defaultValueNote = "Defaults to sending all Camel Message headers with name prefixes of \""
                                 + TahuConstants.METRIC_HEADER_PREFIX + "\", including those with null values")
    private HeaderFilterStrategy headerFilterStrategy;

    private final Marker loggingMarker;

    private static final ConcurrentMap<String, TahuConfiguration> endpointConfigurations = new ConcurrentHashMap<>();

    private Map<String, Map<String, Object>> metricDataTypeMap = Map.of();

    public static final TahuEndpoint getEdgeNodeEndpoint(String groupId, String edgeNode, TahuConfiguration configuration) {
        String sparkplugDescriptorString = groupId + TahuConstants.MAJOR_SEPARATOR + edgeNode;
        String uri = TahuConstants.COMPONENT_SCHEME + "://" + sparkplugDescriptorString;

        TahuEndpoint endpoint = new TahuEndpoint(uri, null, configuration, sparkplugDescriptorString);

        return endpoint;
    }

    public static final TahuEndpoint getDeviceEndpoint(
            String groupId, String edgeNode, String deviceId, TahuConfiguration configuration) {
        String sparkplugDescriptorString = groupId + TahuConstants.MAJOR_SEPARATOR + edgeNode
                                           + TahuConstants.MAJOR_SEPARATOR + deviceId;
        String uri = TahuConstants.COMPONENT_SCHEME + "://" + sparkplugDescriptorString;

        TahuEndpoint endpoint = new TahuEndpoint(uri, null, configuration, sparkplugDescriptorString);

        return endpoint;
    }

    public static final TahuEndpoint getHostAppEndpoint(String hostId, TahuConfiguration configuration) {
        String uri = TahuConstants.COMPONENT_SCHEME + "://" + hostId;

        TahuEndpoint endpoint = new TahuEndpoint(uri, null, configuration, hostId);

        return endpoint;
    }

    TahuEndpoint(String uri, TahuComponent component, TahuConfiguration configuration,
                 String sparkplugDescriptorString) {
        super(UnsafeUriCharactersEncoder.encode(uri), component);

        // Strip any leading separators
        while (ObjectHelper.isNotEmpty(sparkplugDescriptorString)
                && sparkplugDescriptorString.startsWith(TahuConstants.MAJOR_SEPARATOR)) {
            sparkplugDescriptorString = sparkplugDescriptorString.substring(TahuConstants.MAJOR_SEPARATOR.length());
        }

        loggingMarker = MarkerFactory.getMarker(sparkplugDescriptorString);

        LOG.trace(loggingMarker,
                "TahuEndpoint constructor called: uri {} component {} configuration {} sparkplugDescriptorString {}",
                uri, component, configuration, sparkplugDescriptorString);

        try {
            String configurationKey = null;

            String hostId = null;
            String groupId = null;
            String edgeNode = null;
            String deviceId = null;

            if (sparkplugDescriptorString.indexOf(TahuConstants.MAJOR_SEPARATOR) == -1) {
                hostId = sparkplugDescriptorString;

                configurationKey = hostId;

                LOG.debug(loggingMarker, "TahuEndpoint constructor found host app uri for {}",
                        sparkplugDescriptorString);
            } else {
                List<String> splitRemainingSegments = Arrays
                        .stream(sparkplugDescriptorString.split(TahuConstants.MAJOR_SEPARATOR, 3))
                        .map(String::trim).toList();

                groupId = splitRemainingSegments.get(0);
                edgeNode = splitRemainingSegments.get(1);

                configurationKey = groupId + TahuConstants.MAJOR_SEPARATOR + edgeNode;

                if (splitRemainingSegments.size() == 3) {
                    deviceId = splitRemainingSegments.get(2);

                    LOG.debug(loggingMarker, "TahuEndpoint constructor found device uri for {}",
                            sparkplugDescriptorString);
                } else {
                    LOG.debug(loggingMarker, "TahuEndpoint constructor found edge node uri for {}",
                            sparkplugDescriptorString);
                }
            }

            if ((ObjectHelper.isNotEmpty(hostId) && ObjectHelper.isEmpty(groupId) && ObjectHelper.isEmpty(edgeNode)) ||
                    (ObjectHelper.isEmpty(hostId) && ObjectHelper.isNotEmpty(groupId)
                            && ObjectHelper.isNotEmpty(edgeNode))) {
                this.hostId = hostId;
                this.groupId = groupId;
                this.edgeNode = edgeNode;
                this.deviceId = deviceId;

                LOG.info(loggingMarker,
                        "TahuEndpoint constructor found valid Sparkplug configuration for {}",
                        sparkplugDescriptorString);
            } else {
                throw new IllegalArgumentException(
                        "Invalid uri segment found parsing \"" + sparkplugDescriptorString
                                                   + "\" -- unable to continue");
            }

            TahuConfiguration existingConfiguration = endpointConfigurations.putIfAbsent(configurationKey,
                    configuration);
            if (existingConfiguration != null) {
                this.configuration = existingConfiguration;
            } else {
                this.configuration = configuration;
            }
        } finally {
            LOG.trace(loggingMarker, "TahuEndpoint constructor complete");
        }
    }

    public Producer createProducer() throws Exception {
        LOG.trace(loggingMarker, "Camel createProducer called");

        try {
            if (ObjectHelper.isEmpty(groupId) || ObjectHelper.isEmpty(edgeNode)) {
                throw new FailedToCreateProducerException(
                        this, new IllegalArgumentException("No groupId and/or edgeNode configured for this Endpoint"));
            }

            // deviceId may be null
            TahuProducer producer = new TahuProducer(this, groupId, edgeNode, deviceId);
            return producer;
        } finally {
            LOG.trace(loggingMarker, "Camel createProducer complete");
        }
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        LOG.trace(loggingMarker, "Camel createConsumer called");

        try {
            if (ObjectHelper.isEmpty(hostId)) {
                throw new FailedToCreateConsumerException(
                        this, new IllegalArgumentException("No hostId configured for this Endpoint"));
            }

            TahuConsumer consumer = new TahuConsumer(this, processor, hostId);
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
            Map<String, Map<String, Object>> newMetricDataTypeMap = new HashMap<>();

            metricDataTypes.forEach((configName, metricDataTypeObj) -> {
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

    @Override
    public void doStop() {
        LOG.trace(loggingMarker, "Camel doStop called");
        LOG.trace(loggingMarker, "Camel doStop complete");
    }

    public TahuConfiguration getConfiguration() {
        return configuration;
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

    public String getHostId() {
        return hostId;
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

    public Map<String, Map<String, Object>> getMetricDataTypeMap() {
        return metricDataTypeMap;
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

}
