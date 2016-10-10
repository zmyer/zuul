package com.netflix.zuul.filters;

import com.netflix.zuul.message.MessageComponent;

/**
 * User: Mike Smith
 * Date: 10/9/16
 * Time: 1:03 PM
 */
public interface AsyncZuulFilter<I extends MessageComponent, O extends MessageComponent> extends ZuulFilter<I, O>
{
    /**
     * If shouldFilter() is true, this method will be invoked.
     * 
     * Implementors should invoke the passed callback when processing is
     * complete.
     * 
     * @param input
     * @param callback
     */
    void applyAsync(I input, FilterComplete<O> callback);
}
