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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.eclipse.tahu.edge.EdgeClient;
import org.eclipse.tahu.message.BdSeqManager;
import org.eclipse.tahu.message.model.EdgeNodeDescriptor;
import org.eclipse.tahu.message.model.MessageType;
import org.eclipse.tahu.message.model.SparkplugMeta;
import org.eclipse.tahu.message.model.Topic;
import org.eclipse.tahu.model.MqttServerDefinition;

/**
 * Sparkplug B component which does bla bla.
 *
 * TODO: Update one line description above what the component does.
 */
@UriEndpoint(firstVersion = "4.0.0-SNAPSHOT", scheme = "sparkplug", title = "Sparkplug B", syntax = "sparkplug:groupId:edgeNode", category = {
        Category.MESSAGING, Category.IOT, Category.MONITORING }, headersClass = TahuConstants.class)
public class TahuEndpoint extends DefaultEndpoint {

    @UriPath(description = "ID of the group")
    @Metadata(required = true)
    private final String groupId;

    @UriPath(description = "Name of the edge node")
    @Metadata(required = true)
    private final String edgeNode;

    @UriParam
    private final TahuConfiguration configuration;

    @UriParam(label = "advanced")
    private volatile EdgeClient client;

    private final EdgeNodeDescriptor edgeNodeDescriptor;
    private final Topic NDeathTopic;
    private final List<MqttServerDefinition> mqttServerDefinitions = new ArrayList<>();
    private final BdSeqManager bdSeqManager;

    public TahuEndpoint(String uri, String remaining, TahuComponent component,
            TahuConfiguration configuration) {
        this(uri, configuration.getGroupId(), configuration.getEdgeNode(), component,
                configuration);
    }

    public TahuEndpoint(String uri, String groupId, String edgeNode, TahuComponent component,
            TahuConfiguration configuration) {
        super(uri, component);
        this.groupId = groupId;
        this.edgeNode = edgeNode;
        this.configuration = configuration;

        edgeNodeDescriptor = new EdgeNodeDescriptor(groupId, edgeNode);
        NDeathTopic = new Topic(SparkplugMeta.SPARKPLUG_B_TOPIC_PREFIX, edgeNodeDescriptor, MessageType.NDEATH);

        mqttServerDefinitions.addAll(configuration.getServerDefinitions());
        bdSeqManager = new AtomicBdSeqManager();
    }

    private static final class AtomicBdSeqManager implements BdSeqManager {

        private final AtomicLong bdSeqNum = new AtomicLong(0);

        @Override
        public long getNextDeathBdSeqNum() {
            // Returns 0-255 by truncating all bits above lowest 8
            return bdSeqNum.getAndIncrement() & 0xFFL;
        }

        @Override
        public void storeNextDeathBdSeqNum(long nextBdSeqNum) {
            bdSeqNum.set(nextBdSeqNum);
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (client == null) {
            client = new EdgeClient(this, edgeNodeDescriptor, configuration.getDeviceIds(), configuration.getHostId(),
                    configuration.isUseAliases(), configuration.getRebirthDebounceDelay(), mqttServerDefinitions, this,
                    null);
        }
    }

    public Producer createProducer() throws Exception {
        TahuProducer producer = new TahuProducer(this);
        producer.setClient(client);
        return producer;
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        TahuConsumer consumer = new TahuConsumer(this, processor);
        consumer.setClient(client);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public TahuComponent getComponent() {
        return (TahuComponent) super.getComponent();
    }

    public String getGroupId() {
        return groupId;
    }

    public String getEdgeNode() {
        return edgeNode;
    }

    public TahuConfiguration getConfiguration() {
        return configuration;
    }

    public EdgeClient getClient() {
        return client;
    }

    /**
     * To use an existing Tahu edge node client
     */
    public void setClient(EdgeClient client) {
        this.client = client;
    }
}
