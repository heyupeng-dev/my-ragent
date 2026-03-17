package com.nageoffer.ai.ragent.framework.idempotent;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

class SpELUtilTest {

    /**
     * 测试用方法（用于提供参数名给 SpEL）
     */
    static class Fixture {
        String buildKey(String userName, Integer userId) {
            return userName + "-" + userId;
        }

        void noArgs() {
        }
    }

    @Test
    void testAll() throws Exception {
        literal();
        variable();
        staticCall();
        calculate();
        noArgs();
        error();
    }

    /**
     * 普通字符串：不解析，直接返回
     */
    @Test
    void literal() throws Exception {
        // 获取测试方法的 Method 对象
        Method method = Fixture.class.getDeclaredMethod("buildKey", String.class, Integer.class);
        // 解析一个普通字符串表达式
        Object result = SpELUtil.parseKey("abc", method, new Object[]{"xiaoming", 123456});
        // 输出结果，预期为原字符串
        System.out.println("literal => " + result);
    }

    /**
     * 变量解析：#参数名
     */
    @Test
    void variable() throws Exception {
        Method method = Fixture.class.getDeclaredMethod("buildKey", String.class, Integer.class);

        Object result = SpELUtil.parseKey("#userName + '-' + #userId", method, new Object[]{"xiaoming", 123456});

        System.out.println("variable => " + result);
    }

    /**
     * 调用静态方法：T(类名)
     */
    @Test
    void staticCall() throws Exception {
        Method method = Fixture.class.getDeclaredMethod("buildKey", String.class, Integer.class);

        Object result = SpELUtil.parseKey("T(String).valueOf(#userId)", method, new Object[]{"xiaoming", 123456});

        System.out.println("staticCall => " + result);
    }

    /**
     * 表达式计算（带参数）
     */
    @Test
    void calculate() throws Exception {
        Method method = Fixture.class.getDeclaredMethod("buildKey", String.class, Integer.class);

        Object result = SpELUtil.parse("#userName + '-' + (#userId + 1)", method, new Object[]{"xiaoming", 123456});

        System.out.println("calculate => " + result);
    }

    /**
     * 无参方法 + 常量表达式
     */
    @Test
    void noArgs() throws Exception {
        Method method = Fixture.class.getDeclaredMethod("noArgs");

        Object result = SpELUtil.parse("T(Math).max(3, 9)", method, new Object[]{});

        System.out.println("noArgs => " + result);
    }

    /**
     * 错误表达式（变量不存在）
     */
    @Test
    void error() throws Exception {
        Method method = Fixture.class.getDeclaredMethod("buildKey", String.class, Integer.class);

        try {
            SpELUtil.parse("#notExist + 1", method, new Object[]{"xiaoming", 123456});
        } catch (Exception e) {
            System.out.println("error => " + e.getClass().getSimpleName());
        }
    }
}