package br.com.xklpack.shop;

import br.com.xklpack.XKLPack;
import br.com.xklpack.economy.EconomyService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ShopCommand implements CommandExecutor, Listener {
    private static final int PAGE_SIZE = 45;
    private final XKLPack plugin;
    private final EconomyService economy;
    private final NamespacedKey excavatorKey;
    private final NamespacedKey specialToolKey;

    public ShopCommand(XKLPack plugin, EconomyService economy) {
        this.plugin = plugin;
        this.economy = economy;
        this.excavatorKey = new NamespacedKey(plugin, "excavator_3x3");
        this.specialToolKey = new NamespacedKey(plugin, "special_tool");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Este comando só pode ser usado por jogadores.");
            return true;
        }
        openMain(player);
        return true;
    }

    private void openMain(Player player) {
        String title = color(plugin.getAdminShopConfig().getString("settings.title", "&8&lLOJA XKL"));
        ShopHolder holder = new ShopHolder(player.getUniqueId(), null, 0);
        Inventory inventory = Bukkit.createInventory(holder, 27, title);
        holder.inventory = inventory;

        ConfigurationSection categories = plugin.getAdminShopConfig().getConfigurationSection("categories");
        if (categories == null) {
            player.sendMessage("§cA loja administrativa não possui categorias configuradas.");
            return;
        }
        for (String id : categories.getKeys(false)) {
            String path = "categories." + id + ".";
            int slot = plugin.getAdminShopConfig().getInt(path + "slot", -1);
            Material material = Material.matchMaterial(plugin.getAdminShopConfig().getString(path + "icon", "CHEST"));
            if (slot < 0 || slot >= inventory.getSize() || material == null) continue;
            String name = plugin.getAdminShopConfig().getString(path + "name", id);
            inventory.setItem(slot, button(material, color(name), List.of("§7Clique para abrir esta categoria.")));
            holder.actions.put(slot, id);
        }
        player.openInventory(inventory);
    }

    private void openCategory(Player player, String category, int page) {
        ConfigurationSection categories = plugin.getAdminShopConfig().getConfigurationSection("categories");
        String categoryName = categories == null ? category
                : categories.getString(category + ".name", category);
        String titleTemplate = plugin.getAdminShopConfig().getString("settings.category-title", "&8&l{category}");
        String title = color(titleTemplate.replace("{category}", stripColor(categoryName)));
        ShopHolder holder = new ShopHolder(player.getUniqueId(), category, page);
        Inventory inventory = Bukkit.createInventory(holder, 54, title);
        holder.inventory = inventory;

        List<String> products = productsFor(category);
        int pages = Math.max(1, (int) Math.ceil(products.size() / (double) PAGE_SIZE));
        int safePage = Math.max(0, Math.min(page, pages - 1));
        holder.page = safePage;
        int start = safePage * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < products.size(); i++) {
            String id = products.get(start + i);
            ItemStack display = createProduct(id, true);
            if (display != null) {
                inventory.setItem(i, display);
                holder.actions.put(i, id);
            }
        }

        for (int slot = 45; slot < 54; slot++) inventory.setItem(slot, button(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
        inventory.setItem(48, button(Material.ARROW, "§eVoltar", List.of("§7Voltar às categorias.")));
        holder.actions.put(48, "__back");
        inventory.setItem(49, button(Material.PAPER, "§fPágina " + (safePage + 1) + "/" + pages, List.of("§7Itens: " + products.size())));
        if (safePage > 0) {
            inventory.setItem(45, button(Material.SPECTRAL_ARROW, "§ePágina anterior", List.of()));
            holder.actions.put(45, "__previous");
        }
        if (safePage + 1 < pages) {
            inventory.setItem(53, button(Material.SPECTRAL_ARROW, "§ePróxima página", List.of()));
            holder.actions.put(53, "__next");
        }
        if (products.isEmpty()) {
            inventory.setItem(22, button(Material.BARRIER, "§cCategoria sem itens", List.of("§7A administração ainda não cadastrou produtos.")));
        }
        player.openInventory(inventory);
    }

    private List<String> productsFor(String category) {
        ConfigurationSection items = plugin.getAdminShopConfig().getConfigurationSection("items");
        if (items == null) return List.of();
        List<String> ids = new ArrayList<>();
        for (String id : items.getKeys(false)) {
            String path = "items." + id + ".";
            if (plugin.getAdminShopConfig().getBoolean(path + "enabled", true)
                    && category.equalsIgnoreCase(plugin.getAdminShopConfig().getString(path + "category", ""))) {
                ids.add(id);
            }
        }
        return ids;
    }

    private ItemStack createProduct(String id, boolean forDisplay) {
        String path = "items." + id + ".";
        Material material = Material.matchMaterial(plugin.getAdminShopConfig().getString(path + "material", ""));
        if (material == null || !material.isItem()) return null;
        int amount = Math.max(1, plugin.getAdminShopConfig().getInt(path + "amount", 1));
        ItemStack item = new ItemStack(material, Math.min(amount, material.getMaxStackSize()));
        ItemMeta meta = item.getItemMeta();
        String name = plugin.getAdminShopConfig().getString(path + "display-name", material.name());
        meta.setDisplayName(color(name));

        List<String> lore = new ArrayList<>();
        for (String line : plugin.getAdminShopConfig().getStringList(path + "lore")) lore.add(color(line));
        if (!lore.isEmpty()) lore.add("");
        String currency = plugin.getAdminShopConfig().getString(path + "currency", "coins").toLowerCase();
        String price = plugin.getAdminShopConfig().getString(path + "price", "0");
        lore.add("§7Quantidade: §f" + amount);
        lore.add("§7Preço: " + ("gems".equals(currency) ? "§d" : "§a") + price + ("gems".equals(currency) ? " Gemas" : " Coins"));
        if (forDisplay) {
            lore.add("");
            lore.add("§eClique para comprar!");
        }
        meta.setLore(lore);
        if ("ferramentas".equalsIgnoreCase(plugin.getAdminShopConfig().getString(path + "category", ""))) {
            meta.getPersistentDataContainer().set(specialToolKey, PersistentDataType.BYTE, (byte) 1);
        }
        if ("excavator_3x3".equalsIgnoreCase(plugin.getAdminShopConfig().getString(path + "special", ""))) {
            meta.getPersistentDataContainer().set(excavatorKey, PersistentDataType.BYTE, (byte) 1);
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ShopHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player) || !holder.owner.equals(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            if (event.isShiftClick()) event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        String action = holder.actions.get(event.getRawSlot());
        if (action == null) return;
        if (action.equals("__back")) {
            openMain(player);
        } else if (action.equals("__previous")) {
            openCategory(player, holder.category, holder.page - 1);
        } else if (action.equals("__next")) {
            openCategory(player, holder.category, holder.page + 1);
        } else if (holder.category == null) {
            openCategory(player, action, 0);
        } else {
            purchase(player, action);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ShopHolder) event.setCancelled(true);
    }

    private void purchase(Player player, String id) {
        String path = "items." + id + ".";
        if (!plugin.getAdminShopConfig().getBoolean(path + "enabled", true)) return;
        String currency = plugin.getAdminShopConfig().getString(path + "currency", "coins").toLowerCase();
        String category = plugin.getAdminShopConfig().getString(path + "category", "");
        if ("gems".equals(currency) && !"ferramentas".equalsIgnoreCase(category)) {
            player.sendMessage("§cGemas só podem ser usadas para comprar ferramentas.");
            return;
        }
        if (!"coins".equals(currency) && !"gems".equals(currency)) {
            player.sendMessage("§cMoeda inválida na configuração deste produto.");
            return;
        }

        final BigDecimal price;
        try {
            price = new BigDecimal(plugin.getAdminShopConfig().getString(path + "price", "0"))
                    .setScale("gems".equals(currency) ? 0 : 2, RoundingMode.UNNECESSARY);
        } catch (Exception exception) {
            player.sendMessage("§cPreço inválido para este produto. Avise a administração.");
            return;
        }
        if (price.signum() <= 0) {
            player.sendMessage("§cEste produto ainda não possui preço válido.");
            return;
        }

        ItemStack product = createProduct(id, false);
        if (product == null) {
            player.sendMessage("§cEste produto possui um material inválido na configuração.");
            return;
        }
        // Simula a entrega antes de debitar para nunca cobrar quando o inventário está cheio.
        Inventory simulated = Bukkit.createInventory(null, 36);
        ItemStack[] original = player.getInventory().getStorageContents();
        ItemStack[] copy = new ItemStack[original.length];
        for (int i = 0; i < original.length; i++) copy[i] = original[i] == null ? null : original[i].clone();
        simulated.setContents(copy);
        if (!simulated.addItem(product).isEmpty()) {
            player.sendMessage("§cLibere espaço no inventário antes de comprar.");
            return;
        }

        try {
            boolean paid = "gems".equals(currency)
                    ? economy.spendGemsForTool(player.getUniqueId(), player.getName(), price)
                    : economy.spendCoinsForShop(player.getUniqueId(), player.getName(), price);
            if (!paid) {
                player.sendMessage("§cSaldo insuficiente de " + ("gems".equals(currency) ? "Gemas" : "Coins") + ".");
                return;
            }
            player.getInventory().setStorageContents(simulated.getStorageContents());
            player.sendMessage("§aCompra concluída: §f" + ChatColor.stripColor(color(
                    plugin.getAdminShopConfig().getString(path + "display-name", id)))
                    + " §7por §e" + price.toPlainString() + ("gems".equals(currency) ? " Gemas." : " Coins."));
        } catch (SQLException | IllegalArgumentException exception) {
            player.sendMessage("§cNão foi possível concluir a compra: " + exception.getMessage());
        }
    }

    private String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }

    private String stripColor(String value) {
        return ChatColor.stripColor(color(value));
    }

    private static final class ShopHolder implements InventoryHolder {
        private final java.util.UUID owner;
        private final String category;
        private int page;
        private final Map<Integer, String> actions = new HashMap<>();
        private Inventory inventory;

        private ShopHolder(java.util.UUID owner, String category, int page) {
            this.owner = owner;
            this.category = category;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
