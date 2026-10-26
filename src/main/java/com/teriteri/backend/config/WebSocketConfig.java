package com.teriteri.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

@Configuration
public class WebSocketConfig {

    /**
     * 仅在非测试环境下注册一个ServerEndpointExporter，该Bean会自动注册使用@ServerEndpoint注解声明的websocket endpoint
     */
    @Bean
    @Profile("!test")  // 只有当前激活的 Profile 不是 test 时，才创建这个 Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }
}
