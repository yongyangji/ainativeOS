package com.ainativeos.config;

import com.ainativeos.event.EventProperties;
import com.ainativeos.llm.LlmProperties;
import com.ainativeos.plugin.PluginProperties;
import com.ainativeos.template.TemplateProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        ExecutionPolicyProperties.class,
        LlmProperties.class,
        PluginProperties.class,
        EventProperties.class,
        TemplateProperties.class
})
/**
 * 执行配置注册类。
 */
public class ExecutionConfig {
}
