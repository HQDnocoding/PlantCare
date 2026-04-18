package com.backend.search_service.config;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;

@Configuration
public class ElasticsearchConfig extends ElasticsearchConfiguration {

    @Value("${spring.elasticsearch.uris}")
    private List<String> esUris;

    @Value("${spring.elasticsearch.username:}")
    private String username;

    @Value("${spring.elasticsearch.password:}")
    private String password;

    @Override
    public ClientConfiguration clientConfiguration() {
        String[] hosts = esUris.stream()
                .map(ElasticsearchConfig::toAuthority)
                .toArray(String[]::new);

        // Infer scheme from first URI — assume all nodes use same scheme
        boolean useSSL = esUris.stream()
                .anyMatch(uri -> uri.toLowerCase().startsWith("https"));

        ClientConfiguration.MaybeSecureClientConfigurationBuilder builder = ClientConfiguration.builder()
                .connectedTo(hosts);

        if (useSSL) {
            builder.usingSsl();
        }

        if (!username.isBlank()) {
            builder.withBasicAuth(username, password);
        }

        return builder
                .withConnectTimeout(Duration.ofSeconds(5))
                .withSocketTimeout(Duration.ofSeconds(10))
                .build();
    }

    private static String toAuthority(String uri) {
        return URI.create(uri).getAuthority();
    }
}