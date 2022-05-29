/**
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Immutable holder class for the status of command execution.
 * <p>
 * This object can be referenced and "modified" by parent and child threads as well as by different instances of HystrixCommand since
 * 1 instance could create an ExecutionResult, cache a Future that refers to it, a 2nd instance execution then retrieves a Future
 * from cache and wants to append RESPONSE_FROM_CACHE to whatever the ExecutionResult was from the first command execution.
 * <p>
 * This being immutable forces and ensure thread-safety instead of using AtomicInteger/ConcurrentLinkedQueue and determining
 * when it's safe to mutate the object directly versus needing to deep-copy clone to a new instance.
 * 它代表执行的结果，是一个Immutable不可变对象。
 */
public class ExecutionResult {
    private final EventCounts eventCounts;//它是一个POJO，维护着如下字段/ 事件计数器
    // 执行时异常。通过setException设置进来值
    // 唯一放置地：`AbstractCommand#handleFailureViaFallback`
    private final Exception failedExecutionException;
    // 通过`setExecutionException`放进来。只要是执行失败（线程池拒绝，timeout等等都会设置）
    private final Exception executionException;//在检查fallabck之前发出的异常（也就是执行run时就抛出异常了）。当是timeout/short-circuit/rejection/bad request等情况时，值同上
    // 准备执行目标方法的时候，标记一下时刻
    private final long startTimestamp;//命令开始执行的时刻
    private final int executionLatency; //time spent in run() method
    private final int userThreadLatency; //使用线程方式提交任务到得到resposne之间的时间间隔time elapsed between caller thread submitting request and response being visible to it
    private final boolean executionOccurred;//是否执行 // 只要目标方法执行了就标记为true（不管成功or失败）
    private final boolean isExecutedInThread;//是否在线程隔离里执行的
    private final HystrixCollapserKey collapserKey;

    private static final HystrixEventType[] ALL_EVENT_TYPES = HystrixEventType.values();
    private static final int NUM_EVENT_TYPES = ALL_EVENT_TYPES.length;
    // EXCEPTION_PRODUCING_EVENTS和TERMINAL_EVENTS对事件类型进行了归类
    private static final BitSet EXCEPTION_PRODUCING_EVENTS = new BitSet(NUM_EVENT_TYPES);
    private static final BitSet TERMINAL_EVENTS = new BitSet(NUM_EVENT_TYPES);

    static {
        for (HystrixEventType eventType: HystrixEventType.EXCEPTION_PRODUCING_EVENT_TYPES) {
            EXCEPTION_PRODUCING_EVENTS.set(eventType.ordinal());
        }

        for (HystrixEventType eventType: HystrixEventType.TERMINAL_EVENT_TYPES) {
            TERMINAL_EVENTS.set(eventType.ordinal());
        }
    }

    public static class EventCounts {
        /**
         * BitSet events：HystrixEventType的所有可能值
         * int numEmissions：发射数。发送HystrixEventType.EMIT事件时，+1
         * int numFallbackEmissions：降级的发射数。发送FALLBACK_EMIT事件时，+1
         * int numCollapsed：合并数。发送HystrixEventType.COLLAPSED事件时，+1
         */

        private final BitSet events;
        private final int numEmissions;
        private final int numFallbackEmissions;
        private final int numCollapsed;

        EventCounts() {
            this.events = new BitSet(NUM_EVENT_TYPES);
            this.numEmissions = 0;
            this.numFallbackEmissions = 0;
            this.numCollapsed = 0;
        }

        EventCounts(BitSet events, int numEmissions, int numFallbackEmissions, int numCollapsed) {
            this.events = events;
            this.numEmissions = numEmissions;
            this.numFallbackEmissions = numFallbackEmissions;
            this.numCollapsed = numCollapsed;
        }

