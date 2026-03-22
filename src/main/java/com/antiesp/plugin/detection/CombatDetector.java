package com.antiesp.plugin.detection;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import com.antiesp.plugin.utils.ViolationTracker;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Combat & Reach Hack Detection
 * Detects: Reach/killaura, fast attack, damage hacking
 */
public class CombatDetector implements Listener {
    
    private final JavaPlugin plugin;
    private final ViolationTracker tracker;
    private final Map<UUID, PlayerCombatData> playerData;
    private final double MAX_LEGITIMATE_REACH = 3.5; // Blocks (with knockback, 4.3)
    private final long MIN_ATTACK_DELAY = 400; // ms (0.4 seconds per attack)
    
    public CombatDetector(JavaPlugin plugin) {
        this.plugin = plugin;
        this.tracker = new ViolationTracker("combat_hack");
        this.playerData = new ConcurrentHashMap<>();
    }
    
    /**
     * Monitor damage events for reach hacks and killaura
     */
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        
        Player attacker = (Player) event.getDamager();
        UUID uuid = attacker.getUniqueId();
        
        if (!playerData.containsKey(uuid)) {
            playerData.put(uuid, new PlayerCombatData(attacker));
        }
        
        PlayerCombatData data = playerData.get(uuid);
        long currentTime = System.currentTimeMillis();
        
        // Check 1: Reach hacks (hitting too far away)
        checkReachHack(attacker, event.getEntity().getLocation(), data, currentTime);
        
        // Check 2: Attack speed (too many hits too fast)
        checkAttackSpeed(attacker, data, currentTime);
        
        // Check 3: Killaura patterns (hitting multiple enemies impossibly fast)
        checkKillaura(attacker, data, currentTime);
        
