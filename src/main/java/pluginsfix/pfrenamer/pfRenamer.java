package pluginsfix.pfrenamer;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import pluginsfix.pfrenamer.command.pfRenamerCommand;
import pluginsfix.pfrenamer.config.RenamerConfig;
import pluginsfix.pfrenamer.domain.RenamerEngine;
import pluginsfix.pfrenamer.text.Messages;

import java.io.File;
import java.nio.file.Path;

public final class pfRenamer extends JavaPlugin {
    private Messages messages;
    private RenamerEngine engine;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.messages = new Messages(this);
        reloadPlugin();

        PluginCommand command = getCommand("pfrenamer");
        if (command != null) {
            pfRenamerCommand executor = new pfRenamerCommand(this, messages);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
    }

    public void reloadPlugin() {
        File configFile = new File(getDataFolder(), "config.yml");
        RenamerConfig config = RenamerConfig.load(configFile, getResource("config.yml"));
        this.engine = new RenamerEngine(
            config.rules(),
            config.fileExtensions(),
            config.ignoredDirectories(),
            config.createBackup()
        );
        if (this.messages != null) {
            this.messages.reload();
        }
    }

    public RenamerEngine getEngine() {
        return this.engine;
    }

    public Path getPluginsFolder() {
        return getDataFolder().getParentFile().toPath();
    }
}
