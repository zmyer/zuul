package com.netflix.zuul.message;

import com.netflix.zuul.context.SessionContext;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * User: Mike Smith
 * Date: 10/9/16
 * Time: 12:00 PM
 */
public class MessageContentImpl implements MessageContent
{
    private final ZuulMessage message;
    private final ByteBuf content;
    private final boolean last;

    public MessageContentImpl(ZuulMessage message, ByteBuf content, boolean last)
    {
        this.message = message;
        this.content = content;
        this.last = last;
    }
    
    public static MessageContent createEmptyLastContent(ZuulMessage msg)
    {
        return new MessageContentImpl(msg, Unpooled.EMPTY_BUFFER, true);
    }

    @Override
    public SessionContext getContext()
    {
        return message.getContext();
    }

    @Override
    public ZuulMessage message()
    {
        return message;
    }

    @Override
    public ByteBuf content()
    {
        return content;
    }

    @Override
    public boolean isLast()
    {
        return last;
    }
}
