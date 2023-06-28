package org.apache.camel.component.sparkplug;

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SparkplugBProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(SparkplugBProducer.class);
    private SparkplugBEndpoint endpoint;

    public SparkplugBProducer(SparkplugBEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    public void process(Exchange exchange) throws Exception {
        System.out.println(exchange.getIn().getBody());
    }

}
