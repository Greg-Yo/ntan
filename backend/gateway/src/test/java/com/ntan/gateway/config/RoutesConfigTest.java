package com.ntan.gateway.config;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
class RoutesConfigTest {

    private static final AtomicReference<String> cheminRecu = new AtomicReference<>();
    private static final AtomicReference<String> authorizationRecue = new AtomicReference<>();
    private static final HttpServer fauxServiceTiers = demarrerFauxService();

    private static HttpServer demarrerFauxService() {
        try {
            HttpServer serveur = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            serveur.createContext("/", echange -> {
                cheminRecu.set(echange.getRequestURI().getPath());
                authorizationRecue.set(echange.getRequestHeaders().getFirst("Authorization"));
                byte[] corps = "ok".getBytes(StandardCharsets.UTF_8);
                echange.sendResponseHeaders(200, corps.length);
                echange.getResponseBody().write(corps);
                echange.close();
            });
            serveur.start();
            return serveur;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void proprietes(DynamicPropertyRegistry registry) {
        registry.add("ntan.routes.tiers", () -> "http://localhost:" + fauxServiceTiers.getAddress().getPort());
    }

    @AfterAll
    static void arreter() {
        fauxServiceTiers.stop(0);
    }

    @TestConfiguration
    static class DecodeurDeTest {
        @Bean
        @Primary
        ReactiveJwtDecoder decodeurDeTest() {
            return jeton -> "jeton-test".equals(jeton)
                    ? Mono.just(Jwt.withTokenValue(jeton).header("alg", "none")
                            .claim("preferred_username", "commercial.ntan")
                            .claim("realm_access", Map.of("roles", List.of("COMMERCIAL")))
                            .build())
                    : Mono.error(new BadJwtException("jeton refusé"));
        }
    }

    @Autowired
    ApplicationContext context;

    @Autowired
    RouteLocator routeLocator;

    WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToApplicationContext(context).configureClient().build();
        cheminRecu.set(null);
        authorizationRecue.set(null);
    }

    @Test
    void declareLesTroisRoutesMetier() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();

        assertThat(routes)
                .extracting(Route::getId, r -> r.getUri().getPort())
                .contains(
                        tuple("tiers", fauxServiceTiers.getAddress().getPort()),
                        tuple("catalog", 8082),
                        tuple("sales", 8083));
    }

    @Test
    void relaieVersLeServiceSansLePrefixeApiEtAvecLeJeton() {
        client.get().uri("/api/tiers/123")
                .header("Authorization", "Bearer jeton-test")
                .exchange()
                .expectStatus().isOk();

        assertThat(cheminRecu.get()).isEqualTo("/tiers/123");
        assertThat(authorizationRecue.get()).isEqualTo("Bearer jeton-test");
    }

    @Test
    void refuseLAccesAuxRoutesSansJetonOuAvecUnJetonInvalide() {
        client.get().uri("/api/tiers/123").exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/sales/devis")
                .header("Authorization", "Bearer faux")
                .exchange()
                .expectStatus().isUnauthorized();

        assertThat(cheminRecu.get()).isNull();
    }
}
