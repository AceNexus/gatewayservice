package com.acenexus.tata.gatewayservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BusRefreshSecurityTests {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void busrefreshWithoutAuthShouldReturn401() {
        webTestClient.post()
                .uri("/actuator/busrefresh")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void busrefreshWithValidAuthShouldPassSecurity() {
        // Spring Security 通過（非 401），實際端點在測試環境未開放故回 404
        webTestClient.post()
                .uri("/actuator/busrefresh")
                .headers(headers -> headers.setBasicAuth("admin", "password"))
                .exchange()
                .expectStatus().value(status -> assertThat(status).isNotEqualTo(401));
    }

}
