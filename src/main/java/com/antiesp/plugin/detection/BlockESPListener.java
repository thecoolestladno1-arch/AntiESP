package com.antiesp.plugin.detection;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

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
        detector.registerBlockBreak(player, block);
    }
}
