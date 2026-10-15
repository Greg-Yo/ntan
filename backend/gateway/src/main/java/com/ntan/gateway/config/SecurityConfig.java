package com.ntan.gateway.config;

import com.ntan.gateway.security.KeycloakRealmRolesConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRolesConverter());
        jwtConverter.setPrincipalClaimName("preferred_username");

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .pathMatchers("/api/**").authenticated()
                        .anyExchange().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                new ReactiveJwtAuthenticationConverterAdapter(jwtConverter))))
                .build();
    }

    /**
     * Les clés sont lues via {@code jwk-set-uri} (URL interne en Docker) tandis que l'émetteur
     * est validé contre l'URL publique : c'est elle que le navigateur a utilisée pour obtenir le jeton.
     */
    @Bean
    ReactiveJwtDecoder jwtDecoder(NtanProperties properties) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
                .withJwkSetUri(properties.security().jwkSetUri())
                .build();
        decoder.setJwtValidator(jwtValidator(properties.security().issuer()));
        return decoder;
    }

    /** Validation par défaut (dates d'expiration, avec tolérance de 60 s) + émetteur attendu. */
    public static OAuth2TokenValidator<Jwt> jwtValidator(String issuer) {
        return JwtValidators.createDefaultWithIssuer(issuer);
    }
}
