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

package com.sk89q.worldguard.commands;

import com.google.common.io.Files;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.sk89q.minecraft.util.commands.*;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extension.platform.Capability;
import com.sk89q.worldedit.util.auth.AuthorizationException;
import com.sk89q.worldedit.util.formatting.component.MessageBox;
import com.sk89q.worldedit.util.formatting.component.TextComponentProducer;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.formatting.text.event.ClickEvent;
import com.sk89q.worldedit.util.formatting.text.format.TextColor;
import com.sk89q.worldedit.util.paste.ActorCallbackPaste;
import com.sk89q.worldedit.util.report.ReportList;
import com.sk89q.worldedit.util.report.SystemInfoReport;
import com.sk89q.worldedit.util.task.FutureForwardingTask;
import com.sk89q.worldedit.util.task.Task;
import com.sk89q.worldedit.util.task.TaskStateComparator;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.config.ConfigurationManager;
import com.sk89q.worldguard.internal.platform.WorldGuardPlatform;
import com.sk89q.worldguard.util.logging.LoggerToChatHandler;
import com.sk89q.worldguard.util.profiler.SamplerBuilder;
import com.sk89q.worldguard.util.profiler.SamplerBuilder.Sampler;
import com.sk89q.worldguard.util.profiler.ThreadIdFilter;
import com.sk89q.worldguard.util.profiler.ThreadNameFilter;
import com.sk89q.worldguard.util.report.ApplicableRegionsReport;
import com.sk89q.worldguard.util.report.ConfigReport;

