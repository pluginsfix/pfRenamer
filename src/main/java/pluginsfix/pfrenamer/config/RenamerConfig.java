package pluginsfix.pfrenamer.config;

import org.yaml.snakeyaml.Yaml;
import pluginsfix.pfrenamer.domain.ReplacementRule;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record RenamerConfig(
    Set<String> fileExtensions,
    Set<String> ignoredDirectories,
    List<ReplacementRule> rules,
    boolean createBackup
) {
    public static RenamerConfig load(File configFile, InputStream fallbackStream) {
        Yaml yaml = new Yaml();
        Map<String, Object> data = null;

        if (configFile != null && configFile.exists()) {
            try (InputStream in = new FileInputStream(configFile)) {
                data = yaml.load(in);
            } catch (IOException ignored) {
            }
        }

        if (data == null && fallbackStream != null) {
            try (fallbackStream) {
                data = yaml.load(fallbackStream);
            } catch (IOException ignored) {
            }
        }

        if (data == null) {
            return defaultFallback();
        }

        Set<String> extensions = new HashSet<>();
        Object extensionsObj = data.get("file-extensions");
        if (extensionsObj instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    extensions.add(item.toString().toLowerCase());
                }
            }
        }
        if (extensions.isEmpty()) {
            extensions.addAll(Set.of(".yml", ".yaml", ".json", ".txt", ".toml", ".properties", ".conf", ".cfg", ".xml", ".csv"));
        }

        Set<String> ignored = new HashSet<>();
        Object ignoredObj = data.get("ignored-directories");
        if (ignoredObj instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    ignored.add(item.toString());
                }
            }
        }
        if (ignored.isEmpty()) {
            ignored.addAll(Set.of("pfRenamer", "bStats"));
        }

        boolean backup = false;
        Object backupObj = data.get("create-backup");
        if (backupObj instanceof Boolean b) {
            backup = b;
        }

        List<ReplacementRule> rules = new ArrayList<>();
        Object replacementsObj = data.get("replacements");
        if (replacementsObj instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    String target = entry.getKey().toString();
                    String replacement = entry.getValue().toString();
                    if (!target.isEmpty()) {
                        rules.add(new ReplacementRule(target, replacement));
                    }
                }
            }
        } else if (replacementsObj instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> itemMap) {
                    Object targetObj = itemMap.get("target");
                    if (targetObj == null) targetObj = itemMap.get("from");
                    if (targetObj == null) targetObj = itemMap.get("find");

                    Object replacementObj = itemMap.get("replacement");
                    if (replacementObj == null) replacementObj = itemMap.get("to");
                    if (replacementObj == null) replacementObj = itemMap.get("replace");

                    if (targetObj != null && replacementObj != null) {
                        String target = targetObj.toString();
                        String replacement = replacementObj.toString();
                        if (!target.isEmpty()) {
                            rules.add(new ReplacementRule(target, replacement));
                        }
                    }
                }
            }
        }

        if (rules.isEmpty()) {
            rules.addAll(defaultRules());
        }

        return new RenamerConfig(Set.copyOf(extensions), Set.copyOf(ignored), List.copyOf(rules), backup);
    }

    private static RenamerConfig defaultFallback() {
        return new RenamerConfig(
            Set.of(".yml", ".yaml", ".json", ".txt", ".toml", ".properties", ".conf", ".cfg", ".xml", ".csv"),
            Set.of("pfRenamer", "bStats"),
            defaultRules(),
            false
        );
    }

    private static List<ReplacementRule> defaultRules() {
        return List.of(
            new ReplacementRule("ᴡᴀɴᴅʏɢʀɪᴇꜰ", "ɪᴄᴇᴡᴏʀʟᴅ"),
            new ReplacementRule("www.wandygrief.ru", "shop.iceworld.pw"),
            new ReplacementRule("wandygrief.ru", "shop.iceworld.pw"),
            new ReplacementRule("wandy.trademc.org/", "shop.iceworld.pw"),
            new ReplacementRule("wandy.trademc.org", "shop.iceworld.pw")
        );
    }
}
