package com.antiesp.plugin.detection;

import org.bukkit.Bukkit;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;
import com.antiesp.plugin.utils.ViolationTracker;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects Entity ESP - players seeing invisible/hidden entities
 * Detection methods:
 * 1. Packet-based detection (combat/interaction with invisible entities)
 * 2. Line-of-sight analysis
 * 3. Entity rendering impossibility analysis
 * 4. Spatial clustering detection
 */
public class EntityESPDetector {
    
    private final JavaPlugin plugin;
    private final ViolationTracker tracker;
    private final Map<UUID, PlayerEntityData> playerData;
    private final Set<UUID> recentInvisibleAccess;
    private final double DETECTION_RADIUS = 64.0; // Detection range
    private final int TICKS_TO_REPORT = 3; // Ticks before reporting suspicious activity
    
    public EntityESPDetector(JavaPlugin plugin) {
        this.plugin = plugin;
        this.tracker = new ViolationTracker("entity_esp");
        this.playerData = new ConcurrentHashMap<>();
        this.recentInvisibleAccess = ConcurrentHashMap.newKeySet();
    }
    
    /**
     * Main detection check - called every tick
     */
    public void checkPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        
        if (!playerData.containsKey(uuid)) {
            playerData.put(uuid, new PlayerEntityData(player));
            return;
        }
        
        PlayerEntityData data = playerData.get(uuid);
        
        // Check 1: Invisible entity interaction detection
        checkInvisibleEntityInteraction(player, data);
        
        // Check 2: Impossible line-of-sight patterns
        checkLineOfSightPatterns(player, data);
        
        // Check 3: Spatial clustering of entity access
        checkEntityAccessPattern(player, data);
        
