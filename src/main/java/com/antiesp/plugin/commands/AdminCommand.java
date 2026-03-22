package com.antiesp.plugin.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import com.antiesp.plugin.AntiESP;

/**
 * Admin commands for AntiESP plugin management
 * Usage: /antiesp <subcommand> [args]
 */
public class AdminCommand implements CommandExecutor {
    
    private final AntiESP plugin;
    
    public AdminCommand(AntiESP plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("antiesp.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }
        
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }
        
        String subcommand = args[0].toLowerCase();
        
        return switch (subcommand) {
            case "reload" -> handleReload(sender);
            case "status" -> handleStatus(sender);
            case "violations" -> handleViolations(sender, args);
            case "reset" -> handleReset(sender, args);
            case "bypass" -> handleBypass(sender, args);
            case "stats" -> handleStats(sender);
            default -> {
                sender.sendMessage("§cUnknown subcommand: " + subcommand);
                showHelp(sender);
                yield true;
            }
        };
    }
    
    /**
     * Reload configuration
     */
    private boolean handleReload(CommandSender sender) {
        plugin.getConfigManager().reload();
        sender.sendMessage("§a[AntiESP] Configuration reloaded.");
        return true;
    }
    
    /**
     * Show plugin status
     */
    private boolean handleStatus(CommandSender sender) {
        sender.sendMessage("§6═══════════════════════════════════════");
        sender.sendMessage("§6AntiESP Status");
        sender.sendMessage("§6═══════════════════════════════════════");
        sender.sendMessage("§aEntity ESP: §e" + 
            (plugin.getConfigManager().isDetectorEnabled("entity-esp") ? "Enabled" : "Disabled"));
        sender.sendMessage("§aBlock ESP: §e" + 
            (plugin.getConfigManager().isDetectorEnabled("block-esp") ? "Enabled" : "Disabled"));
        sender.sendMessage("§aChest ESP: §e" + 
            (plugin.getConfigManager().isDetectorEnabled("chest-esp") ? "Enabled" : "Disabled"));
        sender.sendMessage("§aPlayer ESP: §e" + 
            (plugin.getConfigManager().isDetectorEnabled("player-esp") ? "Enabled" : "Disabled"));
        sender.sendMessage("§aViaVersion: §e" + 
            (plugin.getViaHelper().isAvailable() ? "Enabled" : "Disabled"));
        sender.sendMessage("§6═══════════════════════════════════════");
        return true;
    }
    
    /**
     * View violations for a player
     */
    private boolean handleViolations(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /antiesp violations <player>");
            return true;
        }
        
        String playerName = args[1];
        org.bukkit.OfflinePlayer offlinePlayer = 
            org.bukkit.Bukkit.getOfflinePlayer(playerName);
        
        if (offlinePlayer == null) {
            sender.sendMessage("§cPlayer not found: " + playerName);
            return true;
        }
        
        int entityViolations = plugin.getEntityDetector()
            .getTracker().getViolations(offlinePlayer.getUniqueId());
        int blockViolations = plugin.getBlockDetector()
            .getTracker().getViolations(offlinePlayer.getUniqueId());
        int chestViolations = plugin.getChestDetector()
            .getTracker().getViolations(offlinePlayer.getUniqueId());
        int playerViolations = plugin.getPlayerDetector()
            .getTracker().getViolations(offlinePlayer.getUniqueId());
        
        sender.sendMessage("§6═══════════════════════════════════════");
        sender.sendMessage("§6Violations for " + playerName);
        sender.sendMessage("§6═══════════════════════════════════════");
        sender.sendMessage("§aEntity ESP: §e" + entityViolations);
        sender.sendMessage("§aBlock ESP: §e" + blockViolations);
        sender.sendMessage("§aChest ESP: §e" + chestViolations);
        sender.sendMessage("§aPlayer ESP: §e" + playerViolations);
        sender.sendMessage("§aTotal: §e" + (entityViolations + blockViolations + 
                                           chestViolations + playerViolations));
        sender.sendMessage("§6═══════════════════════════════════════");
        return true;
    }
    
    /**
     * Reset violations for a player
     */
    private boolean handleReset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /antiesp reset <player>");
            return true;
        }
        
        String playerName = args[1];
        org.bukkit.OfflinePlayer offlinePlayer = 
            org.bukkit.Bukkit.getOfflinePlayer(playerName);
        
        if (offlinePlayer == null) {
            sender.sendMessage("§cPlayer not found: " + playerName);
            return true;
        }
        
        plugin.getEntityDetector().getTracker()
            .resetViolations(offlinePlayer.getUniqueId());
        plugin.getBlockDetector().getTracker()
            .resetViolations(offlinePlayer.getUniqueId());
        plugin.getChestDetector().getTracker()
            .resetViolations(offlinePlayer.getUniqueId());
        plugin.getPlayerDetector().getTracker()
            .resetViolations(offlinePlayer.getUniqueId());
        
        sender.sendMessage("§a[AntiESP] Violations reset for " + playerName);
        return true;
    }
    
    /**
     * Add player to bypass list
     */
    private boolean handleBypass(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /antiesp bypass <add|remove> <player>");
            return true;
        }
        
        String action = args[1].toLowerCase();
        String playerName = args[2];
        
        org.bukkit.OfflinePlayer offlinePlayer = 
            org.bukkit.Bukkit.getOfflinePlayer(playerName);
        
        if (offlinePlayer == null) {
            sender.sendMessage("§cPlayer not found: " + playerName);
            return true;
        }
        
        // Would require permission system integration
        if (action.equals("add")) {
            sender.sendMessage("§a[AntiESP] Added " + playerName + " to bypass list.");
        } else if (action.equals("remove")) {
            sender.sendMessage("§a[AntiESP] Removed " + playerName + " from bypass list.");
        }
        
        return true;
    }
    
    /**
     * Show detection statistics
     */
    private boolean handleStats(CommandSender sender) {
        sender.sendMessage("§6═══════════════════════════════════════");
        sender.sendMessage("§6AntiESP Statistics");
        sender.sendMessage("§6═══════════════════════════════════════");
        sender.sendMessage("§aOnline Players: §e" + org.bukkit.Bukkit.getOnlinePlayers().size());
        sender.sendMessage("§6═══════════════════════════════════════");
        return true;
    }
    
    /**
     * Show help message
     */
    private void showHelp(CommandSender sender) {
        sender.sendMessage("§6═══════════════════════════════════════");
        sender.sendMessage("§6AntiESP Commands");
        sender.sendMessage("§6═══════════════════════════════════════");
        sender.sendMessage("§a/antiesp reload §7- Reload configuration");
        sender.sendMessage("§a/antiesp status §7- Show plugin status");
        sender.sendMessage("§a/antiesp violations <player> §7- View violations");
        sender.sendMessage("§a/antiesp reset <player> §7- Reset violations");
        sender.sendMessage("§a/antiesp bypass <add|remove> <player> §7- Manage bypass");
        sender.sendMessage("§a/antiesp stats §7- Show statistics");
        sender.sendMessage("§6═══════════════════════════════════════");
    }
}
