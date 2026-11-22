package br.com.xklpack.shop;

import br.com.xklpack.XKLPack;
import br.com.xklpack.jobs.JobService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EquipmentService implements Listener {
    private final XKLPack plugin;
    private final NamespacedKey specialToolKey;
    private final NamespacedKey ownerKey;

    public EquipmentService(XKLPack plugin) {
        this.plugin = plugin;
        this.specialToolKey = new NamespacedKey(plugin, "special_tool");
        this.ownerKey = new NamespacedKey(plugin, "special_tool_owner");
    }

    public boolean isSpecialTool(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(specialToolKey, PersistentDataType.BYTE);
    }

    public void bindOwner(Player player, ItemStack item) {
        if (!isSpecialTool(item)) return;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());
        item.setItemMeta(meta);
    }

    public DeliveryPlan prepareDelivery(Player player, ItemStack product) {
        ItemStack[] contents = cloneContents(player.getInventory().getStorageContents());
        if (!isSpecialTool(product)) {
            Inventory simulated = Bukkit.createInventory(null, 36);
            simulated.setContents(contents);
            if (!simulated.addItem(product.clone()).isEmpty()) return DeliveryPlan.failure("Libere espaço no inventário antes de comprar.");
            return DeliveryPlan.success(simulated.getStorageContents());
        }

        int level = getLevel(player);
        int ownedLimit = getLimit("owned-slots", level);
        int equippedLimit = getLimit("equipped-slots", level);
        int owned = countOwned(player);
        if (owned >= ownedLimit) {
            return DeliveryPlan.failure("Você atingiu seu limite de ferramentas: " + owned + "/" + ownedLimit + ". Evolua sua profissão para liberar mais espaços.");
        }

        int targetSlot = -1;
        if (countEquipped(player) < equippedLimit) {
            targetSlot = firstEmpty(contents, 0, 9);
        }
        if (targetSlot < 0) targetSlot = firstEmpty(contents, 9, 36);
        if (targetSlot < 0) return DeliveryPlan.failure("Libere espaço no inventário para receber a ferramenta.");
        contents[targetSlot] = product.clone();
        return DeliveryPlan.success(contents);
    }

    public void enforceLimits(Player player) {
        if (!plugin.getConfig().getBoolean("equipment-limits.enabled", true)) return;
        int equippedLimit = getLimit("equipped-slots", getLevel(player));
        ItemStack[] contents = player.getInventory().getStorageContents();
        int equipped = countEquipped(player);
        if (equipped <= equippedLimit) return;

        for (int slot = 8; slot >= 0 && equipped > equippedLimit; slot--) {
            ItemStack item = contents[slot];
            if (!isSpecialTool(item)) continue;
            int free = firstEmpty(contents, 9, 36);
            if (free < 0) break;
            contents[free] = item;
            contents[slot] = null;
            equipped--;
        }
        player.getInventory().setStorageContents(contents);
        if (equipped > equippedLimit) {
            player.sendMessage("§eSeu limite atual é de " + equippedLimit + " ferramentas equipadas. Libere espaço para guardar as excedentes.");
        }
    }

    private int countOwned(Player player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) if (isSpecialTool(item)) count++;
        for (ItemStack item : player.getEnderChest().getContents()) if (isSpecialTool(item)) count++;
        return count;
    }

    private int countEquipped(Player player) {
        int count = 0;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < Math.min(9, contents.length); slot++) if (isSpecialTool(contents[slot])) count++;
        if (isSpecialTool(player.getInventory().getItemInOffHand())) count++;
        return count;
    }

    private int getLevel(Player player) {
        if (!"active-profession".equalsIgnoreCase(plugin.getConfig().getString("equipment-limits.level-source", "active-profession"))) {
            return 1;
        }
        JobService jobs = plugin.getJobService();
        if (jobs == null) return 1;
        try {
            String job = jobs.getActiveJob(player.getUniqueId());
            return job == null ? 1 : jobs.getProgress(player.getUniqueId(), job).level();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Não foi possível consultar o nível para os limites de ferramentas: " + exception.getMessage());
            return 1;
        }
    }

    private int getLimit(String section, int level) {
        ConfigurationSection values = plugin.getConfig().getConfigurationSection("equipment-limits." + section);
        if (values == null) return 1;
        List<Integer> thresholds = new ArrayList<>();
        for (String key : values.getKeys(false)) {
            try { thresholds.add(Integer.parseInt(key)); }
            catch (NumberFormatException ignored) {}
        }
        thresholds.sort(Comparator.naturalOrder());
        int limit = 1;
        for (int threshold : thresholds) {
            if (threshold <= level) limit = Math.max(1, values.getInt(String.valueOf(threshold), 1));
            else break;
        }
        return limit;
    }

    private int firstEmpty(ItemStack[] contents, int start, int end) {
        for (int slot = start; slot < Math.min(end, contents.length); slot++) {
            if (contents[slot] == null || contents[slot].getType().isAir()) return slot;
        }
        return -1;
    }

    private ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) copy[i] = contents[i] == null ? null : contents[i].clone();
        return copy;
    }

    private boolean ownedBy(Player player, ItemStack item) {
        if (!isSpecialTool(item)) return false;
        String owner = item.getItemMeta().getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        return player.getUniqueId().toString().equals(owner);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> enforceLimits(event.getPlayer()));
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (isSpecialTool(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cFerramentas especiais são vinculadas à sua conta e não podem ser descartadas.");
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        boolean clickedTop = event.getClickedInventory() == top;
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        if (clickedTop && (isSpecialTool(current) || isSpecialTool(cursor))) {
            event.setCancelled(true);
            player.sendMessage("§cFerramentas especiais não podem ser guardadas em baús ou outras interfaces.");
            return;
        }
        if (event.isShiftClick() && isSpecialTool(current)) {
            event.setCancelled(true);
            return;
        }

        if (event.getClickedInventory() == player.getInventory()) {
            int slot = event.getSlot();
            if (slot >= 0 && slot < 9 && isSpecialTool(cursor) && !isSpecialTool(current)
                    && countEquipped(player) >= getLimit("equipped-slots", getLevel(player))) {
                event.setCancelled(true);
                player.sendMessage("§cVocê atingiu o limite de ferramentas equipadas para seu nível.");
                return;
            }
            if (event.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY
                    && slot >= 9 && slot < 36
                    && isSpecialTool(current)
                    && !isSpecialTool(player.getInventory().getItem(event.getHotbarButton()))
                    && countEquipped(player) >= getLimit("equipped-slots", getLevel(player))) {
                event.setCancelled(true);
                player.sendMessage("§cVocê atingiu o limite de ferramentas equipadas para seu nível.");
                return;
            }
            if (event.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND
                    && isSpecialTool(current) && !isSpecialTool(player.getInventory().getItemInOffHand())
                    && countEquipped(player) >= getLimit("equipped-slots", getLevel(player))) {
                event.setCancelled(true);
                player.sendMessage("§cVocê atingiu o limite de ferramentas equipadas para seu nível.");
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        boolean intoTop = event.getRawSlots().stream().anyMatch(slot -> slot < event.getView().getTopInventory().getSize());
        if (intoTop && event.getNewItems().values().stream().anyMatch(this::isSpecialTool)) {
            event.setCancelled(true);
            return;
        }
        if (event.getWhoClicked() instanceof Player player) {
            int topSize = event.getView().getTopInventory().getSize();
            long newHotbarTools = event.getNewItems().entrySet().stream()
                    .filter(entry -> entry.getKey() >= topSize && entry.getKey() - topSize < 9)
                    .filter(entry -> isSpecialTool(entry.getValue()))
                    .count();
            if (newHotbarTools > 0 && countEquipped(player) + newHotbarTools > getLimit("equipped-slots", getLevel(player))) {
                event.setCancelled(true);
                player.sendMessage("§cVocê atingiu o limite de ferramentas equipadas para seu nível.");
            }
        }
    }

    @EventHandler
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (isSpecialTool(event.getItem())) event.setCancelled(true);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        ItemStack item = event.getItem().getItemStack();
        if (!isSpecialTool(item)) return;
        Entity entity = event.getEntity();
        if (!(entity instanceof Player player) || !ownedBy(player, item)) event.setCancelled(true);
    }

    public record DeliveryPlan(boolean accepted, ItemStack[] contents, String reason) {
        public static DeliveryPlan success(ItemStack[] contents) { return new DeliveryPlan(true, contents, ""); }
        public static DeliveryPlan failure(String reason) { return new DeliveryPlan(false, null, reason); }
    }
}
