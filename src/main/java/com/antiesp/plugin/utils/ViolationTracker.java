package com.antiesp.plugin.utils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Tracks violations per player across all detection types
 * Manages escalation and cleanup
 */
public class ViolationTracker {
    
    private final String detectorName;
    private final Map<UUID, Integer> violationCounts;
    private final Map<UUID, Queue<Long>> violationTimestamps;
    private final long CLEANUP_INTERVAL = 3600000; // 1 hour
    private final long VIOLATION_EXPIRY = 1800000; // 30 minutes
    
    public ViolationTracker(String detectorName) {
        this.detectorName = detectorName;
        this.violationCounts = new ConcurrentHashMap<>();
        this.violationTimestamps = new ConcurrentHashMap<>();
    }
    
    /**
     * Add a violation for a player
     */
    public void addViolation(UUID playerUuid) {
        violationCounts.merge(playerUuid, 1, Integer::sum);
        violationTimestamps.computeIfAbsent(playerUuid, k -> 
            new ConcurrentLinkedQueue<>()
        ).add(System.currentTimeMillis());
    }
    
    /**
     * Get violation count for a player
     */
    public int getViolations(UUID playerUuid) {
        return violationCounts.getOrDefault(playerUuid, 0);
    }
    
    /**
     * Get violations within last N seconds
     */
    public int getRecentViolations(UUID playerUuid, long windowMs) {
        Queue<Long> timestamps = violationTimestamps.get(playerUuid);
        if (timestamps == null) return 0;
        
        long cutoff = System.currentTimeMillis() - windowMs;
        return (int) timestamps.stream()
            .filter(ts -> ts > cutoff)
            .count();
    }
    
    /**
     * Reset violations for a player
     */
    public void resetViolations(UUID playerUuid) {
        violationCounts.remove(playerUuid);
        violationTimestamps.remove(playerUuid);
    }
    
    /**
     * Clean up expired violations
     */
    public void cleanup() {
        long cutoff = System.currentTimeMillis() - VIOLATION_EXPIRY;
        
        violationTimestamps.forEach((uuid, timestamps) -> {
            timestamps.removeIf(ts -> ts < cutoff);
            if (timestamps.isEmpty()) {
                violationCounts.remove(uuid);
                violationTimestamps.remove(uuid);
            }
        });
    }
    
    /**
     * Get tracker statistics
     */
    public String getStatistics() {
        return String.format("%s Tracker: %d players monitored, %d total violations",
            detectorName,
            violationCounts.size(),
            violationCounts.values().stream().mapToInt(Integer::intValue).sum());
    }
}
