package pluginsfix.pfrenamer.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import pluginsfix.pfrenamer.pfRenamer;
import pluginsfix.pfrenamer.domain.RenameResult;
import pluginsfix.pfrenamer.text.Messages;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class pfRenamerCommand implements CommandExecutor, TabCompleter {
    private final pfRenamer plugin;
    private final Messages messages;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public pfRenamerCommand(pfRenamer plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String label,
        @NotNull String[] args
    ) {
        if (!sender.hasPermission("pfrenamer.admin")) {
            messages.send(sender, "no-permission");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            messages.send(sender, "help");
            return true;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("reload")) {
            plugin.reloadPlugin();
            messages.send(sender, "reload-success");
            return true;
        }

        if (sub.equals("run") || sub.equals("replace")) {
            executeTask(sender, false);
            return true;
        }

        if (sub.equals("check") || sub.equals("dryrun")) {
            executeTask(sender, true);
            return true;
        }

        messages.send(sender, "help");
        return true;
    }

    private void executeTask(CommandSender sender, boolean dryRun) {
        if (!running.compareAndSet(false, true)) {
            messages.send(sender, "already-running");
            return;
        }

        if (dryRun) {
            messages.send(sender, "check-started");
        } else {
            messages.send(sender, "process-started");
        }

        CompletableFuture.supplyAsync(() -> plugin.getEngine().process(plugin.getPluginsFolder(), dryRun))
            .thenAccept(result -> {
                running.set(false);
                sendResults(sender, result, dryRun);
            })
            .exceptionally(throwable -> {
                running.set(false);
                messages.send(sender, "process-error", Map.of("error", throwable.getMessage() == null ? "Unknown" : throwable.getMessage()));
                return null;
            });
    }

    private void sendResults(CommandSender sender, RenameResult result, boolean dryRun) {
        if (result.totalReplacements() == 0) {
            messages.send(sender, "no-replacements");
            return;
        }

        Map<String, String> placeholders = Map.of(
            "scanned", String.valueOf(result.scannedFiles()),
            "modified", String.valueOf(result.modifiedFiles()),
            "replacements", String.valueOf(result.totalReplacements()),
            "time", String.valueOf(result.elapsedMillis())
        );

        if (dryRun) {
            messages.send(sender, "check-success", placeholders);
        } else {
            messages.send(sender, "process-success", placeholders);
        }
    }

    @Override
    public List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String label,
        @NotNull String[] args
    ) {
        if (!sender.hasPermission("pfrenamer.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> subcommands = List.of("run", "check", "reload", "help");
            List<String> matches = new ArrayList<>();
            String current = args[0].toLowerCase();
            for (String sub : subcommands) {
                if (sub.startsWith(current)) {
                    matches.add(sub);
                }
            }
            return matches;
        }

        return Collections.emptyList();
    }
}
