package com.sky.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

/**
 * WebSocket配置类，用于注册WebSocket的Bean
 * <p>
 * 只有以内置容器（Tomcat）方式启动时才需要手动注册ServerEndpointExporter；
 * 若打成war包部署到外部容器，则应由容器负责扫描，需去掉该Bean。
 */
@Configuration
public class WebSocketConfiguration {

    @Bean
    public ServerEndpointExporter serverEndpointExporter() {

        return new ServerEndpointExporter();
    }
}
