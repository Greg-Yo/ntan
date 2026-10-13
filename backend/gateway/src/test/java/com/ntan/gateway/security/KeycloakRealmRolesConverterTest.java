package com.ntan.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakRealmRolesConverterTest {

    private final KeycloakRealmRolesConverter converter = new KeycloakRealmRolesConverter();

    private static Jwt.Builder jwt() {
        return Jwt.withTokenValue("jeton").header("alg", "none").claim("sub", "u1");
    }

    @Test
    void neGardeQueLesRolesNtan() {
        Jwt token = jwt().claim("realm_access", Map.of("roles",
                List.of("COMMERCIAL", "offline_access", "uma_authorization", "default-roles-ntan", "ADMIN"))).build();

        assertThat(converter.convert(token))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_COMMERCIAL");
    }

    @Test
    void sansClaimRealmAccessRenvoieAucuneAutorite() {
        assertThat(converter.convert(jwt().build())).isEmpty();
    }

    @Test
    void claimMalFormeeRenvoieAucuneAutorite() {
        Jwt rolesPasUneListe = jwt().claim("realm_access", Map.of("roles", "ADMIN")).build();
        Jwt realmAccessPasUneMap = jwt().claim("realm_access", "ADMIN").build();

        assertThat(converter.convert(rolesPasUneListe)).isEmpty();
        assertThat(converter.convert(realmAccessPasUneMap)).isEmpty();
    }
}
