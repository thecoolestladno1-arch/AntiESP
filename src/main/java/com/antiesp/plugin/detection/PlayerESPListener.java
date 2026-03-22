package com.antiesp.plugin.detection;

import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public class PlayerESPListener implements Listener {
    private final JavaPlugin plugin;
    private final PlayerESPDetector detector;
    
    public PlayerESPListener(JavaPlugin plugin, PlayerESPDetector detector) {
        this.plugin = plugin;
        this.detector = detector;
    }
}
