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

import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.apache.camel.support.service.ServiceSupport;
import org.eclipse.tahu.SparkplugInvalidTypeException;
import org.eclipse.tahu.edge.EdgeClient;
import org.eclipse.tahu.edge.api.MetricHandler;
import org.eclipse.tahu.message.BdSeqManager;
import org.eclipse.tahu.message.SparkplugBPayloadEncoder;
import org.eclipse.tahu.message.model.DeviceDescriptor;
import org.eclipse.tahu.message.model.EdgeNodeDescriptor;
import org.eclipse.tahu.message.model.MessageType;
import org.eclipse.tahu.message.model.Metric;
import org.eclipse.tahu.message.model.MetricDataType;
import org.eclipse.tahu.message.model.SparkplugBPayload;
import org.eclipse.tahu.message.model.SparkplugBPayloadMap;
import org.eclipse.tahu.message.model.SparkplugDescriptor;
import org.eclipse.tahu.message.model.SparkplugMeta;
import org.eclipse.tahu.message.model.Topic;
import org.eclipse.tahu.model.MqttServerDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public class TahuEdgeNodeHandler extends ServiceSupport implements MetricHandler {

    private static final Logger LOG = LoggerFactory.getLogger(TahuEdgeNodeHandler.class);

    private final BdSeqManager bdSeqManager;
    private volatile long currentBirthBdSeq;
    private volatile long currentDeathBdSeq;

    private volatile EdgeClient client;
    private volatile Future<?> edgeClientFuture;
    private volatile boolean nBirthPublished;
    private final ExecutorService clientExecutorService;

    private final EdgeNodeDescriptor edgeNodeDescriptor;
    private final ConcurrentMap<EdgeNodeDescriptor, ConcurrentMap<String, Metric>> descriptorMetricMap
            = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, DeviceDescriptor> deviceDescriptorMap = new ConcurrentHashMap<>();

    private final Marker loggingMarker;

    private final List<MqttServerDefinition> serverDefinitions;

    private final String primaryHostId;
    private final boolean useAliases;
    private final long rebirthDebounceDelay;
    private final List<String> deviceIds;

    TahuEdgeNodeHandler(EdgeNodeDescriptor edgeNodeDescriptor, List<MqttServerDefinition> serverDefinitions,
                        String primaryHostId, boolean useAliases, long rebirthDebounceDelay,
                        Map<String, Map<String, Object>> metricDataTypeMap, ExecutorService clientExecutorService,
                        BdSeqManager bdSeqManager) {

        this.edgeNodeDescriptor = edgeNodeDescriptor;

        loggingMarker = MarkerFactory.getMarker(edgeNodeDescriptor.getDescriptorString());

        LOG.trace(loggingMarker, "TahuEdgeNodeHandler constructor called");

        this.clientExecutorService = clientExecutorService;

        this.serverDefinitions = List.copyOf(serverDefinitions);
        this.primaryHostId = primaryHostId;
        this.useAliases = useAliases;
        this.rebirthDebounceDelay = rebirthDebounceDelay;

        descriptorMetricMap.put(edgeNodeDescriptor, new ConcurrentHashMap<>());
        addMetricDataTypes(metricDataTypeMap);

        this.deviceIds = List.copyOf(deviceDescriptorMap.keySet());

        this.bdSeqManager = bdSeqManager;

        currentDeathBdSeq = bdSeqManager.getNextDeathBdSeqNum();
        currentBirthBdSeq = currentDeathBdSeq;

        LOG.trace(loggingMarker, "TahuEdgeNodeHandler constructor complete");
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        LOG.trace(loggingMarker, "Camel doInit called");

        EdgeClient edgeClient = client;
        if (edgeClient == null) {
            LOG.debug(loggingMarker, "doInit() : currentBirthBdSeq = {}  currentDeathBdSeq = {}", currentBirthBdSeq,
                    currentDeathBdSeq);

            TahuEdgeNodeClientCallback tahuClientCallback = new TahuEdgeNodeClientCallback(edgeNodeDescriptor, this);

            this.client = edgeClient = new EdgeClient(
                    this, edgeNodeDescriptor, deviceIds, primaryHostId, useAliases,
                    rebirthDebounceDelay, serverDefinitions, tahuClientCallback, null);

            tahuClientCallback.setClient(edgeClient);

            nBirthPublished = false;

        }

        LOG.trace(loggingMarker, "Camel doInit complete");
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        LOG.trace(loggingMarker, "Camel doStart called");

        EdgeClient edgeClient = client;
        if (edgeClient != null) {
            edgeClientFuture = clientExecutorService.submit(edgeClient);
        } else {
            throw new TahuException(
                    edgeNodeDescriptor, "Null EdgeClient found in doStart()",
                    new IllegalStateException());
        }

        LOG.trace(loggingMarker, "Camel doStart complete");
    }

    @Override
    protected void doSuspend() throws Exception {
        super.doSuspend();

        LOG.trace(loggingMarker, "Camel doSuspend called");

        EdgeClient edgeClient = client;
        if (edgeClient != null) {
            edgeClient.disconnect(true);
        }

        LOG.trace(loggingMarker, "Camel doSuspend complete");
    }

    @Override
    protected void doResume() throws Exception {
        super.doResume();

        LOG.trace(loggingMarker, "Camel doResume called");

        EdgeClient edgeClient = client;
        if (edgeClient != null) {
            edgeClient.handleRebirthRequest(true);
        }

        LOG.trace(loggingMarker, "Camel doResume complete");
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        LOG.trace(loggingMarker, "Camel doStop called");

        EdgeClient edgeClient = client;
        if (edgeClient != null) {
            edgeClient.shutdown();
            client = null;
            edgeClientFuture.cancel(true);
        }

        LOG.trace(loggingMarker, "Camel doStop complete");
    }

    private void addMetricDataTypes(Map<String, Map<String, Object>> metricDataTypeMap) {
        String edgeNodeId = edgeNodeDescriptor.getEdgeNodeId();

        metricDataTypeMap.forEach((descriptorId, metricDataTypes) -> {

            EdgeNodeDescriptor end;
            if (descriptorId.equals(edgeNodeId)) {
                end = edgeNodeDescriptor;
            } else {
                end = deviceDescriptorMap.computeIfAbsent(descriptorId,
                        deviceId -> new DeviceDescriptor(edgeNodeDescriptor, deviceId));
            }

            metricDataTypes.forEach((metricName, rawMetricDataType) -> {

                MetricDataType metricDataType;
                if (rawMetricDataType instanceof MetricDataType) {
                    metricDataType = (MetricDataType) rawMetricDataType;
                } else if (rawMetricDataType instanceof String) {
                    metricDataType = Enum.valueOf(MetricDataType.class, (String) rawMetricDataType);
                } else {
                    LOG.error(loggingMarker, "Invalid metricDataType type {} found for {}",
                            rawMetricDataType.getClass().getName(), descriptorId);
                    return;
                }

                if (end.isDeviceDescriptor()) {
                    LOG.trace(loggingMarker, "Found Device Metric Data Types for {} named {} with type {}",
                            descriptorId, metricName, metricDataType);
                } else {
                    LOG.trace(loggingMarker, "Found Edge Node Metric Data Type for {} named {} with type {}",
                            edgeNodeId, metricName, metricDataType);
                }

                // Create each metric for the side effect of populating descriptorMetricMap
                createMetric(end, metricName, metricDataType, null, new Date());
            });

        });

        // Add all Edge Node Template Metrics to each Device
        descriptorMetricMap.get(edgeNodeDescriptor).entrySet().stream()
                .filter(e -> e.getValue().getDataType() == MetricDataType.Template)
                .forEach(e -> {
                    String metricName = e.getKey();
                    Metric metric = e.getValue();

                    descriptorMetricMap.keySet().stream()
                            .filter(end -> !end.equals(edgeNodeDescriptor))
                            .forEach(end -> {
                                createMetric(end, metricName, metric.getDataType(), metric.getValue(),
                                        metric.getTimestamp());
                            });
                });
    }

    @Override
    public Topic getDeathTopic() {
        LOG.trace(loggingMarker, "MetricHandler getDeathTopic called");

        try {
            return new Topic(SparkplugMeta.SPARKPLUG_B_TOPIC_PREFIX, edgeNodeDescriptor, MessageType.NDEATH);
        } finally {
            LOG.trace(loggingMarker, "MetricHandler getDeathTopic complete");
        }
    }

    @Override
    public byte[] getDeathPayloadBytes() throws Exception {
        LOG.trace(loggingMarker, "MetricHandler getDeathPayloadBytes called");

        SparkplugBPayload deathPayload = new PayloadBuilder(edgeNodeDescriptor)
                .addMetric(createMetric(edgeNodeDescriptor, SparkplugMeta.SPARKPLUG_BD_SEQUENCE_NUMBER_KEY,
                        MetricDataType.Int64, currentDeathBdSeq, new Date()))
                .createPayload();

        LOG.debug(loggingMarker, "Created death payload with bdSeq metric {}", currentDeathBdSeq);

        currentBirthBdSeq = currentDeathBdSeq;
        currentDeathBdSeq = bdSeqManager.getNextDeathBdSeqNum();

        SparkplugBPayloadEncoder encoder = new SparkplugBPayloadEncoder();

        try {
            return encoder.getBytes(deathPayload, true);
        } finally {
            LOG.trace(loggingMarker, "MetricHandler getDeathPayloadBytes complete");
        }
    }

    @Override
    public boolean hasMetric(SparkplugDescriptor sparkplugDescriptor, String metricName) {
        LOG.trace(loggingMarker, "MetricHandler hasMetric called: sparkplugDescriptor {} metricName {}",
                sparkplugDescriptor, metricName);

        boolean metricFound = descriptorMetricMap.containsKey(sparkplugDescriptor)
                && descriptorMetricMap.get(sparkplugDescriptor).containsKey(metricName);
        try {
            return metricFound;
        } finally {
            LOG.trace(loggingMarker, "MetricHandler hasMetric complete (metricFound = {})", metricFound);
        }
    }

    @Override
    public void publishBirthSequence() {
        LOG.trace(loggingMarker, "MetricHandler publishBirthSequence called");

        try {
            // Tempting to skip publishing if nBirthPublished, but spec-compliant rebirth
            // handling prevents that

            Date timestamp = new Date();

            // SparkplugBPayloadMap, not a SparkplugBPayload--can't use createPayload()
            SparkplugBPayloadMap nBirthPayload = new SparkplugBPayloadMap.SparkplugBPayloadMapBuilder()
                    .setTimestamp(timestamp)
                    .addMetric(createMetric(edgeNodeDescriptor, SparkplugMeta.SPARKPLUG_BD_SEQUENCE_NUMBER_KEY,
                            currentBirthBdSeq, timestamp))
                    .addMetrics(getCachedMetrics(edgeNodeDescriptor, timestamp))
                    .createPayload();

            LOG.debug(loggingMarker, "Created birth payload with bdSeq metric {}", currentBirthBdSeq);

            client.publishNodeBirth(nBirthPayload);

            deviceDescriptorMap.forEach((deviceId, deviceDescriptor) -> {
                SparkplugBPayload dBirthPayload = new PayloadBuilder(deviceDescriptor)
                        .setTimestamp(timestamp.getTime())
                        .addMetrics(getCachedMetrics(deviceDescriptor, timestamp))
                        .createPayload();

                client.publishDeviceBirth(deviceId, dBirthPayload);
            });

            nBirthPublished = true;

        } catch (Exception e) {
            LOG.error(loggingMarker, "Exception caught publishing birth sequence", e);
            throw new TahuException(edgeNodeDescriptor, e);
        } finally {
            LOG.trace(loggingMarker, "MetricHandler publishBirthSequence complete");
        }
    }

    private List<Metric> getCachedMetrics(EdgeNodeDescriptor metricDescriptor, Date timestamp) {
        return descriptorMetricMap.get(edgeNodeDescriptor).entrySet().stream().map(entry -> {
            try {
                Metric metric = new Metric(entry.getValue());
                metric.setTimestamp(timestamp);
                return metric;
            } catch (SparkplugInvalidTypeException e) {
                LOG.warn(loggingMarker, "Exception caught copying cached metric publishing node birth sequence", e);
                return null;
            }
        }).filter(m -> m != null).toList();
    }

    long getCurrentBirthBdSeq() {
        LOG.trace(loggingMarker, "getCurrentBirthBdSeq() : {}", currentBirthBdSeq);
        return currentBirthBdSeq;
    }

    long getCurrentDeathBdSeq() {
        LOG.trace(loggingMarker, "getCurrentDeathBdSeq() : {}", currentDeathBdSeq);
        return currentDeathBdSeq;
    }

    void handleNCMDMessage(SparkplugBPayload ncmdPayload) {
        List<Metric> responseMetrics = processCMDMetrics(ncmdPayload, edgeNodeDescriptor);

        if (responseMetrics.isEmpty()) {
            LOG.warn(loggingMarker, "Received NCMD with no valid metrics to write for {} - ignoring",
                    edgeNodeDescriptor);
            return;
        }

        SparkplugBPayload ndataPayload = new PayloadBuilder(edgeNodeDescriptor).createPayload();

        ndataPayload.addMetrics(responseMetrics);

        LOG.debug(loggingMarker, "Publishing NDATA based on NCMD message for {}", edgeNodeDescriptor);

        publishNodeData(ndataPayload);
    }

    void handleDCMDMessage(SparkplugBPayload dcmdPayload, String deviceId) {
        DeviceDescriptor deviceDescriptor = new DeviceDescriptor(edgeNodeDescriptor, deviceId);

        List<Metric> responseMetrics = processCMDMetrics(dcmdPayload, deviceDescriptor);

        if (responseMetrics.isEmpty()) {
            LOG.warn(loggingMarker, "Received DCMD with no valid metrics to write for {} - ignoring", deviceDescriptor);
            return;
        }

        SparkplugBPayload ddataPayload = new PayloadBuilder(deviceDescriptor).createPayload();

        ddataPayload.addMetrics(responseMetrics);

        LOG.debug(loggingMarker, "Publishing DDATA based on DCMD message for {}", deviceDescriptor);

        publishDeviceData(deviceId, ddataPayload);
    }

    private List<Metric> processCMDMetrics(SparkplugBPayload payload, EdgeNodeDescriptor cmdDescriptor) {
        List<Metric> receivedMetrics = payload.getMetrics();
        if (receivedMetrics == null || receivedMetrics.isEmpty()) {
            return List.of();
        }

        // Look for a METRIC_NODE_REBIRTH received metric with True value
        if (!cmdDescriptor.isDeviceDescriptor()) {
            Map<Boolean, List<Metric>> groupedMetrics = receivedMetrics.stream()
                    .collect(Collectors.groupingBy(m -> (Boolean) SparkplugMeta.METRIC_NODE_REBIRTH.equals(m.getName())
                            && m.getDataType() == MetricDataType.Boolean && (Boolean) m.getValue()));

            if (groupedMetrics.containsKey(Boolean.TRUE) && !groupedMetrics.get(Boolean.TRUE).isEmpty()) {
                client.handleRebirthRequest(true);
            }

            receivedMetrics = groupedMetrics.get(Boolean.FALSE);
        }

        List<Metric> responseMetrics = receivedMetrics.stream().map(m -> {
            if (!hasMetric(cmdDescriptor, m.getName())) {
                LOG.warn(loggingMarker, "Received CMD request for {} metric {} not in configured metrics - skipping",
                        cmdDescriptor, m.getName());
                return null;
            }

            try {
                Metric responseMetric = new Metric(descriptorMetricMap.get(cmdDescriptor).get(m.getName()));

                responseMetric.setHistorical(true);

                return responseMetric;
            } catch (SparkplugInvalidTypeException e) {
                LOG.warn(loggingMarker,
                        "Exception caught copying metric handling CMD request for {} metric {} - skipping",
                        cmdDescriptor, m.getName());
                return null;
            }
        }).filter(Objects::nonNull).toList();

        return responseMetrics;
    }

    private Metric createMetric(
            EdgeNodeDescriptor metricDescriptor, String metricName, Object metricValue,
            Date timestamp) {

        if (!hasMetric(metricDescriptor, metricName)) {
            String message = String.format(
                    "Unable to create metric for %1$s with name %2$s and value %3$s due to no metric configuration found",
                    metricDescriptor, metricName, metricValue);
            LOG.error(loggingMarker, message);
            throw new IllegalArgumentException(message);
        }

        MetricDataType metricType = null;
        switch (metricName) {
            case SparkplugMeta.SPARKPLUG_BD_SEQUENCE_NUMBER_KEY:
                metricType = MetricDataType.Int64;
                break;

            case SparkplugMeta.METRIC_NODE_REBIRTH:
                metricType = MetricDataType.Boolean;
                break;

            default:
                metricType = descriptorMetricMap.get(metricDescriptor).get(metricName).getDataType();
                break;
        }

        return createMetric(metricDescriptor, metricName, metricType, metricValue, timestamp);
    }

    private Metric createMetric(
            EdgeNodeDescriptor edgeNodeDescriptor, String metricName, MetricDataType metricDataType, Object metricValue,
            Date timestamp) {
        try {
            ConcurrentMap<String, Metric> descriptorMetrics = descriptorMetricMap.computeIfAbsent(edgeNodeDescriptor,
                    sd -> new ConcurrentHashMap<>());

            Metric metric = new Metric.MetricBuilder(metricName, metricDataType, metricValue).createMetric();
            metric.setTimestamp(timestamp);

            Metric existingMetric = descriptorMetrics.putIfAbsent(metricName, metric);
            if (existingMetric != null) {
                // Use a copy of the existing metric instead of the new one
                metric = new Metric(existingMetric);

                // Update the cached metric value
                existingMetric.setValue(metricValue);

                // Update the cache metric timestamp as the last modified time
                existingMetric.setTimestamp(timestamp);
            }

            return metric;
        } catch (SparkplugInvalidTypeException e) {
            LOG.error(loggingMarker, "Exception caught creating metric for name {} with type {} and value {}",
                    metricName, metricDataType, metricValue);
            throw new TahuException(edgeNodeDescriptor, e);
        }
    }

    private void publishNodeData(SparkplugBPayload ndataPayload) {
        LOG.trace(loggingMarker, "TahuMetricHandler publishNodeData called: ndataPayload {}", ndataPayload);
        try {
            client.publishNodeData(ndataPayload);
        } catch (Exception e) {
            LOG.error(loggingMarker, "Error publishing NDATA message", e);
            throw new TahuException(edgeNodeDescriptor, e);
        } finally {
            LOG.trace(loggingMarker, "TahuMetricHandler publishNodeData complete");
        }
    }

    private void publishDeviceData(String deviceId, SparkplugBPayload ddataPayload) {
        LOG.trace(loggingMarker, "TahuMetricHandler publishDeviceData called: deviceId {} ddataPayload {}", deviceId,
                ddataPayload);
        try {
            client.publishDeviceData(deviceId, ddataPayload);
        } catch (Exception e) {
            LOG.error(loggingMarker, "Error publishing DDATA message for {}", deviceDescriptorMap.get(deviceId), e);
            throw new TahuException(deviceDescriptorMap.get(deviceId), e);
        } finally {
            LOG.trace(loggingMarker, "TahuMetricHandler publishDeviceData complete");
        }
    }

    class PayloadBuilder {

        private final SparkplugBPayload.SparkplugBPayloadBuilder sparkplugBuilder;
        private final EdgeNodeDescriptor payloadDescriptor;

        private long timestamp;

        PayloadBuilder(EdgeNodeDescriptor payloadDescriptor) {
            sparkplugBuilder = new SparkplugBPayload.SparkplugBPayloadBuilder();

            setTimestamp(Instant.now().toEpochMilli());
            this.payloadDescriptor = payloadDescriptor;
        }

        PayloadBuilder setTimestamp(long timestamp) {
            this.timestamp = timestamp;
            sparkplugBuilder.setTimestamp(new Date(timestamp));
            return this;
        }

        PayloadBuilder setUUID(String uuidString) {
            sparkplugBuilder.setUuid(uuidString);
            return this;
        }

        PayloadBuilder setUUID(UUID uuid) {
            sparkplugBuilder.setUuid(uuid.toString());
            return this;
        }

        PayloadBuilder setBody(byte[] body) {
            sparkplugBuilder.setBody(body);
            return this;
        }

        PayloadBuilder addMetric(String metricName, Object metricValue) {
            return addMetric(createMetric(payloadDescriptor, metricName, metricValue, new Date(timestamp)));
        }

        PayloadBuilder addMetric(Metric metric) {
            sparkplugBuilder.addMetric(metric);
            return this;
        }

        PayloadBuilder addMetrics(Collection<Metric> metrics) {
            sparkplugBuilder.addMetrics(metrics);
            return this;
        }

        SparkplugBPayload createPayload() {
            return sparkplugBuilder.createPayload();
        }

        void publish() {
            if (nBirthPublished) {
                SparkplugBPayload payload = createPayload();

                if (payloadDescriptor.isDeviceDescriptor()) {
                    publishDeviceData(((DeviceDescriptor) payloadDescriptor).getDeviceId(), payload);
                } else {
                    publishNodeData(payload);
                }
            } else {
                LOG.warn(loggingMarker,
                        "Attempted to publish data payload before birth payloads - cached metric values updated instead");
            }
        }
    }

    public EdgeNodeDescriptor getEdgeNodeDescriptor() {
        return edgeNodeDescriptor;
    }

}
