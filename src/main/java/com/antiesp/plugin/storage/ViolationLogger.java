package com.antiesp.plugin.storage;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/**
 * Violation Logging System
 * Stores all violations for investigation, appeals, and pattern analysis
 */
public class ViolationLogger {
    
    private final JavaPlugin plugin;
    private final File logDirectory;
    private final ExecutorService executorService;
    private final SimpleDateFormat dateFormat;
    private final BlockingQueue<LogEntry> logQueue;
    
    public ViolationLogger(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logDirectory = new File(plugin.getDataFolder(), "violations");
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "AntiESP-Logger");
            t.setDaemon(true);
            return t;
        });
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        this.logQueue = new LinkedBlockingQueue<>();
        
        // Create log directory
        if (!logDirectory.exists()) {
            logDirectory.mkdirs();
        }
        
        // Start async logging thread
        startAsyncLogger();
    }
    
    /**
     * Log a violation
     */
    public void logViolation(Player player, String detectorType, String details, int violationCount) {
        LogEntry entry = new LogEntry(
            player.getUniqueId(),
            player.getName(),
            player.getAddress().getAddress().getHostAddress(),
            detectorType,
            details,
            violationCount,
            System.currentTimeMillis()
        );
        
        try {
            logQueue.offer(entry, 100, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            plugin.getLogger().warning("Failed to queue violation log");
        }
    }
    
    /**
     * Async logging thread
     */
    private void startAsyncLogger() {
        executorService.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    LogEntry entry = logQueue.take();
                    writeViolationLog(entry);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }
    
    /**
     * Write violation to log file
     */
    private void writeViolationLog(LogEntry entry) {
        String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date(entry.timestamp));
        File logFile = new File(logDirectory, "violations_" + date + ".log");
        
        try (FileWriter fw = new FileWriter(logFile, true);
             BufferedWriter bw = new BufferedWriter(fw)) {
            
            String logLine = String.format(
                "[%s] UUID: %s | Player: %s | IP: %s | Detector: %s | Violations: %d | Details: %s%n",
                dateFormat.format(new Date(entry.timestamp)),
                entry.playerId,
                entry.playerName,
                entry.playerIp,
                entry.detectorType,
                entry.violationCount,
                entry.details
            );
            
            bw.write(logLine);
            bw.flush();
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to write violation log: " + e.getMessage());
        }
    }
    
    /**
     * Get violation history for a player
     */
    public List<ViolationRecord> getPlayerViolationHistory(UUID playerId) {
        List<ViolationRecord> records = new ArrayList<>();
        
        try {
            File[] logFiles = logDirectory.listFiles((d, name) -> name.startsWith("violations_"));
            if (logFiles == null) return records;
            
            String playerIdStr = playerId.toString();
            
            for (File file : logFiles) {
                List<String> lines = Files.readAllLines(file.toPath());
                
                for (String line : lines) {
                    if (line.contains(playerIdStr)) {
                        ViolationRecord record = parseLogLine(line);
                        if (record != null) {
                            records.add(record);
                        }
                    }
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to read violation history: " + e.getMessage());
        }
        
        return records;
    }
    
    /**
     * Parse log line into violation record
     */
    private ViolationRecord parseLogLine(String line) {
        try {
            // Parse format: [timestamp] UUID: xxx | Player: xxx | ...
            String[] parts = line.split(" \\| ");
            if (parts.length < 4) return null;
            
            String timestamp = parts[0].substring(1, parts[0].length() - 1);
            String uuid = parts[1].split(": ")[1];
            String playerName = parts[2].split(": ")[1];
            String ip = parts[3].split(": ")[1];
            String detector = parts[4].split(": ")[1];
            int violations = Integer.parseInt(parts[5].split(": ")[1]);
            String details = parts.length > 6 ? parts[6].split(": ")[1] : "";
            
            return new ViolationRecord(uuid, playerName, ip, detector, violations, details, timestamp);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Get statistics from logs
     */
    public ViolationStatistics getStatistics() {
        ViolationStatistics stats = new ViolationStatistics();
        
        try {
            File[] logFiles = logDirectory.listFiles((d, name) -> name.startsWith("violations_"));
            if (logFiles == null) return stats;
            
            for (File file : logFiles) {
                List<String> lines = Files.readAllLines(file.toPath());
                stats.totalViolations += lines.size();
                
                for (String line : lines) {
                    // Count by detector type
                    if (line.contains("entity_esp")) stats.entityESPCount++;
                    if (line.contains("block_esp")) stats.blockESPCount++;
                    if (line.contains("chest_esp")) stats.chestESPCount++;
                    if (line.contains("player_esp")) stats.playerESPCount++;
                    
                    // Extract IP and count unique
                    String[] parts = line.split("IP: ");
                    if (parts.length > 1) {
                        String ip = parts[1].split(" \\|")[0];
                        stats.ipAddresses.add(ip);
                    }
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to read statistics: " + e.getMessage());
        }
        
        return stats;
    }
    
    /**
     * Export violations for a player (for admin review)
     */
    public String exportPlayerViolations(UUID playerId) {
        List<ViolationRecord> records = getPlayerViolationHistory(playerId);
        StringBuilder sb = new StringBuilder();
        
        sb.append("=".repeat(80)).append("\n");
        sb.append("VIOLATION REPORT FOR ").append(playerId).append("\n");
        sb.append("=".repeat(80)).append("\n\n");
        
        sb.append("SUMMARY:\n");
        sb.append("Total Violations: ").append(records.size()).append("\n");
        
        // Group by detector
        Map<String, Integer> byDetector = new HashMap<>();
        for (ViolationRecord record : records) {
            byDetector.merge(record.detectorType, 1, Integer::sum);
        }
        
        sb.append("\nViolations by Type:\n");
        for (Map.Entry<String, Integer> entry : byDetector.entrySet()) {
            sb.append("  - ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        
        sb.append("\n\nDETAILED VIOLATIONS:\n");
        sb.append("-".repeat(80)).append("\n");
        
        for (ViolationRecord record : records) {
            sb.append("Timestamp: ").append(record.timestamp).append("\n");
            sb.append("Detector: ").append(record.detectorType).append("\n");
            sb.append("Violations: ").append(record.violationCount).append("\n");
            sb.append("Details: ").append(record.details).append("\n");
            sb.append("IP: ").append(record.ip).append("\n");
            sb.append("-".repeat(80)).append("\n");
        }
        
        return sb.toString();
    }
    
    public void shutdown() {
        executorService.shutdown();
    }
    
    /**
     * Log entry data
     */
    private static class LogEntry {
        UUID playerId;
        String playerName;
        String playerIp;
        String detectorType;
        String details;
        int violationCount;
        long timestamp;
        
        LogEntry(UUID playerId, String playerName, String playerIp, String detectorType,
                String details, int violationCount, long timestamp) {
            this.playerId = playerId;
            this.playerName = playerName;
            this.playerIp = playerIp;
            this.detectorType = detectorType;
            this.details = details;
            this.violationCount = violationCount;
            this.timestamp = timestamp;
        }
    }
    
    /**
     * Violation record for displaying history
     */
    public static class ViolationRecord {
        public String uuid;
        public String playerName;
        public String ip;
        public String detectorType;
        public int violationCount;
        public String details;
        public String timestamp;
        
        ViolationRecord(String uuid, String playerName, String ip, String detectorType,
                       int violationCount, String details, String timestamp) {
            this.uuid = uuid;
            this.playerName = playerName;
            this.ip = ip;
            this.detectorType = detectorType;
            this.violationCount = violationCount;
            this.details = details;
            this.timestamp = timestamp;
        }
    }
    
    /**
     * Violation statistics
     */
    public static class ViolationStatistics {
        public int totalViolations = 0;
        public int entityESPCount = 0;
        public int blockESPCount = 0;
        public int chestESPCount = 0;
        public int playerESPCount = 0;
        public Set<String> ipAddresses = new HashSet<>();
        
        @Override
        public String toString() {
            return String.format(
                "Total Violations: %d | Entity: %d | Block: %d | Chest: %d | Player: %d | Unique IPs: %d",
                totalViolations, entityESPCount, blockESPCount, chestESPCount,
                playerESPCount, ipAddresses.size()
            );
        }
    }
}
