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
package org.apache.camel.model;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test to verify end-user exceptions for miss configuration
 */
public class ProcessorTypeConfigurationTest extends ContextTestSupport {

    @Test
    public void testProcessorRefMissConfigured() {
        Exception e = assertThrows(Exception.class, () -> {
            context.addRoutes(new RouteBuilder() {
                public void configure() {
                    from("direct:in").process("hello");
                }
            });
        }, "Should have thrown IllegalArgumentException");

        assertEquals("No bean could be found in the registry for: hello of type: org.apache.camel.Processor",
                e.getCause().getMessage());
    }
}
