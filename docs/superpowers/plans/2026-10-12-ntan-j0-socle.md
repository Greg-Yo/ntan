# Ntan J0 — Socle : plan d'implémentation

> Les étapes utilisent des cases à cocher (`- [ ]`) pour suivre l'avancement. Chaque tâche se réalise sur sa propre branche (voir « Workflow Git »).

**Goal :** poser le socle technique de Ntan — monorepo, infrastructure Docker (PostgreSQL, RabbitMQ, Keycloak), gateway sécurisée par JWT, coquille Angular avec connexion Keycloak, CI — de sorte qu'un utilisateur se connecte dans Angular et qu'un appel authentifié traverse la gateway.

**Architecture :** la gateway Spring Cloud Gateway (WebFlux) valide les JWT émis par Keycloak (émetteur = URL publique, clés récupérées via l'URL interne), expose `GET /api/me` (identité + rôles Ntan) et déclare les routes vers les futurs services (`/api/tiers/**`, `/api/catalog/**`, `/api/sales/**`, préfixe `/api` retiré). Angular s'authentifie en OIDC (Authorization Code + PKCE), appelle `/api/me` via la gateway et construit son menu selon les rôles. Tout se lance avec `docker compose`.

**Tech Stack :** Java 25, Spring Boot 4.1.1, Spring Cloud 2025.1.3 (Gateway Server WebFlux), Spring Security 7 (resource server), Maven Wrapper 3.3.4 / Maven 3.9.16, Keycloak 26.7.5, PostgreSQL 18, RabbitMQ 4.2, Angular 22.2.2, Angular Material 22.2.2, angular-auth-oidc-client 22.0.1, Vitest (runner Angular par défaut), nginx 1.28, GitHub Actions.

**Spec :** `docs/superpowers/specs/2026-10-12-ntan-v1-design.md` (sections 3, 5.7, 6.1, 7.3, 8, 10).

## Global Constraints

- Java 25 ; Spring Boot `4.1.1` ; Spring Cloud `2025.1.3` ; Angular `22.2.2` ; Keycloak `26.7.5`.
- Package racine Java : `com.ntan.<module>`.
- Rôles Ntan : exactement `ADMIN`, `COMMERCIAL`, `MAGASINIER` (rôles de realm Keycloak, claim `realm_access.roles`).
- Émetteur JWT attendu : `http://localhost:8180/realms/ntan` (URL publique) ; clés via `jwk-set-uri` (interne en Docker : `http://keycloak:8080/realms/ntan/protocol/openid-connect/certs`).
- Convention de routage : `/api/<service>/**` → service, avec `StripPrefix=1` (ex. `/api/tiers/123` → `/tiers/123` dans `tiers-service`). Chaque service expose donc ses ressources sous son propre préfixe (`/tiers`, `/catalog`, `/sales`).
- Ports : gateway `8080` ; Keycloak `8180` (hôte) ; front conteneur `8000` ; `ng serve` `4200` ; services en local `8081` (tiers), `8082` (catalog), `8083` (sales) ; PostgreSQL `5432` ; RabbitMQ `5672` / `15672`.
- Interface en français, locale `fr-CM`.
- Les identifiants de test (realm Keycloak, PostgreSQL, RabbitMQ) sont des valeurs de **développement uniquement**, versionnées dans `infra/`.
- Messages de commit au format conventionnel (`feat:`, `fix:`, `build:`, `docs:`, `ci:`), en français, sans ligne de co-auteur.
- Commandes données pour Git Bash sous Windows ; Maven toujours via `./mvnw` depuis `backend/`.

## Workflow Git (une branche par tâche)

Dépôt GitHub : `origin`, branche principale `main`. Pour **chaque** tâche N (1 à 10) :

1. Mettre `main` à jour : `git checkout main && git pull`.
2. Passer sur la branche de la tâche (créée d'avance sur GitHub, nom exact dans l'en-tête de chaque tâche) et l'aligner sur `main` :
   `git checkout j0/tache-NN-<sujet> && git pull && git merge --ff-only main`
3. Réaliser les étapes ; les commits indiqués dans la tâche se font sur cette branche.
4. Pousser et ouvrir une pull request vers `main` :
   `git push -u origin HEAD && gh pr create --base main --fill`
5. Attendre que la CI soit verte (à partir de la Task 1, qui l'installe), puis **fusionner après accord du développeur** :
   `gh pr merge --merge --delete-branch`
6. Revenir sur `main` à jour avant la tâche suivante.

La Task 0 (prérequis du poste) ne produit aucun commit et n'a pas de branche.

## Review Focus

1. **Jeton émis via `localhost:8180` présenté à la gateway en Docker** (piège de l'émetteur) → doit être accepté ; un jeton dont l'émetteur est `http://keycloak:8080/...` doit être refusé. Testé en Task 3 (`JwtValidationTest`) et en Task 5 (`smoke-test.sh`).
2. **Jeton contenant les rôles techniques Keycloak** (`offline_access`, `uma_authorization`, `default-roles-ntan`) → `/api/me` ne renvoie que les rôles Ntan. Testé en Task 3.
3. **Jeton sans claim `realm_access`, ou claim mal formée** → 200 avec `roles: []`, jamais 500. Testé en Task 3.
4. **Endpoint non prévu (`/actuator/env`, chemin inconnu)** → 401 anonyme / 403 authentifié, aucune fuite d'information. Testé en Task 3.
5. **Utilisateur Keycloak sans aucun rôle Ntan** → menu vide, message explicite à l'accueil, pas d'écran cassé. Testé en Task 8.

---

## Structure des fichiers créés en J0

```
.gitignore, .gitattributes, README.md
.github/workflows/ci.yml
backend/
  .dockerignore
  .mvn/wrapper/maven-wrapper.properties
  mvnw, mvnw.cmd
  pom.xml                                   POM parent
  gateway/
    Dockerfile
    pom.xml
    src/main/java/com/ntan/gateway/
      GatewayApplication.java
      config/NtanProperties.java            propriétés ntan.security.* et ntan.routes.*
      config/SecurityConfig.java            chaîne de sécurité, décodeur JWT, validateur
      config/RoutesConfig.java              routes vers les services
      security/NtanRole.java                enum des rôles
      security/KeycloakRealmRolesConverter.java
      me/MeController.java                  GET /api/me
      me/CurrentUserResponse.java
    src/main/resources/application.yml
    src/test/java/com/ntan/gateway/
      GatewayHealthTest.java
      security/KeycloakRealmRolesConverterTest.java
      security/JwtValidationTest.java
      security/GatewaySecurityTest.java
      config/RoutesConfigTest.java
infra/
  docker-compose.yml
  postgres/init/01-databases.sql
  keycloak/ntan-realm.json
  smoke-test.sh
frontend/ntan-web/                          généré par Angular CLI, puis :
  proxy.conf.json, Dockerfile, nginx.conf, .dockerignore
  src/index.html, src/styles.scss, src/main.ts
  src/app/app.ts, app.html, app.config.ts, app.routes.ts
  src/app/core/auth/auth.config.ts
  src/app/core/auth/current-user.ts
  src/app/core/auth/current-user.service.ts (+ .spec.ts)
  src/app/core/auth/role.guard.ts (+ .spec.ts)
  src/app/core/layout/menu.ts (+ .spec.ts)
  src/app/core/layout/shell.ts
  src/app/features/accueil/accueil.ts (+ .spec.ts)
  src/app/features/a-venir/a-venir.ts
```

---

### Task 0 : Prérequis du poste (manuel)

Cette tâche est réalisée **par le développeur** ; l'exécutant vérifie seulement.

- [ ] **Step 1 : Installer le JDK 25** (Eclipse Temurin 25, https://adoptium.net) et le mettre dans le `PATH` / `JAVA_HOME`.
- [ ] **Step 2 : Installer Docker Desktop** (https://www.docker.com/products/docker-desktop), le démarrer.
- [ ] **Step 3 : Vérifier**

Run :
```bash
java -version
docker --version
docker compose version
node -v
```
Expected : `java version "25…"` (ou `openjdk version "25…"`), une version de Docker, Docker Compose v2, Node `v22.22.3+`, `v24.15+` ou `v26+`. Si une commande échoue : **stopper** et prévenir le développeur.

---

### Task 1 : Dépôt, POM parent, Maven Wrapper et CI backend

**Branche :** `j0/tache-01-socle-maven-ci`

**Files :**
- Create : `.gitignore`, `.gitattributes`, `backend/pom.xml`, `backend/.mvn/wrapper/maven-wrapper.properties`, `backend/mvnw`, `backend/mvnw.cmd`, `backend/.dockerignore`

**Interfaces :**
- Produces : POM parent `com.ntan:ntan-backend:0.1.0-SNAPSHOT` (packaging `pom`) dont héritent tous les modules ; propriétés `java.version=25`, `spring-cloud.version=2025.1.3` ; BOM Spring Cloud importé.

- [ ] **Step 1 : Créer `.gitattributes`** (les scripts shell doivent rester en LF, sinon `mvnw` casse dans Docker)

```gitattributes
* text=auto eol=lf
*.cmd text eol=crlf
*.bat text eol=crlf
*.jar binary
*.png binary
*.ico binary
```

- [ ] **Step 2 : Créer `.gitignore`**

```gitignore
# Java / Maven
target/
!.mvn/wrapper/maven-wrapper.jar

# IDE
.idea/
*.iml
.vscode/

# OS
.DS_Store
Thumbs.db

# Environnement local
infra/.env
```

- [ ] **Step 3 : Créer `backend/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.1</version>
        <relativePath/>
    </parent>

    <groupId>com.ntan</groupId>
    <artifactId>ntan-backend</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <packaging>pom</packaging>
    <name>Ntan - backend</name>

    <properties>
        <java.version>25</java.version>
        <spring-cloud.version>2025.1.3</spring-cloud.version>
    </properties>

    <modules>
    </modules>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

- [ ] **Step 4 : Installer Maven Wrapper** (script seul, sans jar)

Run (depuis la racine du dépôt) :
```bash
cd backend
curl -sSLo /tmp/mvnw.zip https://repo1.maven.org/maven2/org/apache/maven/wrapper/maven-wrapper-distribution/3.3.4/maven-wrapper-distribution-3.3.4-only-script.zip
unzip -o /tmp/mvnw.zip mvnw mvnw.cmd -d .
mkdir -p .mvn/wrapper
printf 'wrapperVersion=3.3.4\ndistributionType=only-script\ndistributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.16/apache-maven-3.9.16-bin.zip\n' > .mvn/wrapper/maven-wrapper.properties
chmod +x mvnw
```

- [ ] **Step 5 : Créer `backend/.dockerignore`**

```
**/target
**/.idea
**/*.iml
```

- [ ] **Step 6 : Vérifier**

Run : `cd backend && ./mvnw -B -v && ./mvnw -B validate`
Expected : `Apache Maven 3.9.16`, `Java version: 25…`, puis `BUILD SUCCESS`.

- [ ] **Step 7 : Créer `.github/workflows/ci.yml`** (job backend ; le job front est ajouté en Task 6)

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:

jobs:
  backend:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: backend
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '25'
          cache: maven
      - run: ./mvnw -B verify
```

- [ ] **Step 8 : Commit, pousser, ouvrir la PR**

```bash
git add .gitattributes .gitignore backend .github
git update-index --chmod=+x backend/mvnw
git commit -m "build: POM parent, Maven Wrapper et CI du backend"
git push -u origin HEAD
gh pr create --base main --fill
gh pr checks --watch
```
Expected : le job `backend` est vert.

---

### Task 2 : Module gateway minimal avec santé publique

**Branche :** `j0/tache-02-gateway-sante`

**Files :**
- Modify : `backend/pom.xml` (ajout du module)
- Create : `backend/gateway/pom.xml`, `backend/gateway/src/main/java/com/ntan/gateway/GatewayApplication.java`, `backend/gateway/src/main/java/com/ntan/gateway/config/NtanProperties.java`, `backend/gateway/src/main/resources/application.yml`
- Test : `backend/gateway/src/test/java/com/ntan/gateway/GatewayHealthTest.java`

**Interfaces :**
- Produces : `record NtanProperties(Security security, Routes routes)` avec `record Security(String issuer, String jwkSetUri)` et `record Routes(URI tiers, URI catalog, URI sales)`, lié au préfixe `ntan`.
- Produces : artefact `target/gateway.jar` (`finalName` = `gateway`).

- [ ] **Step 1 : Déclarer le module dans `backend/pom.xml`**

Remplacer `<modules>\n    </modules>` par :
```xml
    <modules>
        <module>gateway</module>
    </modules>
```

- [ ] **Step 2 : Créer `backend/gateway/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.ntan</groupId>
        <artifactId>ntan-backend</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>gateway</artifactId>
    <name>Ntan - gateway</name>

    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-gateway-server-webflux</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security-oauth2-resource-server</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security-oauth2-resource-server-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <finalName>gateway</finalName>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 3 : Écrire le test qui échoue** — `backend/gateway/src/test/java/com/ntan/gateway/GatewayHealthTest.java`

```java
package com.ntan.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

@SpringBootTest
class GatewayHealthTest {

    @Autowired
    ApplicationContext context;

    WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToApplicationContext(context)
                .apply(springSecurity())
                .configureClient()
                .build();
    }

    @Test
    void laSanteEstPubliqueEtUp() {
        client.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }
}
```

- [ ] **Step 4 : Lancer le test pour le voir échouer**

Run : `cd backend && ./mvnw -B -pl gateway test`
Expected : échec de compilation (`GatewayApplication` absente / aucune `@SpringBootConfiguration` trouvée).

- [ ] **Step 5 : Créer `GatewayApplication.java`**

```java
package com.ntan.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
```

- [ ] **Step 6 : Créer `config/NtanProperties.java`**

```java
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
```

- [ ] **Step 7 : Créer `src/main/resources/application.yml`**

```yaml
spring:
  application:
    name: gateway

server:
  port: 8080

ntan:
  security:
    issuer: ${NTAN_SECURITY_ISSUER:http://localhost:8180/realms/ntan}
    jwk-set-uri: ${NTAN_SECURITY_JWK_SET_URI:http://localhost:8180/realms/ntan/protocol/openid-connect/certs}
  routes:
    tiers: ${NTAN_ROUTES_TIERS:http://localhost:8081}
    catalog: ${NTAN_ROUTES_CATALOG:http://localhost:8082}
    sales: ${NTAN_ROUTES_SALES:http://localhost:8083}

management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      probes:
        enabled: true
```

- [ ] **Step 8 : Lancer le test**

Run : `cd backend && ./mvnw -B -pl gateway test`
Expected : `Tests run: 1, Failures: 0` — `BUILD SUCCESS`. (Sans configuration de sécurité explicite, Spring Boot protège tout sauf `/actuator/health` ; le test passe.)

- [ ] **Step 9 : Commit**

```bash
git add backend
git commit -m "feat(gateway): module gateway avec santé publique"
```

---

### Task 3 : Sécurité JWT de la gateway et endpoint `/api/me`

**Branche :** `j0/tache-03-gateway-securite-jwt`

**Files :**
- Create : `backend/gateway/src/main/java/com/ntan/gateway/security/NtanRole.java`, `security/KeycloakRealmRolesConverter.java`, `config/SecurityConfig.java`, `me/CurrentUserResponse.java`, `me/MeController.java`
- Test : `backend/gateway/src/test/java/com/ntan/gateway/security/KeycloakRealmRolesConverterTest.java`, `security/JwtValidationTest.java`, `security/GatewaySecurityTest.java`

**Interfaces :**
- Consumes : `NtanProperties.security().issuer()`, `.jwkSetUri()` (Task 2).
- Produces : `enum NtanRole { ADMIN, COMMERCIAL, MAGASINIER }` avec `static Optional<NtanRole> parse(String)` ; `KeycloakRealmRolesConverter implements Converter<Jwt, Collection<GrantedAuthority>>` (autorités `ROLE_<ROLE>`, rôles Ntan seulement) ; `static OAuth2TokenValidator<Jwt> SecurityConfig.jwtValidator(String issuer)` ; `GET /api/me` → `{"username": string, "nom": string, "roles": string[]}` (rôles triés, Ntan seulement). Ce contrat JSON est consommé par Angular (Task 7).

- [ ] **Step 1 : Écrire les tests du convertisseur** — `security/KeycloakRealmRolesConverterTest.java`

```java
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
```

- [ ] **Step 2 : Écrire les tests du validateur** — `security/JwtValidationTest.java`

```java
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
```

- [ ] **Step 3 : Écrire les tests HTTP** — `security/GatewaySecurityTest.java`

```java
package com.ntan.gateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

@SpringBootTest
class GatewaySecurityTest {

    @Autowired
    ApplicationContext context;

    WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToApplicationContext(context)
                .apply(springSecurity())
                .configureClient()
                .build();
    }

    @Test
    void meSansJetonRenvoie401() {
        client.get().uri("/api/me").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void meRenvoieLIdentiteEtUniquementLesRolesNtan() {
        client.mutateWith(mockJwt()
                        .jwt(j -> j.claim("preferred_username", "admin.ntan")
                                .claim("name", "Awa Admin")
                                .claim("realm_access", Map.of("roles",
                                        List.of("offline_access", "ADMIN", "default-roles-ntan"))))
                        .authorities(new KeycloakRealmRolesConverter()))
                .get().uri("/api/me")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.username").isEqualTo("admin.ntan")
                .jsonPath("$.nom").isEqualTo("Awa Admin")
                .jsonPath("$.roles.length()").isEqualTo(1)
                .jsonPath("$.roles[0]").isEqualTo("ADMIN");
    }

    @Test
    void meSansRealmAccessRenvoieDesRolesVides() {
        client.mutateWith(mockJwt()
                        .jwt(j -> j.claim("preferred_username", "sans.role"))
                        .authorities(new KeycloakRealmRolesConverter()))
                .get().uri("/api/me")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.username").isEqualTo("sans.role")
                .jsonPath("$.nom").isEqualTo("sans.role")
                .jsonPath("$.roles.length()").isEqualTo(0);
    }

    @Test
    void lesEndpointsNonPrevusSontFermes() {
        client.get().uri("/actuator/env").exchange().expectStatus().isUnauthorized();
        client.get().uri("/inconnu").exchange().expectStatus().isUnauthorized();

        client.mutateWith(mockJwt())
                .get().uri("/actuator/env")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void laSanteRestePublique() {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }
}
```

- [ ] **Step 4 : Lancer les tests pour les voir échouer**

Run : `cd backend && ./mvnw -B -pl gateway test`
Expected : échec de compilation (`KeycloakRealmRolesConverter`, `SecurityConfig` introuvables).

- [ ] **Step 5 : Créer `security/NtanRole.java`**

```java
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
```

- [ ] **Step 6 : Créer `security/KeycloakRealmRolesConverter.java`**

```java
package com.ntan.gateway.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Traduit la claim Keycloak {@code realm_access.roles} en autorités {@code ROLE_<ROLE>}.
 * Seuls les rôles Ntan sont conservés ; une claim absente ou mal formée donne zéro autorité.
 */
public class KeycloakRealmRolesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        if (!(jwt.getClaims().get("realm_access") instanceof Map<?, ?> realmAccess)) {
            return List.of();
        }
        if (!(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(NtanRole::parse)
                .flatMap(Optional::stream)
                .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .toList();
    }
}
```

- [ ] **Step 7 : Créer `config/SecurityConfig.java`**

```java
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
```

- [ ] **Step 8 : Créer `me/CurrentUserResponse.java`**

```java
package com.ntan.gateway.me;

import java.util.List;

/** Identité de l'utilisateur connecté, telle que l'application web l'affiche. */
public record CurrentUserResponse(String username, String nom, List<String> roles) {
}
```

- [ ] **Step 9 : Créer `me/MeController.java`**

```java
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
```

- [ ] **Step 10 : Lancer les tests**

Run : `cd backend && ./mvnw -B -pl gateway test`
Expected : 12 tests (1 santé + 3 convertisseur + 3 validateur + 5 sécurité), `Failures: 0, Errors: 0` — `BUILD SUCCESS`.

- [ ] **Step 11 : Commit**

```bash
git add backend/gateway
git commit -m "feat(gateway): validation des JWT Keycloak et endpoint /api/me"
```

---

### Task 4 : Routes de la gateway vers les services métier

**Branche :** `j0/tache-04-gateway-routes`

**Files :**
- Create : `backend/gateway/src/main/java/com/ntan/gateway/config/RoutesConfig.java`
- Test : `backend/gateway/src/test/java/com/ntan/gateway/config/RoutesConfigTest.java`

**Interfaces :**
- Consumes : `NtanProperties.routes()` (Task 2), chaîne de sécurité (Task 3).
- Produces : routes d'identifiants `tiers`, `catalog`, `sales` ; `/api/<service>/**` relayé avec `StripPrefix=1` et en-tête `Authorization` conservé (relais du jeton, consommé par les services en J1–J4).

- [ ] **Step 1 : Écrire le test qui échoue** — `config/RoutesConfigTest.java`

Le test démarre un faux service HTTP (serveur intégré au JDK) et remplace le décodeur JWT par un décodeur de test qui accepte le jeton `jeton-test`, afin de traverser la vraie chaîne de sécurité.

```java
package com.ntan.gateway.config;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
class RoutesConfigTest {

    private static final AtomicReference<String> cheminRecu = new AtomicReference<>();
    private static final AtomicReference<String> authorizationRecue = new AtomicReference<>();
    private static final HttpServer fauxServiceTiers = demarrerFauxService();

    private static HttpServer demarrerFauxService() {
        try {
            HttpServer serveur = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            serveur.createContext("/", echange -> {
                cheminRecu.set(echange.getRequestURI().getPath());
                authorizationRecue.set(echange.getRequestHeaders().getFirst("Authorization"));
                byte[] corps = "ok".getBytes(StandardCharsets.UTF_8);
                echange.sendResponseHeaders(200, corps.length);
                echange.getResponseBody().write(corps);
                echange.close();
            });
            serveur.start();
            return serveur;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void proprietes(DynamicPropertyRegistry registry) {
        registry.add("ntan.routes.tiers", () -> "http://localhost:" + fauxServiceTiers.getAddress().getPort());
    }

    @AfterAll
    static void arreter() {
        fauxServiceTiers.stop(0);
    }

    @TestConfiguration
    static class DecodeurDeTest {
        @Bean
        @Primary
        ReactiveJwtDecoder decodeurDeTest() {
            return jeton -> "jeton-test".equals(jeton)
                    ? Mono.just(Jwt.withTokenValue(jeton).header("alg", "none")
                            .claim("preferred_username", "commercial.ntan")
                            .claim("realm_access", Map.of("roles", List.of("COMMERCIAL")))
                            .build())
                    : Mono.error(new BadJwtException("jeton refusé"));
        }
    }

    @Autowired
    ApplicationContext context;

    @Autowired
    RouteLocator routeLocator;

    WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToApplicationContext(context).configureClient().build();
        cheminRecu.set(null);
        authorizationRecue.set(null);
    }

    @Test
    void declareLesTroisRoutesMetier() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();

        assertThat(routes)
                .extracting(Route::getId, r -> r.getUri().getPort())
                .contains(
                        tuple("tiers", fauxServiceTiers.getAddress().getPort()),
                        tuple("catalog", 8082),
                        tuple("sales", 8083));
    }

    @Test
    void relaieVersLeServiceSansLePrefixeApiEtAvecLeJeton() {
        client.get().uri("/api/tiers/123")
                .header("Authorization", "Bearer jeton-test")
                .exchange()
                .expectStatus().isOk();

        assertThat(cheminRecu.get()).isEqualTo("/tiers/123");
        assertThat(authorizationRecue.get()).isEqualTo("Bearer jeton-test");
    }

    @Test
    void refuseLAccesAuxRoutesSansJetonOuAvecUnJetonInvalide() {
        client.get().uri("/api/tiers/123").exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/sales/devis")
                .header("Authorization", "Bearer faux")
                .exchange()
                .expectStatus().isUnauthorized();

        assertThat(cheminRecu.get()).isNull();
    }
}
```

- [ ] **Step 2 : Lancer le test pour le voir échouer**

Run : `cd backend && ./mvnw -B -pl gateway test -Dtest=RoutesConfigTest`
Expected : FAIL — `declareLesTroisRoutesMetier` ne trouve pas les routes ; `relaieVersLeService…` reçoit 404.

- [ ] **Step 3 : Créer `config/RoutesConfig.java`**

```java
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
```

- [ ] **Step 4 : Lancer tous les tests du module**

Run : `cd backend && ./mvnw -B -pl gateway test`
Expected : tous les tests passent — `BUILD SUCCESS`.

- [ ] **Step 5 : Commit**

```bash
git add backend/gateway
git commit -m "feat(gateway): routes vers les services tiers, catalog et sales"
```

---

### Task 5 : Infrastructure Docker Compose et Keycloak

**Branche :** `j0/tache-05-infra-docker-keycloak`

**Files :**
- Create : `infra/docker-compose.yml`, `infra/postgres/init/01-databases.sql`, `infra/keycloak/ntan-realm.json`, `infra/smoke-test.sh`, `backend/gateway/Dockerfile`

**Interfaces :**
- Consumes : `backend/gateway` (Tasks 2–4), variables `NTAN_SECURITY_*`, `NTAN_ROUTES_*`.
- Produces : realm `ntan` ; client public `ntan-web` (PKCE S256, redirections `http://localhost:4200/*` et `http://localhost:8000/*`) ; client `ntan-dev-cli` (mot de passe direct, **scripts de dev uniquement**) ; utilisateurs de test `admin.ntan` / `admin123` (ADMIN, « Awa Admin »), `commercial.ntan` / `commercial123` (COMMERCIAL, « Paul Commercial »), `magasinier.ntan` / `magasinier123` (MAGASINIER, « Brice Magasinier ») ; bases PostgreSQL `tiers`, `catalog`, `sales` (utilisateur = mot de passe = nom de la base).

- [ ] **Step 1 : Créer `infra/postgres/init/01-databases.sql`**

```sql
-- Une base et un utilisateur par service (valeurs de développement uniquement).
CREATE USER tiers WITH PASSWORD 'tiers';
CREATE DATABASE tiers OWNER tiers;

CREATE USER catalog WITH PASSWORD 'catalog';
CREATE DATABASE catalog OWNER catalog;

CREATE USER sales WITH PASSWORD 'sales';
CREATE DATABASE sales OWNER sales;
```

- [ ] **Step 2 : Créer `infra/keycloak/ntan-realm.json`**

```json
{
  "realm": "ntan",
  "enabled": true,
  "displayName": "Ntan",
  "sslRequired": "external",
  "internationalizationEnabled": true,
  "supportedLocales": ["fr", "en"],
  "defaultLocale": "fr",
  "accessTokenLifespan": 300,
  "roles": {
    "realm": [
      { "name": "ADMIN", "description": "Administrateur : accès complet" },
      { "name": "COMMERCIAL", "description": "Tiers, devis et factures" },
      { "name": "MAGASINIER", "description": "Produits et stock" }
    ]
  },
  "clients": [
    {
      "clientId": "ntan-web",
      "name": "Ntan - application web",
      "enabled": true,
      "publicClient": true,
      "standardFlowEnabled": true,
      "directAccessGrantsEnabled": false,
      "implicitFlowEnabled": false,
      "redirectUris": ["http://localhost:4200/*", "http://localhost:8000/*"],
      "webOrigins": ["http://localhost:4200", "http://localhost:8000"],
      "attributes": {
        "pkce.code.challenge.method": "S256",
        "post.logout.redirect.uris": "http://localhost:4200/*##http://localhost:8000/*"
      }
    },
    {
      "clientId": "ntan-dev-cli",
      "name": "Ntan - scripts de développement (mot de passe direct)",
      "enabled": true,
      "publicClient": true,
      "standardFlowEnabled": false,
      "directAccessGrantsEnabled": true
    }
  ],
  "users": [
    {
      "username": "admin.ntan",
      "enabled": true,
      "firstName": "Awa",
      "lastName": "Admin",
      "email": "admin@ntan.local",
      "emailVerified": true,
      "credentials": [{ "type": "password", "value": "admin123", "temporary": false }],
      "realmRoles": ["ADMIN"]
    },
    {
      "username": "commercial.ntan",
      "enabled": true,
      "firstName": "Paul",
      "lastName": "Commercial",
      "email": "commercial@ntan.local",
      "emailVerified": true,
      "credentials": [{ "type": "password", "value": "commercial123", "temporary": false }],
      "realmRoles": ["COMMERCIAL"]
    },
    {
      "username": "magasinier.ntan",
      "enabled": true,
      "firstName": "Brice",
      "lastName": "Magasinier",
      "email": "magasinier@ntan.local",
      "emailVerified": true,
      "credentials": [{ "type": "password", "value": "magasinier123", "temporary": false }],
      "realmRoles": ["MAGASINIER"]
    }
  ]
}
```

- [ ] **Step 3 : Créer `backend/gateway/Dockerfile`** (contexte de build = `backend/`, pour disposer du POM parent et du wrapper)

```dockerfile
FROM eclipse-temurin:25-jdk AS build
WORKDIR /build
COPY . .
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw \
    && ./mvnw -B -q -pl gateway -am package -DskipTests

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /build/gateway/target/gateway.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

- [ ] **Step 4 : Créer `infra/docker-compose.yml`**

```yaml
name: ntan

services:
  postgres:
    image: postgres:18-alpine
    environment:
      POSTGRES_USER: ntan
      POSTGRES_PASSWORD: ntan
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql
      - ./postgres/init:/docker-entrypoint-initdb.d:ro
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ntan"]
      interval: 5s
      timeout: 3s
      retries: 20

  rabbitmq:
    image: rabbitmq:4.2-management-alpine
    environment:
      RABBITMQ_DEFAULT_USER: ntan
      RABBITMQ_DEFAULT_PASS: ntan
    ports:
      - "5672:5672"
      - "15672:15672"
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "-q", "ping"]
      interval: 10s
      timeout: 5s
      retries: 20

  keycloak:
    image: quay.io/keycloak/keycloak:26.7.5
    command: ["start-dev", "--import-realm"]
    environment:
      KC_BOOTSTRAP_ADMIN_USERNAME: admin
      KC_BOOTSTRAP_ADMIN_PASSWORD: admin
      # URL publique fixe : c'est l'émetteur (iss) des jetons, vu du navigateur.
      KC_HOSTNAME: http://localhost:8180
      # Autorise les appels internes (gateway -> http://keycloak:8080) pour lire les clés.
      KC_HOSTNAME_BACKCHANNEL_DYNAMIC: "true"
      KC_HEALTH_ENABLED: "true"
    ports:
      - "8180:8080"
    volumes:
      - ./keycloak:/opt/keycloak/data/import:ro
    healthcheck:
      test: ["CMD-SHELL", "exec 3<>/dev/tcp/127.0.0.1/9000 && printf 'GET /health/ready HTTP/1.1\\r\\nHost: localhost\\r\\nConnection: close\\r\\n\\r\\n' >&3 && grep -q UP <&3"]
      interval: 10s
      timeout: 5s
      retries: 30
      # Premier démarrage mesuré à ~6 min sur le poste de dev (préparation Quarkus + schéma) :
      # les échecs pendant cette période ne comptent pas.
      start_period: 600s

  gateway:
    build:
      context: ../backend
      dockerfile: gateway/Dockerfile
    environment:
      NTAN_SECURITY_ISSUER: http://localhost:8180/realms/ntan
      NTAN_SECURITY_JWK_SET_URI: http://keycloak:8080/realms/ntan/protocol/openid-connect/certs
      NTAN_ROUTES_TIERS: http://tiers-service:8080
      NTAN_ROUTES_CATALOG: http://catalog-service:8080
      NTAN_ROUTES_SALES: http://sales-service:8080
    ports:
      - "8080:8080"
    depends_on:
      keycloak:
        condition: service_healthy

volumes:
  pgdata:
```

- [ ] **Step 5 : Créer `infra/smoke-test.sh`**

```bash
#!/usr/bin/env bash
# Vérifie de bout en bout que la plateforme répond (à lancer après `docker compose up -d`).
set -euo pipefail

TOKEN_URL=http://localhost:8180/realms/ntan/protocol/openid-connect/token
GATEWAY=${GATEWAY:-http://localhost:8080}

ok()    { echo "OK     $1"; }
echec() { echo "ECHEC  $1"; exit 1; }

statut() { curl -s -o /dev/null -w '%{http_code}' "$@"; }

jeton() {
  curl -s -d client_id=ntan-dev-cli -d grant_type=password \
       -d "username=$1" -d "password=$2" "$TOKEN_URL" \
    | sed -E 's/.*"access_token":"([^"]+)".*/\1/'
}

verifier_statut() { # description attendu url [options curl...]
  local description=$1 attendu=$2; shift 2
  local obtenu; obtenu=$(statut "$@")
  [ "$obtenu" = "$attendu" ] && ok "$description ($obtenu)" || echec "$description : attendu $attendu, obtenu $obtenu"
}

verifier_me() { # description base_url utilisateur mot_de_passe role_attendu
  local corps; corps=$(curl -s -H "Authorization: Bearer $(jeton "$3" "$4")" "$2/api/me")
  echo "$corps" | grep -q "\"$5\"" && ok "$1 : $corps" || echec "$1 : $corps"
}

verifier_statut "santé de la gateway" 200 "$GATEWAY/actuator/health"
verifier_statut "/api/me sans jeton" 401 "$GATEWAY/api/me"
verifier_me "/api/me admin via gateway" "$GATEWAY" admin.ntan admin123 ADMIN
verifier_me "/api/me magasinier via gateway" "$GATEWAY" magasinier.ntan magasinier123 MAGASINIER

echo "Tous les contrôles sont passés."
```

- [ ] **Step 6 : Démarrer la plateforme**

Run :
```bash
chmod +x infra/smoke-test.sh
docker compose -f infra/docker-compose.yml up -d --build
docker compose -f infra/docker-compose.yml ps
```
Expected : `postgres`, `rabbitmq`, `keycloak` en `healthy` ; `gateway` en `running`. Le premier démarrage de Keycloak prend environ 6 minutes sur le poste de dev, et le premier build Maven de la gateway plusieurs minutes : attendre avant de conclure à une panne.

- [ ] **Step 7 : Vérifier les bases PostgreSQL**

Run : `docker compose -f infra/docker-compose.yml exec postgres psql -U ntan -lqt | cut -d'|' -f1 | grep -wE 'tiers|catalog|sales'`
Expected : trois lignes `tiers`, `catalog`, `sales`.

- [ ] **Step 8 : Lancer le test de fumée**

Run : `./infra/smoke-test.sh`
Expected :
```
OK     santé de la gateway (200)
OK     /api/me sans jeton (401)
OK     /api/me admin via gateway : {"username":"admin.ntan","nom":"Awa Admin","roles":["ADMIN"]}
OK     /api/me magasinier via gateway : {"username":"magasinier.ntan","nom":"Brice Magasinier","roles":["MAGASINIER"]}
Tous les contrôles sont passés.
```
Si `/api/me` renvoie 401 avec jeton : lire `docker compose -f infra/docker-compose.yml logs gateway` — un message mentionnant `iss` signale un problème de `KC_HOSTNAME` / `NTAN_SECURITY_ISSUER`, un message sur les clés signale un problème de `NTAN_SECURITY_JWK_SET_URI`.

- [ ] **Step 9 : Vérifier l'interface RabbitMQ**

Ouvrir http://localhost:15672 (ntan / ntan). Expected : tableau de bord RabbitMQ accessible.

- [ ] **Step 10 : Commit**

```bash
git add infra backend/gateway/Dockerfile
git update-index --chmod=+x infra/smoke-test.sh
git commit -m "build(infra): Docker Compose avec PostgreSQL, RabbitMQ, Keycloak et gateway"
```

---

### Task 6 : Projet Angular, Material, locale et proxy

**Branche :** `j0/tache-06-angular-projet`

**Files :**
- Create (CLI) : `frontend/ntan-web/**`
- Create : `frontend/ntan-web/proxy.conf.json`
- Modify : `frontend/ntan-web/angular.json` (via `ng config`), `src/index.html`, `src/styles.scss`, `src/app/app.ts`, `src/app/app.html`
- Delete : `src/app/app.spec.ts` (test du gabarit CLI, sans objet)

**Interfaces :**
- Produces : application Angular 22 (composants standalone, sans zone.js) buildable ; `/api/**` relayé vers `http://localhost:8080` par `ng serve` ; locale `fr-CM` enregistrée ; thème Material 3.

- [ ] **Step 1 : Générer le projet**

Run (depuis la racine du dépôt) :
```bash
npx -y @angular/cli@22.2.2 new ntan-web --directory frontend/ntan-web --style=scss --ssr=false --skip-git --interactive=false
```
Expected : projet créé, dépendances installées.

- [ ] **Step 2 : Installer les dépendances**

Run :
```bash
cd frontend/ntan-web
npm install angular-auth-oidc-client@22.0.1 @angular/material@22.2.2 @angular/cdk@22.2.2
```

- [ ] **Step 3 : Créer `proxy.conf.json` et le brancher**

```json
{
  "/api": {
    "target": "http://localhost:8080",
    "secure": false
  }
}
```

Run : `npx ng config projects.ntan-web.architect.serve.options.proxyConfig proxy.conf.json`

- [ ] **Step 4 : Remplacer `src/styles.scss`**

```scss
@use '@angular/material' as mat;

html {
  color-scheme: light;
  @include mat.theme((
    color: mat.$azure-palette,
    typography: Roboto,
    density: 0,
  ));
}

html,
body {
  height: 100%;
}

body {
  margin: 0;
  font-family: Roboto, 'Helvetica Neue', sans-serif;
  background: var(--mat-sys-surface);
  color: var(--mat-sys-on-surface);
}
```

- [ ] **Step 5 : Modifier `src/index.html`**

Mettre `<html lang="fr">`, le titre `<title>Ntan</title>`, et ajouter dans `<head>` :
```html
  <link rel="preconnect" href="https://fonts.gstatic.com">
  <link href="https://fonts.googleapis.com/css2?family=Roboto:wght@300;400;500&display=swap" rel="stylesheet">
  <link href="https://fonts.googleapis.com/icon?family=Material+Icons" rel="stylesheet">
```

- [ ] **Step 6 : Remplacer `src/app/app.ts` et `src/app/app.html`, supprimer `src/app/app.spec.ts`**

`src/app/app.ts` :
```ts
import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
})
export class App {}
```

`src/app/app.html` :
```html
<router-outlet />
```

Run : `rm -f src/app/app.spec.ts src/app/app.css src/app/app.scss`

- [ ] **Step 7 : Vérifier le build**

Run : `npx ng build`
Expected : `Application bundle generation complete.` sans erreur.

- [ ] **Step 8 : Commit**

```bash
cd ../..
git add frontend/ntan-web
git commit -m "feat(web): projet Angular avec Material, locale et proxy"
```

---

### Task 7 : Authentification OIDC et utilisateur courant

**Branche :** `j0/tache-07-angular-auth-oidc`

**Files :**
- Create : `src/app/core/auth/auth.config.ts`, `src/app/core/auth/current-user.ts`, `src/app/core/auth/current-user.service.ts`, `src/app/core/auth/role.guard.ts`
- Modify : `src/app/app.config.ts`, `.github/workflows/ci.yml` (racine du dépôt : job front)
- Test : `src/app/core/auth/current-user.service.spec.ts`, `src/app/core/auth/role.guard.spec.ts`

(chemins relatifs à `frontend/ntan-web/`)

**Interfaces :**
- Consumes : `GET /api/me` → `{username, nom, roles}` (Task 3).
- Produces : `type Role = 'ADMIN' | 'COMMERCIAL' | 'MAGASINIER'` ; `interface CurrentUser { username: string; nom: string; roles: Role[] }` ; `CurrentUserService` avec `currentUser: Signal<CurrentUser | null>` et `load(): Observable<CurrentUser>` ; `roleGuard(...roles: Role[]): CanActivateFn` (redirige vers `/accueil` si aucun rôle ne correspond).

- [ ] **Step 1 : Créer `current-user.ts`**

```ts
export type Role = 'ADMIN' | 'COMMERCIAL' | 'MAGASINIER';

export interface CurrentUser {
  username: string;
  nom: string;
  roles: Role[];
}
```

- [ ] **Step 2 : Écrire le test du service** — `current-user.service.spec.ts`

```ts
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { CurrentUserService } from './current-user.service';
import { CurrentUser } from './current-user';

describe('CurrentUserService', () => {
  const admin: CurrentUser = { username: 'admin.ntan', nom: 'Awa Admin', roles: ['ADMIN'] };
  let service: CurrentUserService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(CurrentUserService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('est vide avant chargement', () => {
    expect(service.currentUser()).toBeNull();
  });

  it('charge /api/me et mémorise l’utilisateur', async () => {
    const resultat = firstValueFrom(service.load());
    http.expectOne('/api/me').flush(admin);

    expect(await resultat).toEqual(admin);
    expect(service.currentUser()).toEqual(admin);
  });
});
```

- [ ] **Step 3 : Écrire le test du garde** — `role.guard.spec.ts`

```ts
import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { ActivatedRouteSnapshot, provideRouter, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { firstValueFrom, isObservable, Observable, of } from 'rxjs';
import { roleGuard } from './role.guard';
import { CurrentUserService } from './current-user.service';
import { CurrentUser } from './current-user';

describe('roleGuard', () => {
  const magasinier: CurrentUser = { username: 'magasinier.ntan', nom: 'Brice Magasinier', roles: ['MAGASINIER'] };

  function configurer(utilisateurCharge: CurrentUser | null, chargement: Observable<CurrentUser> = of(magasinier)) {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: CurrentUserService, useValue: { currentUser: signal(utilisateurCharge), load: () => chargement } },
      ],
    });
  }

  async function executer(...roles: Parameters<typeof roleGuard>) {
    const resultat = TestBed.runInInjectionContext(() =>
      roleGuard(...roles)({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    );
    return isObservable(resultat) ? firstValueFrom(resultat) : resultat;
  }

  it('autorise un utilisateur qui a l’un des rôles', async () => {
    configurer(magasinier);
    expect(await executer('ADMIN', 'MAGASINIER')).toBe(true);
  });

  it('redirige vers /accueil un utilisateur sans le rôle requis', async () => {
    configurer(magasinier);
    const resultat = await executer('ADMIN', 'COMMERCIAL');
    expect(resultat).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(Router).serializeUrl(resultat as UrlTree)).toBe('/accueil');
  });

  it('charge l’utilisateur s’il ne l’est pas encore', async () => {
    configurer(null, of(magasinier));
    expect(await executer('MAGASINIER')).toBe(true);
  });
});
```

- [ ] **Step 4 : Lancer les tests pour les voir échouer**

Run : `cd frontend/ntan-web && npx ng test --watch=false`
Expected : FAIL — `./current-user.service` et `./role.guard` introuvables.

- [ ] **Step 5 : Créer `current-user.service.ts`**

```ts
import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { CurrentUser } from './current-user';

/** Identité et rôles de l'utilisateur connecté, fournis par la gateway (GET /api/me). */
@Injectable({ providedIn: 'root' })
export class CurrentUserService {
  private readonly http = inject(HttpClient);
  private readonly utilisateur = signal<CurrentUser | null>(null);

  readonly currentUser = this.utilisateur.asReadonly();

  load(): Observable<CurrentUser> {
    return this.http.get<CurrentUser>('/api/me').pipe(tap((u) => this.utilisateur.set(u)));
  }
}
```

- [ ] **Step 6 : Créer `role.guard.ts`**

```ts
import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { CurrentUser, Role } from './current-user';
import { CurrentUserService } from './current-user.service';

/** Autorise la route si l'utilisateur possède au moins un des rôles ; sinon renvoie vers l'accueil. */
export function roleGuard(...roles: Role[]): CanActivateFn {
  return () => {
    const service = inject(CurrentUserService);
    const router = inject(Router);
    const verifier = (u: CurrentUser) => roles.some((r) => u.roles.includes(r)) || router.createUrlTree(['/accueil']);

    const utilisateur = service.currentUser();
    return utilisateur ? verifier(utilisateur) : service.load().pipe(map(verifier));
  };
}
```

- [ ] **Step 7 : Créer `auth.config.ts`**

```ts
import { PassedInitialConfig } from 'angular-auth-oidc-client';

/** Keycloak est toujours atteint via son URL publique, en développement comme en conteneur. */
export const authConfig: PassedInitialConfig = {
  config: {
    authority: 'http://localhost:8180/realms/ntan',
    redirectUrl: window.location.origin,
    postLogoutRedirectUri: window.location.origin,
    clientId: 'ntan-web',
    scope: 'openid profile email',
    responseType: 'code',
    silentRenew: true,
    useRefreshToken: true,
    secureRoutes: ['/api/'],
  },
};
```

- [ ] **Step 8 : Remplacer `src/app/app.config.ts`**

```ts
import { ApplicationConfig, LOCALE_ID, provideBrowserGlobalErrorListeners } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeFrCm from '@angular/common/locales/fr-CM';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { authInterceptor, provideAuth, withAppInitializerAuthCheck } from 'angular-auth-oidc-client';
import { routes } from './app.routes';
import { authConfig } from './core/auth/auth.config';

registerLocaleData(localeFrCm);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor()])),
    provideAuth(authConfig, withAppInitializerAuthCheck()),
    { provide: LOCALE_ID, useValue: 'fr-CM' },
  ],
};
```

- [ ] **Step 9 : Lancer les tests et le build**

Run : `npx ng test --watch=false && npx ng build`
Expected : 5 tests passent ; build OK.

- [ ] **Step 10 : Ajouter le job front à `.github/workflows/ci.yml`** (à la suite du job `backend`, même indentation). Il arrive ici et non en Task 6 car Vitest échoue lorsqu'aucun fichier de test n'existe.

```yaml
  frontend:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: frontend/ntan-web
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '24'
          cache: npm
          cache-dependency-path: frontend/ntan-web/package-lock.json
      - run: npm ci
      - run: npx ng test --watch=false
      - run: npx ng build
