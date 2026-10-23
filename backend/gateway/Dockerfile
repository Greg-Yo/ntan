FROM eclipse-temurin:25-jdk AS build
WORKDIR /build
# Certificats racine optionnels (poste derrière un proxy/antivirus qui inspecte le TLS) :
# déposer des fichiers *.crt, *.cer ou *.pem dans backend/certs/ (ignorés par Git). Sans fichier, rien ne change.
COPY certs/ /usr/local/share/ca-certificates/
RUN set -e; \
    for f in /usr/local/share/ca-certificates/*.crt /usr/local/share/ca-certificates/*.cer /usr/local/share/ca-certificates/*.pem; do \
      [ -e "$f" ] || continue; \
      keytool -importcert -noprompt -trustcacerts -cacerts \
        -storepass changeit -alias "$(basename "$f")" -file "$f"; \
    done
COPY . .
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw \
    && ./mvnw -B -q -pl gateway -am package -DskipTests

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /build/gateway/target/gateway.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
