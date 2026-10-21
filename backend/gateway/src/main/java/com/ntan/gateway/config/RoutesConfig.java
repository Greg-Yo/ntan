package com.ntan.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@code /api/<service>/**} est relayé vers le service sans le segment {@code /api} :
 * {@code /api/tiers/123} devient {@code /tiers/123}. L'en-tête Authorization est conservé
 * (relais du jeton) : chaque service revalide le JWT.
 */
@Configuration
class RoutesConfig {

    @Bean
    RouteLocator ntanRoutes(RouteLocatorBuilder builder, NtanProperties properties) {
        NtanProperties.Routes routes = properties.routes();
        return builder.routes()
                .route("tiers", r -> r.path("/api/tiers/**").filters(f -> f.stripPrefix(1)).uri(routes.tiers()))
                .route("catalog", r -> r.path("/api/catalog/**").filters(f -> f.stripPrefix(1)).uri(routes.catalog()))
                .route("sales", r -> r.path("/api/sales/**").filters(f -> f.stripPrefix(1)).uri(routes.sales()))
                .build();
    }
}
