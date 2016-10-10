/**
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.zuul.ribbon;

import com.netflix.client.ClientException;
import com.netflix.client.ClientFactory;
import com.netflix.client.http.CaseInsensitiveMultiMap;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpResponse;
import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.loadbalancer.Server;
import com.netflix.niws.client.http.RestClient;
import com.netflix.zuul.bytebuf.ByteBufUtils;
import com.netflix.zuul.constants.ZuulConstants;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.exception.ZuulException;
import com.netflix.zuul.filters.FilterComplete;
import com.netflix.zuul.message.*;
import com.netflix.zuul.message.http.*;
import com.netflix.zuul.origins.Origin;
import com.netflix.zuul.origins.OriginRequest;
import com.netflix.zuul.stats.Timing;
import com.netflix.zuul.util.ProxyUtils;
import io.netty.buffer.ByteBuf;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Promise;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertTrue;

/**
 * User: Mike Smith
 * Date: 5/12/15
 * Time: 11:25 PM
 */
public class RibbonOrigin implements Origin
{
    private static final Logger LOG = LoggerFactory.getLogger(RibbonOrigin.class);

    private static final DynamicIntProperty MAX_BODY_SIZE_PROP = DynamicPropertyFactory.getInstance().getIntProperty(
            ZuulConstants.ZUUL_REQUEST_BODY_MAX_SIZE, 25 * 1000 * 1024);

    private final String name;
    private final RestClient client;

    public RibbonOrigin(String name)
    {
        this.name = name;
        this.client = (RestClient) ClientFactory.getNamedClient(name);
    }

    @Override
    public String getName()
    {
        return name;
    }

    @Override
    public boolean isAvailable() {
        if(client == null || client.getLoadBalancer() == null) {
            return false;
        }
        List<Server> serverList = client.getLoadBalancer().getServerList(true);
        return serverList != null && serverList.size() > 0;
    }


    @Override
    public OriginRequest request(HttpRequestMessage requestMsg)
    {
        SessionContext context = requestMsg.getContext();

        Timing readTiming = context.getTimings().getRequestBodyRead();
        readTiming.start();
        
        RibbonOriginRequest originRequest = new RibbonOriginRequest(requestMsg, new RibbonPromise());
        
        return originRequest;
    }

    protected ZuulException proxyError(HttpRequestMessage zuulReq, Throwable t, String errorCauseMsg)
    {
        // Flag this as a proxy failure in the RequestContext. Error filter will then use this flag.
        zuulReq.getContext().setShouldSendErrorResponse(true);

        LOG.error(String.format("Error making http request to Origin. restClientName=%s, url=%s",
                this.name, zuulReq.getPathAndQuery()), t);

        if (errorCauseMsg == null) {
            if (t.getCause() != null) {
                errorCauseMsg = t.getCause().getMessage();
            }
        }
        if (errorCauseMsg == null)
            errorCauseMsg = "unknown";

        return new ZuulException("Proxying error", t, errorCauseMsg);
    }

    protected HttpResponseMessage createHttpResponseMessage(HttpResponse ribbonResp, 
                                                            HttpRequestMessage request,
                                                            FilterComplete callback)
    {
        // Convert to a zuul response object.
        HttpResponseMessage respMsg = new HttpResponseMessageImpl(request.getContext(), request, 500);
        respMsg.setStatus(ribbonResp.getStatus());
        for (Map.Entry<String, String> header : ribbonResp.getHttpHeaders().getAllHeaders()) {
            HeaderName headerName = HttpHeaderNames.get(header.getKey());
            if (ProxyUtils.isValidResponseHeader(headerName)) {
                respMsg.getHeaders().add(headerName, header.getValue());
            }
        }

        // Store this original response info for future reference (ie. for metrics and access logging purposes).
        respMsg.storeInboundResponse();
        
        // Invoke the outbound filter chain for this response.
        callback.invoke(respMsg);

        // And now invoke the outbound filter chain for each chunk of bytes of the response body.
        if (ribbonResp.hasEntity()) {
            ByteBufUtils.bodyInputStreamToFilterCallbacks(respMsg, ribbonResp.getInputStream(), callback);
        }
        else {
            // No response body, so push an empty last content component.
            callback.invoke(MessageContentImpl.createEmptyLastContent(respMsg));
        }

        return respMsg;
    }
    
    private class RibbonOriginRequest implements OriginRequest
    {
        private final HttpRequestMessage request;
        private final Promise<HttpResponseMessage> promise;

        public RibbonOriginRequest(HttpRequestMessage request, Promise<HttpResponseMessage> promise)
        {
            this.request = request;
            this.promise = promise;
        }

