package br.com.xklpack.shop;

import br.com.xklpack.XKLPack;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.Chest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;

import java.sql.SQLException;
import java.util.UUID;

/**
 * Cria o comerciante e o baú de estoque quando termina o prazo do alvará.
 * O NPC é um Villager persistente do próprio Paper; não exige Citizens.
 */
public final class PlayerShopRuntime implements Listener {
    private final XKLPack plugin;
    private final PlayerShopService shops;
    private final NamespacedKey shopIdKey;
    private final NamespacedKey ownerKey;
    private final NamespacedKey merchantKey;
    private final NamespacedKey centerXKey;
    private final NamespacedKey centerZKey;
    private final NamespacedKey centerYKey;
    private final NamespacedKey radiusKey;

    public PlayerShopRuntime(XKLPack plugin, PlayerShopService shops) {
        this.plugin = plugin;
        this.shops = shops;
        this.shopIdKey = new NamespacedKey(plugin, "player_shop_id");
        this.ownerKey = new NamespacedKey(plugin, "player_shop_owner");
        this.merchantKey = new NamespacedKey(plugin, "player_shop_merchant");
        this.centerXKey = new NamespacedKey(plugin, "player_shop_center_x");
        this.centerZKey = new NamespacedKey(plugin, "player_shop_center_z");
        this.centerYKey = new NamespacedKey(plugin, "player_shop_center_y");
        this.radiusKey = new NamespacedKey(plugin, "player_shop_radius");
        Bukkit.getScheduler().runTaskTimer(plugin, this::activateDueShops, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::keepMerchantsInsideArea, 10L, 10L);
    }

