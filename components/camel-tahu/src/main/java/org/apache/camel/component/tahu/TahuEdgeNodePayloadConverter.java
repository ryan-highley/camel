package org.apache.camel.component.tahu;

import java.util.Arrays;
import java.util.Date;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.TypeConversionException;
import org.apache.camel.TypeConverter;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.TypeConverterRegistry;
import org.eclipse.tahu.SparkplugInvalidTypeException;
import org.eclipse.tahu.message.model.Metric;
import org.eclipse.tahu.message.model.MetricDataType;
import org.eclipse.tahu.message.model.SparkplugBPayload;

@Converter(generateLoader = true)
public class TahuEdgeNodePayloadConverter implements HeaderFilterStrategyAware {

    @SuppressWarnings("unchecked")
    @Converter(fallback = true)
    public <T> T toSparkplugBPayload(Class<T> type, Exchange exchange, Object value, TypeConverterRegistry registry) throws TypeConversionException {
        if (!type.equals(SparkplugBPayload.class)) {
            return null;
        }

        if (SparkplugBPayload.class.isAssignableFrom(value.getClass())) {
            return (T) value;
        }

        Message message = exchange.getMessage();

        SparkplugBPayload.SparkplugBPayloadBuilder dataPayloadBuilder = new SparkplugBPayload.SparkplugBPayloadBuilder();
        // PayloadBuilder dataPayloadBuilder = tahuEdgeNodeHandler.new PayloadBuilder(edgeNodeDescriptor);
        // PayloadBuilder dataPayloadBuilder = tahuEdgeNodeHandler.new PayloadBuilder(edgeNodeDescriptor);

        dataPayloadBuilder.setTimestamp(exchange.getClock().asDate());

        TypeConverter converter = registry.lookup(byte[].class, value.getClass());
        
        byte[] valueBytes = converter.convertTo(byte[].class, value);

        dataPayloadBuilder.setBody(valueBytes);

        if (message != null) {
            long messageTimestamp = message.getMessageTimestamp();
            if (messageTimestamp != 0L) {
                dataPayloadBuilder.setTimestamp(new Date(messageTimestamp));
            }

            message.getHeaders().forEach((headerName, headerValue) -> {
                // if (headerFilterStrategy != null && headerFilterStrategy.applyFilterToCamelHeaders(headerName, headerValue, exchange)) {
    
                //     // Skip headers where the headerFilterStrategy returns true, per
                //     // HeaderFilterStrategy.applyFilterToCamelHeaders
    
                // } else if (headerName.equals(TahuConstants.MESSAGE_UUID)) {
                if (headerName.equals(TahuConstants.MESSAGE_UUID)) {

                    dataPayloadBuilder.setUuid(headerValue.toString());
                    // if (headerValue instanceof UUID) {
                    //     dataPayloadBuilder.setUUID((UUID) headerValue);
                    // } else {
                    //     dataPayloadBuilder.setUUID(headerValue.toString());
                    // }
    
                } else if (headerValue instanceof Metric) {
    
                    dataPayloadBuilder.addMetric((Metric) headerValue);
    
                } else if (headerName.startsWith(TahuConstants.METRIC_HEADER_PREFIX)) {
    
                    // If using the default headerFilterStrategy, strip off the header name prefix
                    // if (headerName.startsWith(TahuConstants.METRIC_HEADER_PREFIX)) {
                        String metricName = headerName.substring(TahuConstants.METRIC_HEADER_PREFIX.length());
                    // }

                    try {
                        MetricDataType defaultType = getDefaulMetricDataType(headerValue);
                        Metric metric = new Metric.MetricBuilder(metricName, defaultType, headerValue).createMetric();
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
    }

    private MetricDataType getDefaulMetricDataType(Object headerValue) {
        MetricDataType defaultType = Arrays.stream(MetricDataType.values()).dropWhile(checkType -> {
            try {
                checkType.checkType(headerValue);
                return true;
            } catch (SparkplugInvalidTypeException site) {
                return false;
            }
        }).findFirst().orElse(MetricDataType.Unknown);

        return defaultType;
    }

    private HeaderFilterStrategy headerFilterStrategy;

    @Override
    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    @Override
    public void setHeaderFilterStrategy(HeaderFilterStrategy strategy) {
        this.headerFilterStrategy = strategy;
    }
}
