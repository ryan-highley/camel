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
package org.apache.camel.component.jt400;

import java.time.Duration;
import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.junit5.TestSupport;
import org.apache.camel.util.StopWatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test case for {@link Jt400DataQueueConsumer}.
 * <p>
 * So that timeout semantics can be tested, an URI to an empty data queue on an AS400 system should be provided (in a
 * resource named <code>"jt400test.properties"</code>, in a property with key
 * <code>"org.apache.camel.component.jt400.emptydtaq.uri"</code>).
 * </p>
 */
@Disabled("Test manual")
public class Jt400DataQueueConsumerManualTest {

    /**
     * The deviation of the actual timeout value that we permit in our timeout tests.
     */
    private static final long TIMEOUT_TOLERANCE = 300L;

    /**
     * Timeout value in milliseconds used to test <code>receive(long)</code>.
     */
    private static final long TIMEOUT_VALUE = 3999L;

    /**
     * The amount of time in milliseconds to pass so that a call is assumed to be a blocking call.
     */
    private static final long BLOCKING_THRESHOLD = 5000L;

    /**
     * The consumer instance used in the tests.
     */
    private Jt400DataQueueConsumer consumer;

    /**
     * Flag that indicates whether <code>receive()</code> has returned from call.
     */
    private boolean receiveFlag;

    @BeforeEach
    public void setUp() throws Exception {
        // Load endpoint URI

        Properties props = TestSupport.loadExternalProperties(getClass(), "jt400test.properties");
        String endpointURI = props.getProperty("org.apache.camel.component.jt400.emptydtaq.uri");

        // Instantiate consumer
        CamelContext camel = new DefaultCamelContext();
        Jt400Component component = new Jt400Component();

        component.setCamelContext(camel);
        consumer = (Jt400DataQueueConsumer) component.createEndpoint(endpointURI).createPollingConsumer();
        camel.start();
    }

    /**
     * Tests whether <code>receive(long)</code> honours the <code>timeout</code> parameter.
     */
    @Test
    public void testReceiveLong() {
        assertTimeout(Duration.ofMillis(TIMEOUT_VALUE + TIMEOUT_TOLERANCE),
                () -> consumer.receive(TIMEOUT_VALUE));
    }

    /**
     * Tests whether receive() blocks indefinitely.
     */
    @Test
    public void testReceive() throws InterruptedException {
        new Thread(new Runnable() {
            public void run() {
                consumer.receive();
                receiveFlag = true;
            }
        }).start();

        StopWatch watch = new StopWatch();
        while (!receiveFlag) {
            if ((watch.taken()) > BLOCKING_THRESHOLD) {
                /* Passed test. */
                return;
            }
            Thread.sleep(50L);
        }
        fail("Method receive() has returned from call.");
    }

}
