package com.ntan.gateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

@SpringBootTest
class GatewaySecurityTest {

    @Autowired
    ApplicationContext context;

    WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToApplicationContext(context)
                .apply(springSecurity())
                .configureClient()
                .build();
    }

    @Test
    void meSansJetonRenvoie401() {
        client.get().uri("/api/me").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void meRenvoieLIdentiteEtUniquementLesRolesNtan() {
        client.mutateWith(mockJwt()
                        .jwt(j -> j.claim("preferred_username", "admin.ntan")
                                .claim("name", "Awa Admin")
                                .claim("realm_access", Map.of("roles",
                                        List.of("offline_access", "ADMIN", "default-roles-ntan"))))
                        .authorities(new KeycloakRealmRolesConverter()))
                .get().uri("/api/me")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.username").isEqualTo("admin.ntan")
                .jsonPath("$.nom").isEqualTo("Awa Admin")
                .jsonPath("$.roles.length()").isEqualTo(1)
                .jsonPath("$.roles[0]").isEqualTo("ADMIN");
    }

    @Test
    void meSansRealmAccessRenvoieDesRolesVides() {
        client.mutateWith(mockJwt()
                        .jwt(j -> j.claim("preferred_username", "sans.role"))
                        .authorities(new KeycloakRealmRolesConverter()))
                .get().uri("/api/me")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.username").isEqualTo("sans.role")
                .jsonPath("$.nom").isEqualTo("sans.role")
                .jsonPath("$.roles.length()").isEqualTo(0);
    }

    @Test
    void lesEndpointsNonPrevusSontFermes() {
        client.get().uri("/actuator/env").exchange().expectStatus().isUnauthorized();
        client.get().uri("/inconnu").exchange().expectStatus().isUnauthorized();

        client.mutateWith(mockJwt())
                .get().uri("/actuator/env")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void laSanteRestePublique() {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }
}
