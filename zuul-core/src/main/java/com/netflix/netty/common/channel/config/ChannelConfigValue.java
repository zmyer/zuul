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

package com.netflix.netty.common.channel.config;

/**
 * User: michaels@netflix.com
 * Date: 2/8/17
 * Time: 6:41 PM
 */
// TODO: 2018/7/2 by zmyer
public class ChannelConfigValue<T> {
    private final ChannelConfigKey<T> key;
    private final T value;

    public ChannelConfigValue(ChannelConfigKey<T> key, T value) {
        this.key = key;
        this.value = value;
    }

    public ChannelConfigKey<T> key() {
        return key;
    }

    public T value() {
        return value;
    }
}
