/*
 *
 *
 *  Copyright 2013-2015 Netflix, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * /
 */

package com.netflix.zuul.message;

import com.netflix.zuul.context.SessionContext;
import io.netty.buffer.ByteBuf;
import rx.Observable;

import java.nio.charset.Charset;

/**
 * User: Mike Smith
 * Date: 7/16/15
 * Time: 12:22 AM
 */
public interface ZuulMessage extends MessageComponent, Content, Cloneable
{
    SessionContext getContext();

    Headers getHeaders();

    void setHeaders(Headers newHeaders);
    
    byte[] getBody();
    
    void setBody(byte[] body);
    
    boolean hasBody();
    
    void setBodyAsText(String bodyText, Charset cs);
    
    void setBodyAsText(String bodyText);
    
    int getMaxBodySize();
    
    boolean isBodyBuffered();
    
    /** Add another bytebuf to the content so far aggregated. */
    void addContent(ByteBuf bb);
    
    /** Get a ByteBuf representing the so far aggregated body content. */
    ByteBuf content();

    ZuulMessage clone();

    String getInfoForLogging();
}
