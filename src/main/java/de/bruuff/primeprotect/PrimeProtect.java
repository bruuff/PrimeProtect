/*
 * This file is part of the plugin PrimeProtect for Sponge licensed under the MIT License (MIT).
 *
 * Copyright (c) 2015 Florian Brunzlaff
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package de.bruuff.primeprotect;

import com.flowpowered.math.vector.Vector3d;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import ninja.leaping.configurate.ConfigurationOptions;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import org.slf4j.Logger;
import org.spongepowered.api.Game;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.source.ConsoleSource;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.effect.particle.ParticleEffect;
import org.spongepowered.api.effect.particle.ParticleTypes;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.block.ChangeBlockEvent;
import org.spongepowered.api.event.entity.DisplaceEntityEvent;
import org.spongepowered.api.event.entity.InteractEntityEvent;
import org.spongepowered.api.event.game.state.GameStartingServerEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.scoreboard.Scoreboard;
import org.spongepowered.api.scoreboard.critieria.Criteria;
import org.spongepowered.api.scoreboard.displayslot.DisplaySlots;
import org.spongepowered.api.scoreboard.objective.Objective;
import org.spongepowered.api.scoreboard.objective.displaymode.ObjectiveDisplayModes;
import org.spongepowered.api.service.ProviderExistsException;
import org.spongepowered.api.service.user.UserStorageService;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.Texts;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Plugin(id = "PrimeProtect", name = "PrimeProtect", version = "0.1")
public class PrimeProtect {

    @Inject
    public Game game;

    @Inject
    public Logger logger;

    public Map<Player, Plot> showPlotTicks = new HashMap<>();
    Task showPlotTickTask;
    public Map<Player, Plot> playersEditingPlots = new HashMap<>();

    public PropertyService propertyService;
    public UserStorageService userStorageService;

    public PropertyService getPropertyService() {
        return propertyService;
    }

    public CommentedConfigurationNode mainConfig;
    public CommentedConfigurationNode localizationConfig;
    public CommentedConfigurationNode permissionConfig;
    @Inject
    @DefaultConfig(sharedRoot = false)
    private File defaultConfig;

    @Listener
    public void onStart(GameStartingServerEvent event) {
        loadConfig();
        loadCommands();
        runShowPlotTask();

        //Initializing plot management
        try {
            game.getServiceManager().setProvider(this, PropertyService.class, new PlotPropertyService(this));
        } catch (ProviderExistsException e) {
            logger.error("PropertyProvider already exists.");
            e.printStackTrace();
        }

        Optional<PropertyService> optPropertyService = game.getServiceManager().provide(PropertyService.class);
        if(optPropertyService.isPresent()){
            propertyService = optPropertyService.get();
        }else{
            logger.error("Could not get PropertyService.");
        }

        Optional<UserStorageService> optUserStorageService= game.getServiceManager().provide(UserStorageService.class);
        if(optUserStorageService.isPresent()){
            userStorageService = optUserStorageService.get();
        }else{
            logger.error("Could not get UserStorageService.");
        }

    }

    private void loadConfig(){
        //Main plot config
        ConfigurationLoader<CommentedConfigurationNode> configManager = HoconConfigurationLoader.builder().setPath(defaultConfig.toPath()).build();
        try {
            if (!defaultConfig.exists() ) {
                if(defaultConfig.createNewFile()){
                    mainConfig = configManager.createEmptyNode(ConfigurationOptions.defaults());
                    mainConfig.getNode("plots").setComment("The main config file will later hold general configuration like maximum plot sizes, economy support and stuff like that. Currently empty.");

                    configManager.save(mainConfig);
                }
            }
            mainConfig = configManager.load();
        } catch (IOException e) {
            logger.error("Could not write config file.");
        }

        //Localization config
        File localization = new File(defaultConfig.getParent() + "\\localization.conf");
        ConfigurationLoader<CommentedConfigurationNode> localizationConfigManager = HoconConfigurationLoader.builder().setPath(localization.toPath()).build();
        try {
            if (!localization.exists()) {
                if(localization.createNewFile()){
                    localizationConfig = localizationConfigManager.createEmptyNode(ConfigurationOptions.defaults());
                    localizationConfig.setComment("Change it however you like. Unknown failures should not fire, but are a fallback while in development.");
                    localizationConfig.getNode("plot", "new", "SUCCESS").setValue("Now creating new plot in %parent%.");
                    localizationConfig.getNode("plot", "new", "FAILURE_WRONG_USAGE").setValue("Could not create new plot. (Vacant plot already there.)");
                    localizationConfig.getNode("plot", "new", "FAILURE_NO_PERMISSION").setValue("Could not add vertex. (No permission)");
                    localizationConfig.getNode("plot", "new", "FAILURE").setValue("Could not create new plot. (Unknown reason)");
                    localizationConfig.getNode("plot", "exit", "SUCCESS").setValue("Aborted plot creation.");
                    localizationConfig.getNode("plot", "exit", "FAILURE").setValue("You are not claiming a plot right now.");
                    localizationConfig.getNode("plot", "add", "SUCCESS").setValue("Vertex added.");
                    localizationConfig.getNode("plot", "add", "FAILURE_NOT_FOUND").setValue("Could not add vertex. (Start a new plot first)");
                    localizationConfig.getNode("plot", "add", "FAILURE_WRONG_USAGE").setValue("Could not add vertex. (Plot has to be inside parent plot)");
                    localizationConfig.getNode("plot", "add", "FAILURE_INTERSECTS_BORDER").setValue("Could not add vertex. (Plot intersects parent plot border)");
                    localizationConfig.getNode("plot", "add", "FAILURE_BAD_ALIGNMENT").setValue("Could not add vertex. (Border can only be 45° or 90°)");
                    localizationConfig.getNode("plot", "add", "FAILURE_NO_PERMISSION").setValue("Could not add vertex. (No permission)");
                    localizationConfig.getNode("plot", "add", "FAILURE").setValue("Could not add vertex. (Unknown reason)");
                    localizationConfig.getNode("plot", "save", "SUCCESS").setValue("Plot saved.");
                    localizationConfig.getNode("plot", "save", "FAILURE_INTERSECTS_BORDER").setValue("Could not save plot. (Plot has to be inside parent plot)");
                    localizationConfig.getNode("plot", "save", "FAILURE_BAD_ALIGNMENT").setValue("Could not save plot. (Border can only be 45° or 90°)");
                    localizationConfig.getNode("plot", "save", "FAILURE").setValue("Could not save plot. (Unknown reason)");
                    localizationConfig.getNode("plot", "give", "SUCCESS").setValue("Plot given to %ownertype% %receiver%");
                    localizationConfig.getNode("plot", "give", "FAILURE_WRONG_USAGE").setValue("Could not transfer plot. (Plot already owned)");
                    localizationConfig.getNode("plot", "give", "FAILURE_DATABASE_GROUP").setValue("Could not transfer plot. (Group not found)");
                    localizationConfig.getNode("plot", "give", "FAILURE_DATABASE_PLAYER").setValue("Could not transfer plot. (Player not found)");
                    localizationConfig.getNode("plot", "give", "FAILURE_NO_PERMISSION").setValue("Could not transfer plot. (No permission)");
                    localizationConfig.getNode("plot", "give", "FAILURE").setValue("Could not transfer plot. (Unknown reason)");

                    localizationConfig.getNode("group", "new", "SUCCESS").setValue("New Group created.");
                    localizationConfig.getNode("group", "new", "FAILURE_DATABASE_GROUP").setValue("Could not create group. (Group already exists)");
                    localizationConfig.getNode("group", "new", "FAILURE").setValue("Could not create group. (Unknown reason)");
                    localizationConfig.getNode("group", "add", "SUCCESS").setValue("Player %player% added to group %group%.");
                    localizationConfig.getNode("group", "add", "FAILURE_ALREADY_THERE").setValue("Could not add player %player% to group %group%. (Player already in group)");
                    localizationConfig.getNode("group", "add", "FAILURE_DATABASE_GROUP").setValue("Could not add player %player% to group %group%. (Group not found)");
                    localizationConfig.getNode("group", "add", "FAILURE_DATABASE_PLAYER").setValue("Could not add player %player% to group %group%. (Player not found)");
                    localizationConfig.getNode("group", "add", "FAILURE").setValue("Could not add player %player% to group %group%. (Unknown reason)");
                    localizationConfig.getNode("group", "remove", "SUCCESS").setValue("Player %player% removed from group %group%.");
                    localizationConfig.getNode("group", "remove", "FAILURE_NOT_FOUND").setValue("Could not remove player %player% from group %group%. (Player not in group)");
                    localizationConfig.getNode("group", "remove", "FAILURE_DATABASE_GROUP").setValue("Could not remove player %player% from group %group%. (Group not found)");
                    localizationConfig.getNode("group", "remove", "FAILURE_DATABASE_PLAYER").setValue("Could not remove player %player% from group %group%. (Player not found)");
                    localizationConfig.getNode("group", "remove", "FAILURE").setValue("Could not remove %player% from group %group%. (Unknown reason)");

                    localizationConfig.getNode("group", "rank", "SUCCESS").setValue("Player %player% is now ranked %rank% in group %group%.");
                    localizationConfig.getNode("group", "rank", "FAILURE_NOT_FOUND").setValue("Could not rank player %player% in group %group%. (Player not in group)");
                    localizationConfig.getNode("group", "rank", "FAILURE_DATABASE_GROUP").setValue("Could not rank player %player% in group %group%. (Group not found)");
                    localizationConfig.getNode("group", "rank", "FAILURE_DATABASE_PLAYER").setValue("Could not rank player %player% in group %group%. (Player not found)");
                    localizationConfig.getNode("group", "rank", "FAILURE").setValue("Could not rank player %player% in group %group%. (Unknown reason)");

                    localizationConfig.getNode("group", "list", "SUCCESS").setValue("Groups:");
                    localizationConfig.getNode("group", "list", "FAILURE").setValue("There are no groups yet.");
                    localizationConfigManager.save(localizationConfig);
                }
            }
            localizationConfig = localizationConfigManager.load();
        } catch (IOException e) {
            logger.error("Could not write config file.");
        }

        //Permission config
        File permissions = new File(defaultConfig.getParent() + "\\permissions.conf");
        ConfigurationLoader<CommentedConfigurationNode> permissionsConfigManager = HoconConfigurationLoader.builder().setPath(permissions.toPath()).build();
        try {
            if (!permissions.exists()) {
                if(permissions.createNewFile()){
                    permissionConfig = localizationConfigManager.createEmptyNode(ConfigurationOptions.defaults());

                    permissionConfig.setComment("Define what rank the player in plot has to be at least, do do certain things. (3=operator, 2=assistant, 1=member, 0=outsider");

                    permissionConfig.getNode("plot", "build").setValue(2);
                    permissionConfig.getNode("plot", "break").setValue(2);
                    permissionConfig.getNode("plot", "entity").setValue(1);
                    permissionConfig.getNode("plot", "use").setValue(0);
                    permissionConfig.getNode("plot", "claim").setValue(2);
                    permissionConfig.getNode("plot", "give").setValue(2);
                    permissionConfig.getNode("plot", "rename").setValue(3);
                    permissionConfig.getNode("plot", "delete").setValue(3);

                    permissionConfig.getNode("group", "add").setValue(2);
                    permissionConfig.getNode("group", "list").setValue(0);
                    permissionConfig.getNode("group", "remove").setValue(3);
                    permissionConfig.getNode("group", "rank").setValue(3);

                    permissionsConfigManager.save(permissionConfig);
                }
            }
            permissionConfig = permissionsConfigManager.load();
        } catch (IOException e) {
            logger.error("Could not write config file.");
        }
    }

    private void loadCommands(){
        CommandSpec primePlotNewCmd = CommandSpec.builder().description(Texts.of("Claim a new vacant plot."))
                .permission("primeprotect.plot.claim")
                .arguments( GenericArguments.none() )
                .executor((src, args) -> {
                    if (src instanceof Player) {
                        Player player = (Player) src;
                        Map<String, String> dictionary = new HashMap<>();
                        dictionary.put("player", player.getName());
                        Response response;

                        if(playersEditingPlots.containsKey(player)) {
                            //We dont need this anymore (Not that it would hurt as it has no area)
                            this.getPropertyService().deletePlot(playersEditingPlots.get(player).getId());
                        }

                        Plot parentPlot = this.getPropertyService().getPlot(player.getLocation()); // Worst case: Wilderness

                        //Put names in dictionary for response handling
                        dictionary.put("parent", parentPlot.getDisplayName());
                        Plot plot = null;

                        if(parentPlot.getOwner().isPresent()){
                            PlotOwner parentOwner = parentPlot.getOwner().get();
                            //If you are in the current plot at least the rank it takes to claim...
                            if(parentOwner.containsUser(player.getUniqueId(), Rank.valueOf(permissionConfig.getNode("plot", "claim").getInt()))){
                                plot = this.getPropertyService().createPlot(Optional.empty(), player.getWorld(), parentPlot);
                                dictionary.put("plot", plot.getDisplayName());
                                response = plot.addPoint(new PlotPoint(player.getLocation().getBlockX(), player.getLocation().getBlockZ()));
                                showPlotTicks.put(player, plot);
                                playersEditingPlots.put(player, plot);
                                this.updateScoreboard(plot, player);
                            }else response = Response.FAILURE_NO_PERMISSION; //Player has not the right permission to claim here.
                        }else response = Response.FAILURE_WRONG_USAGE; //Only in vacant plots, so there has to be already one.
                        player.sendMessage(makeResponse(localizationConfig.getNode("plot", "new"), response, dictionary));
                    } else if (src instanceof ConsoleSource) {
                        src.sendMessage(Texts.of("You are a console and can't claim."));
                    }
                    return CommandResult.success();
                })
                .build();

        CommandSpec primePlotExitCmd = CommandSpec.builder().description(Texts.of("Exit plot creation."))
                .permission("primeprotect.plot.claim")
                .arguments( GenericArguments.none() )
                .executor((src, args) -> {
                    if (src instanceof Player) {
                        Player player = (Player) src;
                        Map<String, String> dictionary = new HashMap<>();
                        dictionary.put("player", player.getName());
                        Response response;

                        if(playersEditingPlots.containsKey(player)) {
                            this.getPropertyService().deletePlot(playersEditingPlots.get(player).getId());
                            playersEditingPlots.remove(player);
                            showPlotTicks.remove(player);
                            this.clearScoreboard(player);
                            response = Response.SUCCESS;
                        }else response = Response.FAILURE_WRONG_USAGE;

                        player.sendMessage(makeResponse(localizationConfig.getNode("plot", "exit"), response, dictionary));
                    } else if (src instanceof ConsoleSource) {
                        src.sendMessage(Texts.of("You are a console and can't claim."));
                    }
                    return CommandResult.success();
                })
                .build();

        CommandSpec primePlotAddCmd = CommandSpec.builder().description(Texts.of("Add vertex to a current claim"))
                .permission("primeprotect.plot.claim")
                .arguments( GenericArguments.none() )
                .executor((src, args) -> {
                    if (src instanceof Player) {
                        Player player = (Player) src;
                        Map<String, String> dictionary = new HashMap<>();
                        dictionary.put("player", player.getName());
                        Location<World> location = player.getLocation();
                        dictionary.put("location", location.toString());
                        Response response;
                        Plot parentPlot = this.getPropertyService().getPlot(player.getLocation()); // Worst case: Wilderness

                        //Put names in dictionary for response handling
                        dictionary.put("parent", parentPlot.getDisplayName());
                        if(playersEditingPlots.containsKey(player)){
                            Plot plot = playersEditingPlots.get(player);
                            dictionary.put("plot", plot.getDisplayName());
                            Optional<Plot> optParentPlot2 = plot.getParent();
                            if(optParentPlot2.isPresent()){
                                Plot parentPlot2 = optParentPlot2.get();
                                if(parentPlot.equals(parentPlot2) || parentPlot.getOwner().isPresent()){
                                    PlotOwner parentOwner = parentPlot.getOwner().get();
                                    //If you are in the current plot at least the rank it takes to claim...
                                    if(parentOwner.containsUser(player.getUniqueId(), Rank.valueOf(permissionConfig.getNode("plot", "claim").getInt()))){
                                        response = plot.addPoint(new PlotPoint(location.getBlockX(), location.getBlockZ()));
                                        showPlotTicks.put(player, plot);
                                        playersEditingPlots.put(player, plot);
                                        this.updateScoreboard(plot, player);
                                    }else response = Response.FAILURE_NO_PERMISSION; //Player has not the right permission to claim here.
                                }else response = Response.FAILURE_WRONG_USAGE; //Only in vacant plots, so there has to be already one.
                            }else response = Response.FAILURE; //You can't edit wilderness. Can't imagine how this would even happen.
                        }else response = Response.FAILURE_NOT_FOUND; //Player has to start the claiming of a new plot first.

                        player.sendMessage(makeResponse(localizationConfig.getNode("plot", "add"), response, dictionary));
                    } else if (src instanceof ConsoleSource) {
                        src.sendMessage(Texts.of("You are a console and can't claim."));
                    }
                    return CommandResult.success();
                })
                .build();

        CommandSpec primePlotSaveCmd = CommandSpec.builder().description(Texts.of("Saves plot as vacant plot for sale to others"))
                .permission("primeprotect.plot.claim")
                .arguments( GenericArguments.none() )
                .executor((src, args) -> {
                    if (src instanceof Player) {
                        Player player = (Player) src;
                        Map<String, String> dictionary = new HashMap<>();
                        dictionary.put("player", player.getName());
                        Response response;
                        if(playersEditingPlots.containsKey(player)){
                            Plot plot = playersEditingPlots.get(player);
                            //Put names in dictionary for response handling
                            dictionary.put("plot", plot.getDisplayName());
                            if (plot.isComplete()) {
                                if (plot.isValidShape()) {
                                    this.getPropertyService().savePlot(plot);
                                    playersEditingPlots.remove(player);
                                    showPlotTicks.remove(player);
                                    this.clearScoreboard(player);
                                    response = Response.SUCCESS;
                                }else response = Response.FAILURE_INTERSECTS_BORDER; //Closing line would intersect forbidden territory
                            }else response = Response.FAILURE_BAD_ALIGNMENT; //Closing line does not align correctly
                        }else response = Response.FAILURE_WRONG_USAGE; //Player has to start the claiming of a new plot first.
                        player.sendMessage(makeResponse(localizationConfig.getNode("plot", "save"), response, dictionary));
                    } else if (src instanceof ConsoleSource) {
                        src.sendMessage(Texts.of("You are a console and can't claim."));
                    }
                    return CommandResult.success();
                })
                .build();

        CommandSpec primePlotGiveCmd = CommandSpec.builder().description(Texts.of("Give a plot to another player or group"))
                .permission("primeprotect.plot.give")
                .arguments(GenericArguments.choices(Texts.of("ownertype"), ImmutableMap.of("player", "player", "group","group")), GenericArguments.string(Texts.of("name")) )
                .executor((src, args) -> {
                    if (src instanceof Player) {
                        Player player = (Player) src;
                        Response response;
                        Map<String, String> dictionary = new HashMap<>();
                        dictionary.put("player", player.getName());
                        String receiverName = args.<String>getOne("name").get();
                        String ownerType = args.<String>getOne("ownertype").get();
                        dictionary.put("ownertype", ownerType);

                        Optional<User> optReceiverUser = userStorageService.get(receiverName);
                        Optional<Group> optReceiverGroup = this.getPropertyService().getGroup(receiverName);
                        Plot plot = this.getPropertyService().getPlot(player.getLocation());

                        if(plot.getCurrentOwner().containsUser(player.getUniqueId(), Rank.valueOf(permissionConfig.getNode("plot", "give").getInt()))){
                            if(ownerType.equals("player") && optReceiverUser.isPresent()){
                                User receiver = optReceiverUser.get();
                                dictionary.put("receiver", receiver.getName());
                                plot.setOwner(new PlotOwner(receiver.getUniqueId()));
                                this.getPropertyService().savePlot(plot);
                                response = Response.SUCCESS;
                            }else if(ownerType.equals("group") && optReceiverGroup.isPresent()){
                                Group receiver = optReceiverGroup.get();
                                dictionary.put("receiver", receiver.getName());
                                plot.setOwner(new PlotOwner(receiver));
                                this.getPropertyService().savePlot(plot);
                                response = Response.SUCCESS;
                            }else{
                                if(ownerType.equals("player")) response = Response.FAILURE_DATABASE_PLAYER; //Player not found
                                else response = Response.FAILURE_DATABASE_GROUP; //Group not found
                            }
                        }else response = Response.FAILURE_NO_PERMISSION; //You can't give away what isn't yours.

                        player.sendMessage(makeResponse(localizationConfig.getNode("plot", "give"), response, dictionary));
                    } else if (src instanceof ConsoleSource) {
                        src.sendMessage(Texts.of("You are a console and can't do that."));
                    }
                    return CommandResult.success();
                })
                .build();

        CommandSpec primePlotCmd = CommandSpec.builder()
                .description(Texts.of("PrimeProtect Plot Management"))
                //.arguments(GenericArguments.remainingJoinedStrings(Texts.of("text")))
                .executor((src, args) -> {
                    //String choice = args.<String>getOne("action").get();
                    if (src instanceof Player) {
                        Player player = (Player) src;
                        player.sendMessage(Texts.of("plot help"));
                    } else if (src instanceof ConsoleSource) {
                        src.sendMessage(Texts.of("You are a console and can't use that."));
                    }
                    return CommandResult.success();
                })
                .child(primePlotNewCmd, "new", "create")
                .child(primePlotExitCmd, "exit", "abort", "break")
                .child(primePlotAddCmd, "add")
                .child(primePlotSaveCmd, "save", "done")
                .child(primePlotGiveCmd, "give")
                .build();

        CommandSpec primeGroupNewCmd = CommandSpec.builder().description(Texts.of("Create a new group"))
                .permission("primeprotect.group.new")
                .arguments( GenericArguments.onlyOne(GenericArguments.string(Texts.of("name"))) )
                .executor((src, args) -> {
                    if (src instanceof Player) {
                        Player player = (Player) src;
                        String name = args.<String>getOne("name").get();
                        Map<String, String> dictionary = new HashMap<>();
                        Response response;
                        dictionary.put("player", player.getName());
                        dictionary.put("group", name);
                        Optional<Group> optExistingGroup = this.getPropertyService().getGroup(name);
                        if(!optExistingGroup.isPresent()){
                            this.getPropertyService().createGroup(name, player.getUniqueId());
                            response = Response.SUCCESS;
                        }else response = Response.FAILURE_DATABASE_GROUP; //Name already in use
                        player.sendMessage(makeResponse(localizationConfig.getNode("group", "new"), response, dictionary));
                    } else if (src instanceof ConsoleSource) {
                        src.sendMessage(Texts.of("You are a console and can't do that."));
                    }
                    return CommandResult.success();
                })
                .build();

        CommandSpec primeGroupAddCmd = CommandSpec.builder().description(Texts.of("Add a player to a group"))
                .permission("primeprotect.group.add")
                .arguments( GenericArguments.string(Texts.of("group")), GenericArguments.string(Texts.of("player")) )
                .executor((src, args) -> {
                    if (src instanceof Player) {
                        Response response;
                        Player player = (Player) src;
                        Map<String, String> dictionary = new HashMap<>();
                        String groupName = args.<String>getOne("group").get();
                        dictionary.put("group", groupName);
                        String newMemberName = args.<String>getOne("player").get();
                        Optional<User> optNewMember = userStorageService.get(newMemberName);
                        if(optNewMember.isPresent()){
                            User newMember = optNewMember.get();
                            dictionary.put("player", newMember.getName());
                            Optional<Group> optGroup = this.getPropertyService().getGroup(groupName);
                            if(optGroup.isPresent()){
                                Group group = optGroup.get();
                                if(group.addUser(newMember.getUniqueId(), Rank.MEMBER)){
                                    response = Response.SUCCESS;
                                    this.getPropertyService().saveGroup(group);
                                }else response = Response.FAILURE_ALREADY_THERE; // Player already in group
                            }else response = Response.FAILURE_DATABASE_GROUP; //Could not find group
                        }else response = Response.FAILURE_DATABASE_PLAYER; //Could not find player

                        player.sendMessage(makeResponse(localizationConfig.getNode("group", "add"), response, dictionary));
                    } else if (src instanceof ConsoleSource) {
                        src.sendMessage(Texts.of("You are a console and can't do that."));
                    }
                    return CommandResult.success();
                })
                .build();

        CommandSpec primeGroupRemoveCmd = CommandSpec.builder().description(Texts.of("Remove a player from a group"))
                .permission("primeprotect.group.remove")
                .arguments( GenericArguments.string(Texts.of("group")), GenericArguments.string(Texts.of("player")) )
                .executor((src, args) -> {
                    if (src instanceof Player) {
                        Response response;
                        Player player = (Player) src;
                        Map<String, String> dictionary = new HashMap<>();
                        String groupName = args.<String>getOne("group").get();
                        dictionary.put("group", groupName);
                        String memberName = args.<String>getOne("player").get();
                        Optional<User> optMember = userStorageService.get(memberName);
                        if(optMember.isPresent()){
                            User member = optMember.get();
                            dictionary.put("player", member.getName());
                            Optional<Group> optGroup = this.getPropertyService().getGroup(groupName);
                            if(optGroup.isPresent()){
                                Group group = optGroup.get();
                                if(group.removeUser(member.getUniqueId())){
                                    response = Response.SUCCESS;
                                    this.getPropertyService().saveGroup(group);
                                }else response = Response.FAILURE_NOT_FOUND; // Player not in group.
                            }else response = Response.FAILURE_DATABASE_GROUP; //Could not find group
                        }else response = Response.FAILURE_DATABASE_PLAYER; //Could not find player

                        player.sendMessage(makeResponse(localizationConfig.getNode("group", "remove"), response, dictionary));
                    } else if (src instanceof ConsoleSource) {
                        src.sendMessage(Texts.of("You are a console and can't do that."));
                    }
                    return CommandResult.success();
                })
                .build();

        CommandSpec primeGroupRankCmd = CommandSpec.builder().description(Texts.of("Rank a player in a group"))
                .permission("primeprotect.group.rank")
                .arguments( GenericArguments.string(Texts.of("group")), GenericArguments.string(Texts.of("player")), GenericArguments.choices(Texts.of("rank"), ImmutableMap.of("operator", "operator", "assistant", "assistant", "member", "member")) )
                .executor((src, args) -> {
                    if (src instanceof Player) {
                        Response response;
                        Player player = (Player) src;
                        Map<String, String> dictionary = new HashMap<>();
                        String groupName = args.<String>getOne("group").get();
                        dictionary.put("group", groupName);
                        String rankstring = args.<String>getOne("rank").get();
                        dictionary.put("rank", rankstring.toUpperCase());

                        String memberName = args.<String>getOne("player").get();
                        Optional<User> optMember = userStorageService.get(memberName);
                        if(optMember.isPresent()){
                            User member = optMember.get();
                            dictionary.put("player", member.getName());
                            Optional<Group> optGroup = this.getPropertyService().getGroup(groupName);
                            if(optGroup.isPresent()){
                                Group group = optGroup.get();
                                if(Rank.valueOf(rankstring.toUpperCase()) != null){
                                    if(group.rankUser(member.getUniqueId(), Rank.valueOf(rankstring.toUpperCase()))){
                                        response = Response.SUCCESS;
                                        this.getPropertyService().saveGroup(group);
                                    }else response = Response.FAILURE_NOT_FOUND; // Player not in group.
                                }else response = Response.FAILURE; // Could not find rank (should always be the case though
                            }else response = Response.FAILURE_DATABASE_GROUP; //Could not find group
                        }else response = Response.FAILURE_DATABASE_PLAYER; //Could not find player

                        player.sendMessage(makeResponse(localizationConfig.getNode("group", "rank"), response, dictionary));
                    } else if (src instanceof ConsoleSource) {
                        src.sendMessage(Texts.of("You are a console and can't do that."));
                    }
                    return CommandResult.success();
                })
                .build();

        CommandSpec primeGroupCmd = CommandSpec.builder()
                .description(Texts.of("PrimeProtect info"))
                .executor((src, args) -> {
                    src.sendMessage(Texts.of("group help"));
                    return CommandResult.success();
                })
                .child(primeGroupNewCmd, "new")
                .child(primeGroupAddCmd, "add")
                .child(primeGroupRemoveCmd, "remove")
                .child(primeGroupRankCmd, "rank")
                .build();


        CommandSpec primeReloadCmd = CommandSpec.builder()
                .description(Texts.of("PrimeProtect reload"))
                .executor((src, args) -> {
                    loadConfig();
                    src.sendMessage(Texts.of("Config reloaded."));
                    return CommandResult.success();
                })
                .build();

        CommandSpec primeInfoCmd = CommandSpec.builder()
                .description(Texts.of("PrimeProtect info"))
                .executor((src, args) -> {
                    src.sendMessage(Texts.of("*PrimeProtect info page.*"));
                    return CommandResult.success();
                })
                .build();

        CommandSpec primeCmd = CommandSpec.builder()
                .description(Texts.of("Prime help page"))
                //.arguments(GenericArguments.remainingJoinedStrings(Texts.of("text")))
                .executor((src, args) -> {
                    //String choice = args.<String>getOne("action").get();
                    if (src instanceof Player) {
                        Player player = (Player) src;
                        player.sendMessage(Texts.of("general help"));
                    } else if (src instanceof ConsoleSource) {
                        src.sendMessage(Texts.of("You are a console and can't use that."));
                    }
                    return CommandResult.success();
                })
                .child(primePlotCmd, "plot")
                .child(primeGroupCmd, "group")
                .child(primeReloadCmd, "reload")
                .child(primeInfoCmd, "info")
                .build();
        game.getCommandManager().register(this, primeCmd, "prime");
    }

    public Text makeResponse(CommentedConfigurationNode responseNode, Response response, Map<String, String> dictionary){
        String responseString;
        Text responseText;
        if(responseNode.getNode(response.toString()).getString() != null) {
            responseString = responseNode.getNode(response.toString()).getString();
        }else if(responseNode.getNode("FAILURE").getString() != null) {
            responseString = responseNode.getNode("FAILURE").getString();
        }else{
            responseString = "Command executed (check localization file!).";
        }

        for (Map.Entry<String, String> entry : dictionary.entrySet()){
            responseString = responseString.replaceAll("%" + entry.getKey() +  "%", entry.getValue());
        }

        if(response.toString().startsWith("SUCCESS")) {
            responseText = Texts.of(TextColors.GREEN, responseString);
        }else{
            responseText = Texts.of(TextColors.RED, responseString);
        }
        return responseText;
    }

    @Listener
    public void onMove(DisplaceEntityEvent.TargetPlayer event){
        if(!(event.getFromTransform().getPosition().getFloorX() == event.getToTransform().getPosition().getFloorX())
                || !(event.getFromTransform().getPosition().getFloorZ() == event.getToTransform().getPosition().getFloorZ()) ){

            Plot plotFrom = propertyService.getPlot(event.getFromTransform().getPosition(), event.getTargetEntity().getWorld());
            Plot plotTo = propertyService.getPlot(event.getToTransform().getPosition(), event.getTargetEntity().getWorld());

            if(plotFrom.getId() != plotTo.getId()){
                String message = "" + plotTo.getDisplayName();
                List<Plot> plotFromParentChain = plotFrom.getParentChain();
                for(Plot ancestor: plotTo.getParentChain()){
                    if(plotFromParentChain.contains(ancestor)) break; //If that happens, all further down the line must also be the same, so skip mentioning it.

                    message = ancestor.getDisplayName() + " -> " + message;
                }
                event.getTargetEntity().sendMessage(Texts.of(TextColors.GRAY , "~ " + message));
            }
        }
    }

    @Listener
    public void onBlockChange(ChangeBlockEvent event){
        Optional<Player> playerOptional = event.getCause().first(Player.class);
        Optional<BlockSnapshot> blockOptional = event.getCause().first(BlockSnapshot.class);
        if(!playerOptional.isPresent()) return;
        Player player = playerOptional.get();

        //Still have to test how powerful this is. I might be cancelling to much right now.

        //Fluids cause a lot of updates, allow all for now and revisit later.
        if(blockOptional.isPresent()){
            if(blockOptional.get().getState().getType().equals(BlockTypes.FLOWING_WATER) || blockOptional.get().getState().getType().equals(BlockTypes.FLOWING_LAVA)){
                return;
            }
        }

        event.getTransactions().stream()
                .filter(transaction -> transaction.getOriginal().getLocation().isPresent())
                .filter(transaction -> !checkPermission(transaction.getOriginal().getLocation().get(), player))
                .forEach(transaction -> {
            transaction.setValid(false);
            player.sendMessage(Texts.of(TextColors.RED, "You are not allowed to do this."));
        });
    }

    @Listener
    public void onEntityInteract(InteractEntityEvent event){
        Optional<Player> playerOptional = event.getCause().first(Player.class);
        if(!playerOptional.isPresent()) return;
        Player player = playerOptional.get();
        if(!checkPermission(event.getTargetEntity().getLocation(), player)){
            player.sendMessage(Texts.of(TextColors.RED, "You are not allowed to do this."));
            event.setCancelled(true);
        }
    }


    private boolean checkPermission(Location<World> location, User user){
        Plot plot = this.getPropertyService().getPlot(location);
        PlotOwner plotOwner;
        if(plot.getOwner().isPresent()) {
            plotOwner = plot.getOwner().get();
        }else{ //Vacent lot, check parent
            if(plot.getParent().isPresent()){
                if(plot.getParent().get().getOwner().isPresent()){
                    plotOwner = plot.getParent().get().getOwner().get();
                }else{
                    return false; //Something is bugged.
                }
            }else{
                plotOwner = new PlotOwner(Group.everyone());
            }

        }
        return plotOwner.containsUser(user.getUniqueId(), Rank.valueOf(permissionConfig.getNode("plot", "place").getInt()));
    }

    public void runShowPlotTask(){
        if(showPlotTickTask != null) showPlotTickTask.cancel();
        Task.Builder taskBuilder = game.getScheduler().createTaskBuilder();

        showPlotTickTask = taskBuilder.execute(() -> {
            for (Map.Entry<Player, Plot> entry : showPlotTicks.entrySet()) {
                showPlot(entry.getValue(), entry.getKey());
            }
        }).interval(200, TimeUnit.MILLISECONDS).name("PlotBorderVisualizer").submit(this);
    }

    private void showPlot(Plot plot, Player player){
        ParticleEffect.Builder pe_builder =  game.getRegistry().createBuilder(ParticleEffect.Builder.class);
        World world = player.getWorld();
        for(PlotLine plotLine: plot.getBorderBlocksLines()){
            player.spawnParticles(pe_builder.type(ParticleTypes.FIREWORKS_SPARK).motion(new Vector3d(0,-0.1,0)).count(1).build(), world.getLocation(plotLine.getP1().getX(), player.getLocation().getBlockY()+3, plotLine.getP1().getZ()).getPosition(), 10);
            double x_m = (plotLine.getP1().getX() + plotLine.getP2().getX())/2;
            double z_m = (plotLine.getP1().getZ() + plotLine.getP2().getZ())/2;
            player.spawnParticles(pe_builder.type(ParticleTypes.FIREWORKS_SPARK).motion(new Vector3d(0,-0.1,0)).count(1).build(), world.getLocation(x_m, player.getLocation().getBlockY()+3, z_m).getPosition(), 10);
        }
        for(PlotPoint vertex: plot.getVertices()){
            Location<World> currentLocation = world.getLocation(vertex.getX() + 0.5, player.getLocation().getBlockY()-1.5, vertex.getZ() + 0.5);
            for(int i = -5; i <= 5; i++){
                player.spawnParticles(pe_builder.type(ParticleTypes.SMOKE_LARGE).motion(new Vector3d(0,0.01,0)).count(1).build(), currentLocation.getPosition().add(0,i,0), 10);
            }
        }
    }

    public void updateScoreboard(Plot plot, Player player){
        Scoreboard scoreboard = player.getScoreboard();
        Optional<Objective> optObjective = scoreboard.getObjective("plot");
        if(optObjective.isPresent()) scoreboard.removeObjective(optObjective.get());

        Objective obj = game.getRegistry().createBuilder(Objective.Builder.class).name("plot").criterion(Criteria.DUMMY).objectiveDisplayMode(ObjectiveDisplayModes.INTEGER).displayName(Texts.of(TextColors.YELLOW, "Plot Editor")).build();
        for(PlotPoint vertex: plot.getVertices()){
            if(vertex.equals(plot.getVertices().get(plot.getVertices().size() - 1) ) && !plot.isComplete() ){
                obj.getScore(Texts.of(TextColors.RED, vertex.toString())).setScore(0);
            }else{
                obj.getScore(Texts.of(TextColors.GREEN, vertex.toString())).setScore(0);
            }
        }
        scoreboard.addObjective(obj);
        scoreboard.addObjective(obj, DisplaySlots.SIDEBAR);
    }

    public void clearScoreboard(Player player){
        Scoreboard scoreboard = player.getScoreboard();
        Optional<Objective> optObjective = scoreboard.getObjective("plot");
        if(optObjective.isPresent()) scoreboard.removeObjective(optObjective.get());
    }

}