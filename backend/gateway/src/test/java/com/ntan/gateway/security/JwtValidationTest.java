package com.ntan.gateway.security;

import com.ntan.gateway.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JwtValidationTest {

    private static final String EMETTEUR_PUBLIC = "http://localhost:8180/realms/ntan";

    private final OAuth2TokenValidator<Jwt> validator = SecurityConfig.jwtValidator(EMETTEUR_PUBLIC);

    private static Jwt jeton(String emetteur, Instant expiration) {
        return Jwt.withTokenValue("jeton").header("alg", "RS256").header("typ", "JWT")
                .issuer(emetteur)
                .issuedAt(expiration.minusSeconds(600))
                .expiresAt(expiration)
                .build();
    }

    @Test
    void accepteUnJetonValideDeLEmetteurPublic() {
        Jwt valide = jeton(EMETTEUR_PUBLIC, Instant.now().plusSeconds(300));
        assertThat(validator.validate(valide).hasErrors()).isFalse();
    }

    @Test
    void refuseUnJetonExpire() {
        Jwt expire = jeton(EMETTEUR_PUBLIC, Instant.now().minusSeconds(300));
        assertThat(validator.validate(expire).hasErrors()).isTrue();
    }

    @Test
    void refuseUnJetonEmisParLUrlInterneDeKeycloak() {
        Jwt urlInterne = jeton("http://keycloak:8080/realms/ntan", Instant.now().plusSeconds(300));
        assertThat(validator.validate(urlInterne).hasErrors()).isTrue();
    }
}
