package com.netflix.zuul.origins;

import com.netflix.zuul.filters.FilterComplete;
import com.netflix.zuul.message.MessageComponent;
import com.netflix.zuul.message.MessageContent;
import com.netflix.zuul.message.http.HttpResponseMessage;
import io.netty.util.concurrent.Promise;

/**
 * User: Mike Smith
 * Date: 10/9/16
 * Time: 2:14 PM
 */
public interface OriginRequest
{
    Promise<HttpResponseMessage> promise();
    void writeContent(MessageContent messageContent, FilterComplete<MessageComponent> callback);
}
