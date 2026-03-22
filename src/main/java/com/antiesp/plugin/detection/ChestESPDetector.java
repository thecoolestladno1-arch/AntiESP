package com.antiesp.plugin.detection;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import com.antiesp.plugin.utils.ViolationTracker;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chest ESP Detector - detects impossible container/loot discovery
 * Detection methods:
 * 1. Chest interaction before visibility
 * 2. Hidden container access (unloaded chunks)
 * 3. Impossible loot pathfinding
 * 4. Container interaction patterns (too fast, too accurate)
 */
public class ChestESPDetector {
    
    private final JavaPlugin plugin;
    private final ViolationTracker tracker;
    private final Map<UUID, PlayerChestData> playerData;
    private final Set<Material> CONTAINER_MATERIALS = Set.of(
        Material.CHEST, Material.TRAPPED_CHEST,
        Material.BARREL, Material.SHULKER_BOX,
        Material.ENDER_CHEST,
        Material.DISPENSER, Material.DROPPER,
        Material.HOPPER, Material.FURNACE,
        Material.BLAST_FURNACE, Material.SMOKER
    );
    
    private final double DETECTION_RADIUS = 56.0;
    private final int MIN_TIME_BETWEEN_CHESTS = 15; // ticks
    
    public ChestESPDetector(JavaPlugin plugin) {
        this.plugin = plugin;
        this.tracker = new ViolationTracker("chest_esp");
        this.playerData = new ConcurrentHashMap<>();
    }
    
    /**
     * Main detection check - called every second
     */
    public void checkPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        
        if (!playerData.containsKey(uuid)) {
            playerData.put(uuid, new PlayerChestData(player));
            return;
        }
        
        PlayerChestData data = playerData.get(uuid);
        
        // Check 1: Pre-access visibility analysis
        checkPreAccessVisibility(player, data);
        
        // Check 2: Hidden container breaking/opening
        checkHiddenContainerAccess(player, data);
        
        // Check 3: Container interaction patterns
        checkInteractionPattern(player, data);
        
        // Check 4: Impossible route to loot
        checkLootRouting(player, data);
        
