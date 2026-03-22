package com.antiesp.plugin.detection;

import io.papermc.paper.event.player.PlayerLookEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;
import com.antiesp.plugin.utils.ViolationTracker;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Advanced Packet-Level Detection for Paper 1.21.11
 * Monitors player head rotations, movement patterns, and look events
 * for signs of ESP usage and automated aiming
 */
public class AdvancedPacketDetector implements Listener {
    
    private final JavaPlugin plugin;
    private final ViolationTracker tracker;
    private final Map<UUID, HeadRotationData> playerHeadData;
    private final Map<UUID, MovementData> playerMovementData;
    
    public AdvancedPacketDetector(JavaPlugin plugin) {
        this.plugin = plugin;
        this.tracker = new ViolationTracker("advanced_packet");
        this.playerHeadData = new ConcurrentHashMap<>();
        this.playerMovementData = new ConcurrentHashMap<>();
    }
    
    /**
     * Monitors head rotation patterns for unnaturally perfect aiming
     * ESP users have instantly perfect rotations to enemies
     * Humans have gradual, sometimes imperfect rotations
     */
    @EventHandler
    public void onPlayerLook(PlayerLookEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        if (!playerHeadData.containsKey(uuid)) {
            playerHeadData.put(uuid, new HeadRotationData(player));
            return;
        }
        
        HeadRotationData data = playerHeadData.get(uuid);
        float newPitch = event.getNewPitch();
        float newYaw = event.getNewYaw();
        
        // Detect 1: Instant perfect rotations
        checkInstantAiming(player, data, newPitch, newYaw);
        
        // Detect 2: Unnatural rotation smoothness
        checkRotationSmoothness(data, newPitch, newYaw);
        
        // Detect 3: Rotations matching enemy positions
        checkRotationTracking(player, data, newPitch, newYaw);
        
        data.updateRotation(newPitch, newYaw);
    }
    
    /**
     * Detects movement patterns that indicate ESP usage
     * ESP users move optimally toward enemies/loot
     * Humans explore more randomly
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        if (!playerMovementData.containsKey(uuid)) {
            playerMovementData.put(uuid, new MovementData(player));
            return;
        }
        
        MovementData data = playerMovementData.get(uuid);
        
        // Only check horizontal movement (ignore vertical for now)
        double dx = event.getTo().getX() - event.getFrom().getX();
        double dz = event.getTo().getZ() - event.getFrom().getZ();
        
        // Detect 1: Optimal pathfinding to enemies
        checkOptimalMovement(player, data, dx, dz);
        
        // Detect 2: Movement prediction (moving before seeing enemy)
        checkMovementPrediction(player, data, event.getTo());
        
        // Detect 3: Unnatural strafing patterns
        checkStrafingPattern(data, dx, dz);
        
        data.addMovement(dx, dz, System.currentTimeMillis());
    }
    
    /**
     * Check for instantly perfect aim rotations
     * True humans need ~100-500ms to aim at enemies
     * Bots have <50ms reaction time
     */
    private void checkInstantAiming(Player player, HeadRotationData data, 
                                    float newPitch, float newYaw) {
        // Check if player just rotated to face an enemy instantly
        Player nearestEnemy = getNearestEnemy(player);
        if (nearestEnemy == null) return;
        
        float[] angleToEnemy = calculateAngleToPlayer(player, nearestEnemy);
        float pitchDiff = Math.abs(newPitch - angleToEnemy[0]);
        float yawDiff = Math.abs(normalizeAngle(newYaw - angleToEnemy[1]));
        
        // Perfect aim (within 2 degrees) is suspicious if done frequently
        if (pitchDiff < 2 && yawDiff < 2) {
            data.perfectAimTicks++;
            
            if (data.perfectAimTicks > 10) {
                reportViolation(player, "instant_aim", 
                    String.format("Instant perfect aim to enemy (pitch: %.1f°, yaw: %.1f°)",
                        pitchDiff, yawDiff));
                data.resetPerfectAim();
            }
        } else {
            data.perfectAimTicks = Math.max(0, data.perfectAimTicks - 1);
        }
    }
    
