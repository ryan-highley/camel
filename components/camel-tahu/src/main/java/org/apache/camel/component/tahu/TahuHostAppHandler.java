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
import java.util.function.BiConsumer;

import org.apache.camel.support.service.ServiceSupport;
import org.eclipse.tahu.host.HostApplication;
import org.eclipse.tahu.host.api.HostApplicationEventHandler;
import org.eclipse.tahu.message.SparkplugBPayloadDecoder;
import org.eclipse.tahu.message.model.DeviceDescriptor;
import org.eclipse.tahu.message.model.EdgeNodeDescriptor;
import org.eclipse.tahu.message.model.Message;
import org.eclipse.tahu.message.model.Metric;
import org.eclipse.tahu.message.model.SparkplugDescriptor;
import org.eclipse.tahu.model.MqttServerDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public class TahuHostAppHandler extends ServiceSupport implements HostApplicationEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(TahuHostAppHandler.class);

    private volatile HostApplication hostApplication;

    private final String hostId;

    private final Marker loggingMarker;

    private final List<MqttServerDefinition> serverDefinitions;
    private final BiConsumer<EdgeNodeDescriptor, Message> onMessageConsumer;
    private final BiConsumer<EdgeNodeDescriptor, Metric> onMetricConsumer;

    TahuHostAppHandler(String hostId, List<MqttServerDefinition> serverDefinitions,
                       BiConsumer<EdgeNodeDescriptor, Message> onMessageConsumer,
                       BiConsumer<EdgeNodeDescriptor, Metric> onMetricConsumer) {
        this.hostId = hostId;

        loggingMarker = MarkerFactory.getMarker(hostId);

        LOG.trace(loggingMarker, "TahuHostAppHandler constructor called");

        this.serverDefinitions = List.copyOf(serverDefinitions);

        this.onMessageConsumer = onMessageConsumer;
        this.onMetricConsumer = onMetricConsumer;

        LOG.trace(loggingMarker, "TahuHostAppHandler constructor complete");
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        LOG.trace(loggingMarker, "Camel doStart called");

        HostApplication app = hostApplication;
        if (app == null) {

            this.hostApplication = app = new HostApplication(
                    this, hostId, null, serverDefinitions, null,
                    new SparkplugBPayloadDecoder());
            app.start();
        }

        LOG.trace(loggingMarker, "Camel doStart complete");
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        LOG.trace(loggingMarker, "Camel doStop called");

        if (hostApplication != null) {
            hostApplication.shutdown();
            hostApplication = null;
        }

        LOG.trace(loggingMarker, "Camel doStop complete");
    }

    @Override
    public void onNodeBirthArrived(EdgeNodeDescriptor edgeNodeDescriptor, Message message) {
        LOG.trace(loggingMarker, "HostApplicationEventHandler onNodeBirthArrived from {}...", edgeNodeDescriptor);
        onMessageConsumer.accept(edgeNodeDescriptor, message);
    }

    @Override
    public void onNodeBirthComplete(EdgeNodeDescriptor edgeNodeDescriptor) {
        LOG.trace(loggingMarker, "HostApplicationEventHandler onNodeBirthComplete from {}...", edgeNodeDescriptor);
    }

    @Override
    public void onNodeDataArrived(EdgeNodeDescriptor edgeNodeDescriptor, Message message) {
        LOG.trace(loggingMarker, "HostApplicationEventHandler onNodeDataArrived from {}...", edgeNodeDescriptor);
        onMessageConsumer.accept(edgeNodeDescriptor, message);
    }

    @Override
    public void onNodeDataComplete(EdgeNodeDescriptor edgeNodeDescriptor) {
        LOG.trace(loggingMarker, "HostApplicationEventHandler onNodeDataComplete from {}...", edgeNodeDescriptor);
    }

    @Override
    public void onNodeDeath(EdgeNodeDescriptor edgeNodeDescriptor, Message message) {
        LOG.trace(loggingMarker, "HostApplicationEventHandler onNodeDeath from {}...", edgeNodeDescriptor);
        onMessageConsumer.accept(edgeNodeDescriptor, message);
    }

    @Override
    public void onNodeDeathComplete(EdgeNodeDescriptor edgeNodeDescriptor) {
        LOG.trace(loggingMarker, "HostApplicationEventHandler onNodeDeathComplete from {}...", edgeNodeDescriptor);
    }

    @Override
    public void onDeviceBirthArrived(DeviceDescriptor deviceDescriptor, Message message) {
        LOG.trace(loggingMarker, "HostApplicationEventHandler onDeviceBirthArrived from {}...", deviceDescriptor);
        onMessageConsumer.accept(deviceDescriptor, message);
    }

    @Override
    public void onDeviceBirthComplete(DeviceDescriptor deviceDescriptor) {
        LOG.trace(loggingMarker, "HostApplicationEventHandler onDeviceBirthComplete from {}...", deviceDescriptor);
    }

    @Override
    public void onDeviceDataArrived(DeviceDescriptor deviceDescriptor, Message message) {
        LOG.trace(loggingMarker, "HostApplicationEventHandler onDeviceDataArrived from {}...", deviceDescriptor);
        onMessageConsumer.accept(deviceDescriptor, message);
    }

    @Override
    public void onDeviceDataComplete(DeviceDescriptor deviceDescriptor) {
        LOG.trace(loggingMarker, "HostApplicationEventHandler onDeviceDataComplete from {}...", deviceDescriptor);
    }

    @Override
    public void onDeviceDeath(DeviceDescriptor deviceDescriptor, Message message) {
        LOG.trace(loggingMarker, "HostApplicationEventHandler onDeviceDeath from {}...", deviceDescriptor);
        onMessageConsumer.accept(deviceDescriptor, message);
    }

    @Override
    public void onDeviceDeathComplete(DeviceDescriptor deviceDescriptor) {
        LOG.trace(loggingMarker, "HostApplicationEventHandler onDeviceDeathComplete from {}...", deviceDescriptor);
    }

    @Override
    public void onBirthMetric(SparkplugDescriptor sparkplugDescriptor, Metric metric) {
        LOG.trace(loggingMarker, "HostApplicationEventHandler onBirthMetric from {} with metric={}...", sparkplugDescriptor,
                metric);
        acceptMetric(sparkplugDescriptor, metric);
    }

    @Override
    public void onDataMetric(SparkplugDescriptor sparkplugDescriptor, Metric metric) {
        LOG.trace(loggingMarker, "HostApplicationEventHandler onDataMetric from {} with metric={}...", sparkplugDescriptor,
                metric);
        acceptMetric(sparkplugDescriptor, metric);
    }

    @Override
    public void onStale(SparkplugDescriptor sparkplugDescriptor, Metric metric) {
        LOG.trace(loggingMarker, "HostApplicationEventHandler onStale from {} for {}...", sparkplugDescriptor,
                metric.getName());
        acceptMetric(sparkplugDescriptor, metric);
    }

    @Override
    public void onMessage(SparkplugDescriptor sparkplugDescriptor, Message message) {
        LOG.trace(loggingMarker, "HostApplicationEventHandler onMessage from {} with message={}...", sparkplugDescriptor,
                message);
        if (sparkplugDescriptor.isDeviceDescriptor()) {
            onMessageConsumer.accept((DeviceDescriptor) sparkplugDescriptor, message);
        } else {
            onMessageConsumer.accept((EdgeNodeDescriptor) sparkplugDescriptor, message);
        }
    }

    private void acceptMetric(SparkplugDescriptor sparkplugDescriptor, Metric metric) {
        if (sparkplugDescriptor.isDeviceDescriptor()) {
            onMetricConsumer.accept((DeviceDescriptor) sparkplugDescriptor, metric);
        } else {
            onMetricConsumer.accept((EdgeNodeDescriptor) sparkplugDescriptor, metric);
        }
    }

    @Override
    public void onConnect() {
        LOG.trace(loggingMarker, "HostApplicationEventHandler onConnect...");
    }

    @Override
    public void onDisconnect() {
        LOG.trace(loggingMarker, "HostApplicationEventHandler onDisconnect...");
    }

}
