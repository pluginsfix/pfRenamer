package pluginsfix.pfrenamer.domain;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public final class RenamerEngine {
    private static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024;

    private final List<ReplacementRule> rules;
    private final Set<String> allowedExtensions;
    private final Set<String> ignoredDirectories;

    public RenamerEngine(
        List<ReplacementRule> rules,
        Set<String> allowedExtensions,
        Set<String> ignoredDirectories
    ) {
        this.rules = rules.stream()
            .sorted(Comparator.comparingInt((ReplacementRule r) -> r.target().length()).reversed())
            .toList();
        this.allowedExtensions = allowedExtensions.stream()
            .map(String::toLowerCase)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.ignoredDirectories = Set.copyOf(ignoredDirectories);
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
            String content = Files.readString(file, StandardCharsets.UTF_8);
            String currentContent = content;
            int fileReplacements = 0;

            for (ReplacementRule rule : rules) {
                String target = rule.target();
                String replacement = rule.replacement();

                int occurrences = countOccurrences(currentContent, target);
                if (occurrences > 0) {
                    fileReplacements += occurrences;
                    currentContent = currentContent.replace(target, replacement);
                }
            }

            if (fileReplacements > 0 && !dryRun) {
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

    private int countOccurrences(String text, String target) {
        if (text.isEmpty() || target.isEmpty()) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(target, index)) != -1) {
            count++;
            index += target.length();
        }
        return count;
    }
}
