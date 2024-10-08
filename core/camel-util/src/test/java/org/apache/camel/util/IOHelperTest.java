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
package org.apache.camel.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IOHelperTest {

    @Test
    public void testLookupEnvironmentVariable() {
        assertEquals("8081", IOHelper.lookupEnvironmentVariable("FOO_SERVICE_PORT"));
        assertEquals("8081", IOHelper.lookupEnvironmentVariable("foo-service.port"));
        assertEquals("8081", IOHelper.lookupEnvironmentVariable("foo-service-port"));
        assertEquals("8081", IOHelper.lookupEnvironmentVariable("foo.service.port"));

        assertEquals("mys3arn", IOHelper.lookupEnvironmentVariable("camel.kamelet.aws-s3-source.bucketNameOrArn"));
        assertEquals("mys3arn", IOHelper.lookupEnvironmentVariable("camel.kamelet.aws-s3-source.bucket-name-or-arn"));
        assertEquals("mys3arn", IOHelper.lookupEnvironmentVariable("camel.kamelet.awsS3Source.bucketNameOrArn"));
    }

    @Test
    public void testCharset() {
        assertEquals("UTF-8", IOHelper.getCharsetNameFromContentType("charset=utf-8"));
        assertEquals("UTF-8", IOHelper.getCharsetNameFromContentType("charset=UTF-8"));
        assertEquals("UTF-8", IOHelper.getCharsetNameFromContentType("text/plain; charset=UTF-8"));
        assertEquals("UTF-8", IOHelper.getCharsetNameFromContentType("application/json; charset=utf-8"));
        assertEquals("iso-8859-1", IOHelper.getCharsetNameFromContentType("application/json; charset=iso-8859-1"));
    }
}
