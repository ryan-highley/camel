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
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.RejectedExecutionException;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.component.paho.PahoConstants;
import org.apache.camel.component.paho.PahoProducer;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.util.StreamUtils;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.tahu.edge.EdgeClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unused")
public class TahuProducer extends DefaultAsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(TahuProducer.class);

    public TahuProducer(TahuEndpoint endpoint) {
        super(endpoint);
    }

    private EdgeClient client;

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'process'");
    }

    @Override
    public TahuEndpoint getEndpoint() {
        return (TahuEndpoint) super.getEndpoint();
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
