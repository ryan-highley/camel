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
// use modeline to configure properties directly in the same source file
// camel-k: language=java name=Cool property=period=1000

public class Hey extends org.apache.camel.builder.RouteBuilder {

  @Override
  public void configure() throws Exception {
      // Write your routes here, for example:
      from("timer:java?period={{period}}")
        .routeId("java")
        .process(e -> {
           e.getMessage().setBody("Hello from Camel");
        })
        .log("${body}");
  }
}
