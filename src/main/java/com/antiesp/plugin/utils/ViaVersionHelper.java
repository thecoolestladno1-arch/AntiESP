package com.antiesp.plugin.utils;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * ViaVersion integration for cross-version support
 * Allows detection to work with players on older/newer protocol versions
 */
public class ViaVersionHelper {
    
    private Plugin viaVersion;
    private boolean available = false;
    
    public ViaVersionHelper() {
        this.viaVersion = Bukkit.getPluginManager().getPlugin("ViaVersion");
        this.available = viaVersion != null;
    }
    
    /**
     * Check if ViaVersion is available
     */
    public boolean isAvailable() {
        return available;
    }
    
    /**
     * Get player protocol version
     */
    public int getPlayerProtocolVersion(java.util.UUID playerUuid) {
        if (!available) return 765; // Default to 1.21.11
        
        try {
            Object viaApi = Class.forName("com.viaversion.viaversion.api.Via")
                .getMethod("getAPI")
                .invoke(null);
            
            Object connectionManager = Class.forName("com.viaversion.viaversion.api.Via")
                .getMethod("getConnectionManager")
                .invoke(viaApi);
            
            Object connection = connectionManager.getClass()
                .getMethod("getConnectedClient", java.util.UUID.class)
                .invoke(connectionManager, playerUuid);
            
            if (connection != null) {
                return (Integer) connection.getClass()
                    .getMethod("getProtocolVersion")
                    .invoke(connection);
            }
        } catch (Exception e) {
            return 765;
        }
        
        return 765;
    }
    
    /**
     * Normalize detection parameters based on client protocol version
     * Older versions have different packet structures and capabilities
     */
    public DetectionParams getDetectionParams(int protocolVersion) {
        DetectionParams params = new DetectionParams();
        
        // Adjust detection thresholds based on protocol version
        if (protocolVersion < 340) { // 1.12.2 and below
            params.detectionRadius = 48.0;
            params.entityCheckInterval = 2;
            params.miningEfficiencyThreshold = 0.70;
        } else if (protocolVersion < 573) { // 1.14.4 and below
            params.detectionRadius = 52.0;
            params.entityCheckInterval = 2;
            params.miningEfficiencyThreshold = 0.72;
        } else if (protocolVersion < 756) { // 1.16.5 and below
            params.detectionRadius = 56.0;
            params.entityCheckInterval = 1;
            params.miningEfficiencyThreshold = 0.74;
        } else {
            params.detectionRadius = 64.0; // Default for 1.17+
            params.entityCheckInterval = 1;
            params.miningEfficiencyThreshold = 0.75;
        }
        
        return params;
    }
    
    /**
     * Check if protocol version supports certain detection methods
     */
    public boolean supportsFeature(int protocolVersion, String feature) {
        return switch (feature) {
            case "entity_tracking" -> protocolVersion >= 340;
            case "block_raycast" -> protocolVersion >= 340;
            case "container_tracking" -> protocolVersion >= 340;
            case "advanced_los" -> protocolVersion >= 573;
            default -> true;
        };
    }
    
    /**
     * Get packet-level detection capability
     */
    public boolean canDetectViaPackets() {
        return available; // Can only detect via packets if ViaVersion is installed
    }
    
    /**
     * Detection parameters holder
     */
    public static class DetectionParams {
        public double detectionRadius = 64.0;
        public int entityCheckInterval = 1;
        public double miningEfficiencyThreshold = 0.75;
        public double chestAccessThreshold = 0.6;
        public int playerTrackingThreshold = 80;
    }
}
