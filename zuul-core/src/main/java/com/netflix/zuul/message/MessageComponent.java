package com.netflix.zuul.message;

import com.netflix.zuul.context.SessionContext;

/**
 * User: Mike Smith
 * Date: 10/9/16
 * Time: 12:51 PM
 */
public interface MessageComponent
{
    SessionContext getContext();
}
