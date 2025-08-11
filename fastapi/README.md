# Vardr Secure FastAPI Image

A secure, minimal base image for running FastAPI applications with automatic certificate management and enterprise-grade security features.

## 🎯 Purpose

A production‑grade, framework‑focused hardened image for FastAPI. Beyond a generic "distroless" base, it ships secure defaults, certificate handling, and runtime tuning purpose‑built for FastAPI.

- Security: Distroless (3.12) or slim (3.13), non‑root, read‑only compatible
- Ease of use: Mount `/certs` for custom CAs; no rebuilds
- Performance: Hash‑based CA caching; sensible uvicorn defaults
- Compliance: Optional JSON logs for auditability
- Framework focused: Tuning and defaults made specifically for FastAPI workloads

## 🏗️ Architecture

### Matrix-Ready Multi-Stage Build Process

#### Stage 1: Builder
- **Base**: `python:${PYTHON_VERSION}-slim` (3.12 or 3.13)
- **Purpose**: Installs Python dependencies and build tools
- **Components**:
  - FastAPI and uvicorn
  - Security and cryptography packages
  - Certificate management tools

#### Stage 2: Distroless Runtime (Python 3.12)
- **Base**: `gcr.io/distroless/python3-debian12`
- **Purpose**: Maximum security for Python 3.12
- **Features**:
  - Distroless Python 3.12 runtime
  - Non-root execution by default
  - Minimal attack surface

#### Stage 3: Slim Runtime (Python 3.13)
- **Base**: `python:${PYTHON_VERSION}-slim`
- **Purpose**: Secure fallback for Python 3.13 until distroless exists
- **Features**:
  - Python 3.13 slim runtime
  - Non-root execution
  - Minimal dependencies

#### Stage 4: Final Selector
- **Conditional**: Uses ARG-based selection between distroless and slim
- **Purpose**: Matrix-ready for CI/CD automation

## 🚀 Usage

### Basic Usage

```dockerfile
FROM ghcr.io/vardr/fastapi:python12
COPY app.py /app/app.py
```

### With Custom Certificates

```bash
docker run \
  -v $(pwd)/certs:/certs:ro \
  ghcr.io/vardr/fastapi:python13
```

### With Read-Only Filesystem (Maximum Security)

```bash
docker run \
  --read-only \
  -v $(pwd)/certs:/certs:ro \
  -v /tmp:/tmp \
  ghcr.io/vardr/fastapi:python13
```

## 📦 Adding Additional Dependencies

The Vardr FastAPI base image includes essential packages for most FastAPI applications. However, you may need additional dependencies for your specific use case.

### ✅ Recommended Approach: Multi-Stage Build

This approach maintains security while allowing full flexibility:

```dockerfile
# ====== Stage 1: Install additional dependencies ======
FROM python:3.12-alpine AS user-deps
WORKDIR /app

# Install build dependencies for compiling packages
RUN apk add --no-cache \
    build-base \
    libffi-dev \
    openssl-dev \
    postgresql-dev

# Copy and install your additional requirements
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# ====== Stage 2: Use Vardr base with user dependencies ======
FROM ghcr.io/vardr/fastapi:python3.12

# Copy the additional packages from the builder stage
COPY --from=user-deps /usr/local /usr/local

WORKDIR /app
COPY app.py /app/app.py
EXPOSE 8000
```

### Example Additional Requirements

Create a `requirements.txt` with your additional packages:

```txt
# Data processing
pandas==2.2.0
numpy==1.26.4

# Database connectivity
psycopg2-binary==2.9.9
sqlalchemy==2.0.25

# Caching and messaging
redis==5.0.1

# Authentication and security
python-jose[cryptography]==3.3.0
passlib[bcrypt]==1.7.4
```

### 🔧 Alternative Approaches

#### Simple Extension (Less Secure)
```dockerfile
FROM ghcr.io/vardr/fastapi:python3.12

# Temporarily switch to root to install packages
USER root
RUN apk add --no-cache build-base && \
    pip install pandas numpy && \
    apk del build-base && \
    rm -rf /var/cache/apk/*

# Switch back to non-root user
USER 65532

COPY app.py /app/app.py
EXPOSE 8000
```

⚠️ **Security Note**: This approach reduces security by temporarily using root privileges.

#### Pre-built Extension Image
For common dependency combinations, consider creating your own base image:

```dockerfile
FROM ghcr.io/vardr/fastapi:python3.12 AS base

# Create your own extended base
FROM python:3.12-alpine AS deps
RUN apk add --no-cache build-base libffi-dev openssl-dev
RUN pip install pandas numpy sqlalchemy

FROM base
COPY --from=deps /usr/local /usr/local
# This becomes your new base for multiple projects
```

### 🏗️ Build Dependencies by Category

