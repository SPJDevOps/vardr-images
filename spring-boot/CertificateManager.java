import java.io.*;
import java.nio.file.*;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.MessageDigest;
import java.util.stream.Stream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.stream.Collectors;

public class CertificateManager {
    private static final String TRUSTSTORE_PATH = "/app/cacerts";
    private static final String TRUSTSTORE_PASSWORD = "changeit";
    private static final String CERTS_DIR = "/app/certs";
    private static final String HASH_FILE = "/tmp/certs.hash";
    
    // JSON logging for compliance
    private static final boolean JSON_LOGGING = "true".equals(System.getenv("VARD_JSON_LOGS"));
    
    public static void main(String[] args) {
        try {
            log("Starting certificate import process...", "INFO");
            
            // Check if certificates have changed
            if (certificatesUnchanged()) {
                log("Certificates unchanged, skipping import", "INFO");
            } else {
                // Import certificates if they exist
                importCertificates();
                // Save hash for next startup
                saveCertificateHash();
            }
            
            // Start Spring Boot application
            log("Starting Spring Boot application...", "INFO");
            startSpringBoot(args);
            
        } catch (Exception e) {
            log("Failed to start application: " + e.getMessage(), "ERROR");
            System.exit(1);
        }
    }
    
    private static boolean certificatesUnchanged() {
        try {
            Path hashFile = Paths.get(HASH_FILE);
            if (!Files.exists(hashFile)) {
                return false;
            }
            
            String storedHash = Files.readString(hashFile).trim();
            String currentHash = calculateCertificatesHash();
            
            return storedHash.equals(currentHash);
        } catch (Exception e) {
            log("Error checking certificate hash: " + e.getMessage(), "WARN");
            return false;
        }
    }
    
    private static String calculateCertificatesHash() throws Exception {
        Path certsDir = Paths.get(CERTS_DIR);
        if (!Files.exists(certsDir)) {
            return "no-certs";
        }
        
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        
        try (Stream<Path> paths = Files.walk(certsDir, 1)) {
            paths
                .filter(path -> path.toString().endsWith(".crt"))
                .sorted()
                .forEach(certPath -> {
                    try {
                        byte[] certBytes = Files.readAllBytes(certPath);
                        md.update(certBytes);
                    } catch (Exception e) {
                        log("Error reading certificate for hash: " + certPath + " - " + e.getMessage(), "WARN");
                    }
                });
        }
        
        byte[] hash = md.digest();
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
    
    private static void saveCertificateHash() {
        try {
            String hash = calculateCertificatesHash();
            Files.writeString(Paths.get(HASH_FILE), hash);
        } catch (Exception e) {
            log("Error saving certificate hash: " + e.getMessage(), "WARN");
        }
    }
    
    private static void importCertificates() throws Exception {
        try {
            Path certsDir = Paths.get(CERTS_DIR);
            
            if (!Files.exists(certsDir)) {
                log("No certificates directory found", "INFO");
                return;
            }
            
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);
            Map<String, String> results = new HashMap<>();
            
            // Check both the main certs directory and the nested certs directory
            List<Path> searchDirs = new ArrayList<>();
            searchDirs.add(certsDir);
            
            // Check for nested certs directory
            Path nestedCertsDir = certsDir.resolve("certs");
            if (Files.exists(nestedCertsDir)) {
                searchDirs.add(nestedCertsDir);
            }
            
            long totalCertCount = 0;
            
            for (Path searchDir : searchDirs) {
                try (Stream<Path> paths = Files.walk(searchDir, 1)) {
                    // First, let's see all files before filtering
                    List<Path> allFiles = paths.filter(Files::isRegularFile).collect(Collectors.toList());
                    
                    // Now do the actual filtering
                    long certCount = allFiles.stream()
                        .filter(path -> path.toString().endsWith(".crt"))
                        .count();
                    
                    totalCertCount += certCount;
                    
                    if (certCount > 0) {
                        // Process certificates from this directory
                        allFiles.stream()
                            .filter(path -> path.toString().endsWith(".crt"))
                            .forEach(certPath -> {
                                String alias = certPath.getFileName().toString().replace(".crt", "");
                                
                                try {
                                    // Load existing truststore
                                    KeyStore truststore = KeyStore.getInstance("JKS");
                                    File truststoreFile = new File(TRUSTSTORE_PATH);
                                    if (truststoreFile.exists()) {
                                        try (FileInputStream fis = new FileInputStream(TRUSTSTORE_PATH)) {
                                            truststore.load(fis, TRUSTSTORE_PASSWORD.toCharArray());
                                        }
                                    } else {
                                        // Create a new truststore if it doesn't exist
                                        truststore.load(null, TRUSTSTORE_PASSWORD.toCharArray());
                                    }
                                    
                                    CertificateFactory cf = CertificateFactory.getInstance("X.509");
                                    
                                    try (FileInputStream certFis = new FileInputStream(certPath.toFile())) {
                                        X509Certificate cert = (X509Certificate) cf.generateCertificate(certFis);
                                        truststore.setCertificateEntry(alias, cert);
                                        log("Successfully imported certificate: " + alias, "INFO");
                                        successCount.incrementAndGet();
                                        results.put(alias, "SUCCESS");
                                    }
                                    
                                    // Save updated truststore
                                    try (FileOutputStream fos = new FileOutputStream(TRUSTSTORE_PATH)) {
                                        truststore.store(fos, TRUSTSTORE_PASSWORD.toCharArray());
                                    }
                                    
                                } catch (Exception e) {
                                    String errorMsg = "Could not import " + alias + ": " + e.getMessage();
                                    log(errorMsg, "WARN");
                                    failureCount.incrementAndGet();
                                    results.put(alias, "FAILED: " + e.getMessage());
                                }
                            });
                    }
                }
            }
            
            if (totalCertCount == 0) {
                log("No certificates found in /app/certs directory", "INFO");
                return;
            }
            
            // Log summary
            String summary = String.format("Certificate import completed: %d successful, %d failed", 
                successCount.get(), failureCount.get());
            log(summary, "INFO");
            
            // JSON logging for compliance
            if (JSON_LOGGING) {
                logJson("certificate_import_summary", Map.of(
                    "timestamp", Instant.now().toString(),
                    "successful_imports", successCount.get(),
                    "failed_imports", failureCount.get(),
                    "total_certificates", (int)totalCertCount,
                    "results", results
                ));
            }
            
        } catch (Exception e) {
            log("Failed to import certificates: " + e.getMessage(), "ERROR");
            throw e;
        }
    }
    
