package com.antiesp.plugin.detection;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

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
        if (event.getAction().toString().contains("RIGHT")) {
            detector.registerContainerAccess(event.getPlayer(), block);
        }
    }
}
