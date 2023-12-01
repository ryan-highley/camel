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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.RuntimeCamelException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.tahu.SparkplugParsingException;
import org.eclipse.tahu.edge.EdgeClient;
import org.eclipse.tahu.message.PayloadDecoder;
import org.eclipse.tahu.message.SparkplugBPayloadDecoder;
import org.eclipse.tahu.message.model.EdgeNodeDescriptor;
import org.eclipse.tahu.message.model.MessageType;
import org.eclipse.tahu.message.model.SparkplugBPayload;
import org.eclipse.tahu.message.model.SparkplugMeta;
import org.eclipse.tahu.message.model.StatePayload;
import org.eclipse.tahu.message.model.Topic;
import org.eclipse.tahu.mqtt.ClientCallback;
import org.eclipse.tahu.mqtt.MqttClientId;
import org.eclipse.tahu.mqtt.MqttServerName;
import org.eclipse.tahu.mqtt.MqttServerUrl;
import org.eclipse.tahu.util.SparkplugUtil;
import org.eclipse.tahu.util.TopicUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

class TahuEdgeNodeClientCallback implements ClientCallback {

    private static final Logger LOG = LoggerFactory.getLogger(TahuEdgeNodeClientCallback.class);

    private EdgeClient client;

    private final EdgeNodeDescriptor edgeNodeDescriptor;
    private final TahuEdgeNodeHandler tahuEdgeNodeHandler;

    private final Marker loggingMarker;

    TahuEdgeNodeClientCallback(EdgeNodeDescriptor edgeNodeDescriptor, TahuEdgeNodeHandler tahuEdgeNodeHandler) {
        this.edgeNodeDescriptor = edgeNodeDescriptor;
        this.tahuEdgeNodeHandler = tahuEdgeNodeHandler;

        loggingMarker = MarkerFactory.getMarker(edgeNodeDescriptor.getDescriptorString());
    }

    void setClient(EdgeClient client) {
        this.client = client;
    }

    @Override
    public void messageArrived(
            MqttServerName mqttServerName, MqttServerUrl mqttServerUrl, MqttClientId mqttClientId, String rawTopic,
            MqttMessage mqttMessage) {
        LOG.trace(loggingMarker, "{}: ClientCallback messageArrived called: topic {}", mqttClientId, rawTopic);

        try {

            final Topic topic;
            try {
                topic = TopicUtil.parseTopic(rawTopic);
            } catch (SparkplugParsingException e) {
                LOG.error(loggingMarker, "Exception caught parsing Sparkplug topic {}", rawTopic, e);
                throw new RuntimeCamelException(e);
            }

            if (!SparkplugMeta.SPARKPLUG_B_TOPIC_PREFIX.equals(topic.getNamespace())) {
                LOG.warn(loggingMarker, "Received message on non-Sparkplug topic: {}", topic);
                return;
            }

            if (topic.isType(MessageType.STATE)) {
                LOG.debug(loggingMarker, "Received STATE message: {} :: {}", topic, new String(mqttMessage.getPayload()));

                try {
                    ObjectMapper mapper = new ObjectMapper();
                    StatePayload statePayload = mapper.readValue(mqttMessage.getPayload(), StatePayload.class);
                    client.handleStateMessage(topic.getHostApplicationId(), statePayload);
                } catch (Exception e) {
                    LOG.error(loggingMarker, "Exception caught handling STATE message with topic {} and payload {}", topic,
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
                        LOG.debug(loggingMarker,
                                "Received unexpected LWT for {} but not connected to primary host - ignoring",
                                edgeNodeDescriptor);
                        return;
                    }

                    // Find payload's bdSeq to determine how to proceed
                    long messageBdSeq;
                    try {
                        payload = new SparkplugBPayloadDecoder().buildFromByteArray(mqttMessage.getPayload(), null);
                        messageBdSeq = SparkplugUtil.getBdSequenceNumber(payload);
                    } catch (Exception e) {
                        LOG.error(loggingMarker,
                                "Exception caught handling DEATH message while connected to primary host on topic {}",
                                topic, e);
                        throw new RuntimeCamelException(e);
                    }

                    long currentBirthBdSeq = tahuEdgeNodeHandler.getCurrentBirthBdSeq();

                    if (currentBirthBdSeq == messageBdSeq) {
                        // This is our latest LWT - treat as a rebirth
                        LOG.warn(loggingMarker, "Received unexpected LWT for {} - publishing BIRTH sequence",
                                edgeNodeDescriptor);
                        try {
                            client.handleRebirthRequest(true);
                        } catch (Exception e) {
                            LOG.warn(loggingMarker,
                                    "Received unexpected LWT but failed to publish new BIRTH sequence for {}",
                                    edgeNodeDescriptor);
                            throw new RuntimeCamelException(e);
                        }
                    } else {
                        LOG.warn(loggingMarker,
                                "Received unexpected LWT for {} with different bdSeq - expected {} received {} - ignoring",
                                edgeNodeDescriptor, currentBirthBdSeq, messageBdSeq);
                    }
                } else {
                    LOG.debug(loggingMarker, "Received expected LWT for {} - no action required",
                            topic.getEdgeNodeDescriptor());
                }
                return;
            }

            if (!topic.isType(MessageType.NCMD) && !topic.isType(MessageType.DCMD)) {
                LOG.debug(loggingMarker, "Received unexpected Sparkplug message of type {} - ignoring", topic.getType());
                return;
            }

            try {
                PayloadDecoder<SparkplugBPayload> decoder = new SparkplugBPayloadDecoder();
                payload = decoder.buildFromByteArray(mqttMessage.getPayload(), null);

                if (topic.isType(MessageType.NCMD)) {
                    tahuEdgeNodeHandler.handleNCMDMessage(payload);
                } else if (topic.isType(MessageType.DCMD)) {
                    tahuEdgeNodeHandler.handleDCMDMessage(payload, topic.getDeviceId());
                }
            } catch (Exception e) {
                LOG.error(loggingMarker, "Exception caught decoding Sparkplug message with topic {} and payload {}", topic,
                        new String(mqttMessage.getPayload()));
                throw new RuntimeCamelException(e);
            }
        } finally {
            LOG.trace(loggingMarker, "{}: ClientCallback messageArrived complete", mqttClientId);
        }
    }

    @Override
    public void shutdown() {
        LOG.trace(loggingMarker, "ClientCallback shutdown called");

        LOG.trace(loggingMarker, "ClientCallback shutdown complete");
    }

    @Override
    public void connectionLost(
            MqttServerName mqttServerName, MqttServerUrl mqttServerUrl, MqttClientId mqttClientId,
            Throwable throwable) {
        LOG.trace(loggingMarker, "{}: ClientCallback connectionLost", mqttClientId);
    }

    @Override
    public void connectComplete(
            boolean reconnect, MqttServerName mqttServerName, MqttServerUrl mqttServerUrl, MqttClientId mqttClientId) {
        LOG.trace(loggingMarker, "{}: ClientCallback connectComplete", mqttClientId);
    }

}
