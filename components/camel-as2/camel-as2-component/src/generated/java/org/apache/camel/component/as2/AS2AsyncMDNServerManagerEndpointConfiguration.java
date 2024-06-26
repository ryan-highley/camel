/*
 * Camel EndpointConfiguration generated by camel-api-component-maven-plugin
 */
package org.apache.camel.component.as2;

import org.apache.camel.spi.ApiMethod;
import org.apache.camel.spi.ApiParam;
import org.apache.camel.spi.ApiParams;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

/**
 * Camel endpoint configuration for {@link org.apache.camel.component.as2.api.AS2AsyncMDNServerManager}.
 */
@ApiParams(apiName = "receipt", consumerOnly = true,
           description = "Receives the asynchronous AS2-MDN that is requested by the sender of an AS2 message",
           apiMethods = {@ApiMethod(methodName = "receive", signatures={"void receive(String requestUriPattern, org.apache.hc.core5.http.io.HttpRequestHandler handler)"})}, aliases = {})
@UriParams
@Configurer(extended = true)
public final class AS2AsyncMDNServerManagerEndpointConfiguration extends AS2Configuration {
    @UriParam
    @ApiParam(optional = false, apiMethods = {@ApiMethod(methodName = "receive")})
    private String requestUriPattern;

    public String getRequestUriPattern() {
        return requestUriPattern;
    }

    public void setRequestUriPattern(String requestUriPattern) {
        this.requestUriPattern = requestUriPattern;
    }
}
