package com.georg.newbieprotection;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class NewbieProtection extends JavaPlugin implements Listener, TabCompleter {

    private File dataFile;
    private FileConfiguration dataConfig;
    private long protectionTimeMinutes;
    private String protectionMessage;
    private String blockedMessage;

    private final HashMap<UUID, Long> protectionMap = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();
        createDataFile();
        loadData();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("newbie").setTabCompleter(this);
        getCommand("protectiontime").setTabCompleter(this);
    }

    @Override
    public void onDisable() {
        saveData();
    }

    private void loadConfig() {
        protectionTimeMinutes = getConfig().getLong("protection-time-minutes", 720);
        protectionMessage = ChatColor.translateAlternateColorCodes('&', getConfig().getString("protection-message", "&aYou are under newbie protection for %minutes% more minutes!"));
        blockedMessage = ChatColor.translateAlternateColorCodes('&', getConfig().getString("attack-blocked-message", "&cYou cannot fight while protected!"));
    }

    private void createDataFile() {
        dataFile = new File(getDataFolder(), "players.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }


    // load existing protection data from players.yml
    private void loadData() {
        for (String key : dataConfig.getKeys(false)) {
            protectionMap.put(UUID.fromString(key), dataConfig.getLong(key));
        }
    }

    // save all data protection data to players.yml
    private void saveData() {
        for (UUID uuid : protectionMap.keySet()) {
            dataConfig.set(uuid.toString(), protectionMap.get(uuid));
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // applies protection only if this is the players first time joining
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPlayedBefore()) {
            protectionMap.putIfAbsent(player.getUniqueId(), System.currentTimeMillis());
            player.sendMessage(ChatColor.GREEN + "You are under newbie protection for 12 hours!");
            player.sendMessage(ChatColor.GREEN + "Use /protectiontime to check on your time!");
        }
    }


    // cancel pvp if either attacker or victim has newbie protection
    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) return;

        Player victim = (Player) event.getEntity();
        Player attacker = (Player) event.getDamager();

        if (isProtected(victim)) {
            attacker.sendMessage(ChatColor.RED + "That player is under newbie protection!");
            event.setCancelled(true);
            return;
        }
        if (isProtected(attacker)) {
            attacker.sendMessage(ChatColor.RED + "You can't attack while under newbie protection!");
            event.setCancelled(true);
        }
    }

    private boolean isProtected(Player player) {
        Long joinTime = protectionMap.get(player.getUniqueId());
        if (joinTime == null) return false;
        long elapsedMinutes = (System.currentTimeMillis() - joinTime) / (1000 * 60);
        return elapsedMinutes < protectionTimeMinutes;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("newbie")) {
            if (args.length == 0) {
                sender.sendMessage(ChatColor.AQUA + "Newbie Protection Commands:");
                sender.sendMessage(ChatColor.YELLOW + "/newbie disable" + ChatColor.GRAY + " - Disable your protection early");
                if (sender.hasPermission("newbie.admin")) {
                    sender.sendMessage(ChatColor.YELLOW + "/newbie disable <player>" + ChatColor.GRAY + " - Disable protection for player (admin)");
                    sender.sendMessage(ChatColor.YELLOW + "/newbie reset <player>" + ChatColor.GRAY + " - Reset protection for player (admin)");
                    sender.sendMessage(ChatColor.YELLOW + "/newbie enable <player>" + ChatColor.GRAY + " - Enable protection for player (admin)");
                    sender.sendMessage(ChatColor.YELLOW + "/newbie reload" + ChatColor.GRAY + " - Reload the plugin configuration");
                }
                return true;
            }

            if (args.length == 1 && args[0].equalsIgnoreCase("disable") && sender instanceof Player) {
                Player player = (Player) sender;
                if (!isProtected(player)) {
                    player.sendMessage(ChatColor.RED + "You are not under newbie protection.");
                    return true;
                }
                protectionMap.remove(player.getUniqueId());
                saveData();
                player.sendMessage(ChatColor.YELLOW + "You have ended your newbie protection early.");
                return true;
            }

            if (args.length == 1 && args[0].equalsIgnoreCase("reload") && sender.hasPermission("newbie.admin")) {
                reloadConfig();
                loadConfig();
                sender.sendMessage(ChatColor.GREEN + "NewbieProtection config reloaded.");
                return true;
            }

            if (args.length == 2) {
                String sub = args[0];
                String targetName = args[1];
                OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                if (target == null || target.getUniqueId() == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found.");
                    return true;
                }

                if (!sender.hasPermission("newbie.admin")) {
                    sender.sendMessage(ChatColor.RED + "You must be an operator to use this command.");
                    return true;
                }

                switch (sub.toLowerCase()) {
                    case "reset":
                    case "enable":
                        protectionMap.put(target.getUniqueId(), System.currentTimeMillis());
                        saveData();
                        sender.sendMessage(ChatColor.GREEN + "Newbie protection enabled for " + target.getName());
                        if (target.isOnline()) {
                            ((Player) target).sendMessage(ChatColor.GREEN + "Your newbie protection has been reset by an admin.");
                        }
                        break;
                    case "disable":
                        protectionMap.remove(target.getUniqueId());
                        saveData();
                        sender.sendMessage(ChatColor.YELLOW + "Newbie protection disabled for " + target.getName());
                        if (target.isOnline()) {
                            ((Player) target).sendMessage(ChatColor.RED + "Your newbie protection has been disabled by an admin.");
                        }
                        break;
                    default:
                        sender.sendMessage(ChatColor.RED + "Unknown subcommand.");
                }
                return true;
            }
        }

        if (label.equalsIgnoreCase("protectiontime") && sender instanceof Player) {
            Player player = (Player) sender;
            if (!isProtected(player)) {
                player.sendMessage(ChatColor.RED + "You are not under newbie protection.");
                return true;
            }
            long remaining = protectionTimeMinutes - ((System.currentTimeMillis() - protectionMap.get(player.getUniqueId())) / (1000 * 60));
            if (remaining >= 60) {
                long hours = remaining / 60;
                player.sendMessage(ChatColor.GREEN + "You have " + hours + (hours == 1 ? " hour" : " hours") + " of protection remaining.");
            } else {
                player.sendMessage(ChatColor.GREEN + "You have " + remaining + (remaining == 1 ? " minute" : " minutes") + " of protection remaining.");
            }
            return true;
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("newbie")) {
            if (args.length == 1) {
                List<String> list = new ArrayList<>();
                list.add("disable");
                if (sender.hasPermission("newbie.admin")) {
                    list.add("enable");
                    list.add("reset");
                    list.add("reload");
                }
                return list;
            } else if (args.length == 2 && sender.hasPermission("newbie.admin")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }
        return Collections.emptyList();
    }
}
