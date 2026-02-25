package com.ainativeos.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ExecutionPolicyProperties.class)
/**
 * 执行配置注册类。
 */
public class ExecutionConfig {
}
