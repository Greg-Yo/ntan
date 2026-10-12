# Ntan — Spécification de conception V1

- **Date :** 2026-10-12
- **Statut :** en revue
- **Périmètre :** V1 (backend microservices + application web). Application mobile hors périmètre (V2).

---

## 1. Contexte et objectifs

Ntan est un mini-ERP de gestion d'une PME. C'est un **projet d'apprentissage et de portfolio** : il doit démontrer la maîtrise d'une stack « entreprise » (Java / Spring Boot / Angular) et d'une architecture microservices réaliste, tout en restant livrable par une seule personne.

**Critères de succès de la V1 :**

1. Le flux métier complet fonctionne de bout en bout : client → devis → facture → validation → sortie de stock → paiement.
2. La plateforme se lance en une commande (`docker compose up`) avec des données de démonstration.
3. Les choix d'architecture sont justifiés et documentés (ADR), et vérifiés automatiquement (tests, ArchUnit).
4. Aucune perte ni duplication d'événement entre services, numérotation des factures conforme (continue, chronologique).

**Hypothèses :**

- Une seule entreprise par installation (pas de multi-société / multi-tenant).
- Le développeur connaît les bases de Java et d'Angular.
- Contexte français : TVA à 20 / 10 / 5,5 / 2,1 %, mentions légales de facture françaises.

## 2. Périmètre

### Inclus en V1

| Module | Contenu |
|---|---|
| Tiers | Clients et fournisseurs, contacts |
| Catalogue & stock | Produits, mouvements de stock, alertes de seuil |
| Ventes | Devis, factures, paiement (total), PDF |
| Utilisateurs & rôles | Authentification et rôles via Keycloak |
| Tableau de bord | CA, factures en retard, devis en attente, alertes stock |

### Exclu de la V1 (YAGNI)

Commandes clients et bons de livraison, achats / commandes fournisseurs, avoirs, paiements partiels, multi-devises, catégories de produits, multi-entrepôts, tarifs par client, application mobile, service de reporting dédié, circuit breaker, Spring Cloud Contract, Eureka, Config Server, Zipkin.

## 3. Architecture globale

```
                ┌──────────────┐     ┌──────────────┐
                │ Angular (web)│     │ Mobile (V2)  │
                └──────┬───────┘     └──────┬───────┘
                       └────────┬───────────┘
                                ▼
                     ┌────────────────────┐   valide JWT ◄── Keycloak (OIDC)
                     │ Spring Cloud       │
                     │ Gateway  (:8080)   │
                     └──┬────────┬─────┬──┘
              REST      ▼        ▼     ▼
          ┌──────────────┐ ┌───────────────┐ ┌──────────────┐
          │ tiers-service│ │catalog-service│ │ sales-service│
          └──────┬───────┘ └──────┬────────┘ └──────┬───────┘
                 │  PostgreSQL (1 base logique par service)  │
                 └────────────── RabbitMQ (événements) ──────┘
```

### 3.1 Stack technique

Versions exactes figées lors de l'écriture du plan J0 (dernières versions stables à cette date).

- **Backend :** Java 21 LTS, Spring Boot 3.x, Spring Web, Spring Data JPA, Spring Security (resource server OAuth2), Spring AMQP, Flyway, MapStruct, springdoc-openapi, Micrometer Tracing, Spring Boot Actuator.
- **Gateway :** Spring Cloud Gateway.
- **Identité :** Keycloak (realm `ntan`, versionné en JSON).
- **Messagerie :** RabbitMQ.
- **Base de données :** PostgreSQL (une instance, une base par service : `tiers`, `catalog`, `sales`).
- **Web :** Angular (dernière version stable), composants standalone, Angular Material, bibliothèque client OIDC pour Keycloak.
- **PDF :** génération côté `sales-service` à partir d'un template HTML (bibliothèque choisie dans le plan J3).
- **Tests :** JUnit 5, AssertJ, Testcontainers, WireMock, ArchUnit, spring-security-test, tests unitaires Angular, Playwright.
- **Outillage :** Maven (POM parent), Docker Compose, GitHub Actions.

### 3.2 Organisation du dépôt (monorepo)

```
ntan/
├── backend/
│   ├── pom.xml              ← POM parent (gestion des versions)
│   ├── gateway/
│   ├── tiers-service/
│   ├── catalog-service/
│   ├── sales-service/
│   └── ntan-events/         ← uniquement les contrats d'événements (records Java)
├── frontend/ntan-web/
├── mobile/                  ← vide (V2)
├── infra/
│   ├── docker-compose.yml
│   └── keycloak/ntan-realm.json
└── docs/
    ├── adr/
    └── superpowers/specs/
```

