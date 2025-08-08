# Vardr Secure Spring Boot Image

A secure, minimal base image for running Spring Boot applications with automatic certificate management and enterprise-grade security features.

## 🎯 Purpose

This image solves common problems in Spring Boot containerization:

1. **Security** - Runs on a hardened, minimal runtime (Distroless, non-root)
2. **Ease of Use** - Automatically imports additional CA certificates at startup without rebuilds
3. **Performance** - Smart caching prevents unnecessary certificate re-imports
4. **Compliance** - JSON logging for automated monitoring and audit trails
5. **Spring Boot Optimized** - Pre-configured JVM settings for optimal Spring Boot performance

## 🏗️ Architecture

### Three-Stage Build Process

#### Stage 1: Cert Utilities (`cert-utils`)
- **Base**: `eclipse-temurin:21-jre-alpine`
- **Purpose**: Provides certificate management tools
- **Components**:
  - `ca-certificates` - System trusted root CAs
  - `keytool` - Java truststore management
  - `openssl` - Certificate validation

#### Stage 2: Compiler (`compiler`)
- **Base**: `eclipse-temurin:21-jdk-alpine`
- **Purpose**: Compiles the Java entrypoint
- **Components**:
  - Java compiler for the CertificateManager

#### Stage 3: Final Runtime
- **Base**: `gcr.io/distroless/java21-debian12:nonroot`
- **Purpose**: Minimal, secure production runtime
- **Features**:
  - Java 21 runtime only
  - Debian 12 compatibility
  - Non-root execution
  - Minimal attack surface

## 🚀 Usage

### Basic Usage

```dockerfile
FROM ghcr.io/vardr/spring-boot:latest
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

### With Custom JVM Settings

```bash
docker run \
  -e JAVA_MAX_HEAP=1g \
  -e JAVA_MIN_HEAP=512m \
  -e JAVA_GC_TYPE=g1 \
  -e JAVA_OPTS="-Dserver.port=8080 -Dspring.profiles.active=prod" \
  my-service
```

## 🔧 How It Works

### Startup Process

1. **Certificate Hash Check**: Calculates SHA-256 hash of all certificates
2. **Smart Import**: Only imports certificates if they've changed
3. **Certificate Validation**: Validates each certificate format using Java's CertificateFactory
4. **Truststore Update**: Updates custom truststore in `/tmp/cacerts`
5. **Spring Boot Launch**: Starts Spring Boot with optimized JVM settings

### Performance Optimizations

- **Certificate Caching**: Skips import if certificates haven't changed
- **Hash-based Detection**: Uses SHA-256 to detect certificate modifications
- **Efficient Processing**: Processes certificates in parallel where possible
- **Spring Boot Tuning**: Pre-configured JVM options for optimal performance

### Certificate Handling

- **Format**: Supports standard X.509 certificates (`.crt` files)
- **Validation**: Each certificate is validated before import
- **Error Handling**: Invalid certificates are logged but don't stop startup
- **Duplicates**: Handles duplicate certificate imports gracefully
- **Persistence**: Truststore persists between restarts in `/tmp`

## 🔒 Security Features

### Attack Surface Reduction
- **Distroless Base**: No unnecessary binaries or tools
- **Non-root Execution**: Process runs as `nonroot` user from start to finish
- **No Shell**: Pure Java entrypoint eliminates shell-based attacks
- **Minimal Dependencies**: Only essential Java runtime components
- **Debian 12 Compatibility**: Better libc compatibility while maintaining security

### Certificate Security
- **Runtime Injection**: Certificates loaded at startup, not baked into image
- **Validation**: Certificate format validation prevents malformed certs
- **Isolation**: Certificate volume is read-only mount
- **No Secrets**: Avoids baking secrets into image layers
- **Custom Truststore**: Uses JVM system properties instead of modifying system files

### Compliance Ready
- **CIS Benchmarks**: Follows container security best practices
- **Trivy Compatible**: Clean security scan results
- **JSON Logging**: Structured logs for automated compliance monitoring
- **Audit Trail**: Detailed logging of certificate operations
- **Read-only Filesystem**: Compatible with maximum security deployments

## 📋 Requirements

### Host System
- Docker 20.10+
- Spring Boot JAR file
- Optional: Certificate files (`.crt` format)

### Application
- Spring Boot application packaged as JAR
- No special configuration required
- Standard Spring Boot startup process

## 🛠️ Development

### Building the Image

```bash
cd springboot
docker build -t vardr/spring-boot .
```

### Testing

```bash
# Test with sample certificate
echo "-----BEGIN CERTIFICATE-----" > test.crt
echo "MIIDXTCCAkWgAwIBAgIJAKoK..." >> test.crt
echo "-----END CERTIFICATE-----" >> test.crt

