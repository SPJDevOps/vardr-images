# ---- Stage 1: Cert utilities ----
FROM eclipse-temurin:21-jre-alpine AS cert-utils

USER root
RUN apk add --no-cache ca-certificates openssl bash

# ---- Stage 2: Compile entrypoint ----
FROM eclipse-temurin:21-jdk-alpine AS compiler

COPY CertificateManager.java /tmp/CertificateManager.java
RUN javac /tmp/CertificateManager.java -d /tmp/

# ---- Stage 3: Final image ----
FROM gcr.io/distroless/java21-debian12:nonroot

# Copy keytool & cert scripts from util stage
COPY --from=cert-utils /opt/java/openjdk/bin/keytool /usr/bin/keytool
COPY --from=cert-utils /usr/bin/openssl /usr/bin/openssl
COPY --from=cert-utils /opt/java/openjdk/lib/security/cacerts /app/cacerts
COPY --from=cert-utils /etc/ssl/certs /etc/ssl/certs

# Working directories
WORKDIR /app

# Copy compiled entrypoint to working directory
COPY --from=compiler /tmp/CertificateManager.class /app/CertificateManager.class

VOLUME ["/certs"]

# Ensure /certs is owned by nonroot user (UID 65532)
RUN mkdir -p /certs && chown -R 65532:65532 /certs

USER nonroot
ENTRYPOINT ["java", "CertificateManager"]
    