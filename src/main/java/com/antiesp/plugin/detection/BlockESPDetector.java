package com.antiesp.plugin.detection;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import com.antiesp.plugin.utils.ViolationTracker;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Block ESP Detector - detects impossible ore/resource discovery
 * Detection methods:
 * 1. Mining pattern analysis (too fast, too accurate)
 * 2. Block breaking efficiency rate
 * 3. Hidden block access (breaking blocks in unloaded chunks)
 * 4. Optimal pathfinding through ore veins
 */
public class BlockESPDetector {
    
    private final JavaPlugin plugin;
    private final ViolationTracker tracker;
    private final Map<UUID, PlayerBlockData> playerData;
    private final Set<Material> VALUABLE_BLOCKS = Set.of(
        Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
        Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
        Material.ANCIENT_DEBRIS,
        Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
        Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
        Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
        Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
        Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE
    );
    
    private final double DETECTION_RADIUS = 48.0;
    private final int SAMPLE_WINDOW = 200; // ticks
    private final double EFFICIENCY_THRESHOLD = 0.75; // 75% accuracy is suspicious
    
    public BlockESPDetector(JavaPlugin plugin) {
        this.plugin = plugin;
        this.tracker = new ViolationTracker("block_esp");
        this.playerData = new ConcurrentHashMap<>();
    }
    
    /**
     * Main detection check - called every tick
     */
    public void checkPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        
        if (!playerData.containsKey(uuid)) {
            playerData.put(uuid, new PlayerBlockData(player));
            return;
        }
        
        PlayerBlockData data = playerData.get(uuid);
        
        // Check 1: Mining efficiency anomalies
        checkMiningEfficiency(player, data);
        
        // Check 2: Hidden block breaking
        checkHiddenBlockBreaking(player, data);
        
        // Check 3: Optimal pathfinding through ores
        checkOreMiningPattern(player, data);
        
        // Check 4: Unnatural mining sequences
        checkMiningSequence(player, data);
        
