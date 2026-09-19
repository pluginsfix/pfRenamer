package pluginsfix.pfrenamer.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

final class RenamerEngineTest {

    @Test
    void shouldReplaceTargetStringsCorrectly(@TempDir Path tempDir) throws IOException {
        Path pluginFolder = tempDir.resolve("ExamplePlugin");
        Files.createDirectories(pluginFolder);

        Path configYml = pluginFolder.resolve("config.yml");
        String originalConfig = "server-name: \"ᴡᴀɴᴅʏɢʀɪᴇꜰ\"\n"
            + "donate-url: \"https://www.wandygrief.ru/store\"\n"
            + "site: \"http://wandygrief.ru\"\n"
            + "shop-1: \"https://wandy.trademc.org/\"\n"
            + "shop-2: \"https://wandy.trademc.org\"\n";
        Files.writeString(configYml, originalConfig, StandardCharsets.UTF_8);

        Path messagesJson = pluginFolder.resolve("messages.json");
        String originalJson = "{\"welcome\": \"Добро пожаловать на ᴡᴀɴᴅʏɢʀɪᴇꜰ! Посетите wandygrief.ru\"}";
        Files.writeString(messagesJson, originalJson, StandardCharsets.UTF_8);

        List<ReplacementRule> rules = List.of(
            new ReplacementRule("ᴡᴀɴᴅʏɢʀɪᴇꜰ", "ɪᴄᴇᴡᴏʀʟᴅ"),
            new ReplacementRule("www.wandygrief.ru", "shop.iceworld.pw"),
            new ReplacementRule("wandygrief.ru", "shop.iceworld.pw"),
            new ReplacementRule("wandy.trademc.org/", "shop.iceworld.pw"),
            new ReplacementRule("wandy.trademc.org", "shop.iceworld.pw")
        );

        RenamerEngine engine = new RenamerEngine(
            rules,
            Set.of(".yml", ".yaml", ".json", ".txt"),
            Set.of("pfRenamer")
        );

        RenameResult dryRunResult = engine.process(tempDir, true);
        assertThat(dryRunResult.scannedFiles()).isEqualTo(2);
        assertThat(dryRunResult.modifiedFiles()).isEqualTo(2);
        assertThat(dryRunResult.totalReplacements()).isEqualTo(7);

        String unchangedContent = Files.readString(configYml, StandardCharsets.UTF_8);
        assertThat(unchangedContent).isEqualTo(originalConfig);

        RenameResult liveResult = engine.process(tempDir, false);
        assertThat(liveResult.scannedFiles()).isEqualTo(2);
        assertThat(liveResult.modifiedFiles()).isEqualTo(2);
        assertThat(liveResult.totalReplacements()).isEqualTo(7);

        String modifiedConfig = Files.readString(configYml, StandardCharsets.UTF_8);
        assertThat(modifiedConfig).contains("server-name: \"ɪᴄᴇᴡᴏʀʟᴅ\"");
        assertThat(modifiedConfig).contains("donate-url: \"https://shop.iceworld.pw/store\"");
        assertThat(modifiedConfig).contains("site: \"http://shop.iceworld.pw\"");
        assertThat(modifiedConfig).contains("shop-1: \"https://shop.iceworld.pw\"");
        assertThat(modifiedConfig).contains("shop-2: \"https://shop.iceworld.pw\"");
        assertThat(modifiedConfig).doesNotContain("ᴡᴀɴᴅʏɢʀɪᴇꜰ");
        assertThat(modifiedConfig).doesNotContain("wandygrief.ru");
        assertThat(modifiedConfig).doesNotContain("wandy.trademc.org");

        String modifiedJson = Files.readString(messagesJson, StandardCharsets.UTF_8);
        assertThat(modifiedJson).contains("ɪᴄᴇᴡᴏʀʟᴅ");
        assertThat(modifiedJson).contains("shop.iceworld.pw");
        assertThat(modifiedJson).doesNotContain("ᴡᴀɴᴅʏɢʀɪᴇꜰ");
        assertThat(modifiedJson).doesNotContain("wandygrief.ru");
    }

    @Test
    void shouldIgnoreExcludedDirectoriesAndExtensions(@TempDir Path tempDir) throws IOException {
        Path ignoredDir = tempDir.resolve("pfRenamer");
        Files.createDirectories(ignoredDir);
        Path ignoredFile = ignoredDir.resolve("config.yml");
        Files.writeString(ignoredFile, "site: wandygrief.ru", StandardCharsets.UTF_8);

        Path normalDir = tempDir.resolve("OtherPlugin");
        Files.createDirectories(normalDir);
        Path jarFile = normalDir.resolve("plugin.jar");
        Files.writeString(jarFile, "wandygrief.ru", StandardCharsets.UTF_8);

        List<ReplacementRule> rules = List.of(
            new ReplacementRule("wandygrief.ru", "shop.iceworld.pw")
        );

        RenamerEngine engine = new RenamerEngine(
            rules,
            Set.of(".yml"),
            Set.of("pfRenamer")
        );

        RenameResult result = engine.process(tempDir, false);
        assertThat(result.scannedFiles()).isEqualTo(0);
        assertThat(result.modifiedFiles()).isEqualTo(0);
        assertThat(result.totalReplacements()).isEqualTo(0);

        String untouchedContent = Files.readString(ignoredFile, StandardCharsets.UTF_8);
        assertThat(untouchedContent).contains("wandygrief.ru");
    }
}
