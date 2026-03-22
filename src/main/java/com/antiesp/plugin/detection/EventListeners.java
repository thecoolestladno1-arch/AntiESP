package com.antiesp.plugin.detection;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Event listeners for block breaking and interaction detection
 */
public class BlockESPListener implements Listener {
    
    private final JavaPlugin plugin;
    private final BlockESPDetector detector;
    
    public BlockESPListener(JavaPlugin plugin, BlockESPDetector detector) {
        this.plugin = plugin;
        this.detector = detector;
    }
    
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        
        // Register block access for detection
        detector.registerBlockBreak(player, block);
    }
}

/**
 * Event listeners for chest/container ESP detection
 */
public class ChestESPListener implements Listener {
    
    private final JavaPlugin plugin;
    private final ChestESPDetector detector;
    
    public ChestESPListener(JavaPlugin plugin, ChestESPDetector detector) {
        this.plugin = plugin;
        this.detector = detector;
    }
    
    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        
        Player player = (Player) event.getPlayer();
        Block targetBlock = player.getTargetBlockExact(5);
        
        if (targetBlock != null) {
            detector.registerContainerAccess(player, targetBlock);
        }
    }
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.hasBlock()) return;
        
        Block block = event.getClickedBlock();
        if (block == null) return;
        
        // Register right-click interactions with containers
        if (event.getAction().toString().contains("RIGHT")) {
            detector.registerContainerAccess(event.getPlayer(), block);
        }
    }
}

/**
 * Event listeners for entity ESP detection
 */
public class EntityESPListener implements Listener {
    
    private final JavaPlugin plugin;
    private final EntityESPDetector detector;
    
    public EntityESPListener(JavaPlugin plugin, EntityESPDetector detector) {
        this.plugin = plugin;
        this.detector = detector;
    }
    
    // Entity ESP detection is mostly tick-based rather than event-based
    // to catch invisible entity access patterns
}

/**
 * Event listeners for player ESP detection
 */
public class PlayerESPListener implements Listener {
    
    private final JavaPlugin plugin;
    private final PlayerESPDetector detector;
    
    public PlayerESPListener(JavaPlugin plugin, PlayerESPDetector detector) {
        this.plugin = plugin;
        this.detector = detector;
    }
    
    // Player ESP detection is mostly tick-based for continuous monitoring
}