### 3.3 Principes

- Chaque service possède sa base, ses migrations Flyway et son API ; **aucun service ne lit la base d'un autre**.
- **Aucune clé étrangère inter-services** : on référence par UUID.
- Pas de bibliothèque partagée fourre-tout ; seul `ntan-events` est partagé.
- Chaque service est démarrable seul depuis l'IDE (profil `local`) ou dans Docker Compose.

## 4. Architecture interne des services

Le style est choisi **selon la richesse des règles métier** (décision consignée dans un ADR).

| Service | Règles métier | Architecture |
|---|---|---|
| `tiers-service` | Faibles (CRUD + validations) | En couches |
| `catalog-service` | Moyennes (mouvements, stock, seuils) | Hexagonale |
| `sales-service` | Fortes (cycles de vie, calculs, numérotation, événements) | Hexagonale |
| `gateway` | Aucune | Configuration |

### 4.1 Hexagonale (`catalog-service`, `sales-service`)

```
com.ntan.<service>/
├── domain/              Java pur : modèles, value objects, exceptions métier
├── application/
│   ├── port/in/         interfaces des cas d'usage
│   ├── port/out/        interfaces vers l'extérieur (repository, publication, clients)
│   └── service/         implémentation des cas d'usage (@Transactional ici)
└── infrastructure/
    ├── web/             contrôleurs REST, DTO, mappers
    ├── persistence/     entités JPA, adaptateurs repository, mappers
    ├── messaging/       publication / consommation RabbitMQ
    └── client/          clients REST vers les autres services
```

**Règles vérifiées par ArchUnit (le build échoue sinon) :**

- `domain` n'importe ni `org.springframework..`, ni `jakarta.persistence..`, ni `application..`, ni `infrastructure..`.
- `application` n'importe pas `infrastructure..`.

### 4.2 En couches (`tiers-service`)

```
com.ntan.tiers/
├── web/          contrôleurs + DTO
├── service/
├── repository/
└── entity/
```

## 5. Modèle de données

### 5.1 Règles communes

- Identifiants : UUID.
- Champs d'audit : `createdAt`, `createdBy` (issu du JWT), `updatedAt`, `updatedBy`.
- `@Version` (verrouillage optimiste) sur les agrégats modifiables uniquement.
- Montants : `NUMERIC(12,2)` / `BigDecimal`. Quantités : `NUMERIC(12,3)` / `BigDecimal`. Jamais de `double`.

### 5.2 `tiers-service`

**Tiers**

| Champ | Type | Règle |
|---|---|---|
| code | texte | Unique, généré : `CLI-0001` / `FOU-0001` selon le type principal |
| type | enum | `CLIENT`, `FOURNISSEUR`, `LES_DEUX` |
| raisonSociale | texte | Obligatoire |
| siret | texte | Optionnel, 14 chiffres si présent |
| numeroTvaIntra | texte | Optionnel |
| email, telephone | texte | Optionnels, format validé |
| adresse | embarqué | rue, codePostal, ville, pays (défaut `FR`) |
| delaiPaiementJours | entier | Défaut 30, ≥ 0 |
| actif | booléen | Désactivation au lieu de suppression |

**Contact** (appartient à un Tiers) : nom, prénom, fonction, email, téléphone.

Un tiers n'est jamais supprimé physiquement ; il est désactivé.

### 5.3 `catalog-service`

**Produit**

| Champ | Type | Règle |
|---|---|---|
| reference | texte | Unique |
| libelle | texte | Obligatoire |
| description | texte | Optionnel |
| unite | enum | `PIECE`, `KG`, `LITRE`, `METRE`, `HEURE`, `FORFAIT` |
| prixVenteHT | montant | ≥ 0 |
| tauxTVA | enum | `20`, `10`, `5.5`, `2.1` |
| suivreStock | booléen | Faux pour une prestation |
| stockActuel | quantité | Dénormalisé, mis à jour dans la transaction de chaque mouvement |
| seuilAlerte | quantité | ≥ 0 |
| actif | booléen | |

**MouvementStock** (immuable) : produitId, type (`ENTREE`, `SORTIE`, `AJUSTEMENT`), quantite (> 0 ; pour `AJUSTEMENT`, valeur signée), motif, documentSource (ex. `FAC-2026-0012`, optionnel), date, auteur.

**EvenementTraite** : eventId (clé primaire), dateTraitement — garantit l'idempotence de la consommation.

**Règles de stock :**