        EventCounts(HystrixEventType... eventTypes) {
            BitSet newBitSet = new BitSet(NUM_EVENT_TYPES);
            int localNumEmits = 0;
            int localNumFallbackEmits = 0;
            int localNumCollapsed = 0;
            for (HystrixEventType eventType: eventTypes) {
                switch (eventType) {
                    case EMIT:
                        newBitSet.set(HystrixEventType.EMIT.ordinal());
                        localNumEmits++;
                        break;
                    case FALLBACK_EMIT:
                        newBitSet.set(HystrixEventType.FALLBACK_EMIT.ordinal());
                        localNumFallbackEmits++;
                        break;
                    case COLLAPSED:
                        newBitSet.set(HystrixEventType.COLLAPSED.ordinal());
                        localNumCollapsed++;
                        break;
                    default:
                        //也就是说，只有这三种事件可被识别，对于其它类型的事件，一律执行newBitSet.set(eventType.ordinal());，也就说仅仅只记录值关心此事件发生过与否，而并不关心触发次数。
                        newBitSet.set(eventType.ordinal());
                        break;
                }
            }
            this.events = newBitSet;
            this.numEmissions = localNumEmits;
            this.numFallbackEmissions = localNumFallbackEmits;
            this.numCollapsed = localNumCollapsed;
        }
        /**
         *这两个方法是事件计数方法，但是非public，仅ExecutionResult内会有调用。事件次数记录下来后，下面便是提供的public访问方法：
         */


        // 为指定事件增加一次计数
        EventCounts plus(HystrixEventType eventType) {
            return plus(eventType, 1);
        }
        // 增加指定次数（一次性可增加N次）
        EventCounts plus(HystrixEventType eventType, int count) {
            BitSet newBitSet = (BitSet) events.clone();
            int localNumEmits = numEmissions;
            int localNumFallbackEmits =  numFallbackEmissions;
            int localNumCollapsed = numCollapsed;
            switch (eventType) {
                case EMIT:
                    newBitSet.set(HystrixEventType.EMIT.ordinal());
                    localNumEmits += count;
                    break;
                case FALLBACK_EMIT:
                    newBitSet.set(HystrixEventType.FALLBACK_EMIT.ordinal());
                    localNumFallbackEmits += count;
                    break;
                case COLLAPSED:
                    newBitSet.set(HystrixEventType.COLLAPSED.ordinal());
                    localNumCollapsed += count;
                    break;
                default:
                    newBitSet.set(eventType.ordinal());
                    break;
            }
            return new EventCounts(newBitSet, localNumEmits, localNumFallbackEmits, localNumCollapsed);
        }

        // 用于判断：事件计数器里是否记录有此事件（非常重要）
        // isSuccessfulExecution -> 看是否记录SUCCESS事件
        // isFailedExecution -> 看是否记录了FAILURE事件
        // isResponseFromFallback -> 看是否记录了FALLBACK_SUCCESS事件
        // isResponseTimedOut -> 看是否记录了TIMEOUT事件
        // isResponseShortCircuited -> 看是否记录了SHORT_CIRCUITED事件
        public boolean contains(HystrixEventType eventType) {
            return events.get(eventType.ordinal());
        }

        // 只要包含other里的任何一个事件类型，就会返回true
        // 使用：比如异常类型(BAD_REQUEST/FALLBACK_FAILURE)只要有一种就算异常状态呗
        // 还有TERMINAL类型，如SUCCESS/BAD_REQUEST/FALLBACK_SUCCESS/FALLBACK_FAILURE均属于结束类型
        public boolean containsAnyOf(BitSet other) {
            return events.intersects(other);
        }
        // 拿到指定事件类型的**次数**

        /**
         * 针对getCount()方法，做如下补充说明：
         *
         * 若是可识别的三种类型，直接返回统计数字即可
         * 此处唯独对EXCEPTION_THROWN异常类型记数做了分类处理：BAD_REQUEST、FALLBACK_FAILURE、FALLBACK_MISSING、FALLBACK_REJECTION四种类型均数据异常抛出类型。为嘛自己这种类型不算？是因为该种类型系统不会发射出来，所以没必要算作里面~
         * 其它类型：有就是1，没有就是0
         * 这就是事件计数器EventCounts的所有内容。它对公并未曝露public的事件记录方法，这种动作是被ExecutionResult所代劳。
         * @param eventType
         * @return
         */
        public int getCount(HystrixEventType eventType) {
            switch (eventType) {
                case EMIT: return numEmissions;
                case FALLBACK_EMIT: return numFallbackEmissions;
                case EXCEPTION_THROWN: return containsAnyOf(EXCEPTION_PRODUCING_EVENTS) ? 1 : 0;
                case COLLAPSED: return numCollapsed;
                default: return contains(eventType) ? 1 : 0;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            EventCounts that = (EventCounts) o;

            if (numEmissions != that.numEmissions) return false;
            if (numFallbackEmissions != that.numFallbackEmissions) return false;
            if (numCollapsed != that.numCollapsed) return false;
            return events.equals(that.events);

        }

        @Override
        public int hashCode() {
            int result = events.hashCode();
            result = 31 * result + numEmissions;
            result = 31 * result + numFallbackEmissions;
            result = 31 * result + numCollapsed;
            return result;
        }

        @Override
        public String toString() {
            return "EventCounts{" +
                    "events=" + events +
                    ", numEmissions=" + numEmissions +
                    ", numFallbackEmissions=" + numFallbackEmissions +
                    ", numCollapsed=" + numCollapsed +
                    '}';
        }
    }

