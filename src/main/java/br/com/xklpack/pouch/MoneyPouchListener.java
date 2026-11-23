package br.com.xklpack.pouch;

import br.com.xklpack.XKLPack;
import br.com.xklpack.bank.BankService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MoneyPouchListener implements Listener {
    private final XKLPack plugin;
    private final MoneyPouchService pouches;
    private final BankService bank;
    private final Map<UUID, List<ItemStack>> deathPouches = new ConcurrentHashMap<>();

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

        // Só permite manipular a Trouxa dentro do inventário pessoal do jogador.
        boolean clickedPlayerInventory = event.getClickedInventory() == event.getWhoClicked().getInventory();
        if (!clickedPlayerInventory && (currentPouch || cursorPouch)) {
            event.setCancelled(true);
            return;
        }

        // O armazenamento nativo aceita exclusivamente moedas autenticadas pelo Banco XKL.
        if (cursorPouch && current != null && !current.getType().isAir()
                && !pouches.isOfficialCoin(current)) {
            event.setCancelled(true);
            return;
        }
        if (currentPouch && cursor != null && !cursor.getType().isAir()
                && !pouches.isOfficialCoin(cursor)) {
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
        if (pouches.isPouch(event.getOldCursor())) {
            event.setCancelled(true);
            return;
        }
        for (ItemStack item : event.getNewItems().values()) {
            if (pouches.isPouch(item)) {
                event.setCancelled(true);
                return;
            }
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
            Player player = event.getEntity();
            for (ItemStack pouch : protectedPouches) {
                try {
                    pouches.storePending(player, pouch);
                } catch (java.sql.SQLException exception) {
                    plugin.getLogger().severe("Não foi possível preservar Trouxa na morte de " + player.getName() + ": " + exception.getMessage());
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void restoreAfterRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> pouches.deliverPending(player));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void restorePendingOnJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> pouches.deliverPending(player));
    }
}
