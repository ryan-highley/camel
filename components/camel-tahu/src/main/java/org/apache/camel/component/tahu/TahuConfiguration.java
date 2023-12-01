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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.util.ObjectHelper;
import org.eclipse.tahu.model.MqttServerDefinition;
import org.eclipse.tahu.mqtt.MqttClientId;
import org.eclipse.tahu.mqtt.MqttServerName;
import org.eclipse.tahu.mqtt.MqttServerUrl;

@UriParams
public class TahuConfiguration implements Cloneable {

    private static final Pattern SERVER_DEF_PATTERN = Pattern
            .compile("([^:]+):(?:(?!tcp|ssl)([^:]+):)?((?:tcp|ssl):(?://)?[\\p{Alnum}.-]+(?::\\d+)?),?");

    @UriParam(label = "common",
              description = "MQTT server definition list, given with the following syntax in a comma-separated list: MqttServerName:[MqttClientId:](tcp|ssl)://hostname[:port],...")
    private String servers;

    @UriParam(label = "common",
              description = "MQTT client ID to use for all server definitions, rather than specifying the same one for each",
              defaultValueNote = "If neither the clientId parameter nor an MqttClientId defined for an MQTT Server, a random MQTT Client ID will be generated, prefaced with \"Camel\"")
    private String clientId;

    @UriParam(label = "common",
              description = "MQTT client ID length check enabled", defaultValue = "true")
    private boolean checkClientIdLength = true;

    @UriParam(label = "security", description = "Username for MQTT server authentication", secret = true)
    private String username;

    @UriParam(label = "security", description = "Password for MQTT server authentication", secret = true)
    private String password;

    @UriParam(label = "common", description = "Delay before node a rebirth message will be sent", defaultValue = "5000")
    private long rebirthDebounceDelay = 5000L;

    @UriParam(label = "common", description = "Connection keep alive timeout in seconds", defaultValue = "30")
    private int keepAliveTimeout = 30;

    @UriParam(label = "security", description = "SSL configuration for MQTT server connections")
    private SSLContextParameters sslContextParameters;

    public String getServers() {
        return servers;
    }

    public void setServers(String servers) {
        this.servers = servers;
    }

    public List<MqttServerDefinition> getServerDefinitionList() {
        List<MqttServerDefinition> serverDefinitionList;
        if (ObjectHelper.isEmpty(servers)) {
            serverDefinitionList = List.of();
        } else if (!SERVER_DEF_PATTERN.matcher(servers).find()) {
            throw new RuntimeCamelException("Server definition list has invalid syntax: " + servers);
        } else {
            Matcher serverDefMatcher = SERVER_DEF_PATTERN.matcher(servers);
            serverDefinitionList = serverDefMatcher.results().map(matchResult -> {

                // MatchResult does not support named groups
                String serverName = matchResult.group(1);
                String clientId = matchResult.group(2);
                String serverUrl = matchResult.group(3);

                return parseFromUrlString(serverName, clientId, serverUrl);
            }).toList();
        }
        return serverDefinitionList;
    }

    private MqttServerDefinition parseFromUrlString(
            String serverName, String clientId, String serverUrl) {
        if (ObjectHelper.isEmpty(serverName) || ObjectHelper.isEmpty(serverUrl)) {
            throw new RuntimeCamelException(
                    "Both serverName and serverUrl must be specified in MQTT server definitions");
        }

        try {
            MqttServerName mqttServerName = new MqttServerName(serverName);

            clientId = Stream.of(clientId, this.clientId).filter(ObjectHelper::isNotEmpty).findFirst()
                    .orElse(MqttClientId.generate("Camel"));
            MqttClientId mqttClientId = new MqttClientId(clientId, checkClientIdLength);

            return new MqttServerDefinition(
                    mqttServerName, mqttClientId, new MqttServerUrl(serverUrl), username, password, keepAliveTimeout,
                    null);
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public boolean isCheckClientIdLength() {
        return checkClientIdLength;
    }

    public void setCheckClientIdLength(boolean checkClientIdLength) {
        this.checkClientIdLength = checkClientIdLength;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public long getRebirthDebounceDelay() {
        return rebirthDebounceDelay;
    }

    public void setRebirthDebounceDelay(long rebirthDebounceDelay) {
        this.rebirthDebounceDelay = rebirthDebounceDelay;
    }

    public int getKeepAliveTimeout() {
        return keepAliveTimeout;
    }

    public void setKeepAliveTimeout(int keepAliveTimeout) {
        this.keepAliveTimeout = keepAliveTimeout;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public TahuConfiguration copy() {
        try {
            return (TahuConfiguration) clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

}
