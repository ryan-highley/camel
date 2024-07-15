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

import org.apache.camel.test.infra.common.services.ContainerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.testcontainers.hivemq.HiveMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

public class LocalHiveMQService implements HiveMQService, ContainerService<HiveMQContainer> {

    private static final Logger LOG = LoggerFactory.getLogger(LocalHiveMQService.class);

    @SuppressWarnings("resource")
    @Container
    final HiveMQContainer container = new HiveMQContainer(DockerImageName.parse("hivemq/hivemq-ce").withTag("2024.6"))
            .withLogLevel(Level.DEBUG)
            .withHiveMQConfig(MountableFile.forClasspathResource("/hivemq-ce/conf/config.xml"))
            .withCopyFileToContainer(MountableFile.forClasspathResource("/hivemq-ce/conf/logback.xml"),
                    "/opt/hivemq/conf/logback.xml")
            .withExtension(MountableFile.forClasspathResource("/hivemq-ce/extensions/sparkplug-tck"))
            .withExposedPorts(1883, 8000)
            .waitForExtension("Eclipse. Sparkplug. TCK");

    @Override
    public int getMqttPort() {
        return container.getMqttPort();
    }

    @Override
    public String getMqttHost() {
        return container.getHost();
    }

    @Override
    public void initialize() {
        LOG.info("Starting the HiveMQ container");
        container.start();

        registerProperties();
        LOG.info("HiveMQ instance running at {}", getMqttHostAddress());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the HiveMQ container");
        container.stop();
    }

    @Override
    public void close() {
        LOG.info("Closing the HiveMQ container");
        container.close();
    }

    @Override
    public boolean isRunning() {
        return container.isRunning();
    }

    @Override
    public HiveMQContainer getContainer() {
        return container;
    }
}
