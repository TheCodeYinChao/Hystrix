/**
 * Copyright 2012 Netflix, Inc.
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

import java.util.concurrent.Future;

import rx.Observable;
import rx.schedulers.Schedulers;

import com.netflix.hystrix.HystrixCommandProperties.ExecutionIsolationStrategy;
import com.netflix.hystrix.exception.HystrixBadRequestException;
import com.netflix.hystrix.exception.HystrixRuntimeException;

/**
 * Common interface for executables ({@link HystrixCommand} and {@link HystrixCollapser}) so client code can treat them the same and combine in typed collections if desired.
 * 
 * @param <R>
 */
public interface HystrixExecutable<R> extends HystrixInvokable<R> {

    /**
     * Used for synchronous execution of command.
     * // 同步执行，原理是：queue().get() 所以效果是同步的
     * @return R
     *         Result of {@link HystrixCommand} execution
     * @throws HystrixRuntimeException
     *             if an error occurs and a fallback cannot be retrieved
     * @throws HystrixBadRequestException
     *             if the {@link HystrixCommand} instance considers request arguments to be invalid and needs to throw an error that does not represent a system failure
     */
    public R execute();

    /**
     * Used for asynchronous execution of command.
     * public R execute();
     * 	// 异步执行，什么时候get由调用者决定。get的时候才会阻塞：获取结果或者错误
     * 	// 其实queue的原理是toObservable().toBlocking().toFuture()
     * <p>
     * This will queue up the command on the thread pool and return an {@link Future} to get the result once it completes.
     * <p>
     * NOTE: If configured to not run in a separate thread, this will have the same effect as {@link #execute()} and will block.
     * <p>
     * We don't throw an exception in that case but just flip to synchronous execution so code doesn't need to change in order to switch a circuit from running a separate thread to the calling thread.
     * 
     * @return {@code Future<R>} Result of {@link HystrixCommand} execution
     * @throws HystrixRuntimeException
     *             if an error occurs and a fallback cannot be retrieved
     * @throws HystrixBadRequestException
     *             if the {@link HystrixCommand} instance considers request arguments to be invalid and needs to throw an error that does not represent a system failure
     */
    public Future<R> queue();

    /**
     * Used for asynchronous execution of command with a callback by subscribing to the {@link Observable}.
     * <p>
     * This eagerly starts execution of the command the same as {@link #queue()} and {@link #execute()}.
     * A lazy {@link Observable} can be obtained from {@link HystrixCommand#toObservable()} or {@link HystrixCollapser#toObservable()}.
     * <p>
     * <b>Callback Scheduling</b>
     * <p>
     * <ul>
     * <li>When using {@link ExecutionIsolationStrategy#THREAD} this defaults to using {@link Schedulers#computation()} for callbacks.</li>
     * <li>When using {@link ExecutionIsolationStrategy#SEMAPHORE} this defaults to using {@link Schedulers#immediate()} for callbacks.</li>
     * </ul>
     * <p>
     * See https://github.com/Netflix/RxJava/wiki for more information.
     *
     * @return {@code Observable<R>} that executes and calls back with the result of the command execution or a fallback if the command fails for any reason.
     * @throws HystrixRuntimeException
     *             if a fallback does not exist
     *             <p>
     *             <ul>
     *             <li>via {@code Observer#onError} if a failure occurs</li>
     *             <li>or immediately if the command can not be queued (such as short-circuited, thread-pool/semaphore rejected)</li>
     *             </ul>
     * @throws HystrixBadRequestException
     *             via {@code Observer#onError} if invalid arguments or state were used representing a user failure, not a system failure
     * @throws IllegalStateException
     *             if invoked more than once
     * 	// 异步执行。toObservable().subscribe(subject)  subject=ReplaySubject
     * 	// 原理也是基于toObservable()，但是它是立马执行，且有回放的能力
     */
    public Observable<R> observe();

}
