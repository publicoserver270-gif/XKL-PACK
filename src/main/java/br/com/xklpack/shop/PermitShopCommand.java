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
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PermitShopCommand implements CommandExecutor, Listener {
    private static final int[] SLOTS = {10, 12, 14, 16};
    private final XKLPack plugin;
    private final EconomyService economy;
    private final NamespacedKey permitKey;

    public PermitShopCommand(XKLPack plugin, EconomyService economy) {
        this.plugin = plugin;
        this.economy = economy;
        this.permitKey = new NamespacedKey(plugin, "player_shop_permit");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Este comando só pode ser usado por jogadores.");
            return true;
        }
        open(player);
        return true;
    }

    private void open(Player player) {
        PermitHolder holder = new PermitHolder(player);
        Inventory inventory = Bukkit.createInventory(holder, 27,
                color(plugin.getPermitsConfig().getString("settings.title", "&8&lALVARÁS COMERCIAIS")));
        holder.inventory = inventory;
        ItemStack pane = button(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inventory.getSize(); i++) {
            if (i < 9 || i > 17) inventory.setItem(i, pane);
        }
        List<String> permits = plugin.getPermitsConfig().getStringList("settings.order");
        for (int i = 0; i < Math.min(SLOTS.length, permits.size()); i++) {
            String id = permits.get(i);
            String path = "permits." + id + ".";
            if (!plugin.getPermitsConfig().getBoolean(path + "enabled", true)) continue;
            Material icon = Material.matchMaterial(plugin.getPermitsConfig().getString(path + "icon", "PAPER"));
            if (icon == null) icon = Material.PAPER;
            String name = plugin.getPermitsConfig().getString(path + "name", id);
            BigDecimal price = price(id);
            int radius = Math.max(2, plugin.getPermitsConfig().getInt(path + "area-radius", 4));
            ItemStack display = button(icon, color(name), List.of(
                    "§7Área reservada: §f" + ((radius * 2) + 1) + "x" + ((radius * 2) + 1) + " blocos",
                    "§7Raio: §f" + radius + " blocos",
                    "§7Preço: §a" + price.toPlainString() + " Coins",
                    "", "§eClique para comprar este alvará."));
            inventory.setItem(SLOTS[i], display);
            holder.actions.put(SLOTS[i], id);
        }
        inventory.setItem(22, button(Material.BARRIER, "§cFechar", List.of("§7Fechar a loja de alvarás.")));
        player.openInventory(inventory);
    }

    private BigDecimal price(String id) {
        return new BigDecimal(plugin.getPermitsConfig().getString("permits." + id + ".price", "1000"));
    }

    private void purchase(Player player, String id) {
        String path = "permits." + id + ".";
        if (!plugin.getPermitsConfig().getBoolean(path + "enabled", true)) return;
        BigDecimal price;
        try {
            price = price(id).setScale(2);
            if (price.signum() <= 0) throw new IllegalArgumentException();
        } catch (Exception exception) {
            player.sendMessage("§cO preço deste alvará está configurado incorretamente. Avise a administração.");
            return;
        }

        ItemStack permit = createPermit(id);
        Inventory simulated = Bukkit.createInventory(null, 36);
        ItemStack[] original = player.getInventory().getStorageContents();
        ItemStack[] copy = new ItemStack[original.length];
        for (int i = 0; i < original.length; i++) copy[i] = original[i] == null ? null : original[i].clone();
        simulated.setContents(copy);
        if (!simulated.addItem(permit).isEmpty()) {
            player.sendMessage("§cLibere espaço no inventário para receber o alvará.");
            return;
        }
        try {
            if (!economy.spendCoinsForShop(player.getUniqueId(), player.getName(), price)) {
                player.sendMessage("§cVocê não possui Coins suficientes para este alvará.");
                return;
            }
            player.getInventory().setStorageContents(simulated.getStorageContents());
            player.sendMessage("§aAlvará comprado: §f" + ChatColor.stripColor(color(
                    plugin.getPermitsConfig().getString(path + "name", id))) + "§a.");
            player.sendMessage("§7Use o alvará no local onde deseja construir sua loja.");
        } catch (SQLException | IllegalArgumentException exception) {
            player.sendMessage("§cNão foi possível concluir a compra: " + exception.getMessage());
        }
    }

    private ItemStack createPermit(String id) {
        String path = "permits." + id + ".";
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(plugin.getPermitsConfig().getString(path + "item-name", "&6Alvará Comercial")));
        meta.setLore(List.of(
                color("&7Tipo: &f" + plugin.getPermitsConfig().getString(path + "name", id)),
                color("&7Área: &f" + (plugin.getPermitsConfig().getInt(path + "area-radius", 4) * 2 + 1)
                        + "x" + (plugin.getPermitsConfig().getInt(path + "area-radius", 4) * 2 + 1) + " blocos"),
                color("&8Documento oficial do XKL PACK")
        ));
        meta.getPersistentDataContainer().set(permitKey, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    public String identifyPermit(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(permitKey, PersistentDataType.STRING);
        return id != null && plugin.getPermitsConfig().contains("permits." + id) ? id : null;
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
        if (!(event.getView().getTopInventory().getHolder() instanceof PermitHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || !holder.owner.equals(player.getUniqueId())) return;
        if (event.getRawSlot() == 22) {
            player.closeInventory();
            return;
        }
        String id = holder.actions.get(event.getRawSlot());
        if (id != null) purchase(player, id);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof PermitHolder) event.setCancelled(true);
    }

    private String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }

    private static final class PermitHolder implements InventoryHolder {
        private final java.util.UUID owner;
        private final Map<Integer, String> actions = new HashMap<>();
        private Inventory inventory;
        private PermitHolder(Player player) { this.owner = player.getUniqueId(); }
        @Override public Inventory getInventory() { return inventory; }
    }
}
