FROM ghcr.io/spjdevops/vardr/nextjs:node20

WORKDIR /app

# Copy a simple server entry (for demo). In real apps, copy Next.js standalone output.
COPY server.js /app/server.js

ENV PORT=3000
EXPOSE 3000 