docker run -v $(pwd):/certs:ro vardr/spring-boot
```

### Testing with JSON Logging

```bash
docker run \
  -e VARDR_JSON_LOGS=true \
  -v $(pwd):/certs:ro \
  vardr/spring-boot
```

## 📝 Logging

### Standard Logging

```
[Vardr] Starting certificate import process...
[Vardr] Certificates unchanged, skipping import
[Vardr] Starting Spring Boot application...
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

### JVM Memory Management
| Variable | Default | Description |
|----------|---------|-------------|
| `JAVA_MAX_HEAP` | `75% of container memory` | Maximum heap size (e.g., `1g`, `512m`) |
| `JAVA_MIN_HEAP` | `50% of container memory` | Initial heap size (e.g., `512m`, `256m`) |

### Garbage Collection
| Variable | Default | Description |
|----------|---------|-------------|
| `JAVA_GC_TYPE` | `g1` | GC type: `g1`, `zgc`, `shenandoah` |

### Custom JVM Options
| Variable | Default | Description |
|----------|---------|-------------|
| `JAVA_OPTS` | `none` | Additional JVM options (space-separated) |

## 🚀 Performance Features

### Certificate Caching
- **Hash-based Detection**: SHA-256 hash of all certificates
- **Skip Unchanged**: No re-import if certificates haven't changed
- **Fast Startup**: Subsequent starts are much faster

### Truststore Persistence
- **Location**: `/tmp/cacerts` (writable by non-root)
- **Persistence**: Survives container restarts
- **Performance**: No need to rebuild truststore on every start

### Spring Boot Optimizations
- **G1GC**: Default garbage collector optimized for Spring Boot
- **Container Support**: Automatic memory detection in containers
- **String Deduplication**: Reduces memory usage for string-heavy applications
- **Compressed OOPs**: Reduces memory footprint on 64-bit JVMs
- **Modern GC Logging**: Uses Java 9+ unified logging format
- **Security Hardening**: Headless mode, disabled JMX, optimized for containers

## 🎯 Spring Boot Best Practices

### Memory Management
- **Container-Aware**: Automatically detects container memory limits
- **Percentage-Based**: Uses percentage of available memory for flexibility
- **G1GC Optimized**: Configured for Spring Boot's typical memory patterns
- **Fallback Handling**: Proper fallbacks for both min and max heap settings

### Performance Tuning
- **String Deduplication**: Reduces memory for applications with many similar strings
- **Compressed OOPs**: Reduces memory footprint
- **Optimized String Concatenation**: Better performance for string operations
- **Modern GC Logging**: Uses `-Xlog:gc*` for Java 9+ compatibility

### Security Hardening
- **Headless Mode**: Disabled GUI components
- **Optional Security Policy**: Can be enabled/disabled as needed
- **IPv4 Preference**: Optimized for container networking
- **UTC Timezone**: Consistent timezone handling

### Spring Boot Specific
- **JMX Disabled**: Reduces attack surface
- **Banner Disabled**: Cleaner startup logs
- **Web Application Type**: Optimized for servlet-based applications
- **UTF-8 Encoding**: Consistent character encoding

### JVM Configuration Improvements
- **No Duplicates**: Clean, non-redundant JVM options
- **Modern GC Logging**: Uses unified logging format
- **Deprecated Flag Removal**: Removed `-XX:+UseCGroupMemoryLimitForHeap`
- **Flexible Security**: Optional security policy configuration
- **Dynamic Options**: Support for `JAVA_OPTS` environment variable

## 📋 Examples

### Basic Spring Boot Application

```dockerfile
FROM ghcr.io/vardr/spring-boot:latest
COPY my-application.jar /app/app.jar
```

### High-Performance Spring Boot Application

```dockerfile
FROM ghcr.io/vardr/spring-boot:latest
COPY my-application.jar /app/app.jar

ENV JAVA_MAX_HEAP=2g
ENV JAVA_MIN_HEAP=1g
ENV JAVA_GC_TYPE=zgc
ENV JAVA_OPTS="-Dserver.port=8080 -Dspring.profiles.active=prod"
```

### Memory-Constrained Spring Boot Application

```dockerfile
FROM ghcr.io/vardr/spring-boot:latest
COPY my-application.jar /app/app.jar

ENV JAVA_MAX_HEAP=512m
ENV JAVA_MIN_HEAP=256m
ENV JAVA_GC_TYPE=g1
```

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly
5. Submit a pull request

## 📄 License

[Add your license information here]

## 🆘 Support

For issues and questions:
- Create an issue in the repository
- Check the logs for certificate import errors
- Verify certificate format and permissions
- Enable JSON logging for detailed debugging 