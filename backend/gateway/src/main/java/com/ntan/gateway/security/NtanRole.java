package com.ntan.gateway.security;

import java.util.Arrays;
import java.util.Optional;

/** Rôles métier de Ntan, définis comme rôles de realm dans Keycloak. */
public enum NtanRole {
    ADMIN, COMMERCIAL, MAGASINIER;

    public static Optional<NtanRole> parse(String valeur) {
        return Arrays.stream(values()).filter(r -> r.name().equals(valeur)).findFirst();
    }
}
