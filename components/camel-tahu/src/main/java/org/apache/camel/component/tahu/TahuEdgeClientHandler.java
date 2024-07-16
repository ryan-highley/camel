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

import java.util.List;
import java.util.concurrent.ExecutorService;

import org.apache.camel.support.service.ServiceSupport;
import org.eclipse.tahu.message.BdSeqManager;
import org.eclipse.tahu.message.model.EdgeNodeDescriptor;
import org.eclipse.tahu.message.model.SparkplugBPayload;
import org.eclipse.tahu.message.model.SparkplugBPayloadMap;
import org.eclipse.tahu.message.model.SparkplugDescriptor;
import org.eclipse.tahu.model.MqttServerDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public class TahuEdgeClientHandler extends ServiceSupport {

    private static final Logger LOG = LoggerFactory.getLogger(TahuEdgeClientHandler.class);

    private final TahuEdgeClient.ClientBuilder clientFactory;
    private volatile TahuEdgeClient client;

    private final EdgeNodeDescriptor edgeNodeDescriptor;

    private final Marker loggingMarker;

    TahuEdgeClientHandler(EdgeNodeDescriptor edgeNodeDescriptor, List<MqttServerDefinition> serverDefinitions,
                          String primaryHostId, List<String> deviceIds, boolean useAliases, long rebirthDebounceDelay,
                          ExecutorService clientExecutorService, BdSeqManager bdSeqManager) {

        this.edgeNodeDescriptor = edgeNodeDescriptor;

        loggingMarker = MarkerFactory.getMarker(edgeNodeDescriptor.getDescriptorString());

        LOG.trace(loggingMarker, "TahuEdgeClientHandler constructor called");

        clientFactory = new TahuEdgeClient.ClientBuilder()
                .edgeNodeDescriptor(edgeNodeDescriptor)
                .deviceIds(deviceIds)
                .primaryHostId(primaryHostId)
                .useAliases(useAliases)
                .rebirthDebounceDelay(rebirthDebounceDelay)
                .serverDefinitions(serverDefinitions)
                .bdSeqManager(bdSeqManager)
                .clientExecutorService(clientExecutorService);

        LOG.trace(loggingMarker, "TahuEdgeClientHandler constructor complete");
    }

    @Override
    protected void doInit() throws Exception {
        LOG.trace(loggingMarker, "Camel doInit called");

        try {

            client = clientFactory.build();

        } finally {
            LOG.trace(loggingMarker, "Camel doInit complete");
        }
    }

    @Override
    protected void doStart() throws Exception {
        LOG.trace(loggingMarker, "Camel doStart called");

        try {

            client.startup();

        } finally {
            LOG.trace(loggingMarker, "Camel doStart complete");
        }
    }

    @Override
    protected void doSuspend() throws Exception {
        LOG.trace(loggingMarker, "Camel doSuspend called");

        try {

            client.disconnect(true);

        } finally {
            LOG.trace(loggingMarker, "Camel doSuspend complete");
        }
    }

    @Override
    protected void doResume() throws Exception {
        LOG.trace(loggingMarker, "Camel doResume called");

        try {

            client.handleRebirthRequest(true);

        } finally {
            LOG.trace(loggingMarker, "Camel doResume complete");
        }
    }

    @Override
    protected void doStop() throws Exception {
        LOG.trace(loggingMarker, "Camel doStop called");

        try {

            client.shutdown();

        } finally {
            LOG.trace(loggingMarker, "Camel doStop complete");
        }
    }

    boolean isConnected() {
        return client.isConnected();
    }

    boolean isConnectedToPrimaryHost() {
        return client.isConnectedToPrimaryHost();
    }

    SparkplugBPayloadMap addDeviceMetricDataPayloadMap(
            SparkplugDescriptor metricDescriptor, SparkplugBPayloadMap metricDataTypePayloadMap) {
        return client.addDeviceMetricDataPayloadMap(metricDescriptor, metricDataTypePayloadMap);
    }

    void publishData(SparkplugDescriptor sd, SparkplugBPayload payload) {
        client.publishData(sd, payload);
    }

    public EdgeNodeDescriptor getEdgeNodeDescriptor() {
        return edgeNodeDescriptor;
    }

}
