package pluginsfix.pfrenamer.text;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Messages {
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.builder()
        .character('&')
        .hexColors()
        .build();

    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, List<String>> messages = new HashMap<>();

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "messages.yml");
        reload();
    }

    public void reload() {
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        messages.clear();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String key : config.getKeys(true)) {
            if (config.isList(key)) {
                messages.put(key, config.getStringList(key));
            } else if (config.isString(key)) {
                String single = config.getString(key);
                if (single != null) {
                    messages.put(key, List.of(single));
                }
            }
        }
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        List<String> lines = messages.getOrDefault(key, Collections.emptyList());
        if (lines.isEmpty()) {
            return;
        }

        for (String line : lines) {
            String processed = applyPlaceholders(line, placeholders);
            if (processed.startsWith("[message]")) {
                String text = processed.substring("[message]".length()).trim();
                Component component = parseComponent(text);
                if (sender instanceof Audience audience) {
                    audience.sendMessage(component);
                } else {
                    sender.sendMessage(LegacyComponentSerializer.legacySection().serialize(component));
                }
            } else if (processed.startsWith("[sound]") && sender instanceof Player player) {
                String soundName = processed.substring("[sound]".length()).trim();
                try {
                    Sound sound = Sound.valueOf(soundName.toUpperCase());
                    player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
                } catch (IllegalArgumentException ignored) {
                }
            } else if (!processed.startsWith("[")) {
                Component component = parseComponent(processed);
                if (sender instanceof Audience audience) {
                    audience.sendMessage(component);
                } else {
                    sender.sendMessage(LegacyComponentSerializer.legacySection().serialize(component));
                }
            }
        }
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Collections.emptyMap());
    }

    private String applyPlaceholders(String input, Map<String, String> placeholders) {
        if (placeholders.isEmpty() || input.isEmpty()) {
            return input;
        }
        String result = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue())
                           .replace("<" + entry.getKey() + ">", entry.getValue());
        }
        return result;
    }

    private Component parseComponent(String text) {
        if (text.isEmpty()) {
            return Component.empty();
        }
        String hexParsed = parseHexColors(text);
        return SERIALIZER.deserialize(hexParsed);
    }

    private String parseHexColors(String text) {
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("&x");
            for (char c : hex.toCharArray()) {
                replacement.append('&').append(c);
            }
            matcher.appendReplacement(builder, replacement.toString());
        }
        matcher.appendTail(builder);
        return builder.toString();
    }
}
