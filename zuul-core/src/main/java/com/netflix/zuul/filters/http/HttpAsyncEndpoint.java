package com.netflix.zuul.filters.http;

import com.netflix.zuul.filters.Endpoint;
import com.netflix.zuul.message.MessageComponent;
import com.netflix.zuul.message.MessageContent;
import com.netflix.zuul.message.http.HttpRequestMessage;
import com.netflix.zuul.message.http.HttpResponseMessage;
import com.netflix.zuul.message.http.HttpResponseMessageImpl;

/**
 * User: Mike Smith
 * Date: 11/11/15
 * Time: 10:36 PM
 */
public abstract class HttpAsyncEndpoint extends Endpoint
{
    @Override
    public HttpResponseMessage getDefaultOutput(MessageComponent component)
    {
        HttpRequestMessage request;
        if (component instanceof HttpRequestMessage) {
            request = (HttpRequestMessage) component;
        }
        else if (component instanceof MessageContent) {
            request = (HttpRequestMessage) ((MessageContent) component).message();
        }
        else {
            throw new IllegalArgumentException("Unsupported MessageComponent type - " + String.valueOf(component));
        }
        
        return HttpResponseMessageImpl.defaultErrorResponse(request);
    }
}
