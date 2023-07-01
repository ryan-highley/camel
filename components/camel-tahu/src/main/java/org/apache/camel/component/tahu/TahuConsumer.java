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

import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;
import org.eclipse.tahu.edge.EdgeClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unused")
public class TahuConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(TahuConsumer.class);

    private EdgeClient client;

    public TahuConsumer(TahuEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
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

    @Override
    protected void doStart() throws Exception {
        // TODO Auto-generated method stub
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        // TODO Auto-generated method stub
        super.doStop();
    }

}
