/*
 * WorldGuard, a suite of tools for Minecraft
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldGuard team and contributors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.sk89q.worldguard.commands.region;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.sk89q.minecraft.util.commands.Command;
import com.sk89q.minecraft.util.commands.CommandContext;
import com.sk89q.minecraft.util.commands.CommandException;
import com.sk89q.minecraft.util.commands.CommandPermissionsException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.command.util.AsyncCommandBuilder;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extension.platform.Capability;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.util.formatting.component.ErrorFormat;
import com.sk89q.worldedit.util.formatting.component.LabelFormat;
import com.sk89q.worldedit.util.formatting.component.SubtleFormat;
import com.sk89q.worldedit.util.formatting.text.Component;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.formatting.text.event.ClickEvent;
import com.sk89q.worldedit.util.formatting.text.event.HoverEvent;
import com.sk89q.worldedit.util.formatting.text.format.TextColor;
import com.sk89q.worldedit.util.formatting.text.format.TextDecoration;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.gamemode.GameModes;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.commands.task.RegionAdder;
import com.sk89q.worldguard.commands.task.RegionLister;
import com.sk89q.worldguard.commands.task.RegionManagerLoader;
import com.sk89q.worldguard.commands.task.RegionManagerSaver;
import com.sk89q.worldguard.commands.task.RegionRemover;
import com.sk89q.worldguard.config.ConfigurationManager;
import com.sk89q.worldguard.config.WorldConfiguration;
import com.sk89q.worldguard.internal.permission.RegionPermissionModel;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.FlagValueCalculator;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.FlagContext;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.InvalidFlagFormat;
import com.sk89q.worldguard.protection.flags.RegionGroup;
import com.sk89q.worldguard.protection.flags.RegionGroupFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.RemovalStrategy;
import com.sk89q.worldguard.protection.managers.migration.DriverMigration;
import com.sk89q.worldguard.protection.managers.migration.MigrationException;
import com.sk89q.worldguard.protection.managers.migration.UUIDMigration;
import com.sk89q.worldguard.protection.managers.migration.WorldHeightMigration;
import com.sk89q.worldguard.protection.managers.storage.DriverType;
import com.sk89q.worldguard.protection.managers.storage.RegionDriver;
import com.sk89q.worldguard.protection.regions.GlobalProtectedRegion;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion.CircularInheritanceException;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.util.DomainInputResolver.UserLocatorPolicy;
import com.sk89q.worldguard.protection.util.WorldEditRegionConverter;
import com.sk89q.worldguard.session.Session;
import com.sk89q.worldguard.util.Enums;
import com.sk89q.worldguard.util.logging.LoggerToChatHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Implements the /region commands for WorldGuard.
 */
public final class RegionCommands extends RegionCommandsBase {

    private static final Logger log = Logger.getLogger(RegionCommands.class.getCanonicalName());
    private final WorldGuard worldGuard;

    public RegionCommands(WorldGuard worldGuard) {
        checkNotNull(worldGuard);
        this.worldGuard = worldGuard;
    }

    private static TextComponent passthroughFlagWarning = TextComponent.empty()
            .append(TextComponent.of("UPOZORN??N??:", TextColor.RED, Sets.newHashSet(TextDecoration.BOLD)))
            .append(ErrorFormat.wrap(" Tato zna??ka nesouvis?? s pohybem po regionech."))
            .append(TextComponent.newline())
            .append(TextComponent.of("P??ep????e kontroly stav??n??. Pokud si nejsi jist??, co to znamen??, otev??i ")
                    .append(TextComponent.of("[dokumentace (zat??m v ANJ)]", TextColor.AQUA)
                            .clickEvent(ClickEvent.of(ClickEvent.Action.OPEN_URL,
                                    "https://worldguard.enginehub.org/en/latest/regions/flags/#overrides")))
                    .append(TextComponent.of(" pro v??ce informac??.")));
    private static TextComponent buildFlagWarning = TextComponent.empty()
            .append(TextComponent.of("UPOZORN??N??:", TextColor.RED, Sets.newHashSet(TextDecoration.BOLD)))
            .append(ErrorFormat.wrap(" Nastaven?? t??to zna??ky nen?? nutn?? pro ochranu."))
            .append(TextComponent.newline())
            .append(TextComponent.of("Nastaven?? tohoto p????znaku zcela p??ep????e v??choz?? ochranu a pou??ije se na ??leny, " +
                    " ne??leny, p??sty, fyziku p??sku a v??e ostatn??, co m????e upravovat bloky."))
            .append(TextComponent.newline())
            .append(TextComponent.of("Tuto zna??ku nastav pouze tehdy, pokud si jsi jist??, ??e v????, co d??l????. Koukni se do ")
                    .append(TextComponent.of("[dokumentace (zat??m v ANJ)]", TextColor.AQUA)
                            .clickEvent(ClickEvent.of(ClickEvent.Action.OPEN_URL,
                                    "https://worldguard.enginehub.org/en/latest/regions/flags/#protection-related")))
                    .append(TextComponent.of(" pro v??ce informac??.")));

