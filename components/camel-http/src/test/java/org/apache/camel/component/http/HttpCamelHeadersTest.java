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
package org.apache.camel.component.http;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.component.http.handler.HeaderValidationHandler;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.http.HttpMethods.GET;
import static org.apache.hc.core5.http.HttpHeaders.ACCEPT_LANGUAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class HttpCamelHeadersTest extends BaseHttpTest {

    protected HttpServer localServer;

    @Override
    public void setupResources() throws Exception {
        Map<String, String> expectedHeaders = new HashMap<>();
        expectedHeaders.put("TestHeader", "test");
        expectedHeaders.put(ACCEPT_LANGUAGE, "pl");

        localServer
                = ServerBootstrap.bootstrap()
                        .setCanonicalHostName("localhost").setHttpProcessor(getBasicHttpProcessor())
                        .setConnectionReuseStrategy(getConnectionReuseStrategy()).setResponseFactory(getHttpResponseFactory())
                        .setSslContext(getSSLContext())
                        .register("/",
                                new MyHeaderValidationHandler(GET.name(), "HTTP/1.1", getExpectedContent(), expectedHeaders))
                        .create();
        localServer.start();
    }

    @Override
    public void cleanupResources() throws Exception {

        if (localServer != null) {
            localServer.stop();
        }
    }

    @Test
    public void httpHeadersShouldPresent() {
        assertExchange(doExchange());
    }

    @Override
    protected void assertHeaders(Map<String, Object> headers) {
        super.assertHeaders(headers);

        assertEquals("test", headers.get("TestHeader"));
        assertEquals("pl", headers.get(ACCEPT_LANGUAGE));
    }

    private Exchange doExchange() {
        return template.request(
                "http://localhost:"
                                + localServer.getLocalPort()
                                + "/"
                                + setupEndpointParams(),
                exchange -> {
                    exchange.getIn().setHeader("TestHeader", "test");
                    exchange.getIn().setHeader(ACCEPT_LANGUAGE, "pl");
                    exchange.getIn().setHeader(Exchange.HTTP_PROTOCOL_VERSION, "HTTP/1.1");
                });
    }

    protected String setupEndpointParams() {
        return "";
    }

    private static class MyHeaderValidationHandler extends HeaderValidationHandler {
        private final String expectProtocolVersion;

        MyHeaderValidationHandler(String expectedMethod, String protocolVersion,
                                  String responseContent, Map<String, String> expectedHeaders) {
            super(expectedMethod, null, null, responseContent, expectedHeaders);
            expectProtocolVersion = protocolVersion;
        }

        @Override
        public void handle(
                final ClassicHttpRequest request, final ClassicHttpResponse response,
                final HttpContext context)
                throws HttpException, IOException {
            if (!expectProtocolVersion.equals(request.getVersion().toString())) {
                response.setCode(HttpStatus.SC_HTTP_VERSION_NOT_SUPPORTED);
                return;
            }
            super.handle(request, response, context);
        }

    }
}