```

- [ ] **Step 11 : Commit**

```bash
cd ../..
git add frontend/ntan-web .github
git commit -m "feat(web): authentification OIDC, utilisateur courant et garde par rôle"
```

---

### Task 8 : Coquille applicative, menu par rôle et accueil

**Branche :** `j0/tache-08-angular-coquille-menu`

**Files :**
- Create : `src/app/core/layout/menu.ts`, `src/app/core/layout/shell.ts`, `src/app/features/accueil/accueil.ts`, `src/app/features/a-venir/a-venir.ts`
- Modify : `src/app/app.routes.ts`
- Test : `src/app/core/layout/menu.spec.ts`, `src/app/features/accueil/accueil.spec.ts`

**Interfaces :**
- Consumes : `CurrentUserService`, `roleGuard`, `Role` (Task 7) ; `OidcSecurityService.logoff()`.
- Produces : `interface MenuItem { libelle: string; lien: string; icone: string; roles: readonly Role[] }`, `MENU`, `menuPourRoles(roles: readonly Role[]): MenuItem[]` ; routes `/accueil`, `/tiers`, `/catalogue`, `/ventes`, `/parametres` (les quatre dernières pointent vers `AVenir`, remplacé aux jalons J1–J4).

- [ ] **Step 1 : Écrire le test du menu** — `menu.spec.ts`

```ts
import { menuPourRoles } from './menu';

