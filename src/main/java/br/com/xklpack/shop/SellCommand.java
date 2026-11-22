package br.com.xklpack.shop;

import br.com.xklpack.XKLPack;
import br.com.xklpack.bank.BankService;
import br.com.xklpack.economy.EconomyService;
import br.com.xklpack.pouch.MoneyPouchService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SellCommand implements CommandExecutor, Listener {
    private static final int SELL_SLOTS = 45;
    private static final int PREVIEW_SLOT = 47;
    private static final int CONFIRM_SLOT = 49;
    private static final int CANCEL_SLOT = 51;

    private final XKLPack plugin;
    private final EconomyService economy;
    private final Map<Material, BigDecimal> prices = new HashMap<>();

    public SellCommand(XKLPack plugin, EconomyService economy) {
        this.plugin = plugin;
        this.economy = economy;
        loadPrices();
    }

    private void loadPrices() {
        ConfigurationSection items = plugin.getSellShopConfig().getConfigurationSection("items");
        if (items == null) {
            plugin.getLogger().warning("sell-shop.yml não possui a seção items; /sell ficará sem itens configurados.");
            return;
        }
        for (String id : items.getKeys(false)) {
            String path = "items." + id + ".";
            Material material = Material.matchMaterial(plugin.getSellShopConfig().getString(path + "material", ""));
            BigDecimal price;
            try {
                price = new BigDecimal(plugin.getSellShopConfig().getString(path + "sell-price", "0"));
            } catch (NumberFormatException exception) {
                plugin.getLogger().warning("Preço inválido na tabela /sell: " + id);
                continue;
            }
            if (material == null || !material.isItem() || material == Material.TOTEM_OF_UNDYING || price.signum() <= 0) {
                continue;
            }
            if (prices.putIfAbsent(material, price) != null) {
                plugin.getLogger().warning("Material duplicado na tabela /sell: " + material.name() + ". Foi mantido o primeiro preço.");
            }
        }
        plugin.getLogger().info("Tabela /sell carregada: " + prices.size() + " materiais com preço válido.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Este comando só pode ser usado por jogadores.");
            return true;
        }
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            player.sendMessage("§cA loja de vendas só pode ser usada nos modos Survival e Adventure.");
            return true;
        }
        if (prices.isEmpty()) {
            player.sendMessage("§cA tabela de preços da loja de vendas não está carregada. Avise a administração.");
            return true;
        }
        open(player);
        return true;
    }

    private void open(Player player) {
        SellHolder holder = new SellHolder();
        Inventory inventory = Bukkit.createInventory(holder, 54, "§8§lLOJA DE VENDAS");
        holder.inventory = inventory;
        holder.owner = player.getUniqueId();

        for (int slot = 45; slot < 54; slot++) {
            inventory.setItem(slot, button(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
        }
        inventory.setItem(PREVIEW_SLOT, button(Material.PAPER, "§e§lPrévia da venda",
                List.of("§7Coloque os itens nos espaços acima.", "§7O valor será calculado automaticamente.")));
        inventory.setItem(CONFIRM_SLOT, button(Material.LIME_DYE, "§a§lCONFIRMAR VENDA",
                List.of("§7Confirme para receber Coins.", "§8Somente itens da tabela são aceitos.")));
        inventory.setItem(CANCEL_SLOT, button(Material.RED_DYE, "§c§lCANCELAR",
                List.of("§7Fecha a loja e devolve seus itens.")));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SellHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!holder.owner.equals(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot >= event.getView().getTopInventory().getSize()) {
            if (event.isShiftClick()) {
                ItemStack current = event.getCurrentItem();
                if (!isSellable(current)) {
                    event.setCancelled(true);
                } else {
                    schedulePreview(player, holder);
                }
            }
            return;
        }

        if (rawSlot >= SELL_SLOTS) {
            event.setCancelled(true);
            if (rawSlot == CONFIRM_SLOT) confirm(player, holder);
            else if (rawSlot == CANCEL_SLOT) player.closeInventory();
            return;
        }

        if (event.getClick() == ClickType.DOUBLE_CLICK) {
            event.setCancelled(true);
            return;
        }

        ItemStack cursor = event.getCursor();
        if (isRealItem(cursor) && !isSellable(cursor)) {
            event.setCancelled(true);
            player.sendMessage("§cEsse item não está na tabela de vendas ou possui dados especiais.");
            return;
        }

        if (event.getClick() == ClickType.NUMBER_KEY) {
            ItemStack hotbar = player.getInventory().getItem(event.getHotbarButton());
            if (isRealItem(hotbar) && !isSellable(hotbar)) {
                event.setCancelled(true);
                player.sendMessage("§cEsse item não pode ser colocado na loja de vendas.");
                return;
            }
        } else if (event.getClick() == ClickType.SWAP_OFFHAND) {
            ItemStack offhand = player.getInventory().getItemInOffHand();
            if (isRealItem(offhand) && !isSellable(offhand)) {
                event.setCancelled(true);
                player.sendMessage("§cEsse item não pode ser colocado na loja de vendas.");
                return;
            }
        }

        schedulePreview(player, holder);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SellHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player) || !holder.owner.equals(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        boolean touchesControls = event.getRawSlots().stream().anyMatch(slot -> slot >= SELL_SLOTS && slot < 54);
        if (touchesControls) {
            event.setCancelled(true);
            return;
        }
        boolean touchesSellArea = event.getRawSlots().stream().anyMatch(slot -> slot >= 0 && slot < SELL_SLOTS);
        if (!touchesSellArea) return;
        for (ItemStack item : event.getNewItems().values()) {
            if (isRealItem(item) && !isSellable(item)) {
                event.setCancelled(true);
                player.sendMessage("§cEsse item não está na tabela de vendas ou possui dados especiais.");
                return;
            }
        }
        schedulePreview(player, holder);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SellHolder holder)) return;
        if (!(event.getPlayer() instanceof Player player) || holder.completing) return;
        returnItems(player, event.getView().getTopInventory());
    }

    private void confirm(Player player, SellHolder holder) {
        Inventory inventory = holder.inventory;
        SaleQuote quote = quote(inventory);
        if (!quote.invalidItems().isEmpty()) {
            player.sendMessage("§cRemova os itens inválidos antes de confirmar a venda.");
            return;
        }
        if (quote.total().signum() <= 0) {
            player.sendMessage("§cColoque pelo menos um item válido para vender.");
            return;
        }

        ItemStack[] snapshot = new ItemStack[SELL_SLOTS];
        for (int slot = 0; slot < SELL_SLOTS; slot++) {
            ItemStack item = inventory.getItem(slot);
            snapshot[slot] = item == null ? null : item.clone();
            inventory.setItem(slot, null);
        }

        try {
            economy.creditSellRevenue(player.getUniqueId(), player.getName(), quote.total());
            holder.completing = true;
            player.closeInventory();
            player.sendMessage("§a§lVENDA CONCLUÍDA! §e+" + quote.total().setScale(2, RoundingMode.HALF_UP).toPlainString() + " Coins");
            player.sendMessage("§7Os itens foram vendidos para a loja do servidor.");
        } catch (SQLException | IllegalArgumentException exception) {
            for (int slot = 0; slot < SELL_SLOTS; slot++) {
                ItemStack item = snapshot[slot];
                if (item != null) inventory.setItem(slot, item);
            }
            player.sendMessage("§cA venda não foi concluída. Seus itens foram mantidos: " + exception.getMessage());
            schedulePreview(player, holder);
        }
    }

    private SaleQuote quote(Inventory inventory) {
        BigDecimal total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        List<ItemStack> invalid = new ArrayList<>();
        for (int slot = 0; slot < SELL_SLOTS; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (!isRealItem(item)) continue;
            if (!isSellable(item)) {
                invalid.add(item.clone());
                continue;
            }
            BigDecimal unitPrice = prices.get(item.getType());
            total = total.add(unitPrice.multiply(BigDecimal.valueOf(item.getAmount())));
        }
        return new SaleQuote(total.setScale(2, RoundingMode.HALF_UP), invalid);
    }

    private boolean isSellable(ItemStack item) {
        if (!isRealItem(item) || item.getType() == Material.TOTEM_OF_UNDYING) return false;
        if (!prices.containsKey(item.getType())) return false;

        // Nunca converta moedas físicas, sacos ou itens especiais em saldo virtual.
        BankService bank = plugin.getBankService();
        if (bank != null && bank.identify(item) != null) return false;
        MoneyPouchService pouches = plugin.getMoneyPouchService();
        if (pouches != null && pouches.isPouch(item)) return false;

        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta.hasDisplayName() || meta.hasLore()
                    || !meta.getPersistentDataContainer().getKeys().isEmpty()) return false;
        }
        return true;
    }

    private void schedulePreview(Player player, SellHolder holder) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != holder.inventory) return;
            updatePreview(holder.inventory);
        });
    }

    private void updatePreview(Inventory inventory) {
        SaleQuote quote = quote(inventory);
        List<String> lore = new ArrayList<>();
        lore.add("§7Valor estimado: §a" + quote.total().toPlainString() + " Coins");
        lore.add("§7Itens inválidos: §c" + quote.invalidItems().size());
        lore.add("");
        lore.add(quote.invalidItems().isEmpty()
                ? "§eClique em CONFIRMAR VENDA para receber."
                : "§cRemova os itens inválidos antes de vender.");
        inventory.setItem(PREVIEW_SLOT, button(Material.PAPER, "§e§lPrévia da venda", lore));
    }

    private void returnItems(Player player, Inventory inventory) {
        for (int slot = 0; slot < SELL_SLOTS; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (!isRealItem(item)) continue;
            inventory.setItem(slot, null);
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
            overflow.values().forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
        }
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isRealItem(ItemStack item) {
        return item != null && !item.getType().isAir();
    }

    private static final class SellHolder implements InventoryHolder {
        private Inventory inventory;
        private java.util.UUID owner;
        private boolean completing;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private record SaleQuote(BigDecimal total, List<ItemStack> invalidItems) {}
}
