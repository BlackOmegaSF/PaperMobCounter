package com.floogoobooq.blackomega.papermobcounter;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.*;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.regex.Pattern;

public class PaperMobCounter extends JavaPlugin implements Listener {

    private final HashMap<UUID, Scoreboard> customBoards = new HashMap<>();
    private final HashMap<UUID, HashSet<EntityType>> entityTracker = new HashMap<>();
    private ScoreboardManager sbm;
    private final HashSet<UUID> playersToScoreboard = new HashSet<>();

    File saveFile;
    FileConfiguration saveData;


    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this,this);
        sbm = Bukkit.getScoreboardManager();

        if (!(saveFile = new File(getDataFolder(), "mobcounts.yml")).exists()) {
            saveFile = new File(getDataFolder(), "mobcounts.yml");
        }
        saveData = YamlConfiguration.loadConfiguration(saveFile);

        for (Map.Entry<String, Object> entry : saveData.getValues(true).entrySet()) {
            String playerId;
            UUID playerUuid;
            String entityName;
            //getLogger().log(Level.INFO, "Processing YAML Entry: " + entry.getKey());

            try {
                playerId = entry.getKey().split("\\.")[0];
                playerUuid = UUID.fromString(playerId);
                entityName = entry.getKey().split("\\.")[1];
            } catch (IndexOutOfBoundsException e) {
                getLogger().log(Level.INFO, "Loading player scoreboard: " + entry.getKey().split("\\.")[0]);
                continue;
            }

            Scoreboard board;
            Objective obj;
            if (customBoards.containsKey(playerUuid)) {
                board = customBoards.get(playerUuid);
                obj = board.getObjective("Kill Counter");
            } else {
                board = sbm.getNewScoreboard();
                obj = board.registerNewObjective("Kill Counter", "dummy", Component.text("Mob Kill Counter"));
                obj.setDisplaySlot(DisplaySlot.SIDEBAR);
                customBoards.put(playerUuid, board);
            }

            assert obj != null;
            obj.getScore(entityName).setScore((int)entry.getValue());
            HashSet<EntityType> etList = entityTracker.get(playerUuid);
            if (etList == null) {
                etList = new HashSet<>();
            }
            etList.add(EntityType.valueOf(entityName));
            entityTracker.put(playerUuid, etList);

        }

        for (Map.Entry<UUID, Scoreboard> entry : customBoards.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                //player is online
                player.setScoreboard(entry.getValue());
            } else {
                //player is offline
                playersToScoreboard.add(entry.getKey());
            }
        }


        //Register the command
        this.getCommand("countmobs").setExecutor(new CommandHandler());
        this.getCommand("countmobs").setTabCompleter(new EntityTabCompletion());

    }

    @Override
    public void onDisable() {
        //Save data to saveFile
        for (Map.Entry<UUID, HashSet<EntityType>> entry : entityTracker.entrySet()) {
            for(EntityType entityEntry : entry.getValue()) {
                int score = Objects.requireNonNull(customBoards.get(entry.getKey()).getObjective("Kill Counter")).getScore(entityEntry.toString()).getScore();
                saveData.set(entry.getKey().toString() + "." + entityEntry, score);
            }
        }

        try {
            saveData.save(saveFile);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @EventHandler
    public void onPlayerJoin(final PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (playersToScoreboard.contains(playerId)) {
            player.setScoreboard(customBoards.get(playerId));
            playersToScoreboard.remove(playerId);
        }
    }

    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();

        if (customBoards.containsKey(playerId)) {
            playersToScoreboard.add(playerId);
        }
    }


    //Command responder class
    public class CommandHandler implements CommandExecutor {

        @Override
        public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
            if (sender instanceof Player player) {
                if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
                    return false;
                }

                EntityType toCount;
                try {
                    toCount = EntityType.valueOf(args[1].toUpperCase());

                } catch (IllegalArgumentException|NullPointerException|IndexOutOfBoundsException e) {
                    sender.sendMessage("Invalid mob");
                    return false;
                }
                String entityName = toCount.toString();
                //String entityShortName = entityName.substring(0, Math.min(entityName.length(), 16));  //Trimmed to 16 to avoid error

                final String _START = "START";
                final String _STOP = "STOP";
                final String _RESET = "RESET";
                final String _RESUME = "RESUME";
                final String _GETCOUNT = "GETCOUNT";
                switch (args[0].toUpperCase()) {
                    case _START:
                        // Handle start condition
                        try {
                            //Get or create scoreboard for player
                            Scoreboard board = customBoards.get(player.getUniqueId());
                            if (board == null) {
                                board = sbm.getNewScoreboard();
                                customBoards.put(player.getUniqueId(), board);
                            }
                            player.setScoreboard(board);

                            //Register new objective in scoreboard using info here
                            Objective obj = board.getObjective("Kill Counter");
                            if (obj == null) {
                                obj = board.registerNewObjective("Kill Counter", "dummy", Component.text("Mob Kill Counter"));
                                obj.setDisplaySlot(DisplaySlot.SIDEBAR);
                            }



                            //Add entity to entity tracker
                            HashSet<EntityType> etList = entityTracker.get(player.getUniqueId());
                            if (etList == null) {
                                etList = new HashSet<>();
                            }
                            if (etList.contains(toCount)) {
                                sender.sendMessage("Already counting " + toCount);
                                return true;
                            }

                            etList.add(toCount);
                            entityTracker.put(player.getUniqueId(), etList);

                            //Set up score entry
                            obj.getScore(entityName).setScore(0);

                            player.setScoreboard(board);

                            return true;

                        } catch (IndexOutOfBoundsException e) {
                            sender.sendMessage("Too few arguments");
                            return false;
                        }

                    /*case _RESUME:
                        //TODO Handle resume condition


                        break;
                     */

                    case _STOP:
                        // Handle stop condition
                        try {
                            Scoreboard board = customBoards.get(player.getUniqueId());
                            if (board == null) {
                                sender.sendMessage("No scoreboard initialized for " + PlainTextComponentSerializer.plainText().serialize(player.displayName()) + ". Make sure to start a count before stopping.");
                                return true;
                            }

                            Objective obj = board.getObjective("Kill Counter");
                            if (obj != null) {

                                HashSet<EntityType> etList = entityTracker.get(player.getUniqueId());
                                if (etList == null || !etList.contains(toCount)) {
                                    sender.sendMessage("Not currently counting " + entityName);
                                    return true;
                                }

                                board.resetScores(entityName);

                                etList.remove(toCount);
                                if (etList.size() == 0) {
                                    obj.unregister();
                                    player.setScoreboard(sbm.getMainScoreboard());
                                    customBoards.remove(player.getUniqueId());
                                    entityTracker.remove(player.getUniqueId());
                                    saveData.set(player.getUniqueId().toString(), null);
                                    return true;
                                }
                                saveData.set(player.getUniqueId() + "." + entityName, null);
                                entityTracker.put(player.getUniqueId(), etList);

                            } else {
                                sender.sendMessage("Objective not initialized.");
                            }

                            return true;


                        } catch (NullPointerException e) {
                            sender.sendMessage("Welp, this ABSOLUTELY shouldn't happen. Tell Kian IMMEDIATELY because it's bad.");
                            return true;
                        }


                    /*case _GETCOUNT:
                        //TODO Handle getcount condition
                        break;
                     */

                    case _RESET:
                        // Handle reset condition
                        try {
                            Scoreboard board = customBoards.get(player.getUniqueId());
                            if (board == null) {
                                sender.sendMessage("No scoreboard initialized for " + PlainTextComponentSerializer.plainText().serialize(player.displayName()) + ". Make sure to start a count before stopping.");
                                return true;
                            }

                            Objective obj = board.getObjective("Kill Counter");
                            if (obj != null) {

                                HashSet<EntityType> etList = entityTracker.get(player.getUniqueId());
                                if (etList == null || !etList.contains(toCount)) {
                                    sender.sendMessage("Not currently counting " + entityName);
                                    return true;
                                }

                                Score score = obj.getScore(entityName);
                                score.setScore(0);

                            } else {
                                sender.sendMessage("Objective not initialized.");
                            }

                            return true;

                        } catch (NullPointerException e) {
                            sender.sendMessage("Welp, this ABSOLUTELY shouldn't happen. Tell Kian IMMEDIATELY because it's bad.");
                            return true;
                        }


                    default:
                        sender.sendMessage("Invalid command");
                        return false;
                }

            } else {
                sender.sendMessage("Only players can use this command.");
                return false;
            }
        }

    }

    @EventHandler
    public void onEntityDeath(final EntityDeathEvent event) {
        LivingEntity killedMob = event.getEntity();
        Player killer = killedMob.getKiller();

        if (killer != null) {

            EntityType killedType = killedMob.getType();
            HashSet<EntityType> etList = entityTracker.get(killer.getUniqueId());
            if (etList != null && etList.contains(killedType)) {
                Scoreboard board = customBoards.get(killer.getUniqueId());
                if (board != null) {
                    Objective obj = board.getObjective("Kill Counter");
                    if (obj != null) {
                        Score score = obj.getScore(killedType.toString());
                        score.setScore(score.getScore() + 1);
                    }
                }
            }

        }

    }

    public class EntityTabCompletion implements TabCompleter {

        @Override
        public List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String alias, String[] args) {

            List<String> names = new ArrayList<>();
            if (args.length == 1) {
                if (args[0].isEmpty()) {
                    names.add("start");
                    names.add("reset");
                    names.add("stop");
                } else {
                    Pattern pattern = Pattern.compile(args[0].toLowerCase());
                    if (pattern.matcher("start").lookingAt()) {
                        names.add("start");
                    }
                    if (pattern.matcher("reset").lookingAt()) {
                        names.add("reset");
                    }
                    if (pattern.matcher("stop").lookingAt()) {
                        names.add("stop");
                    }
                }

                return names;

            } else if (args.length == 2) {
                if (args[0].equals("start")) {
                    for (EntityType entry : EntityType.values()) {
                        names.add(entry.toString());
                    }
                } else if (args[0].equals("reset") || args[0].equals("stop")) {
                    if (commandSender instanceof Player && entityTracker.containsKey(((Player) commandSender).getUniqueId())) {
                        HashSet<EntityType> etList = entityTracker.get(((Player) commandSender).getUniqueId());
                        if (!etList.isEmpty()) {
                            for (EntityType entry : etList) {
                                names.add(entry.toString());
                            }
                        }
                    }
                } else {
                    return null;
                }

                List<String> filteredNames = new ArrayList<>();
                if (!args[1].isEmpty()) {
                    Pattern pattern = Pattern.compile(args[1].toUpperCase());
                    for (String name : names) {
                        if (pattern.matcher(name).lookingAt()) {
                            filteredNames.add(name);
                        }
                    }
                    return filteredNames;
                }

                return names;
            }




            return names;
        }
    }



}