describe('menuPourRoles', () => {
  const libelles = (roles: Parameters<typeof menuPourRoles>[0]) => menuPourRoles(roles).map((i) => i.libelle);

  it('montre tout à un administrateur', () => {
    expect(libelles(['ADMIN'])).toEqual(['Accueil', 'Tiers', 'Catalogue', 'Ventes', 'Paramètres']);
  });

  it('masque les paramètres au magasinier', () => {
    expect(libelles(['MAGASINIER'])).toEqual(['Accueil', 'Tiers', 'Catalogue', 'Ventes']);
  });

  it('ne montre rien à un utilisateur sans rôle Ntan', () => {
    expect(libelles([])).toEqual([]);
  });
});
```

- [ ] **Step 2 : Écrire le test de l'accueil** — `accueil.spec.ts`

```ts
import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { Accueil } from './accueil';
import { CurrentUserService } from '../../core/auth/current-user.service';
import { CurrentUser } from '../../core/auth/current-user';

describe('Accueil', () => {
  async function afficher(utilisateur: CurrentUser | null): Promise<string> {
    TestBed.configureTestingModule({
      imports: [Accueil],
      providers: [{ provide: CurrentUserService, useValue: { currentUser: signal(utilisateur) } }],
    });
    const fixture = TestBed.createComponent(Accueil);
    fixture.detectChanges();
    await fixture.whenStable();
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  it('salue l’utilisateur et affiche ses rôles', async () => {
    const texte = await afficher({ username: 'admin.ntan', nom: 'Awa Admin', roles: ['ADMIN'] });
    expect(texte).toContain('Bonjour Awa Admin');
    expect(texte).toContain('ADMIN');
  });

  it('prévient l’utilisateur qui n’a aucun rôle', async () => {
    const texte = await afficher({ username: 'sans.role', nom: 'sans.role', roles: [] });
    expect(texte).toContain('Aucun rôle Ntan ne vous est attribué');
  });

  it('indique le chargement tant que l’utilisateur est inconnu', async () => {
    expect(await afficher(null)).toContain('Chargement');
  });
});
```

- [ ] **Step 3 : Lancer les tests pour les voir échouer**

Run : `cd frontend/ntan-web && npx ng test --watch=false`
Expected : FAIL — `./menu` et `./accueil` introuvables.

- [ ] **Step 4 : Créer `core/layout/menu.ts`**

```ts
import { Role } from '../auth/current-user';

export interface MenuItem {
  libelle: string;
  lien: string;
  icone: string;
  roles: readonly Role[];
}

const TOUS: readonly Role[] = ['ADMIN', 'COMMERCIAL', 'MAGASINIER'];

/** Droits de lecture par module (spec §5.7). */
export const MENU: readonly MenuItem[] = [
  { libelle: 'Accueil', lien: '/accueil', icone: 'home', roles: TOUS },
  { libelle: 'Tiers', lien: '/tiers', icone: 'groups', roles: TOUS },
  { libelle: 'Catalogue', lien: '/catalogue', icone: 'inventory_2', roles: TOUS },
  { libelle: 'Ventes', lien: '/ventes', icone: 'receipt_long', roles: TOUS },
  { libelle: 'Paramètres', lien: '/parametres', icone: 'settings', roles: ['ADMIN', 'COMMERCIAL'] },
];

export function menuPourRoles(roles: readonly Role[]): MenuItem[] {
  return MENU.filter((item) => item.roles.some((r) => roles.includes(r)));
}
```

- [ ] **Step 5 : Créer `features/accueil/accueil.ts`**

```ts
import { Component, inject } from '@angular/core';
import { MatChipsModule } from '@angular/material/chips';
import { CurrentUserService } from '../../core/auth/current-user.service';

@Component({
  selector: 'app-accueil',
  imports: [MatChipsModule],
  template: `
    @if (utilisateur(); as u) {
      <h1>Bonjour {{ u.nom }}</h1>
      @if (u.roles.length > 0) {
        <p>Vos rôles :</p>
        <mat-chip-set aria-label="Rôles">
          @for (role of u.roles; track role) {
            <mat-chip>{{ role }}</mat-chip>
          }
        </mat-chip-set>
      } @else {
        <p class="alerte">Aucun rôle Ntan ne vous est attribué. Contactez un administrateur.</p>
      }
    } @else {
      <p>Chargement…</p>
    }
  `,
  styles: `
    .alerte { color: var(--mat-sys-error); }
  `,
})
export class Accueil {
  protected readonly utilisateur = inject(CurrentUserService).currentUser;
}
```

- [ ] **Step 6 : Créer `features/a-venir/a-venir.ts`**

```ts
import { Component } from '@angular/core';

/** Page provisoire des modules livrés aux jalons suivants. */
@Component({
  selector: 'app-a-venir',
  template: `<p>Ce module arrive dans un prochain jalon.</p>`,
})
export class AVenir {}
```

- [ ] **Step 7 : Créer `core/layout/shell.ts`**

```ts
import { Component, computed, inject, OnInit } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { CurrentUserService } from '../auth/current-user.service';
import { menuPourRoles } from './menu';

@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet, RouterLink, RouterLinkActive,
    MatToolbarModule, MatSidenavModule, MatListModule, MatIconModule, MatButtonModule,
  ],
  template: `
    <mat-toolbar class="barre">
      <span class="titre">Ntan</span>
      <span class="espace"></span>
      @if (utilisateur(); as u) {
        <span class="nom">{{ u.nom }}</span>
      }
      <button mat-icon-button (click)="deconnecter()" aria-label="Se déconnecter">
        <mat-icon>logout</mat-icon>
      </button>
    </mat-toolbar>
    <mat-sidenav-container class="conteneur">
      <mat-sidenav mode="side" opened class="menu">
        <mat-nav-list>
          @for (item of menu(); track item.lien) {
            <a mat-list-item [routerLink]="item.lien" routerLinkActive #actif="routerLinkActive" [activated]="actif.isActive">
              <mat-icon matListItemIcon>{{ item.icone }}</mat-icon>
              <span matListItemTitle>{{ item.libelle }}</span>
            </a>
          }
        </mat-nav-list>
      </mat-sidenav>
      <mat-sidenav-content class="contenu">
        <router-outlet />
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
  styles: `
    :host { display: flex; flex-direction: column; height: 100%; }
    .barre { background: var(--mat-sys-primary); color: var(--mat-sys-on-primary); }
    .titre { font-weight: 500; }
    .espace { flex: 1; }
    .nom { margin-right: 8px; font-size: 14px; }
    .conteneur { flex: 1; }
    .menu { width: 220px; }
    .contenu { padding: 24px; }
  `,
})
export class Shell implements OnInit {
  private readonly currentUserService = inject(CurrentUserService);
  private readonly oidc = inject(OidcSecurityService);

  protected readonly utilisateur = this.currentUserService.currentUser;
  protected readonly menu = computed(() => menuPourRoles(this.utilisateur()?.roles ?? []));

  ngOnInit(): void {
    this.currentUserService.load().subscribe();
  }

  protected deconnecter(): void {
    this.oidc.logoff().subscribe();
  }
}
```

