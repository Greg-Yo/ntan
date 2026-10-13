package com.ntan.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class NtanPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(NtanProperties.class)
    static class TestConfig {
    }

    private static final String[] VALEURS_VALIDES = {
            "ntan.security.issuer=http://localhost:8180/realms/ntan",
            "ntan.security.jwk-set-uri=http://localhost:8180/realms/ntan/certs",
            "ntan.routes.tiers=http://localhost:8081",
            "ntan.routes.catalog=http://localhost:8082",
            "ntan.routes.sales=http://localhost:8083"
    };

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    private static String[] sans(String prefixe) {
        return java.util.Arrays.stream(VALEURS_VALIDES)
                .filter(v -> !v.startsWith(prefixe))
                .toArray(String[]::new);
    }

    private static String chaine(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable c = t; c != null; c = c.getCause()) {
            sb.append(c).append('\n');
        }
        return sb.toString();
    }

    @Test
    void valeursValidesSontLiees() {
        runner.withPropertyValues(VALEURS_VALIDES).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            NtanProperties p = ctx.getBean(NtanProperties.class);
            assertThat(p.security().issuer()).isEqualTo("http://localhost:8180/realms/ntan");
            assertThat(p.security().jwkSetUri()).isEqualTo("http://localhost:8180/realms/ntan/certs");
            assertThat(p.routes().tiers()).isEqualTo(URI.create("http://localhost:8081"));
            assertThat(p.routes().catalog()).isEqualTo(URI.create("http://localhost:8082"));
            assertThat(p.routes().sales()).isEqualTo(URI.create("http://localhost:8083"));
        });
    }

    @Test
    void emetteurVideFaitEchouerLeDemarrage() {
        runner.withPropertyValues(sans("ntan.security.issuer="))
                .withPropertyValues("ntan.security.issuer=")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(chaine(ctx.getStartupFailure())).contains("ntan.security.issuer");
                });
    }

    @Test
    void routeTiersAbsenteFaitEchouerLeDemarrage() {
        runner.withPropertyValues(sans("ntan.routes.tiers="))
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(chaine(ctx.getStartupFailure())).contains("ntan.routes.tiers");
                });
    }
}