    private static void startSpringBoot(String[] args) throws Exception {
        // Set system properties for custom truststore
        System.setProperty("javax.net.ssl.trustStore", TRUSTSTORE_PATH);
        System.setProperty("javax.net.ssl.trustStorePassword", TRUSTSTORE_PASSWORD);
        System.setProperty("java.security.egd", "file:/dev/./urandom");
        
        // Build Spring Boot optimized JVM options
        List<String> jvmOptions = buildSpringBootJvmOptions();
        
        // Create a new process to run the Spring Boot JAR
        List<String> command = new ArrayList<>();
        command.add("java");
        command.addAll(jvmOptions);
        command.add("-jar");
        command.add("/app/app.jar");
        
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.inheritIO();
        Process process = pb.start();
        
        // Wait for the process to complete
        int exitCode = process.waitFor();
        System.exit(exitCode);
    }
    
    private static List<String> buildSpringBootJvmOptions() {
        List<String> options = new ArrayList<>();
        
        // Security and SSL
        options.add("-Djavax.net.ssl.trustStore=" + TRUSTSTORE_PATH);
        options.add("-Djavax.net.ssl.trustStorePassword=" + TRUSTSTORE_PASSWORD);
        options.add("-Djava.security.egd=file:/dev/./urandom");
        
        // Memory management (Spring Boot best practices)
        String maxHeap = System.getenv("JAVA_MAX_HEAP");
        String minHeap = System.getenv("JAVA_MIN_HEAP");
        
        if (maxHeap != null) {
            options.add("-Xmx" + maxHeap);
        } else {
            // Default to percentage-based memory allocation
            options.add("-XX:+UseContainerSupport");
            options.add("-XX:MaxRAMPercentage=75.0");
        }
        
        if (minHeap != null) {
            options.add("-Xms" + minHeap);
        } else {
            // Default initial heap size
            options.add("-XX:InitialRAMPercentage=50.0");
        }
        
        // Garbage Collection (Spring Boot optimized)
        String gcType = System.getenv("JAVA_GC_TYPE");
        if ("g1".equals(gcType) || gcType == null) {
            // G1GC is the default and recommended for Spring Boot
            options.add("-XX:+UseG1GC");
            options.add("-XX:+UseStringDeduplication");
            options.add("-XX:MaxGCPauseMillis=200");
        } else if ("zgc".equals(gcType)) {
            // ZGC for low-latency applications
            options.add("-XX:+UseZGC");
        } else if ("shenandoah".equals(gcType)) {
            // Shenandoah for low-latency applications
            options.add("-XX:+UseShenandoahGC");
        }
        
        // JVM tuning for Spring Boot
        options.add("-XX:+UseContainerSupport");
        options.add("-XX:+UnlockExperimentalVMOptions");
        
        // Performance optimizations
        options.add("-XX:+OptimizeStringConcat");
        options.add("-XX:+UseCompressedOops");
        options.add("-XX:+UseCompressedClassPointers");
        
        // Modern GC logging (Java 9+ unified logging)
        options.add("-Xlog:gc*:file=/tmp/gc.log:time,uptime,level,tags");
        
        // Security hardening (optional - can be disabled if needed)
        // Note: This disables Java security manager policy entirely
        // Remove this line if you need Java security policy
        // options.add("-Djava.security.policy=/dev/null");
        
        options.add("-Djava.awt.headless=true");
        
        // Spring Boot specific optimizations
        options.add("-Dspring.jmx.enabled=false");
        options.add("-Dspring.main.banner-mode=off");
        options.add("-Dspring.main.web-application-type=servlet");
        
        // Disable unnecessary features for containerized environments
        options.add("-Djava.net.preferIPv4Stack=true");
        options.add("-Dfile.encoding=UTF-8");
        options.add("-Duser.timezone=UTC");
        
        // Add custom JVM options from environment
        String customOptions = System.getenv("JAVA_OPTS");
        if (customOptions != null && !customOptions.trim().isEmpty()) {
            String[] customOpts = customOptions.split("\\s+");
            for (String opt : customOpts) {
                if (!opt.trim().isEmpty()) {
                    options.add(opt.trim());
                }
            }
        }
        
        return options;
    }
    
    private static void log(String message, String level) {
        if (JSON_LOGGING) {
            logJson("log", Map.of(
                "timestamp", Instant.now().toString(),
                "level", level,
                "message", message
            ));
        } else {
            System.out.println("[Vardr] " + message);
        }
    }
    
    private static void logJson(String type, Map<String, Object> data) {
        try {
            StringBuilder json = new StringBuilder();
            json.append("{\"type\":\"").append(type).append("\"");
            
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                json.append(",\"").append(entry.getKey()).append("\":");
                if (entry.getValue() instanceof String) {
                    json.append("\"").append(entry.getValue()).append("\"");
                } else {
                    json.append(entry.getValue());
                }
            }
            
            json.append("}");
            System.out.println(json.toString());
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to generate JSON log: " + e.getMessage());
        }
    }
} 