- [ ] **Step 8 : Remplacer `src/app/app.routes.ts`**

```ts
import { Routes } from '@angular/router';
import { autoLoginPartialRoutesGuard } from 'angular-auth-oidc-client';
import { Shell } from './core/layout/shell';
import { roleGuard } from './core/auth/role.guard';

const aVenir = () => import('./features/a-venir/a-venir').then((m) => m.AVenir);

export const routes: Routes = [
  {
    path: '',
    component: Shell,
    canActivate: [autoLoginPartialRoutesGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'accueil' },
      { path: 'accueil', loadComponent: () => import('./features/accueil/accueil').then((m) => m.Accueil) },
      { path: 'tiers', loadComponent: aVenir, canActivate: [roleGuard('ADMIN', 'COMMERCIAL', 'MAGASINIER')] },
      { path: 'catalogue', loadComponent: aVenir, canActivate: [roleGuard('ADMIN', 'COMMERCIAL', 'MAGASINIER')] },
      { path: 'ventes', loadComponent: aVenir, canActivate: [roleGuard('ADMIN', 'COMMERCIAL', 'MAGASINIER')] },
      { path: 'parametres', loadComponent: aVenir, canActivate: [roleGuard('ADMIN', 'COMMERCIAL')] },
    ],
  },
  { path: '**', redirectTo: '' },
];
```

