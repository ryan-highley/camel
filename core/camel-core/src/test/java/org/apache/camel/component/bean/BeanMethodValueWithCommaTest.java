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
package org.apache.camel.component.bean;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;

/**
 *
 */
public class BeanMethodValueWithCommaTest extends ContextTestSupport {

    @Test
    public void testSingle() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Camela,b");

        template.sendBody("direct:single", "Camel");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testDouble() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Camelc,d");

        template.sendBody("direct:double", "Camel");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testHeader() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Camele,f");

        template.sendBodyAndHeader("direct:header", "Camel", "myHeader", "e,f");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void test() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("SomeID0 0 10,11,12 * * ?");

        template.sendBody("direct:cron", new MyCronBody("SomeID", "0 0 10,11,12 * * ?"));

        assertMockEndpointsSatisfied();
    }

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry jndi = super.createCamelRegistry();
        jndi.bind("foo", new MyBean());
        return jndi;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:single").to("bean:foo?method=bar(${body}, 'a,b')").to("mock:result");

                from("direct:double").to("bean:foo?method=bar(${body}, \"c,d\")").to("mock:result");

                from("direct:header").to("bean:foo?method=bar(${body}, ${header.myHeader})").to("mock:result");

                from("direct:cron").to("bean:foo?method=bar(${body.id}, ${body.cron})").to("mock:result");
            }
        };
    }

    public static class MyBean {

        public String bar(String body, String extra) {
            return body + extra;
        }
    }

    public static class MyCronBody {

        private final String id;
        private final String cron;

        public MyCronBody(String id, String cron) {
            this.id = id;
            this.cron = cron;
        }

        public String getId() {
            return id;
        }

        public String getCron() {
            return cron;
        }
    }

}
