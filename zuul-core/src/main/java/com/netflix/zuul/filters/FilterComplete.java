package com.netflix.zuul.filters;


import com.netflix.zuul.message.MessageComponent;

/**
 * User: Mike Smith
 * Date: 10/9/16
 * Time: 12:43 PM
 */
public interface FilterComplete<T extends MessageComponent>
{
    /**
     * To be called by an async filter once it has finished processing
     * 
     * @param msg the output MessageComponent from the filter 
     */
    void invoke(T msg);
}
