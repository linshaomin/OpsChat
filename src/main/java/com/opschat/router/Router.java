package com.opschat.router;

import java.util.List;
import java.util.Map;

/**
 * 路由接口
 * 定义意图识别路由的标准方法
 */
public interface Router {

    /**
     * 执行路由
     * @param question 用户问题
     * @return 路由结果
     */
    RouteResult route(String question);

    /**
     * 执行路由（带历史记录）
     * @param question 用户问题
     * @param history 历史对话记录
     * @return 路由结果
     */
    default RouteResult route(String question, List<Map<String, String>> history) {
        return route(question);
    }
}