package br.com.xklpack.pouch;

import br.com.xklpack.XKLPack;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class MoneyPouchListener implements Listener {
    private final XKLPack plugin;
    private final MoneyPouchService pouches;
    private final br.com.xklpack.bank.BankService bank;

    public MoneyPouchListener(XKLPack plugin, MoneyPouchService pouches, br.com.xklpack.bank.BankService bank) {
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
        try { pouches.open(event.getPlayer(), item); }
        catch (Exception ex) { event.getPlayer().sendMessage("§cNão foi possível abrir o saco: " + ex.getMessage()); }
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof PouchHolder)) return;
        Inventory top = event.getView().getTopInventory();
        if (event.getClickedInventory() != top) {
            event.setCancelled(true);
            return;
        }
        if (event.isShiftClick() || event.getHotbarButton() >= 0 || event.getClick().isKeyboardClick()) {
            event.setCancelled(true);
            return;
        }
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        if ((current != null && !current.getType().isAir() && plugin.getBankService().identify(current) == null)
                || (cursor != null && !cursor.getType().isAir() && plugin.getBankService().identify(cursor) == null)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void drag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof PouchHolder)) return;
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot >= topSize)) {
            event.setCancelled(true);
            return;
        }
        for (ItemStack item : event.getNewItems().values()) {
            if (item != null && !item.getType().isAir() && plugin.getBankService().identify(item) == null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void close(InventoryCloseEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof PouchHolder holder)) return;
        if (event.getPlayer() instanceof Player player)
            pouches.saveAndClose(player, holder, event.getView().getTopInventory());
    }
}
