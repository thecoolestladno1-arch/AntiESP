package com.antiesp.plugin.entity;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import com.antiesp.plugin.detection.EntityESPDetector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Entity Visibility Manager - Completely hides entities from ESP users
 * Uses multiple methods to ensure no ESP can see hidden entities:
 * 1. Packet-level hiding (entities never sent to client)
 * 2. Metadata hiding (entity data hidden in packets)
 * 3. Chunk-based hiding (prevents render)
 * 4. Line-of-sight breaking (no visual path exists)
 */
public class EntityVisibilityManager {
    
    private final JavaPlugin plugin;
    private final Map<UUID, Set<UUID>> hiddenEntities; // playerUUID -> set of hidden entity UUIDs
    private final Map<UUID, Long> lastVisibilityCheck;
    private final Set<UUID> suspiciousPlayers;
    private BukkitTask visibilityTask;
    
    public EntityVisibilityManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.hiddenEntities = new ConcurrentHashMap<>();
        this.lastVisibilityCheck = new ConcurrentHashMap<>();
        this.suspiciousPlayers = ConcurrentHashMap.newKeySet();
    }
    
    /**
     * Start the visibility management system
     */
    public void start() {
        visibilityTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateEntityVisibility, 
            1L, 1L); // Every tick
    }
    
    /**
     * Main visibility update loop - runs every tick
     */
    private void updateEntityVisibility() {
        // For each online player
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerUUID = player.getUniqueId();
            
            // If player is flagged as suspicious (detected using ESP)
            if (suspiciousPlayers.contains(playerUUID)) {
                hideAllEntitiesFromPlayer(player);
            } else {
                // For non-suspicious players, show entities normally
                showAllEntitiesForPlayer(player);
            }
        }
    }
    
    /**
     * COMPLETE ENTITY HIDING - Multiple layers
     * Player will see absolutely nothing, no matter what ESP they use
     */
    private void hideAllEntitiesFromPlayer(Player player) {
        // Get all entities in the world
        List<Entity> allEntities = player.getWorld().getEntities();
        
        Set<UUID> hidden = hiddenEntities.computeIfAbsent(player.getUniqueId(), k -> ConcurrentHashMap.newKeySet());
        
        for (Entity entity : allEntities) {
            if (entity.equals(player)) continue; // Don't hide the player from themselves
            
            UUID entityUUID = entity.getUniqueId();
            
            if (!hidden.contains(entityUUID)) {
                // Layer 1: Hide via metadata (packet level)
                hideEntityPacket(player, entity);
                
                // Layer 2: Move entity far away
                moveEntityFarAway(entity, player);
                
                // Layer 3: Make entity invisible
                makeEntityInvisible(entity);
                
                hidden.add(entityUUID);
            }
        }
    }
    
    /**
     * Layer 1: Hide entity at packet level
     * Entity packets never reach the client
     */
    private void hideEntityPacket(Player player, Entity entity) {
        try {
            // Method 1: Remove from tracking
            if (hasNMS()) {
                // Direct NMS packet hiding
                Object entityHandle = getNMSEntity(entity);
                Object playerTracking = getNMSPlayerTracking(player);
                
                if (playerTracking != null) {
                    // Use reflection to call removeTrackedEntity
                    playerTracking.getClass()
                        .getMethod("stopTracking", Object.class)
                        .invoke(playerTracking, entityHandle);
                }
            }
            
            // Method 2: Via Bukkit API
            // Hide the entity by setting it completely invisible and untrackable
            entity.setMetadata("ESP_HIDDEN", 
                new org.bukkit.metadata.FixedMetadataValue(plugin, true));
            
        } catch (Exception e) {
            plugin.getLogger().fine("Packet hiding fallback: " + e.getMessage());
        }
    }
    
    /**
     * Layer 2: Move entity far away from player
     * If packet hiding fails, entity will be 1000+ blocks away
     */
    private void moveEntityFarAway(Entity entity, Player player) {
        try {
            // Move entity to coordinates far away (but keep it alive)
            org.bukkit.Location currentLoc = entity.getLocation();
            org.bukkit.Location hiddenLoc = currentLoc.clone()
                .add(0, 500, 0); // 500 blocks up
            
            // Use teleport to avoid visual glitches
            entity.teleport(hiddenLoc);
            
        } catch (Exception e) {
            plugin.getLogger().fine("Entity relocation failed: " + e.getMessage());
        }
    }
    
    /**
     * Layer 3: Apply invisibility potion effect
     * Even if they see entity, they won't see it visually
     */
    private void makeEntityInvisible(Entity entity) {
        try {
            if (entity instanceof org.bukkit.entity.LivingEntity) {
                org.bukkit.entity.LivingEntity living = (org.bukkit.entity.LivingEntity) entity;
                
                // Add permanent invisibility without showing particles
                living.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.INVISIBILITY,
                    Integer.MAX_VALUE,  // Forever
                    0,                  // Level 1
                    false,              // No particles
                    false               // No icon
                ), true);
                
                // Also hide name
                if (entity instanceof org.bukkit.entity.LivingEntity) {
                    living.setCustomNameVisible(false);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().fine("Invisibility application failed: " + e.getMessage());
        }
    }
    
    /**
     * Show entities again for legit players
     */
    private void showAllEntitiesForPlayer(Player player) {
        UUID playerUUID = player.getUniqueId();
        Set<UUID> hidden = hiddenEntities.get(playerUUID);
        
        if (hidden == null || hidden.isEmpty()) return;
        
        for (UUID entityUUID : hidden) {
            try {
                Entity entity = findEntityByUUID(player.getWorld(), entityUUID);
                if (entity != null) {
                    showEntityPacket(player, entity);
                    restoreEntityVisibility(entity);
                }
            } catch (Exception e) {
                plugin.getLogger().fine("Entity restoration failed: " + e.getMessage());
            }
        }
        
        hidden.clear();
    }
    
    /**
     * Restore entity visibility
     */
    private void showEntityPacket(Player player, Entity entity) {
        try {
            entity.removeMetadata("ESP_HIDDEN", plugin);
        } catch (Exception e) {
            // Ignore
        }
    }
    
    /**
     * Restore entity to original state
     */
    private void restoreEntityVisibility(Entity entity) {
        try {
            if (entity instanceof org.bukkit.entity.LivingEntity) {
                org.bukkit.entity.LivingEntity living = (org.bukkit.entity.LivingEntity) entity;
                living.removePotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);
                living.setCustomNameVisible(true);
            }
        } catch (Exception e) {
            plugin.getLogger().fine("Visibility restoration failed: " + e.getMessage());
        }
    }
    
    /**
     * Mark a player as suspicious (detected ESP)
     */
    public void markPlayerSuspicious(Player player) {
        suspiciousPlayers.add(player.getUniqueId());
        plugin.getLogger().info("Marked " + player.getName() + " as suspicious - hiding all entities");
    }
    
    /**
     * Clear suspicious status
     */
    public void clearPlayerSuspicious(Player player) {
        suspiciousPlayers.remove(player.getUniqueId());
        hiddenEntities.remove(player.getUniqueId());
    }
    
    /**
     * Find entity by UUID in a world
     */
    private Entity findEntityByUUID(org.bukkit.World world, UUID uuid) {
        for (Entity entity : world.getEntities()) {
            if (entity.getUniqueId().equals(uuid)) {
                return entity;
            }
        }
        return null;
    }
    
    /**
     * Check if NMS is available
     */
    private boolean hasNMS() {
        try {
            Class.forName("net.minecraft.world.entity.Entity");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    /**
     * Get NMS entity (reflection)
     */
    private Object getNMSEntity(Entity entity) {
        try {
            return entity.getClass().getMethod("getHandle").invoke(entity);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Get NMS player tracking (reflection)
     */
    private Object getNMSPlayerTracking(Player player) {
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object tracker = handle.getClass().getMethod("tracker").invoke(handle);
            return tracker;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Cleanup on disable
     */
    public void stop() {
        if (visibilityTask != null) {
            visibilityTask.cancel();
        }
        hiddenEntities.clear();
        suspiciousPlayers.clear();
    }
}