    /**
     * Defines a new region.
     * 
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"define", "def", "d", "create"},
             usage = "[-w <sv??t>] <id> [<majitel1> [<majitel2> [<majitel??...>]]]",
             flags = "ngw:",
             desc = "Definuje region",
             min = 1)
    public void define(CommandContext args, Actor sender) throws CommandException {
        warnAboutSaveFailures(sender);

        // Check permissions
        if (!getPermissionModel(sender).mayDefine()) {
            throw new CommandPermissionsException();
        }

        String id = checkRegionId(args.getString(0), false);

        World world = checkWorld(args, sender, 'w');
        RegionManager manager = checkRegionManager(world);

        checkRegionDoesNotExist(manager, id, true);

        ProtectedRegion region;

        if (args.hasFlag('g')) {
            region = new GlobalProtectedRegion(id);
        } else {
            region = checkRegionFromSelection(sender, id);
        }

        RegionAdder task = new RegionAdder(manager, region);
        task.addOwnersFromCommand(args, 2);

        final String description = String.format("P??id??v??m region '%s'", region.getId());
        AsyncCommandBuilder.wrap(task, sender)
                .registerWithSupervisor(worldGuard.getSupervisor(), description)
                .onSuccess((Component) null,
                        t -> {
                            sender.print(String.format("Nov?? region byl pojmenov??n '%s'.", region.getId()));
                            warnAboutDimensions(sender, region);
                            informNewUser(sender, manager, region);
                            checkSpawnOverlap(sender, world, region);
                        })
                .onFailure(String.format("Region se nepoda??ilo vytvo??it '%s'", region.getId()), worldGuard.getExceptionConverter())
                .buildAndExec(worldGuard.getExecutorService());
    }

    /**
     * Re-defines a region with a new selection.
     *
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"redefine", "update", "move"},
             usage = "[-w <sv??t>] <id>",
             desc = "Znovu definuje tvar oblasti",
             flags = "gw:",
             min = 1, max = 1)
    public void redefine(CommandContext args, Actor sender) throws CommandException {
        warnAboutSaveFailures(sender);

        String id = checkRegionId(args.getString(0), false);

        World world = checkWorld(args, sender, 'w');
        RegionManager manager = checkRegionManager(world);

        ProtectedRegion existing = checkExistingRegion(manager, id, false);

        // Check permissions
        if (!getPermissionModel(sender).mayRedefine(existing)) {
            throw new CommandPermissionsException();
        }

        ProtectedRegion region;

        if (args.hasFlag('g')) {
            region = new GlobalProtectedRegion(id);
        } else {
            region = checkRegionFromSelection(sender, id);
        }

        region.copyFrom(existing);

        RegionAdder task = new RegionAdder(manager, region);

        final String description = String.format("Aktualizuji region '%s'", region.getId());
        AsyncCommandBuilder.wrap(task, sender)
                .registerWithSupervisor(worldGuard.getSupervisor(), description)
                .sendMessageAfterDelay("(Pros??m po??kej... " + description + ")")
                .onSuccess((Component) null,
                        t -> {
                            sender.print(String.format("Region '%s' byl aktualizov??n novou oblast??.", region.getId()));
                            warnAboutDimensions(sender, region);
                            informNewUser(sender, manager, region);
                            checkSpawnOverlap(sender, world, region);
                        })
                .onFailure(String.format("Nepoda??ilo se aktualizovat region '%s'", region.getId()), worldGuard.getExceptionConverter())
                .buildAndExec(worldGuard.getExecutorService());
    }

    /**
     * Claiming command for users.
     *
     * <p>This command is a joke and it needs to be rewritten. It was contributed
     * code :(</p>
     *
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"claim"},
             usage = "<id>",
             desc = "Zaber si region",
             min = 1, max = 1)
    public void claim(CommandContext args, Actor sender) throws CommandException {
        warnAboutSaveFailures(sender);

        LocalPlayer player = worldGuard.checkPlayer(sender);
        RegionPermissionModel permModel = getPermissionModel(player);

        // Check permissions
        if (!permModel.mayClaim()) {
            throw new CommandPermissionsException();
        }

        String id = checkRegionId(args.getString(0), false);

        RegionManager manager = checkRegionManager(player.getWorld());

        checkRegionDoesNotExist(manager, id, false);
        ProtectedRegion region = checkRegionFromSelection(player, id);

        WorldConfiguration wcfg = WorldGuard.getInstance().getPlatform().getGlobalStateManager().get(player.getWorld());

        // Check whether the player has created too many regions
        if (!permModel.mayClaimRegionsUnbounded()) {
            int maxRegionCount = wcfg.getMaxRegionCount(player);
            if (maxRegionCount >= 0
                    && manager.getRegionCountOfPlayer(player) >= maxRegionCount) {
                throw new CommandException(
                        "Vlastn???? p????li?? mnoho region??, sma?? nejprve jeden, abys z??skal nov??.");
            }
        }

        ProtectedRegion existing = manager.getRegion(id);

        // Check for an existing region
        if (existing != null) {
            if (!existing.getOwners().contains(player)) {
                throw new CommandException(
                        "Tento region ji?? existuje a vy ji nevlastn??te.");
            }
        }

        // We have to check whether this region violates the space of any other region
        ApplicableRegionSet regions = manager.getApplicableRegions(region);

        // Check if this region overlaps any other region
        if (regions.size() > 0) {
            if (!regions.isOwnerOfAll(player)) {
                throw new CommandException("Tato oblast se p??ekr??v?? s oblast?? n??koho jin??ho.");
            }
        } else {
            if (wcfg.claimOnlyInsideExistingRegions) {
                throw new CommandException("N??rokovat si m????e?? pouze regiony uvnit?? " +
                        "existuj??c??ch region??, kter?? vlastn???? ty nebo va??e skupina.");
            }
        }

        if (wcfg.maxClaimVolume >= Integer.MAX_VALUE) {
            throw new CommandException("The maximum claim volume get in the configuration is higher than is supported. " +
                    "Currently, it must be " + Integer.MAX_VALUE + " or smaller. Please contact a server administrator.");
        }

        // Check claim volume
        if (!permModel.mayClaimRegionsUnbounded()) {
            if (region instanceof ProtectedPolygonalRegion) {
                throw new CommandException("Polygons are currently not supported for /rg claim.");
            }

            if (region.volume() > wcfg.maxClaimVolume) {
                player.printError("Tento region je p????li?? velk?? na to, aby se dal zabrat.");
                player.printError("Maxim??ln?? velikost: " + wcfg.maxClaimVolume + ", tv?? velikost: " + region.volume());
                return;
            }
        }

        // Inherit from a template region
        if (!Strings.isNullOrEmpty(wcfg.setParentOnClaim)) {
            ProtectedRegion templateRegion = manager.getRegion(wcfg.setParentOnClaim);
            if (templateRegion != null) {
                try {
                    region.setParent(templateRegion);
                } catch (CircularInheritanceException e) {
                    throw new CommandException(e.getMessage());
                }
            }
        }

        region.getOwners().addPlayer(player);
        manager.addRegion(region);
        player.print(TextComponent.of(String.format("Nov?? region byl zabr??n se jm??nem '%s'.", id)));
    }

    /**
     * Get a WorldEdit selection from a region.
     *
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"select", "sel", "s"},
             usage = "[-w <sv??t>] [id]",
             desc = "Na????st region jako WorldEdit selekci ",
             min = 0, max = 1,
             flags = "w:")
    public void select(CommandContext args, Actor sender) throws CommandException {
        World world = checkWorld(args, sender, 'w');
        RegionManager manager = checkRegionManager(world);
        ProtectedRegion existing;

        // If no arguments were given, get the region that the player is inside
        if (args.argsLength() == 0) {
            LocalPlayer player = worldGuard.checkPlayer(sender);
            if (!player.getWorld().equals(world)) { // confusing to get current location regions in another world
                throw new CommandException("Specifikuj pros??m jm??no."); // just don't allow that
            }
            world = player.getWorld();
            existing = checkRegionStandingIn(manager, player, "/rg select -w \"" + world.getName() + "\" %id%");
        } else {
            existing = checkExistingRegion(manager, args.getString(0), false);
        }

        // Check permissions
        if (!getPermissionModel(sender).maySelect(existing)) {
            throw new CommandPermissionsException();
        }

        // Select
        setPlayerSelection(sender, existing, world);
    }

    /**
     * Get information about a region.
     *
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"info", "i"},
             usage = "[id]",
             flags = "usw:",
             desc = "Z??skej informace o regionu",
             min = 0, max = 1)
    public void info(CommandContext args, Actor sender) throws CommandException {
        warnAboutSaveFailures(sender);

        World world = checkWorld(args, sender, 'w'); // Get the world
        RegionPermissionModel permModel = getPermissionModel(sender);

        // Lookup the existing region
        RegionManager manager = checkRegionManager(world);
        ProtectedRegion existing;

        if (args.argsLength() == 0) { // Get region from where the player is
            if (!(sender instanceof LocalPlayer)) {
                throw new CommandException("Pros??m, specifikuj region p????kazem /region info -w n??zev_sv??ta n??zev_regionu.");
            }

            existing = checkRegionStandingIn(manager, (LocalPlayer) sender, true,
                    "/rg info -w \"" + world.getName() + "\" %id%" + (args.hasFlag('u') ? " -u" : "") + (args.hasFlag('s') ? " -s" : ""));
        } else { // Get region from the ID
            existing = checkExistingRegion(manager, args.getString(0), true);
        }

        // Check permissions
        if (!permModel.mayLookup(existing)) {
            throw new CommandPermissionsException();
        }

        // Let the player select the region
        if (args.hasFlag('s')) {
            // Check permissions
            if (!permModel.maySelect(existing)) {
                throw new CommandPermissionsException();
            }

            setPlayerSelection(worldGuard.checkPlayer(sender), existing, world);
        }

        // Print region information
        RegionPrintoutBuilder printout = new RegionPrintoutBuilder(world.getName(), existing,
                args.hasFlag('u') ? null : WorldGuard.getInstance().getProfileCache(), sender);

        AsyncCommandBuilder.wrap(printout, sender)
                .registerWithSupervisor(WorldGuard.getInstance().getSupervisor(), "Na????t??n?? informac?? o regionu")
                .sendMessageAfterDelay("(Pros??m po??kej... Na????t??m informace o regionu...)")
                .onSuccess((Component) null, component -> {
                    sender.print(component);
                    checkSpawnOverlap(sender, world, existing);
                })
                .onFailure("Nepoda??ilo se na????st informace o regionu", WorldGuard.getInstance().getExceptionConverter())
                .buildAndExec(WorldGuard.getInstance().getExecutorService());
    }

    /**
     * List regions.
     *
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"list"},
             usage = "[-w sv??t] [-p majitel [-n]] [-s] [-i filtr] [strana]",
             desc = "Vyp????e seznam region??",
             flags = "np:w:i:s",
             max = 1)
    public void list(CommandContext args, Actor sender) throws CommandException {
        warnAboutSaveFailures(sender);

        World world = checkWorld(args, sender, 'w'); // Get the world
        String ownedBy;

        // Get page
        int page = args.getInteger(0, 1);
        if (page < 1) {
            page = 1;
        }

        // -p flag to lookup a player's regions
        if (args.hasFlag('p')) {
            ownedBy = args.getFlag('p');
        } else {
            ownedBy = null; // List all regions
        }

        // Check permissions
        if (!getPermissionModel(sender).mayList(ownedBy)) {
            ownedBy = sender.getName(); // assume they only want their own
            if (!getPermissionModel(sender).mayList(ownedBy)) {
                throw new CommandPermissionsException();
            }
        }

        RegionManager manager = checkRegionManager(world);

        RegionLister task = new RegionLister(manager, sender, world.getName());
        task.setPage(page);
        if (ownedBy != null) {
            task.filterOwnedByName(ownedBy, args.hasFlag('n'));
        }

        if (args.hasFlag('s')) {
            ProtectedRegion existing = checkRegionFromSelection(sender, "tmp");
            task.filterByIntersecting(existing);
        }

        // -i string is in region id
        if (args.hasFlag('i')) {
            task.filterIdByMatch(args.getFlag('i'));
        }

        AsyncCommandBuilder.wrap(task, sender)
                .registerWithSupervisor(WorldGuard.getInstance().getSupervisor(), "Na????t??n?? seznamu region??")
                .sendMessageAfterDelay("(Pros??m po??kej... Na????t??m seznam region??...)")
                .onFailure("Nepoda??ilo se na????st seznam region??", WorldGuard.getInstance().getExceptionConverter())
                .buildAndExec(WorldGuard.getInstance().getExecutorService());
    }

    /**
     * Set a flag.
     *
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"flag", "f"},
             usage = "<id> <zna??ka> [-w sv??t] [-g skupina] [hodnota]",
             flags = "g:w:eh:",
             desc = "Nastav zna??ky",
             min = 2)
    public void flag(CommandContext args, Actor sender) throws CommandException {
        warnAboutSaveFailures(sender);

        World world = checkWorld(args, sender, 'w'); // Get the world
        String flagName = args.getString(1);
        String value = args.argsLength() >= 3 ? args.getJoinedStrings(2) : null;
        RegionGroup groupValue = null;
        FlagRegistry flagRegistry = WorldGuard.getInstance().getFlagRegistry();
        RegionPermissionModel permModel = getPermissionModel(sender);

        if (args.hasFlag('e')) {
            if (value != null) {
                throw new CommandException("Nem????e?? pou????t -e(pr??zdn??) jako hodnotu zna??ky.");
            }

            value = "";
        }

        // Lookup the existing region
        RegionManager manager = checkRegionManager(world);
        ProtectedRegion existing = checkExistingRegion(manager, args.getString(0), true);

        // Check permissions
        if (!permModel.maySetFlag(existing)) {
            throw new CommandPermissionsException();
        }
        String regionId = existing.getId();

        Flag<?> foundFlag = Flags.fuzzyMatchFlag(flagRegistry, flagName);

        // We didn't find the flag, so let's print a list of flags that the user
        // can use, and do nothing afterwards
        if (foundFlag == null) {
            AsyncCommandBuilder.wrap(new FlagListBuilder(flagRegistry, permModel, existing, world,
                                                         regionId, sender, flagName), sender)
                    .registerWithSupervisor(WorldGuard.getInstance().getSupervisor(), "Seznam zna??ek pro nezn??m?? p????kaz zna??ky.")
                    .onSuccess((Component) null, sender::print)
                    .onFailure((Component) null, WorldGuard.getInstance().getExceptionConverter())
                    .buildAndExec(WorldGuard.getInstance().getExecutorService());
            return;
        } else if (value != null) {
            if (foundFlag == Flags.BUILD || foundFlag == Flags.BLOCK_BREAK || foundFlag == Flags.BLOCK_PLACE) {
                sender.print(buildFlagWarning);
                if (!sender.isPlayer()) {
                    sender.printRaw("https://worldguard.enginehub.org/en/latest/regions/flags/#protection-related");
                }
            } else if (foundFlag == Flags.PASSTHROUGH) {
                sender.print(passthroughFlagWarning);
                if (!sender.isPlayer()) {
                    sender.printRaw("https://worldguard.enginehub.org/en/latest/regions/flags/#overrides");
                }
            }
        }

        // Also make sure that we can use this flag
        // This permission is confusing and probably should be replaced, but
        // but not here -- in the model
        if (!permModel.maySetFlag(existing, foundFlag, value)) {
            throw new CommandPermissionsException();
        }

        // -g for group flag
        if (args.hasFlag('g')) {
            String group = args.getFlag('g');
            RegionGroupFlag groupFlag = foundFlag.getRegionGroupFlag();

            if (groupFlag == null) {
                throw new CommandException("Zna??ky regionu '" + foundFlag.getName()
                        + "' nemaj?? skupinovou zna??ku!");
            }

            // Parse the [-g group] separately so entire command can abort if parsing
            // the [value] part throws an error.
            try {
                groupValue = groupFlag.parseInput(FlagContext.create().setSender(sender).setInput(group).setObject("region", existing).build());
            } catch (InvalidFlagFormat e) {
                throw new CommandException(e.getMessage());
            }

        }

        // Set the flag value if a value was set
        if (value != null) {
            // Set the flag if [value] was given even if [-g group] was given as well
            try {
                value = setFlag(existing, foundFlag, sender, value).toString();
            } catch (InvalidFlagFormat e) {
                throw new CommandException(e.getMessage());
            }

            if (!args.hasFlag('h')) {
                sender.print("Zna??ka " + foundFlag.getName() + " nastaven?? v regionu '" + regionId + "' na hodnotu '" + value + "'.");
            }

        // No value? Clear the flag, if -g isn't specified
        } else if (!args.hasFlag('g')) {
            // Clear the flag only if neither [value] nor [-g group] was given
            existing.setFlag(foundFlag, null);

            // Also clear the associated group flag if one exists
            RegionGroupFlag groupFlag = foundFlag.getRegionGroupFlag();
            if (groupFlag != null) {
                existing.setFlag(groupFlag, null);
            }

            if (!args.hasFlag('h')) {
                sender.print("Zna??ka " + foundFlag.getName() + " byla odstran??na z regionu '" + regionId + "'. (V??echny -g(skupiny) byly tak?? odstran??ny.)");
            }
        }

        // Now set the group
        if (groupValue != null) {
            RegionGroupFlag groupFlag = foundFlag.getRegionGroupFlag();

            // If group set to the default, then clear the group flag
            if (groupValue == groupFlag.getDefault()) {
                existing.setFlag(groupFlag, null);
                sender.print("Zna??ka skupiny regionu '" + foundFlag.getName() + "' byla resetov??na na v??choz??.");
            } else {
                existing.setFlag(groupFlag, groupValue);
                sender.print("Zna??ka skupiny regionu '" + foundFlag.getName() + "' byla nastavena.");
            }
        }

        // Print region information
        if (args.hasFlag('h')) {
            int page = args.getFlagInteger('h');
            sendFlagHelper(sender, world, existing, permModel, page);
        } else {
            RegionPrintoutBuilder printout = new RegionPrintoutBuilder(world.getName(), existing, null, sender);
            printout.append(SubtleFormat.wrap("(Nyn??j???? zna??ky: "));
            printout.appendFlagsList(false);
            printout.append(SubtleFormat.wrap(")"));
            printout.send(sender);
            checkSpawnOverlap(sender, world, existing);
        }
    }

    @Command(aliases = "flags",
             usage = "[-p <str??nka>] [id]",
             flags = "p:w:",
             desc = "Zobraz?? zna??ky regionu",
             min = 0, max = 2)
    public void flagHelper(CommandContext args, Actor sender) throws CommandException {
        World world = checkWorld(args, sender, 'w'); // Get the world

        // Lookup the existing region
        RegionManager manager = checkRegionManager(world);
        ProtectedRegion region;
        if (args.argsLength() == 0) { // Get region from where the player is
            if (!(sender instanceof LocalPlayer)) {
                throw new CommandException("Pros??m specifikuj regionu p????kazem /region flags -w n??zev_sv??ta n??zev_regionu.");
            }

            region = checkRegionStandingIn(manager, (LocalPlayer) sender, true,
                    "/rg flags -w \"" + world.getName() + "\" %id%");
        } else { // Get region from the ID
            region = checkExistingRegion(manager, args.getString(0), true);
        }

        final RegionPermissionModel perms = getPermissionModel(sender);
        if (!perms.mayLookup(region)) {
            throw new CommandPermissionsException();
        }
        int page = args.hasFlag('p') ? args.getFlagInteger('p') : 1;

        sendFlagHelper(sender, world, region, perms, page);
    }

    private static void sendFlagHelper(Actor sender, World world, ProtectedRegion region, RegionPermissionModel perms, int page) {
        final FlagHelperBox flagHelperBox = new FlagHelperBox(world, region, perms);
        flagHelperBox.setComponentsPerPage(18);
        if (!sender.isPlayer()) {
            flagHelperBox.tryMonoSpacing();
        }
        AsyncCommandBuilder.wrap(() -> {
                    if (checkSpawnOverlap(sender, world, region)) {
                        flagHelperBox.setComponentsPerPage(15);
                    }
                    return flagHelperBox.create(page);
                }, sender)
                .onSuccess((Component) null, sender::print)
                .onFailure("Nepoda??ilo se na????st zna??ky regionu", WorldGuard.getInstance().getExceptionConverter())
                .buildAndExec(WorldGuard.getInstance().getExecutorService());
    }

    /**
     * Set the priority of a region.
     *
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"setpriority", "priority", "pri"},
             usage = "<id> <priorita>",
             flags = "w:",
             desc = "Nastav?? prioritu regionu",
             min = 2, max = 2)
    public void setPriority(CommandContext args, Actor sender) throws CommandException {
        warnAboutSaveFailures(sender);

        World world = checkWorld(args, sender, 'w'); // Get the world
        int priority = args.getInteger(1);

        // Lookup the existing region
        RegionManager manager = checkRegionManager(world);
        ProtectedRegion existing = checkExistingRegion(manager, args.getString(0), false);

        // Check permissions
        if (!getPermissionModel(sender).maySetPriority(existing)) {
            throw new CommandPermissionsException();
        }

        existing.setPriority(priority);

        sender.print("Priorita regionu '" + existing.getId() + "' nastavena na " + priority + " (vy?????? ????sla p??episuj??).");
        checkSpawnOverlap(sender, world, existing);
    }

    /**
     * Set the parent of a region.
     *
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"setparent", "parent", "par"},
             usage = "<id> [id-nad??azen??ho-regionu]",
             flags = "w:",
             desc = "Nastavit nad??azen?? region",
             min = 1, max = 2)
    public void setParent(CommandContext args, Actor sender) throws CommandException {
        warnAboutSaveFailures(sender);

        World world = checkWorld(args, sender, 'w'); // Get the world
        ProtectedRegion parent;
        ProtectedRegion child;

        // Lookup the existing region
        RegionManager manager = checkRegionManager(world);

        // Get parent and child
        child = checkExistingRegion(manager, args.getString(0), false);
        if (args.argsLength() == 2) {
            parent = checkExistingRegion(manager, args.getString(1), false);
        } else {
            parent = null;
        }

        // Check permissions
        if (!getPermissionModel(sender).maySetParent(child, parent)) {
            throw new CommandPermissionsException();
        }

        try {
            child.setParent(parent);
        } catch (CircularInheritanceException e) {
            // Tell the user what's wrong
            RegionPrintoutBuilder printout = new RegionPrintoutBuilder(world.getName(), parent, null, sender);
            assert parent != null;
            printout.append(ErrorFormat.wrap("Ajaj... Nastaven?? regionu '", parent.getId(), "' jako nad??azen?? pro region '", child.getId(),
                    "' m????e zp??sobit cyklick?? d??d??n??.")).newline();
            printout.append(SubtleFormat.wrap("(Aktu??ln?? d??d??n?? regionu '", parent.getId(), "':")).newline();
            printout.appendParentTree(true);
            printout.append(SubtleFormat.wrap(")"));
            printout.send(sender);
            return;
        }

        // Tell the user the current inheritance
        RegionPrintoutBuilder printout = new RegionPrintoutBuilder(world.getName(), child, null, sender);
        printout.append(TextComponent.of("Nov?? nastaven?? d??d??n?? pro region '" + child.getId() + "':", TextColor.LIGHT_PURPLE));
        if (parent != null) {
            printout.newline();
            printout.append(SubtleFormat.wrap("(Aktu??ln?? d??d??n??:")).newline();
            printout.appendParentTree(true);
            printout.append(SubtleFormat.wrap(")"));
        } else {
            printout.append(LabelFormat.wrap(" Region ji?? ned??d??."));
        }
        printout.send(sender);
    }

    /**
     * Remove a region.
     *
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"remove", "delete", "del", "rem"},
             usage = "<id>",
             flags = "fuw:",
             desc = "Odstran?? region",
             min = 1, max = 1)
    public void remove(CommandContext args, Actor sender) throws CommandException {
        warnAboutSaveFailures(sender);

        World world = checkWorld(args, sender, 'w'); // Get the world
        boolean removeChildren = args.hasFlag('f');
        boolean unsetParent = args.hasFlag('u');

        // Lookup the existing region
        RegionManager manager = checkRegionManager(world);
        ProtectedRegion existing = checkExistingRegion(manager, args.getString(0), true);

        // Check permissions
        if (!getPermissionModel(sender).mayDelete(existing)) {
            throw new CommandPermissionsException();
        }

        RegionRemover task = new RegionRemover(manager, existing);

        if (removeChildren && unsetParent) {
            throw new CommandException("Nem????e?? pou????t -u (odebrat nad??azen??) a -f (odebrat d??d??n??) dohromady.");
        } else if (removeChildren) {
            task.setRemovalStrategy(RemovalStrategy.REMOVE_CHILDREN);
        } else if (unsetParent) {
            task.setRemovalStrategy(RemovalStrategy.UNSET_PARENT_IN_CHILDREN);
        }

        final String description = String.format("Odstra??uji region '%s' ve sv??t?? '%s'", existing.getId(), world.getName());
        AsyncCommandBuilder.wrap(task, sender)
                .registerWithSupervisor(WorldGuard.getInstance().getSupervisor(), description)
                .sendMessageAfterDelay("Pros??m po??kej... Odeb??r??m region.")
                .onSuccess((Component) null, removed -> sender.print(TextComponent.of(
                        "??sp????n?? odebr??n region " + removed.stream().map(ProtectedRegion::getId).collect(Collectors.joining(", ")) + ".",
                        TextColor.LIGHT_PURPLE)))
                .onFailure("Nepoda??ilo se odebrat region", WorldGuard.getInstance().getExceptionConverter())
                .buildAndExec(WorldGuard.getInstance().getExecutorService());
    }

    /**
     * Reload the region database.
     *
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"load", "reload"},
            usage = "[world]",
            desc = "Znovu na??te region ze souboru",
            flags = "w:")
    public void load(CommandContext args, final Actor sender) throws CommandException {
        warnAboutSaveFailures(sender);

        World world = null;
        try {
            world = checkWorld(args, sender, 'w'); // Get the world
        } catch (CommandException ignored) {
            // assume the user wants to reload all worlds
        }

        // Check permissions
        if (!getPermissionModel(sender).mayForceLoadRegions()) {
            throw new CommandPermissionsException();
        }

        if (world != null) {
            RegionManager manager = checkRegionManager(world);

            if (manager == null) {
                throw new CommandException("Neexistuje ????dn?? mana??er region?? pro sv??t '" + world.getName() + "'.");
            }

            final String description = String.format("Na????t??m data region?? pro sv??t '%s'.", world.getName());
            AsyncCommandBuilder.wrap(new RegionManagerLoader(manager), sender)
                    .registerWithSupervisor(worldGuard.getSupervisor(), description)
                    .sendMessageAfterDelay("Pros??m po??kej... " + description)
                    .onSuccess(String.format("Na??etl jsem data region?? pro sv??t '%s'", world.getName()), null)
                    .onFailure(String.format("Nepovedlo se na????st data region?? pro sv??t '%s'", world.getName()), worldGuard.getExceptionConverter())
                    .buildAndExec(worldGuard.getExecutorService());
        } else {
            // Load regions for all worlds
            List<RegionManager> managers = new ArrayList<>();

            for (World w : WorldEdit.getInstance().getPlatformManager().queryCapability(Capability.GAME_HOOKS).getWorlds()) {
                RegionManager manager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(w);
                if (manager != null) {
                    managers.add(manager);
                }
            }

            AsyncCommandBuilder.wrap(new RegionManagerLoader(managers), sender)
                    .registerWithSupervisor(worldGuard.getSupervisor(), "Na????t??m regiony ze v??ech sv??t??")
                    .sendMessageAfterDelay("(Pros??m po??kej... Na????t??m data region?? ze v??ech sv??t??...)")
                    .onSuccess("Na??etly se data region?? ze v??ech sv??t?? .", null)
                    .onFailure("Nepovedlo se na????st data region?? ze v??ech sv??t??", worldGuard.getExceptionConverter())
                    .buildAndExec(worldGuard.getExecutorService());
        }
    }

    /**
     * Re-save the region database.
     *
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"save", "write"},
            usage = "[sv??t]",
            desc = "Znovu ulo?? regiony do souboru",
            flags = "w:")
    public void save(CommandContext args, final Actor sender) throws CommandException {
        warnAboutSaveFailures(sender);

        World world = null;
        try {
            world = checkWorld(args, sender, 'w'); // Get the world
        } catch (CommandException ignored) {
            // assume user wants to save all worlds
        }

        // Check permissions
        if (!getPermissionModel(sender).mayForceSaveRegions()) {
            throw new CommandPermissionsException();
        }

        if (world != null) {
            RegionManager manager = checkRegionManager(world);

            if (manager == null) {
                throw new CommandException("Neexistuje ????dn?? mana??er region?? pro sv??t '" + world.getName() + "'.");
            }

            final String description = String.format("Ukl??d??m data region?? pro sv??t '%s'.", world.getName());
            AsyncCommandBuilder.wrap(new RegionManagerSaver(manager), sender)
                    .registerWithSupervisor(worldGuard.getSupervisor(), description)
                    .sendMessageAfterDelay("Pros??m po??kej... " + description)
                    .onSuccess(String.format("Ukl??d??m data region?? pro sv??t '%s'", world.getName()), null)
                    .onFailure(String.format("Nepoda??ilo se na????st data region?? pro sv??t '%s'", world.getName()), worldGuard.getExceptionConverter())
                    .buildAndExec(worldGuard.getExecutorService());
        } else {
            // Save for all worlds
            List<RegionManager> managers = new ArrayList<>();

            final RegionContainer regionContainer = worldGuard.getPlatform().getRegionContainer();
            for (World w : WorldEdit.getInstance().getPlatformManager().queryCapability(Capability.GAME_HOOKS).getWorlds()) {
                RegionManager manager = regionContainer.get(w);
                if (manager != null) {
                    managers.add(manager);
                }
            }

            AsyncCommandBuilder.wrap(new RegionManagerSaver(managers), sender)
                    .registerWithSupervisor(worldGuard.getSupervisor(), "Ukl??d??m regiony ze v??ech sv??t??")
                    .sendMessageAfterDelay("(Pros??m po??kej... Ukl??d??m data region?? ze v??ech sv??t??...)")
                    .onSuccess("Data region?? ze v??ech sv??t?? byly ulo??eny.", null)
                    .onFailure("Nepoda??ilo se ulo??it data region?? ze v??ech sv??t??", worldGuard.getExceptionConverter())
                    .buildAndExec(worldGuard.getExecutorService());
        }
    }

    /**
     * Migrate the region database.
     *
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"migratedb"}, usage = "<z> <do>",
             flags = "y",
             desc = "P??esune z jedn?? datab??ze ochran do jin??.", min = 2, max = 2)
    public void migrateDB(CommandContext args, Actor sender) throws CommandException {
        // Check permissions
        if (!getPermissionModel(sender).mayMigrateRegionStore()) {
            throw new CommandPermissionsException();
        }

        DriverType from = Enums.findFuzzyByValue(DriverType.class, args.getString(0));
        DriverType to = Enums.findFuzzyByValue(DriverType.class, args.getString(1));

        if (from == null) {
            throw new CommandException("Hodnota 'z' nen?? spr??vn??m typem datab??ze dat region??.");
        }

        if (to == null) {
            throw new CommandException("Hodnota 'do' nen?? spr??vn??m typem datab??ze dat region??.");
        }

        if (from == to) {
            throw new CommandException("Nen?? mo??n?? p??esouvat data mezi stejn??mi typy datab??z?? dat region??.");
        }

        if (!args.hasFlag('y')) {
            throw new CommandException("Tento p????klad je nebezpe??n??.\n" +
                    "Ujisti se, ??e jsi provedl z??lohu tv??ch dat, a pot?? znovu zadej p????kaz se zna??kou '-y' na konci, abys mohl pokra??ovat.");
        }

        ConfigurationManager config = WorldGuard.getInstance().getPlatform().getGlobalStateManager();
        RegionDriver fromDriver = config.regionStoreDriverMap.get(from);
        RegionDriver toDriver = config.regionStoreDriverMap.get(to);

        if (fromDriver == null) {
            throw new CommandException("Zd?? se, ??e ovlada?? zadan?? jako 'z' nen?? ve tv?? verzi WorldGuardu podporov??n.");
        }

        if (toDriver == null) {
            throw new CommandException("se, ??e ovlada?? zadan?? jako 'do' nen?? ve tv?? verzi WorldGuardu podporov??n.");
        }

        DriverMigration migration = new DriverMigration(fromDriver, toDriver, WorldGuard.getInstance().getFlagRegistry());

        LoggerToChatHandler handler = null;
        Logger minecraftLogger = null;

        if (sender instanceof LocalPlayer) {
            handler = new LoggerToChatHandler(sender);
            handler.setLevel(Level.ALL);
            minecraftLogger = Logger.getLogger("com.sk89q.worldguard");
            minecraftLogger.addHandler(handler);
        }

        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            sender.print("P??ev??d??m data... toto asi zabere chv??li.");
            container.migrate(migration);
            sender.print(
                    "P??esun byl dokon??en! Toto p??esunulo jen data. Pokud jsi ji?? zm??nil nastaven?? pro pou??it?? " +
                    "c??lov??ho ovlada??e, WorldGuard nyn?? pou????v?? nov?? dat. Pokud ne, mus??te upravit konfiguraci, " +
                    "abyste mohli pou????vat nov?? ovlada??, a pot?? restartovat server.");
        } catch (MigrationException e) {
            log.log(Level.WARNING, "Nepoda??ilo se p??esunout datab??zi", e);
            throw new CommandException("Chyba p??i p??esunu datab??ze: " + e.getMessage());
        } finally {
            if (minecraftLogger != null) {
                minecraftLogger.removeHandler(handler);
            }
        }
    }

    /**
     * Migrate the region databases to use UUIDs rather than name.
     *
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"migrateuuid"},
            desc = "P??evede na??ten?? datab??ze pro pou??it?? UUID", max = 0)
    public void migrateUuid(CommandContext args, Actor sender) throws CommandException {
        // Check permissions
        if (!getPermissionModel(sender).mayMigrateRegionNames()) {
            throw new CommandPermissionsException();
        }

        LoggerToChatHandler handler = null;
        Logger minecraftLogger = null;

        if (sender instanceof LocalPlayer) {
            handler = new LoggerToChatHandler(sender);
            handler.setLevel(Level.ALL);
            minecraftLogger = Logger.getLogger("com.sk89q.worldguard");
            minecraftLogger.addHandler(handler);
        }

        try {
            ConfigurationManager config = WorldGuard.getInstance().getPlatform().getGlobalStateManager();
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionDriver driver = container.getDriver();
            UUIDMigration migration = new UUIDMigration(driver, WorldGuard.getInstance().getProfileService(), WorldGuard.getInstance().getFlagRegistry());
            migration.setKeepUnresolvedNames(config.keepUnresolvedNames);
            sender.print("P??ev??d??m data... toto asi zabere chv??li.");
            container.migrate(migration);
            sender.print("P??evod dokon??en!");
        } catch (MigrationException e) {
            log.log(Level.WARNING, "Nepoda??ilo se p??ev??st data", e);
            throw new CommandException("Chaba p??i p??ev??d??n??: " + e.getMessage());
        } finally {
            if (minecraftLogger != null) {
                minecraftLogger.removeHandler(handler);
            }
        }
    }


    /**
     * Migrate regions that went from 0-255 to new world heights.
     *
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"migrateheights"},
            usage = "[sv??t]", max = 1,
            flags = "yw:",
            desc = "Migruje oblasti ze star??ch v????kov??ch limit?? na nov?? v????kov?? limity")
    public void migrateHeights(CommandContext args, Actor sender) throws CommandException {
        // Check permissions
        if (!getPermissionModel(sender).mayMigrateRegionHeights()) {
            throw new CommandPermissionsException();
        }

        if (!args.hasFlag('y')) {
            throw new CommandException("Tento p????kaz je nebezpe??n??.\n" +
                    "Ujisti se, ??e jsi provedl z??lohu tv??ch dat, a pot?? znovu zadej p????kaz se zna??kou '-y' na konci, abys mohl pokra??ovat.");
        }

        World world = null;
        try {
            world = checkWorld(args, sender, 'w');
        } catch (CommandException ignored) {
        }

        LoggerToChatHandler handler = null;
        Logger minecraftLogger = null;

        if (sender instanceof LocalPlayer) {
            handler = new LoggerToChatHandler(sender);
            handler.setLevel(Level.ALL);
            minecraftLogger = Logger.getLogger("com.sk89q.worldguard");
            minecraftLogger.addHandler(handler);
        }

        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionDriver driver = container.getDriver();
            WorldHeightMigration migration = new WorldHeightMigration(driver, WorldGuard.getInstance().getFlagRegistry(), world);
            container.migrate(migration);
            sender.print("P??evod dokon??en!");
        } catch (MigrationException e) {
            log.log(Level.WARNING, "Nepoda??ilo se p??ev??st data", e);
            throw new CommandException("Chyba p??i p??evodu: " + e.getMessage());
        } finally {
            if (minecraftLogger != null) {
                minecraftLogger.removeHandler(handler);
            }
        }
    }


    /**
     * Teleport to a region
     * 
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"teleport", "tp"},
             usage = "[-w sv??t] [-c|s] <id>",
             flags = "csw:",
             desc = "Teleportuje t?? na m??sto spojen?? s regionem.",
             min = 1, max = 1)
    public void teleport(CommandContext args, Actor sender) throws CommandException {
        LocalPlayer player = worldGuard.checkPlayer(sender);
        Location teleportLocation;

        // Lookup the existing region
        World world = checkWorld(args, player, 'w');
        RegionManager regionManager = checkRegionManager(world);
        ProtectedRegion existing = checkExistingRegion(regionManager, args.getString(0), true);

        // Check permissions
        if (!getPermissionModel(player).mayTeleportTo(existing)) {
            throw new CommandPermissionsException();
        }

        // -s for spawn location
        if (args.hasFlag('s')) {
            teleportLocation = FlagValueCalculator.getEffectiveFlagOf(existing, Flags.SPAWN_LOC, player);
            
            if (teleportLocation == null) {
                throw new CommandException(
                        "Region nem?? ????dn?? bod zrozen??.");
            }
        } else if (args.hasFlag('c')) {
            // Check permissions
            if (!getPermissionModel(player).mayTeleportToCenter(existing)) {
                throw new CommandPermissionsException();
            }
            Region region = WorldEditRegionConverter.convertToRegion(existing);
            if (region == null || region.getCenter() == null) {
                throw new CommandException("Region nem?? ????dn?? centrum.");
            }
            if (player.getGameMode() == GameModes.SPECTATOR) {
                teleportLocation = new Location(world, region.getCenter(), 0, 0);
            } else {
                // TODO: Add some method to create a safe teleport location.
                // The method AbstractPlayerActor$findFreePoisition(Location loc) is no good way for this.
                // It doesn't return the found location and it can't be checked if the location is inside the region.
                throw new CommandException("Teleport na st??ed je dostupn?? pouze v re??imu Div??k.");
            }
        } else {
            teleportLocation = FlagValueCalculator.getEffectiveFlagOf(existing, Flags.TELE_LOC, player);
            
            if (teleportLocation == null) {
                throw new CommandException("Region nem?? p??i??azen?? ????dn?? bod pro teleport.");
            }
        }

        String message = FlagValueCalculator.getEffectiveFlagOf(existing, Flags.TELE_MESSAGE, player);

        // If the flag isn't set, use the default message
        // If message.isEmpty(), no message is sent by LocalPlayer#teleport(...)
        if (message == null) {
            message = Flags.TELE_MESSAGE.getDefault();
        }

        player.teleport(teleportLocation,
                message.replace("%id%", existing.getId()),
                "Nebylo mo??n?? t?? teleportovat do regionu '" + existing.getId() + "'.");
    }

    @Command(aliases = {"toggle-bypass", "bypass"},
             usage = "[zap|vyp]",
             desc = "Toggle region bypassing, effectively ignoring bypass permissions.")
    public void toggleBypass(CommandContext args, Actor sender) throws CommandException {
        LocalPlayer player = worldGuard.checkPlayer(sender);
        if (!player.hasPermission("worldguard.region.toggle-bypass")) {
            throw new CommandPermissionsException();
        }
        Session session = WorldGuard.getInstance().getPlatform().getSessionManager().get(player);
        boolean shouldEnableBypass;
        if (args.argsLength() > 0) {
            String arg1 = args.getString(0);
            if (!arg1.equalsIgnoreCase("zap") && !arg1.equalsIgnoreCase("vyp")) {
                throw new CommandException("Povolen?? voliteln?? mo??nosti jsou: zap, vyp");
            }
            shouldEnableBypass = arg1.equalsIgnoreCase("on");
        } else {
            shouldEnableBypass = session.hasBypassDisabled();
        }
        if (shouldEnableBypass) {
            session.setBypassDisabled(false);
            player.print("Nyn?? obch??z???? ochranu regionu (pokud m???? opr??vn??n??).");
        } else {
            session.setBypassDisabled(true);
            player.print("Ji?? neobch??z???? ochranu regionu.");
        }
    }

    private static class FlagListBuilder implements Callable<Component> {
        private final FlagRegistry flagRegistry;
        private final RegionPermissionModel permModel;
        private final ProtectedRegion existing;
        private final World world;
        private final String regionId;
        private final Actor sender;
        private final String flagName;

        FlagListBuilder(FlagRegistry flagRegistry, RegionPermissionModel permModel, ProtectedRegion existing,
                        World world, String regionId, Actor sender, String flagName) {
            this.flagRegistry = flagRegistry;
            this.permModel = permModel;
            this.existing = existing;
            this.world = world;
            this.regionId = regionId;
            this.sender = sender;
            this.flagName = flagName;
        }

        @Override
        public Component call() {
            ArrayList<String> flagList = new ArrayList<>();

            // Need to build a list
            for (Flag<?> flag : flagRegistry) {
                // Can the user set this flag?
                if (!permModel.maySetFlag(existing, flag)) {
                    continue;
                }

                flagList.add(flag.getName());
            }

            Collections.sort(flagList);

            final TextComponent.Builder builder = TextComponent.builder("Dostupn?? zna??ky: ");

            final HoverEvent clickToSet = HoverEvent.of(HoverEvent.Action.SHOW_TEXT, TextComponent.of("Klikni pro nastaven??"));
            for (int i = 0; i < flagList.size(); i++) {
                String flag = flagList.get(i);

                builder.append(TextComponent.of(flag, i % 2 == 0 ? TextColor.GRAY : TextColor.WHITE)
                        .hoverEvent(clickToSet).clickEvent(ClickEvent.of(ClickEvent.Action.SUGGEST_COMMAND,
                                "/rg flag -w \"" + world.getName() + "\" " + regionId + " " + flag + " ")));
                if (i < flagList.size() + 1) {
                    builder.append(TextComponent.of(", "));
                }
            }

            Component ret = ErrorFormat.wrap("Nezn??m?? zna??ka: " + flagName)
                    .append(TextComponent.newline())
                    .append(builder.build());
            if (sender.isPlayer()) {
                return ret.append(TextComponent.of("Nebo pou??ij p????kaz ", TextColor.LIGHT_PURPLE)
                                .append(TextComponent.of("/rg flags " + regionId, TextColor.AQUA)
                                    .clickEvent(ClickEvent.of(ClickEvent.Action.RUN_COMMAND,
                                        "/rg flags -w \"" + world.getName() + "\" " + regionId))));
            }
            return ret;
        }
    }
}
