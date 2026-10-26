package com.teriteri.backend.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ElasticSearchConfig {

    @Value("${elasticsearch.host}")
    private String host;

    @Value("${elasticsearch.port}")
    private Integer port;

    @Value("${elasticsearch.username}")
    private String username;

    @Value("${elasticsearch.password}")
    private String password;

    // @Bean(destroyMethod = "close")  不需要主动设置 close，因为默认值下，它也会调用 close()
    @Bean
    public RestClient restClient() {
        final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));
        return RestClient
                .builder(new HttpHost(host, port, "http"))
                // 配置连接池
                .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder
                        .setDefaultCredentialsProvider(credentialsProvider) // 身份验证
                        .setMaxConnTotal(100)  // 最大总连接数
                        .setMaxConnPerRoute(50)  // 每个路由的最大连接数
                        .setKeepAliveStrategy(((response, context) -> Duration.ofMinutes(5).toMillis()))    // httpclient保活策略 5分钟不连接就关闭
                        .setDefaultIOReactorConfig(IOReactorConfig.custom().setSoKeepAlive(true).build())   // 开启tcp keepalive
                )
                .build();
    }

    @Bean
    public ElasticsearchTransport transport() {
        return new RestClientTransport(restClient(), new JacksonJsonpMapper());
    }

    @Bean
    public ElasticsearchClient elasticsearchClient() {
         // 方法互调，这是一种写法，还有一种是参数注入
        return new ElasticsearchClient(transport());
    }
}
