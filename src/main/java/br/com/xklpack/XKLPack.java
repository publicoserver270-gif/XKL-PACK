package br.com.xklpack;

import br.com.xklpack.economy.EconomyService;
import br.com.xklpack.economy.EconomyStorage;
import br.com.xklpack.economy.command.PayCommand;
import br.com.xklpack.economy.command.TopCommand;
import br.com.xklpack.jobs.JobCommand;
import br.com.xklpack.jobs.JobListener;
import br.com.xklpack.jobs.JobService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;

public final class XKLPack extends JavaPlugin {
    private EconomyStorage economyStorage;
    private EconomyService economyService;
    private JobService jobService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        boolean hadJobConfiguration = getConfig().isConfigurationSection("jobs.professions");
        getConfig().options().copyDefaults(true);
        if (!hadJobConfiguration) {
            getConfig().set("modules.jobs", true);
        }
        saveConfig();

        try {
            economyStorage = new EconomyStorage(this);
            economyStorage.open();
            economyService = new EconomyService(this, economyStorage);
            registerCommand("pay", new PayCommand(economyService));
            registerCommand("top", new TopCommand(economyService));

            if (getConfig().getBoolean("modules.jobs", true)) {
                jobService = new JobService(this, economyService);
                jobService.initialize(economyStorage.connection());
                JobCommand jobCommand = new JobCommand(this, jobService);
                registerCommand("job", jobCommand);
                getServer().getPluginManager().registerEvents(jobCommand, this);
                getServer().getPluginManager().registerEvents(new JobListener(this, jobService), this);
            }
        } catch (Exception exception) {
            getLogger().severe("Não foi possível iniciar o XKL PACK: " + exception.getMessage());
            exception.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("XKL PACK iniciado. Economia virtual, transferências e profissões estão ativas.");
    }

    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            throw new IllegalStateException("Comando /" + name + " não foi declarado em plugin.yml");
        }
        command.setExecutor(executor);
    }

    public EconomyService getEconomyService() {
        return economyService;
    }

    public EconomyStorage getEconomyStorage() {
        return economyStorage;
    }

    @Override
    public void onDisable() {
        if (economyStorage != null) {
            try {
                economyStorage.close();
            } catch (SQLException exception) {
                getLogger().severe("Erro ao fechar banco de dados: " + exception.getMessage());
            }
        }
        getLogger().info("XKL PACK finalizado.");
    }
}
