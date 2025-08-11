# syntax=docker/dockerfile:1.5

# ====== Stage 1: Builder ======
ARG PYTHON_VERSION=3.12
FROM python:${PYTHON_VERSION}-alpine AS builder

RUN apk add --no-cache \
    build-base \
    libffi-dev \
    openssl-dev \
    ca-certificates \
    openssl

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY certificate_manager.py /certificate_manager.py

# Create /app and /certs directories with proper ownership for distroless stage
RUN mkdir -p /app/certs && chown -R 65532:65532 /app /app/certs

# ====== Stage 2: Distroless runtime ======
FROM gcr.io/distroless/python3-debian12 AS distroless
WORKDIR /app
COPY --from=builder /usr/local /usr/local
COPY --from=builder /usr/bin/openssl /usr/bin/openssl
COPY --from=builder /etc/ssl/certs /etc/ssl/certs
COPY --from=builder /certificate_manager.py /certificate_manager.py
COPY --from=builder /app/certs /app/certs
VOLUME ["/app/certs"]
ENV PYTHONUNBUFFERED=1 PYTHONDONTWRITEBYTECODE=1 PORT=8000 WORKERS=1
# Distroless runs as nonroot by default, ensure /certs is accessible
USER 65532:65532
ENTRYPOINT ["python", "/certificate_manager.py"]

# ====== Stage 3: Alpine runtime ======
FROM python:${PYTHON_VERSION}-alpine AS alpine
RUN apk add --no-cache \
    ca-certificates \
    openssl
WORKDIR /app
COPY --from=builder /usr/local /usr/local
COPY --from=builder /usr/bin/openssl /usr/bin/openssl
COPY --from=builder /etc/ssl/certs /etc/ssl/certs
COPY --from=builder /certificate_manager.py /certificate_manager.py
VOLUME ["/app/certs"]
ENV PYTHONUNBUFFERED=1 PYTHONDONTWRITEBYTECODE=1 PORT=8000 WORKERS=1
RUN addgroup -g 65532 appuser && adduser -D -s /bin/sh -u 65532 -G appuser appuser && \
    mkdir -p /app/certs && chown -R appuser:appuser /app /app/certs
USER appuser
ENTRYPOINT ["python", "/certificate_manager.py"]
