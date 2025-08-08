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

# ====== Stage 2: Distroless runtime ======
FROM gcr.io/distroless/python3-debian12 AS distroless
WORKDIR /app
COPY --from=builder /usr/local /usr/local
COPY --from=builder /usr/bin/openssl /usr/bin/openssl
COPY --from=builder /etc/ssl/certs /etc/ssl/certs
COPY --from=builder /certificate_manager.py /certificate_manager.py
VOLUME ["/certs"]
ENV PYTHONUNBUFFERED=1 PYTHONDONTWRITEBYTECODE=1 PORT=8000 WORKERS=1
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
VOLUME ["/certs"]
ENV PYTHONUNBUFFERED=1 PYTHONDONTWRITEBYTECODE=1 PORT=8000 WORKERS=1
RUN addgroup -g 1000 appuser && adduser -D -s /bin/sh -u 1000 -G appuser appuser
USER appuser
ENTRYPOINT ["python", "/certificate_manager.py"]
