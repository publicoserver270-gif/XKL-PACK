package br.com.xklpack;

import br.com.xklpack.bank.AtmNpcCommand;
import br.com.xklpack.bank.AtmNpcListener;
import br.com.xklpack.bank.BankCommand;
import br.com.xklpack.bank.BankListener;
import br.com.xklpack.bank.BankMenu;
import br.com.xklpack.bank.BankService;
import br.com.xklpack.economy.EconomyService;
import br.com.xklpack.economy.EconomyStorage;
import br.com.xklpack.economy.command.PayCommand;
import br.com.xklpack.economy.command.TopCommand;
import br.com.xklpack.jobs.JobCommand;
import br.com.xklpack.jobs.JobListener;
import br.com.xklpack.jobs.JobService;
import br.com.xklpack.jobs.KitService;
import br.com.xklpack.pouch.MoneyPouchCommand;
import br.com.xklpack.pouch.MoneyPouchListener;
import br.com.xklpack.pouch.MoneyPouchService;
import br.com.xklpack.shop.SellCommand;
import br.com.xklpack.shop.ShopCommand;
import br.com.xklpack.shop.ExcavatorListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.SQLException;

public final class XKLPack extends JavaPlugin {
    private EconomyStorage economyStorage;
    private EconomyService economyService;
    private JobService jobService;
    private KitService kitService;
    private FileConfiguration bankConfig;
    private FileConfiguration physicalCoinsConfig;
    private FileConfiguration sellShopConfig;
    private FileConfiguration adminShopConfig;
    private BankService bankService;
    private MoneyPouchService moneyPouchService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        boolean hadJobConfiguration = getConfig().isConfigurationSection("jobs.professions");
        int configVersion = getConfig().getInt("config-version", 0);
        getConfig().options().copyDefaults(true);
        if (!hadJobConfiguration) getConfig().set("modules.jobs", true);
        if (configVersion < 2) {
            // Migra instalações que receberam modules.selling=false como valor padrão
            // antes da implementação do comando /sell.
            getConfig().set("modules.selling", true);
            getConfig().set("config-version", 2);
        }
        if (configVersion < 3) {
            getConfig().set("modules.shops", true);
            getConfig().set("config-version", 3);
        }
        saveConfig();

        saveResourceIfMissing("bank/settings.yml");
        saveResourceIfMissing("economy/physical-coins.yml");
        saveResourceIfMissing("sell-shop.yml");
        saveResourceIfMissing("admin-shop.yml");
        bankConfig = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "bank/settings.yml"));
        physicalCoinsConfig = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "economy/physical-coins.yml"));
        sellShopConfig = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "sell-shop.yml"));
        adminShopConfig = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "admin-shop.yml"));

        try {
            economyStorage = new EconomyStorage(this);
            economyStorage.open();
            economyService = new EconomyService(this, economyStorage);
            registerCommand("pay", new PayCommand(economyService));
            registerCommand("top", new TopCommand(economyService));

            if (getConfig().getBoolean("modules.bank", false) && bankConfig.getBoolean("atm.enabled", true)) {
                bankService = new BankService(this);
                BankMenu bankMenu = new BankMenu(this, bankService);
                registerCommand("atm", new BankCommand(bankMenu));
                registerCommand("xklatm", new AtmNpcCommand(this));
                getServer().getPluginManager().registerEvents(new AtmNpcListener(this, bankMenu), this);
                if (getServer().getPluginManager().getPlugin("Citizens") == null) {
                    getLogger().warning("Citizens não encontrado: os caixas por NPC ficarão indisponíveis até instalar o Citizens.");
                } else {
                    getLogger().info("Integração com NPCs do Citizens ativada. Use /xklatm npc link <id>.");
                }
                getServer().getPluginManager().registerEvents(new BankListener(this, bankService, bankMenu), this);
                if (bankConfig.getBoolean("money-pouch.enabled", true)) {
                    moneyPouchService = new MoneyPouchService(this, bankService);
                    moneyPouchService.initialize();
                    registerCommand("xklpouch", new MoneyPouchCommand(moneyPouchService));
                    getServer().getPluginManager().registerEvents(new MoneyPouchListener(this, moneyPouchService, bankService), this);
                    getLogger().info("Saco de moedas físicas ativado.");
                }
                getLogger().info("Sistema bancário ATM ativado.");
            }

            if (getConfig().getBoolean("modules.jobs", true)) {
                jobService = new JobService(this, economyService);
                jobService.initialize(economyStorage.connection());
                kitService = new KitService(this);
                kitService.initialize(economyStorage.connection());
                JobCommand jobCommand = new JobCommand(this, jobService, kitService);
                registerCommand("job", jobCommand);
                getServer().getPluginManager().registerEvents(jobCommand, this);
                getServer().getPluginManager().registerEvents(new JobListener(this, jobService, kitService), this);
                getServer().getPluginManager().registerEvents(kitService, this);
            }

            if (getConfig().getBoolean("modules.selling", true)) {
                SellCommand sellCommand = new SellCommand(this, economyService);
                registerCommand("sell", sellCommand);
                getServer().getPluginManager().registerEvents(sellCommand, this);
                var sellItems = sellShopConfig.getConfigurationSection("items");
                int sellItemCount = sellItems == null ? 0 : sellItems.getKeys(false).size();
                getLogger().info("Loja de vendas /sell ativada com " + sellItemCount + " itens configurados.");
            }

            if (getConfig().getBoolean("modules.shops", true)) {
                ShopCommand shopCommand = new ShopCommand(this, economyService);
                registerCommand("loja", shopCommand);
                getServer().getPluginManager().registerEvents(shopCommand, this);
                getServer().getPluginManager().registerEvents(new ExcavatorListener(this), this);
                getLogger().info("Loja administrativa /loja ativada.");
            }
        } catch (Exception exception) {
            getLogger().severe("Não foi possível iniciar o XKL PACK: " + exception.getMessage());
            exception.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("XKL PACK iniciado. Economia virtual, banco e profissões disponíveis conforme configuração.");
    }

    private void saveResourceIfMissing(String path) {
        File file = new File(getDataFolder(), path);
        if (!file.exists()) saveResource(path, false);
    }

    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) throw new IllegalStateException("Comando /" + name + " não foi declarado em plugin.yml");
        command.setExecutor(executor);
    }

    public FileConfiguration getBankConfig() { return bankConfig; }
    public FileConfiguration getPhysicalCoinsConfig() { return physicalCoinsConfig; }
    public FileConfiguration getSellShopConfig() { return sellShopConfig; }
    public FileConfiguration getAdminShopConfig() { return adminShopConfig; }
    public EconomyService getEconomyService() { return economyService; }
    public EconomyStorage getEconomyStorage() { return economyStorage; }
    public BankService getBankService() { return bankService; }
    public MoneyPouchService getMoneyPouchService() { return moneyPouchService; }

    @Override
    public void onDisable() {
        if (economyStorage != null) {
            try { economyStorage.close(); }
            catch (SQLException exception) { getLogger().severe("Erro ao fechar banco de dados: " + exception.getMessage()); }
        }
        getLogger().info("XKL PACK finalizado.");
    }
}