- Sortie ou ajustement **manuel** (magasinier) : refusé s'il rend le stock négatif (422).
- Sortie **issue d'une vente** (événement) : toujours appliquée, même si le stock devient négatif.
- Un produit est **en alerte** si `suivreStock` et `stockActuel ≤ seuilAlerte`.

### 5.4 `sales-service`

**ParametresEntreprise** (ligne unique, modifiable par ADMIN) : raisonSociale, formeJuridique, capital, siret, numeroTvaIntra, rcs, adresse, email, telephone, iban, bic, mentionsLegales (texte : pénalités de retard, indemnité forfaitaire de 40 €, escompte).

**Devis**

| Champ | Règle |
|---|---|
| numero | `DEV-AAAA-NNNN`, attribué à l'envoi |
| clientId + copie client | voir copie figée ci-dessous |
| dateEmission | Fixée à l'envoi |
| dateValidite | Défaut : émission + 30 jours |
| statut | `BROUILLON → ENVOYE → ACCEPTE | REFUSE` |
| lignes, totaux, ventilation TVA | |

Statut **calculé** (non stocké) : *expiré* = `ENVOYE` et `dateValidite < aujourd'hui`. Un devis expiré ne peut pas être accepté.

**Facture**

| Champ | Règle |
|---|---|
| numero | `FAC-AAAA-NNNN`, attribué à la validation, continu, sans trou |
| devisId | Optionnel |
| copie client, copie émetteur | Émetteur figé à la validation |
| dateEmission | **Fixée au jour de la validation** |
| dateEcheance | dateEmission + délai de paiement du client |
| statut | `BROUILLON → VALIDEE → PAYEE` |
| datePaiement, modePaiement | `VIREMENT`, `CHEQUE`, `ESPECES`, `CARTE` |
| validePar | Utilisateur ayant validé |
| lignes, totaux, ventilation TVA | |

Statut **calculé** : *en retard* = `VALIDEE` et `dateEcheance < aujourd'hui`.

Une facture `VALIDEE` ou `PAYEE` est immuable et non supprimable. Un brouillon peut être supprimé.

**Copie figée client** : raisonSociale, adresse, siret, numeroTvaIntra, delaiPaiementJours.

**LigneDocument** : ordre, produitId, copie produit (référence, libellé, unité, prixUnitaireHT, tauxTVA), quantite (> 0), remisePourcent (0–100), totalHT.

**VentilationTVA** : taux, baseHT, montantTVA.

**Totaux** : totalHT, totalTVA, totalTTC.

**CompteurNumerotation** : (typeDocument, annee) → dernierNumero. Incrémenté sous verrou de ligne (`SELECT … FOR UPDATE`).

**Outbox** : id, eventId, type, payload (JSON), dateCreation, datePublication (null tant que non publié).

### 5.5 Règles de calcul (objet métier pur, testé exhaustivement)

1. `totalHT ligne = arrondi(quantite × prixUnitaireHT × (1 − remisePourcent/100), 2)`
2. Pour chaque taux : `baseHT = Σ totalHT des lignes de ce taux` ; `montantTVA = arrondi(baseHT × taux/100, 2)`
3. `totalHT = Σ baseHT` ; `totalTVA = Σ montantTVA` ; `totalTTC = totalHT + totalTVA`
4. Arrondi : `HALF_UP` à 2 décimales.

### 5.6 Rafraîchissement des copies figées

- **Brouillon** (devis ou facture) : copies client et produits rafraîchies à chaque enregistrement.
- **Facture issue d'un devis** : prix et lignes du devis conservés (valeur contractuelle) ; copie client rafraîchie à chaque enregistrement du brouillon.
- **Validation / envoi** : aucune copie n'est rafraîchie ; la validation n'appelle aucun autre service.

### 5.7 Utilisateurs et rôles (Keycloak)

Aucune table utilisateur dans les services.

| Rôle | Tiers | Produits / stock | Devis / factures | Paramètres entreprise |
|---|---|---|---|---|
| ADMIN | RW | RW | RW | RW |
| COMMERCIAL | RW | R | RW | R |
| MAGASINIER | R | RW | R | — |

## 6. Flux

### 6.1 Sécurité transverse

- Angular s'authentifie auprès de Keycloak (OIDC, Authorization Code + PKCE) et envoie le JWT à la gateway.
- La gateway valide le JWT et route `/api/tiers/**`, `/api/catalog/**`, `/api/sales/**`.
- **Chaque service valide aussi le JWT et les rôles** (resource server). Un convertisseur lit `realm_access.roles`.
- Appels inter-services : **relais du JWT utilisateur** (intercepteur du `RestClient`).

### 6.2 Appels synchrones

