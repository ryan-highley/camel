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
package org.apache.camel.component.browse;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BrowsableEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.DefaultProducer;

/**
 * Inspect the messages received on endpoints supporting {@link BrowsableEndpoint}.
 *
 * This can be useful for testing, visualisation tools or debugging. The exchanges sent to the endpoint are all
 * available to be browsed.
 */
@UriEndpoint(firstVersion = "1.3.0", scheme = "browse", title = "Browse", syntax = "browse:name",
             remote = false, category = { Category.CORE, Category.MONITORING })
public class BrowseEndpoint extends DefaultEndpoint implements BrowsableEndpoint {

    @UriPath(description = "A name which can be any string to uniquely identify the endpoint")
    @Metadata(required = true)
    private String name;
    @UriParam(label = "advanced",
              description = "Maximum number of messages to keep in memory available for browsing. Use 0 for unlimited.")
    private int browseLimit;
    @UriParam(label = "advanced",
              description = "To use a predicate to filter whether to include the message for browsing. Return true to include, false to exclude.")
    private Predicate<Exchange> filter;

    private List<Exchange> exchanges;
    private volatile Processor onExchangeProcessor;

    public BrowseEndpoint() {
    }

    public BrowseEndpoint(String uri, Component component) {
        super(uri, component);
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    @Override
    public int getBrowseLimit() {
        return browseLimit;
    }

    @Override
    public void setBrowseLimit(int browseLimit) {
        this.browseLimit = browseLimit;
    }

    @Override
    public List<Exchange> getExchanges() {
        if (exchanges == null) {
            exchanges = createExchangeList();
        }
        return exchanges;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new DefaultProducer(this) {
            public void process(Exchange exchange) throws Exception {
                boolean accept = true;
                if (filter != null) {
                    accept = filter.test(exchange);
                }
                if (accept) {
                    onExchange(exchange);
                }
            }
        };
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        this.onExchangeProcessor = processor;

        Consumer answer = new DefaultConsumer(this, processor);
        configureConsumer(answer);
        return answer;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Predicate<Exchange> getFilter() {
        return filter;
    }

    public void setFilter(Predicate<Exchange> filter) {
        this.filter = filter;
    }

    protected List<Exchange> createExchangeList() {
        return new CopyOnWriteArrayList<>();
    }

    /**
     * Invoked on a message exchange being sent by a producer
     *
     * @param  exchange  the exchange
     * @throws Exception is thrown if failed to process the exchange
     */
    protected void onExchange(Exchange exchange) throws Exception {
        if (browseLimit > 0) {
            // make room if end of capacity
            int max = exchanges.size();
            if (max >= browseLimit) {
                int space = Math.abs(max - (browseLimit - 1));
                exchanges = exchanges.subList(space, max);
            }
        }
        exchanges.add(exchange);

        // now fire the consumer
        if (onExchangeProcessor != null) {
            onExchangeProcessor.process(exchange);
        }
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        exchanges = createExchangeList();
    }

    @Override
    protected void doStop() throws Exception {
        if (exchanges != null) {
            exchanges.clear();
            exchanges = null;
        }
        super.doStop();
    }
}
