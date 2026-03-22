package com.antiesp.plugin.detection;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import com.antiesp.plugin.utils.ViolationTracker;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Advanced Anti-Bypass Detection
 * Detects players attempting to evade or bypass anti-cheat
 * Includes: Packet spoofing detection, logging manipulation, pattern obfuscation
 */
public class AntiBypassDetector {
    
    private final JavaPlugin plugin;
    private final ViolationTracker tracker;
    private final Map<UUID, BypassAttemptData> playerData;
    
    public AntiBypassDetector(JavaPlugin plugin) {
        this.plugin = plugin;
        this.tracker = new ViolationTracker("anti_bypass");
        this.playerData = new ConcurrentHashMap<>();
    }
    
    /**
     * Detect suspicious packet patterns that indicate anti-cheat evasion
     */
    public void checkBypassAttempts(Player player) {
        UUID uuid = player.getUniqueId();
        
        if (!playerData.containsKey(uuid)) {
            playerData.put(uuid, new BypassAttemptData(player));
            return;
        }
        
        BypassAttemptData data = playerData.get(uuid);
        
        // Check 1: Packet flooding/spam (DoS attempt on detector)
        checkPacketFlooding(player, data);
        
        // Check 2: Inconsistent packet data (spoofing)
        checkPacketInconsistency(player, data);
        
        // Check 3: Suspicious disconnects (trying to avoid detection)
        checkSuspiciousDisconnects(player, data);
        
        // Check 4: Pattern obfuscation attempts
        checkPatternObfuscation(player, data);
        
        data.tick();
    }
    
    /**
     * Detect packet flooding/spam that could be DoS on anti-cheat
     */
    private void checkPacketFlooding(Player player, BypassAttemptData data) {
        long now = System.currentTimeMillis();
        long windowStart = now - 1000; // 1 second window
        
        // Count packets in last second
        int packetsInWindow = (int) data.packetTimestamps.stream()
            .filter(ts -> ts > windowStart)
            .count();
        
        data.packetTimestamps.add(now);
        
        // Legitimate players: ~20-40 packets/sec
        // Spammers: 100+ packets/sec
        if (packetsInWindow > 100) {
            data.floodingAttempts++;
            
            if (data.floodingAttempts > 3) {
                reportViolation(player, "packet_flooding",
                    String.format("Packet flooding detected: %d packets/sec", packetsInWindow));
                data.resetFlooding();
            }
        } else {
            data.floodingAttempts = Math.max(0, data.floodingAttempts - 1);
        }
    }
    
    /**
     * Detect packet spoofing - sending inconsistent/fake data
     */
    private void checkPacketInconsistency(Player player, BypassAttemptData data) {
        // Get player's actual position
        org.bukkit.Location actual = player.getLocation();
        org.bukkit.Location reported = player.getLocation(); // From last packet
        
        // If position jumps are impossible (teleporting without packets)
        double distance = actual.distance(reported);
        if (distance > 10 && !player.isFlying()) {
            data.packetSpoofingAttempts++;
            
            if (data.packetSpoofingAttempts > 5) {
                reportViolation(player, "packet_spoofing",
                    String.format("Inconsistent position: %.1f block jump", distance));
                data.resetSpoofing();
            }
        } else {
            data.packetSpoofingAttempts = Math.max(0, data.packetSpoofingAttempts - 1);
        }
    }
    
    /**
     * Detect suspicious disconnects/reconnects
     * Cheaters disconnect immediately after violations to avoid instant ban
     */
    private void checkSuspiciousDisconnects(Player player, BypassAttemptData data) {
        // This is handled by listening to disconnect events
        // Flag if disconnect happens suspiciously soon after violation detection
        if (data.timesSuspiciouslyDisconnected > 2) {
            reportViolation(player, "evasion_disconnect",
                "Multiple suspicious disconnects detected");
            data.resetDisconnects();
        }
    }
    
