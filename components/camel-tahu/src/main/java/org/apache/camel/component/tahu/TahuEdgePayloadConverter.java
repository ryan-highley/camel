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
package org.apache.camel.component.tahu;

import java.util.Date;
import java.util.stream.IntStream;
import java.util.stream.Stream;

// import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.TypeConversionException;
import org.apache.camel.TypeConverter;
// import org.apache.camel.spi.HeaderFilterStrategy;
// import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.TypeConverterRegistry;
import org.eclipse.tahu.SparkplugInvalidTypeException;
import org.eclipse.tahu.message.model.Metric;
import org.eclipse.tahu.message.model.MetricDataType;
import org.eclipse.tahu.message.model.SparkplugBPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// @Converter(generateLoader = true)
public class TahuEdgePayloadConverter { // implements HeaderFilterStrategyAware {

    private static final Logger LOG = LoggerFactory.getLogger(TahuEdgePayloadConverter.class);

    @SuppressWarnings("unchecked")
    // @Converter(fallback = true)
    public <T> T toSparkplugBPayload(Class<T> type, Exchange exchange, Object value, TypeConverterRegistry registry)
            throws TypeConversionException {
        LOG.trace("TypeConverter toSparkplugBPayload called: {} {} {} {}", type, exchange, value, registry);

        try {
            if (!type.equals(SparkplugBPayload.class)) {
                LOG.debug("Requested type conversion for invalid type: {}", type.getName());
                return null;
            }

            if (value != null && SparkplugBPayload.class.isAssignableFrom(value.getClass())) {
                LOG.debug("Requested type conversion for value assignable as a SparkplugBPayload: {}",
                        value.getClass().getName());
                return (T) value;
            }

            if (value == null && (exchange == null || exchange.getMessage() == null)) {
                LOG.debug("Requested type conversion for null exchange or message");
                return null;
            }

            final Message message;
            if (Message.class.isAssignableFrom(value.getClass())) {
                message = (Message) value;
            } else {
                message = exchange.getMessage();
            }

            SparkplugBPayload.SparkplugBPayloadBuilder dataPayloadBuilder = new SparkplugBPayload.SparkplugBPayloadBuilder();

            long payloadTimestamp;
            if (message != null && message.getMessageTimestamp() != 0L) {
                payloadTimestamp = message.getMessageTimestamp();
            } else {
                payloadTimestamp = exchange.getClock().getCreated();
            }
            final Date payloadDate = new Date(payloadTimestamp);

            dataPayloadBuilder.setTimestamp(payloadDate);

            TypeConverter converter = registry.lookup(byte[].class, value.getClass());
            if (converter != null) {
                byte[] valueBytes = converter.convertTo(byte[].class, value);

                dataPayloadBuilder.setBody(valueBytes);
            }

            if (message != null) {

                message.getHeaders().forEach((headerName, headerValue) -> {
                    // if (headerFilterStrategy != null && headerFilterStrategy.applyFilterToCamelHeaders(headerName, headerValue, exchange)) {

                    //     // Skip headers where the headerFilterStrategy returns true, per
                    //     // HeaderFilterStrategy.applyFilterToCamelHeaders

                    if (headerName.equals(TahuConstants.MESSAGE_UUID)) {
                        LOG.trace("Found UUID header: {}", headerValue);

                        dataPayloadBuilder.setUuid(headerValue.toString());

                    } else if (headerValue instanceof Metric) {
                        Metric metricValue = (Metric) headerValue;

                        LOG.trace("Found Metric header: {}", metricValue.getName());

                        // if (metricValue.getTimestamp() == null) {
                        //     metricValue.setTimestamp(payloadDate);
                        // }

                        dataPayloadBuilder.addMetric(metricValue);

                    } else if (headerName.startsWith(TahuConstants.METRIC_HEADER_PREFIX)) {

                        String metricName = headerName;
                        // If using the default headerFilterStrategy, strip off the header name prefix
                        if (metricName.startsWith(TahuConstants.METRIC_HEADER_PREFIX)) {
                            metricName = metricName.substring(TahuConstants.METRIC_HEADER_PREFIX.length());
                        }

                        try {
                            MetricDataType defaultType = getDefaultMetricDataType(headerValue);
                            Metric metric = new Metric.MetricBuilder(metricName, defaultType, headerValue).createMetric();
                            metric.setTimestamp(payloadDate);

                            dataPayloadBuilder.addMetric(metric);
                        } catch (SparkplugInvalidTypeException site) {
                            // Handled by getDefaultMetricDataType method
                            // Safe to no-op here
                        }

                        // dataPayloadBuilder.addMetric(headerName, headerValue);

                    }
                });
            }

            return (T) dataPayloadBuilder.createPayload();

        } finally {
            LOG.trace("TypeConverter toSparkplugBPayload complete");
        }
    }

    private static class MetricDataTypeWorkaround extends MetricDataType {
        private static Stream<MetricDataType> values() {
            // 20 and 21 are not MetricDataTypes, 35 will hit the default case to include Unknown
            return IntStream.range(1, 35)
                    .filter(i -> i != 20 && i != 21)
                    .mapToObj(MetricDataType::fromInteger);
        }
    }

    private MetricDataType getDefaultMetricDataType(Object headerValue) {
        MetricDataType defaultType = MetricDataTypeWorkaround.values().dropWhile(checkType -> {
            try {
                checkType.checkType(headerValue);
                return false;
            } catch (SparkplugInvalidTypeException site) {
                return true;
            }
        }).findFirst().orElse(MetricDataType.Unknown);

        return defaultType;
    }

    // private HeaderFilterStrategy headerFilterStrategy;

    // @Override
    // public HeaderFilterStrategy getHeaderFilterStrategy() {
    //     return headerFilterStrategy;
    // }

    // @Override
    // public void setHeaderFilterStrategy(HeaderFilterStrategy strategy) {
    //     this.headerFilterStrategy = strategy;
    // }
}
