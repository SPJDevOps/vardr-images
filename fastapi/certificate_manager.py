#!/usr/bin/env python3
"""
Vardr Certificate Manager for FastAPI
Handles certificate import and FastAPI application startup with security features.
"""

import os
import sys
import hashlib
import json
import time
import subprocess
from pathlib import Path
from typing import Dict, List, Optional, Any
import ssl
import tempfile
import shutil
from datetime import datetime
import certifi
import urllib3


class CertificateManager:
    """Manages certificate import and FastAPI application startup."""
    
    def __init__(self):
        self.certs_dir = "/certs"
        self.hash_file = "/app/certs.hash"
        self.json_logging = os.getenv("VARD_JSON_LOGS", "false").lower() == "true"
        self.custom_ca_bundle = "/app/custom_ca_bundle.pem"
        
    def log(self, message: str, level: str = "INFO") -> None:
        """Log messages with optional JSON formatting."""
        if self.json_logging:
            log_entry = {
                "type": "log",
                "timestamp": datetime.now().isoformat(),
                "level": level,
                "message": message
            }
            print(json.dumps(log_entry))
        else:
            print(f"[Vardr] {message}")
    
    def log_json(self, type_name: str, data: Dict[str, Any]) -> None:
        """Log structured JSON data."""
        try:
            log_entry = {
                "type": type_name,
                "timestamp": datetime.now().isoformat(),
                **data
            }
            print(json.dumps(log_entry))
        except Exception as e:
            print(f"[ERROR] Failed to generate JSON log: {e}", file=sys.stderr)
    
    def calculate_certificates_hash(self) -> str:
        """Calculate SHA-256 hash of all certificates."""
        certs_dir = Path(self.certs_dir)
        if not certs_dir.exists():
            return "no-certs"
        
        sha256_hash = hashlib.sha256()
        
        # Find all .crt files and sort them for consistent hashing
        cert_files = sorted(certs_dir.glob("*.crt"))
        
        for cert_file in cert_files:
            try:
                with open(cert_file, 'rb') as f:
                    sha256_hash.update(f.read())
            except Exception as e:
                self.log(f"Error reading certificate for hash: {cert_file} - {e}", "WARN")
        
        return sha256_hash.hexdigest()
    
    def certificates_unchanged(self) -> bool:
        """Check if certificates have changed since last import."""
        try:
            hash_file = Path(self.hash_file)
            if not hash_file.exists():
                return False
            
            stored_hash = hash_file.read_text().strip()
            current_hash = self.calculate_certificates_hash()
            
            return stored_hash == current_hash
        except Exception as e:
            self.log(f"Error checking certificate hash: {e}", "WARN")
            return False
    
    def save_certificate_hash(self) -> None:
        """Save the current certificate hash for future comparison."""
        try:
            hash_value = self.calculate_certificates_hash()
            Path(self.hash_file).write_text(hash_value)
        except Exception as e:
            self.log(f"Error saving certificate hash: {e}", "WARN")
    
    def validate_certificate(self, cert_path: Path) -> bool:
        """Validate certificate format using Python's ssl module."""
        try:
            with open(cert_path, 'r') as f:
                cert_data = f.read()
            
            # Try to load the certificate using ssl module
            # Create a temporary context to validate the certificate
            context = ssl.create_default_context()
            context.load_verify_locations(cadata=cert_data)
            return True
        except Exception:
            return False
    
    def create_custom_ca_bundle(self) -> None:
        """Create a custom CA bundle by combining system CAs with custom certificates."""
        try:
            # Start with the system CA bundle
            system_ca_bundle = certifi.where()
            
            # Create custom bundle starting with system CAs
            with open(system_ca_bundle, 'r') as f:
                custom_bundle_content = f.read()
            
            # Add custom certificates
            certs_dir = Path(self.certs_dir)
            if certs_dir.exists():
                cert_files = sorted(certs_dir.glob("*.crt"))
                
                for cert_file in cert_files:
                    try:
                        if self.validate_certificate(cert_file):
                            with open(cert_file, 'r') as f:
                                cert_content = f.read()
                                custom_bundle_content += "\n" + cert_content
                            self.log(f"Added certificate: {cert_file.name}")
                        else:
                            self.log(f"Invalid certificate format: {cert_file.name}", "WARN")
                    except Exception as e:
                        self.log(f"Error processing certificate {cert_file.name}: {e}", "WARN")
            
            # Write the custom bundle
            with open(self.custom_ca_bundle, 'w') as f:
                f.write(custom_bundle_content)
                
            self.log(f"Created custom CA bundle with {len(cert_files)} additional certificates")
            
        except Exception as e:
            self.log(f"Error creating custom CA bundle: {e}", "ERROR")
            # Fallback to system CA bundle
            shutil.copy(certifi.where(), self.custom_ca_bundle)
    
    def import_certificates(self) -> None:
        """Import certificates by creating a custom CA bundle."""
        certs_dir = Path(self.certs_dir)
        if not certs_dir.exists():
            self.log("No certificates directory found")
            return
        
        # Count certificates
        cert_files = list(certs_dir.glob("*.crt"))
        if not cert_files:
            self.log("No certificates found in /certs directory")
            return
        
        self.log(f"Found {len(cert_files)} certificate(s)...")
        
        success_count = 0
        failure_count = 0
        results = {}
        
        for cert_file in cert_files:
            alias = cert_file.stem
            self.log(f"Processing {alias} from {cert_file.name}")
            
            try:
                # Validate certificate
                if not self.validate_certificate(cert_file):
                    raise ValueError("Invalid certificate format")
                
                success_count += 1
                results[alias] = "SUCCESS"
                self.log(f"Successfully validated {alias}")
                
            except Exception as e:
                error_msg = f"Could not validate {alias}: {e}"
                self.log(error_msg, "WARN")
                failure_count += 1
                results[alias] = f"FAILED: {e}"
        
        # Create the custom CA bundle
        self.create_custom_ca_bundle()
        
        # Log summary
        summary = f"Certificate processing completed: {success_count} successful, {failure_count} failed"
        self.log(summary)
        
        # JSON logging for compliance
        if self.json_logging:
            self.log_json("certificate_import_summary", {
                "successful_imports": success_count,
                "failed_imports": failure_count,
                "total_certificates": len(cert_files),
                "results": results
            })
    
    def start_fastapi_application(self) -> None:
        """Start the FastAPI application with proper environment setup."""
        # Set environment variables for certificate truststore
        env = os.environ.copy()
        env["SSL_CERT_FILE"] = self.custom_ca_bundle
        env["REQUESTS_CA_BUNDLE"] = self.custom_ca_bundle
        env["CURL_CA_BUNDLE"] = self.custom_ca_bundle
        env["REQUESTS_CA_BUNDLE"] = self.custom_ca_bundle
        
        # Set Python-specific environment variables
        env["PYTHONUNBUFFERED"] = "1"
        env["PYTHONDONTWRITEBYTECODE"] = "1"
        
        # Determine the application file to run
        app_file = self._find_application_file()
        
        if not app_file:
            self.log("No FastAPI application found. Please ensure your app.py or main.py exists in /app", "ERROR")
            sys.exit(1)
        
        self.log(f"Starting FastAPI application: {app_file}")
        
        # Start the FastAPI application
        try:
            # Use uvicorn to run the FastAPI app
            # Remove .py extension for module name
            module_name = app_file.replace('.py', '')
            cmd = [
                "python", "-m", "uvicorn", 
                f"{module_name}:app",
                "--host", "0.0.0.0",
                "--port", os.getenv("PORT", "8000"),
                "--workers", os.getenv("WORKERS", "1")
            ]
            
            # Add custom uvicorn options
            uvicorn_opts = os.getenv("UVICORN_OPTS", "")
            if uvicorn_opts:
                cmd.extend(uvicorn_opts.split())
            
            self.log(f"Running command: {' '.join(cmd)}")
            
            # Start the process
            process = subprocess.Popen(cmd, env=env)
            
            # Wait for the process to complete
            exit_code = process.wait()
            sys.exit(exit_code)
            
        except Exception as e:
            self.log(f"Failed to start FastAPI application: {e}", "ERROR")
            sys.exit(1)
    
    def _find_application_file(self) -> Optional[str]:
        """Find the FastAPI application file."""
        app_dir = Path("/app")
        
        # Common FastAPI application file names
        possible_files = [
            "main.py",
            "app.py", 
            "application.py",
            "api.py",
            "server.py"
        ]
        
        for filename in possible_files:
            file_path = app_dir / filename
            if file_path.exists():
                return filename
        
        # If no standard file found, look for any Python file with FastAPI
        for py_file in app_dir.glob("*.py"):
            try:
                content = py_file.read_text()
                if "FastAPI" in content or "from fastapi" in content:
                    return py_file.name
            except Exception:
                continue
        
        return None
    
    def run(self) -> None:
        """Main entry point for the certificate manager."""
        try:
            self.log("Starting certificate import process...")
            
            # Check if certificates have changed
            if self.certificates_unchanged():
                self.log("Certificates unchanged, skipping import")
            else:
                # Import certificates if they exist
                self.import_certificates()
                # Save hash for next startup
                self.save_certificate_hash()
            
            # Start FastAPI application
            self.log("Starting FastAPI application...")
            self.start_fastapi_application()
            
        except Exception as e:
            self.log(f"Failed to start application: {e}", "ERROR")
            sys.exit(1)


if __name__ == "__main__":
    manager = CertificateManager()
    manager.run() 