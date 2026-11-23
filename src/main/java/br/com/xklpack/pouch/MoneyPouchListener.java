package br.com.xklpack.pouch;

import br.com.xklpack.XKLPack;
import br.com.xklpack.bank.BankService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;

import java.util.ArrayList;
import java.util.List;

public final class MoneyPouchListener implements Listener {
    private final XKLPack plugin;
    private final MoneyPouchService pouches;
    private final BankService bank;

    public MoneyPouchListener(XKLPack plugin, MoneyPouchService pouches, BankService bank) {
        this.plugin = plugin;
        this.pouches = pouches;
        this.bank = bank;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void preventDrop(PlayerDropItemEvent event) {
        if (pouches.isPouch(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cA Trouxa XKL não pode ser descartada.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void protectContents(InventoryClickEvent event) {
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        boolean currentPouch = pouches.isPouch(current);
        boolean cursorPouch = pouches.isPouch(cursor);
        if (!currentPouch && !cursorPouch) return;

        // A Trouxa pode ser reorganizada no inventário do jogador, mas não movida
        // para baús, bancadas, fornalhas ou qualquer inventário externo.
        boolean playerInventoryView = event.getView().getTopInventory() == event.getWhoClicked().getInventory();
        if (!playerInventoryView && (currentPouch || cursorPouch)) {
            event.setCancelled(true);
            return;
        }

        // O clique com a Trouxa sobre uma pilha só aceita moedas autenticadas.
        if (cursorPouch && current != null && !current.getType().isAir()
                && !pouches.isOfficialCoin(current)) {
            event.setCancelled(true);
            return;
        }
        // Não permita trocar a Trouxa por item comum via tecla numérica ou offhand.
        if ((currentPouch || cursorPouch)
                && (event.getHotbarButton() >= 0
                || event.getClick().name().equals("SWAP_OFFHAND")
                || event.getClick().name().equals("DOUBLE_CLICK"))) {
            event.setCancelled(true);
            return;
        }
        if (currentPouch && !pouches.containsOnlyOfficialCoins(current)) {
            event.setCancelled(true);
            event.getWhoClicked().sendMessage("§cA Trouxa XKL contém um item não autorizado e foi bloqueada.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void protectDrag(InventoryDragEvent event) {
        ItemStack oldCursor = event.getOldCursor();
        if (!pouches.isPouch(oldCursor)) {
            for (ItemStack item : event.getNewItems().values()) {
                if (pouches.isPouch(item)) {
                    event.setCancelled(true);
                    return;
                }
            }
            return;
        }
        for (ItemStack item : event.getNewItems().values()) {
            if (!pouches.isOfficialCoin(item)) {
                event.setCancelled(true);
                return;
            }
        }
        if (event.getRawSlots().stream().anyMatch(slot -> slot < event.getView().getTopInventory().getSize())
                && event.getView().getTopInventory() != event.getWhoClicked().getInventory()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void keepOnDeath(PlayerDeathEvent event) {
        List<ItemStack> protectedPouches = new ArrayList<>();
        event.getDrops().removeIf(item -> {
            if (!pouches.isPouch(item)) return false;
            protectedPouches.add(item.clone());
            return true;
        });
        if (!protectedPouches.isEmpty()) {
            event.getEntity().getScheduler().runDelayed(plugin, task -> {
                Player player = event.getEntity();
                for (ItemStack pouch : protectedPouches) pouches.restorePouch(player, pouch);
            }, null, 1L);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Nada é criado novamente aqui: a entrega inicial é única e registrada no banco.
    }
}
