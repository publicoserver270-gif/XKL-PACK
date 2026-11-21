package br.com.xklpack.pouch;

import br.com.xklpack.XKLPack;
import br.com.xklpack.bank.BankService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class MoneyPouchListener implements Listener {
    private final XKLPack plugin;
    private final MoneyPouchService pouches;
    private final BankService bank;

    public MoneyPouchListener(XKLPack plugin, MoneyPouchService pouches, BankService bank) {
        this.plugin = plugin;
        this.pouches = pouches;
        this.bank = bank;
    }

    @EventHandler
    public void interact(PlayerInteractEvent event) {
        if (!plugin.getBankConfig().getBoolean("money-pouch.enabled", true)
                || !plugin.getBankConfig().getBoolean("money-pouch.open-with-right-click", true)
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        ItemStack item = event.getItem();
        if (!pouches.isPouch(item)) return;
        event.setCancelled(true);
        try {
            pouches.open(event.getPlayer(), item);
            if (event.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND)
                event.getPlayer().getInventory().setItemInOffHand(item);
            else
                event.getPlayer().getInventory().setItemInMainHand(item);
        } catch (Exception ex) {
            event.getPlayer().sendMessage("§cNão foi possível abrir o saco: " + ex.getMessage());
        }
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof PouchHolder holder)) return;

        Inventory top = event.getView().getTopInventory();
        int rawSlot = event.getRawSlot();
        boolean touchesPouch = rawSlot >= 0 && rawSlot < top.getSize();

        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        // Nunca permita inserir itens comuns no saco, nem retirar o próprio saco enquanto aberto.
        if (touchesPouch) {
            if (isRealItem(current) && (bank.identify(current) == null || pouches.isPouch(current))) {
                event.setCancelled(true);
                return;
            }
            if (isRealItem(cursor) && (bank.identify(cursor) == null || pouches.isPouch(cursor))) {
                event.setCancelled(true);
                return;
            }
            if (event.getClick() == ClickType.NUMBER_KEY || event.getClick() == ClickType.SWAP_OFFHAND
                    || event.getClick() == ClickType.DOUBLE_CLICK) {
                event.setCancelled(true);
                return;
            }
        } else {
            // Shift-click de moedas do inventário do jogador pode enviá-las ao saco.
            if (event.isShiftClick() && isRealItem(current)) {
                if (bank.identify(current) == null || pouches.isPouch(current)) {
                    event.setCancelled(true);
                    return;
                }
            }
            // Não deixe mover o saco que está aberto para dentro dele mesmo.
            if (pouches.isPouch(current)) {
                event.setCancelled(true);
                return;
            }
        }

        if (touchesPouch && !event.isCancelled() && event.getWhoClicked() instanceof Player player) {
            scheduleSave(player, holder, top);
        } else if (!touchesPouch && event.isShiftClick() && isRealItem(current)
                && bank.identify(current) != null && !event.isCancelled()
                && event.getWhoClicked() instanceof Player player) {
            scheduleSave(player, holder, top);
        }
    }

    @EventHandler
    public void drag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof PouchHolder holder)) return;
        Inventory top = event.getView().getTopInventory();
        boolean touchesPouch = event.getRawSlots().stream().anyMatch(slot -> slot < top.getSize());
        if (!touchesPouch) return;

        for (var entry : event.getNewItems().entrySet()) {
            if (entry.getKey() < top.getSize()) {
                ItemStack item = entry.getValue();
                if (isRealItem(item) && (bank.identify(item) == null || pouches.isPouch(item))) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
        if (!event.isCancelled() && event.getWhoClicked() instanceof Player player)
            scheduleSave(player, holder, top);
    }

    private boolean isRealItem(ItemStack item) {
        return item != null && !item.getType().isAir();
    }

    private void scheduleSave(Player player, PouchHolder holder, Inventory inventory) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && player.getOpenInventory().getTopInventory() == inventory) {
                try {
                    pouches.saveSnapshot(holder, inventory);
                } catch (Exception ex) {
                    player.sendMessage("§cNão foi possível salvar o conteúdo do saco: " + ex.getMessage());
                    plugin.getLogger().severe("Falha ao salvar saco " + holder.pouchId() + ": " + ex.getMessage());
                }
            }
        });
    }

    @EventHandler
    public void close(InventoryCloseEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof PouchHolder holder)) return;
        if (event.getPlayer() instanceof Player player)
            pouches.saveAndClose(player, holder, event.getView().getTopInventory());
    }
}