    /**
     * Check for unnaturally smooth rotations
     * ESP aimbots rotate with perfect, constant angular velocity
     * Humans have variable, sometimes jerky rotations
     */
    private void checkRotationSmoothness(HeadRotationData data, float newPitch, float newYaw) {
        if (data.lastPitch == null || data.lastYaw == null) return;
        
        float pitchDelta = Math.abs(newPitch - data.lastPitch);
        float yawDelta = Math.abs(normalizeAngle(newYaw - data.lastYaw));
        
        // Check consistency - same delta every tick is suspicious
        if (data.lastPitchDelta != null && data.lastYawDelta != null) {
            float pitchConsistency = Math.abs(pitchDelta - data.lastPitchDelta);
            float yawConsistency = Math.abs(yawDelta - data.lastYawDelta);
            
            // Perfect consistency (bots have <0.1° variation)
            if (pitchConsistency < 0.1 && yawConsistency < 0.1) {
                data.smoothRotationTicks++;
                
                if (data.smoothRotationTicks > 15) {
                    reportViolation(player, "smooth_rotation",
                        "Unnaturally perfect rotation smoothness detected");
                    data.resetSmoothRotation();
                }
            } else {
                data.smoothRotationTicks = Math.max(0, data.smoothRotationTicks - 1);
            }
        }
        
        data.lastPitchDelta = pitchDelta;
        data.lastYawDelta = yawDelta;
    }
    
    /**
     * Check if rotations are matching enemy positions too consistently
     * If player always rotates to where enemies are, they know their positions
     */
    private void checkRotationTracking(Player player, HeadRotationData data, 
                                       float newPitch, float newYaw) {
        List<Player> nearbyPlayers = getNearbyPlayers(player);
        int matchingRotations = 0;
        
        for (Player other : nearbyPlayers) {
            if (other.equals(player) || other.isDead()) continue;
            
            float[] angleToOther = calculateAngleToPlayer(player, other);
            float pitchDiff = Math.abs(newPitch - angleToOther[0]);
            float yawDiff = Math.abs(normalizeAngle(newYaw - angleToOther[1]));
            
            // Within 5 degrees of enemy = matching
            if (pitchDiff < 5 && yawDiff < 5) {
                matchingRotations++;
                data.addTrackedEnemy(other.getUniqueId());
            }
        }
        
        // If player rotates to match many enemies continuously
        if (matchingRotations > 2) {
            data.rotationTrackingViolations++;
            
            if (data.rotationTrackingViolations > 8) {
                reportViolation(player, "rotation_tracking",
                    String.format("Rotations matching %d enemy positions", matchingRotations));
                data.resetRotationTracking();
            }
        } else {
            data.rotationTrackingViolations = Math.max(0, data.rotationTrackingViolations - 1);
        }
    }
    
    /**
     * Detects optimal movement toward enemies or resources
     * Humans explore, ESP users move directly to objectives
     */
    private void checkOptimalMovement(Player player, MovementData data, double dx, double dz) {
        if (Math.abs(dx) < 0.01 && Math.abs(dz) < 0.01) return; // No movement
        
        // Check if moving toward nearest enemy
        Player nearestEnemy = getNearestEnemy(player);
        if (nearestEnemy != null) {
            double toEnemyX = nearestEnemy.getLocation().getX() - player.getLocation().getX();
            double toEnemyZ = nearestEnemy.getLocation().getZ() - player.getLocation().getZ();
            
            // Normalize vectors
            double moveLength = Math.sqrt(dx * dx + dz * dz);
            double moveX = dx / moveLength;
            double moveZ = dz / moveLength;
            
            double enemyLength = Math.sqrt(toEnemyX * toEnemyX + toEnemyZ * toEnemyZ);
            double enemyX = toEnemyX / enemyLength;
            double enemyZ = toEnemyZ / enemyLength;
            
            // Calculate angle between movement and enemy direction
            double dotProduct = (moveX * enemyX) + (moveZ * enemyZ);
            
            // Angle close to 0 = moving toward enemy
            if (dotProduct > 0.9) {
                data.optimalMovementTicks++;
                
                if (data.optimalMovementTicks > 20) {
                    reportViolation(player, "optimal_movement",
                        "Perfect pathfinding toward enemy detected");
                    data.resetOptimalMovement();
                }
            } else {
                data.optimalMovementTicks = Math.max(0, data.optimalMovementTicks - 1);
            }
        }
    }
    
