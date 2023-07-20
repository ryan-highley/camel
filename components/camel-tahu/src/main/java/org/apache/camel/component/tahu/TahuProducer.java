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

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.util.ObjectHelper;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.tahu.SparkplugInvalidTypeException;
import org.eclipse.tahu.SparkplugParsingException;
import org.eclipse.tahu.edge.EdgeClient;
import org.eclipse.tahu.edge.api.MetricHandler;
import org.eclipse.tahu.message.PayloadDecoder;
import org.eclipse.tahu.message.SparkplugBPayloadDecoder;
import org.eclipse.tahu.message.SparkplugBPayloadEncoder;
import org.eclipse.tahu.message.model.*;
import org.eclipse.tahu.mqtt.ClientCallback;
import org.eclipse.tahu.mqtt.MqttClientId;
import org.eclipse.tahu.mqtt.MqttServerName;
import org.eclipse.tahu.mqtt.MqttServerUrl;
import org.eclipse.tahu.util.SparkplugUtil;
import org.eclipse.tahu.util.TopicUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unused")
public class TahuProducer extends DefaultAsyncProducer implements MetricHandler, ClientCallback {

    private static final Logger LOG = LoggerFactory.getLogger(TahuProducer.class);

    private final TahuEndpoint endpoint;
    private final TahuConfiguration configuration;

    private final EdgeNodeDescriptor edgeNodeDescriptor;
    private final AtomicBdSeqManager bdSeqManager;
    private final ConcurrentMap<SparkplugDescriptor, ConcurrentMap<String, MetricDataType>> descriptorMetricTypes
            = new ConcurrentHashMap<>();
    private EdgeClient client;
    private Topic NDeathTopic;
    private ExecutorService executorService;
    private long birthBdSeq;
    private long deathBdSeq;

    public TahuProducer(TahuEndpoint endpoint) {
        super(endpoint);

        this.endpoint = endpoint;

        configuration = endpoint.getConfiguration();
        edgeNodeDescriptor = endpoint.getEdgeNodeDescriptor();

        bdSeqManager = new AtomicBdSeqManager();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        LOG.trace("{}: Camel doStart called", edgeNodeDescriptor);

        NDeathTopic = new Topic(SparkplugMeta.SPARKPLUG_B_TOPIC_PREFIX, edgeNodeDescriptor, MessageType.NDEATH);

        deathBdSeq = bdSeqManager.getNextDeathBdSeqNum();
        birthBdSeq = deathBdSeq;

        if (client == null) {
            client = new EdgeClient(
                    this, edgeNodeDescriptor, new ArrayList<>(endpoint.getDeviceIdList()), endpoint.getPrimaryHostId(),
                    configuration.isUseAliases(), configuration.getRebirthDebounceDelay(),
                    new ArrayList<>(configuration.getServerDefinitionList(edgeNodeDescriptor)), this, null);
        }

        executorService = endpoint.createProducerExecutor();

        executorService.submit(client);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        LOG.trace("{}: Camel doStop called", edgeNodeDescriptor);

        if (client != null) {
            client.shutdown();
            client = null;
        }

        executorService.shutdown();
        executorService = null;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        LOG.trace("{}: Camel process called with exchange={}", edgeNodeDescriptor, exchange);

        Message message = exchange.getMessage();

        try {
            SparkplugBPayload.SparkplugBPayloadBuilder ndataPayloadBuilder
                    = new SparkplugBPayload.SparkplugBPayloadBuilder().setTimestamp(new Date(message.getMessageTimestamp()));

            Object body = message.getBody();
            if (body != null) {
                byte[] bodyBytes = exchange.getContext().getTypeConverter().mandatoryConvertTo(byte[].class, body);

                ndataPayloadBuilder.setBody(bodyBytes);
            }

            HeaderFilterStrategy headerFilterStrategy = endpoint.getHeaderFilterStrategy();

            List<Metric> messageMetrics = message.getHeaders().entrySet().stream()
                    .filter(e -> headerFilterStrategy.applyFilterToCamelHeaders(e.getKey(), e.getValue(), exchange)).map(e -> {
                        String metricName = e.getKey();
                        Object metricValue = e.getValue();

                        // If using the default headerFilterStrategy, strip off the header name prefix
                        if (metricName.startsWith(TahuConstants.METRIC_HEADER_PREFIX)) {
                            metricName = metricName.substring(TahuConstants.METRIC_HEADER_PREFIX.length());
                        }

                        Metric metric;
                        if (metricValue instanceof Metric) {
                            metric = (Metric) metricValue;
                        } else {
                            metric = createMetric(edgeNodeDescriptor, metricName, metricValue);
                        }

                        if (ObjectHelper.isEmpty(metric.getName())) {
                            metric.setName(metricName);
                        }

                        if (metric.getTimestamp() == null) {
                            metric.setTimestamp(new Date(message.getMessageTimestamp()));
                        }

                        return metric;
                    }).toList();

            if (!messageMetrics.isEmpty()) {
                ndataPayloadBuilder.addMetrics(messageMetrics);
            }

            client.publishNodeData(ndataPayloadBuilder.createPayload());

            return false;
        } catch (Exception e) {
            exchange.setException(e);
        }

        callback.done(true);
        return true;
    }

