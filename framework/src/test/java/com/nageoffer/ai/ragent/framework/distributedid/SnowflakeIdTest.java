package com.nageoffer.ai.ragent.framework.distributedid;

import cn.hutool.core.lang.Snowflake;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class SnowflakeIdTest {

    private StringRedisTemplate redisTemplate;

    private static final String KEY = "snowflake_work_id_key";

    @BeforeEach
    public void init() {
        System.out.println("\n===== 初始化 RedisTemplate =====");

        // 创建单机配置
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName("127.0.0.1");
        config.setPort(6380);
        config.setPassword("123456");

        // 创建连接工厂
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();

        // 创建 RedisTemplate
        redisTemplate = new StringRedisTemplate();
        redisTemplate.setConnectionFactory(factory);
        redisTemplate.afterPropertiesSet();

        System.out.println("RedisTemplate 初始化完成");
    }

    // ===========================
    // 单独看 Redis 当前状态
    // ===========================
    @Test
    public void testRedisState() {
        System.out.println("========== Redis 当前状态 ==========");

        Map<Object, Object> map = redisTemplate.opsForHash().entries(KEY);
        System.out.println(map);
    }

    // ===========================
    // 单独执行 Lua 脚本，查看返回结果
    // ===========================
    @Test
    public void testExecuteLua() {
        System.out.println("========== 执行 Lua ==========");

        List<Long> result = executeLua();

        System.out.println("Lua 返回: " + result);
    }

    // ===========================
    // 执行 Lua 脚本，查看 workerId 和 datacenterId
    // ===========================
    @Test
    public void testWorkerAllocation() {
        System.out.println("========== 获取 workerId ==========");

        List<Long> result = executeLua();

        Long workerId = result.get(0);
        Long datacenterId = result.get(1);

        System.out.println("workerId     = " + workerId);
        System.out.println("datacenterId = " + datacenterId);
    }

    // ===========================
    // 执行十次 Lua 脚本，查看 workerId 和 datacenterId
    // ===========================
    @Test
    public void testWorkerAllocation10Times() {
        System.out.println("========== 获取 workerId 10 次 ==========");

        for (int i = 0; i < 10; i++) {
            List<Long> result = executeLua();

            Long workerId = result.get(0);
            Long datacenterId = result.get(1);

            System.out.println("workerId     = " + workerId);
            System.out.println("datacenterId = " + datacenterId);
        }
     }

    // ===========================
    // 单独测试 Snowflake 初始化
    // ===========================
    @Test
    public void testInitSnowflake() {
        System.out.println("========== 初始化 Snowflake ==========");

        Snowflake snowflake = new Snowflake(1, 1);

        System.out.println("Snowflake 创建成功");
        System.out.println("生成ID: " + snowflake.nextId());
    }

    // ===========================
    // 用 Redis Lua 脚本分配结果生成 ID
    // ===========================
    @Test
    public void testGenerateIdFromRedis() {
        System.out.println("========== Redis → Snowflake → ID ==========");

        List<Long> result = executeLua();

        Long workerId = result.get(0);
        Long datacenterId = result.get(1);

        System.out.println("使用 workerId=" + workerId + ", datacenterId=" + datacenterId);

        Snowflake snowflake = new Snowflake(workerId, datacenterId);

        for (int i = 0; i < 5; i++) {
            long id = snowflake.nextId();
            System.out.println("ID[" + i + "] = " + id);
        }
    }

    // ===========================
    // 测试 ID 递增
    // ===========================
    @Test
    public void testIdOrder() {
        System.out.println("========== ID 递增测试 ==========");

        Snowflake snowflake = new Snowflake(1, 1);

        long prev = snowflake.nextId();

        for (int i = 0; i < 10; i++) {
            long curr = snowflake.nextId();

            System.out.println("prev=" + prev + ", curr=" + curr);

            if (curr <= prev) {
                throw new RuntimeException("ID 非递增");
            }

            prev = curr;
        }

        System.out.println("ID 正常递增");
    }

    // ===========================
    // 公共方法：执行 Lua
    // ===========================
    private List<Long> executeLua() {

        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptSource(
                new ResourceScriptSource(
                        new ClassPathResource("lua/snowflake_init.lua")
                )
        );
        script.setResultType(List.class);

        List<Long> result = redisTemplate.execute(script, Collections.emptyList());

        if (result == null || result.size() != 2) {
            throw new RuntimeException("Lua 脚本执行失败");
        }

        return result;
    }
}