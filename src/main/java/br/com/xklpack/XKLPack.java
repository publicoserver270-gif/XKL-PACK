package br.com.xklpack;

import br.com.xklpack.bank.AtmNpcCommand;
import br.com.xklpack.bank.AtmNpcListener;
import br.com.xklpack.bank.BankCommand;
import br.com.xklpack.bank.BankListener;
import br.com.xklpack.bank.BankMenu;
import br.com.xklpack.bank.BankService;
import br.com.xklpack.admin.AdminPanelCommand;
import br.com.xklpack.economy.EconomyService;
import br.com.xklpack.economy.WalletCommand;
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
import br.com.xklpack.shop.EquipmentService;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
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
    private SellCommand sellCommand;
    private EquipmentService equipmentService;

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
        if (configVersion < 4) {
            getConfig().set("modules.admin-panel", true);
            getConfig().set("config-version", 4);
        }
        if (configVersion < 5) {
            getConfig().set("modules.wallet", true);
            getConfig().set("config-version", 5);
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

            if (getConfig().getBoolean("modules.wallet", true)) {
                WalletCommand walletCommand = new WalletCommand(this, economyService);
                registerCommand("carteira", walletCommand);
                getServer().getPluginManager().registerEvents(walletCommand, this);
                getLogger().info("Carteira virtual /carteira ativada.");
            }

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
                sellCommand = new SellCommand(this, economyService);
                registerCommand("sell", sellCommand);
                getServer().getPluginManager().registerEvents(sellCommand, this);
                var sellItems = sellShopConfig.getConfigurationSection("items");
                int sellItemCount = sellItems == null ? 0 : sellItems.getKeys(false).size();
                getLogger().info("Loja de vendas /sell ativada com " + sellItemCount + " itens configurados.");
            }

            if (getConfig().getBoolean("modules.shops", true)) {
                equipmentService = new EquipmentService(this);
                ShopCommand shopCommand = new ShopCommand(this, economyService, equipmentService);
                registerCommand("loja", shopCommand);
                getServer().getPluginManager().registerEvents(shopCommand, this);
                getServer().getPluginManager().registerEvents(equipmentService, this);
                getServer().getPluginManager().registerEvents(new ExcavatorListener(this), this);
                getLogger().info("Loja administrativa /loja ativada.");
            }

            if (getConfig().getBoolean("modules.admin-panel", true)) {
                AdminPanelCommand adminPanel = new AdminPanelCommand(this);
                registerCommand("xkladmin", adminPanel);
                getServer().getPluginManager().registerEvents(adminPanel, this);
                getLogger().info("Painel administrativo /xkladmin ativado.");
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
    public SellCommand getSellCommand() { return sellCommand; }
    public EquipmentService getEquipmentService() { return equipmentService; }
    public void saveBankConfig() throws IOException { bankConfig.save(new File(getDataFolder(), "bank/settings.yml")); }
    public void savePhysicalCoinsConfig() throws IOException { physicalCoinsConfig.save(new File(getDataFolder(), "economy/physical-coins.yml")); }
    public void saveSellShopConfig() throws IOException { sellShopConfig.save(new File(getDataFolder(), "sell-shop.yml")); }
    public void saveAdminShopConfig() throws IOException { adminShopConfig.save(new File(getDataFolder(), "admin-shop.yml")); }
    public EconomyService getEconomyService() { return economyService; }
    public JobService getJobService() { return jobService; }
    public KitService getKitService() { return kitService; }
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
