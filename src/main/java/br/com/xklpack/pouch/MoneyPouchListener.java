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
            deathPouches.put(event.getEntity().getUniqueId(), protectedPouches);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void restoreAfterRespawn(PlayerRespawnEvent event) {
        List<ItemStack> protectedPouches = deathPouches.remove(event.getPlayer().getUniqueId());
        if (protectedPouches == null || protectedPouches.isEmpty()) return;
        Player player = event.getPlayer();
        for (ItemStack pouch : protectedPouches) {
            var overflow = player.getInventory().addItem(pouch);
            if (!overflow.isEmpty()) {
                var enderOverflow = player.getEnderChest().addItem(overflow.values().toArray(new ItemStack[0]));
                if (!enderOverflow.isEmpty()) {
                    // Não descarta a Trouxa caso ambos os inventários estejam cheios.
                    deathPouches.put(player.getUniqueId(), new ArrayList<>(enderOverflow.values()));
                    player.sendMessage("§eLibere espaço no inventário ou no baú do Ender para recuperar sua Trouxa XKL.");
                }
            }
        }
    }
}
