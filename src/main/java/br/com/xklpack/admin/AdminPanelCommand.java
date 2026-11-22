package br.com.xklpack.admin;

import br.com.xklpack.XKLPack;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AdminPanelCommand implements CommandExecutor, Listener {
    private static final int PAGE_SIZE = 45;
    private final XKLPack plugin;
    private final Map<UUID, PendingEdit> pending = new ConcurrentHashMap<>();

    public AdminPanelCommand(XKLPack plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Este painel só pode ser usado dentro do jogo.");
            return true;
        }
        if (!player.hasPermission("xklpack.admin.panel")) {
            player.sendMessage("§cVocê não tem permissão para abrir o painel administrativo.");
            return true;
        }
        openMain(player);
        return true;
    }

    private void openMain(Player player) {
        PanelHolder holder = new PanelHolder(player.getUniqueId(), "main", 0);
        Inventory inventory = Bukkit.createInventory(holder, 27, "§8§lPAINEL ADMINISTRATIVO XKL");
        holder.inventory = inventory;
        inventory.setItem(10, button(Material.GOLD_INGOT, "§6§lSALÁRIOS E PROFISSÕES", "§7Editar metas, salários, XP e gemas."));
        inventory.setItem(12, button(Material.IRON_INGOT, "§b§lBANCO E TAXAS", "§7Editar taxas de depósito e saque."));
        inventory.setItem(14, button(Material.CHEST, "§a§lLOJA DE COMPRAS", "§7Editar preços pagos em Coins/Gemas."));
        inventory.setItem(16, button(Material.HOPPER, "§e§lLOJA DE VENDAS", "§7Editar preços recebidos em Coins."));
        inventory.setItem(20, button(Material.EMERALD, "§a§lMOEDAS FÍSICAS", "§7Editar o valor das denominações."));
        inventory.setItem(22, button(Material.DIAMOND_PICKAXE, "§d§lKITS PROFISSIONAIS", "§7Editar materiais de evolução dos kits."));
        player.openInventory(inventory);
    }

    private void openSettings(Player player, String category, int page) {
        List<Entry> entries = entries(category);
        int pages = Math.max(1, (int) Math.ceil(entries.size() / (double) PAGE_SIZE));
        int safePage = Math.max(0, Math.min(page, pages - 1));
        PanelHolder holder = new PanelHolder(player.getUniqueId(), category, safePage);
        Inventory inventory = Bukkit.createInventory(holder, 54, "§8§lXKL ADMIN §7• §f" + categoryName(category));
        holder.inventory = inventory;

        int start = safePage * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < entries.size(); i++) {
            Entry entry = entries.get(start + i);
            inventory.setItem(i, settingItem(entry));
            holder.entries.put(i, entry);
        }
        for (int slot = 45; slot < 54; slot++) inventory.setItem(slot, button(Material.GRAY_STAINED_GLASS_PANE, " "));
        inventory.setItem(48, button(Material.ARROW, "§eVoltar"));
        holder.actions.put(48, "__back");
        inventory.setItem(49, button(Material.PAPER, "§fPágina " + (safePage + 1) + "/" + pages));
        if (safePage > 0) {
            inventory.setItem(45, button(Material.SPECTRAL_ARROW, "§ePágina anterior"));
            holder.actions.put(45, "__previous");
        }
        if (safePage + 1 < pages) {
            inventory.setItem(53, button(Material.SPECTRAL_ARROW, "§ePróxima página"));
            holder.actions.put(53, "__next");
        }
        if (entries.isEmpty()) inventory.setItem(22, button(Material.BARRIER, "§cNenhuma configuração encontrada"));
        player.openInventory(inventory);
    }

    private List<Entry> entries(String category) {
        List<Entry> result = new ArrayList<>();
        if ("salary".equals(category)) {
            add(result, "Salário base por meta", Material.GOLD_INGOT, FileType.MAIN, "jobs.salary.base-reward", 2, false);
            add(result, "Produção da primeira meta", Material.HAY_BLOCK, FileType.MAIN, "jobs.salary.production-goal", 0, false);
            add(result, "Aumento de produção por ciclo", Material.BARREL, FileType.MAIN, "jobs.salary.goal-increase-per-cycle", 0, false);
            add(result, "Multiplicador inicial", Material.EXPERIENCE_BOTTLE, FileType.MAIN, "jobs.salary.multiplier-start", 2, false);
            add(result, "Metas por aumento do multiplicador", Material.CLOCK, FileType.MAIN, "jobs.salary.multiplier-every-goals", 0, false);
            add(result, "Aumento do multiplicador", Material.AMETHYST_SHARD, FileType.MAIN, "jobs.salary.multiplier-increase-every-40-goals", 2, false);
            add(result, "XP para receber gema", Material.EMERALD, FileType.MAIN, "jobs.gems.every-xp", 0, false);
            add(result, "Gemas por recompensa", Material.DIAMOND, FileType.MAIN, "jobs.gems.amount", 0, false);
            add(result, "Nível máximo profissional", Material.NETHER_STAR, FileType.MAIN, "jobs.max-level", 0, false);
            add(result, "XP base por nível", Material.EXPERIENCE_BOTTLE, FileType.MAIN, "jobs.leveling.base-xp", 0, false);
            add(result, "Aumento de XP por nível", Material.EXPERIENCE_BOTTLE, FileType.MAIN, "jobs.leveling.xp-per-level", 0, false);
        } else if ("bank".equals(category)) {
            add(result, "Taxa de depósito (%)", Material.HOPPER, FileType.BANK, "atm.fees.deposit-percent", 2, false);
            add(result, "Taxa de saque (%)", Material.GOLD_NUGGET, FileType.BANK, "atm.fees.withdraw-percent", 2, false);
        } else if ("buy".equals(category)) {
            ConfigurationSection items = plugin.getAdminShopConfig().getConfigurationSection("items");
            if (items != null) {
                for (String id : items.getKeys(false)) {
                    String path = "items." + id + ".";
                    String currency = plugin.getAdminShopConfig().getString(path + "currency", "coins");
                    int scale = "gems".equalsIgnoreCase(currency) ? 0 : 2;
                    String name = ChatColor.stripColor(color(plugin.getAdminShopConfig().getString(path + "display-name", id)));
                    Material icon = Material.matchMaterial(plugin.getAdminShopConfig().getString(path + "material", "CHEST"));
                    result.add(new Entry(name + " (" + currency + ")", icon == null ? Material.CHEST : icon,
                            FileType.ADMIN_SHOP, path + "price", scale, false));
                }
            }
        } else if ("sell".equals(category)) {
            ConfigurationSection items = plugin.getSellShopConfig().getConfigurationSection("items");
            if (items != null) {
                for (String id : items.getKeys(false)) {
                    String path = "items." + id + ".";
                    String name = plugin.getSellShopConfig().getString(path + "display-name", id).replaceAll("<[^>]+>", "");
                    Material icon = Material.matchMaterial(plugin.getSellShopConfig().getString(path + "material", "CHEST"));
                    result.add(new Entry(name + " (Coins)", icon == null ? Material.CHEST : icon,
                            FileType.SELL_SHOP, path + "sell-price", 10, false));
                }
            }
        } else if ("coins".equals(category)) {
            ConfigurationSection denominations = plugin.getPhysicalCoinsConfig().getConfigurationSection("denominations");
            if (denominations != null) {
                for (String id : denominations.getKeys(false)) {
                    String path = "denominations." + id + ".";
                    String name = plugin.getPhysicalCoinsConfig().getString(path + "display-name", id);
                    Material icon = Material.matchMaterial(plugin.getPhysicalCoinsConfig().getString(path + "material", "EMERALD"));
                    result.add(new Entry(name + " • valor em Coins", icon == null ? Material.EMERALD : icon,
                            FileType.PHYSICAL_COINS, path + "value", 0, false));
                }
            }
        } else if ("kits".equals(category)) {
            ConfigurationSection professions = plugin.getConfig().getConfigurationSection("jobs.kits.professions");
            if (professions != null) {
                for (String job : professions.getKeys(false)) {
                    ConfigurationSection tiers = professions.getConfigurationSection(job + ".tiers");
                    if (tiers == null) continue;
                    List<String> levels = new ArrayList<>(tiers.getKeys(false));
                    levels.sort(Comparator.comparingInt(Integer::parseInt));
                    for (String level : levels) {
                        String path = "jobs.kits.professions." + job + ".tiers." + level + ".material";
                        String name = plugin.getConfig().getString("jobs.professions." + job + ".name", job);
                        Material icon = Material.matchMaterial(plugin.getConfig().getString(path, "CHEST"));
                        result.add(new Entry(name + " • nível " + level, icon == null ? Material.CHEST : icon,
                                FileType.MAIN, path, -1, true));
                    }
                }
            }
        }
        return result;
    }

    private void add(List<Entry> entries, String label, Material icon, FileType file, String path, int scale, boolean text) {
        entries.add(new Entry(label, icon, file, path, scale, text));
    }

    private ItemStack settingItem(Entry entry) {
        String value = readValue(entry);
        List<String> lore = new ArrayList<>();
        lore.add("§7Atual: §f" + value);
        lore.add("");
        lore.add(entry.text() ? "§eClique e digite um material Bukkit."
                : "§eClique e digite o novo valor no chat.");
        ItemStack item = new ItemStack(entry.icon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§e" + entry.label());
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private String readValue(Entry entry) {
        return switch (entry.file()) {
            case MAIN -> plugin.getConfig().getString(entry.path(), "não definido");
            case BANK -> plugin.getBankConfig().getString(entry.path(), "não definido");
            case ADMIN_SHOP -> plugin.getAdminShopConfig().getString(entry.path(), "não definido");
            case SELL_SHOP -> plugin.getSellShopConfig().getString(entry.path(), "não definido");
            case PHYSICAL_COINS -> plugin.getPhysicalCoinsConfig().getString(entry.path(), "não definido");
        };
    }

    private void beginEdit(Player player, PanelHolder holder, Entry entry) {
        pending.put(player.getUniqueId(), new PendingEdit(holder.category, holder.page, entry));
        player.closeInventory();
        player.sendMessage("§6§lXKL ADMIN §7• §f" + entry.label());
        player.sendMessage(entry.text()
                ? "§eDigite o novo material Bukkit (ex.: NETHERITE_PICKAXE)."
                : "§eDigite o novo valor numérico.");
        player.sendMessage("§7Digite §ccancelar §7para desistir.");
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        PendingEdit edit = pending.get(event.getPlayer().getUniqueId());
        if (edit == null) return;
        event.setCancelled(true);
        String message = event.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin, () -> applyEdit(event.getPlayer(), edit, message));
    }

    private void applyEdit(Player player, PendingEdit pendingEdit, String input) {
        if (!player.isOnline() || !player.hasPermission("xklpack.admin.panel")) {
            pending.remove(player.getUniqueId());
            return;
        }
        if ("cancelar".equalsIgnoreCase(input)) {
            pending.remove(player.getUniqueId());
            player.sendMessage("§cEdição cancelada.");
            openSettings(player, pendingEdit.category(), pendingEdit.page());
            return;
        }

        Entry entry = pendingEdit.entry();
        try {
            if (entry.text()) {
                Material material = Material.matchMaterial(input.toUpperCase());
                if (material == null) throw new IllegalArgumentException("Material Bukkit não reconhecido.");
                setValue(entry, material.name());
            } else {
                BigDecimal value = new BigDecimal(input.replace(',', '.'));
                if (value.signum() < 0 || (value.signum() == 0 && entry.file() != FileType.BANK)) {
                    throw new IllegalArgumentException("O valor precisa ser maior que zero.");
                }
                value = value.setScale(entry.scale(), RoundingMode.UNNECESSARY);
                if (entry.file() == FileType.BANK && value.compareTo(new BigDecimal("100")) >= 0) {
                    throw new IllegalArgumentException("A taxa precisa ser menor que 100%.");
                }
                setValue(entry, entry.scale() == 0 ? value.intValueExact() : value.doubleValue());
            }
            pending.remove(player.getUniqueId());
            player.sendMessage("§aConfiguração salva: §f" + entry.label());
            if (entry.file() == FileType.SELL_SHOP && plugin.getSellCommand() != null) {
                plugin.getSellCommand().reloadPrices();
            }
            openSettings(player, pendingEdit.category(), pendingEdit.page());
        } catch (Exception exception) {
            player.sendMessage("§cValor inválido: " + exception.getMessage());
            player.sendMessage("§7Tente novamente ou digite §ccancelar§7.");
        }
    }

    private void setValue(Entry entry, Object value) throws Exception {
        switch (entry.file()) {
            case MAIN -> {
                plugin.getConfig().set(entry.path(), value);
                plugin.saveConfig();
            }
            case BANK -> {
                plugin.getBankConfig().set(entry.path(), value);
                plugin.saveBankConfig();
            }
            case ADMIN_SHOP -> {
                plugin.getAdminShopConfig().set(entry.path(), value);
                plugin.saveAdminShopConfig();
            }
            case SELL_SHOP -> {
                plugin.getSellShopConfig().set(entry.path(), value);
                plugin.saveSellShopConfig();
            }
            case PHYSICAL_COINS -> {
                plugin.getPhysicalCoinsConfig().set(entry.path(), value);
                plugin.savePhysicalCoinsConfig();
            }
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof PanelHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.owner.equals(player.getUniqueId())) return;
        int slot = event.getRawSlot();
        if (slot >= event.getView().getTopInventory().getSize()) return;

        if ("main".equals(holder.category)) {
            String category = switch (slot) {
                case 10 -> "salary";
                case 12 -> "bank";
                case 14 -> "buy";
                case 16 -> "sell";
                case 20 -> "coins";
                case 22 -> "kits";
                default -> null;
            };
            if (category != null) openSettings(player, category, 0);
            return;
        }

        String action = holder.actions.get(slot);
        if ("__back".equals(action)) {
            openMain(player);
            return;
        }
        if ("__previous".equals(action)) {
            openSettings(player, holder.category, holder.page - 1);
            return;
        }
        if ("__next".equals(action)) {
            openSettings(player, holder.category, holder.page + 1);
            return;
        }
        Entry entry = holder.entries.get(slot);
        if (entry != null) beginEdit(player, holder, entry);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof PanelHolder) event.setCancelled(true);
    }

    private String categoryName(String category) {
        return switch (category) {
            case "salary" -> "SALÁRIOS";
            case "bank" -> "BANCO";
            case "buy" -> "LOJA DE COMPRAS";
            case "sell" -> "LOJA DE VENDAS";
            case "kits" -> "KITS";
            case "coins" -> "MOEDAS FÍSICAS";
            default -> category.toUpperCase();
        };
    }

    private ItemStack button(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(lore));
        item.setItemMeta(meta);
        return item;
    }

    private String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }

    private enum FileType { MAIN, BANK, ADMIN_SHOP, SELL_SHOP, PHYSICAL_COINS }
    private record Entry(String label, Material icon, FileType file, String path, int scale, boolean text) {}
    private record PendingEdit(String category, int page, Entry entry) {}

    private static final class PanelHolder implements InventoryHolder {
        private final UUID owner;
        private final String category;
        private final int page;
        private final Map<Integer, Entry> entries = new HashMap<>();
        private final Map<Integer, String> actions = new HashMap<>();
        private Inventory inventory;

        private PanelHolder(UUID owner, String category, int page) {
            this.owner = owner;
            this.category = category;
            this.page = page;
            this.actions.put(48, "__back");
        }

        @Override public Inventory getInventory() { return inventory; }
    }
}
