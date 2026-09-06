package me.docdrewskii.profitmultiplier.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;

/**
 * Thread-routing helper for Folia's regionized scheduler.
 * <p>
 * Folia has no single "main thread" — each world region (and each entity) is ticked by its own
 * thread, and the legacy {@code Bukkit.getScheduler()} sync methods throw
 * {@code UnsupportedOperationException} there. Paper backports the Folia scheduler API
 * ({@code Bukkit.getGlobalRegionScheduler()}, {@code Bukkit.getAsyncScheduler()},
 * {@code Entity#getScheduler()}) onto non-Folia servers too, running everything on the normal
 * main thread, so the same call sites work unchanged on Spigot/Paper and Folia alike.
 */
public final class FoliaScheduler {

    private static final boolean FOLIA = detectFolia();

    private FoliaScheduler() {
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    /**
     * Runs a task that touches global/shared state (console commands, cross-region events).
     * Executes immediately if already on the primary thread; otherwise hops onto it.
     */
    public static void runGlobal(Plugin plugin, Runnable task) {
        if (FOLIA) {
            Bukkit.getGlobalRegionScheduler().run(plugin, t -> task.run());
        } else if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /** Repeating task on the global region thread (ticks apply the same on Folia and Paper). */
    public static void runGlobalTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (FOLIA) {
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> task.run(),
                    Math.max(1, delayTicks), Math.max(1, periodTicks));
        } else {
            Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        }
    }

    /**
     * Runs a task that must execute on the given player's own owning thread (inventory,
     * location, or other entity-state access). Executes immediately if already safe to do so.
     */
    public static void runForPlayer(Plugin plugin, Player player, Runnable task) {
        if (FOLIA) {
            player.getScheduler().run(plugin, t -> task.run(), null);
        } else if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /** Fire-and-forget task with no world/player-state access (e.g. blocking I/O, HTTP calls). */
    public static void runAsync(Plugin plugin, Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, t -> task.run());
    }

    /** Repeating fire-and-forget task with no world/player-state access. */
    public static void runAsyncTimer(Plugin plugin, Runnable task, long delaySeconds, long periodSeconds) {
        Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> task.run(),
                Math.max(1, delaySeconds), Math.max(1, periodSeconds), TimeUnit.SECONDS);
    }
}
