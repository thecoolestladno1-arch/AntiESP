package com.antiesp.plugin.detection;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import com.antiesp.plugin.utils.ViolationTracker;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Player ESP Detector - detects impossible player discovery/tracking
 * Detection methods:
 * 1. Player visibility through walls
 * 2. Perfect player tracking (always knows where players are)
 * 3. Impossible sight lines to hidden players
 * 4. Wallhack combined with ESP detection
 */
public class PlayerESPDetector {
    
    private final JavaPlugin plugin;
    private final ViolationTracker tracker;
    private final Map<UUID, PlayerTrackerData> playerData;
    private final double DETECTION_RADIUS = 100.0;
    private final int SIGHT_CHECK_INTERVAL = 10; // ticks
    
    public PlayerESPDetector(JavaPlugin plugin) {
        this.plugin = plugin;
        this.tracker = new ViolationTracker("player_esp");
        this.playerData = new ConcurrentHashMap<>();
    }
    
    /**
     * Main detection check - called every tick
     */
    public void checkPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        
        if (!playerData.containsKey(uuid)) {
            playerData.put(uuid, new PlayerTrackerData(player));
            return;
        }
        
        PlayerTrackerData data = playerData.get(uuid);
        
        // Check every SIGHT_CHECK_INTERVAL ticks to save performance
        if (data.tickCounter % SIGHT_CHECK_INTERVAL == 0) {
            // Check 1: Impossible line of sight to players
            checkImpossibleSightLines(player, data);
            
            // Check 2: Perfect player tracking
            checkPlayerTracking(player, data);
            
            // Check 3: Hidden player detection
            checkHiddenPlayerDetection(player, data);
        }
        
