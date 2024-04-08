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

import org.apache.camel.test.infra.common.services.TestService;

public interface HiveMQService extends TestService {

    public static final String HIVEMQ_SERVICE_MQTT_HOST = "hivemq.service.mqtt.host";
    public static final String HIVEMQ_SERVICE_MQTT_PORT = "hivemq.service.mqtt.port";
    public static final String HIVEMQ_SERVICE_MQTT_HOST_ADDRESS = "hivemq.service.mqtt.hostaddress";

    int getMqttPort();

    String getMqttHost();

    boolean isRunning();

    default String getMqttHostAddress() {
        return String.format("tcp://%s:%d", getMqttHost(), getMqttPort());
    }

    @Override
    default void registerProperties() {
        System.setProperty(HIVEMQ_SERVICE_MQTT_HOST, getMqttHost());
        System.setProperty(HIVEMQ_SERVICE_MQTT_PORT, String.valueOf(getMqttPort()));
        System.setProperty(HIVEMQ_SERVICE_MQTT_HOST_ADDRESS, getMqttHostAddress());
    }

    public static class Factory {
        public static final HiveMQService INSTANCE;

        static {
            // Add logic for using RemoteHiveMQService based on system property if necessary
            INSTANCE = new LocalHiveMQService();
        }
    }
}
