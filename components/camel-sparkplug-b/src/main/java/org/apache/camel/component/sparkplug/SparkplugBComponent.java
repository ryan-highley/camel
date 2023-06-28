package org.apache.camel.component.sparkplug;

import java.util.Map;

import org.apache.camel.Endpoint;

import org.apache.camel.support.DefaultComponent;

@org.apache.camel.spi.annotations.Component("sparkplug")
public class SparkplugBComponent extends DefaultComponent {
    
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        Endpoint endpoint = new SparkplugBEndpoint(uri, this);
        setProperties(endpoint, parameters);
        return endpoint;
    }
}
