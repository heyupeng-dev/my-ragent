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

package com.nageoffer.ai.ragent.framework.idempotent;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.google.gson.Gson;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 防止用户重复提交表单信息切面控制器
 */
@Aspect
@Component
@RequiredArgsConstructor
public final class IdempotentSubmitAspect {

    private final RedissonClient redissonClient;
    private final Gson gson = new Gson();

    /**
     * 对所有标注了 {@link IdempotentSubmit} 注解的方法进行环绕增强
     * 在执行目标方法前后增加防重复提交控制
     */
    @Around("@annotation(com.nageoffer.ai.ragent.framework.idempotent.IdempotentSubmit)")
    public Object idempotentSubmit(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取当前被拦截方法上的 IdempotentSubmit 注解实例
        IdempotentSubmit idempotentSubmit = getIdempotentSubmitAnnotation(joinPoint);
        // 根据当前请求和注解配置构建分布式锁的 key
        String lockKey = buildLockKey(joinPoint, idempotentSubmit);
        RLock lock = redissonClient.getLock(lockKey);
        // 如果当前线程没有成功拿到锁，说明同一请求正在处理中或刚刚提交过
        if (!lock.tryLock()) {
            // 抛出客户端异常，提示用户不要重复提交
            throw new ClientException(idempotentSubmit.message());
        }

        Object result;
        try {
            // 执行原始业务方法
            result = joinPoint.proceed();
        } finally {
            // 释放当前分布式锁
            lock.unlock();
        }
        // 返回原始业务方法执行结果
        return result;
    }

    /**
     * 获取目标方法上的 IdempotentSubmit 注解
     * @return 返回自定义防重复提交注解
     */
    public static IdempotentSubmit getIdempotentSubmitAnnotation(ProceedingJoinPoint joinPoint) throws NoSuchMethodException {
        // 将通用签名转换为方法签名
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        // 通过目标对象的实际类型、方法名和参数类型反射获取目标方法
        Method targetMethod = joinPoint.getTarget().getClass().getDeclaredMethod(methodSignature.getName(), methodSignature.getMethod().getParameterTypes());
        // 返回目标方法上的 IdempotentSubmit 注解
        return targetMethod.getAnnotation(IdempotentSubmit.class);
    }

    /**
     * @return 获取当前线程上下文 ServletPath
     */
    private String getServletPath() {
        // 从 Spring 请求上下文中取出当前线程绑定的请求属性
        ServletRequestAttributes sra = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        // 返回当前请求的 ServletPath，如果请求为空则抛出空指针异常
        return Objects.requireNonNull(sra).getRequest().getServletPath();
    }

    /**
     * @return 当前操作用户 ID
     */
    private String getCurrentUserId() {
        // 从用户上下文中读取当前用户 ID
        return UserContext.getUserId();
    }

    /**
     * @return 计算当前方法参数的 MD5 值
     */
    private String calcArgsMD5(ProceedingJoinPoint joinPoint) {
        // 将参数序列化为 JSON，再按 UTF-8 编码后计算 MD5
        return DigestUtil.md5Hex(gson.toJson(joinPoint.getArgs()).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 构建分布式锁的唯一 key
     */
    private String buildLockKey(ProceedingJoinPoint joinPoint, IdempotentSubmit idempotentSubmit) {
        // 如果注解中显式配置了 key，则优先使用自定义 key 逻辑
        if (StrUtil.isNotBlank(idempotentSubmit.key())) {
            // 获取当前方法签名
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            // 使用 SpEL 表达式从方法参数中解析出动态 key 值
            Object keyValue = SpELUtil.parseKey(idempotentSubmit.key(), signature.getMethod(), joinPoint.getArgs());
            // 返回基于注解 key 构建的锁标识
            return String.format("idempotent-submit:key:%s", keyValue);
        }

        // 如果没有配置自定义 key，则根据请求路径、用户 ID 和参数摘要拼接默认锁 key
        return String.format(
                // 默认锁 key 模板，确保同一路径、同一用户、同一参数的请求会命中同一把锁
                "idempotent-submit:path:%s:currentUserId:%s:md5:%s",
                // 填充当前请求路径
                getServletPath(),
                // 填充当前用户 ID
                getCurrentUserId(),
                // 填充参数 MD5 摘要
                calcArgsMD5(joinPoint)
        );
    }
}
