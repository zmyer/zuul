package com.netflix.zuul.message;

/**
 * User: Mike Smith
 * Date: 10/9/16
 * Time: 11:59 AM
 */
public interface MessageContent extends Content, MessageComponent
{
    ZuulMessage message();
}
