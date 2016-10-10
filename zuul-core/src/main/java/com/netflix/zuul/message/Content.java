package com.netflix.zuul.message;

import io.netty.buffer.ByteBuf;

/**
 * User: Mike Smith
 * Date: 10/9/16
 * Time: 12:36 PM
 */
public interface Content
{
    ByteBuf content();
    boolean isLast();
}
