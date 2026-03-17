/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.framework.trace;

import com.alibaba.ttl.TransmittableThreadLocal;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * RAG Trace 上下文
 * 使用 TTL 在异步线程池中透传 traceId 与节点栈
 */
public final class RagTraceContext {

    // 定义线程上下文变量：保存 traceId
    private static final TransmittableThreadLocal<String> TRACE_ID = new TransmittableThreadLocal<>();
    // 定义线程上下文变量：保存 taskId
    private static final TransmittableThreadLocal<String> TASK_ID = new TransmittableThreadLocal<>();
    // 定义线程上下文变量：保存当前线程的节点栈
    private static final TransmittableThreadLocal<Deque<String>> NODE_STACK = new TransmittableThreadLocal<>();

    // 私有构造器，禁止外部实例化该工具类
    private RagTraceContext() {
    }

    // 获取当前线程中的 traceId
    public static String getTraceId() {
        // 返回当前线程上下文中的 traceId 值
        return TRACE_ID.get();
    }

    // 设置当前线程中的 traceId
    public static void setTraceId(String traceId) {
        // 将 traceId 写入当前线程上下文
        TRACE_ID.set(traceId);
    }

    // 获取当前线程中的 taskId
    public static String getTaskId() {
        // 返回当前线程上下文中的 taskId 值
        return TASK_ID.get();
    }

    // 设置当前线程中的 taskId
    public static void setTaskId(String taskId) {
        // 将 taskId 写入当前线程上下文
        TASK_ID.set(taskId);
    }

    // 获取当前节点栈深度（用于表示嵌套层级）
    public static int depth() {
        // 读取当前线程中的节点栈
        Deque<String> stack = NODE_STACK.get();
        // 若栈为空返回 0，否则返回栈大小
        return stack == null ? 0 : stack.size();
    }

    // 获取当前栈顶节点 ID（当前执行节点）
    public static String currentNodeId() {
        // 读取当前线程中的节点栈
        Deque<String> stack = NODE_STACK.get();
        // 若栈为空返回 null，否则返回栈顶元素
        return stack == null ? null : stack.peek();
    }

    // 将一个节点 ID 压入当前线程节点栈
    public static void pushNode(String nodeId) {
        // 读取当前线程中的节点栈
        Deque<String> stack = NODE_STACK.get();
        // 若当前还未初始化栈，则先创建
        if (stack == null) {
            // 创建新的数组双端队列作为栈
            stack = new ArrayDeque<>();
            // 将新栈写回线程上下文
            NODE_STACK.set(stack);
        }
        // 将节点 ID 压入栈顶
        stack.push(nodeId);
    }

    // 从当前线程节点栈弹出一个节点
    public static void popNode() {
        // 读取当前线程中的节点栈
        Deque<String> stack = NODE_STACK.get();
        // 如果栈不存在或已经为空，则无需处理直接返回
        if (stack == null || stack.isEmpty()) {
            return;
        }
        // 弹出栈顶节点
        stack.pop();
        // 若弹出后栈为空，则清理 ThreadLocal 避免残留
        if (stack.isEmpty()) {
            // 移除当前线程中的节点栈变量
            NODE_STACK.remove();
        }
    }

    // 清理当前线程中的所有 Trace 相关上下文
    public static void clear() {
        // 移除当前线程 traceId
        TRACE_ID.remove();
        // 移除当前线程 taskId
        TASK_ID.remove();
        // 移除当前线程节点栈
        NODE_STACK.remove();
    }
}