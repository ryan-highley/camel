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
package org.apache.camel.language.datasonnet;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.apache.camel.support.language.LanguageAnnotation;

/**
 * Used to inject a DataSonnet expression into a field, property, method or parameter when using
 * <a href="http://camel.apache.org/bean-integration.html">Bean Integration</a>.
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target({ ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER })
@LanguageAnnotation(language = "datasonnet", factory = DatasonnetAnnotationExpressionFactory.class)
public @interface Datasonnet {

    /**
     * The datasonnet expression which will be applied
     */
    String value();

    /**
     * The desired return type from the evaluated datasonnet.
     */
    Class<?> resultType() default Object.class;

    /**
     * Source to use, instead of message body. You can prefix with variable:, header:, or property: to specify kind of
     * source. Otherwise, the source is assumed to be a variable. Use empty or null to use default source, which is the
     * message body.
     */
    String source() default "";

    /**
     * The message body media type.
     */
    String bodyMediaType() default "";

    /**
     * The media type to output.
     */
    String outputMediaType() default "";
}