- [ ] **Step 9 : Lancer les tests et le build**

Run : `npx ng test --watch=false && npx ng build`
Expected : 11 tests passent ; build OK.

- [ ] **Step 10 : Vérification manuelle dans le navigateur**

Pré-requis : `docker compose -f infra/docker-compose.yml up -d` (Task 5) en cours.
Run : `npx ng serve` puis ouvrir http://localhost:4200.
Expected :
1. Redirection vers la page de connexion Keycloak « Ntan » (en français).
2. Connexion `magasinier.ntan` / `magasinier123` → « Bonjour Brice Magasinier », chip `MAGASINIER`, menu sans « Paramètres ».
3. Saisir http://localhost:4200/parametres → retour sur l'accueil.
4. Déconnexion → retour à la page de connexion ; connexion `admin.ntan` / `admin123` → menu complet avec « Paramètres ».

- [ ] **Step 11 : Commit**

```bash
cd ../..
git add frontend/ntan-web
git commit -m "feat(web): coquille applicative, menu par rôle et page d'accueil"
```

---

### Task 9 : Conteneur du front (nginx) dans Docker Compose

**Branche :** `j0/tache-09-front-nginx`

**Files :**
- Create : `frontend/ntan-web/Dockerfile`, `frontend/ntan-web/nginx.conf`, `frontend/ntan-web/.dockerignore`
- Modify : `infra/docker-compose.yml`, `infra/smoke-test.sh`

