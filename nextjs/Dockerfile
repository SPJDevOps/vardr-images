# syntax=docker/dockerfile:1.5

# ====== Stage 1: Builder (prepare runtime files) ======
ARG NODE_VERSION=20
FROM node:${NODE_VERSION}-bookworm-slim AS builder

# No package installs here to keep the builder minimal; we only prep files
WORKDIR /app

# Create certs dir with proper ownership for distroless nonroot user (65532)
RUN mkdir -p /app/certs && chown -R 65532:65532 /app/certs

# Copy the certificate manager script
COPY certificate_manager.mjs /certificate_manager.mjs

# ====== Stage 2: Distroless Node.js runtime ======
FROM gcr.io/distroless/nodejs${NODE_VERSION}-debian12:nonroot

# Distroless default user is nonroot (65532)
WORKDIR /app

# Copy certs dir and certificate manager
COPY --from=builder /app/certs /app/certs
COPY --from=builder /certificate_manager.mjs /certificate_manager.mjs

# Ensure system CA certificates are present (provided by distroless debian)
# Expose a volume for users to mount custom certs
VOLUME ["/app/certs"]

# Default environment
ENV PORT=3000 \
    HOST=0.0.0.0 \
    NODE_ENV=production \
    VARD_JSON_LOGS=false

# The manager will search for /app/server.js or /app/.next/standalone/server.js to start
ENTRYPOINT ["node", "/certificate_manager.mjs"] 