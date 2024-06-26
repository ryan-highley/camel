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
package org.apache.camel.processor.onexception;

import org.apache.camel.builder.RouteBuilder;

/**
 * Unit test for CAMEL-1188
 */
public class OnExceptionFromChoiceWithDefaultErrorHandlerTest extends OnExceptionFromChoiceTest {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                onException(MyTechnicalException.class).handled(true).to("mock:tech");
                onException(MyFunctionalException.class).handled(true).to("mock:func");

                from("direct:start").choice().when(method("myServiceBean").isEqualTo("James")).to("mock:when").otherwise()
                        .to("mock:otherwise");
            }
        };
    }

}
