package com.acenexus.tata.gatewayservice.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Set;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

@Component
public class GatewayLoggerFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(GatewayLoggerFilter.class);
    private static final Set<String> SENSITIVE_HEADERS = Set.of("authorization", "cookie", "jwt", "api-key");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String requestId = request.getId();
        long startTimeMillis = System.currentTimeMillis();
        String clientIP = getClientIP(request);
        String httpMethod = request.getMethod().name();
        String requestPath = request.getPath().value();

        // 記錄請求開始
        log.info("[Request Start] requestId={} | httpMethod={} | requestPath={} | clientIP={}", requestId, httpMethod, requestPath, clientIP);

        // 排除敏感訊息
        request.getHeaders().forEach((name, values) -> {
            if (SENSITIVE_HEADERS.contains(name.toLowerCase())) {
                log.debug("[Request Header] requestId={} | {}=[PROTECTED]", requestId, name);
            } else {
                values.forEach(value -> log.debug("[Request Header] requestId={} | {}={}", requestId, name, value));
            }
        });

        return chain.filter(exchange)
                .then(Mono.fromRunnable(() -> {
                    long executionTime = System.currentTimeMillis() - startTimeMillis;
                    ServerHttpResponse response = exchange.getResponse();
                    HttpStatusCode statusCode = response.getStatusCode() != null ? response.getStatusCode() : HttpStatus.INTERNAL_SERVER_ERROR;

                    Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
                    String routeId = route.getId();

                    URI targetUri = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
                    String target = targetUri.toString();

                    // 記錄回應日誌
                    if (statusCode.is5xxServerError()) {
                        log.error("[Request End] requestId={} | httpMethod={} | requestPath={} | status={} | route={} | target={} | duration={}ms", requestId, httpMethod, requestPath, statusCode, routeId, target, executionTime);
                    } else if (statusCode.is4xxClientError()) {
                        log.warn("[Request End] requestId={} | httpMethod={} | requestPath={} | status={} | route={} | target={} | duration={}ms", requestId, httpMethod, requestPath, statusCode, routeId, target, executionTime);
                    } else {
                        log.info("[Request End] requestId={} | httpMethod={} | requestPath={} | status={} | route={} | target={} | duration={}ms", requestId, httpMethod, requestPath, statusCode, routeId, target, executionTime);
                    }

                    // 記錄慢請求
                    if (executionTime > 3000) {
                        log.warn("[Slow Request] requestId={} | httpMethod={} | requestPath={} | duration={}ms (over threshold)", requestId, httpMethod, requestPath, executionTime);
                    }
                }));
    }

    /**
     * 獲取客戶端真實 IP 地址
     */
    private String getClientIP(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (!xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddress().getHostString();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

}
