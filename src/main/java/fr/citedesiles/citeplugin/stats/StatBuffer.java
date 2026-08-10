package fr.citedesiles.citeplugin.stats;

import fr.citedesiles.coreplugin.CoreCDI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Buffer de stats : accumule les incréments en mémoire et les envoie à l'API
 * de façon asynchrone toutes les 30 secondes (et immédiatement au quit/disable).
 * Le client HTTP est synchrone, on ne l'appelle donc JAMAIS sur le thread principal.
 */
public class StatBuffer {
    private final JavaPlugin plugin;
    private final CoreCDI api;
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Long>> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> playtimeBase = new ConcurrentHashMap<>();
    private BukkitTask flushTask;

    public StatBuffer(JavaPlugin plugin, CoreCDI api) {
        this.plugin = plugin;
        this.api = api;
    }

    public void start() {
        flushTask = Bukkit.getScheduler().runTaskTimer(plugin, this::flush, 600L, 600L);
    }

    public void stop() {
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        flush();
    }

    /** Accumule une valeur pour une stat d'un joueur (thread-safe). */
    public void add(UUID uuid, String stat, long amount) {
        if (amount <= 0) return;
        pending.computeIfAbsent(uuid.toString(), k -> new ConcurrentHashMap<>()).merge(stat, amount, Long::sum);
    }

    /** Marque le début d'une session (base du calcul de playtime). */
    public void onPlayerJoin(UUID uuid) {
        playtimeBase.put(uuid.toString(), System.currentTimeMillis());
    }

    /** Comptabilise le playtime restant de la session puis force un flush immédiat. */
    public void onPlayerQuit(UUID uuid) {
        Long base = playtimeBase.remove(uuid.toString());
        if (base != null) {
            long delta = (System.currentTimeMillis() - base) / 1000;
            if (delta > 0) add(uuid, "playtime", delta);
        }
        flush();
    }

    /** Comptabilise le playtime des joueurs en ligne puis envoie le buffer à l'API (async). */
    public void flush() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            String id = player.getUniqueId().toString();
            long base = playtimeBase.getOrDefault(id, now);
            long delta = (now - base) / 1000;
            if (delta > 0) {
                add(player.getUniqueId(), "playtime", delta);
                playtimeBase.put(id, now);
            }
        }

        List<StatEntry> batch = drain();
        if (batch.isEmpty()) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (StatEntry entry : batch) {
                try {
                    api.addStat(entry.uuid(), entry.stat(), entry.value());
                } catch (Exception e) {
                    plugin.getLogger().warning("Échec d'écriture de la stat " + entry.stat() + " pour " + entry.uuid() + " : " + e.getMessage());
                }
            }
        });
    }

    private List<StatEntry> drain() {
        List<StatEntry> batch = new ArrayList<>();
        for (String uuid : pending.keySet()) {
            ConcurrentHashMap<String, Long> stats = pending.remove(uuid);
            if (stats == null) continue;
            for (Map.Entry<String, Long> e : stats.entrySet()) {
                batch.add(new StatEntry(uuid, e.getKey(), e.getValue()));
            }
        }
        return batch;
    }

    private record StatEntry(String uuid, String stat, long value) {
    }
}