- `RestClient` avec timeout de 2 s, sans retry. Indisponibilité → 503 Problem Details explicite.

### 6.3 Flux A — Créer / modifier un brouillon de devis

1. `POST|PUT /api/sales/devis` avec `{clientId, lignes[{produitId, quantite, remisePourcent}]}`.
2. `sales-service` appelle `GET tiers /tiers/{id}` et `GET catalog /produits?ids=…` (appel groupé).
3. Client ou produit inactif / inexistant → 422.
4. Calcul des totaux, enregistrement en `BROUILLON`.

Puis : `POST /devis/{id}/envoi` (numéro + date), `POST /devis/{id}/acceptation`, `POST /devis/{id}/refus`.

### 6.4 Flux B — Devis accepté → facture

`POST /api/sales/devis/{id}/facture` crée une facture `BROUILLON` reprenant lignes et prix. Une facture peut aussi être créée directement (même logique que le flux A).

### 6.5 Flux C — Validation d'une facture

1. `GET /factures/{id}/verification-stock` → `sales` appelle `POST catalog /produits/disponibilite` ; retourne la liste des produits insuffisants. Si le catalogue est indisponible, retourne un indicateur « stock non vérifié ».
2. `POST /factures/{id}/validation {confirmerStockInsuffisant}` — **une seule transaction** :
   - verrou et incrément du compteur `(FACTURE, année de la date du jour)` ;
   - `dateEmission = aujourd'hui`, `dateEcheance`, copie émetteur, `validePar`, statut `VALIDEE` ;
   - insertion dans `outbox` de `FactureValidee{eventId, factureId, numero, validePar, lignes[{produitId, quantite}]}`.
3. **Relais outbox** (`@Scheduled`, toutes les 2 s) : lit les lignes non publiées en `FOR UPDATE SKIP LOCKED`, publie sur l'exchange `ntan.sales` (clé `facture.validee`) avec **publisher confirms**, marque `datePublication` seulement après confirmation.
4. **`catalog-service`** consomme la file `catalog.facture-validee` dans une transaction :
   - `eventId` déjà présent dans `EvenementTraite` → ignoré ;
   - sinon, pour chaque ligne dont le produit a `suivreStock` : `MouvementStock SORTIE` (auteur = `validePar`, documentSource = numéro) et mise à jour de `stockActuel` ;
   - insertion de `eventId` dans `EvenementTraite`.

### 6.6 Flux D — Paiement, PDF, tableau de bord