    private ExecutionResult(EventCounts eventCounts, long startTimestamp, int executionLatency,
                            int userThreadLatency, Exception failedExecutionException, Exception executionException,
                            boolean executionOccurred, boolean isExecutedInThread, HystrixCollapserKey collapserKey) {
        this.eventCounts = eventCounts;
        this.startTimestamp = startTimestamp;
        this.executionLatency = executionLatency;
        this.userThreadLatency = userThreadLatency;
        this.failedExecutionException = failedExecutionException;
        this.executionException = executionException;
        this.executionOccurred = executionOccurred;
        this.isExecutedInThread = isExecutedInThread;
        this.collapserKey = collapserKey;
    }

    // we can return a static version since it's immutable
    static ExecutionResult EMPTY = ExecutionResult.from();

    // 内部调用new EventCounts(eventTypes)用于记录事件（对应事件次数+1）
    public static ExecutionResult from(HystrixEventType... eventTypes) {
        boolean didExecutionOccur = false;
        for (HystrixEventType eventType: eventTypes) {
            if (didExecutionOccur(eventType)) {
                didExecutionOccur = true;
            }
        }
        return new ExecutionResult(new EventCounts(eventTypes), -1L, -1, -1, null, null, didExecutionOccur, false, null);
    }

    private static boolean didExecutionOccur(HystrixEventType eventType) {
        switch (eventType) {
            case SUCCESS: return true;
            case FAILURE: return true;
            case BAD_REQUEST: return true;
            case TIMEOUT: return true;
            case CANCELLED: return true;
            default: return false;
        }
    }
    // ===各种set方法：每个set方法都返回一个新的ExecutionResult 实例===
    public ExecutionResult setExecutionOccurred() {
        return new ExecutionResult(eventCounts, startTimestamp, executionLatency, userThreadLatency,
                failedExecutionException, executionException, true, isExecutedInThread, collapserKey);
    }

    public ExecutionResult setExecutionLatency(int executionLatency) {
        return new ExecutionResult(eventCounts, startTimestamp, executionLatency, userThreadLatency,
                failedExecutionException, executionException, executionOccurred, isExecutedInThread, collapserKey);
    }

    public ExecutionResult setException(Exception e) {
        return new ExecutionResult(eventCounts, startTimestamp, executionLatency, userThreadLatency, e,
                executionException, executionOccurred, isExecutedInThread, collapserKey);
    }

    public ExecutionResult setExecutionException(Exception executionException) {
        return new ExecutionResult(eventCounts, startTimestamp, executionLatency, userThreadLatency,
                failedExecutionException, executionException, executionOccurred, isExecutedInThread, collapserKey);
    }

    public ExecutionResult setInvocationStartTime(long startTimestamp) {
        return new ExecutionResult(eventCounts, startTimestamp, executionLatency, userThreadLatency,
                failedExecutionException, executionException, executionOccurred, isExecutedInThread, collapserKey);
    }

    public ExecutionResult setExecutedInThread() {
        return new ExecutionResult(eventCounts, startTimestamp, executionLatency, userThreadLatency,
                failedExecutionException, executionException, executionOccurred, true, collapserKey);
    }

    public ExecutionResult setNotExecutedInThread() {
        return new ExecutionResult(eventCounts, startTimestamp, executionLatency, userThreadLatency,
                failedExecutionException, executionException, executionOccurred, false, collapserKey);
    }
    // 注意：这方法里写死的事件类型：COLLAPSED

