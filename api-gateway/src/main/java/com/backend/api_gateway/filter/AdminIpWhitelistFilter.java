package com.backend.api_gateway.filter;

import com.backend.api_gateway.exception.AppException;
import com.backend.api_gateway.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.List;

@Slf4j
// @Component
public class AdminIpWhitelistFilter implements GlobalFilter, Ordered {

    @Value("${admin.whitelist-ips:127.0.0.1,localhost}")
    private String whitelistIps;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (!path.startsWith("/api/v1/admin/")) {
            return chain.filter(exchange);
        }

        String clientIp = getClientIp(exchange);
        List<String> allowedIps = List.of(whitelistIps.split(","));

        if (!allowedIps.stream().anyMatch(ip -> clientIp.equals(ip.trim()))) {
            log.warn("[AdminIpFilter] IP not whitelisted | ip={} | path={}", clientIp, path);
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }

        log.debug("[AdminIpFilter] IP whitelisted | ip={}", clientIp);
        return chain.filter(exchange);
    }

    private String getClientIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        InetSocketAddress addr = exchange.getRequest().getRemoteAddress();
        return addr != null ? addr.getAddress().getHostAddress() : "unknown";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 3;
    }
}