#!/bin/sh
set -euo pipefail

# Path to JVM truststore (in writable location)
TRUSTSTORE="/tmp/cacerts"
STOREPASS="changeit"

# Function to validate certificate file
validate_cert() {
    local cert_file="$1"
    if ! openssl x509 -in "$cert_file" -text -noout >/dev/null 2>&1; then
        echo "    [ERROR] Invalid certificate format: $cert_file"
        return 1
    fi
    return 0
}

echo "[Vardr] Starting certificate import process..."

# Merge any custom certs from /certs
if [ -d "/certs" ] && [ "$(ls -A /certs/*.crt 2>/dev/null || true)" ]; then
    echo "[Vardr] Found certificates in /certs directory..."
    
    # Count certificates for logging
    cert_count=$(ls /certs/*.crt 2>/dev/null | wc -l)
    echo "[Vardr] Processing $cert_count certificate(s)..."
    
    for crt in /certs/*.crt; do
        alias=$(basename "$crt" .crt)
        echo "  -> Importing $alias from $(basename "$crt")"
        
        # Validate certificate format
        if validate_cert "$crt"; then
            if keytool -import -trustcacerts -noprompt \
                -keystore "$TRUSTSTORE" \
                -storepass "$STOREPASS" \
                -file "$crt" \
                -alias "$alias" 2>/dev/null; then
                echo "    [OK] Successfully imported $alias"
            else
                echo "    [WARN] Could not import $alias (may already exist)"
            fi
        fi
    done
    
    echo "[Vardr] Certificate import process completed"
else
    echo "[Vardr] No certificates found in /certs directory"
fi

# Start Spring Boot with custom truststore
echo "[Vardr] Starting Spring Boot application with custom truststore..."
exec java \
    -Djava.security.egd=file:/dev/./urandom \
    -Djavax.net.ssl.trustStore="$TRUSTSTORE" \
    -Djavax.net.ssl.trustStorePassword="$STOREPASS" \
    -jar /app/app.jar
