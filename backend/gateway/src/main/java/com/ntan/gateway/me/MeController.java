package com.ntan.gateway.me;

import com.ntan.gateway.security.NtanRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
class MeController {

    private static final String PREFIXE_ROLE = "ROLE_";

    @GetMapping("/api/me")
    CurrentUserResponse me(JwtAuthenticationToken authentication) {
        String username = authentication.getToken().getClaimAsString("preferred_username");
        String nom = Optional.ofNullable(authentication.getToken().getClaimAsString("name")).orElse(username);
        var roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith(PREFIXE_ROLE))
                .map(a -> a.substring(PREFIXE_ROLE.length()))
                .map(NtanRole::parse)
                .flatMap(Optional::stream)
                .map(NtanRole::name)
                .sorted()
                .toList();
        return new CurrentUserResponse(username, nom, roles);
    }
}