    /**
     * Detect movement prediction - moving before seeing enemy
     * ESP users know where enemies are before seeing them
     */
    private void checkMovementPrediction(Player player, MovementData data, org.bukkit.Location newLoc) {
        // Find if any nearby players are hidden from view
        List<Player> hiddenEnemies = new ArrayList<>();
        
        for (Player other : getNearbyPlayers(player)) {
            if (other.equals(player) || other.isDead()) continue;
            if (!player.hasLineOfSight(other)) {
                hiddenEnemies.add(other);
            }
        }
        
        // If moving toward hidden enemies, that's prediction
        for (Player hidden : hiddenEnemies) {
            double toHiddenX = hidden.getLocation().getX() - newLoc.getX();
            double toHiddenZ = hidden.getLocation().getZ() - newLoc.getZ();
            
            if (Math.abs(toHiddenX) < 3 && Math.abs(toHiddenZ) < 3) {
                data.predictionViolations++;
                
                if (data.predictionViolations > 5) {
                    reportViolation(player, "movement_prediction",
                        "Moving toward hidden enemy before seeing them");
                    data.resetPrediction();
                }
            }
        }
    }
    
    /**
     * Detect unnatural strafing patterns
     * ESP users strafe with perfect timing and patterns
     * Humans have variable, imperfect strafing
     */
    private void checkStrafingPattern(MovementData data, double dx, double dz) {
        // Add to strafe history
        data.addStrafeMovement(dx, dz);
        
        // Check if strafe pattern is too perfect
        if (data.getStrafeCount() > 20) {
            double consistency = data.calculateStrafeConsistency();
            
            // Perfect strafing (bots have 0.95+ consistency)
            if (consistency > 0.95) {
                data.perfectStrafeTicks++;
                
                if (data.perfectStrafeTicks > 10) {
                    reportViolation(player, "perfect_strafe",
                        String.format("Perfect strafe pattern (consistency: %.2f)", consistency));
                    data.resetStrafe();
                }
            } else {
                data.perfectStrafeTicks = Math.max(0, data.perfectStrafeTicks - 1);
            }
        }
    }
    