        // Check 4: Damage consistency
        checkDamageConsistency(attacker, event.getDamage(), data);
    }
    
    /**
     * Detect reach hacks - hitting entities beyond arm reach
     * Legit reach: ~3.5 blocks
     * Reach hackers: 5-20+ blocks
     */
    private void checkReachHack(Player attacker, org.bukkit.Location targetLoc, 
                                PlayerCombatData data, long currentTime) {
        double distance = attacker.getLocation().distance(targetLoc);
        
        // Allow some margin for lag/latency
        if (distance > MAX_LEGITIMATE_REACH + 0.5) {
            data.reachViolations++;
            data.lastLongReach = currentTime;
            
            if (data.reachViolations > 2) {
                reportViolation(attacker, "reach_hack",
                    String.format("Attacking entity %.1f blocks away (max: %.1f)",
                        distance, MAX_LEGITIMATE_REACH));
                data.resetReach();
            }
        } else {
            data.reachViolations = Math.max(0, data.reachViolations - 1);
        }
    }
    
    /**
     * Detect impossible attack speed
     * Legit: min 400ms between hits (with haste: 300ms)
     * Hackers: <100ms between hits
     */
    private void checkAttackSpeed(Player attacker, PlayerCombatData data, long currentTime) {
        if (data.lastAttackTime == 0) {
            data.lastAttackTime = currentTime;
            return;
        }
        
        long timeSinceLastAttack = currentTime - data.lastAttackTime;
        
        if (timeSinceLastAttack < MIN_ATTACK_DELAY) {
            data.fastAttackCounter++;
            
            if (data.fastAttackCounter > 5) {
                reportViolation(attacker, "fast_attack",
                    String.format("Attack speed %.0fms (min: %dms)",
                        timeSinceLastAttack, MIN_ATTACK_DELAY));
                data.resetAttackSpeed();
            }
        } else {
            data.fastAttackCounter = Math.max(0, data.fastAttackCounter - 1);
        }
        
        data.lastAttackTime = currentTime;
    }
    
    /**
     * Detect killaura - hitting multiple enemies impossibly fast and accurately
     */
    private void checkKillaura(Player attacker, PlayerCombatData data, long currentTime) {
        // Get list of online players
        List<Player> nearbyEnemies = getNearbyPlayers(attacker, 8.0);
        
        // If recently attacking multiple different targets very fast
        if (nearbyEnemies.size() > 2) {
            // Check if attacker is hitting different targets within impossible timeframe
            int targetsHitRecently = 0;
            
            for (Player target : nearbyEnemies) {
                if (target.equals(attacker) || target.isDead()) continue;
                
                Long lastHitTime = data.targetHitTimes.get(target.getUniqueId());
                if (lastHitTime != null && currentTime - lastHitTime < 1000) {
                    targetsHitRecently++;
                }
            }
            
            if (targetsHitRecently > 2) {
                data.killauraViolations++;
                
                if (data.killauraViolations > 3) {
                    reportViolation(attacker, "killaura",
                        String.format("Killaura: hitting %d targets impossibly fast",
                            targetsHitRecently));
                    data.resetKillaura();
                }
            } else {
                data.killauraViolations = Math.max(0, data.killauraViolations - 1);
            }
        }
    }
    
    /**
     * Check for impossible damage consistency
     * Players doing precise damage amounts = suspicious (damage mods)
     */
    private void checkDamageConsistency(Player attacker, double damage, PlayerCombatData data) {
        data.addDamageAmount(damage);
        
        if (data.recentDamages.size() > 20) {
            // Check if all damages are suspiciously similar
            Double firstDamage = data.recentDamages.get(0);
            int consistentCount = 0;
            
            for (Double d : data.recentDamages) {
                if (Math.abs(d - firstDamage) < 0.5) {
                    consistentCount++;
                }
            }
            
            double consistency = (double) consistentCount / data.recentDamages.size();
            
            if (consistency > 0.85) {
                data.damageConsistencyViolations++;
                
                if (data.damageConsistencyViolations > 3) {
                    reportViolation(attacker, "damage_hack",
                        String.format("Impossible damage consistency (%.0f%% similar)",
                            consistency * 100));
                    data.resetDamageConsistency();
                }
            } else {
                data.damageConsistencyViolations = Math.max(0, data.damageConsistencyViolations - 1);
            }
        }
    }
    
    /**
     * Get nearby players
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
     * Report violation
     */
    private void reportViolation(Player player, String type, String details) {
        tracker.addViolation(player.getUniqueId());
        int violations = tracker.getViolations(player.getUniqueId());
        
        if (violations >= 1 && violations < 3) {
            player.kickPlayer("§cCombat hack detected. Violation #" + violations);
        } else if (violations >= 3) {
            Bukkit.broadcastMessage("§c[AntiESP] " + player.getName() + " detected with combat hacks");
            player.kickPlayer("§cCombat hack confirmed. Permanent ban.");
            plugin.getLogger().warning("Combat hack violation: " + player.getName() + " - " + details);
        }
    }
    
    public void cleanup() {
        playerData.entrySet().removeIf(entry -> Bukkit.getPlayer(entry.getKey()) == null);
        tracker.cleanup();
    }
    
    /**
     * Per-player combat data
     */
    private static class PlayerCombatData {
        private final Player player;
        private int reachViolations = 0;
        private int fastAttackCounter = 0;
        private int killauraViolations = 0;
        private int damageConsistencyViolations = 0;
        private long lastAttackTime = 0;
        private long lastLongReach = 0;
        private final Map<UUID, Long> targetHitTimes = new ConcurrentHashMap<>();
        private final List<Double> recentDamages = new ArrayList<>();
        
        PlayerCombatData(Player player) {
            this.player = player;
        }
        
        void addDamageAmount(double damage) {
            recentDamages.add(damage);
            if (recentDamages.size() > 50) {
                recentDamages.remove(0);
            }
        }
        
        void resetReach() { reachViolations = 0; }
        void resetAttackSpeed() { fastAttackCounter = 0; }
        void resetKillaura() { killauraViolations = 0; }
        void resetDamageConsistency() { damageConsistencyViolations = 0; }
    }
}
