package pluginsfix.pfrenamer.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

final class RenamerConfigTest {

    @Test
    void shouldLoadDottedKeysWithoutSplitting(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("config.yml");
        String yamlContent = "file-extensions:\n"
            + "  - \".yml\"\n"
            + "  - \".json\"\n"
            + "ignored-directories:\n"
            + "  - \"pfRenamer\"\n"
            + "replacements:\n"
            + "  \"www.wandygrief.ru\": \"shop.iceworld.pw\"\n"
            + "  \"wandygrief.ru\": \"shop.iceworld.pw\"\n"
            + "  \"wandy.trademc.org/\": \"shop.iceworld.pw\"\n";
        Files.writeString(configFile, yamlContent, StandardCharsets.UTF_8);

        RenamerConfig config = RenamerConfig.load(configFile.toFile(), null);

        assertThat(config.fileExtensions()).contains(".yml", ".json");
        assertThat(config.ignoredDirectories()).contains("pfRenamer");
        assertThat(config.rules()).hasSize(3);
        assertThat(config.rules()).anyMatch(r -> r.target().equals("www.wandygrief.ru") && r.replacement().equals("shop.iceworld.pw"));
        assertThat(config.rules()).anyMatch(r -> r.target().equals("wandygrief.ru") && r.replacement().equals("shop.iceworld.pw"));
        assertThat(config.rules()).anyMatch(r -> r.target().equals("wandy.trademc.org/") && r.replacement().equals("shop.iceworld.pw"));
    }

    @Test
    void shouldFallbackToResourceStreamWhenFileNotFound() {
        String yamlContent = "replacements:\n"
            + "  \"ᴡᴀɴᴅʏɢʀɪᴇꜰ\": \"ɪᴄᴇᴡᴏʀʟᴅ\"\n";
        ByteArrayInputStream stream = new ByteArrayInputStream(yamlContent.getBytes(StandardCharsets.UTF_8));

        RenamerConfig config = RenamerConfig.load(new File("non-existent.yml"), stream);

        assertThat(config.rules()).hasSize(1);
        assertThat(config.rules().get(0).target()).isEqualTo("ᴡᴀɴᴅʏɢʀɪᴇꜰ");
        assertThat(config.rules().get(0).replacement()).isEqualTo("ɪᴄᴇᴡᴏʀʟᴅ");
    }
}
