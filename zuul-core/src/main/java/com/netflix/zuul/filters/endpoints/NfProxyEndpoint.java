package com.netflix.zuul.filters.endpoints;

import com.netflix.zuul.context.Debug;
import com.netflix.zuul.context.SessionContext;
import com.netflix.zuul.exception.ZuulException;
import com.netflix.zuul.filters.FilterComplete;
import com.netflix.zuul.filters.http.HttpAsyncEndpoint;
import com.netflix.zuul.message.MessageComponent;
import com.netflix.zuul.message.MessageContent;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.message.http.HttpResponseMessageImpl;
import com.netflix.zuul.monitoring.MonitoringHelper;
import com.netflix.zuul.origins.Origin;
import com.netflix.zuul.origins.OriginManager;
import com.netflix.zuul.origins.OriginRequest;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Promise;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

/**
 * General-purpose Proxy endpoint implementation with both async and sync/blocking methods.
 * <p>
 * You can probably just subclass this in your project, and use as-is.
 * <p>
 * User: michaels@netflix.com
 * Date: 5/22/15
 * Time: 1:42 PM
 */
public class NfProxyEndpoint extends HttpAsyncEndpoint
{
    @Override
    public void applyAsync(MessageComponent component, FilterComplete callback)
    {
        if (component instanceof HttpRequestMessage) {
            applyForHttpRequest((HttpRequestMessage) component, callback);
        }
        else if (component instanceof MessageContent) {
            applyForContent((MessageContent) component, callback);
        }
        else {
            throw new IllegalArgumentException("Unsupported type of MessageComponent! " + String.valueOf(component));
        }
    }
    
    protected void applyForHttpRequest(HttpRequestMessage request, FilterComplete callback)
    {
        SessionContext context = request.getContext();
        Debug.writeDebugRequest(context, request, false);

        // Get the Origin.
        Origin origin = getOrigin(request);

        // Start making the request.
        OriginRequest originRequest = origin.request(request);
        context.set("origin_request", originRequest);
        
        Promise<HttpResponseMessage> promise = originRequest.promise();
        if (promise.isDone()) {
            handleCompletion(context, request, callback, promise);
        }
        else {
            promise.addListener(future -> {
                handleCompletion(context, request, callback, promise);
            });
        }
    }
    
    protected void applyForContent(MessageContent messageContent, FilterComplete callback)
    {
        OriginRequest originRequest = (OriginRequest) messageContent.message().getContext().get("origin_request");
        if (originRequest == null) {
            throw new IllegalStateException("Received MessageContent to proxy, but no OriginRequest has been setup yet!");
        }
        
        originRequest.writeContent(messageContent, callback);
    }
    
    protected void handleCompletion(SessionContext context, 
                                    HttpRequestMessage request, 
                                    FilterComplete<HttpResponseMessage> callback,
                                    Promise<HttpResponseMessage> promise)
    {
        if (promise.isSuccess()) {
            HttpResponseMessage response;
            try {
                response = promise.get();
            }
            catch(Exception e) {
                context.setError(promise.cause());
                HttpResponseMessageImpl.defaultErrorResponse(request);
                return;
            }
            
            context.put("origin_http_status", Integer.toString(response.getStatus()));
            Debug.writeDebugResponse(context, response, true);
            callback.invoke(response);
        }
        else {
            context.setError(promise.cause());
            HttpResponseMessageImpl.defaultErrorResponse(request);
        }
    }

    protected Origin getOrigin(HttpRequestMessage request)
    {
        final String name = request.getContext().getRouteVIP();
        OriginManager originManager = (OriginManager) request.getContext().get("origin_manager");
        Origin origin = originManager.getOrigin(name);
        if (origin == null) {
            throw new ZuulException("No Origin registered for name=" + name + "!", "UNKNOWN_VIP");
        }

        return origin;
    }

    @RunWith(MockitoJUnitRunner.class)
    public static class TestUnit
    {
        @Mock
        private OriginManager originManager;
        @Mock
        private Origin origin;
        @Mock
        private OriginRequest originRequest;
        @Mock
        private HttpRequestMessage request;
        private NfProxyEndpoint filter;
        private SessionContext ctx;
        private HttpResponseMessage response;

        @Before
        public void setup()
        {
            MonitoringHelper.initMocks();
            filter = new NfProxyEndpoint();
            ctx = new SessionContext();
            when(request.getContext()).thenReturn(ctx);
            response = new HttpResponseMessageImpl(ctx, request, 202);
            
            Promise<HttpResponseMessage> promise = new MockResponsePromise(response);
            when(originRequest.promise()).thenReturn(promise);

            when(origin.request(request)).thenReturn(originRequest);

            when(originManager.getOrigin("an-origin")).thenReturn(origin);
            ctx.put("origin_manager", originManager);
        }

        @Test
        public void testApplyAsync()
        {
            ctx.setRouteVIP("an-origin");

            filter.applyAsync(request, msg -> {
                if (msg instanceof HttpResponseMessage) {
                    HttpResponseMessage resp = (HttpResponseMessage) msg; 
                    assertEquals(202, resp.getStatus());
                    assertEquals("202", ctx.get("origin_http_status"));
                }
            });
        }

        @Test
        public void testApply_NoOrigin()
        {
            ctx.setRouteVIP("a-different-origin");

            filter.applyAsync(request, msg -> {
                assertNotNull(msg.getContext().getError());
            });
        }
    }
    
    static class MockResponsePromise extends DefaultPromise<HttpResponseMessage>
    {
        private final HttpResponseMessage response;
        
        MockResponsePromise(HttpResponseMessage response) {
            super();
            this.response = response;
        }

        @Override
        protected void checkDeadLock() {
            // No check
        }

        @Override
        public HttpResponseMessage get() throws InterruptedException, ExecutionException
        {
            return response;
        }
    }
}
