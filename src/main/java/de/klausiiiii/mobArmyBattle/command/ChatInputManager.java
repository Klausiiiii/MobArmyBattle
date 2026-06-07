package de.klausiiiii.mobArmyBattle.command;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Captures the next chat message a player sends and routes it to a one-shot
 * callback instead of broadcasting it. Used by the team-selector to collect a
 * team password without needing an extra slash-command.
 *
 * <p>Player can cancel by typing {@code cancel}. Pending prompts auto-expire
 * after {@link #TIMEOUT_SEC} seconds. Quit while pending → cancelled silently.
 */
public class ChatInputManager implements Listener {

    public static final int TIMEOUT_SEC = 30;
    private static final String CANCEL_KEYWORD = "cancel";

    private static class Pending {
        final Consumer<String> onInput;
        final Runnable onCancel;
        BukkitTask timeoutTask;

        Pending(Consumer<String> onInput, Runnable onCancel) {
            this.onInput = onInput;
            this.onCancel = onCancel;
        }
    }

    private final Plugin plugin;
    // Touched from both the async chat thread (onChat) and main (prompt, timeouts).
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public ChatInputManager(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers a one-shot input handler. {@code onInput} is invoked on the
     * main thread with the trimmed chat content; {@code onCancel} is invoked
     * on the main thread if the player types {@code cancel}, the prompt
     * times out, or the player disconnects.
     */
    public void prompt(Player player, String promptMessage,
                       Consumer<String> onInput, Runnable onCancel) {
        UUID id = player.getUniqueId();
        cancelExisting(id);
        Pending p = new Pending(onInput, onCancel);
        pending.put(id, p);
        player.sendMessage(Component.text(promptMessage, NamedTextColor.GOLD));
        player.sendMessage(Component.text(
                "Tippe '" + CANCEL_KEYWORD + "' zum Abbrechen. (Timeout " + TIMEOUT_SEC + "s)",
                NamedTextColor.GRAY));
        p.timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Pending current = pending.remove(id);
            if (current == null) return;
            Player live = Bukkit.getPlayer(id);
            if (live != null) {
                live.sendMessage(Component.text(
                        "Eingabe abgelaufen.", NamedTextColor.GRAY));
            }
            if (current.onCancel != null) current.onCancel.run();
        }, TIMEOUT_SEC * 20L);
    }

    private void cancelExisting(UUID playerId) {
        Pending existing = pending.remove(playerId);
        if (existing == null) return;
        if (existing.timeoutTask != null) existing.timeoutTask.cancel();
        if (existing.onCancel != null) {
            Bukkit.getScheduler().runTask(plugin, existing.onCancel);
        }
    }

    public boolean isWaitingFor(UUID playerId) {
        return pending.containsKey(playerId);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Pending p = pending.get(id);
        if (p == null) return;
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        // Move the callback to the main thread — chat fires async.
        Bukkit.getScheduler().runTask(plugin, () -> {
            Pending current = pending.remove(id);
            if (current == null) return;
            if (current.timeoutTask != null) current.timeoutTask.cancel();
            if (text.equalsIgnoreCase(CANCEL_KEYWORD)) {
                Player live = Bukkit.getPlayer(id);
                if (live != null) live.sendMessage(Component.text("Abgebrochen.", NamedTextColor.GRAY));
                if (current.onCancel != null) current.onCancel.run();
                return;
            }
            current.onInput.accept(text);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Pending p = pending.remove(event.getPlayer().getUniqueId());
        if (p == null) return;
        if (p.timeoutTask != null) p.timeoutTask.cancel();
        if (p.onCancel != null) p.onCancel.run();
    }

    public void cancelAll() {
        for (UUID id : new java.util.ArrayList<>(pending.keySet())) {
            cancelExisting(id);
        }
    }
}
