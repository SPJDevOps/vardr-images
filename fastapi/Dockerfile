# syntax=docker/dockerfile:1.5

# ====== Stage 1: Builder ======
ARG PYTHON_VERSION=3.12
FROM python:${PYTHON_VERSION}-slim AS builder

# Install system build dependencies
RUN apt-get update && apt-get install -y \
    build-essential \
    libffi-dev \
    libssl-dev \
    ca-certificates \
    openssl \
    && rm -rf /var/lib/apt/lists/*

# Set workdir
WORKDIR /app

# Copy requirements and install deps
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy certificate manager
COPY certificate_manager.py /certificate_manager.py

# ====== Stage 2: Distroless runtime (for Python 3.12) ======
FROM gcr.io/distroless/python3-debian12 AS distroless
WORKDIR /app

# Copy Python dependencies
COPY --from=builder /usr/local /usr/local

# Copy certificate management tools
COPY --from=builder /usr/bin/openssl /usr/bin/openssl
COPY --from=builder /etc/ssl/certs /etc/ssl/certs

# Copy certificate manager
COPY --from=builder /certificate_manager.py /certificate_manager.py

# Create volume for certificates
VOLUME ["/certs"]

# Set environment variables
ENV PYTHONUNBUFFERED=1
ENV PYTHONDONTWRITEBYTECODE=1
ENV PORT=8000
ENV WORKERS=1

# Set the entrypoint
ENTRYPOINT ["python", "/certificate_manager.py"]

# ====== Stage 3: Slim runtime (fallback for Python 3.13) ======
FROM python:${PYTHON_VERSION}-slim AS slim

# Install runtime dependencies
RUN apt-get update && apt-get install -y \
    ca-certificates \
    openssl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy Python dependencies
COPY --from=builder /usr/local /usr/local

# Copy certificate management tools
COPY --from=builder /usr/bin/openssl /usr/bin/openssl
COPY --from=builder /etc/ssl/certs /etc/ssl/certs

# Copy certificate manager
COPY --from=builder /certificate_manager.py /certificate_manager.py

# Create volume for certificates
VOLUME ["/certs"]

# Set environment variables
ENV PYTHONUNBUFFERED=1
ENV PYTHONDONTWRITEBYTECODE=1
ENV PORT=8000
ENV WORKERS=1

# Run as non-root user
RUN groupadd -r appuser && useradd -r -g appuser appuser
USER appuser

# Set the entrypoint
ENTRYPOINT ["python", "/certificate_manager.py"]

# ====== Stage 4: Final selector ======
ARG BASE_IMAGE
FROM ${BASE_IMAGE} AS final
WORKDIR /app

# Copy Python dependencies
COPY --from=builder /usr/local /usr/local

# Copy certificate management tools
COPY --from=builder /usr/bin/openssl /usr/bin/openssl
COPY --from=builder /etc/ssl/certs /etc/ssl/certs

# Copy certificate manager
COPY --from=builder /certificate_manager.py /certificate_manager.py

# Create volume for certificates
VOLUME ["/certs"]

# Set environment variables
ENV PYTHONUNBUFFERED=1
ENV PYTHONDONTWRITEBYTECODE=1
ENV PORT=8000
ENV WORKERS=1

# Set the entrypoint
ENTRYPOINT ["python", "/certificate_manager.py"] 