        @Override
        public Promise<HttpResponseMessage> promise()
        {
            return promise;
        }

        @Override
        public void writeContent(MessageContent messageContent, FilterComplete callback)
        {
            // Buffer the content until we've received the last one.
            request.addContent(messageContent.content());
            
            if (messageContent.isLast()) {
                onLastContent(callback);
            }
        }
        
        private void onLastContent(FilterComplete callback)
        {
            SessionContext context = request.getContext();
            if (client == null) {
                throw proxyError(request, new IllegalArgumentException("No RestClient found for name! name=" + String.valueOf(name)), null);
            }

            // Convert to a ribbon request.
            HttpRequest.Verb verb = HttpRequest.Verb.valueOf(request.getMethod().toUpperCase());
            URI uri = URI.create(request.getPath());
            Headers headers = request.getHeaders();
            HttpQueryParams params = request.getQueryParams();

            HttpRequest.Builder builder = HttpRequest.newBuilder().
                    verb(verb).
                    uri(uri);

            // Add X-Forwarded headers if not already there.
            ProxyUtils.addXForwardedHeaders(request);

            // Request headers.
            for (Header entry : headers.entries()) {
                if (ProxyUtils.isValidRequestHeader(entry.getName())) {
                    builder.header(entry.getKey(), entry.getValue());
                }
            }

            // Request query params.
            for (Map.Entry<String, String> entry : params.entries()) {
                builder.queryParams(entry.getKey(), entry.getValue());
            }

            // Request body.
            HttpRequest httpClientRequest;
            ByteBuf body = request.content();
            if (body != null) {
                builder.entity(body);
            }
            httpClientRequest = builder.build();
            context.getTimings().getRequestBodyRead().end();
            
            // Execute the request.
            final Timing timing = context.getTimings().getRequestProxy();
            timing.start();
            HttpResponse ribbonResp;
            try {
                ribbonResp = client.executeWithLoadBalancer(httpClientRequest);

                // Store the ribbon response on context, so that code can later get access
                // to it to release the resources.
                context.set("_ribbonResp", ribbonResp);

                HttpResponseMessage respMsg = createHttpResponseMessage(ribbonResp, request, callback);
                promise.setSuccess(respMsg);
            }
            catch (ClientException e) {
                promise.setFailure(proxyError(request, e, e.getErrorType().toString()));
            }
            catch(Exception e) {
                promise.setFailure(proxyError(request, e, null));
            }
            finally {
                timing.end();
            }
        }
    }

    static class RibbonPromise extends DefaultPromise<com.netflix.zuul.message.http.HttpResponseMessage>
    {
        RibbonPromise() {
            super();
        }

        @Override
        protected void checkDeadLock() {
            // No check
        }
    }


    @RunWith(MockitoJUnitRunner.class)
    public static class TestUnit
    {
        @Mock
        HttpResponse proxyResp;
        @Mock
        HttpRequestMessage request;
        @Mock
        FilterComplete callback;
        
        @Test
        public void testCreateHttpResponseMessage() throws Exception
        {
            RibbonOrigin origin = new RibbonOrigin("blah");
            origin = Mockito.spy(origin);

            CaseInsensitiveMultiMap headers = new CaseInsensitiveMultiMap();
            headers.addHeader("test", "test");
            headers.addHeader("content-length", "100");

            byte[] body = "test-body".getBytes("UTF-8");
            InputStream inp = new ByteArrayInputStream(body);

            Mockito.when(proxyResp.getStatus()).thenReturn(200);
            Mockito.when(proxyResp.getInputStream()).thenReturn(inp);
            Mockito.when(proxyResp.hasEntity()).thenReturn(true);
            Mockito.when(proxyResp.getHttpHeaders()).thenReturn(headers);

            HttpResponseMessage response = origin.createHttpResponseMessage(proxyResp, request, callback);

            Assert.assertEquals(200, response.getStatus());
            assertTrue(response.getHeaders().contains("test", "test"));

            ArgumentCaptor<MessageComponent> argument = ArgumentCaptor.forClass(MessageComponent.class);
            Mockito.verify(callback).invoke(argument.capture());
            
            assertTrue(argument.getValue() instanceof MessageContent);
            MessageContent messageContent = (MessageContent) argument.getValue();
            
            byte[] respBodyBytes = ByteBufUtils.toBytes(messageContent.content());
            Assert.assertNotNull(respBodyBytes);
            Assert.assertEquals(body.length, respBodyBytes.length);

            
        }
    }
}