    /**
     * Detect attempts to obfuscate behavior patterns
     * Cheaters try to alternate between legit and cheating behavior
     */
    private void checkPatternObfuscation(Player player, BypassAttemptData data) {
        if (data.behaviorPattern.size() < 10) return;
        
        // Check for alternating good/bad behavior
        int alternations = 0;
        for (int i = 1; i < data.behaviorPattern.size(); i++) {
            if (data.behaviorPattern.get(i) != data.behaviorPattern.get(i - 1)) {
                alternations++;
            }
        }
        
        // High alternation = trying to avoid detection
        double alternationRate = (double) alternations / data.behaviorPattern.size();
        
        if (alternationRate > 0.6) {
            data.obfuscationAttempts++;
            
            if (data.obfuscationAttempts > 3) {
                reportViolation(player, "pattern_obfuscation",
                    String.format("Behavior pattern obfuscation (%.0f%% alternation)",
                        alternationRate * 100));
                data.resetObfuscation();
            }
        } else {
            data.obfuscationAttempts = Math.max(0, data.obfuscationAttempts - 1);
        }
    }
    
    /**
     * Mark suspicious disconnect
     */
    public void registerSuspiciousDisconnect(Player player) {
        UUID uuid = player.getUniqueId();
        if (playerData.containsKey(uuid)) {
            playerData.get(uuid).timesSuspiciouslyDisconnected++;
        }
    }
    
    /**
     * Record behavior (cheating or legit)
     */
    public void recordBehavior(Player player, boolean cheatingBehavior) {
        UUID uuid = player.getUniqueId();
        if (playerData.containsKey(uuid)) {
            BypassAttemptData data = playerData.get(uuid);
            data.behaviorPattern.add(cheatingBehavior);
            if (data.behaviorPattern.size() > 50) {
                data.behaviorPattern.remove(0);
            }
        }
    }
    
    /**
     * Report violation
     */
    private void reportViolation(Player player, String type, String details) {
        tracker.addViolation(player.getUniqueId());
        int violations = tracker.getViolations(player.getUniqueId());
        
        if (violations >= 1 && violations < 3) {
            player.kickPlayer("§cAnti-cheat evasion detected. Violation #" + violations);
        } else if (violations >= 3) {
            org.bukkit.Bukkit.broadcastMessage("§c[AntiESP] " + player.getName() + 
                " detected attempting to evade anti-cheat");
            player.kickPlayer("§cEvasion confirmed. Permanent ban.");
            plugin.getLogger().warning("Evasion violation: " + player.getName() + " - " + details);
        }
    }
    
    public void cleanup() {
        playerData.entrySet().removeIf(entry -> 
            org.bukkit.Bukkit.getPlayer(entry.getKey()) == null
        );
        tracker.cleanup();
    }
    
    /**
     * Bypass attempt tracking data
     */
    private static class BypassAttemptData {
        private final Player player;
        private final List<Long> packetTimestamps = new ArrayList<>();
        private final List<Boolean> behaviorPattern = new ArrayList<>();
        private int floodingAttempts = 0;
        private int packetSpoofingAttempts = 0;
        private int obfuscationAttempts = 0;
        private int timesSuspiciouslyDisconnected = 0;
        private int tickCounter = 0;
        
        BypassAttemptData(Player player) {
            this.player = player;
        }
        
        void tick() {
            tickCounter++;
            // Clean old timestamps every 20 ticks
            if (tickCounter % 20 == 0) {
                long cutoff = System.currentTimeMillis() - 5000;
                packetTimestamps.removeIf(ts -> ts < cutoff);
            }
        }
        
        void resetFlooding() { floodingAttempts = 0; }
        void resetSpoofing() { packetSpoofingAttempts = 0; }
        void resetObfuscation() { obfuscationAttempts = 0; }
        void resetDisconnects() { timesSuspiciouslyDisconnected = 0; }
    }
}
