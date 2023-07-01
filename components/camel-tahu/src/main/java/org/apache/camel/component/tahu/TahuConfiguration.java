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

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.eclipse.tahu.model.MqttServerDefinition;

@UriParams
public class TahuConfiguration {

    @UriPath(description = "ID of the group")
    @Metadata(required = true)
    private String groupId;

    @UriPath(description = "Name of the edge node")
    @Metadata(required = true)
    private String edgeNode;

    @UriParam(description = "IDs of the edge node devices", multiValue = true)
    private List<String> deviceIds;

    @UriParam(description = "Host ID for this Sparkplug B Host")
    private String hostId;

    @UriParam(description = "MQTT server definition list", multiValue = true)
    private List<MqttServerDefinition> serverDefinitions;

    @UriParam(description = "Flag enabling support for metric aliases", defaultValue = "false")
    private boolean useAliases;

    @UriParam(description = "Delay before node a rebirth message will be sent", defaultValue = "5000")
    private Long rebirthDebounceDelay;

    public String getGroupId() {
        return groupId;
    }

    /**
     * Sparkplug B group identifier
     *
     * @param groupId
     */
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getEdgeNode() {
        return edgeNode;
    }

    /**
     * Sparkplug B edge node name. The name must be unique within the group identifier.
     *
     * @param edgeNode
     */
    public void setEdgeNode(String edgeNode) {
        this.edgeNode = edgeNode;
    }

    public List<String> getDeviceIds() {
        return deviceIds;
    }

    public void setDeviceIds(List<String> deviceIds) {
        this.deviceIds = deviceIds;
    }

    public String getHostId() {
        return hostId;
    }

    public void setHostId(String hostId) {
        this.hostId = hostId;
    }

    public List<MqttServerDefinition> getServerDefinitions() {
        return serverDefinitions;
    }

    public void setServerDefinitions(List<MqttServerDefinition> serverDefinitions) {
        this.serverDefinitions = serverDefinitions;
    }

    public boolean isUseAliases() {
        return useAliases;
    }

    public void setUseAliases(boolean useAliases) {
        this.useAliases = useAliases;
    }

    public Long getRebirthDebounceDelay() {
        return rebirthDebounceDelay;
    }

    public void setRebirthDebounceDelay(Long rebirthDebounceDelay) {
        this.rebirthDebounceDelay = rebirthDebounceDelay;
    }

    public TahuConfiguration copy() {
        try {
            return (TahuConfiguration) clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

}