        data.tick();
    }
    
    /**
     * Detects players seeing other players through solid blocks
     */
    private void checkImpossibleSightLines(Player player, PlayerTrackerData data) {
        List<Player> nearby = getNearbyPlayers(player, DETECTION_RADIUS);
        
        int impossibleSights = 0;
        int totalPlayers = 0;
        
        for (Player other : nearby) {
            if (other.equals(player)) continue;
            if (other.isDead()) continue;
            
            totalPlayers++;
            
            // Check if player can see this player
            if (!player.hasLineOfSight(other)) {
                // Player cannot see this player due to blocks
                
                // But check if they're looking in the right direction
                if (isLookingAt(player, other, 30)) {
                    impossibleSights++;
                    data.addSuspiciousSight(other.getUniqueId());
                }
            }
        }
        
        if (totalPlayers > 0) {
            double impossibleRatio = (double) impossibleSights / totalPlayers;
            
            // If player is looking at hidden enemies too much
            if (impossibleRatio > 0.4 && totalPlayers > 2) {
                data.impossibleSightViolations++;
                
                if (data.impossibleSightViolations > 5) {
                    reportViolation(player, "player_esp_sight",
                        String.format("Looking at %.0f%% of hidden players",
                            impossibleRatio * 100));
                    data.resetImpossibleSight();
                }
            } else {
                data.impossibleSightViolations = Math.max(0, data.impossibleSightViolations - 1);
            }
        }
    }
    
    /**
     * Detects perfect player tracking (always knowing player positions)
     */
    private void checkPlayerTracking(Player player, PlayerTrackerData data) {
        List<Player> nearby = getNearbyPlayers(player, DETECTION_RADIUS);
        
        // Track if player has looked at all nearby players recently
        for (Player other : nearby) {
            if (other.equals(player)) continue;
            if (other.isDead()) continue;
            
            // Has player looked in direction of this player?
            if (isLookingAt(player, other, 20)) {
                data.addTrackedPlayer(other.getUniqueId());
            }
        }
        
        // Check tracking accuracy
        int trackedPlayers = data.getTrackedPlayerCount();
        
        // If player tracks more than 80% of nearby players constantly
        if (trackedPlayers >= Math.max(2, nearby.size() * 0.8)) {
            data.trackingViolations++;
            
            if (data.trackingViolations > 10) {
                reportViolation(player, "player_esp_tracking",
                    "Perfect player tracking behavior detected");
                data.resetTracking();
            }
        } else {
            data.trackingViolations = Math.max(0, data.trackingViolations - 1);
        }
    }
    
    /**
     * Detects players finding hidden/invisible players
     */
    private void checkHiddenPlayerDetection(Player player, PlayerTrackerData data) {
        List<Player> nearby = getNearbyPlayers(player, DETECTION_RADIUS);
        int hiddenPlayerDetections = 0;
        
        for (Player other : nearby) {
            if (other.equals(player)) continue;
            if (other.isDead()) continue;
            
            // Check if other player is hidden (invisible or far away)
            boolean isHidden = isPlayerHidden(other);
            
            if (isHidden) {
                // Check if current player is looking at hidden player
                if (isLookingAt(player, other, 15)) {
                    hiddenPlayerDetections++;
                    data.hiddenDetectionCounter++;
                }
            }
        }
        
        // Humans shouldn't detect hidden players frequently
        if (data.hiddenDetectionCounter > 5) {
            data.hiddenPlayerViolations++;
            
            if (data.hiddenPlayerViolations > 3) {
                reportViolation(player, "player_esp_hidden",
                    "Detection of hidden players detected");
                data.resetHiddenPlayer();
            }
        }
    }
    
    /**
     * Check if player is looking at a target (for head rotation ESP detection)
     */
    private boolean isLookingAt(Player viewer, Player target, double tolerance) {
        Location eyeLocation = viewer.getEyeLocation();
        Location targetEyes = target.getEyeLocation();
        
        // Get direction vectors
        org.bukkit.util.Vector viewerDir = eyeLocation.getDirection();
        org.bukkit.util.Vector toTarget = targetEyes.subtract(eyeLocation).toVector().normalize();
        
        // Calculate angle between view direction and target
        double dotProduct = viewerDir.dot(toTarget);
        double angleRadians = Math.acos(dotProduct);
        double angleDegrees = Math.toDegrees(angleRadians);
        
        return angleDegrees < tolerance;
    }
    
    /**
     * Check if a player is hidden (invisible or can't be seen)
     */
    private boolean isPlayerHidden(Player player) {
        // Check if invisible
        return player.hasPotionEffect(
            org.bukkit.potion.PotionEffectType.INVISIBILITY
        );
    }
    
    /**
     * Get nearby players within radius
     */
    private List<Player> getNearbyPlayers(Player player, double radius) {
        List<Player> nearby = new ArrayList<>();
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getWorld() == player.getWorld()) {
                if (player.getLocation().distance(other.getLocation()) <= radius) {
                    nearby.add(other);
                }
            }
        }
        return nearby;
    }
    
    /**
     * Reports a violation
     */
    private void reportViolation(Player player, String type, String details) {
        tracker.addViolation(player.getUniqueId());
        int violations = tracker.getViolations(player.getUniqueId());
        
        if (violations >= 1 && violations < 3) {
            player.kickPlayer("§cPlayer ESP detected. Violation #" + violations);
        } else if (violations >= 3) {
            Bukkit.broadcastMessage("§c[AntiESP] " + player.getName() + " detected with Player ESP");
            player.kickPlayer("§cPlayer ESP confirmed. Permanent ban.");
            plugin.getLogger().warning("Player ESP violation: " + player.getName() + " - " + details);
        }
    }
    
    public void cleanup() {
        playerData.entrySet().removeIf(entry -> 
            Bukkit.getPlayer(entry.getKey()) == null
        );
        tracker.cleanup();
    }
    
    /**
     * Per-player tracking data
     */
    private static class PlayerTrackerData {
        private final Player player;
        private final Set<UUID> suspiciousSights = ConcurrentHashMap.newKeySet();
        private final Set<UUID> trackedPlayers = ConcurrentHashMap.newKeySet();
        private int impossibleSightViolations = 0;
        private int trackingViolations = 0;
        private int hiddenPlayerViolations = 0;
        private int hiddenDetectionCounter = 0;
        private int tickCounter = 0;
        
        PlayerTrackerData(Player player) {
            this.player = player;
        }
        
        void tick() {
            tickCounter++;
            
            // Reset tracking windows periodically
            if (tickCounter % 100 == 0) {
                suspiciousSights.clear();
                trackedPlayers.clear();
                hiddenDetectionCounter = 0;
            }
        }
        
        void addSuspiciousSight(UUID playerUuid) {
            suspiciousSights.add(playerUuid);
        }
        
        void addTrackedPlayer(UUID playerUuid) {
            trackedPlayers.add(playerUuid);
        }
        
        int getTrackedPlayerCount() {
            return trackedPlayers.size();
        }
        
        void resetImpossibleSight() { impossibleSightViolations = 0; }
        void resetTracking() { trackingViolations = 0; }
        void resetHiddenPlayer() { hiddenPlayerViolations = 0; }
    }
}