Choose the appropriate build dependencies based on your requirements:

```dockerfile
# For data science packages (pandas, numpy, scipy)
RUN apk add --no-cache build-base gfortran openblas-dev

# For database connectivity
RUN apk add --no-cache build-base postgresql-dev mysql-dev

# For cryptography and security
RUN apk add --no-cache build-base libffi-dev openssl-dev

# For image processing
RUN apk add --no-cache build-base jpeg-dev zlib-dev

# For XML/HTML processing
RUN apk add --no-cache build-base libxml2-dev libxslt-dev
```

## 🔧 How It Works

### Startup Process

1. **Certificate Hash Check**: Calculates SHA-256 hash of all certificates
2. **Smart Import**: Only processes certificates if they've changed
3. **Certificate Validation**: Validates each certificate format using Python's ssl module
4. **Custom CA Bundle**: Creates a custom CA bundle combining system CAs with custom certificates
5. **FastAPI Launch**: Starts FastAPI with uvicorn and optimized settings

### Performance Optimizations

- **Certificate Caching**: Skips processing if certificates haven't changed
- **Hash-based Detection**: Uses SHA-256 to detect certificate modifications
- **Efficient Processing**: Processes certificates in parallel where possible
- **FastAPI Tuning**: Pre-configured uvicorn settings for optimal performance

### Certificate Handling

- **Format**: Supports standard X.509 certificates (`.crt` files)
- **Validation**: Each certificate is validated before processing
- **Error Handling**: Invalid certificates are logged but don't stop startup
- **Custom CA Bundle**: Creates `/tmp/custom_ca_bundle.pem` with system + custom CAs
- **Environment Variables**: Sets `SSL_CERT_FILE`, `REQUESTS_CA_BUNDLE`, `CURL_CA_BUNDLE`

## 🔒 Security Features

### Attack Surface Reduction
- **Distroless Base** (Python 3.12): No unnecessary binaries, shells, or package managers
- **Slim Base** (Python 3.13): Minimal runtime with essential packages only
- **Non-root Execution**: Process runs as non-root user from start to finish
- **No Shell**: Pure Python entrypoint eliminates shell-based attacks
- **Minimal Dependencies**: Only essential Python runtime components

### Certificate Security
- **Runtime Injection**: Certificates loaded at startup, not baked into image
- **Validation**: Certificate format validation prevents malformed certs
- **Isolation**: Certificate volume is read-only mount
- **No Secrets**: Avoids baking secrets into image layers
- **Custom CA Bundle**: Uses environment variables for certificate paths

### Compliance Ready
- **CIS Benchmarks**: Follows container security best practices
- **Trivy Compatible**: Clean security scan results
- **JSON Logging**: Structured logs for automated compliance monitoring
- **Audit Trail**: Detailed logging of certificate operations
- **Read-only Filesystem**: Compatible with maximum security deployments

## 📋 Requirements

### Host System
- Docker 20.10+
- FastAPI application files
- Optional: Certificate files (`.crt` format)

### Application
- FastAPI application with standard structure
- No special configuration required
- Standard FastAPI startup process

## 🛠️ Development

### Building Images

```bash
# Build Python 3.12 (distroless)
cd fastapi
docker build --build-arg PYTHON_VERSION=3.12 --build-arg DISTROLESS=true -t vardr/fastapi:python12 .

# Build Python 3.13 (slim)
docker build --build-arg PYTHON_VERSION=3.13 --build-arg DISTROLESS=false -t vardr/fastapi:python13 .
```

### Testing

```bash
# Test with sample certificate
echo "-----BEGIN CERTIFICATE-----" > test.crt
echo "MIIDXTCCAkWgAwIBAgIJAKoK..." >> test.crt
echo "-----END CERTIFICATE-----" >> test.crt

docker run -v $(pwd):/certs:ro vardr/fastapi:python12
```

### Testing with JSON Logging

```bash
docker run \
  -e VARDR_JSON_LOGS=true \
  -v $(pwd):/certs:ro \
  vardr/fastapi:python13
```

## 📝 Logging

### Standard Logging

```
[Vardr] Starting certificate import process...
[Vardr] Certificates unchanged, skipping import
[Vardr] Starting FastAPI application...
```

### JSON Logging (Compliance)

```json
{"type":"log","timestamp":"2024-01-15T10:30:00Z","level":"INFO","message":"Starting certificate import process..."}
{"type":"certificate_import_summary","timestamp":"2024-01-15T10:30:01Z","successful_imports":2,"failed_imports":0,"total_certificates":2,"results":{"my-ca":"SUCCESS","internal-ca":"SUCCESS"}}
```

## 🔧 Environment Variables

### Certificate Management
| Variable | Default | Description |
|----------|---------|-------------|
| `VARDR_JSON_LOGS` | `false` | Enable JSON logging for compliance |

