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

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.eclipse.tahu.edge.EdgeClient;

@Component("sparkplug")
public class TahuComponent extends DefaultComponent {

    @Metadata
    private TahuConfiguration configuration = new TahuConfiguration();

    @Metadata(label = "advanced")
    private EdgeClient client;

    public TahuComponent() {
        this(null);
    }

    public TahuComponent(CamelContext context) {
        super(context);

        // registerExtension(new SparkplugBComponentVerifierExtension());
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        // Each endpoint can have its own configuration so make
        // a copy of the configuration
        TahuConfiguration spbConfig = getConfiguration().copy();

        TahuEndpoint answer = new TahuEndpoint(uri, remaining, this, spbConfig);
        answer.setClient(client);

        setProperties(answer, parameters);
        return answer;
    }

    public TahuConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * To use a shared Sparkplug B configuration
     *
     * @param configuration
     */
    public void setConfiguration(TahuConfiguration configuration) {
        this.configuration = configuration;
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
