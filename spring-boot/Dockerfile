# ---- Stage 1: Cert utilities ----
FROM eclipse-temurin:21-jre-alpine AS cert-utils

USER root
RUN apk add --no-cache ca-certificates openssl bash && \
    mkdir -p /app/certs && chown -R 65532:65532 /app/certs && chown -R 65532:65532 /opt/java/openjdk/lib/security/cacerts

# ---- Stage 2: Compile entrypoint ----
FROM eclipse-temurin:21-jdk-alpine AS compiler

COPY CertificateManager.java /tmp/CertificateManager.java
RUN javac /tmp/CertificateManager.java -d /tmp/

# ---- Stage 3: Final image ----
FROM gcr.io/distroless/java21-debian12:nonroot

# Copy necessary files
COPY --from=cert-utils /opt/java/openjdk/bin/keytool /usr/bin/keytool
COPY --from=cert-utils /usr/bin/openssl /usr/bin/openssl
COPY --from=cert-utils /opt/java/openjdk/lib/security/cacerts /app/cacerts
COPY --from=cert-utils /etc/ssl/certs /etc/ssl/certs
COPY --from=cert-utils /app/certs /app/certs

WORKDIR /app
COPY --from=compiler /tmp/CertificateManager.class /app/CertificateManager.class

USER nonroot
ENTRYPOINT ["java", "CertificateManager"]

    