    private void activateDueShops() {
        try {
            for (PlayerShopService.ShopRecord shop : shops.shopsDueForActivation(System.currentTimeMillis())) {
                var world = Bukkit.getWorld(shop.worldId());
                if (world == null) continue;
                Location center = new Location(world, shop.x() + 0.5, shop.y(), shop.z() + 0.5);
                if (!center.getBlock().isEmpty() || !center.clone().add(0, 1, 0).getBlock().isEmpty()
                        || !center.clone().add(0, -1, 0).getBlock().getType().isSolid()) {
                    plugin.getLogger().warning("Loja " + shop.id() + " aguardando espaço livre para o comerciante.");
                    continue;
                }
                Location stockLocation = findStockLocation(center, shop.radius());
                if (stockLocation == null) {
                    plugin.getLogger().warning("Loja " + shop.id() + " aguardando espaço para o baú ESTOQUE.");
                    continue;
                }
                Villager villager = (Villager) world.spawnEntity(center, EntityType.VILLAGER);
                villager.setCustomName("§6" + shop.merchantName());
                villager.setCustomNameVisible(true);
                villager.setInvulnerable(true);
                villager.setPersistent(true);
                villager.setRemoveWhenFarAway(false);
                villager.setCanPickupItems(false);
                villager.getPersistentDataContainer().set(shopIdKey, PersistentDataType.LONG, shop.id());
                villager.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, shop.owner().toString());
                villager.getPersistentDataContainer().set(merchantKey, PersistentDataType.BYTE, (byte) 1);
                villager.getPersistentDataContainer().set(centerXKey, PersistentDataType.INTEGER, shop.x());
                villager.getPersistentDataContainer().set(centerZKey, PersistentDataType.INTEGER, shop.z());
                villager.getPersistentDataContainer().set(centerYKey, PersistentDataType.INTEGER, shop.y());
                villager.getPersistentDataContainer().set(radiusKey, PersistentDataType.INTEGER, shop.radius());

                Block stockBlock = stockLocation.getBlock();
                stockBlock.setType(Material.CHEST, false);
                BlockState state = stockBlock.getState();
                if (state instanceof Chest chest) {
                    chest.setCustomName("§6ESTOQUE");
                    chest.getPersistentDataContainer().set(shopIdKey, PersistentDataType.LONG, shop.id());
                    chest.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, shop.owner().toString());
                    chest.update(true, false);
                }
                shops.saveShopRuntime(shop.id(), villager.getUniqueId(),
                        stockBlock.getX(), stockBlock.getY(), stockBlock.getZ());
                plugin.getLogger().info("Loja " + shop.id() + " ativada: comerciante " + shop.merchantName() + ".");
            }
        } catch (SQLException exception) {
            plugin.getLogger().severe("Falha ao ativar lojas de jogadores: " + exception.getMessage());
        }
    }

    private void keepMerchantsInsideArea() {
        for (var world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof Villager villager)
                        || !villager.getPersistentDataContainer().has(merchantKey, PersistentDataType.BYTE)) continue;
                var data = villager.getPersistentDataContainer();
                Integer centerX = data.get(centerXKey, PersistentDataType.INTEGER);
                Integer centerZ = data.get(centerZKey, PersistentDataType.INTEGER);
                Integer centerY = data.get(centerYKey, PersistentDataType.INTEGER);
                Integer radius = data.get(radiusKey, PersistentDataType.INTEGER);
                if (centerX == null || centerZ == null || centerY == null || radius == null) continue;
                int x = villager.getLocation().getBlockX();
                int z = villager.getLocation().getBlockZ();
                if (Math.abs(x - centerX) > radius || Math.abs(z - centerZ) > radius) {
                    villager.teleport(new Location(world, centerX + 0.5, centerY, centerZ + 0.5));
                }
            }
        }
    }

    private Location findStockLocation(Location center, int radius) {
        int[][] offsets = {{2,0},{-2,0},{0,2},{0,-2},{2,2},{-2,2},{2,-2},{-2,-2}};
        for (int[] offset : offsets) {
            if (Math.abs(offset[0]) > radius || Math.abs(offset[1]) > radius) continue;
            Location candidate = center.clone().add(offset[0], 0, offset[1]);
            if (candidate.getBlock().isEmpty()
                    && candidate.clone().add(0, -1, 0).getBlock().getType().isSolid()) return candidate;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void protectMerchant(EntityDamageEvent event) {
        if (event.getEntity() instanceof Villager villager
                && villager.getPersistentDataContainer().has(merchantKey, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void protectMerchantTarget(EntityTargetLivingEntityEvent event) {
        if (event.getEntity() instanceof Villager villager
                && villager.getPersistentDataContainer().has(merchantKey, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void merchantClicked(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager villager)
                || !villager.getPersistentDataContainer().has(merchantKey, PersistentDataType.BYTE)) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage("§eEsta loja está sendo preparada. O catálogo de vendas será aberto quando estiver configurado.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void protectStockOpen(InventoryOpenEvent event) {
        if (!(event.getInventory().getHolder() instanceof Container container)) return;
        var data = container.getPersistentDataContainer();
        if (!data.has(shopIdKey, PersistentDataType.LONG)) return;
        String ownerText = data.get(ownerKey, PersistentDataType.STRING);
        if (!(event.getPlayer() instanceof Player player) || ownerText == null
                || !player.getUniqueId().toString().equals(ownerText)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cSomente o dono da loja pode acessar este estoque.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void protectStockBreak(BlockBreakEvent event) {
        BlockState state = event.getBlock().getState();
        if (!(state instanceof Container container)
                || !container.getPersistentDataContainer().has(shopIdKey, PersistentDataType.LONG)) return;
        String ownerText = container.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (ownerText == null || !event.getPlayer().getUniqueId().toString().equals(ownerText)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cEste baú ESTOQUE é protegido.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void preventHopperTransfer(InventoryMoveItemEvent event) {
        if (isProtectedStock(event.getSource().getHolder()) || isProtectedStock(event.getDestination().getHolder())) {
            event.setCancelled(true);
        }
    }

    private boolean isProtectedStock(Object holder) {
        return holder instanceof Container container
                && container.getPersistentDataContainer().has(shopIdKey, PersistentDataType.LONG);
    }
}
