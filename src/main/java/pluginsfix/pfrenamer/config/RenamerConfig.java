package pluginsfix.pfrenamer.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import pluginsfix.pfrenamer.domain.ReplacementRule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record RenamerConfig(
    Set<String> fileExtensions,
    Set<String> ignoredDirectories,
    List<ReplacementRule> rules
) {
    public static RenamerConfig fromBukkit(FileConfiguration config) {
        List<String> rawExtensions = config.getStringList("file-extensions");
        Set<String> extensions = new HashSet<>();
        if (rawExtensions.isEmpty()) {
            extensions.addAll(Set.of(".yml", ".yaml", ".json", ".txt", ".toml", ".properties", ".conf", ".cfg", ".xml", ".csv"));
        } else {
            extensions.addAll(rawExtensions);
        }

        List<String> rawIgnored = config.getStringList("ignored-directories");
        Set<String> ignored = new HashSet<>();
        if (rawIgnored.isEmpty()) {
            ignored.addAll(Set.of("pfRenamer", "bStats"));
        } else {
            ignored.addAll(rawIgnored);
        }

        List<ReplacementRule> rules = new ArrayList<>();
        ConfigurationSection section = config.getConfigurationSection("replacements");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String replacement = section.getString(key);
                if (replacement != null && !key.isEmpty()) {
                    rules.add(new ReplacementRule(key, replacement));
                }
            }
        }

        return new RenamerConfig(Set.copyOf(extensions), Set.copyOf(ignored), List.copyOf(rules));
    }
}
