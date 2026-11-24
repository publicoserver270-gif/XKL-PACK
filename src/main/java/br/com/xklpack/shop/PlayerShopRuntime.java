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
import java.util.LinkedHashMap;
import java.util.List;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.ClickType;

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
        Bukkit.getScheduler().runTaskTimer(plugin, this::activateDueShops, 20L, 100L);
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

                // Recupera entidades/blocos criados antes de uma queda do servidor.
                // Assim, uma tentativa interrompida não duplica o NPC nem sobrescreve baús.
                Villager villager = findExistingMerchant(world, shop.id());
                boolean createdMerchant = false;
                if (villager == null) {
                    Entity spawned = world.spawnEntity(center, EntityType.VILLAGER);
                    if (!(spawned instanceof Villager spawnedVillager)) {
                        plugin.getLogger().warning("Loja " + shop.id() + " não conseguiu gerar o comerciante; nova tentativa será feita.");
                        continue;
                    }
                    villager = spawnedVillager;
                    createdMerchant = true;
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
                }

                Location stockLocation = findExistingStockLocation(center, shop.radius(), shop.id(), shop.stockX(), shop.stockY(), shop.stockZ());
                boolean createdStock = false;
                if (stockLocation == null) {
                    stockLocation = findStockLocation(center, shop.radius());
                    if (stockLocation == null) {
                        if (createdMerchant) villager.remove();
                        plugin.getLogger().warning("Loja " + shop.id() + " aguardando espaço para o baú ESTOQUE.");
                        continue;
                    }
                    Block stockBlock = stockLocation.getBlock();
                    if (!stockBlock.getType().isAir()) {
                        if (createdMerchant) villager.remove();
                        plugin.getLogger().warning("Loja " + shop.id() + " não criou estoque: o local deixou de estar livre.");
                        continue;
                    }
                    stockBlock.setType(Material.CHEST, false);
                    createdStock = true;
                    BlockState state = stockBlock.getState();
                    if (!(state instanceof Chest chest)) {
                        if (createdMerchant) villager.remove();
                        stockBlock.setType(Material.AIR, false);
                        plugin.getLogger().warning("Loja " + shop.id() + " não conseguiu inicializar o baú ESTOQUE.");
                        continue;
                    }
                    chest.setCustomName("§6ESTOQUE");
                    chest.getPersistentDataContainer().set(shopIdKey, PersistentDataType.LONG, shop.id());
                    chest.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, shop.owner().toString());
                    if (!chest.update(true, false)) {
                        if (createdMerchant) villager.remove();
                        stockBlock.setType(Material.AIR, false);
                        plugin.getLogger().warning("Loja " + shop.id() + " não conseguiu persistir a proteção do ESTOQUE.");
                        continue;
                    }
                }

                try {
                    shops.saveShopRuntime(shop.id(), villager.getUniqueId(),
                            stockLocation.getBlockX(), stockLocation.getBlockY(), stockLocation.getBlockZ());
                } catch (SQLException persistenceFailure) {
                    if (createdMerchant) villager.remove();
                    if (createdStock) {
                        BlockState failedState = stockLocation.getBlock().getState();
                        if (failedState instanceof Container failedContainer
                                && java.util.Objects.equals(
                                        failedContainer.getPersistentDataContainer().get(shopIdKey, PersistentDataType.LONG),
                                        shop.id())) {
                            stockLocation.getBlock().setType(Material.AIR, false);
                        }
                    }
                    throw persistenceFailure;
                }
                plugin.getLogger().info("Loja " + shop.id() + " ativada/recuperada: comerciante "
                        + shop.merchantName() + ".");
            }
        } catch (SQLException exception) {
            plugin.getLogger().severe("Falha ao ativar lojas de jogadores: " + exception.getMessage());
        } catch (RuntimeException exception) {
            plugin.getLogger().severe("Falha inesperada ao ativar lojas de jogadores: " + exception.getMessage());
            exception.printStackTrace();
        }
    }

    private Villager findExistingMerchant(org.bukkit.World world, long shopId) {
        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof Villager villager)) continue;
            var data = villager.getPersistentDataContainer();
            Long storedId = data.get(shopIdKey, PersistentDataType.LONG);
            if (java.util.Objects.equals(storedId, shopId)
                    && data.has(merchantKey, PersistentDataType.BYTE)) return villager;
        }
        return null;
    }

    private Location findExistingStockLocation(Location center, int radius, long shopId, int savedX, int savedY, int savedZ) {
        if (savedX != 0 || savedY != 0 || savedZ != 0) {
            Block saved = center.getWorld().getBlockAt(savedX, savedY, savedZ);
            BlockState savedState = saved.getState();
            if (savedState instanceof Container container
                    && java.util.Objects.equals(
                            container.getPersistentDataContainer().get(shopIdKey, PersistentDataType.LONG), shopId)) {
                return saved.getLocation();
            }
        }
        int[][] offsets = {{2,0},{-2,0},{0,2},{0,-2},{2,2},{-2,2},{2,-2},{-2,-2}};
        for (int[] offset : offsets) {
            if (Math.abs(offset[0]) > radius || Math.abs(offset[1]) > radius) continue;
            Block block = center.getWorld().getBlockAt(center.getBlockX() + offset[0],
                    center.getBlockY(), center.getBlockZ() + offset[1]);
            BlockState state = block.getState();
            if (state instanceof Container container
                    && java.util.Objects.equals(
                            container.getPersistentDataContainer().get(shopIdKey, PersistentDataType.LONG), shopId)) {
                return block.getLocation();
            }
        }
        return null;
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
                Integer configuredSize = data.get(areaSizeKey, PersistentDataType.INTEGER);
                if (centerX == null || centerZ == null || centerY == null || radius == null) continue;
                int size = configuredSize == null ? radius * 2 + 1 : configuredSize;
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
        Player player = event.getPlayer();
        if (player.isSneaking()) {
            try {
                if (shops.canManageProducts(shopId, player.getUniqueId())
                        || shops.canConfigure(shopId, player.getUniqueId())
                        || shops.canWithdraw(shopId, player.getUniqueId())
                        || shops.isAuthorizedForStock(shopId, player.getUniqueId())) {
                    openManagementCatalog(player, shopId);
                } else {
                    player.sendMessage("§cVocê não tem permissão para administrar esta loja.");
                }
            } catch (SQLException exception) {
                player.sendMessage("§cNão foi possível verificar suas permissões.");
                plugin.getLogger().severe("Falha ao abrir gerenciamento da loja " + shopId + ": " + exception.getMessage());
            }
            return;
        }
        openCatalog(player, shopId);
    }

    private void openManagementCatalog(Player player, long shopId) {
        try {
            if (!shops.canManageProducts(shopId, player.getUniqueId())) {
                player.sendMessage("§cVocê não tem permissão para editar produtos.");
                return;
            }
            var offers = shops.listOffers(shopId, false);
            ManagementHolder holder = new ManagementHolder(player.getUniqueId(), shopId);
            Inventory inv = Bukkit.createInventory(holder, 54, "§8Gerenciar loja #" + shopId);
            holder.inventory = inv;
            int slot = 0;
            for (var offer : offers) {
                if (slot >= 45) break;
                ItemStack display = offer.item().clone();
                display.setAmount(Math.min(display.getMaxStackSize(), offer.amount()));
                ItemMeta meta = display.getItemMeta();
                List<String> lore = meta.hasLore() ? new java.util.ArrayList<>(meta.getLore()) : new java.util.ArrayList<>();
                lore.add("§7Lote: §f" + offer.amount() + " unidades");
                lore.add("§7Preço atual: §e" + offer.price() + " Coins");
                lore.add(offer.enabled() ? "§aOferta ativa" : "§cOferta esgotada/desativada");
                lore.add("§eClique para configurar o preço");
                meta.setLore(lore);
                display.setItemMeta(meta);
                inv.setItem(slot, display);
                holder.offerIds.put(slot, offer.id());
                slot++;
            }
            inv.setItem(49, namedItem(Material.BARRIER, "§cFechar"));
            player.openInventory(inv);
        } catch (SQLException exception) {
            player.sendMessage("§cNão foi possível carregar os produtos da loja.");
            plugin.getLogger().severe("Falha ao carregar gerenciamento da loja " + shopId + ": " + exception.getMessage());
        }
    }

    private void openPriceEditor(Player player, long shopId, long offerId) {
        try {
            if (!shops.canManageProducts(shopId, player.getUniqueId())) {
                player.sendMessage("§cVocê não tem permissão para editar preços.");
                return;
            }
            var offer = shops.listOffers(shopId, false).stream()
                    .filter(value -> value.id() == offerId).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado."));
            BankService bank = plugin.getBankService();
            if (bank == null) throw new IllegalStateException("Banco de moedas físicas indisponível.");
            List<BankService.Denomination> denominations = bank.denominations();
            if (denominations.size() != 6) {
                player.sendMessage("§cA configuração precisa conter exatamente as seis moedas oficiais para editar preços.");
                return;
            }
            PriceHolder holder = new PriceHolder(player.getUniqueId(), shopId, offerId, offer);
            for (BankService.Denomination denomination : denominations) holder.counts.put(denomination.id(), 0L);
            Inventory inv = Bukkit.createInventory(holder, 27, "§8Definir preço do produto");
            holder.inventory = inv;
            ItemStack product = offer.item().clone();
            product.setAmount(Math.min(product.getMaxStackSize(), offer.amount()));
            ItemMeta productMeta = product.getItemMeta();
            List<String> productLore = productMeta.hasLore() ? new java.util.ArrayList<>(productMeta.getLore()) : new java.util.ArrayList<>();
            productLore.add("§7Quantidade do lote: §f" + offer.amount());
            productLore.add("§7Clique nas moedas para montar o preço.");
            product.setItemMeta(productMeta);
            inv.setItem(4, product);
            int[] coinSlots = {10, 11, 12, 13, 14, 15};
            for (int i = 0; i < denominations.size(); i++) {
                holder.coinSlots.put(coinSlots[i], denominations.get(i).id());
                inv.setItem(coinSlots[i], coinButton(bank, denominations.get(i), 0));
            }
            inv.setItem(22, namedItem(Material.LIME_DYE, "§aConfirmar preço"));
            inv.setItem(26, namedItem(Material.BARRIER, "§cCancelar"));
            player.openInventory(inv);
        } catch (SQLException | IllegalArgumentException exception) {
            player.sendMessage("§c" + exception.getMessage());
        }
    }

    private ItemStack coinButton(BankService bank, BankService.Denomination denomination, long count) {
        ItemStack item = bank.createCoin(denomination, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§e" + denomination.name() + " §7(" + denomination.value() + " Coins)");
        meta.setLore(List.of("§7Quantidade: §f" + count, "§aClique esquerdo: +1", "§cClique direito: -1"));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack namedItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private long calculatePrice(PriceHolder holder, BankService bank) {
        long total = 0;
        for (BankService.Denomination denomination : bank.denominations()) {
            total = Math.addExact(total, Math.multiplyExact(
                    holder.counts.getOrDefault(denomination.id(), 0L), (long) denomination.value()));
        }
        return total;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void managementClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ManagementHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.viewer.equals(player.getUniqueId())) return;
        if (event.getRawSlot() == 49) { player.closeInventory(); return; }
        Long offerId = holder.offerIds.get(event.getRawSlot());
        if (offerId != null) openPriceEditor(player, holder.shopId, offerId);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void priceEditorClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof PriceHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.viewer.equals(player.getUniqueId())) return;
        int rawSlot = event.getRawSlot();
        if (rawSlot == 26) { openManagementCatalog(player, holder.shopId); return; }
        if (rawSlot == 22) {
            try {
                if (!shops.canManageProducts(holder.shopId, player.getUniqueId())) {
                    player.sendMessage("§cVocê perdeu a permissão para editar produtos.");
                    player.closeInventory();
                    return;
                }
                BankService bank = plugin.getBankService();
                if (bank == null) throw new IllegalStateException("Banco de moedas físicas indisponível.");
                long total = calculatePrice(holder, bank);
                if (total < 1 || total > Integer.MAX_VALUE) {
                    player.sendMessage("§cO preço precisa ficar entre 1 e " + Integer.MAX_VALUE + " Coins.");
                    return;
                }
                shops.updateOfferPrice(holder.shopId, holder.offerId, player.getUniqueId(), (int) total);
                player.sendMessage("§aPreço atualizado para " + total + " Coins.");
                openManagementCatalog(player, holder.shopId);
            } catch (SQLException | ArithmeticException | IllegalArgumentException exception) {
                player.sendMessage("§cNão foi possível salvar o preço: " + exception.getMessage());
            }
            return;
        }
        String denominationId = holder.coinSlots.get(rawSlot);
        if (denominationId == null) return;
        long current = holder.counts.getOrDefault(denominationId, 0L);
        if (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.SHIFT_LEFT) {
            if (current == Long.MAX_VALUE) return;
            current++;
        } else if (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT) {
            if (current == 0) return;
            current--;
        } else return;
        holder.counts.put(denominationId, current);
        BankService bank = plugin.getBankService();
        if (bank == null) return;
        BankService.Denomination denomination = bank.denominations().stream()
                .filter(value -> value.id().equals(denominationId)).findFirst().orElse(null);
        if (denomination == null) return;
        event.getView().getTopInventory().setItem(rawSlot, coinButton(bank, denomination, current));
        try {
            long total = calculatePrice(holder, bank);
            ItemStack product = holder.offer.item().clone();
            product.setAmount(Math.min(product.getMaxStackSize(), holder.offer.amount()));
            ItemMeta meta = product.getItemMeta();
            List<String> lore = meta.hasLore() ? new java.util.ArrayList<>(meta.getLore()) : new java.util.ArrayList<>();
            lore.add("§7Lote: §f" + holder.offer.amount() + " unidades");
            lore.add("§7Preço novo: §e" + total + " Coins");
            product.setItemMeta(meta);
            event.getView().getTopInventory().setItem(4, product);
        } catch (ArithmeticException ignored) {
            player.sendMessage("§cO valor ficou acima do limite permitido.");
        }
    }

    @EventHandler
    public void customShopMenusDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof ManagementHolder || holder instanceof PriceHolder) event.setCancelled(true);
    }

    private static final class ManagementHolder implements InventoryHolder {
        final UUID viewer; final long shopId; final Map<Integer, Long> offerIds = new HashMap<>();
        Inventory inventory;
        ManagementHolder(UUID viewer, long shopId) { this.viewer = viewer; this.shopId = shopId; }
        @Override public Inventory getInventory() { return inventory; }
    }

    private static final class PriceHolder implements InventoryHolder {
        final UUID viewer; final long shopId; final long offerId; final PlayerShopService.Offer offer;
        final Map<String, Long> counts = new LinkedHashMap<>();
        final Map<Integer, String> coinSlots = new HashMap<>();
        Inventory inventory;
        PriceHolder(UUID viewer, long shopId, long offerId, PlayerShopService.Offer offer) {
            this.viewer = viewer; this.shopId = shopId; this.offerId = offerId; this.offer = offer;
        }
        @Override public Inventory getInventory() { return inventory; }
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
