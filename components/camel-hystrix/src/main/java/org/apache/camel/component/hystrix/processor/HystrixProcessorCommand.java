/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.hystrix.processor;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hystrix Command for the Camel Hystrix EIP.
 */
public class HystrixProcessorCommand extends HystrixCommand<Exchange> {

    private static final Logger LOG = LoggerFactory.getLogger(HystrixProcessorCommand.class);
    private final Exchange exchange;
    private final AsyncCallback callback;
    private final AsyncProcessor processor;
    private final AsyncProcessor fallback;

    public HystrixProcessorCommand(Setter setter, Exchange exchange, AsyncCallback callback, AsyncProcessor processor, AsyncProcessor fallback) {
        super(setter);
        this.exchange = exchange;
        this.callback = callback;
        this.processor = processor;
        this.fallback = fallback;
    }

    @Override
    protected Exchange getFallback() {
        // only run fallback if there was an exception
        Exception exception = exchange.getException();
        if (exception == null) {
            return exchange;
        }

        try {
            if (fallback != null) {
                LOG.debug("Error occurred processing. Will now run fallback. Exception class: {} message: {}.", exception.getClass().getName(), exception.getMessage());
                // store the last to endpoint as the failure endpoint
                if (exchange.getProperty(Exchange.FAILURE_ENDPOINT) == null) {
                    exchange.setProperty(Exchange.FAILURE_ENDPOINT, exchange.getProperty(Exchange.TO_ENDPOINT));
                }
                // give the rest of the pipeline another chance
                exchange.setProperty(Exchange.EXCEPTION_HANDLED, true);
                exchange.setProperty(Exchange.EXCEPTION_CAUGHT, exception);
                exchange.setException(null);
                // and we should not be regarded as exhausted as we are in a try .. catch block
                exchange.removeProperty(Exchange.REDELIVERY_EXHAUSTED);
                // run the fallback processor
                try {
                    LOG.debug("Running fallback: {} with exchange: {}", fallback, exchange);
                    fallback.process(exchange, callback);
                } catch (Exception e) {
                    exchange.setException(e);
                }
            }
        } finally {
            LOG.debug("Running fallback: {} with exchange: {} done", fallback, exchange);
            exchange.removeProperty(Exchange.TRY_ROUTE_BLOCK);
            callback.done(false);
        }

        return exchange;
    }

    @Override
    protected Exchange run() throws Exception {
        LOG.debug("Running processor: {} with exchange: {}", processor, exchange);

        // run this as if we run inside try .. catch so there is no regular Camel error handler
        exchange.setProperty(Exchange.TRY_ROUTE_BLOCK, true);
        try {
            processor.process(exchange, callback);
        } catch (Exception e) {
            exchange.setException(e);
        }

        // is fallback enabled
        Boolean fallbackEnabled = getProperties().fallbackEnabled().get();

        // if we failed then throw an exception if fallback is enabled
        if (fallbackEnabled == null || fallbackEnabled && exchange.getException() != null) {
            throw exchange.getException();
        }
        // no fallback then we are done
        try {
            LOG.debug("Running processor: {} with exchange: {} done", processor, exchange);
            exchange.removeProperty(Exchange.TRY_ROUTE_BLOCK);
            callback.done(false);
        } catch (Throwable e) {
            exchange.setException(e);
        }

        return exchange;
    }
}