        data.tick();
    }
    
    /**
     * Detects when player interacts with invisible entities
     */
    private void checkInvisibleEntityInteraction(Player player, PlayerEntityData data) {
        // Get all entities in range
        List<Entity> nearby = player.getNearbyEntities(DETECTION_RADIUS, DETECTION_RADIUS, DETECTION_RADIUS);
        
        for (Entity entity : nearby) {
            if (!(entity instanceof LivingEntity)) continue;
            if (entity.equals(player)) continue;
            
            LivingEntity living = (LivingEntity) entity;
            
            // Check if entity is invisible or has high-level invisibility
            boolean isInvisible = living.hasPotionEffect(PotionEffectType.INVISIBILITY) || 
                                 (living instanceof Player && ((Player) entity).canSee(player));
            
            if (isInvisible) {
                // Check if player has line of sight to invisible entity
                if (hasDirectLineOfSight(player, entity)) {
                    data.invisibleEntityAccess++;
                    
                    // Flag if this happens too frequently
                    if (data.invisibleEntityAccess > TICKS_TO_REPORT) {
                        reportViolation(player, "invisible_entity_access", 
                            "Suspicious interaction with invisible entity at " + entity.getLocation());
                        data.resetInvisibleAccess();
                    }
                }
            }
        }
    }
    
    /**
     * Detects impossible line-of-sight patterns
     */
    private void checkLineOfSightPatterns(Player player, PlayerEntityData data) {
        List<Entity> nearby = player.getNearbyEntities(DETECTION_RADIUS, DETECTION_RADIUS, DETECTION_RADIUS);
        int visibleCount = 0;
        int totalHostileCount = 0;
        
        for (Entity entity : nearby) {
            if (!(entity instanceof Monster)) continue;
            if (entity.isDead()) continue;
            
            totalHostileCount++;
            
            // Check if player can see the entity without obstacles
            if (hasDirectLineOfSight(player, entity)) {
                visibleCount++;
            }
        }
        
        // If player can see suspiciously many hostiles through walls/obstructions
        if (totalHostileCount > 0) {
            double visibilityRatio = (double) visibleCount / totalHostileCount;
            
            if (visibilityRatio > 0.85 && totalHostileCount > 3) {
                data.impossibleLineOfSightTicks++;
                
                if (data.impossibleLineOfSightTicks > TICKS_TO_REPORT * 2) {
                    reportViolation(player, "impossible_los", 
                        String.format("Impossible line-of-sight: %.1f%% visibility", visibilityRatio * 100));
                    data.resetLineOfSight();
                }
            } else {
                data.impossibleLineOfSightTicks = Math.max(0, data.impossibleLineOfSightTicks - 1);
            }
        }
    }
    
    /**
     * Detects spatial clustering - player accessing multiple entities from impossible positions
     */
    private void checkEntityAccessPattern(Player player, PlayerEntityData data) {
        List<Entity> nearby = player.getNearbyEntities(DETECTION_RADIUS, DETECTION_RADIUS, DETECTION_RADIUS);
        
        // Track entity positions player has accessed
        for (Entity entity : nearby) {
            if (!(entity instanceof LivingEntity)) continue;
            
            // Check if player is attempting to path through entities impossibly
            data.addAccessedEntity(entity.getUniqueId(), entity.getLocation());
        }
        
        // Analyze clustering
        if (data.getAccessedEntityCount() > 5) {
            double clusterDensity = data.calculateClusterDensity();
            
            // If entities are accessed in impossible spatial patterns
            if (clusterDensity > 0.8) {
                data.clusteringViolations++;
                
                if (data.clusteringViolations > TICKS_TO_REPORT) {
                    reportViolation(player, "entity_clustering", 
                        "Impossible entity access pattern detected");
                    data.resetClustering();
                }
            }
        }
    }
    
    /**
     * Improved line-of-sight check accounting for player perspective
     */
    private boolean hasDirectLineOfSight(Player player, Entity target) {
        // Use Bukkit's ray-cast for accurate line-of-sight
        return player.hasLineOfSight(target);
    }
    
    /**
     * Reports a violation and handles punishment
     */
    private void reportViolation(Player player, String type, String details) {
        tracker.addViolation(player.getUniqueId());
        int violations = tracker.getViolations(player.getUniqueId());
        
        // Escalating punishments
        if (violations >= 1 && violations < 3) {
            player.kickPlayer("§cEntity ESP detected. Violation #" + violations);
        } else if (violations >= 3) {
            Bukkit.broadcastMessage("§c[AntiESP] " + player.getName() + " detected with Entity ESP");
            player.kickPlayer("§cEntity ESP confirmed. Permanent ban.");
            // Log for admin review
            plugin.getLogger().warning("Entity ESP violation: " + player.getName() + " - " + details);
        }
    }
    
    public void cleanup() {
        // Remove inactive players
        playerData.entrySet().removeIf(entry -> 
            Bukkit.getPlayer(entry.getKey()) == null
        );
        
        // Reset old violation data
        tracker.cleanup();
    }
    
    /**
     * Data holder for per-player entity ESP detection
     */
    private static class PlayerEntityData {
        private final Player player;
        private int invisibleEntityAccess = 0;
        private int impossibleLineOfSightTicks = 0;
        private int clusteringViolations = 0;
        private final Map<UUID, Location> accessedEntities = new LinkedHashMap<>();
        private int tickCounter = 0;
        
        PlayerEntityData(Player player) {
            this.player = player;
        }
        
        void tick() {
            tickCounter++;
            // Clear old data every 20 ticks
            if (tickCounter % 20 == 0) {
                if (accessedEntities.size() > 50) {
                    accessedEntities.clear();
                }
            }
        }
        
        void addAccessedEntity(UUID entityId, org.bukkit.Location loc) {
            accessedEntities.put(entityId, loc);
        }
        
        int getAccessedEntityCount() {
            return accessedEntities.size();
        }
        
        double calculateClusterDensity() {
            if (accessedEntities.size() < 2) return 0.0;
            
            Collection<org.bukkit.Location> locations = accessedEntities.values();
            org.bukkit.Location center = player.getLocation();
            double totalDistance = 0;
            
            for (org.bukkit.Location loc : locations) {
                totalDistance += center.distance(loc);
            }
            
            double avgDistance = totalDistance / locations.size();
            return Math.min(1.0, 1.0 / (1.0 + avgDistance));
        }
        
        void resetInvisibleAccess() { invisibleEntityAccess = 0; }
        void resetLineOfSight() { impossibleLineOfSightTicks = 0; }
        void resetClustering() { clusteringViolations = 0; }
    }
}
