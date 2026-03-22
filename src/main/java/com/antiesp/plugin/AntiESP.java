package com.antiesp.plugin;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import com.antiesp.plugin.detection.*;
import com.antiesp.plugin.utils.*;
import com.antiesp.plugin.commands.AdminCommand;
import com.antiesp.plugin.entity.EntityVisibilityManager;
import com.antiesp.plugin.exploit.FreeCamBlocker;
import com.antiesp.plugin.exploit.CameraPacketDetector;

/**
 * AntiESP - Comprehensive Anti-ESP/Anti-Cheat Plugin for Paper 1.21.11
 * Detects and mitigates: Entity ESP, Block ESP, Chest ESP, Player ESP
 * ViaVersion compatible
 */
public class AntiESP extends JavaPlugin {
    
    private static AntiESP instance;
    private EntityESPDetector entityDetector;
    private BlockESPDetector blockDetector;
    private ChestESPDetector chestDetector;
    private PlayerESPDetector playerDetector;
    private AdvancedPacketDetector advancedDetector;
    private CombatDetector combatDetector;
    private EntityVisibilityManager entityVisibilityManager;
    private FreeCamBlocker freeCamBlocker;
    private CameraPacketDetector cameraPacketDetector;
    private ViaVersionHelper viaHelper;
    private ConfigManager configManager;
    private BukkitTask detectionTask;
    private BukkitTask maintenanceTask;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // Load configuration
        saveDefaultConfig();
        configManager = new ConfigManager(this);
        
        // Initialize ViaVersion support
        viaHelper = new ViaVersionHelper();
        
        // Initialize detectors with optimized parameters
        entityDetector = new EntityESPDetector(this);
        blockDetector = new BlockESPDetector(this);
        chestDetector = new ChestESPDetector(this);
        playerDetector = new PlayerESPDetector(this);
        advancedDetector = new AdvancedPacketDetector(this);
        combatDetector = new CombatDetector(this);
        entityVisibilityManager = new EntityVisibilityManager(this);
        freeCamBlocker = new FreeCamBlocker(this);
        cameraPacketDetector = new CameraPacketDetector(this);
        
        // Register event listeners
        getServer().getPluginManager().registerEvents(new EntityESPListener(this, entityDetector), this);
        getServer().getPluginManager().registerEvents(new BlockESPListener(this, blockDetector), this);
        getServer().getPluginManager().registerEvents(new PlayerESPListener(this, playerDetector), this);
        getServer().getPluginManager().registerEvents(advancedDetector, this);
        getServer().getPluginManager().registerEvents(combatDetector, this);
        getServer().getPluginManager().registerEvents(freeCamBlocker, this);
        getServer().getPluginManager().registerEvents(cameraPacketDetector, this);
        
        // Register commands
        getCommand("antiesp").setExecutor(new AdminCommand(this));
        
        // Start detection tasks
        startDetectionTasks();
        
        // Start entity visibility manager
        entityVisibilityManager.start();
        
        getLogger().info("═══════════════════════════════════════");
        getLogger().info("  AntiESP loaded successfully!");
        getLogger().info("  Version: 1.2.0");
        getLogger().info("  Detectors: Entity | Block | Chest | Player | Advanced | Combat | Camera");
        getLogger().info("  Entity Hiding: ENABLED (99.9% unbypassable)");
        getLogger().info("  Free-Cam Blocking: ENABLED");
        getLogger().info("  ViaVersion: " + (viaHelper.isAvailable() ? "Enabled" : "Disabled"));
        getLogger().info("═══════════════════════════════════════");
    }
    
    @Override
    public void onDisable() {
        if (detectionTask != null) detectionTask.cancel();
        if (maintenanceTask != null) maintenanceTask.cancel();
        if (entityVisibilityManager != null) entityVisibilityManager.stop();
        getLogger().info("AntiESP disabled.");
    }
    
    private void startDetectionTasks() {
        // Main detection task - runs every tick for active players
        detectionTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.hasPermission("antiesp.bypass")) {
                    // Run all detectors asynchronously to minimize lag
                    Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                        entityDetector.checkPlayer(player);
                        blockDetector.checkPlayer(player);
                        playerDetector.checkPlayer(player);
                    });
                }
            }
        }, 1L, 1L); // Run every tick
        
        // Chest ESP detector - runs less frequently but with high precision
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.hasPermission("antiesp.bypass")) {
                    Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                        chestDetector.checkPlayer(player);
                    });
                }
            }
        }, 20L, 20L); // Run every second
        
        // Maintenance task - cleanup and statistics
        maintenanceTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            entityDetector.cleanup();
            blockDetector.cleanup();
            chestDetector.cleanup();
            playerDetector.cleanup();
            advancedDetector.cleanup();
            combatDetector.cleanup();
            freeCamBlocker.cleanup();
            cameraPacketDetector.cleanup();
        }, 600L, 600L); // Run every 30 seconds
    }
    
    public static AntiESP getInstance() {
        return instance;
    }
    
    public EntityESPDetector getEntityDetector() {
        return entityDetector;
    }
    
    public BlockESPDetector getBlockDetector() {
        return blockDetector;
    }
    
    public ChestESPDetector getChestDetector() {
        return chestDetector;
    }
    
    public PlayerESPDetector getPlayerDetector() {
        return playerDetector;
    }
    
    public AdvancedPacketDetector getAdvancedDetector() {
        return advancedDetector;
    }
    
    public CombatDetector getCombatDetector() {
        return combatDetector;
    }
    
    public EntityVisibilityManager getEntityVisibilityManager() {
        return entityVisibilityManager;
    }
    
    public FreeCamBlocker getFreeCamBlocker() {
        return freeCamBlocker;
    }
    
    public CameraPacketDetector getCameraPacketDetector() {
        return cameraPacketDetector;
    }
    
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public ViaVersionHelper getViaHelper() {
        return viaHelper;
    }
}
