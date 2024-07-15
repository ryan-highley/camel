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
import org.eclipse.tahu.message.model.Metric.MetricBuilder;
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
    private final ConcurrentMap<SparkplugDescriptor, SparkplugBPayloadMap> descriptorMetricMap = new ConcurrentHashMap<>();

    private final Marker loggingMarker;

    private final List<MqttServerDefinition> serverDefinitions;

    private final String primaryHostId;
    private final boolean useAliases;
    private final long rebirthDebounceDelay;

    TahuEdgeNodeHandler(EdgeNodeDescriptor edgeNodeDescriptor, List<MqttServerDefinition> serverDefinitions,
                        String primaryHostId, boolean useAliases, long rebirthDebounceDelay,
                        ExecutorService clientExecutorService, BdSeqManager bdSeqManager) {

        this.edgeNodeDescriptor = edgeNodeDescriptor;

        loggingMarker = MarkerFactory.getMarker(edgeNodeDescriptor.getDescriptorString());

        LOG.trace(loggingMarker, "TahuEdgeNodeHandler constructor called");

        this.clientExecutorService = clientExecutorService;

        this.serverDefinitions = List.copyOf(serverDefinitions);
        this.primaryHostId = primaryHostId;
        this.useAliases = useAliases;
        this.rebirthDebounceDelay = rebirthDebounceDelay;

        this.bdSeqManager = bdSeqManager;

        currentBirthBdSeq = currentDeathBdSeq = bdSeqManager.getNextDeathBdSeqNum();

        LOG.trace(loggingMarker, "TahuEdgeNodeHandler constructor complete");
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        LOG.trace(loggingMarker, "Camel doInit called");

        LOG.trace(loggingMarker, "Camel doInit complete");
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        LOG.trace(loggingMarker, "Camel doStart called");

        List<String> deviceIds = descriptorMetricMap.keySet().stream()
                .filter(sd -> sd.isDeviceDescriptor())
                .map(dd -> ((DeviceDescriptor) dd).getDeviceId()).toList();

        EdgeClient edgeClient = client;
        if (edgeClient == null) {
            LOG.debug(loggingMarker, "doStart() : currentBirthBdSeq = {}  currentDeathBdSeq = {}", currentBirthBdSeq,
                    currentDeathBdSeq);

            TahuEdgeNodeClientCallback tahuClientCallback = new TahuEdgeNodeClientCallback(edgeNodeDescriptor, this);

            this.client = edgeClient = new EdgeClient(
                    this, edgeNodeDescriptor, deviceIds, primaryHostId, useAliases,
                    rebirthDebounceDelay, serverDefinitions, tahuClientCallback, null);

            tahuClientCallback.setClient(edgeClient);

            nBirthPublished = false;

        }

        edgeClientFuture = clientExecutorService.submit(edgeClient);

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
        if (edgeClient != null && edgeClient.isConnected() && !edgeClient.isDisconnectedOrDisconnecting()) {
            edgeClient.shutdown();
            edgeClientFuture.cancel(true);
        }
        client = null;

        LOG.trace(loggingMarker, "Camel doStop complete");
    }

    SparkplugBPayloadMap addDeviceMetricDataPayloadMap(
            SparkplugDescriptor metricDescriptor, SparkplugBPayloadMap metricDataTypePayloadMap) {
        LOG.trace(loggingMarker, "addDeviceMetricDataPayloadMap called: {}", metricDescriptor);

        try {
            if (nBirthPublished) {
                throw new IllegalStateException(
                        "Unable to add new metric data payload map due to handler already publishing BIRTH payloads");
            }

            return descriptorMetricMap.putIfAbsent(metricDescriptor, metricDataTypePayloadMap);
        } finally {
            LOG.trace(loggingMarker, "addDeviceMetricDataPayloadMap complete");
        }
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
                .addMetric(new MetricBuilder(
                        SparkplugMeta.SPARKPLUG_BD_SEQUENCE_NUMBER_KEY,
                        MetricDataType.Int64, currentDeathBdSeq).createMetric())
                .createPayload();

        LOG.debug(loggingMarker, "Created death payload with bdSeq metric {}", currentDeathBdSeq);

        currentBirthBdSeq = currentDeathBdSeq++;
        currentDeathBdSeq &= 0xFFL;
        bdSeqManager.storeNextDeathBdSeqNum(currentDeathBdSeq);

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
                && descriptorMetricMap.get(sparkplugDescriptor).getMetric(metricName) != null;
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
                    .addMetric(new MetricBuilder(
                            SparkplugMeta.SPARKPLUG_BD_SEQUENCE_NUMBER_KEY,
                            MetricDataType.Int64, currentBirthBdSeq).createMetric())
                    .addMetrics(getCachedMetrics(edgeNodeDescriptor, timestamp))
                    .createPayload();

            LOG.debug(loggingMarker, "Created birth payload with bdSeq metric {}", currentBirthBdSeq);

            publishNodeBirth(nBirthPayload);

            descriptorMetricMap.keySet().stream().filter(sd -> sd.isDeviceDescriptor()).forEach(sd -> {
                DeviceDescriptor deviceDescriptor = (DeviceDescriptor) sd;
                String deviceId = deviceDescriptor.getDeviceId();

                SparkplugBPayload dBirthPayload = new PayloadBuilder(deviceDescriptor)
                        .setTimestamp(timestamp)
                        .addMetrics(getCachedMetrics(deviceDescriptor, timestamp))
                        .createPayload();

                publishDeviceBirth(deviceId, dBirthPayload);
            });

            nBirthPublished = true;

        } catch (Exception e) {
            LOG.error(loggingMarker, "Exception caught publishing birth sequence", e);
            throw new TahuException(edgeNodeDescriptor, e);
        } finally {
            LOG.trace(loggingMarker, "MetricHandler publishBirthSequence complete");
        }
    }

    List<Metric> getCachedMetrics(SparkplugDescriptor metricDescriptor, Date timestamp) {
        if (!descriptorMetricMap.containsKey(metricDescriptor)) {
            return List.of();
        }

        return descriptorMetricMap.get(metricDescriptor).getMetrics().stream().map(entry -> {
            try {
                Metric metric = new Metric(entry);
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

    // void publishData(SparkplugBPayload payload) {
    //     publishData(edgeNodeDescriptor, payload);
    // }

    void publishData(SparkplugDescriptor sd, SparkplugBPayload payload) {
        if (nBirthPublished) {
            if (sd.isDeviceDescriptor()) {
                publishDeviceData(((DeviceDescriptor) sd).getDeviceId(), payload);
            } else {
                publishNodeData(payload);
            }
        } else {
            LOG.warn(loggingMarker,
                    "Attempted to publish data payload before birth payloads - cached metric values updated instead");
        }
    }

    private List<Metric> processCMDMetrics(SparkplugBPayload payload, SparkplugDescriptor cmdDescriptor) {
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
                Metric responseMetric = new Metric(descriptorMetricMap.get(cmdDescriptor).getMetric(m.getName()));

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
            SparkplugDescriptor metricDescriptor, String metricName, Object metricValue,
            Date timestamp)
            throws SparkplugInvalidTypeException {

        boolean createSpecMetric = false;
        MetricDataType metricType = null;
        switch (metricName) {
            case SparkplugMeta.SPARKPLUG_BD_SEQUENCE_NUMBER_KEY:
                metricType = MetricDataType.Int64;
                createSpecMetric = true;
                break;

            case SparkplugMeta.METRIC_NODE_REBIRTH:
                metricType = MetricDataType.Boolean;
                createSpecMetric = true;
                break;
        }

        if (createSpecMetric) {
            try {
                Metric specMetric = new Metric.MetricBuilder(metricName, metricType, metricValue).createMetric();
                descriptorMetricMap.get(metricDescriptor).addMetric(specMetric);
            } catch (SparkplugInvalidTypeException site) {
                String message = String.format(
                        "Unable to create Sparkplug B specification metric for %1$s with name %2$s and value %3$s due to an invalid value type",
                        metricDescriptor, metricName, metricValue);
                LOG.error(loggingMarker, message);
                throw new IllegalArgumentException(message, site);
            }
        }

        if (!hasMetric(metricDescriptor, metricName)) {
            String message = String.format(
                    "Unable to create metric for %1$s with name %2$s and value %3$s due to no metric configuration found",
                    metricDescriptor, metricName, metricValue);
            LOG.error(loggingMarker, message);
            throw new IllegalArgumentException(message);
        }

        Metric cachedMetric = descriptorMetricMap.get(metricDescriptor).getMetric(metricName);
        cachedMetric.setValue(metricValue);

        Metric createdMetric = new Metric(cachedMetric);
        createdMetric.setTimestamp(timestamp);

        return createdMetric;
    }

    private void ensureClientIsRunning() throws TahuException {
        EdgeClient edgeClient = client;
        if (edgeClient == null) {
            throw new TahuException(
                    edgeNodeDescriptor, "Null EdgeClient found attempting to publish payload", new IllegalStateException());
        }

        if (edgeClient.isDisconnectedOrDisconnecting()) {
            LOG.warn(loggingMarker, "Attempting operation while EdgeClient is disconnected or disconnecting");
        }
    }

    private void publishNodeBirth(SparkplugBPayloadMap nbirthPayload) {
        LOG.trace(loggingMarker, "TahuMetricHandler publishNodeBirth called: nbirthPayload {}", nbirthPayload);
        try {
            ensureClientIsRunning();
            client.publishNodeBirth(nbirthPayload);
        } catch (Exception e) {
            LOG.error(loggingMarker, "Error publishing NBIRTH message", e);
            throw new TahuException(edgeNodeDescriptor, e);
        } finally {
            LOG.trace(loggingMarker, "TahuMetricHandler publishNodeBirth complete");
        }
    }

    void publishNodeData(SparkplugBPayload ndataPayload) {
        LOG.trace(loggingMarker, "TahuMetricHandler publishNodeData called: ndataPayload {}", ndataPayload);
        try {
            ensureClientIsRunning();
            client.publishNodeData(ndataPayload);
        } catch (Exception e) {
            LOG.error(loggingMarker, "Error publishing NDATA message", e);
            throw new TahuException(edgeNodeDescriptor, e);
        } finally {
            LOG.trace(loggingMarker, "TahuMetricHandler publishNodeData complete");
        }
    }

    private void publishDeviceBirth(String deviceId, SparkplugBPayload dbirthPayload) {
        LOG.trace(loggingMarker, "TahuMetricHandler publishDeviceBirth called: deviceId {} dbirthPayload {}", deviceId,
                dbirthPayload);
        try {
            ensureClientIsRunning();
            client.publishDeviceBirth(deviceId, dbirthPayload);
        } catch (Exception e) {
            LOG.error(loggingMarker, "Error publishing DBIRTH message", e);
            throw new TahuException(edgeNodeDescriptor, e);
        } finally {
            LOG.trace(loggingMarker, "TahuMetricHandler publishDeviceBirth complete");
        }
    }

    void publishDeviceData(String deviceId, SparkplugBPayload ddataPayload) {
        LOG.trace(loggingMarker, "TahuMetricHandler publishDeviceData called: deviceId {} ddataPayload {}", deviceId,
                ddataPayload);
        try {
            ensureClientIsRunning();
            client.publishDeviceData(deviceId, ddataPayload);
        } catch (Exception e) {
            DeviceDescriptor deviceDescriptor = new DeviceDescriptor(edgeNodeDescriptor, deviceId);
            LOG.error(loggingMarker, "Error publishing DDATA message for {}", deviceDescriptor, e);
            throw new TahuException(deviceDescriptor, e);
        } finally {
            LOG.trace(loggingMarker, "TahuMetricHandler publishDeviceData complete");
        }
    }

    class PayloadBuilder {

        private final SparkplugBPayload.SparkplugBPayloadBuilder sparkplugBuilder;
        private final SparkplugDescriptor payloadDescriptor;

        private Date timestamp;

        PayloadBuilder(SparkplugDescriptor payloadDescriptor) {
            sparkplugBuilder = new SparkplugBPayload.SparkplugBPayloadBuilder();

            setTimestamp(Date.from(Instant.now()));

            this.payloadDescriptor = payloadDescriptor;
        }

        PayloadBuilder setTimestamp(Date timestamp) {
            this.timestamp = timestamp;
            sparkplugBuilder.setTimestamp(timestamp);
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

        PayloadBuilder addMetric(String metricName, Object metricValue) throws SparkplugInvalidTypeException {
            return addMetric(createMetric(payloadDescriptor, metricName, metricValue, timestamp));
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
    }

    public EdgeNodeDescriptor getEdgeNodeDescriptor() {
        return edgeNodeDescriptor;
    }

}
