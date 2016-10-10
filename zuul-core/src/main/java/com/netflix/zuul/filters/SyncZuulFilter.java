package com.netflix.zuul.filters;

import com.netflix.zuul.message.MessageComponent;

/**
 * User: michaels@netflix.com
 * Date: 11/16/15
 * Time: 2:07 PM
 */
public interface SyncZuulFilter<I extends MessageComponent, O extends MessageComponent> extends ZuulFilter<I, O>
{
    /**
     * If shouldFilter() is true, this method will be invoked. 
     * This is the core method of a ZuulFilter.
     */
    O apply(I input);
}
