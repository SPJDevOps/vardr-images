# Vardr Secure Images

Framework‑focused hardened container images with automatic certificate management and secure defaults for Spring Boot, FastAPI, and Next.js.

## 🎯 Purpose

Vardr Secure Images are production‑grade, framework‑focused hardened images. They go beyond generic “distroless” bases by baking in secure defaults, certificate management, and runtime tuning for each framework.

- Security: Distroless/minimal runtimes, non‑root, read‑only compatible
- Ease of use: Drop in your app; mount `/certs` to add CAs without rebuilding
- Performance: Hash‑based certificate caching, optimized runtime flags
- Compliance: JSON logs and predictable, auditable startup
- Framework focused: Pre‑tuned for Spring Boot, FastAPI, and Next.js

Why this matters vs other hardened images:
- Many hardened bases stop at “minimal + non‑root.” Vardr also configures framework‑specific runtime flags, logging, and certificate handling so teams spend less time on glue code and more on shipping.

## 🏗️ Architecture

### Multi-Stage Build Process

All Vardr Secure Images follow a consistent multi-stage build pattern:

1. **Cert Utilities Stage** - Provides certificate management tools
2. **Compiler Stage** - Compiles the Java entrypoint (where applicable)
3. **Final Runtime Stage** - Minimal, secure production runtime

### Security Features

- **Distroless Base** - No unnecessary binaries or tools
- **Non-root Execution** - Process runs as non-root user from start to finish
- **No Shell** - Pure Java entrypoint eliminates shell-based attacks
- **Minimal Dependencies** - Only essential runtime components
- **Certificate Isolation** - Runtime certificate injection, not baked into image

## 🚀 Available Images

### Spring Boot
- Base: `ghcr.io/vardr/spring-boot:java21`
- Focus: JVM flags and truststore handling tuned for Spring Boot
- Docs: [Spring Boot](spring-boot/README.md)

### FastAPI
- Base: `ghcr.io/vardr/fastapi:python12` and `ghcr.io/vardr/fastapi:python13`
- Focus: Uvicorn defaults and Python CA bundle handling
- Docs: [FastAPI](fastapi/README.md)

### Next.js
- Base: `ghcr.io/vardr/nextjs:node20`
- Focus: Standalone output and `NODE_EXTRA_CA_CERTS` integration
- Docs: [Next.js](nextjs/README.md)

## 📋 Quick Start

### Basic Usage

```dockerfile
FROM ghcr.io/vardr/spring-boot:java21
COPY my-service.jar /app/app.jar
```

### With Custom Certificates

```bash
docker run \
  -v $(pwd)/certs:/certs:ro \
  my-service
```

### With Read-Only Filesystem (Maximum Security)

```bash
docker run \
  --read-only \
  -v $(pwd)/certs:/certs:ro \
  -v /tmp:/tmp \
  my-service
```

### With JSON Logging (Compliance)

```bash
docker run \
  -e VARDR_JSON_LOGS=true \
  -v $(pwd)/certs:/certs:ro \
  my-service
```

## 🔧 How It Works

### Certificate Management
1. **Certificate Discovery** - Checks for `.crt` files in `/certs` directory
2. **Hash-based Caching** - Skips import if certificates haven't changed
3. **Certificate Validation** - Validates each certificate format
4. **Truststore Update** - Updates custom truststore in writable location
5. **Application Launch** - Starts application with custom truststore

### Security Features
- **Runtime Injection** - Certificates loaded at startup, not baked into image
- **Validation** - Certificate format validation prevents malformed certs
- **Isolation** - Certificate volume is read-only mount
- **No Secrets** - Avoids baking secrets into image layers
- **Custom Truststore** - Uses framework-specific methods instead of modifying system files

## 🔒 Security Features

### Attack Surface Reduction
- **Distroless Base** - No unnecessary binaries or tools
- **Non-root Execution** - Process runs as non-root user from start to finish
- **No Shell** - Pure Java entrypoint eliminates shell-based attacks
- **Minimal Dependencies** - Only essential runtime components

### Certificate Security
- **Runtime Injection** - Certificates loaded at startup, not baked into image
- **Validation** - Certificate format validation prevents malformed certs
- **Isolation** - Certificate volume is read-only mount
- **No Secrets** - Avoids baking secrets into image layers

### Compliance Ready
- **CIS Benchmarks** - Follows container security best practices
- **Trivy Compatible** - Clean security scan results
- **JSON Logging** - Structured logs for automated compliance monitoring
- **Audit Trail** - Detailed logging of certificate operations
- **Read-only Filesystem** - Compatible with maximum security deployments

## 📝 Logging

### Standard Logging
```
[Vardr] Starting certificate import process...
[Vardr] Certificates unchanged, skipping import
[Vardr] Starting application...
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

*Framework-specific environment variables are documented in each image's README.*

## 🚀 Performance Features

### Certificate Caching
- **Hash-based Detection** - SHA-256 hash of all certificates
- **Skip Unchanged** - No re-import if certificates haven't changed
- **Fast Startup** - Subsequent starts are much faster

### Truststore Persistence
- **Location** - `/tmp/cacerts` (writable by non-root)
- **Persistence** - Survives container restarts
- **Performance** - No need to rebuild truststore on every start

## 📋 Requirements

### Host System
- Docker 20.10+
- Application JAR/file
- Optional: Certificate files (`.crt` format)

### Application
- Framework-specific application packaged appropriately
- No special configuration required
- Standard framework startup process

## 🛠️ Development

### Building Images

```bash
# Build Spring Boot image
cd spring-boot
docker build -t vardr/spring-boot .

# Build FastAPI images
cd ../fastapi
docker build --build-arg PYTHON_VERSION=3.12 --build-arg DISTROLESS=true -t vardr/fastapi:python12 .
docker build --build-arg PYTHON_VERSION=3.13 --build-arg DISTROLESS=false -t vardr/fastapi:python13 .

# Build Next.js image
cd ../nextjs
docker build -t vardr/nextjs:node20 .
```

### Testing

```bash
# Test with sample certificate
echo "-----BEGIN CERTIFICATE-----" > test.crt
echo "MIIDXTCCAkWgAwIBAgIJAKoK..." >> test.crt
echo "-----END CERTIFICATE-----" >> test.crt

docker run -v $(pwd):/certs:ro vardr/spring-boot
```

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
- Check framework-specific documentation for detailed configuration 