    @Override
    public Topic getDeathTopic() {
        LOG.trace("{}: MetricHandler getDeathTopic", edgeNodeDescriptor);
        return NDeathTopic;
    }

    @Override
    public byte[] getDeathPayloadBytes() throws Exception {
        LOG.trace("{}: MetricHandler getDeathPayloadBytes", edgeNodeDescriptor);
        SparkplugBPayload nDeathPayload
                = new SparkplugBPayload.SparkplugBPayloadBuilder().setTimestamp(new Date()).createPayload();
        addDeathSeqNum(nDeathPayload);
        return new SparkplugBPayloadEncoder().getBytes(nDeathPayload, true);
    }

    @Override
    public void publishBirthSequence() {
        LOG.trace("{}: MetricHandler publishBirthSequence", edgeNodeDescriptor);

        try {
            SparkplugBPayloadMap nBirthPayload
                    = new SparkplugBPayloadMap.SparkplugBPayloadMapBuilder().setTimestamp(new Date()).createPayload();

            addBirthSeqNum(nBirthPayload);

            client.publishNodeBirth(nBirthPayload);

            for (String deviceId : endpoint.getDeviceIdList()) {
                SparkplugBPayload dBirthPayload
                        = new SparkplugBPayload.SparkplugBPayloadBuilder().setTimestamp(new Date()).createPayload();

                client.publishDeviceBirth(deviceId, dBirthPayload);
            }
        } catch (Exception e) {
            LOG.error("Exception caught publishing birth sequence", e);
            throw new RuntimeCamelException(e);
        }
    }

    private void addBirthSeqNum(SparkplugBPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Birth payload should never be null when attempting to add birth seq num");
        }

        LOG.trace("Birth bdSeq(before) = {}", birthBdSeq);
        payload.addMetric(createMetric(SparkplugMeta.SPARKPLUG_BD_SEQUENCE_NUMBER_KEY, MetricDataType.Int64, birthBdSeq));

