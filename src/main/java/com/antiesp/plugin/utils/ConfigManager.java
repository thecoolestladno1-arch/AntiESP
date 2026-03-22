package com.antiesp.plugin.utils;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Manages plugin configuration and detection thresholds
 */
public class ConfigManager {
    
    private final JavaPlugin plugin;
    private FileConfiguration config;
    
    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        loadDefaults();
    }
    
    /**
     * Load default configuration values
     */
    private void loadDefaults() {
        // Detection settings
        setDefault("detection.enabled", true);
        setDefault("detection.radius", 64.0);
        setDefault("detection.check-interval", 1);
        
        // Entity ESP settings
        setDefault("entity-esp.enabled", true);
        setDefault("entity-esp.invisible-threshold", 3);
        setDefault("entity-esp.los-threshold", 0.85);
        
        // Block ESP settings
        setDefault("block-esp.enabled", true);
        setDefault("block-esp.efficiency-threshold", 0.75);
        setDefault("block-esp.mining-window", 200);
        
        // Chest ESP settings
        setDefault("chest-esp.enabled", true);
        setDefault("chest-esp.speed-threshold", 15);
        setDefault("chest-esp.interaction-distance", 48.0);
        
        // Player ESP settings
        setDefault("player-esp.enabled", true);
        setDefault("player-esp.tracking-threshold", 0.8);
        setDefault("player-esp.sight-check-interval", 10);
        
        // Punishment settings
        setDefault("punishment.kick-on-violation", true);
        setDefault("punishment.first-kick", 1);
        setDefault("punishment.second-kick", 3);
        setDefault("punishment.ban", 3);
        setDefault("punishment.broadcast-on-ban", true);
        
        // Performance settings
        setDefault("performance.use-async", true);
        setDefault("performance.cleanup-interval", 600);
        setDefault("performance.max-tracked-blocks", 1000);
        
        // ViaVersion
        setDefault("viaversion.enabled", true);
        setDefault("viaversion.version-specific-thresholds", true);
        
        plugin.saveConfig();
    }
    
    /**
     * Set a config default if not already set
     */
    private void setDefault(String path, Object value) {
        if (!config.contains(path)) {
            config.set(path, value);
        }
    }
    
    /**
     * Check if a detector is enabled
     */
    public boolean isDetectorEnabled(String detector) {
        return config.getBoolean(detector.toLowerCase() + ".enabled", true);
    }
    
    /**
     * Get detection radius
     */
    public double getDetectionRadius() {
        return config.getDouble("detection.radius", 64.0);
    }
    
    /**
     * Get mining efficiency threshold
     */
    public double getMiningEfficiencyThreshold() {
        return config.getDouble("block-esp.efficiency-threshold", 0.75);
    }
    
    /**
     * Get mining check window (ticks)
     */
    public int getMiningWindow() {
        return config.getInt("block-esp.mining-window", 200);
    }
    
    /**
     * Check if punishment is enabled
     */
    public boolean isPunishmentEnabled() {
        return config.getBoolean("punishment.kick-on-violation", true);
    }
    
    /**
     * Get violation level for first kick
     */
    public int getFirstKickViolations() {
        return config.getInt("punishment.first-kick", 1);
    }
    
    /**
     * Get violation level for ban
     */
    public int getBanViolations() {
        return config.getInt("punishment.ban", 3);
    }
    
    /**
     * Check if broadcasts are enabled
     */
    public boolean shouldBroadcastBan() {
        return config.getBoolean("punishment.broadcast-on-ban", true);
    }
    
    /**
     * Check if async detection is enabled
     */
    public boolean useAsync() {
        return config.getBoolean("performance.use-async", true);
    }
    
    /**
     * Get cleanup interval (ticks)
     */
    public long getCleanupInterval() {
        return config.getLong("performance.cleanup-interval", 600);
    }
    
    /**
     * Get max tracked blocks per player
     */
    public int getMaxTrackedBlocks() {
        return config.getInt("performance.max-tracked-blocks", 1000);
    }
    
    /**
     * Check if ViaVersion support is enabled
     */
    public boolean isViaVersionEnabled() {
        return config.getBoolean("viaversion.enabled", true);
    }
    
    /**
     * Check if version-specific thresholds are enabled
     */
    public boolean useVersionSpecificThresholds() {
        return config.getBoolean("viaversion.version-specific-thresholds", true);
    }
    
    /**
     * Reload configuration
     */
    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }
}
