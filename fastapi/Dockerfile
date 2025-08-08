# syntax=docker/dockerfile:1.5

# ====== Stage 1: Builder ======
ARG PYTHON_VERSION=3.12
FROM python:${PYTHON_VERSION}-slim AS builder

RUN apt-get update && apt-get install -y \
    build-essential \
    libffi-dev \
    libssl-dev \
    ca-certificates \
    openssl \
 && rm -rf /var/lib/apt/lists/*

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

# ====== Stage 3: Slim runtime ======
FROM python:${PYTHON_VERSION}-slim AS slim
RUN apt-get update && apt-get install -y \
    ca-certificates \
    openssl \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=builder /usr/local /usr/local
COPY --from=builder /usr/bin/openssl /usr/bin/openssl
COPY --from=builder /etc/ssl/certs /etc/ssl/certs
COPY --from=builder /certificate_manager.py /certificate_manager.py
VOLUME ["/certs"]
ENV PYTHONUNBUFFERED=1 PYTHONDONTWRITEBYTECODE=1 PORT=8000 WORKERS=1
RUN groupadd -r appuser && useradd -r -g appuser appuser
USER appuser
ENTRYPOINT ["python", "/certificate_manager.py"]
