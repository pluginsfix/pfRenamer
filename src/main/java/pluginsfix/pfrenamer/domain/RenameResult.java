package pluginsfix.pfrenamer.domain;

import java.nio.file.Path;
import java.util.List;

public record RenameResult(
    int scannedFiles,
    int modifiedFiles,
    int totalReplacements,
    long elapsedMillis,
    List<Path> modifiedPaths
) {}