### FastAPI Configuration
| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | `8000` | Port for the FastAPI application |
| `WORKERS` | `1` | Number of uvicorn workers |
| `UVICORN_OPTS` | `none` | Additional uvicorn options |

### Python Configuration
| Variable | Default | Description |
|----------|---------|-------------|
| `PYTHONUNBUFFERED` | `1` | Unbuffered Python output |
| `PYTHONDONTWRITEBYTECODE` | `1` | Don't write .pyc files |

## 🚀 Performance Features

### Certificate Caching
- **Hash-based Detection**: SHA-256 hash of all certificates
- **Skip Unchanged**: No re-processing if certificates haven't changed
- **Fast Startup**: Subsequent starts are much faster

### Custom CA Bundle
- **Location**: `/tmp/custom_ca_bundle.pem` (writable by non-root)
- **Content**: System CAs + custom certificates
- **Persistence**: Survives container restarts
- **Performance**: No need to rebuild bundle on every start

### FastAPI Optimizations
- **Uvicorn Server**: High-performance ASGI server
- **Multiple Workers**: Configurable worker processes
- **Hot Reload**: Development-friendly reload option
- **Structured Logging**: JSON logging support
- **Security Hardening**: Non-root execution, minimal dependencies

## 🎯 FastAPI Best Practices

### Application Structure
- **Auto-discovery**: Automatically finds `main.py`, `app.py`, etc.
- **Standard Layout**: Follows FastAPI conventions
- **Environment Variables**: Configurable via environment
- **Health Checks**: Built-in health check endpoints

### Performance Tuning
- **Uvicorn Workers**: Configurable for load balancing
- **Async Support**: Full async/await support
- **Type Hints**: Pydantic integration for validation
- **OpenAPI**: Automatic API documentation

### Security Hardening
- **Non-root User**: Runs as non-root by default
- **Certificate Validation**: Built-in certificate checking
- **Environment Isolation**: Secure environment variable handling
- **Minimal Dependencies**: Only essential packages included

### FastAPI Specific
- **Auto-reload**: Development mode support
- **Logging**: Structured logging with JSON support
- **CORS**: Configurable CORS settings
- **Dependencies**: Pre-installed common FastAPI packages

## 📋 Examples

### Basic FastAPI Application

```dockerfile
FROM ghcr.io/vardr/fastapi:python12
COPY app.py /app/app.py
```

### FastAPI Application with Dependencies

```dockerfile
FROM ghcr.io/vardr/fastapi:python13
COPY requirements.txt /app/requirements.txt
COPY app.py /app/app.py

# Install additional dependencies
RUN pip install -r requirements.txt
```

### High-Performance FastAPI Application

```dockerfile
FROM ghcr.io/vardr/fastapi:python12
COPY app.py /app/app.py

ENV WORKERS=4
ENV UVICORN_OPTS="--log-level info"
```

### Development FastAPI Application

```dockerfile
FROM ghcr.io/vardr/fastapi:python13
COPY app.py /app/app.py

ENV UVICORN_OPTS="--reload --log-level debug"
```

## 📋 Application Examples

### Basic FastAPI App (`app.py`)

```python
from fastapi import FastAPI

app = FastAPI(title="My API", version="1.0.0")

@app.get("/")
async def root():
    return {"message": "Hello World"}

@app.get("/health")
async def health():
    return {"status": "healthy"}
```

### FastAPI App with Dependencies (`app.py`)

```python
from fastapi import FastAPI, Depends
from pydantic import BaseModel
import requests

app = FastAPI(title="Secure API", version="1.0.0")

class Item(BaseModel):
    name: str
    description: str

@app.get("/")
async def root():
    return {"message": "Secure FastAPI Application"}

@app.post("/items/")
async def create_item(item: Item):
    return {"item": item, "status": "created"}
```

## 🔄 Matrix Build Strategy

### Available Images

| Python Version | Runtime | Tag | Security Level |
|----------------|---------|-----|----------------|
| 3.12 | Distroless | `python12` | Maximum |
| 3.13 | Slim | `python13` | High |

### Migration Path

When Python 3.13 distroless becomes available:
1. Update matrix configuration
2. Set `distroless: "true"` for Python 3.13
3. Rebuild images
4. Update documentation

### CI/CD Integration

The matrix build automatically:
- Builds both Python versions in parallel
- Runs security scans with Trivy
- Tests certificate management
- Pushes to container registry
- Generates security reports

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly
5. Submit a pull request

## 📄 License

This project is licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).

## 🆘 Support

For issues and questions:
- Create an issue in the repository
- Check the logs for certificate import errors
- Verify certificate format and permissions
- Enable JSON logging for detailed debugging 