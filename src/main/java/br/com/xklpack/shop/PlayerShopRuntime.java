package br.com.xklpack.shop;

import br.com.xklpack.XKLPack;
import br.com.xklpack.bank.BankService;
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
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;

import java.sql.SQLException;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

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
    private final NamespacedKey areaSizeKey;

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
        this.areaSizeKey = new NamespacedKey(plugin, "player_shop_area_size");
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
                villager.setCollidable(false);
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
                villager.getPersistentDataContainer().set(areaSizeKey, PersistentDataType.INTEGER, shop.size());

                Block stockBlock = stockLocation.getBlock();
                stockBlock.setType(Material.CHEST, false);
                BlockState state = stockBlock.getState();
                if (state instanceof Chest chest) {
                    chest.setCustomName("§6ESTOQUE");
                    chest.getPersistentDataContainer().set(shopIdKey, PersistentDataType.LONG, shop.id());
                    chest.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, shop.owner().toString());
                    chest.update(true, false);
                }
                try {
                    shops.saveShopRuntime(shop.id(), villager.getUniqueId(),
                            stockBlock.getX(), stockBlock.getY(), stockBlock.getZ());
                } catch (SQLException persistenceFailure) {
                    // Se a gravação falhar, desfaz os objetos criados para que a próxima
                    // tentativa não duplique NPC ou baú.
                    villager.remove();
                    BlockState failedState = stockBlock.getState();
                    if (failedState instanceof Container failedContainer
                            && failedContainer.getPersistentDataContainer().has(shopIdKey, PersistentDataType.LONG)
                            && java.util.Objects.equals(
                                    failedContainer.getPersistentDataContainer().get(shopIdKey, PersistentDataType.LONG),
                                    shop.id())) {
                        stockBlock.setType(Material.AIR, false);
                    }
                    throw persistenceFailure;
                }
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
                Integer size = data.get(areaSizeKey, PersistentDataType.INTEGER);
                if (centerX == null || centerZ == null || centerY == null || radius == null || size == null) continue;
                int minX = centerX - (size - 1) / 2;
                int minZ = centerZ - (size - 1) / 2;
                int maxX = minX + size - 1;
                int maxZ = minZ + size - 1;
                int x = villager.getLocation().getBlockX();
                int z = villager.getLocation().getBlockZ();
                if (x < minX || x > maxX || z < minZ || z > maxZ) {
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
        Long shopId = villager.getPersistentDataContainer().get(shopIdKey, PersistentDataType.LONG);
        if (shopId == null) return;
        openCatalog(event.getPlayer(), shopId);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void protectStockOpen(InventoryOpenEvent event) {
        if (!(event.getInventory().getHolder() instanceof Container container)) return;
        var data = container.getPersistentDataContainer();
        if (!data.has(shopIdKey, PersistentDataType.LONG)) return;
        String ownerText = data.get(ownerKey, PersistentDataType.STRING);
        if (!(event.getPlayer() instanceof Player player) || ownerText == null) {
            event.setCancelled(true);
            return;
        }
        Long shopId = data.get(shopIdKey, PersistentDataType.LONG);
        try {
            if (shopId == null || !shops.isAuthorizedForStock(shopId, player.getUniqueId())) {
                event.setCancelled(true);
                player.sendMessage("§cSomente o dono ou aliados autorizados podem acessar este estoque.");
            }
        } catch (SQLException exception) {
            event.setCancelled(true);
            plugin.getLogger().severe("Falha ao validar acesso ao estoque: " + exception.getMessage());
        }
    }


    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void preventStockChestMerge(BlockPlaceEvent event) {
        if (!event.getBlockPlaced().getType().name().endsWith("_CHEST")) return;
        for (org.bukkit.block.BlockFace face : new org.bukkit.block.BlockFace[]{
                org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH,
                org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST}) {
            if (isProtectedStock(event.getBlockPlaced().getRelative(face).getState())) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§cNão é permitido conectar outro baú ao ESTOQUE protegido.");
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void protectStockBreak(BlockBreakEvent event) {
        BlockState state = event.getBlock().getState();
        if (!(state instanceof Container container)
                || !container.getPersistentDataContainer().has(shopIdKey, PersistentDataType.LONG)) return;
        String ownerText = container.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        Long shopId = container.getPersistentDataContainer().get(shopIdKey, PersistentDataType.LONG);
        try {
            if (ownerText == null || shopId == null
                    || !shops.isAuthorizedForStock(shopId, event.getPlayer().getUniqueId())) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§cEste baú ESTOQUE é protegido.");
            }
        } catch (SQLException exception) {
            event.setCancelled(true);
            plugin.getLogger().severe("Falha ao validar quebra do estoque: " + exception.getMessage());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void preventHopperTransfer(InventoryMoveItemEvent event) {
        if (isProtectedStock(event.getSource().getHolder()) || isProtectedStock(event.getDestination().getHolder())) {
            event.setCancelled(true);
        }
    }


    private void openCatalog(Player player, long shopId) {
        try {
            if (!shops.isShopOpen(shopId)) {
                player.sendMessage("§cEsta loja está fechada no momento.");
                return;
            }
            var offers = shops.listOffers(shopId, true);
            CatalogHolder holder = new CatalogHolder(player.getUniqueId(), shopId);
            Inventory inv = Bukkit.createInventory(holder, 54, "§8Loja de jogador #" + shopId);
            holder.inventory = inv;
            int slot = 0;
            for (var offer : offers) {
                if (slot >= 45) break;
                ItemStack display = offer.item().clone();
                display.setAmount(Math.min(display.getMaxStackSize(), Math.max(1, offer.amount())));
                ItemMeta meta = display.getItemMeta();
                java.util.List<String> lore = meta.hasLore()
                        ? new java.util.ArrayList<>(meta.getLore()) : new java.util.ArrayList<>();
                lore.add("§7Estoque: §f" + offer.amount());
                lore.add("§7Preço do lote: §e" + offer.price() + " coins físicas");
                lore.add("§eClique para comprar o lote.");
                meta.setLore(lore);
                display.setItemMeta(meta);
                inv.setItem(slot, display);
                holder.offers.put(slot, offer.id());
                slot++;
            }
            ItemStack close = new ItemStack(Material.BARRIER);
            ItemMeta closeMeta = close.getItemMeta();
            closeMeta.setDisplayName("§cFechar");
            close.setItemMeta(closeMeta);
            inv.setItem(49, close);
            player.openInventory(inv);
        } catch (SQLException exception) {
            player.sendMessage("§cNão foi possível carregar o catálogo.");
            plugin.getLogger().severe("Falha ao abrir catálogo da loja " + shopId + ": " + exception.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void catalogClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof CatalogHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.owner.equals(player.getUniqueId())) return;
        if (event.getRawSlot() == 49) {
            player.closeInventory();
            return;
        }
        Long offerId = holder.offers.get(event.getRawSlot());
        if (offerId == null) return;
        BankService bank = plugin.getBankService();
        if (bank == null) {
            player.sendMessage("§cSistema de moedas físicas indisponível.");
            return;
        }
        try {
            var result = shops.purchase(holder.shopId, offerId, player, bank);
            player.sendMessage("§aCompra concluída: " + result.itemAmount() + " itens por " + result.price() + " coins.");
            if (result.change() > 0) player.sendMessage("§7Troco devolvido: §e" + result.change() + " coins.");
            player.closeInventory();
        } catch (IllegalArgumentException exception) {
            player.sendMessage("§c" + exception.getMessage());
        } catch (SQLException exception) {
            player.sendMessage("§cFalha ao processar a compra. Nenhuma moeda foi debitada.");
            plugin.getLogger().severe("Falha na compra da loja " + holder.shopId + ": " + exception.getMessage());
        }
    }

    @EventHandler
    public void catalogDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof CatalogHolder) event.setCancelled(true);
    }

    private static final class CatalogHolder implements InventoryHolder {
        final UUID owner;
        final long shopId;
        final Map<Integer, Long> offers = new HashMap<>();
        Inventory inventory;
        CatalogHolder(UUID owner, long shopId) { this.owner = owner; this.shopId = shopId; }
        @Override public Inventory getInventory() { return inventory; }
    }

    private boolean containsProtectedStock(java.util.List<Block> blocks) {
        for (Block block : blocks) {
            BlockState state = block.getState();
            if (state instanceof Container container
                    && container.getPersistentDataContainer().has(shopIdKey, PersistentDataType.LONG)) return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void preventStockBlockExplosion(BlockExplodeEvent event) {
        if (containsProtectedStock(event.blockList())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void preventStockEntityExplosion(EntityExplodeEvent event) {
        if (containsProtectedStock(event.blockList())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void preventStockPistonExtend(BlockPistonExtendEvent event) {
        if (containsProtectedStock(event.getBlocks())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void preventStockPistonRetract(BlockPistonRetractEvent event) {
        if (containsProtectedStock(event.getBlocks())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void preventStockBurn(BlockBurnEvent event) {
        if (isProtectedStock(event.getBlock().getState())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void preventStockFluid(BlockFromToEvent event) {
        if (isProtectedStock(event.getToBlock().getState())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void preventHopperMinecartPickup(InventoryPickupItemEvent event) {
        if (isProtectedStock(event.getInventory().getHolder())) event.setCancelled(true);
    }

    private boolean isProtectedStock(Object holder) {
        return holder instanceof Container container
                && container.getPersistentDataContainer().has(shopIdKey, PersistentDataType.LONG);
    }
}
