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

package com.sk89q.worldguard.bukkit.util;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.PluginClassLoader;

import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Validates that certain specified classes came from the same source as
 * a plugin.
 * This is copied from the same class in WorldEdit because unfortunately
 * trying to use WorldEdit's means we're susceptible to getting a bad version
 * of this class if another plugin shades it....which is exactly what we're
 * trying to detect and report.
 */
public class ClassSourceValidator {

    private static final String SEPARATOR_LINE = Strings.repeat("*", 46);
    private static final Method loadClass;
    private static Class<?> pluginClassLoaderClass;

    static {
        Method tmp;
        try {
            pluginClassLoaderClass = Class.forName("org.bukkit.plugin.java.PluginClassLoader", false,
                    Bukkit.class.getClassLoader());
            tmp = pluginClassLoaderClass.getDeclaredMethod("loadClass0",
                    String.class, boolean.class, boolean.class, boolean.class);
            tmp.setAccessible(true);
        } catch (NoSuchMethodException | ClassNotFoundException e) {
            tmp = null;
        }
        loadClass = tmp;
    }

    private final Plugin plugin;
    @Nullable
    private final ClassLoader expectedClassLoader;

    /**
     * Create a new instance.
     *
     * @param plugin The plugin
     */
    public ClassSourceValidator(Plugin plugin) {
        checkNotNull(plugin, "plugin");
        this.plugin = plugin;
        this.expectedClassLoader = plugin.getClass().getClassLoader();
    }

    /**
     * Return a map of classes that been loaded from a different source.
     *
     * @param classes A list of classes to check
     * @return The results
     */
    public Map<Class<?>, Plugin> findMismatches(List<Class<?>> classes) {
        checkNotNull(classes, "classes");

        if (expectedClassLoader == null || loadClass == null) {
            return ImmutableMap.of();
        }

        Map<Class<?>, Plugin> mismatches = new HashMap<>();

        for (Plugin target : Bukkit.getPluginManager().getPlugins()) {
            if (target == plugin) {
                continue;
            }
            ClassLoader targetLoader = target.getClass().getClassLoader();
            if (!(pluginClassLoaderClass.isAssignableFrom(targetLoader.getClass()))) {
                continue;
            }
            for (Class<?> testClass : classes) {
                Class<?> targetClass;
                try {
                    targetClass = (Class<?>) loadClass.invoke(targetLoader, testClass.getName(), false, false, false);
                } catch (IllegalAccessException | InvocationTargetException ignored) {
                    continue;
                }
                if (targetClass.getClassLoader() != expectedClassLoader) {
                    mismatches.putIfAbsent(testClass, targetClass.getClassLoader() == targetLoader ? target : null);
                }
            }
        }

        return mismatches;
    }

    /**
     * Reports classes that have come from a different source.
     *
     * <p>The warning is emitted to the log.</p>
     *
     * @param classes The list of classes to check
     */
    public void reportMismatches(List<Class<?>> classes) {
        if (Boolean.getBoolean("enginehub.disable.class.source.validation")) {
            return;
        }
        Map<Class<?>, Plugin> mismatches = findMismatches(classes);

        if (mismatches.isEmpty()) {
            return;
        }
        StringBuilder builder = new StringBuilder("\n");

        builder.append(SEPARATOR_LINE).append("\n");
        builder.append("** /!\\    VÁŽNÉ VAROVÁNÍ    /!\\\n");
        builder.append("** \n");
        builder.append("** Vývojář pluginu zahrnul část \n");
        builder.append("** ").append(plugin.getName()).append(" do svého vlastního pluginu, takže spíše než\n");
        builder.append("** pomocí verze ").append(plugin.getName()).append(" kterou jste si stáhli,\n");
        builder.append("** budete používat nefunkční mix starého ").append(plugin.getName()).append(" (který přišel\n");
        builder.append("** s pluginem) and your downloaded version. TOTO MŮŽE\n");
        builder.append("** VÁŽNĚ POŠKODIT ").append(plugin.getName().toUpperCase(Locale.ROOT)).append(" A VŠECHNY JEHO VLASTNOSTI.\n");
        builder.append("**\n");
        builder.append("** To se mohlo stát, protože vývojář používá\n");
        builder.append("** ").append(plugin.getName()).append(" API a myslí si, že je nutné zahrnout\n");
        builder.append("** ").append(plugin.getName()).append(" Nicméně není!\n");
        builder.append("**\n");
        builder.append("** Zde jsou některé soubory, které byly přepsány:\n");
        builder.append("** \n");
        for (Map.Entry<Class<?>, Plugin> entry : mismatches.entrySet()) {
            Plugin badPlugin = entry.getValue();
            String url = badPlugin == null
                    ? "(neznámý)"
                    : badPlugin.getName() + " (" + badPlugin.getClass().getProtectionDomain().getCodeSource().getLocation() + ")";
            builder.append("** '").append(entry.getKey().getSimpleName()).append("' původem z '").append(url).append("'\n");
        }
        builder.append("**\n");
        builder.append("** Nahlaste to prosím nejdříve vývojáři překladu.\n");
        builder.append(SEPARATOR_LINE).append("\n");

        plugin.getLogger().severe(builder.toString());
    }
}