    /**
     * Get nearest enemy player
     */
    private Player getNearestEnemy(Player player) {
        Player nearest = null;
        double closestDistance = Double.MAX_VALUE;
        
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player) || other.isDead() || 
                other.getWorld() != player.getWorld()) continue;
            
            double distance = player.getLocation().distance(other.getLocation());
            if (distance < closestDistance) {
                closestDistance = distance;
                nearest = other;
            }
        }
        
        return nearest;
    }
    
    /**
     * Get nearby players
     */
    private List<Player> getNearbyPlayers(Player player) {
        List<Player> nearby = new ArrayList<>();
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getWorld() == player.getWorld()) {
                if (player.getLocation().distance(other.getLocation()) <= 64) {
                    nearby.add(other);
                }
            }
        }
        return nearby;
    }
    
    /**
     * Calculate angle from player to target
     * Returns [pitch, yaw]
     */
    private float[] calculateAngleToPlayer(Player viewer, Player target) {
        org.bukkit.Location eye = viewer.getEyeLocation();
        org.bukkit.Location targetEye = target.getEyeLocation();
        
        double dx = targetEye.getX() - eye.getX();
        double dy = targetEye.getY() - eye.getY();
        double dz = targetEye.getZ() - eye.getZ();
        
        double distance = Math.sqrt(dx * dx + dz * dz);
        
        float pitch = (float) -Math.toDegrees(Math.atan(dy / distance));
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        
        return new float[]{pitch, yaw};
    }
    
    /**
     * Normalize angle to -180 to 180 range
     */
    private float normalizeAngle(float angle) {
        while (angle > 180) angle -= 360;
        while (angle < -180) angle += 360;
        return angle;
    }
    
    /**
     * Report violation
     */
    private void reportViolation(Player player, String type, String details) {
        tracker.addViolation(player.getUniqueId());
        int violations = tracker.getViolations(player.getUniqueId());
        
        if (violations >= 1 && violations < 3) {
            player.kickPlayer("§cAimbot/Movement hack detected. Violation #" + violations);
        } else if (violations >= 3) {
            Bukkit.broadcastMessage("§c[AntiESP] " + player.getName() + " detected with aiming hacks");
            player.kickPlayer("§cAimbot confirmed. Permanent ban.");
            plugin.getLogger().warning("Advanced packet violation: " + player.getName() + " - " + details);
        }
    }
    
    public void cleanup() {
        playerHeadData.entrySet().removeIf(entry -> Bukkit.getPlayer(entry.getKey()) == null);
        playerMovementData.entrySet().removeIf(entry -> Bukkit.getPlayer(entry.getKey()) == null);
    }
    
    /**
     * Head rotation tracking data
     */
    private static class HeadRotationData {
        private final Player player;
        Float lastPitch;
        Float lastYaw;
        Float lastPitchDelta;
        Float lastYawDelta;
        int perfectAimTicks = 0;
        int smoothRotationTicks = 0;
        int rotationTrackingViolations = 0;
        Set<UUID> trackedEnemies = ConcurrentHashMap.newKeySet();
        
        HeadRotationData(Player player) {
            this.player = player;
        }
        
        void updateRotation(float pitch, float yaw) {
            this.lastPitch = pitch;
            this.lastYaw = yaw;
        }
        
        void addTrackedEnemy(UUID uuid) {
            trackedEnemies.add(uuid);
        }
        
        void resetPerfectAim() { perfectAimTicks = 0; }
        void resetSmoothRotation() { smoothRotationTicks = 0; }
        void resetRotationTracking() { rotationTrackingViolations = 0; trackedEnemies.clear(); }
    }
    
    /**
     * Movement pattern tracking data
     */
    private static class MovementData {
        private final Player player;
        int optimalMovementTicks = 0;
        int predictionViolations = 0;
        int perfectStrafeTicks = 0;
        List<double[]> strafeHistory = new ArrayList<>();
        
        MovementData(Player player) {
            this.player = player;
        }
        
        void addMovement(double dx, double dz, long timestamp) {
            strafeHistory.add(new double[]{dx, dz, timestamp});
            if (strafeHistory.size() > 100) {
                strafeHistory.remove(0);
            }
        }
        
        void addStrafeMovement(double dx, double dz) {
            strafeHistory.add(new double[]{dx, dz});
            if (strafeHistory.size() > 50) strafeHistory.remove(0);
        }
        
        int getStrafeCount() {
            return strafeHistory.size();
        }
        
        double calculateStrafeConsistency() {
            if (strafeHistory.size() < 5) return 0;
            
            // Calculate variance in movement deltas
            double[] movements = new double[strafeHistory.size()];
            for (int i = 0; i < strafeHistory.size(); i++) {
                double[] move = strafeHistory.get(i);
                movements[i] = Math.sqrt(move[0] * move[0] + move[1] * move[1]);
            }
            
            double mean = Arrays.stream(movements).average().orElse(0);
            double variance = Arrays.stream(movements)
                .map(m -> Math.pow(m - mean, 2))
                .average().orElse(0);
            
            double stdDev = Math.sqrt(variance);
            
            // Consistency = 1 - (variance / mean)
            return 1.0 - Math.min(1.0, stdDev / (mean + 0.01));
        }
        
        void resetOptimalMovement() { optimalMovementTicks = 0; }
        void resetPrediction() { predictionViolations = 0; }
        void resetStrafe() { perfectStrafeTicks = 0; }
    }
}
