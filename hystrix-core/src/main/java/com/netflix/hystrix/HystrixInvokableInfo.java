/**
 * Copyright 2014 Netflix, Inc.
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
package com.netflix.hystrix;

import java.util.List;

/**
 * 该接口提供大量的get类型的方法获取各种各样的值，而这些方法所需要的信息大多在执行结果ExecutionResult这个POJO里。
 * @param <R>
 */
public interface HystrixInvokableInfo<R> {

    // 获取各种key：
    // CommandKey：命令的id
    // CommandGroupKey：逻辑分组的key
    // ThreadPoolKey：线程池分组的key。不指定默认使用CommandGroupKey的值
    // CollapserKey：合并的id
    HystrixCommandGroupKey getCommandGroup();

    HystrixCommandKey getCommandKey();

    HystrixThreadPoolKey getThreadPoolKey();

    // 内部实际调用protected的AbstractCommand#getCacheKey()方法
    // 就是把这个方法public掉的作用
    String getPublicCacheKey(); //have to use public in the name, as there's already a protected {@link AbstractCommand#getCacheKey()} method.

    HystrixCollapserKey getOriginatingCollapserKey();
    // 获取command的指标信息（该类前面已经重点介绍过了）
    HystrixCommandMetrics getMetrics();
    // HystrixCommand的配置信息：非常多的属性可定制
    // 实现：init初始化，一般来源于SPI机制
    HystrixCommandProperties getProperties();

    // 断路器是否打开：由HystrixCommandProperties配置以及circuitBreaker.isOpen()共同决定
    boolean isCircuitBreakerOpen();

    // 执行是否完成：commandState命令状态是否是TERMINAL状态：
    boolean isExecutionComplete();
    // 是否在隔离线程里执行的任务
    // ExecutionResult#isExecutedInThread()
    boolean isExecutedInThread();
    // 是否执行成功。
    // ExecutionResult#getEventCounts().contains(HystrixEventType.SUCCESS)
    boolean isSuccessfulExecution();
    // 实现：ExecutionResult#getEventCounts().contains(HystrixEventType.FAILURE)
    boolean isFailedExecution();
    // 若失败了，获取其异常信息
    // 实现：executionResult.getException()
    Throwable getFailedExecutionException();
    // 是否是fallback了
    // 实现：ExecutionResult#getEventCounts().contains(HystrixEventType.FALLBACK_SUCCESS)
    boolean isResponseFromFallback();
    // 实现：ExecutionResult#getEventCounts().contains(HystrixEventType.TIMEOUT)
    boolean isResponseTimedOut();
    // short-circuited：短路
    // isCircuitBreakerOpen() == true并且结果来自于fallabck，这就叫直接短路了（根本不走你目标方法）
    // 实现：ExecutionResult#getEventCounts().contains(HystrixEventType.SHORT_CIRCUITED
    boolean isResponseShortCircuited();
    // 结果是否来自于缓存。若true，那么run()将不会被执行
    boolean isResponseFromCache();
    // 拒绝：响应是否是被拒绝后的回退（请注意：没有执行run方法）
    // 包括线程池拒绝和信号量拒绝
    // 实现：ExecutionResult#isResponseRejected()
    boolean isResponseRejected();
    boolean isResponseSemaphoreRejected();
    boolean isResponseThreadPoolRejected();

    // 执行过程中，所有被记录过的事件们（没被记录过的就木有）
    // 实现ExecutionResult#getOrderedList()
    List<HystrixEventType> getExecutionEvents();

    // 命令执行过程中的发射次数。
    // ExecutionResult#getEventCounts().getCount(HystrixEventType.EMIT);
    int getNumberEmissions();
    int getNumberFallbackEmissions();
    int getNumberCollapsed();
    // 此command实例的执行时间(以毫秒为单位)，如果未执行，则为-1。
    // 实现：ExecutionResult#getExecutionLatency
    int getExecutionTimeInMilliseconds();
    // 调用该command实例的run方法的时刻纳秒，如果没有执行，则为-1
    // 实现：ExecutionResult#getCommandRunStartTimeInNanos
    long getCommandRunStartTimeInNanos();
    // EventCounts对象是对事件类型的的一个计数、统计
    // 实现ExecutionResult#getEventCounts()
    ExecutionResult.EventCounts getEventCounts();
}