- `POST /factures/{id}/paiement {date, mode}` : uniquement depuis `VALIDEE`, `date ≥ dateEmission`.
- `GET /devis/{id}/pdf`, `GET /factures/{id}/pdf` : générés à la demande à partir des seules données figées (devis : uniquement s'il est numéroté ; facture : uniquement si validée).
- Tableau de bord : Angular appelle
  - `GET /api/sales/statistiques` : CA HT mois et année (factures `VALIDEE` + `PAYEE`, par `dateEmission`), nombre et montant des factures en retard, nombre de devis envoyés non expirés ;
  - `GET /api/catalog/produits/alertes`.
  Chaque widget gère son propre 403 (le menu et les widgets s'adaptent au rôle).

### 6.7 Contrat d'événement (`ntan-events`)

```java
record FactureValidee(
    UUID eventId, Instant occurredAt, UUID factureId, String numero,
    String validePar, List<Ligne> lignes) {
  record Ligne(UUID produitId, BigDecimal quantite) {}
}
```

## 7. Gestion des erreurs et résilience

### 7.1 Format d'erreur

Toutes les API renvoient des **Problem Details (RFC 9457)** : `{type, title, status, detail, instance, errors[]}`, `errors` listant les erreurs de validation par champ.

| Cas | Code |
|---|---|
| Validation de champ | 400 |
| Non authentifié / rôle insuffisant | 401 / 403 |
| Ressource inexistante | 404 |
| Conflit de version / transition d'état interdite | 409 |
| Règle métier violée | 422 |
| Service dépendant indisponible | 503 |

### 7.2 Comportement en cas de panne

| Panne | Comportement |
|---|---|
| Tiers/catalog indisponible pendant l'édition | 503, rien n'est enregistré |
| Catalog indisponible pendant la vérification de stock | « Stock non vérifié », validation possible |
| RabbitMQ indisponible à la validation | Facture validée, événement conservé dans l'outbox |
| Crash de `sales` après commit | Événement en outbox, publié au redémarrage |
| Perte réseau lors de la publication | Pas de confirmation → republication au cycle suivant |
| Plusieurs instances de `sales` | `SKIP LOCKED` ; idempotence en filet de sécurité |
| Message dupliqué | Ignoré (`EvenementTraite`) |
| Erreur technique côté consommateur | 3 retries avec backoff, puis DLQ `catalog.facture-validee.dlq` |
| Erreur métier côté consommateur (produit inconnu) | DLQ directement |
| Validation concurrente d'une même facture | 409 (verrouillage optimiste) |

La procédure de rejeu de la DLQ (via l'interface d'administration RabbitMQ) est documentée dans `docs/`.

### 7.3 Observabilité

- **Micrometer Tracing** : `traceId` propagé dans les appels REST et les messages RabbitMQ, affiché dans les logs.
- **Actuator** `/actuator/health` sur chaque service, utilisé par les healthchecks Docker Compose.
- springdoc-openapi : Swagger UI sur chaque service.

## 8. Application web (Angular)

- **Structure :** `core/` (auth, intercepteurs, layout), `shared/` (composants communs), et un dossier par domaine chargé en lazy loading : `tiers/`, `catalogue/`, `ventes/`, `tableau-de-bord/`, `parametres/`.
- **Authentification :** client OIDC (Authorization Code + PKCE) ; intercepteur ajoutant le jeton.
- **Erreurs :** intercepteur transformant les Problem Details en notifications lisibles et en erreurs de formulaire.
- **Droits :** gardes de route par rôle ; menu filtré selon le rôle.
- **Formulaires :** Reactive Forms typés ; calcul des totaux affiché en direct à titre indicatif (le serveur fait foi).
- **UI :** Angular Material, layout avec barre latérale.

**Écrans V1 :** connexion ; tableau de bord ; tiers (liste filtrable, fiche avec contacts) ; produits (liste, fiche, historique des mouvements, saisie de mouvement) ; devis (liste, édition, actions envoyer/accepter/refuser/facturer, PDF) ; factures (liste avec filtre « en retard », édition, validation avec avertissement stock, paiement, PDF) ; paramètres entreprise.

## 9. Stratégie de tests

| Niveau | Outil | Cible |
|---|---|---|
| Domaine | JUnit 5 (tests paramétrés) | Cycles de vie, calculs HT/TVA/arrondis, ventilation, règles de stock |
| Architecture | ArchUnit | Règles de dépendance hexagonales |
| Intégration | `@SpringBootTest` + Testcontainers | Repositories, Flyway, numérotation concurrente, relais outbox, consommateur (idempotence, DLQ) |
| API | MockMvc + JWT simulé | Codes HTTP, Problem Details, droits par rôle |
| Inter-services | WireMock | Réponses, lenteurs (timeout), 503 |
| Web | Tests unitaires Angular | Services, formulaires, intercepteurs |
| Bout en bout | Playwright + Docker Compose | Connexion → devis → facture → validation → stock diminué |
| CI | GitHub Actions | Build et tests de chaque module à chaque push |

TDD sur le domaine et les flux critiques (calculs, numérotation, outbox, idempotence).

## 10. Jalons

Chaque jalon fait l'objet de son propre plan d'implémentation.

| Jalon | Contenu | Critère de fin |
|---|---|---|
| **J0 Socle** | Monorepo, POM parent, Docker Compose (PostgreSQL, RabbitMQ, Keycloak + realm + 3 utilisateurs de test), gateway, coquille Angular avec connexion, CI | Connexion dans Angular ; un appel authentifié traverse la gateway |
| **J1 Tiers** | `tiers-service` + écrans | CRUD clients/contacts, droits par rôle testés |
| **J2 Catalogue** | `catalog-service` hexagonal, mouvements manuels, alertes + écrans | Création de produit, entrée/sortie manuelle, alerte de seuil |
| **J3 Devis** | `sales-service` : domaine, calculs, numérotation, paramètres entreprise, PDF devis + écrans | Devis créé, envoyé, accepté, PDF obtenu |
| **J4 Factures** | Conversion, validation, outbox, consommation catalog, paiement, PDF facture + écrans | Valider une facture diminue le stock ; pannes du §7.2 testées |
| **J5 Finition** | Tableau de bord, données de démo (profil `demo`), E2E Playwright, README, ADRs, schémas | Démo complète lançable en une commande |

## 11. Décisions à consigner en ADR

1. Microservices pragmatiques (sans Eureka / Config Server en V1).
2. Style d'architecture par service (hexagonal vs couches).
3. Keycloak pour l'identité, relais du JWT entre services.
4. RabbitMQ + transactional outbox + consommateur idempotent.
5. Copies figées dans les documents de vente.
6. Retrait des commandes clients de la V1.
