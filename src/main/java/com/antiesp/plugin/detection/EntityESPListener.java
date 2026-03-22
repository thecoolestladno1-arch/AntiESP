package com.antiesp.plugin.detection;

import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public class EntityESPListener implements Listener {
    private final JavaPlugin plugin;
    private final EntityESPDetector detector;
    
    public EntityESPListener(JavaPlugin plugin, EntityESPDetector detector) {
        this.plugin = plugin;
        this.detector = detector;
    }
}
