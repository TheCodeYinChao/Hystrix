/**
 * Copyright 2015 Netflix, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.metric;

import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixThreadPoolKey;
import rx.functions.Func1;

/**
 * Data class that comprises the event stream for Hystrix command executions.
 * As of 1.5.0-RC1, this is only {@link HystrixCommandCompletion}s.
 */
public abstract class HystrixCommandEvent implements HystrixEvent {
    private final HystrixCommandKey commandKey;
    private final HystrixThreadPoolKey threadPoolKey;

    protected HystrixCommandEvent(HystrixCommandKey commandKey, HystrixThreadPoolKey threadPoolKey) {
        this.commandKey = commandKey;
        this.threadPoolKey = threadPoolKey;
    }

    public HystrixCommandKey getCommandKey() {
        return commandKey;
    }

    public HystrixThreadPoolKey getThreadPoolKey() {
        return threadPoolKey;
    }

    public abstract boolean isExecutionStart();// command是否开始

    public abstract boolean isExecutedInThread();//是否是在隔离线程里执行的（先线程池模式隔离）

    public abstract boolean isResponseThreadPoolRejected(); // 响应被线程池拒绝

    public abstract boolean isCommandCompletion(); // command是否执行完成（包括成功、错误等）

    public abstract boolean didCommandExecute();  // command命令是否执行过
    // 提供两个public的功能函数，过滤
    // 过滤只需要已经完成了的HystrixCommandEvent数据
    // 过滤只需要执行过的数据
    public static final Func1<HystrixCommandEvent, Boolean> filterCompletionsOnly = new Func1<HystrixCommandEvent, Boolean>() {
        @Override
        public Boolean call(HystrixCommandEvent commandEvent) {
            return commandEvent.isCommandCompletion();
        }
    };

    public static final Func1<HystrixCommandEvent, Boolean> filterActualExecutions = new Func1<HystrixCommandEvent, Boolean>() {
        @Override
        public Boolean call(HystrixCommandEvent commandEvent) {
            return commandEvent.didCommandExecute();
        }
    };
}
