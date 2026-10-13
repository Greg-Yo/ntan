package com.ntan.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

/**
 * Configuration propre à Ntan.
 *
 * @param security validation des JWT : émetteur attendu (URL publique de Keycloak)
 *                 et URL de récupération des clés (URL interne en Docker)
 * @param routes   adresses des services métier
 */
@ConfigurationProperties("ntan")
public record NtanProperties(Security security, Routes routes) {

    public record Security(String issuer, String jwkSetUri) {
    }

    public record Routes(URI tiers, URI catalog, URI sales) {
    }
}
