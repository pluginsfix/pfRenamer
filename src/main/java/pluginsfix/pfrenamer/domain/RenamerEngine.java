package pluginsfix.pfrenamer.domain;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class RenamerEngine {
    private static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024;
    private static final Pattern INNER_MEMORY_SECTION_PATTERN = Pattern.compile("MemorySection\\[path='([^']*)',\\s*root='YamlConfiguration'\\]");
    private static final Pattern ORPHAN_ROOT_PATTERN = Pattern.compile("(?i)(?:replacements\\.)*[^'\\n\\r]*root='YamlConfiguration'\\]+['\"]*");
    private static final Pattern CORRUPTED_PREFIX_PATTERN = Pattern.compile("(?i)(?:replacements\\.|mc\\.|www\\.)+(shop\\.iceworld\\.pw|ɪᴄᴇᴡᴏʀʟᴅ)(?:\\.(?:ru|org|com|net|pw))*");
    private static final Pattern DOTTED_YAML_KEY_PATTERN = Pattern.compile("(?m)^([ \\t]*)'([a-zA-Z0-9_-]*)shop\\.iceworld\\.pw([a-zA-Z0-9_.-]*)':");

    private final List<ReplacementRule> rules;
    private final Set<String> allowedExtensions;
    private final Set<String> ignoredDirectories;
    private final boolean createBackup;

    public RenamerEngine(
        List<ReplacementRule> rules,
        Set<String> allowedExtensions,
        Set<String> ignoredDirectories,
        boolean createBackup
    ) {
        this.rules = rules.stream()
            .sorted(Comparator.comparingInt((ReplacementRule r) -> r.target().length()).reversed())
            .toList();
        this.allowedExtensions = allowedExtensions.stream()
            .map(String::toLowerCase)
            .collect(Collectors.toUnmodifiableSet());
        this.ignoredDirectories = Set.copyOf(ignoredDirectories);
        this.createBackup = createBackup;
    }

    public RenamerEngine(
        List<ReplacementRule> rules,
        Set<String> allowedExtensions,
        Set<String> ignoredDirectories
    ) {
        this(rules, allowedExtensions, ignoredDirectories, false);
    }

    public RenameResult process(Path rootDirectory, boolean dryRun) {
        if (!Files.exists(rootDirectory) || !Files.isDirectory(rootDirectory)) {
            return new RenameResult(0, 0, 0, 0, List.of());
        }

        long startTime = System.currentTimeMillis();
        int scannedCount = 0;
        int modifiedCount = 0;
        int totalReplacements = 0;
        List<Path> modifiedPaths = new ArrayList<>();

        List<Path> filesToProcess = collectFiles(rootDirectory);
        for (Path file : filesToProcess) {
            scannedCount++;
            int fileReplacements = processFile(file, dryRun);
            if (fileReplacements > 0) {
                modifiedCount++;
                totalReplacements += fileReplacements;
                modifiedPaths.add(file);
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        return new RenameResult(scannedCount, modifiedCount, totalReplacements, elapsed, List.copyOf(modifiedPaths));
    }

    private List<Path> collectFiles(Path rootDirectory) {
        List<Path> result = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(rootDirectory)) {
            stream.filter(Files::isRegularFile)
                .filter(this::isAllowedFile)
                .forEach(result::add);
        } catch (IOException e) {
            return result;
        }
        return result;
    }

    private boolean isAllowedFile(Path path) {
        try {
            if (Files.size(path) > MAX_FILE_SIZE_BYTES) {
                return false;
            }
        } catch (IOException e) {
            return false;
        }

        for (Path element : path) {
            if (ignoredDirectories.contains(element.toString())) {
                return false;
            }
        }

        String fileName = path.getFileName().toString().toLowerCase();
        for (String extension : allowedExtensions) {
            if (fileName.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    private int processFile(Path file, boolean dryRun) {
        try {
            String originalContent = Files.readString(file, StandardCharsets.UTF_8);
            String currentContent = originalContent;
            int fileReplacements = 0;

            String cleanedContent = repairMemorySections(currentContent);
            if (!cleanedContent.equals(currentContent)) {
                fileReplacements++;
                currentContent = cleanedContent;
            }

            String safeProcessed = processLinesSafely(currentContent);
            if (!safeProcessed.equals(currentContent)) {
                fileReplacements++;
                currentContent = safeProcessed;
            }

            String postCleaned = cleanupArtifacts(currentContent);
            if (!postCleaned.equals(currentContent)) {
                if (fileReplacements == 0) {
                    fileReplacements++;
                }
                currentContent = postCleaned;
            }

            if (!currentContent.equals(originalContent) && !dryRun) {
                if (createBackup) {
                    Path backupPath = file.resolveSibling(file.getFileName().toString() + ".bak");
                    if (!Files.exists(backupPath)) {
                        Files.copy(file, backupPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                }

                Files.writeString(
                    file,
                    currentContent,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
                );
            }

            return fileReplacements;
        } catch (IOException e) {
            return 0;
        }
    }

    private String processLinesSafely(String content) {
        String[] lines = content.split("\\r?\\n", -1);
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            if (isProtectedLine(line)) {
                builder.append(line);
            } else {
                String processedLine = line;
                for (ReplacementRule rule : rules) {
                    String target = rule.target();
                    String replacement = rule.replacement();
                    if (processedLine.contains(target)) {
                        processedLine = processedLine.replace(target, replacement);
                    }
                }
                builder.append(processedLine);
            }

            if (i < lines.length - 1) {
                builder.append("\n");
            }
        }

        return builder.toString();
    }

    private boolean isProtectedLine(String line) {
        String trimmed = line.trim();
        return trimmed.startsWith("material: basehead-")
            || trimmed.contains("basehead-eyJ")
            || trimmed.contains("eyJ0ZXh0");
    }

    private String repairMemorySections(String content) {
        if (!content.contains("MemorySection[") && !content.contains("root='YamlConfiguration']")) {
            return content;
        }

        String result = content;
        int maxIterations = 50;
        while (result.contains("MemorySection[") && maxIterations-- > 0) {
            Matcher matcher = INNER_MEMORY_SECTION_PATTERN.matcher(result);
            if (!matcher.find()) {
                break;
            }

            StringBuffer buffer = new StringBuffer();
            do {
                String path = matcher.group(1);
                String resolved = resolveMemorySectionTarget(path);
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(resolved));
            } while (matcher.find());
            matcher.appendTail(buffer);
            result = buffer.toString();
        }

        Matcher orphanMatcher = ORPHAN_ROOT_PATTERN.matcher(result);
        if (orphanMatcher.find()) {
            result = orphanMatcher.replaceAll("shop.iceworld.pw");
        }

        return cleanupArtifacts(result);
    }

    private String resolveMemorySectionTarget(String path) {
        if (path.contains("ᴡᴀɴᴅʏɢʀɪᴇꜰ")) {
            return "ɪᴄᴇᴡᴏʀʟᴅ";
        }
        return "shop.iceworld.pw";
    }

    private String cleanupArtifacts(String content) {
        String result = content;

        result = result.replace("shop.iceworld.pw.shop.iceworld.pw", "shop.iceworld.pw");
        result = result.replace("shop.iceworld.pw.ru", "shop.iceworld.pw");
        result = result.replace("replacements.shop.iceworld.pw", "shop.iceworld.pw");
        result = result.replace("replacements.ɪᴄᴇᴡᴏʀʟᴅ", "ɪᴄᴇᴡᴏʀʟᴅ");
        result = result.replace("mc.shop.iceworld.pw.ru", "shop.iceworld.pw");
        result = result.replace("mc.shop.iceworld.pw", "shop.iceworld.pw");
        result = result.replace("www.shop.iceworld.pw.ru", "shop.iceworld.pw");
        result = result.replace("www.shop.iceworld.pw", "shop.iceworld.pw");
        result = result.replace("https://shop.iceworld.pw.ru", "https://shop.iceworld.pw");
        result = result.replace("http://shop.iceworld.pw.ru", "http://shop.iceworld.pw");
        result = result.replace("https://https://", "https://");
        result = result.replace("http://http://", "http://");

        result = result.replace("shop.iceworld.pwmace", "fitems give %player_name% mace");
        result = result.replace("wandymace give %player_name% mace", "fitems give %player_name% mace");

        Matcher dottedKeyMatcher = DOTTED_YAML_KEY_PATTERN.matcher(result);
        if (dottedKeyMatcher.find()) {
            result = dottedKeyMatcher.replaceAll("$1'$2sub$3':");
        }

        Matcher prefixMatcher = CORRUPTED_PREFIX_PATTERN.matcher(result);
        if (prefixMatcher.find()) {
            result = prefixMatcher.replaceAll("$1");
        }

        return result;
    }
}
