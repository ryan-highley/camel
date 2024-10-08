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
package org.apache.camel.component.twitter;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.test.junit5.TestSupport;
import org.apache.camel.util.ObjectHelper;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EnabledIfSystemProperty(named = "enable.twitter.itests", matches = "true",
                         disabledReason = "Likely has API limits, so it's better to keep it off by default")
public class CamelTwitterITSupport extends CamelTestSupport {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected String consumerKey;
    protected String consumerSecret;
    protected String accessToken;
    protected String accessTokenSecret;

    public CamelTwitterITSupport() {
        Properties properties = new Properties();

        // Load from env
        addProperty(properties, "consumer.key", "CAMEL_TWITTER_CONSUMER_KEY");
        addProperty(properties, "consumer.secret", "CAMEL_TWITTER_CONSUMER_SECRET");
        addProperty(properties, "access.token", "CAMEL_TWITTER_ACCESS_TOKEN");
        addProperty(properties, "access.token.secret", "CAMEL_TWITTER_ACCESS_TOKE_SECRET");

        // if any of the properties is not set, load test-options.properties
        if (!properties.containsKey("consumer.key")
                || !properties.containsKey("consumer.secret")
                || !properties.containsKey("access.token")
                || !properties.containsKey("access.token.secret")) {

            TestSupport.loadExternalPropertiesQuietly(properties, getClass(), "/test-options.properties");
        }

        consumerKey = properties.getProperty("consumer.key");
        consumerSecret = properties.getProperty("consumer.secret");
        accessToken = properties.getProperty("access.token");
        accessTokenSecret = properties.getProperty("access.token.secret");

        ObjectHelper.notNull(consumerKey, "consumer.key");
        ObjectHelper.notNull(consumerSecret, "consumer.secret");
        ObjectHelper.notNull(accessToken, "access.token");
        ObjectHelper.notNull(accessTokenSecret, "access.token.secret");
    }

    protected String getUriTokens() {
        return "consumerKey=" + consumerKey
               + "&consumerSecret=" + consumerSecret
               + "&accessToken=" + accessToken
               + "&accessTokenSecret=" + accessTokenSecret;
    }

    protected Map<String, Object> getParameters() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("consumerKey", this.consumerKey);
        parameters.put("consumerSecret", this.consumerSecret);
        parameters.put("accessToken", this.accessToken);
        parameters.put("accessTokenSecret", this.accessTokenSecret);

        return parameters;
    }

    protected void addProperty(Properties properties, String name, String envName) {
        if (!properties.containsKey(name)) {
            String value = System.getenv(envName);
            if (ObjectHelper.isNotEmpty(value)) {
                properties.setProperty(name, value);
            }
        }
    }
}
