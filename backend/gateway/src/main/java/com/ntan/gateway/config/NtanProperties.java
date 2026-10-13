package com.ntan.gateway.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;

/**
 * Configuration propre à Ntan.
 *
 * @param security validation des JWT : émetteur attendu (URL publique de Keycloak)
 *                 et URL de récupération des clés (URL interne en Docker)
 * @param routes   adresses des services métier
 */
@Validated
@ConfigurationProperties("ntan")
public record NtanProperties(@NotNull @Valid Security security, @NotNull @Valid Routes routes) {

    public record Security(@NotBlank String issuer, @NotBlank String jwkSetUri) {
    }

    public record Routes(@NotNull URI tiers, @NotNull URI catalog, @NotNull URI sales) {
    }
}
