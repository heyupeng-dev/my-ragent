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

import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 防止消息队列消费者重复消费消息切面控制器
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public final class IdempotentConsumeAspect {

    private final StringRedisTemplate stringRedisTemplate;

    // 定义用于 Redis 原子幂等判断的 Lua 脚本
    private static final String LUA_SCRIPT = """
            local key = KEYS[1]
            local value = ARGV[1]
            local expire_time_ms = ARGV[2]
            // 原子执行 SET key value NX GET PX expire_time_ms，并返回旧值
            return redis.call('SET', key, value, 'NX', 'GET', 'PX', expire_time_ms)
            """;

    /**
     * 对所有标注了 {@link IdempotentConsume} 注解的方法进行环绕增强
     * 在目标方法执行前后插入幂等控制逻辑
     */
    @Around("@annotation(com.nageoffer.ai.ragent.framework.idempotent.IdempotentConsume)")
    public Object idempotentConsume(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取当前被拦截方法上的 IdempotentConsume 注解实例
        IdempotentConsume idempotentConsume = getIdempotentConsumeAnnotation(joinPoint);
        // 根据注解中的 keyPrefix 和 SpEL 表达式解析结果拼接唯一幂等 key
        String uniqueKey = idempotentConsume.keyPrefix()
                + SpELUtil.parseKey(idempotentConsume.key(), ((MethodSignature) joinPoint.getSignature()).getMethod(), joinPoint.getArgs());
        // 读取注解中配置的 key 超时时间，单位秒
        long keyTimeoutSeconds = idempotentConsume.keyTimeout();

        // 执行 Lua 脚本，尝试将当前消息标记为“消费中”并返回旧值
        String absentAndGet = stringRedisTemplate.execute(
                // 构造 Lua 脚本对象，并声明返回值类型为 String
                RedisScript.of(LUA_SCRIPT, String.class),
                // 传入 Lua 脚本的 KEYS 参数列表，这里列表中只有一个幂等 key
                List.of(uniqueKey),
                // 传入 Lua 脚本的第一个 ARGV 参数，表示“消费中”状态码
                IdempotentConsumeStatusEnum.CONSUMING.getCode(),
                // 传入 Lua 脚本的第二个 ARGV 参数，表示过期时间的毫秒值
                String.valueOf(TimeUnit.SECONDS.toMillis(keyTimeoutSeconds))
        );

        // 如果已有消费中状态，提示延迟消费；已完成则直接跳过
        // 判断当前返回的旧值是否表示消费中状态
        boolean errorFlag = IdempotentConsumeStatusEnum.isError(absentAndGet);
        // 如果检测到重复消费中的状态，则抛出异常让上层走重试逻辑
        if (errorFlag) {
            log.warn("[{}] MQ repeated consumption, wait for delayed retry.", uniqueKey);
            throw new ServiceException(String.format("消息消费者幂等异常，幂等标识：%s", uniqueKey));
        }
        // 如果检测到已消费状态，则跳过本次处理
        if (IdempotentConsumeStatusEnum.CONSUMED.getCode().equals(absentAndGet)) {
            log.info("[{}] MQ consumption already completed, skip.", uniqueKey);
            return null;
        }

        // 进入 try 块，准备执行业务方法并在成功后更新状态
        try {
            // 执行被拦截的原始业务方法
            Object result = joinPoint.proceed();
            // 将 Redis 中的幂等 key 状态更新为“已消费”
            stringRedisTemplate.opsForValue().set(
                    uniqueKey,
                    IdempotentConsumeStatusEnum.CONSUMED.getCode(),
                    keyTimeoutSeconds,
                    TimeUnit.SECONDS
            );
            // 返回原始业务方法的执行结果
            return result;
        } catch (Throwable ex) {
            // 如果业务失败，则删除之前占用的幂等 key，便于后续重试
            stringRedisTemplate.delete(uniqueKey);
            throw ex;
        }
    }

    /**
     * 从切点中提取目标方法上的 IdempotentConsume 注解
     * @return 返回自定义防重复消费注解
     */
    public static IdempotentConsume getIdempotentConsumeAnnotation(ProceedingJoinPoint joinPoint) throws NoSuchMethodException {
        // 将通用签名转换为方法签名
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        // 通过目标对象的实际类型、方法名和参数类型反射获取目标方法
        Method targetMethod = joinPoint.getTarget().getClass().getDeclaredMethod(methodSignature.getName(), methodSignature.getMethod().getParameterTypes());
        // 返回目标方法上的 IdempotentConsume 注解
        return targetMethod.getAnnotation(IdempotentConsume.class);
    }
}