        data.tick();
    }
    
    /**
     * Detects mining efficiency that's too high to be natural
     */
    private void checkMiningEfficiency(Player player, PlayerBlockData data) {
        // Calculate blocks broken in last SAMPLE_WINDOW ticks
        long recentBroken = data.getBlocksMinedInWindow(SAMPLE_WINDOW);
        
        if (recentBroken > 5) {
            // Check what percentage were valuable blocks
            long valuableBlocks = data.getValuableBlocksInWindow(SAMPLE_WINDOW);
            double efficiency = (double) valuableBlocks / recentBroken;
            
            // Human players typically have 30-50% accuracy on valuable blocks
            // ESP users have 70%+ accuracy
            if (efficiency > EFFICIENCY_THRESHOLD && recentBroken > 10) {
                data.efficiencyViolations++;
                
                if (data.efficiencyViolations > 5) {
                    reportViolation(player, "block_esp_efficiency",
                        String.format("Mining efficiency %.1f%% (%.0f valuable blocks out of %.0f)",
                            efficiency * 100, valuableBlocks, recentBroken));
                    data.resetEfficiency();
                }
            } else {
                data.efficiencyViolations = Math.max(0, data.efficiencyViolations - 1);
            }
        }
    }
    
    /**
     * Detects breaking blocks that shouldn't be visible (in unloaded chunks)
     */
    private void checkHiddenBlockBreaking(Player player, PlayerBlockData data) {
        for (BlockAccess access : data.getRecentBlockAccesses()) {
            Block block = access.block;
            
            // Check if block is loaded
            if (!block.isValid() || !block.getChunk().isLoaded()) {
                // Block breaking in unloaded chunk
                data.hiddenBlockViolations++;
                
                if (data.hiddenBlockViolations > 2) {
                    reportViolation(player, "block_esp_hidden",
                        "Attempted to break block in unloaded chunk at " + block.getLocation());
                    data.resetHiddenBlocks();
                }
            }
            
            // Check if block is behind solid blocks
            if (!hasLineOfSightToBlock(player, block)) {
                data.obstructedBlockViolations++;
                
                if (data.obstructedBlockViolations > 3) {
                    reportViolation(player, "block_esp_obstructed",
                        "Breaking blocks through solid objects");
                    data.resetObstructed();
                }
            }
        }
    }
    
    /**
     * Detects optimal pathfinding through ore veins (connecting ores impossibly)
     */
    private void checkOreMiningPattern(Player player, PlayerBlockData data) {
        if (data.getRecentOreAccesses().size() < 3) return;
        
        List<BlockAccess> ores = data.getRecentOreAccesses();
        double totalDistance = 0;
        
        // Calculate distances between consecutive ore blocks
        for (int i = 0; i < ores.size() - 1; i++) {
            double dist = ores.get(i).block.getLocation()
                .distance(ores.get(i + 1).block.getLocation());
            totalDistance += dist;
        }
        
        double avgDistance = totalDistance / (ores.size() - 1);
        
        // Natural mining has average distance 1-3 blocks
        // ESP users have 0.8-1.2 (perfect adjacency)
        if (avgDistance < 1.3 && ores.size() > 5) {
            data.optimalPathViolations++;
            
            if (data.optimalPathViolations > 4) {
                reportViolation(player, "block_esp_optimal_path",
                    String.format("Impossible ore vein connection (avg distance: %.2f)", avgDistance));
                data.resetOptimalPath();
            }
        } else {
            data.optimalPathViolations = Math.max(0, data.optimalPathViolations - 1);
        }
    }
    
    /**
     * Detects unnatural mining sequences (always hitting valuable blocks first)
     */
    private void checkMiningSequence(Player player, PlayerBlockData data) {
        List<BlockAccess> recent = data.getRecentBlockAccesses(20);
        if (recent.size() < 5) return;
        
        // Count how many of the first 5 blocks were valuable
        int valuableInFirst5 = 0;
        for (int i = 0; i < Math.min(5, recent.size()); i++) {
            if (VALUABLE_BLOCKS.contains(recent.get(i).block.getType())) {
                valuableInFirst5++;
            }
        }
        
        // Humans don't find all valuable blocks in first 5
        if (valuableInFirst5 >= 4) {
            data.sequenceViolations++;
            
            if (data.sequenceViolations > 3) {
                reportViolation(player, "block_esp_sequence",
                    "Unnatural mining sequence detected");
                data.resetSequence();
            }
        } else {
            data.sequenceViolations = Math.max(0, data.sequenceViolations - 1);
        }
    }
    
    /**
     * Check if player has line of sight to a block
     */
    private boolean hasLineOfSightToBlock(Player player, Block target) {
        return player.hasLineOfSight(target);
    }
    
    /**
     * Reports a violation
     */
    private void reportViolation(Player player, String type, String details) {
        tracker.addViolation(player.getUniqueId());
        int violations = tracker.getViolations(player.getUniqueId());
        
        if (violations >= 1 && violations < 3) {
            player.kickPlayer("§cBlock ESP detected. Violation #" + violations);
        } else if (violations >= 3) {
            Bukkit.broadcastMessage("§c[AntiESP] " + player.getName() + " detected with Block ESP");
            player.kickPlayer("§cBlock ESP confirmed. Permanent ban.");
            plugin.getLogger().warning("Block ESP violation: " + player.getName() + " - " + details);
        }
    }
    
    public void cleanup() {
        playerData.entrySet().removeIf(entry -> 
            Bukkit.getPlayer(entry.getKey()) == null
        );
        tracker.cleanup();
    }
    
    /**
     * Registers a block break event (called from listener)
     */
    public void registerBlockBreak(Player player, Block block) {
        UUID uuid = player.getUniqueId();
        if (playerData.containsKey(uuid)) {
            PlayerBlockData data = playerData.get(uuid);
            data.addBlockAccess(block);
        }
    }
    
    /**
     * Per-player block mining data
     */
    private static class PlayerBlockData {
        private final Player player;
        private final List<BlockAccess> blockAccesses = new ArrayList<>();
        private int efficiencyViolations = 0;
        private int hiddenBlockViolations = 0;
        private int obstructedBlockViolations = 0;
        private int optimalPathViolations = 0;
        private int sequenceViolations = 0;
        private int tickCounter = 0;
        
        PlayerBlockData(Player player) {
            this.player = player;
        }
        
        void tick() {
            tickCounter++;
            // Clear old data periodically
            if (tickCounter % 100 == 0) {
                blockAccesses.removeIf(access -> 
                    access.timestamp < System.currentTimeMillis() - 30000 // 30 seconds
                );
            }
        }
        
        void addBlockAccess(Block block) {
            blockAccesses.add(new BlockAccess(block, System.currentTimeMillis()));
        }
        
        long getBlocksMinedInWindow(int ticks) {
            long cutoff = System.currentTimeMillis() - (ticks * 50L);
            return blockAccesses.stream()
                .filter(access -> access.timestamp > cutoff)
                .count();
        }
        
        long getValuableBlocksInWindow(int ticks) {
            long cutoff = System.currentTimeMillis() - (ticks * 50L);
            return blockAccesses.stream()
                .filter(access -> access.timestamp > cutoff && 
                        VALUABLE_BLOCKS.contains(access.block.getType()))
                .count();
        }
        
        List<BlockAccess> getRecentBlockAccesses() {
            return new ArrayList<>(blockAccesses);
        }
        
        List<BlockAccess> getRecentBlockAccesses(int count) {
            int start = Math.max(0, blockAccesses.size() - count);
            return blockAccesses.subList(start, blockAccesses.size());
        }
        
        List<BlockAccess> getRecentOreAccesses() {
            return blockAccesses.stream()
                .filter(access -> VALUABLE_BLOCKS.contains(access.block.getType()))
                .toList();
        }
        
        void resetEfficiency() { efficiencyViolations = 0; }
        void resetHiddenBlocks() { hiddenBlockViolations = 0; }
        void resetObstructed() { obstructedBlockViolations = 0; }
        void resetOptimalPath() { optimalPathViolations = 0; }
        void resetSequence() { sequenceViolations = 0; }
    }
    
    /**
     * Represents a single block access event
     */
    private static class BlockAccess {
        Block block;
        long timestamp;
        
        BlockAccess(Block block, long timestamp) {
            this.block = block;
            this.timestamp = timestamp;
        }
    }
}
