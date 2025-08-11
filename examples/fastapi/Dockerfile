# Example: FastAPI app with additional dependencies
# This shows how to add your own requirements to the Vardr FastAPI base image

# ====== Stage 1: Install additional dependencies ======
FROM python:3.13-alpine AS user-deps
WORKDIR /app

# Install build dependencies for compiling packages
RUN apk add --no-cache \
    build-base \
    libffi-dev \
    openssl-dev \
    postgresql-dev

# Copy and install your additional requirements
# Create a requirements.txt with your additional packages:
# pandas==2.0.0
# psycopg2-binary==2.9.5
# redis==4.5.0
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# ====== Stage 2: Use Vardr base with user dependencies ======
FROM ghcr.io/spjdevops/vardr/fastapi:python3.13

# Copy the additional packages from the builder stage
COPY --from=user-deps /usr/local /usr/local

WORKDIR /app

# Copy your FastAPI application
COPY test_app.py app.py

# Expose port (adjust to your app's port)
EXPOSE 8000
