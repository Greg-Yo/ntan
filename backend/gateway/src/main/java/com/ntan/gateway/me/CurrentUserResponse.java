package com.ntan.gateway.me;

import java.util.List;

/** Identité de l'utilisateur connecté, telle que l'application web l'affiche. */
public record CurrentUserResponse(String username, String nom, List<String> roles) {
}