import javax.annotation.Nullable;
import java.io.*;
import java.lang.management.ThreadInfo;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WorldGuardCommands {

    private final WorldGuard worldGuard;
    private static int build = 69;
    public static int buildnumber = build + 1;
    @Nullable
    private Sampler activeSampler;

    public WorldGuardCommands(WorldGuard worldGuard) {
        this.worldGuard = worldGuard;
    }

    @Command(aliases = {"version", "verze"}, desc = "Aktuální verze WorldGuardu", max = 0)
    public void version(CommandContext args, Actor sender) throws CommandException {
        sender.print("WorldGuard_" + WorldGuard.getVersion() + "-překlad_v" + WorldGuard.getTransVersion() + "-B" + buildnumber);
        sender.print("http://www.enginehub.org");
        sender.print(" ");
        sender.print("§bPřeložil: _patik_");
        sender.print("§bhttps://valleycube.cz");
        sender.print(" ");

        sender.printDebug("----------- Platformy -----------");
        sender.printDebug(String.format("* %s (%s)", worldGuard.getPlatform().getPlatformName(), worldGuard.getPlatform().getPlatformVersion()));
    }

    //Kontrola verze překladu WorldGuardu

    @Command(aliases = {"update", "aktualizovat"}, desc = "Zkontroluje aktualizace", max = 0)
    @CommandPermissions({"worldguard.update"})
    public void update(CommandContext args, Actor sender) throws Exception {
        String giturl = "http://jenkins.valleycube.cz/job/WorldGuard-CZ-preklad-master/ws/build.number";
        URL url = new URL(giturl);
        URLConnection con = url.openConnection();
        Pattern p = Pattern.compile("text/html;\\s+charset=([^\\s]+)\\s*");
        Matcher m = p.matcher(con.getContentType());

        String charset = m.matches() ? m.group(1) : "UTF-8";
        Reader r = new InputStreamReader(con.getInputStream(), charset);
        StringBuilder buf = new StringBuilder();

        while (true) {
            int ch = r.read();
            if (ch < 0)
                break;
            buf.append((char) ch);
        }
        String str = buf.toString();

        File cacheDir = new File("plugins/WorldGuard", "cache");
        File output = new File(cacheDir, "buildcheck.txt");
        FileWriter writer = new FileWriter(output);

        writer.write(str);
        writer.flush();
        writer.close();

        try {
            BufferedReader br = new BufferedReader(new FileReader(output));
            br.readLine();
            br.readLine();
            String line3 = br.readLine();

            String target = line3.copyValueOf("build.number=".toCharArray());
            String gbuild = line3.replace(target, "");
            int buildn = Integer.parseInt(gbuild);

            if (buildn == buildnumber) {
                sender.print("Nainstalovaná verze překladu WorldGuardu je nejnovější!");
                sender.print("Aktuální verze: WorldGuard_"
                        + WorldGuard.getVersion() + "-překlad_v"
                            + WorldGuard.getTransVersion() + "-B" + buildnumber);
                } else if (buildn > buildnumber){
                sender.print("Nová verze překladu WorldGuard CZ je dostupná na http://jenkins.valleycube.cz/job/WorldGuard-CZ-preklad-master/");
                sender.print("Aktuální verze: WorldGuard_"
                        + WorldGuard.getVersion() + "-překlad_v"
                        + WorldGuard.getTransVersion() + "-B" + buildnumber);
                sender.print("Nová verze: WorldGuard_"
                        + WorldGuard.getLatestVersion() + "-překlad_v"
                            + WorldGuard.getLatestTransVersion() + "-B" + buildn);
                } else {
                sender.print("Nesprávná verze překladu WorldGuardu - " + buildnumber + " místo " + buildn + "! Koukni na http://jenkins.valleycube.cz/job/WorldGuard-CZ-preklad-master/");
            }
        } catch (Exception e) {
            sender.print("Chyba při načítání updateru!");
            e.printStackTrace();
        }
    }

    @Command(aliases = {"reload"}, desc = "Znovu načte konfiguraci", max = 0)
    @CommandPermissions({"worldguard.reload"})
    public void reload(CommandContext args, Actor sender) throws CommandException {
        // TODO: This is subject to a race condition, but at least other commands are not being processed concurrently
        List<Task<?>> tasks = WorldGuard.getInstance().getSupervisor().getTasks();
        if (!tasks.isEmpty()) {
            throw new CommandException("Jsou tu čekající úlohy. Pro zobrazení úloh použij /wg running");
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
            config.unload();
            config.load();
            for (World world : WorldEdit.getInstance().getPlatformManager().queryCapability(Capability.GAME_HOOKS).getWorlds()) {
                config.get(world);
            }
            WorldGuard.getInstance().getPlatform().getRegionContainer().reload();
            // WGBukkit.cleanCache();
            sender.print("Konfigurace WorldGuardu byla znovu načtena");
        } catch (Throwable t) {
            sender.printError("Chyba při načítání WorldGuardu: " + t.getMessage());
        } finally {
            if (minecraftLogger != null) {
                minecraftLogger.removeHandler(handler);
            }
        }
    }
    
    @Command(aliases = {"report"}, desc = "Nahlásí chybu na WorldGuard", flags = "p", max = 0)
    @CommandPermissions({"worldguard.report"})
    public void report(CommandContext args, final Actor sender) throws CommandException, AuthorizationException {
        ReportList report = new ReportList("Seznam nahlášených chyb");
        worldGuard.getPlatform().addPlatformReports(report);
        report.add(new SystemInfoReport());
        report.add(new ConfigReport());
        if (sender instanceof LocalPlayer) {
            report.add(new ApplicableRegionsReport((LocalPlayer) sender));
        }
        String result = report.toString();

        try {
            File dest = new File(worldGuard.getPlatform().getConfigDir().toFile(), "report.txt");
            Files.write(result, dest, StandardCharsets.UTF_8);
            sender.print("Chyby byly zapsány do: " + dest.getAbsolutePath());
        } catch (IOException e) {
            throw new CommandException("Nepodařilo se vytvořit soubor s chybou: " + e.getMessage());
        }
        
        if (args.hasFlag('p')) {
            sender.checkPermission("worldguard.report.pastebin");
            ActorCallbackPaste.pastebin(worldGuard.getSupervisor(), sender, result, "WorldGuard chyby: %s.report");
        }
    }

    @Command(aliases = {"profile"}, usage = "[-p] [-i <interval>] [-t <filtr ohrožení>] [<minuty>]",
            desc = "Profil využití CPU serveru", min = 0, max = 1,
            flags = "t:i:p")
    @CommandPermissions("worldguard.profile")
    public void profile(final CommandContext args, final Actor sender) throws CommandException, AuthorizationException {
        Predicate<ThreadInfo> threadFilter;
        String threadName = args.getFlag('t');
        final boolean pastebin;

        if (args.hasFlag('p')) {
            sender.checkPermission("worldguard.report.pastebin");
            pastebin = true;
        } else {
            pastebin = false;
        }

        if (threadName == null) {
            threadFilter = new ThreadIdFilter(Thread.currentThread().getId());
        } else if (threadName.equals("*")) {
            threadFilter = thread -> true;
        } else {
            threadFilter = new ThreadNameFilter(threadName);
        }

        int minutes;
        if (args.argsLength() == 0) {
            minutes = 5;
        } else {
            minutes = args.getInteger(0);
            if (minutes < 1) {
                throw new CommandException("Profilování musí trvat alespoň 1 minutu.");
            } else if (minutes > 10) {
                throw new CommandException("Profilování může trvat maximálně 10 minut.");
            }
        }

        int interval = 20;
        if (args.hasFlag('i')) {
            interval = args.getFlagInteger('i');
            if (interval < 1 || interval > 100) {
                throw new CommandException("Interval musí být mezi 1 a 100 milisekundami");
            }
            if (interval < 10) {
                sender.printDebug("Poznámka: Nízký interval může způsobit další zpomalení během profilování");
            }
        }
        Sampler sampler;

        synchronized (this) {
            if (activeSampler != null) {
                throw new CommandException("Profilování právě probíhá! Pro zrušení aktuálního profilování použijte /wg stopprofile.");
            }

            SamplerBuilder builder = new SamplerBuilder();
            builder.setThreadFilter(threadFilter);
            builder.setRunTime(minutes, TimeUnit.MINUTES);
            builder.setInterval(interval);
            sampler = activeSampler = builder.start();
        }

        sender.print(TextComponent.of("Spouštím profilování CPU. Výsledky budou k dispozici za " + minutes + " minut.", TextColor.LIGHT_PURPLE)
                .append(TextComponent.newline())
                .append(TextComponent.of("Použij ", TextColor.GRAY))
                .append(TextComponent.of("/wg stopprofile", TextColor.AQUA)
                        .clickEvent(ClickEvent.of(ClickEvent.Action.SUGGEST_COMMAND, "/wg stopprofile")))
                .append(TextComponent.of(" pro zrušení profilování.", TextColor.GRAY)));

        worldGuard.getSupervisor().monitor(FutureForwardingTask.create(
                sampler.getFuture(), "Profilování CPU bude probíhat " + minutes + " minut", sender));

        sampler.getFuture().addListener(() -> {
            synchronized (WorldGuardCommands.this) {
                activeSampler = null;
            }
        }, MoreExecutors.directExecutor());

        Futures.addCallback(sampler.getFuture(), new FutureCallback<>() {
            @Override
            public void onSuccess(Sampler result) {
                String output = result.toString();

                try {
                    File dest = new File(worldGuard.getPlatform().getConfigDir().toFile(), "profile.txt");
                    Files.write(output, dest, StandardCharsets.UTF_8);
                    sender.print("Data profilování CPU byla zapsaná do " + dest.getAbsolutePath());
                } catch (IOException e) {
                    sender.printError("Zápis dat profilování CPU se nezdařil: " + e.getMessage());
                }

                if (pastebin) {
                    ActorCallbackPaste.pastebin(worldGuard.getSupervisor(), sender, output, "Výsledek profilování: %s.profile");
                }
            }

            @Override
            public void onFailure(Throwable throwable) {
            }
        }, MoreExecutors.directExecutor());
    }

    @Command(aliases = {"stopprofile"}, usage = "",desc = "Zastaví spuštěné profilování", min = 0, max = 0)
    @CommandPermissions("worldguard.profile")
    public void stopProfile(CommandContext args, final Actor sender) throws CommandException {
        synchronized (this) {
            if (activeSampler == null) {
                throw new CommandException("Žádné profilování CPU právě neprobíhá.");
            }

            activeSampler.cancel();
            activeSampler = null;
        }

        sender.print("Spuštěné profilování bylo zrušeno.");
    }

    @Command(aliases = {"flushstates", "clearstates"},
            usage = "[player]", desc = "...", max = 1)
    @CommandPermissions("worldguard.flushstates")
    public void flushStates(CommandContext args, Actor sender) throws CommandException {
        if (args.argsLength() == 0) {
            WorldGuard.getInstance().getPlatform().getSessionManager().resetAllStates();
            sender.print("Všechny stavy vymazány.");
        } else {
            LocalPlayer player = worldGuard.getPlatform().getMatcher().matchSinglePlayer(sender, args.getString(0));
            if (player != null) {
                WorldGuard.getInstance().getPlatform().getSessionManager().resetState(player);
                sender.print("Vymazal jsi všechny stavy hráče " + player.getName() + ".");
            }
        }
    }

    @Command(aliases = {"running", "queue"}, desc = "Seznam běžících úloh", max = 0)
    @CommandPermissions("worldguard.running")
    public void listRunningTasks(CommandContext args, Actor sender) throws CommandException {
        List<Task<?>> tasks = WorldGuard.getInstance().getSupervisor().getTasks();

        if (tasks.isEmpty()) {
            sender.print("Nejsou tu žádné probíhající úlohy.");
        } else {
            tasks.sort(new TaskStateComparator());
            MessageBox builder = new MessageBox("Spuštěné úlohy", new TextComponentProducer());
            builder.append(TextComponent.of("Poznámka: Některé „běžící“ úlohy mohou čekat na spuštění.", TextColor.GRAY));
            for (Task<?> task : tasks) {
                builder.append(TextComponent.newline());
                builder.append(TextComponent.of("(" + task.getState().name() + ") ", TextColor.BLUE));
                builder.append(TextComponent.of(CommandUtils.getOwnerName(task.getOwner()) + ": ", TextColor.YELLOW));
                builder.append(TextComponent.of(task.getName(), TextColor.WHITE));
            }
            sender.print(builder.create());
        }
    }

    @Command(aliases = {"debug"}, desc = "Příkaz pro lazení")
    @NestedCommand({DebuggingCommands.class})
    public void debug(CommandContext args, Actor sender) {}

}