        LOG.trace("Birth bdSeq(after) = {}", birthBdSeq);
    }

    //    private void addNodeMetrics(SparkplugBPayload payload) {
    //        Map<String, MetricDataType> nodeMetricDataTypes = configuration.getDescriptorMetricDataTypeMap().get(edgeNodeDescriptor);
    //        if (nodeMetricDataTypes == null) {
    //            return;
    //        }
    //
    //        payload.addMetrics(nodeMetricDataTypes.entrySet().stream().map(e ->
    //                createMetric(e.getKey(), e.getValue(), null)).toList());
    //    }
    //
    //    private void addDeviceMetrics(String deviceId, SparkplugBPayload payload) {
    //        DeviceDescriptor deviceDescriptor = new DeviceDescriptor(edgeNodeDescriptor, deviceId);
    //        Map<String, MetricDataType> deviceMetricDataTypes = configuration.getDescriptorMetricDataTypeMap().get(deviceDescriptor);
    //        if (deviceMetricDataTypes == null) {
    //            return;
    //        }
    //
    //        payload.addMetrics(deviceMetricDataTypes.entrySet().stream().map(e ->
    //                createMetric(e.getKey(), e.getValue(), null)).toList());
    //    }

    private void addDeathSeqNum(SparkplugBPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Death payload should never be null when attempting to add death seq num");
        }

        LOG.trace("Death bdSeq(before) = {}", deathBdSeq);
        payload.addMetric(createMetric(SparkplugMeta.SPARKPLUG_BD_SEQUENCE_NUMBER_KEY, MetricDataType.Int64, deathBdSeq));

        // Increment sequence numbers in preparation for the next new connect
        birthBdSeq = deathBdSeq;
        deathBdSeq = bdSeqManager.getNextDeathBdSeqNum();

        LOG.trace("Death bdSeq(after) = {}", deathBdSeq);
    }

    private Metric createMetric(final SparkplugDescriptor descriptor, final String key, final Object value) {
        ConcurrentMap<String, MetricDataType> metricTypes
                = descriptorMetricTypes.computeIfAbsent(descriptor, d -> new ConcurrentHashMap<String, MetricDataType>());

        MetricDataType metricType = metricTypes.computeIfAbsent(key, k -> guessMetricDataType(value));

        return createMetric(key, metricType, value);
    }

    private Metric createMetric(String key, MetricDataType type, Object value) {
        try {
            return new Metric.MetricBuilder(key, type, value).createMetric();
        } catch (SparkplugInvalidTypeException e) {
            LOG.error("Failed to create payload metric for key {} with type {} and value {}", key, type, value);
            throw new RuntimeCamelException(e);
        }
    }

    private MetricDataType guessMetricDataType(final Object value) {
        Class<?> valueClass = value.getClass();

        MetricDataType guessedType = MetricDataType.Unknown;

        if (String.class.isAssignableFrom(valueClass)) {
            guessedType = MetricDataType.String;
        } else if (Byte.class.isAssignableFrom(valueClass)) {
            guessedType = MetricDataType.Int8;
        } else if (Short.class.isAssignableFrom(valueClass)) {
            guessedType = MetricDataType.Int16;
        } else if (Integer.class.isAssignableFrom(valueClass)) {
            guessedType = MetricDataType.Int32;
        } else if (Long.class.isAssignableFrom(valueClass)) {
            guessedType = MetricDataType.Int64;
        } else if (Float.class.isAssignableFrom(valueClass)) {
            guessedType = MetricDataType.Float;
        } else if (Double.class.isAssignableFrom(valueClass)) {
            guessedType = MetricDataType.Double;
        } else if (Date.class.isAssignableFrom(valueClass)) {
            guessedType = MetricDataType.DateTime;
        } else if (UUID.class.isAssignableFrom(valueClass)) {
            guessedType = MetricDataType.UUID;
        } else if (byte[].class.isAssignableFrom(valueClass)) {
            guessedType = MetricDataType.Bytes;
        } else if (InputStream.class.isAssignableFrom(valueClass)) {
            guessedType = MetricDataType.File;
        }

        return guessedType;
    }

    @Override
    public boolean hasMetric(SparkplugDescriptor sparkplugDescriptor, String metricName) {
        LOG.trace("{}: MetricHandler hasMetric for sparkplugDescriptor={} metricName={}", edgeNodeDescriptor,
                sparkplugDescriptor, metricName);
        return false;
        //        Map<SparkplugDescriptor, Map<String, MetricDataType>> descriptorMetricDataTypeMap = configuration.getDescriptorMetricDataTypeMap();
        //
        //        return descriptorMetricDataTypeMap.containsKey(sparkplugDescriptor)
        //                && descriptorMetricDataTypeMap.get(sparkplugDescriptor).containsKey(metricName);
    }

    @Override
    public void shutdown() {

    }

    @Override
    public void messageArrived(
            MqttServerName mqttServerName, MqttServerUrl mqttServerUrl, MqttClientId mqttClientId, String rawTopic,
            MqttMessage mqttMessage) {
        LOG.trace("{}: ClientCallback messageArrived on topic={}", mqttClientId, rawTopic);

        final Topic topic;
        try {
            topic = TopicUtil.parseTopic(rawTopic);
        } catch (SparkplugParsingException e) {
            LOG.error("Error parsing Sparkplug topic {}", rawTopic, e);
            throw new RuntimeCamelException(e);
        }

        if (!SparkplugMeta.SPARKPLUG_B_TOPIC_PREFIX.equals(topic.getNamespace())) {
            LOG.warn("Received message on non-Sparkplug topic: {}", topic);
            return;
        }

        if (topic.isType(MessageType.STATE)) {
            LOG.debug("Received STATE message: {} :: {}", topic, new String(mqttMessage.getPayload()));

            try {
                ObjectMapper mapper = new ObjectMapper();
                StatePayload statePayload = mapper.readValue(mqttMessage.getPayload(), StatePayload.class);
                client.handleStateMessage(topic.getHostApplicationId(), statePayload);
            } catch (Exception e) {
                LOG.error("Error handling STATE message with topic {} and payload {}", topic,
                        new String(mqttMessage.getPayload()));
                throw new RuntimeCamelException(e);
            }
            return;
        }

        final SparkplugBPayload payload;

        if (topic.isType(MessageType.NDEATH) && topic.getEdgeNodeDescriptor().equals(edgeNodeDescriptor)) {
            if (!client.isDisconnectedOrDisconnecting()) {
                // MQTT Server published our LWT message before client finished disconnecting

                if (!client.isConnectedToPrimaryHost()) {
                    LOG.debug("Received unexpected LWT for {} but not connected to primary host - ignoring",
                            edgeNodeDescriptor);
                    return;
                }

                // Find payload's bdSeq to determine how to proceed
                long messageBdSeq;
                try {
                    payload = new SparkplugBPayloadDecoder().buildFromByteArray(mqttMessage.getPayload(), null);
                    messageBdSeq = SparkplugUtil.getBdSequenceNumber(payload);
                } catch (Exception e) {
                    LOG.error("Error handling DEATH message while connected to primary host on topic {}", topic, e);
                    throw new RuntimeCamelException(e);
                }

                if (birthBdSeq == messageBdSeq) {
                    // This is our latest LWT - treat as a rebirth
                    LOG.warn("Received unexpected LWT for {} - publishing BIRTH sequence", edgeNodeDescriptor);
                    try {
                        client.handleRebirthRequest(true);
                    } catch (Exception e) {
                        LOG.warn("Received unexpected LWT but failed to publish new BIRTH sequence for {}", edgeNodeDescriptor);
                        throw new RuntimeCamelException(e);
                    }
                } else {
                    LOG.warn("Received unexpected LWT for {} with different bdSeq - expected {} received {} - ignoring",
                            edgeNodeDescriptor, birthBdSeq, messageBdSeq);
                }
            } else {
                LOG.debug("Received expected LWT for {} - no action required", topic.getEdgeNodeDescriptor());
            }
            return;
        }

        if (!topic.isType(MessageType.NCMD) && !topic.isType(MessageType.DCMD)) {
            LOG.debug("Received unexpected Sparkplug message of type {} - ignoring", topic.getType());
            return;
        }

        try {
            LOG.debug("Decoding Sparkplug payload");
            PayloadDecoder<SparkplugBPayload> decoder = new SparkplugBPayloadDecoder();
            payload = decoder.buildFromByteArray(mqttMessage.getPayload(), null);
            LOG.debug("Message Timestamp: {}", payload.getTimestamp());
        } catch (Exception e) {
            LOG.error("Error decoding Sparkplug message with topic {} and payload {}", topic,
                    new String(mqttMessage.getPayload()));
            return;
        }

        if (topic.isType(MessageType.NCMD)) {
            handleNCMDMessage(payload);
        } else if (topic.isType(MessageType.DCMD)) {
            handleDCMDMessage(payload, topic.getDeviceId());
        }
    }

    private List<Metric> handleCMDMetrics(SparkplugBPayload payload) {
        final List<Metric> receivedMetrics = payload.getMetrics();
        if (receivedMetrics == null || receivedMetrics.isEmpty()) {
            return List.of();
        }

        Map<Boolean, List<Metric>> groupedMetrics = receivedMetrics.stream()
                .collect(Collectors.groupingBy(m -> SparkplugMeta.METRIC_NODE_REBIRTH.equals(m.getName())
                        && m.getDataType() == MetricDataType.Boolean && (Boolean) m.getValue()));

        if (groupedMetrics.containsKey(true)) {
            client.handleRebirthRequest(true);
        }

        List<Metric> responseMetrics = groupedMetrics.get(false).stream().map(m -> {
            try {
                Metric responseMetric = new Metric(m);

                responseMetric.setHistorical(true);

                return responseMetric;
            } catch (SparkplugInvalidTypeException e) {
                LOG.warn("Error copying Metric for response: {} - skipping", m);
                return null;
            }
        }).filter(Objects::nonNull).toList();

        // Add the metrics
        for (Metric metric : receivedMetrics) {
            String name = metric.getName();

            LOG.debug("Node Metric Name: {}", name);
            Object value = metric.getValue();
            LOG.debug("Metric: {} :: {} :: {}", name, value, metric.getDataType());
            if (SparkplugMeta.METRIC_NODE_REBIRTH.equals(name) && value.equals(true)) {
                client.handleRebirthRequest(true);
            } else {
                Metric writtenMetric = null; // dataSimulator.handleMetricWrite(edgeNodeDescriptor, metric);
                if (writtenMetric != null) {
                    responseMetrics.add(writtenMetric);
                }
            }
        }
        return responseMetrics;
    }

    private SparkplugBPayload generateDATAPayload(SparkplugBPayload cmdPayload) {
        final List<Metric> responseMetrics = handleCMDMetrics(cmdPayload);

        if (responseMetrics.isEmpty()) {
            return null;
        }

        SparkplugBPayloadMap.SparkplugBPayloadMapBuilder payloadBuilder
                = new SparkplugBPayloadMap.SparkplugBPayloadMapBuilder();
        payloadBuilder.setTimestamp(new Date());

        payloadBuilder.addMetrics(responseMetrics);

        return payloadBuilder.createPayload();
    }

    private void handleNCMDMessage(SparkplugBPayload ncmdPayload) {
        SparkplugBPayload ndataPayload = generateDATAPayload(ncmdPayload);

        if (ndataPayload != null) {
            // Publish the response NDATA message
            LOG.debug("Publishing NDATA based on NCMD message for {}", edgeNodeDescriptor);

            try {
                client.publishNodeData(ndataPayload);
            } catch (Exception e) {
                LOG.error("Error publishing NDATA from NCMD message for {}", edgeNodeDescriptor, e);
                throw new RuntimeCamelException(e);
            }
        } else {
            LOG.warn("Received NCMD with no valid metrics to write for {}", edgeNodeDescriptor);
        }
    }

    private void handleDCMDMessage(SparkplugBPayload dcmdPayload, String deviceId) {
        SparkplugBPayload ddataPayload = generateDATAPayload(dcmdPayload);

        if (ddataPayload != null) {
            // Publish the response DDATA message
            LOG.debug("Publishing DDATA based on DCMD message for {}/{}", edgeNodeDescriptor, deviceId);

            try {
                client.publishDeviceData(deviceId, ddataPayload);
            } catch (Exception e) {
                LOG.error("Error publishing DDATA from DCMD message for {}", edgeNodeDescriptor + "/" + deviceId, e);
                throw new RuntimeCamelException(e);
            }
        } else {
            LOG.warn("Received DCMD with no valid metrics to write for {}/{}", edgeNodeDescriptor, deviceId);
        }
    }

    @Override
    public void connectionLost(
            MqttServerName mqttServerName, MqttServerUrl mqttServerUrl, MqttClientId mqttClientId, Throwable throwable) {
        LOG.trace("{}: ClientCallback connectionLost", mqttClientId);
    }

    @Override
    public void connectComplete(
            boolean reconnect, MqttServerName mqttServerName, MqttServerUrl mqttServerUrl, MqttClientId mqttClientId) {
        LOG.trace("{}: ClientCallback connectComplete", mqttClientId);
    }
}
