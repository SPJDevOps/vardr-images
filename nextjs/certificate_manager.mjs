#!/usr/bin/env node

import fs from 'fs';
import path from 'path';
import crypto from 'crypto';
import { spawn } from 'child_process';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

class CertificateManager {
  constructor() {
    this.certsDir = '/app/certs';
    this.hashFile = '/app/certs.hash';
    this.customBundle = '/app/custom_ca_bundle.pem';
    this.jsonLogs = (process.env.VARDR_JSON_LOGS || 'false').toLowerCase() === 'true';
  }

  log(message, level = 'INFO') {
    if (this.jsonLogs) {
      const entry = {
        type: 'log',
        timestamp: new Date().toISOString(),
        level,
        message,
      };
      process.stdout.write(JSON.stringify(entry) + '\n');
    } else {
      process.stdout.write(`[Vardr] ${message}\n`);
    }
  }

  logJson(typeName, data) {
    try {
      const entry = { type: typeName, timestamp: new Date().toISOString(), ...data };
      process.stdout.write(JSON.stringify(entry) + '\n');
    } catch (e) {
      process.stderr.write(`[ERROR] Failed to generate JSON log: ${e}\n`);
    }
  }

  calculateCertsHash() {
    try {
      if (!fs.existsSync(this.certsDir)) return 'no-certs';
      const crtFiles = fs
        .readdirSync(this.certsDir)
        .filter((f) => f.endsWith('.crt'))
        .sort();
      const hash = crypto.createHash('sha256');
      for (const f of crtFiles) {
        const buf = fs.readFileSync(path.join(this.certsDir, f));
        hash.update(buf);
      }
      return hash.digest('hex');
    } catch (e) {
      this.log(`Error calculating certs hash: ${e}`, 'WARN');
      return String(Date.now());
    }
  }

  certsUnchanged() {
    try {
      if (!fs.existsSync(this.hashFile)) return false;
      const stored = fs.readFileSync(this.hashFile, 'utf8').trim();
      const current = this.calculateCertsHash();
      return stored === current;
    } catch (e) {
      this.log(`Error checking cert hash: ${e}`, 'WARN');
      return false;
    }
  }

  saveCertsHash() {
    try {
      fs.writeFileSync(this.hashFile, this.calculateCertsHash(), 'utf8');
    } catch (e) {
      this.log(`Error saving cert hash: ${e}`, 'WARN');
    }
  }

  validateCertificate(pemPath) {
    try {
      const data = fs.readFileSync(pemPath, 'utf8');
      // Simple validation: PEM must contain BEGIN CERTIFICATE
      return data.includes('-----BEGIN CERTIFICATE-----') && data.includes('-----END CERTIFICATE-----');
    } catch {
      return false;
    }
  }

  createCustomBundle() {
    try {
      // Use Debian system bundle as baseline
      const systemBundleCandidates = [
        '/etc/ssl/certs/ca-certificates.crt',
        '/etc/ssl/cert.pem',
      ];
      let base = '';
      for (const p of systemBundleCandidates) {
        if (fs.existsSync(p)) {
          base = fs.readFileSync(p, 'utf8');
          break;
        }
      }
      if (!base) {
        this.log('System CA bundle not found, proceeding with custom certs only', 'WARN');
      }

      let combined = base;
      if (fs.existsSync(this.certsDir)) {
        const crtFiles = fs
          .readdirSync(this.certsDir)
          .filter((f) => f.endsWith('.crt'))
          .sort();
        for (const f of crtFiles) {
          const full = path.join(this.certsDir, f);
          if (this.validateCertificate(full)) {
            combined += '\n' + fs.readFileSync(full, 'utf8');
            this.log(`Added certificate: ${f}`);
          } else {
            this.log(`Invalid certificate format: ${f}`, 'WARN');
          }
        }
      }

      fs.writeFileSync(this.customBundle, combined, 'utf8');
    } catch (e) {
      this.log(`Error creating custom CA bundle: ${e}`, 'ERROR');
      // best-effort fallback to empty bundle; Node will still use system CAs
      try { fs.writeFileSync(this.customBundle, '', 'utf8'); } catch {}
    }
  }

  importCertificates() {
    if (!fs.existsSync(this.certsDir)) {
      this.log('No certificates directory found');
      return { total: 0, success: 0, failed: 0 };
    }
    const files = fs.readdirSync(this.certsDir).filter((f) => f.endsWith('.crt'));
    if (files.length === 0) {
      this.log('No certificates found in /app/certs directory');
      return { total: 0, success: 0, failed: 0 };
    }
    this.log(`Found ${files.length} certificate(s)...`);

    let success = 0;
    let failed = 0;
    const results = {};

    for (const f of files) {
      const alias = path.basename(f, '.crt');
      try {
        if (!this.validateCertificate(path.join(this.certsDir, f))) {
          throw new Error('Invalid certificate format');
        }
        success += 1;
        results[alias] = 'SUCCESS';
        this.log(`Successfully validated ${alias}`);
      } catch (e) {
        failed += 1;
        results[alias] = `FAILED: ${e.message || e}`;
        this.log(`Could not validate ${alias}: ${e.message || e}`, 'WARN');
      }
    }

    this.createCustomBundle();
    return { total: files.length, success, failed, results };
  }

  detectServerEntry() {
    // Prefer Next.js standalone output if present
    const candidates = [
      '/app/.next/standalone/server.js',
      '/app/server.js',
      '/app/index.js',
    ];
    for (const c of candidates) {
      if (fs.existsSync(c)) return c;
    }
    return null;
  }

  startNextApp() {
    const entry = this.detectServerEntry();
    if (!entry) {
      this.log('No Next.js server entry found. Expected /app/.next/standalone/server.js or /app/server.js', 'ERROR');
      process.exit(1);
    }

    const env = { ...process.env };
    // Point Node to custom CA bundle while keeping system CAs
    env.NODE_EXTRA_CA_CERTS = this.customBundle;
    env.PORT = env.PORT || '3000';
    env.HOST = env.HOST || '0.0.0.0';

    const args = [entry];
    this.log(`Starting Next.js server: node ${args.join(' ')}`);

    const child = spawn('node', args, { stdio: 'inherit', env });
    child.on('exit', (code) => process.exit(code ?? 1));
  }

  run() {
    try {
      this.log('Starting certificate import process...');
      if (!this.certsUnchanged()) {
        const summary = this.importCertificates();
        this.saveCertsHash();
        if (this.jsonLogs) this.logJson('certificate_import_summary', summary);
      } else {
        this.log('Certificates unchanged, skipping import');
      }

      this.log('Starting Next.js application...');
      this.startNextApp();
    } catch (e) {
      this.log(`Failed to start application: ${e}`, 'ERROR');
      process.exit(1);
    }
  }
}

new CertificateManager().run(); 