    public ExecutionResult markCollapsed(HystrixCollapserKey collapserKey, int sizeOfBatch) {
        return new ExecutionResult(eventCounts.plus(HystrixEventType.COLLAPSED, sizeOfBatch), startTimestamp, executionLatency, userThreadLatency,
                failedExecutionException, executionException, executionOccurred, isExecutedInThread, collapserKey);
    }

    public ExecutionResult markUserThreadCompletion(long userThreadLatency) {
        if (startTimestamp > 0 && !isResponseRejected()) {
            /* execution time (must occur before terminal state otherwise a race condition can occur if requested by client) */
            return new ExecutionResult(eventCounts, startTimestamp, executionLatency, (int) userThreadLatency,
                    failedExecutionException, executionException, executionOccurred, isExecutedInThread, collapserKey);
        } else {
            return this;
        }
    }

    /**
     * Creates a new ExecutionResult by adding the defined 'event' to the ones on the current instance.
     *	// 记录事件：记录事件，对应事件+1
     * 	// 内部依赖于事件计数器的plus方法
     * @param eventType event to add
     * @return new {@link ExecutionResult} with event added
     */
    public ExecutionResult addEvent(HystrixEventType eventType) {
        return new ExecutionResult(eventCounts.plus(eventType), startTimestamp, executionLatency,
                userThreadLatency, failedExecutionException, executionException,
                executionOccurred, isExecutedInThread, collapserKey);
    }

    public ExecutionResult addEvent(int executionLatency, HystrixEventType eventType) {
        if (startTimestamp >= 0 && !isResponseRejected()) {
            return new ExecutionResult(eventCounts.plus(eventType), startTimestamp, executionLatency,
                    userThreadLatency, failedExecutionException, executionException,
                    executionOccurred, isExecutedInThread, collapserKey);
        } else {
            return addEvent(eventType);
        }
    }

    public EventCounts getEventCounts() {
        return eventCounts;
    }

    public long getStartTimestamp() {
        return startTimestamp;
    }

    public int getExecutionLatency() {
        return executionLatency;
    }

    public int getUserThreadLatency() {
        return userThreadLatency;
    }
    // 纳秒的表现形式
    public long getCommandRunStartTimeInNanos() {
        return startTimestamp * 1000 * 1000;
    }

    public Exception getException() {
        return failedExecutionException;
    }

    public Exception getExecutionException() {
        return executionException;
    }

    public HystrixCollapserKey getCollapserKey() {
        return collapserKey;
    }
    // 是否被信号量拒绝：只需看是否记录了此事件即可
    public boolean isResponseSemaphoreRejected() {
        return eventCounts.contains(HystrixEventType.SEMAPHORE_REJECTED);
    }

    public boolean isResponseThreadPoolRejected() {
        return eventCounts.contains(HystrixEventType.THREAD_POOL_REJECTED);
    }

    public boolean isResponseRejected() {
        return isResponseThreadPoolRejected() || isResponseSemaphoreRejected();
    }

    public List<HystrixEventType> getOrderedList() {
        List<HystrixEventType> eventList = new ArrayList<HystrixEventType>();
        for (HystrixEventType eventType: ALL_EVENT_TYPES) {
            if (eventCounts.contains(eventType)) {
                eventList.add(eventType);
            }
        }
        return eventList;
    }

    public boolean isExecutedInThread() {
        return isExecutedInThread;
    }

    public boolean executionOccurred() {
        return executionOccurred;
    }
    // 结果里是否包含有终止时间：
    //SUCCESS/BAD_REQUEST/FALLBACK_SUCCESS/FALLBACK_FAILURE/FALLBACK_REJECTION
    // FALLBACK_MISSING/RESPONSE_FROM_CACHE/CANCELLED等这些都算终止喽
    public boolean containsTerminalEvent() {
        return eventCounts.containsAnyOf(TERMINAL_EVENTS);
    }

    @Override
    public String toString() {
        return "ExecutionResult{" +
                "eventCounts=" + eventCounts +
                ", failedExecutionException=" + failedExecutionException +
                ", executionException=" + executionException +
                ", startTimestamp=" + startTimestamp +
                ", executionLatency=" + executionLatency +
                ", userThreadLatency=" + userThreadLatency +
                ", executionOccurred=" + executionOccurred +
                ", isExecutedInThread=" + isExecutedInThread +
                ", collapserKey=" + collapserKey +
                '}';
    }
}
