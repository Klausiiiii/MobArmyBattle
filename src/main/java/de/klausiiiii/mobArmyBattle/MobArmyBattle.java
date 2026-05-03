package de.klausiiiii.mobArmyBattle;

import de.klausiiiii.mobArmyBattle.command.MabCommand;
import de.klausiiiii.mobArmyBattle.match.MatchManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class MobArmyBattle extends JavaPlugin {

    private MatchManager matchManager;

    @Override
    public void onEnable() {
        matchManager = new MatchManager();

        PluginCommand mabCmd = getCommand("mab");
        if (mabCmd == null) {
            getLogger().severe("Befehl /mab nicht in plugin.yml deklariert!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        mabCmd.setExecutor(new MabCommand(matchManager));

        getLogger().info("MobArmyBattle aktiviert.");
    }

    @Override
    public void onDisable() {
        getLogger().info("MobArmyBattle deaktiviert.");
    }

    public MatchManager getMatchManager() {
        return matchManager;
    }
}
