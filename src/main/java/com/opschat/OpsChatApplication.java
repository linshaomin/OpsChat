package com.opschat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * OpsChat 应用启动类
 * AI Ops 智能运维助手入口
 */
@SpringBootApplication
public class OpsChatApplication {

    /**
     * 应用主入口
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(OpsChatApplication.class, args);
    }
}