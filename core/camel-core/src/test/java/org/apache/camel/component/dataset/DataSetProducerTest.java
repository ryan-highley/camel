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
package org.apache.camel.component.dataset;

import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DataSetProducerTest extends ContextTestSupport {

    protected final SimpleDataSet dataSet = new SimpleDataSet(20);

    final String dataSetName = "foo";
    final String dataSetUri = "dataset://" + dataSetName;
    final String dataSetUriWithDataSetIndexSetToOff = dataSetUri + "?dataSetIndex=off";
    final String dataSetUriWithDataSetIndexSetToLenient = dataSetUri + "?dataSetIndex=lenient";
    final String dataSetUriWithDataSetIndexSetToStrict = dataSetUri + "?dataSetIndex=strict";
    final String sourceUri = "direct://source";
    final String resultUri = "mock://result";

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry answer = super.createCamelRegistry();
        answer.bind("foo", dataSet);
        return answer;
    }

    @Test
    public void testSendingMessagesExplicitlyToDataSetEndpointWithDataSetIndexHeader() throws Exception {
        long size = dataSet.getSize();
        for (long i = 0; i < size; i++) {
            template.sendBodyAndHeader(dataSetUri, dataSet.getDefaultBody(), Exchange.DATASET_INDEX, i);
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendingMessagesExplicitlyToDataSetEndpointWithoutDataSetIndexHeader() throws Exception {
        long size = dataSet.getSize();
        for (long i = 0; i < size; i++) {
            template.sendBody(dataSetUri, dataSet.getDefaultBody());
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendingMessagesExplicitlyToDataSetEndpointWithoutDataSetIndexAndDataSetIndexUriParameterSetToOff()
            throws Exception {
        long size = dataSet.getSize();
        for (long i = 0; i < size; i++) {
            if (0 == i % 2) {
                template.sendBodyAndHeader(dataSetUriWithDataSetIndexSetToLenient, dataSet.getDefaultBody(),
                        Exchange.DATASET_INDEX, i);
            } else {
                template.sendBody(dataSetUriWithDataSetIndexSetToLenient, dataSet.getDefaultBody());
            }
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendingMessagesExplicitlyToDataSetEndpointWithoutDataSetIndexAndDataSetIndexUriParameterSetToLenient()
            throws Exception {
        long size = dataSet.getSize();
        for (long i = 0; i < size; i++) {
            if (0 == i % 2) {
                template.sendBodyAndHeader(dataSetUriWithDataSetIndexSetToLenient, dataSet.getDefaultBody(),
                        Exchange.DATASET_INDEX, i);
            } else {
                template.sendBody(dataSetUriWithDataSetIndexSetToLenient, dataSet.getDefaultBody());
            }
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendingMessagesExplicitlyToDataSetEndpointWithoutDataSetIndexAndDataSetIndexUriParameterSetToStrict()
            throws Exception {
        long size = dataSet.getSize();
        for (long i = 0; i < size; i++) {
            template.sendBodyAndHeader(dataSetUriWithDataSetIndexSetToStrict, dataSet.getDefaultBody(), Exchange.DATASET_INDEX,
                    i);
        }

        assertMockEndpointsSatisfied();
    }

    /**
     * Verify that the CamelDataSetIndex header is optional when the dataSetIndex parameter is unset
     */
    @Test
    public void testNotSettingDataSetIndexHeaderWhenDataSetIndexUriParameterIsUnset() throws Exception {
        long size = dataSet.getSize();
        for (long i = 0; i < size; i++) {
            if (0 == (size % 2)) {
                template.sendBodyAndHeader(dataSetUri, dataSet.getDefaultBody(), Exchange.DATASET_INDEX, i);
            } else {
                template.sendBody(dataSetUri, dataSet.getDefaultBody());
            }
        }

        assertMockEndpointsSatisfied();
    }

    /**
     * Verify that the CamelDataSetIndex header is ignored when the dataSetIndex URI paramter is set to off
     */
    @Test
    public void testNotSettingDataSetIndexHeaderWhenDataSetIndexUriParameterSetToOff() throws Exception {
        long size = dataSet.getSize();
        for (long i = 0; i < size; i++) {
            if (0 == (size % 2)) {
                template.sendBodyAndHeader(dataSetUriWithDataSetIndexSetToOff, dataSet.getDefaultBody(), Exchange.DATASET_INDEX,
                        size - i);
            } else {
                template.sendBody(dataSetUriWithDataSetIndexSetToOff, dataSet.getDefaultBody());
            }
        }

        assertMockEndpointsSatisfied();
    }

    /**
     * Verify that the CamelDataSetIndex header is optional when the dataSetIndex URI parameter is set to lenient
     */
    @Test
    public void testNotSettingDataSetIndexHeaderWhenDataSetIndexUriParameterSetToLenient() throws Exception {
        long size = dataSet.getSize();
        for (long i = 0; i < size; i++) {
            if (0 == (size % 2)) {
                template.sendBodyAndHeader(dataSetUriWithDataSetIndexSetToLenient, dataSet.getDefaultBody(),
                        Exchange.DATASET_INDEX, i);
            } else {
                template.sendBody(dataSetUriWithDataSetIndexSetToLenient, dataSet.getDefaultBody());
            }
        }

        assertMockEndpointsSatisfied();
    }

    /**
     * Verify that the CamelDataSetIndex header is required when the dataSetIndex URI parameter is set to strict
     */
    @Test
    public void testNotSettingDataSetIndexHeaderWhenDataSetIndexUriParameterSetToStrict() throws Exception {
        long size = dataSet.getSize();
        for (long i = 0; i < size; i++) {
            template.sendBody(dataSetUriWithDataSetIndexSetToStrict, dataSet.getDefaultBody());
        }

        // Check as much of the string as possible - but the ExchangeID at
        // the end will be unique
        String expectedErrorString = "Caught exception on " + dataSetUriWithDataSetIndexSetToStrict + " due to:" + " No '"
                                     + Exchange.DATASET_INDEX
                                     + "' header available of type: java.lang.Long";
        List<Endpoint> endpoints = context.getEndpoints().stream()
                .filter(e -> e.getEndpointUri().startsWith(dataSetUriWithDataSetIndexSetToStrict))
                .toList();
        for (Endpoint endpoint : endpoints) {
            assertThat(endpoint).isInstanceOf(MockEndpoint.class);
            ((MockEndpoint) endpoint).assertIsNotSatisfied(expectedErrorString);
        }
    }

    @Test
    public void testDataSetIndexUriParameterUnset() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(sourceUri).to(dataSetUri).to(resultUri);
            }
        });
        context.start();

        long size = dataSet.getSize();

        MockEndpoint result = getMockEndpoint(resultUri);
        result.expectedMessageCount((int) size);
        result.allMessages().header(Exchange.DATASET_INDEX).isNotNull();
        result.expectsAscending(header(Exchange.DATASET_INDEX).convertTo(Number.class));

        for (long i = 0; i < size; i++) {
            template.sendBody(sourceUri, dataSet.getDefaultBody());
        }

        assertMockEndpointsSatisfied();

        result.assertMessagesAscending(header(Exchange.DATASET_INDEX).convertTo(Number.class));
    }

    @Test
    public void testDataSetIndexUriParameterSetToOff() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(sourceUri).to(dataSetUriWithDataSetIndexSetToOff).to(resultUri);
            }
        });
        context.start();

        long size = dataSet.getSize();

        MockEndpoint result = getMockEndpoint(resultUri);
        result.expectedMessageCount((int) size);
        result.expectsAscending(header(Exchange.DATASET_INDEX).convertTo(Number.class));
        result.allMessages().header(Exchange.DATASET_INDEX).isNotNull();

        for (long i = 0; i < size; i++) {
            template.sendBodyAndHeader(sourceUri, dataSet.getDefaultBody(), Exchange.DATASET_INDEX, i);
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testDataSetIndexUriParameterSetToLenient() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(sourceUri).to(dataSetUriWithDataSetIndexSetToLenient).to(resultUri);
            }
        });
        context.start();

        long size = dataSet.getSize();

        MockEndpoint result = getMockEndpoint(resultUri);
        result.expectedMessageCount((int) size);
        result.expectsAscending(header(Exchange.DATASET_INDEX).convertTo(Number.class));
        result.allMessages().header(Exchange.DATASET_INDEX).isNotNull();

        for (long i = 0; i < size; i++) {
            template.sendBodyAndHeader(sourceUri, dataSet.getDefaultBody(), Exchange.DATASET_INDEX, i);
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testDataSetIndexUriParameterSetToStrict() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(sourceUri).to(dataSetUriWithDataSetIndexSetToStrict).to(resultUri);
            }
        });
        context.start();

        long size = dataSet.getSize();

        MockEndpoint result = getMockEndpoint(resultUri);
        result.expectedMessageCount((int) size);
        result.expectsAscending(header(Exchange.DATASET_INDEX).convertTo(Number.class));
        result.allMessages().header(Exchange.DATASET_INDEX).isNotNull();

        for (long i = 0; i < size; i++) {
            template.sendBodyAndHeader(sourceUri, dataSet.getDefaultBody(), Exchange.DATASET_INDEX, i);
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInvalidDataSetIndexValueWithDataSetIndexUriParameterUnset() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(sourceUri).to(dataSetUri).to(resultUri);
            }
        });
        context.start();

        long size = dataSet.getSize();

        MockEndpoint result = getMockEndpoint(resultUri);
        result.expectedMessageCount((int) size);
        result.allMessages().header(Exchange.DATASET_INDEX).isNotNull();

        for (long i = 0; i < size; i++) {
            if (i == (size / 2)) {
                template.sendBodyAndHeader(sourceUri, dataSet.getDefaultBody(), Exchange.DATASET_INDEX, i + 10);
            } else {
                template.sendBody(sourceUri, dataSet.getDefaultBody());
            }
        }

        // Check as much of the string as possible - but the ExchangeID at
        // the end will be unique
        String expectedErrorString = "Caught exception on " + dataSetUri + " due to: " + "Header: " + Exchange.DATASET_INDEX
                                     + " does not match. Expected: " + size / 2
                                     + " but was: " + (size / 2 + 10);
        List<Endpoint> endpoints = context.getEndpoints().stream()
                .filter(e -> e.getEndpointUri().startsWith(dataSetUri))
                .toList();
        for (Endpoint endpoint : endpoints) {
            assertThat(endpoint).isInstanceOf(MockEndpoint.class);
            ((MockEndpoint) endpoint).assertIsNotSatisfied(expectedErrorString);
        }
    }

    @Test
    public void testInvalidDataSetIndexValueWithDataSetIndexUriParameterSetToOff() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(sourceUri).to(dataSetUriWithDataSetIndexSetToOff).to(resultUri);
            }
        });
        context.start();

        long size = dataSet.getSize();

        MockEndpoint result = getMockEndpoint(resultUri);
        result.expectedMessageCount((int) size);

        for (long i = 0; i < size; i++) {
            if (i == (size / 2)) {
                template.sendBodyAndHeader(sourceUri, dataSet.getDefaultBody(), Exchange.DATASET_INDEX, i + 10);
            } else {
                template.sendBody(sourceUri, dataSet.getDefaultBody());
            }
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInvalidDataSetIndexValueWithDataSetIndexUriParameterSetToLenient() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(sourceUri).to(dataSetUriWithDataSetIndexSetToLenient).to(resultUri);
            }
        });
        context.start();

        long size = dataSet.getSize();

        MockEndpoint result = getMockEndpoint(resultUri);
        result.expectedMessageCount((int) size);
        result.allMessages().header(Exchange.DATASET_INDEX).isNotNull();

        for (long i = 0; i < size; i++) {
            if (i == (size / 2)) {
                template.sendBodyAndHeader(sourceUri, dataSet.getDefaultBody(), Exchange.DATASET_INDEX, i + 10);
            } else {
                template.sendBody(sourceUri, dataSet.getDefaultBody());
            }
        }

        // Check as much of the string as possible - but the ExchangeID at
        // the end will be unique
        String expectedErrorString = "Caught exception on " + dataSetUriWithDataSetIndexSetToLenient + " due to: "
                                     + "Header: " + Exchange.DATASET_INDEX
                                     + " does not match. Expected: " + size / 2 + " but was: " + (size / 2 + 10);
        List<Endpoint> endpoints = context.getEndpoints().stream()
                .filter(e -> e.getEndpointUri().startsWith(dataSetUriWithDataSetIndexSetToLenient))
                .toList();
        for (Endpoint endpoint : endpoints) {
            assertThat(endpoint).isInstanceOf(MockEndpoint.class);
            ((MockEndpoint) endpoint).assertIsNotSatisfied(expectedErrorString);
        }
    }

    @Test
    public void testInvalidDataSetIndexValueWithDataSetIndexUriParameterSetToStrict() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(sourceUri).to(dataSetUriWithDataSetIndexSetToStrict).to(resultUri);
            }
        });
        context.start();

        long size = dataSet.getSize();

        MockEndpoint result = getMockEndpoint(resultUri);
        result.expectedMessageCount((int) size);
        result.allMessages().header(Exchange.DATASET_INDEX).isNotNull();

        for (long i = 0; i < size; i++) {
            if (i == (size / 2)) {
                template.sendBodyAndHeader(sourceUri, dataSet.getDefaultBody(), Exchange.DATASET_INDEX, i + 10);
            } else {
                template.sendBodyAndHeader(sourceUri, dataSet.getDefaultBody(), Exchange.DATASET_INDEX, i);
            }
        }

        // Check as much of the string as possible - but the ExchangeID at
        // the end will be unique
        String expectedErrorString = "Caught exception on " + dataSetUriWithDataSetIndexSetToStrict + " due to: "
                                     + "Header: " + Exchange.DATASET_INDEX
                                     + " does not match. Expected: " + size / 2 + " but was: " + (size / 2 + 10);
        List<Endpoint> endpoints = context.getEndpoints().stream()
                .filter(e -> e.getEndpointUri().startsWith(dataSetUriWithDataSetIndexSetToStrict))
                .toList();
        for (Endpoint endpoint : endpoints) {
            assertThat(endpoint).isInstanceOf(MockEndpoint.class);
            ((MockEndpoint) endpoint).assertIsNotSatisfied(expectedErrorString);
        }
    }
}