**Interfaces :**
- Consumes : build Angular (`dist/ntan-web/browser`), gateway (`gateway:8080`).
- Produces : service `web` sur http://localhost:8000, qui relaie `/api/**` vers la gateway (même origine).

- [ ] **Step 1 : Créer `frontend/ntan-web/.dockerignore`**

```
node_modules
dist
.angular
```

- [ ] **Step 2 : Créer `frontend/ntan-web/nginx.conf`**

```nginx
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;

    location /api/ {
        proxy_pass http://gateway:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

- [ ] **Step 3 : Créer `frontend/ntan-web/Dockerfile`**

```dockerfile
FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npx ng build --configuration production

FROM nginx:1.28-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/ntan-web/browser /usr/share/nginx/html
EXPOSE 80
```

- [ ] **Step 4 : Ajouter le service `web` à `infra/docker-compose.yml`** (avant `volumes:`)

```yaml
  web:
    build:
      context: ../frontend/ntan-web
    ports:
      - "8000:80"
    depends_on:
      - gateway
```

- [ ] **Step 5 : Étendre `infra/smoke-test.sh`** — ajouter avant la ligne `echo "Tous les contrôles sont passés."` :

```bash
WEB=${WEB:-http://localhost:8000}
verifier_statut "page d'accueil servie par nginx" 200 "$WEB/"
verifier_statut "route Angular profonde (repli SPA)" 200 "$WEB/parametres"
verifier_me "/api/me commercial via nginx" "$WEB" commercial.ntan commercial123 COMMERCIAL
```

- [ ] **Step 6 : Reconstruire et vérifier**

Run :
```bash
docker compose -f infra/docker-compose.yml up -d --build
./infra/smoke-test.sh
```
Expected : tous les contrôles `OK`, dont les trois nouveaux.

- [ ] **Step 7 : Vérification manuelle**

Ouvrir http://localhost:8000, se connecter avec `commercial.ntan` / `commercial123`. Expected : « Bonjour Paul Commercial », menu avec « Paramètres ».

- [ ] **Step 8 : Commit**

```bash
git add frontend/ntan-web infra
git commit -m "build(web): conteneur nginx du front relayant l'API vers la gateway"
```

---

### Task 10 : README et vérification complète

**Branche :** `j0/tache-10-readme`

**Files :**
- Create : `README.md`

**Interfaces :**
- Consumes : `./mvnw -B verify` (backend), `npm ci`, `ng test`, `ng build` (front).

- [ ] **Step 1 : Créer `README.md`**

````markdown
# Ntan

Mini-ERP de gestion de PME adapté au Cameroun (FCFA, TVA 19,25 %, NIU/RCCM, Mobile Money).
Projet d'apprentissage : microservices Spring Boot, Angular, Keycloak, RabbitMQ.

Conception : [docs/superpowers/specs/2026-10-12-ntan-v1-design.md](docs/superpowers/specs/2026-10-12-ntan-v1-design.md)

## Prérequis

- JDK 25
- Node.js 22.22+, 24.15+ ou 26+
- Docker Desktop

## Démarrer toute la plateforme

```bash
docker compose -f infra/docker-compose.yml up -d --build
./infra/smoke-test.sh
```

| Adresse | Rôle |
|---|---|
| http://localhost:8000 | Application web |
| http://localhost:8080 | Gateway (API) |
| http://localhost:8180 | Keycloak (console : admin / admin) |
| http://localhost:15672 | RabbitMQ (ntan / ntan) |

## Comptes de test (développement uniquement)

| Utilisateur | Mot de passe | Rôle |
|---|---|---|
| admin.ntan | admin123 | ADMIN |
| commercial.ntan | commercial123 | COMMERCIAL |
| magasinier.ntan | magasinier123 | MAGASINIER |

Le client Keycloak `ntan-dev-cli` (mot de passe direct) ne sert qu'aux scripts de développement.

## Développer

```bash
# Infrastructure seule
docker compose -f infra/docker-compose.yml up -d postgres rabbitmq keycloak

# Gateway depuis le poste
cd backend && ./mvnw -pl gateway spring-boot:run

# Front avec rechargement à chaud (http://localhost:4200, /api relayé vers :8080)
cd frontend/ntan-web && npx ng serve
```

## Tests

```bash
cd backend && ./mvnw verify
cd frontend/ntan-web && npx ng test --watch=false
```
````

- [ ] **Step 2 : Vérifier la suite complète en local**

Run :
```bash
(cd backend && ./mvnw -B verify) && (cd frontend/ntan-web && npx ng test --watch=false && npx ng build)
```
Expected : `BUILD SUCCESS` côté backend ; tous les tests front passent ; build OK.

- [ ] **Step 3 : Commit**

```bash
git add README.md
git commit -m "docs: README de démarrage"
```

---

## Critère de fin du jalon J0

- `./infra/smoke-test.sh` passe entièrement.
- Connexion dans Angular (http://localhost:4200 et http://localhost:8000) avec les trois comptes ; menu adapté au rôle.
- `./mvnw verify` et `ng test` passent ; la CI est verte au premier push.
