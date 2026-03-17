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

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.ArrayUtil;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

/**
 * SpEL 表达式解析工具
 */
public final class SpELUtil {

    // 定义静态参数名发现器
    private static final DefaultParameterNameDiscoverer PARAMETER_NAME_DISCOVERER = new DefaultParameterNameDiscoverer();
    // 定义静态 SpEL 解析器，负责将字符串表达式解析成可执行表达式
    private static final ExpressionParser EXPRESSION_PARSER = new SpelExpressionParser();

    /**
     * 校验并返回实际使用的 SpEL 表达式
     *
     * @param SpEl SpEL 表达式
     * @return 实际使用的 SpEL 表达式
     */
    public static Object parseKey(String SpEl, Method method, Object[] contextObj) {
        // 定义判断表达式特征的标记：变量引用 # 和类型引用 T(
        List<String> spELFlag = ListUtil.of("#", "T(");
        // 判断 SpEl 表达式中是否包含任意 SpEL 标记
        Optional<String> optional = spELFlag.stream().filter(flag -> SpEl.contains(flag)).findFirst();
        // 如果包含 SpEL 标记，则进入解析流程
        if (optional.isPresent()) {
            return parse(SpEl, method, contextObj);
        }
        // 否则直接返回原字符串
        return SpEl;
    }

    /**
     * 转换参数为字符串
     *
     * @param SpEl       SpEl 表达式
     * @param contextObj 上下文对象
     * @return 解析的字符串值
     */
    public static Object parse(String SpEl, Method method, Object[] contextObj) {
        // 将字符串表达式解析为可执行的 Expression 对象
        Expression exp = EXPRESSION_PARSER.parseExpression(SpEl);
        // 获取方法参数名数组，用于后续绑定变量
        String[] params = PARAMETER_NAME_DISCOVERER.getParameterNames(method);
        // 创建标准求值上下文，承载表达式变量
        StandardEvaluationContext context = new StandardEvaluationContext();

        // 如果参数名数组非空，则逐个绑定“参数名 -> 参数值”
        if (ArrayUtil.isNotEmpty(params)) {
            // 遍历所有参数名并建立上下文变量映射
            for (int len = 0; len < params.length; len++) {
                // 将当前参数名及对应实参值放入 SpEL 上下文
                context.setVariable(params[len], contextObj[len]);
            }
        }
        // 在上下文中执行表达式求值并返回结果
        return exp.getValue(context);
    }
}