        data.tick();
    }
    
    /**
     * Detects players accessing containers before they're visible
     */
    private void checkPreAccessVisibility(Player player, PlayerChestData data) {
        // Get all containers in range
        List<Block> containers = getNearbyContainers(player);
        
        for (Block container : containers) {
            // Check if player can see this container
            if (!player.hasLineOfSight(container)) {
                // Player interacted with invisible container
                if (data.hasRecentAccess(container.getLocation())) {
                    data.hiddenAccessViolations++;
                    
                    if (data.hiddenAccessViolations > 2) {
                        reportViolation(player, "chest_esp_hidden_access",
                            "Interaction with non-visible container");
                        data.resetHiddenAccess();
                    }
                }
            }
            
            // Check if container is beyond visible render distance
            if (player.getLocation().distance(container.getLocation()) > 48) {
                if (data.hasRecentAccess(container.getLocation())) {
                    data.beyondRenderViolations++;
                    
                    if (data.beyondRenderViolations > 1) {
                        reportViolation(player, "chest_esp_beyond_render",
                            "Access to container beyond render distance");
                        data.resetBeyondRender();
                    }
                }
            }
        }
    }
    
    /**
     * Detects breaking/opening containers in unloaded chunks
     */
    private void checkHiddenContainerAccess(Player player, PlayerChestData data) {
        for (ChestAccess access : data.getRecentAccesses()) {
            Block block = access.location.getBlock();
            
            // Check if chunk is loaded
            if (!block.getChunk().isLoaded()) {
                data.unloadedChunkViolations++;
                
                if (data.unloadedChunkViolations > 1) {
                    reportViolation(player, "chest_esp_unloaded",
                        "Container accessed in unloaded chunk");
                    data.resetUnloadedChunk();
                }
            }
            
            // Check if container is inside solid blocks
            if (!isContainerAccessible(block)) {
                data.inaccessibleViolations++;
                
                if (data.inaccessibleViolations > 1) {
                    reportViolation(player, "chest_esp_inaccessible",
                        "Access to sealed container");
                    data.resetInaccessible();
                }
            }
        }
    }
    
    /**
     * Detects unnatural container interaction patterns
     */
    private void checkInteractionPattern(Player player, PlayerChestData data) {
        if (data.getRecentAccesses().size() < 3) return;
        
        List<ChestAccess> accesses = data.getRecentAccesses();
        
        // Check 1: Interaction speed (too fast between containers)
        int tooFastCount = 0;
        for (int i = 0; i < accesses.size() - 1; i++) {
            long timeDiff = accesses.get(i + 1).timestamp - accesses.get(i).timestamp;
            if (timeDiff < MIN_TIME_BETWEEN_CHESTS * 50) { // milliseconds
                tooFastCount++;
            }
        }
        
        if (tooFastCount > accesses.size() * 0.6) {
            data.speedViolations++;
            
            if (data.speedViolations > 3) {
                reportViolation(player, "chest_esp_speed",
                    "Unnaturally fast container interactions");
                data.resetSpeed();
            }
        } else {
            data.speedViolations = Math.max(0, data.speedViolations - 1);
        }
        
        // Check 2: Visit patterns (hitting valuable containers first)
        int valuableFirst = 0;
        int sampleSize = Math.min(5, accesses.size());
        
        for (int i = 0; i < sampleSize; i++) {
            if (isValuableContainer(accesses.get(i))) {
                valuableFirst++;
            }
        }
        
        if (valuableFirst >= sampleSize - 1 && sampleSize >= 3) {
            data.patternViolations++;
            
            if (data.patternViolations > 3) {
                reportViolation(player, "chest_esp_pattern",
                    "Unnatural container visit pattern");
                data.resetPattern();
            }
        } else {
            data.patternViolations = Math.max(0, data.patternViolations - 1);
        }
    }
    
    /**
     * Detects impossible pathfinding through containers
     */
    private void checkLootRouting(Player player, PlayerChestData data) {
        if (data.getRecentAccesses().size() < 4) return;
        
        List<ChestAccess> accesses = data.getRecentAccesses();
        double totalDistance = 0;
        int pathPoints = 0;
        
        // Calculate distances between consecutive container accesses
        for (int i = 0; i < accesses.size() - 1; i++) {
            double dist = accesses.get(i).location
                .distance(accesses.get(i + 1).location);
            totalDistance += dist;
            pathPoints++;
        }
        
        double avgDistance = totalDistance / pathPoints;
        
        // Check if path goes through walls
        int wallCrosses = 0;
        for (int i = 0; i < accesses.size() - 1; i++) {
            if (wouldCrossWall(accesses.get(i).location, accesses.get(i + 1).location)) {
                wallCrosses++;
            }
        }
        
        // Natural pathing has lower average distance and fewer wall crosses
        if (avgDistance < 8 && wallCrosses > pathPoints * 0.3) {
            data.routingViolations++;
            
            if (data.routingViolations > 3) {
                reportViolation(player, "chest_esp_routing",
                    String.format("Impossible loot path (avg distance: %.1f, walls crossed: %d)",
                        avgDistance, wallCrosses));
                data.resetRouting();
            }
        } else {
            data.routingViolations = Math.max(0, data.routingViolations - 1);
        }
    }
    
    /**
     * Gets all containers near player
     */
    private List<Block> getNearbyContainers(Player player) {
        List<Block> containers = new ArrayList<>();
        int chunkRadius = (int) Math.ceil(DETECTION_RADIUS / 16.0);
        
        for (int cx = -chunkRadius; cx <= chunkRadius; cx++) {
            for (int cz = -chunkRadius; cz <= chunkRadius; cz++) {
                org.bukkit.Chunk chunk = player.getWorld()
                    .getChunkAt(player.getChunk().getX() + cx, player.getChunk().getZ() + cz);
                
                if (!chunk.isLoaded()) continue;
                
                for (BlockState state : chunk.getTileEntities()) {
                    if (state instanceof Container) {
                        Block block = state.getBlock();
                        if (player.getLocation().distance(block.getLocation()) <= DETECTION_RADIUS) {
                            containers.add(block);
                        }
                    }
                }
            }
        }
        
        return containers;
    }
    
    /**
     * Checks if container is accessible (not sealed in blocks)
     */
    private boolean isContainerAccessible(Block container) {
        // Check if at least one side is exposed to air
        Block[] adjacent = {
            container.getRelative(0, 1, 0),
            container.getRelative(0, -1, 0),
            container.getRelative(1, 0, 0),
            container.getRelative(-1, 0, 0),
            container.getRelative(0, 0, 1),
            container.getRelative(0, 0, -1)
        };
        
        for (Block side : adjacent) {
            if (side.getType() == Material.AIR || side.isLiquid()) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Checks if path between two locations would cross walls
     */
    private boolean wouldCrossWall(org.bukkit.Location from, org.bukkit.Location to) {
        if (from.distance(to) > 30) return false; // Don't check very far distances
        
        int steps = (int) from.distance(to);
        double stepX = (to.getX() - from.getX()) / steps;
        double stepY = (to.getY() - from.getY()) / steps;
        double stepZ = (to.getZ() - from.getZ()) / steps;
        
        int wallCrossCount = 0;
        for (int i = 0; i < steps; i++) {
            double x = from.getX() + (stepX * i);
            double y = from.getY() + (stepY * i);
            double z = from.getZ() + (stepZ * i);
            
            Block block = from.getWorld().getBlockAt(
                (int) x, (int) y, (int) z
            );
            
            if (block.getType().isSolid() && !CONTAINER_MATERIALS.contains(block.getType())) {
                wallCrossCount++;
            }
        }
        
        return wallCrossCount > steps * 0.3;
    }
    
    /**
     * Check if container type is valuable
     */
    private boolean isValuableContainer(ChestAccess access) {
        Block block = access.location.getBlock();
        return block.getType() == Material.CHEST || 
               block.getType() == Material.TRAPPED_CHEST ||
               block.getType() == Material.BARREL;
    }
    
    /**
     * Reports a violation
     */
    private void reportViolation(Player player, String type, String details) {
        tracker.addViolation(player.getUniqueId());
        int violations = tracker.getViolations(player.getUniqueId());
        
        if (violations >= 1 && violations < 3) {
            player.kickPlayer("§cChest ESP detected. Violation #" + violations);
        } else if (violations >= 3) {
            Bukkit.broadcastMessage("§c[AntiESP] " + player.getName() + " detected with Chest ESP");
            player.kickPlayer("§cChest ESP confirmed. Permanent ban.");
            plugin.getLogger().warning("Chest ESP violation: " + player.getName() + " - " + details);
        }
    }
    
    public void cleanup() {
        playerData.entrySet().removeIf(entry -> 
            Bukkit.getPlayer(entry.getKey()) == null
        );
        tracker.cleanup();
    }
    
    /**
     * Registers a container interaction (called from listener)
     */
    public void registerContainerAccess(Player player, Block container) {
        UUID uuid = player.getUniqueId();
        if (playerData.containsKey(uuid)) {
            PlayerChestData data = playerData.get(uuid);
            data.addAccess(container.getLocation());
        }
    }
    
    /**
     * Per-player chest data
     */
    private static class PlayerChestData {
        private final Player player;
        private final List<ChestAccess> accesses = new ArrayList<>();
        private int hiddenAccessViolations = 0;
        private int beyondRenderViolations = 0;
        private int unloadedChunkViolations = 0;
        private int inaccessibleViolations = 0;
        private int speedViolations = 0;
        private int patternViolations = 0;
        private int routingViolations = 0;
        private int tickCounter = 0;
        
        PlayerChestData(Player player) {
            this.player = player;
        }
        
        void tick() {
            tickCounter++;
            // Clean old data
            if (tickCounter % 50 == 0) {
                accesses.removeIf(access -> 
                    access.timestamp < System.currentTimeMillis() - 60000 // 60 seconds
                );
            }
        }
        
        void addAccess(org.bukkit.Location location) {
            accesses.add(new ChestAccess(location, System.currentTimeMillis()));
        }
        
        boolean hasRecentAccess(org.bukkit.Location location) {
            long cutoff = System.currentTimeMillis() - 5000; // 5 seconds
            return accesses.stream()
                .anyMatch(access -> access.location.distance(location) < 2 && 
                                   access.timestamp > cutoff);
        }
        
        List<ChestAccess> getRecentAccesses() {
            return new ArrayList<>(accesses);
        }
        
        void resetHiddenAccess() { hiddenAccessViolations = 0; }
        void resetBeyondRender() { beyondRenderViolations = 0; }
        void resetUnloadedChunk() { unloadedChunkViolations = 0; }
        void resetInaccessible() { inaccessibleViolations = 0; }
        void resetSpeed() { speedViolations = 0; }
        void resetPattern() { patternViolations = 0; }
        void resetRouting() { routingViolations = 0; }
    }
    
    /**
     * Represents a container access event
     */
    private static class ChestAccess {
        org.bukkit.Location location;
        long timestamp;
        
        ChestAccess(org.bukkit.Location location, long timestamp) {
            this.location = location;
            this.timestamp = timestamp;
        }
    }
}
