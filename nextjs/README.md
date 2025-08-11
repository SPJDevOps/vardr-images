# Vardr Next.js Hardened Image

A minimal, security‑focused, framework‑focused runtime image for Next.js apps with built‑in certificate handling. Beyond generic hardened bases, it tunes Node and CA management specifically for Next.js. Runs as non‑root by default and works well in regulated and air‑gapped environments.

## Key Features
- Runs as non-root (distroless Node.js)
- Custom CA certificates import via `/app/certs/*.crt`
- Uses `NODE_EXTRA_CA_CERTS` so system CAs remain available
- JSON logging option via `VARDR_JSON_LOGS=true`
- Works with Next.js standalone output (`output: 'standalone'`)

## Usage

### 1) Build your app as standalone
In your Next.js project `next.config.js`:

```js
/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'standalone',
};
module.exports = nextConfig;
```

Build your project and copy the standalone output into the image, or mount it at runtime.

### 2) Example Dockerfile for your app

```Dockerfile
# Build stage (your app)
FROM node:20-bookworm-slim AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci --only=production
COPY . .
RUN npm run build

# Runtime using Vardr Next.js base
FROM ghcr.io/vardr/nextjs:node20
WORKDIR /app

# Copy standalone output
COPY --from=build /app/.next/standalone .
COPY --from=build /app/public ./public

# Optional: mount or copy custom certs to /app/certs
# COPY certs/*.crt /app/certs/

ENV PORT=3000
EXPOSE 3000
```

### 3) Run with custom certificates
- Mount PEM-encoded CAs as `.crt` files to `/app/certs`.
- The entrypoint will validate and merge them into a custom bundle.
- Node is started with `NODE_EXTRA_CA_CERTS` pointing to that bundle.

```bash
docker run --rm -p 3000:3000 \
  -v $(pwd)/certs:/app/certs:ro \
  ghcr.io/vardr/nextjs:node20
```

## Environment Variables
- `PORT` (default: 3000)
- `HOST` (default: 0.0.0.0)
- `NODE_ENV` (default: production)
- `VARDR_JSON_LOGS` (default: false)

## How it starts
- The entrypoint searches for `/app/.next/standalone/server.js` or `/app/server.js` and starts it with Node.
- System CAs are preserved; custom CAs are appended via `NODE_EXTRA_CA_CERTS`.

## Security Notes
- Distroless runtime with no package manager.
- Non-root user by default.
- Certificates are validated with basic PEM guards; rejected if malformed. 