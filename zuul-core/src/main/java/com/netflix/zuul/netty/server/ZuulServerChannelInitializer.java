/*
 * Copyright 2018 Netflix, Inc.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 */

package com.netflix.zuul.netty.server;

import com.netflix.netty.common.channel.config.ChannelConfig;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.group.ChannelGroup;

/**
 * User: Mike Smith
 * Date: 3/5/16
 * Time: 6:44 PM
 */
// TODO: 2018/7/2 by zmyer
public class ZuulServerChannelInitializer extends BaseZuulChannelInitializer {

    // TODO: 2018/7/4 by zmyer
    public ZuulServerChannelInitializer(int port,
            ChannelConfig channelConfig,
            ChannelConfig channelDependencies,
            ChannelGroup channels) {
        super(port, channelConfig, channelDependencies, channels);
    }

    // TODO: 2018/7/4 by zmyer
    @Override
    protected void initChannel(Channel ch) throws Exception {
        // Configure our pipeline of ChannelHandlerS.
        ChannelPipeline pipeline = ch.pipeline();

        storeChannel(ch);
        addTimeoutHandlers(pipeline);
        addPassportHandler(pipeline);
        addTcpRelatedHandlers(pipeline);
        addHttp1Handlers(pipeline);
        addHttpRelatedHandlers(pipeline);
        addZuulHandlers(pipeline);
    }
}
