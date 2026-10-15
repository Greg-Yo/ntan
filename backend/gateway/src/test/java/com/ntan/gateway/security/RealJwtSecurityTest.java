package com.ntan.gateway.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

/**
 * Exerce le vrai décodeur, le vrai validateur et le vrai convertisseur de {@code SecurityConfig}
 * avec des jetons réellement signés, dont la clé publique est servie par un faux endpoint JWKS.
 */
@SpringBootTest
class RealJwtSecurityTest {

    private static final String EMETTEUR_PUBLIC = "http://localhost:8180/realms/ntan";
    private static final String EMETTEUR_INTERNE = "http://keycloak:8080/realms/ntan";

    private static RSAKey cle;
    private static HttpServer serveurJwks;

    @Autowired
    ApplicationContext context;

    WebTestClient client;

    @BeforeAll
    static void demarrerServeurJwks() throws Exception {
        cle = new RSAKeyGenerator(2048).keyID("cle-test").generate();
        byte[] corps = new JWKSet(cle.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        serveurJwks = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serveurJwks.createContext("/certs", echange -> {
            echange.getResponseHeaders().add("Content-Type", "application/json");
            echange.sendResponseHeaders(200, corps.length);
            echange.getResponseBody().write(corps);
            echange.close();
        });
        serveurJwks.start();
    }

    @AfterAll
    static void arreterServeurJwks() {
        serveurJwks.stop(0);
    }

    @DynamicPropertySource
    static void proprietes(DynamicPropertyRegistry registry) throws IOException {
        // Le serveur est démarré par @BeforeAll, qui s'exécute après ce hook : on résout le port à la demande.
        registry.add("ntan.security.jwk-set-uri", () -> {
            try {
                demarrerSiNecessaire();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return "http://127.0.0.1:" + serveurJwks.getAddress().getPort() + "/certs";
        });
    }

    private static synchronized void demarrerSiNecessaire() throws Exception {
        if (serveurJwks == null) {
            demarrerServeurJwks();
        }
    }

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToApplicationContext(context)
                .apply(springSecurity())
                .configureClient()
                .build();
    }

    private static String jeton(RSAKey signataire, String emetteur, Instant expiration, Object realmAccess)
            throws JOSEException {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(emetteur)
                .subject("u1")
                .claim("preferred_username", "admin.ntan")
                .claim("name", "Awa Admin")
                .issueTime(Date.from(expiration.minusSeconds(600)))
                .expirationTime(Date.from(expiration));
        if (realmAccess != null) {
            claims.claim("realm_access", realmAccess);
        }
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).keyID(signataire.getKeyID()).build(),
                claims.build());
        jwt.sign(new RSASSASigner(signataire));
        return jwt.serialize();
    }

    private static Map<String, Object> roles(String... valeurs) {
        return Map.of("roles", List.of(valeurs));
    }

    private WebTestClient.ResponseSpec appeler(String chemin, String jeton) {
        return client.get().uri(chemin).header("Authorization", "Bearer " + jeton).exchange();
    }

    @Test
    void jetonValideDeLEmetteurPublicDonneIdentiteEtRolesNtan() throws Exception {
        String jeton = jeton(cle, EMETTEUR_PUBLIC, Instant.now().plusSeconds(300),
                roles("offline_access", "ADMIN", "default-roles-ntan"));

        appeler("/api/me", jeton)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.username").isEqualTo("admin.ntan")
                .jsonPath("$.nom").isEqualTo("Awa Admin")
                .jsonPath("$.roles.length()").isEqualTo(1)
                .jsonPath("$.roles[0]").isEqualTo("ADMIN");
    }

    @Test
    void jetonEmisParLUrlInterneDeKeycloakEstRefuse() throws Exception {
        String jeton = jeton(cle, EMETTEUR_INTERNE, Instant.now().plusSeconds(300), roles("ADMIN"));

        appeler("/api/me", jeton).expectStatus().isUnauthorized();
    }

    @Test
    void jetonExpireEstRefuse() throws Exception {
        String jeton = jeton(cle, EMETTEUR_PUBLIC, Instant.now().minusSeconds(300), roles("ADMIN"));

        appeler("/api/me", jeton).expectStatus().isUnauthorized();
    }

    @Test
    void jetonSigneParUneCleInconnueEstRefuse() throws Exception {
        RSAKey autreCle = new RSAKeyGenerator(2048).keyID("cle-test").generate();
        String jeton = jeton(autreCle, EMETTEUR_PUBLIC, Instant.now().plusSeconds(300), roles("ADMIN"));

        appeler("/api/me", jeton).expectStatus().isUnauthorized();
    }

    @Test
    void realmAccessMalFormeDonneDesRolesVides() throws Exception {
        String jeton = jeton(cle, EMETTEUR_PUBLIC, Instant.now().plusSeconds(300), "ADMIN");

        appeler("/api/me", jeton)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.roles.length()").isEqualTo(0);
    }

    @Test
    void cheminInconnuAvecJetonValideEstInterdit() throws Exception {
        String jeton = jeton(cle, EMETTEUR_PUBLIC, Instant.now().plusSeconds(300), roles("ADMIN"));

        appeler("/inconnu", jeton).expectStatus().isForbidden();
    }
}
