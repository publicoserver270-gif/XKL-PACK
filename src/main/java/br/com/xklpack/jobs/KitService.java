package br.com.xklpack.jobs;

import br.com.xklpack.XKLPack;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class KitService implements Listener {
    private final XKLPack plugin;
    private final NamespacedKey markerKey;
    private final NamespacedKey jobKey;
    private final NamespacedKey ownerKey;
    private final NamespacedKey tierKey;

    public KitService(XKLPack plugin) {
        this.plugin = plugin;
        this.markerKey = new NamespacedKey(plugin, "profession_kit");
        this.jobKey = new NamespacedKey(plugin, "profession_kit_job");
        this.ownerKey = new NamespacedKey(plugin, "profession_kit_owner");
        this.tierKey = new NamespacedKey(plugin, "profession_kit_tier");
    }

    public void initialize(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS profession_kit_state (
                    player_uuid TEXT NOT NULL,
                    job_id TEXT NOT NULL,
                    item_data BLOB NOT NULL,
                    updated_at INTEGER NOT NULL,
                    PRIMARY KEY(player_uuid, job_id)
                )
                """);
        }
    }

    public void switchProfession(Player player, String oldJob, String newJob, int newLevel) throws SQLException {
        if (!enabled()) return;
        if (oldJob != null && !oldJob.equalsIgnoreCase(newJob)) saveAndRemove(player, oldJob);
        if (newJob != null) ensureKit(player, newJob, newLevel);
    }

    public void leaveProfession(Player player, String oldJob) throws SQLException {
        if (enabled() && oldJob != null) saveAndRemove(player, oldJob);
    }

    public void upgradeIfNeeded(Player player, String jobId, int level) {
        if (!enabled()) return;
        try {
            KitLocation current = findOwnedKit(player, jobId);
            if (current == null) {
                ensureKit(player, jobId, level);
                return;
            }
            int previousTier = current.item().getItemMeta().getPersistentDataContainer()
                    .getOrDefault(tierKey, PersistentDataType.INTEGER, 0);
            ItemStack upgraded = makeUnbreakable(upgrade(current.item(), jobId, level, player.getUniqueId()));
            player.getInventory().setItem(current.slot(), upgraded);
            saveState(player.getUniqueId(), jobId, upgraded);
            if (selectedTier(jobId, level) > previousTier) {
                player.sendMessage("§6§lKIT EVOLUÍDO! §eSua ferramenta profissional evoluiu automaticamente.");
            }
        } catch (Exception exception) {
            plugin.getLogger().severe("Falha ao evoluir kit de " + player.getName() + ": " + exception.getMessage());
            player.sendMessage("§cNão foi possível atualizar seu kit. Avise a administração.");
        }
    }

    public void ensureActiveKit(Player player) {
        if (!enabled()) return;
        try {
            String job = activeJob(player.getUniqueId());
            if (job == null) return;
            int level = activeLevel(player.getUniqueId(), job);
            ensureKit(player, job, level);
        } catch (Exception exception) {
            plugin.getLogger().severe("Falha ao restaurar kit de " + player.getName() + ": " + exception.getMessage());
        }
    }

    private void ensureKit(Player player, String jobId, int level) throws SQLException {
        if (!hasKitConfiguration(jobId)) return;
        KitLocation current = findOwnedKit(player, jobId);
        if (current != null) {
            ItemStack upgraded = upgrade(current.item(), jobId, level, player.getUniqueId());
            player.getInventory().setItem(current.slot(), upgraded);
            removeDuplicateKits(player, jobId, current.slot());
            deleteState(player.getUniqueId(), jobId);
            return;
        }

        ItemStack item = loadState(player.getUniqueId(), jobId);
        if (item == null || !isOwnedKit(item, player.getUniqueId(), jobId)) {
            item = createKit(player.getUniqueId(), jobId, level);
        } else {
            item = upgrade(item, jobId, level, player.getUniqueId());
        }
        item = makeUnbreakable(item);

        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        if (overflow.isEmpty()) {
            deleteState(player.getUniqueId(), jobId);
            player.sendMessage("§aSeu kit de profissão foi entregue.");
        } else {
            saveState(player.getUniqueId(), jobId, item);
            player.sendMessage("§eLibere espaço no inventário para receber seu kit profissional. Ele está guardado com segurança.");
        }
    }

    private ItemStack upgrade(ItemStack oldItem, String jobId, int level, UUID owner) {
        int targetTier = selectedTier(jobId, level);
        int currentTier = oldItem.getItemMeta().getPersistentDataContainer()
                .getOrDefault(tierKey, PersistentDataType.INTEGER, 0);
        if (currentTier >= targetTier) return makeUnbreakable(oldItem);

        // Kits profissionais não sofrem desgaste: ao evoluir, a nova ferramenta
        // começa sem dano e continua indestrutível.
        return createKit(owner, jobId, level);
    }

    private ItemStack createKit(UUID owner, String jobId, int level) {
        ConfigurationSection profession = plugin.getConfig().getConfigurationSection("jobs.kits.professions." + jobId);
        if (profession == null) throw new IllegalArgumentException("Kit não configurado para " + jobId);
        int tier = selectedTier(jobId, level);
        String tierPath = "jobs.kits.professions." + jobId + ".tiers." + tier;
        String materialName = plugin.getConfig().getString(tierPath + ".material", "");
        Material material = Material.matchMaterial(materialName);
        if (material == null) throw new IllegalStateException("Material inválido no kit " + jobId + ": " + materialName);

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        String displayName = plugin.getConfig().getString("jobs.kits.professions." + jobId + ".display-name",
                "&6Ferramenta profissional");
        meta.setDisplayName(color(displayName) + " §8[§7Nível " + tier + "§8]");

        List<String> lore = new ArrayList<>();
        lore.add("§7Ferramenta vinculada à profissão.");
        lore.add("§7Profissão: §e" + plugin.getConfig().getString("jobs.professions." + jobId + ".name", jobId));
        lore.add("§7Evolui automaticamente conforme seu nível.");
        lore.add("§8Não pode ser transferida, guardada ou descartada.");
        meta.setLore(lore);
        meta.setUnbreakable(true);

        ConfigurationSection enchantments = plugin.getConfig().getConfigurationSection(tierPath + ".enchantments");
        if (enchantments != null) {
            for (String key : enchantments.getKeys(false)) {
                Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(key.toLowerCase()));
                int enchantmentLevel = Math.max(1, enchantments.getInt(key, 1));
                if (enchantment != null) meta.addEnchant(enchantment, enchantmentLevel, true);
                else plugin.getLogger().warning("Encantamento inválido no kit " + jobId + ": " + key);
            }
        }

        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(jobKey, PersistentDataType.STRING, jobId);
        meta.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner.toString());
        meta.getPersistentDataContainer().set(tierKey, PersistentDataType.INTEGER, tier);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeUnbreakable(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return item;
        ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        if (meta instanceof Damageable damageable) damageable.setDamage(0);
        item.setItemMeta(meta);
        return item;
    }

    private int selectedTier(String jobId, int level) {
        ConfigurationSection tiers = plugin.getConfig().getConfigurationSection("jobs.kits.professions." + jobId + ".tiers");
        if (tiers == null) throw new IllegalArgumentException("Progressão do kit não configurada: " + jobId);
        List<Integer> levels = new ArrayList<>();
        for (String key : tiers.getKeys(false)) {
            try { levels.add(Integer.parseInt(key)); }
            catch (NumberFormatException ignored) {}
        }
        levels.sort(Comparator.naturalOrder());
        if (levels.isEmpty()) throw new IllegalArgumentException("Kit sem níveis configurados: " + jobId);
        int selected = levels.get(0);
        for (int required : levels) {
            if (required <= level) selected = required;
            else break;
        }
        return selected;
    }

    private KitLocation findOwnedKit(Player player, String jobId) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (isOwnedKit(item, player.getUniqueId(), jobId)) return new KitLocation(slot, item);
        }
        return null;
    }

    private void removeDuplicateKits(Player player, String jobId, int keepSlot) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (slot == keepSlot) continue;
            ItemStack item = contents[slot];
            if (isOwnedKit(item, player.getUniqueId(), jobId)) player.getInventory().setItem(slot, null);
        }
    }

    private void saveAndRemove(Player player, String jobId) throws SQLException {
        KitLocation location = findOwnedKit(player, jobId);
        if (location != null) saveState(player.getUniqueId(), jobId, location.item());
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isOwnedKit(contents[slot], player.getUniqueId(), jobId)) player.getInventory().setItem(slot, null);
        }
    }

    private void saveCurrent(Player player) {
        try {
            String job = activeJob(player.getUniqueId());
            if (job == null) return;
            KitLocation location = findOwnedKit(player, job);
            if (location != null) saveState(player.getUniqueId(), job, location.item());
        } catch (Exception exception) {
            plugin.getLogger().severe("Falha ao salvar kit de " + player.getName() + ": " + exception.getMessage());
        }
    }

    private void saveState(UUID owner, String jobId, ItemStack item) throws SQLException {
        byte[] data = serialize(item);
        synchronized (plugin.getEconomyStorage()) {
            try (PreparedStatement statement = plugin.getEconomyStorage().connection().prepareStatement("""
                    INSERT INTO profession_kit_state(player_uuid, job_id, item_data, updated_at)
                    VALUES(?, ?, ?, ?)
                    ON CONFLICT(player_uuid, job_id) DO UPDATE SET
                      item_data = excluded.item_data,
                      updated_at = excluded.updated_at
                    """)) {
                statement.setString(1, owner.toString());
                statement.setString(2, jobId);
                statement.setBytes(3, data);
                statement.setLong(4, System.currentTimeMillis());
                statement.executeUpdate();
            }
        }
    }

    private ItemStack loadState(UUID owner, String jobId) throws SQLException {
        synchronized (plugin.getEconomyStorage()) {
            try (PreparedStatement statement = plugin.getEconomyStorage().connection().prepareStatement(
                    "SELECT item_data FROM profession_kit_state WHERE player_uuid=? AND job_id=?")) {
                statement.setString(1, owner.toString());
                statement.setString(2, jobId);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) return null;
                    try {
                        return deserialize(result.getBytes("item_data"));
                    } catch (Exception exception) {
                        throw new SQLException("Não foi possível restaurar o kit salvo.", exception);
                    }
                }
            }
        }
    }

    private void deleteState(UUID owner, String jobId) throws SQLException {
        synchronized (plugin.getEconomyStorage()) {
            try (PreparedStatement statement = plugin.getEconomyStorage().connection().prepareStatement(
                    "DELETE FROM profession_kit_state WHERE player_uuid=? AND job_id=?")) {
                statement.setString(1, owner.toString());
                statement.setString(2, jobId);
                statement.executeUpdate();
            }
        }
    }

    private byte[] serialize(ItemStack item) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (BukkitObjectOutputStream output = new BukkitObjectOutputStream(bytes)) {
                output.writeObject(item);
            }
            return bytes.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Não foi possível serializar o kit.", exception);
        }
    }

    private ItemStack deserialize(byte[] bytes) throws Exception {
        try (BukkitObjectInputStream input = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
            Object value = input.readObject();
            return value instanceof ItemStack item ? item : null;
        }
    }

    private String activeJob(UUID owner) throws SQLException {
        synchronized (plugin.getEconomyStorage()) {
            try (PreparedStatement statement = plugin.getEconomyStorage().connection().prepareStatement(
                    "SELECT job_id FROM job_progress WHERE player_uuid=? AND active=1 LIMIT 1")) {
                statement.setString(1, owner.toString());
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? result.getString("job_id") : null;
                }
            }
        }
    }

    private int activeLevel(UUID owner, String jobId) throws SQLException {
        synchronized (plugin.getEconomyStorage()) {
            try (PreparedStatement statement = plugin.getEconomyStorage().connection().prepareStatement(
                    "SELECT level FROM job_progress WHERE player_uuid=? AND job_id=?")) {
                statement.setString(1, owner.toString());
                statement.setString(2, jobId);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? result.getInt("level") : 1;
                }
            }
        }
    }

    private boolean isOwnedKit(ItemStack item, UUID owner, String jobId) {
        if (!isKit(item)) return false;
        ItemMeta meta = item.getItemMeta();
        String itemOwner = meta.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        String itemJob = meta.getPersistentDataContainer().get(jobKey, PersistentDataType.STRING);
        return owner.toString().equals(itemOwner) && jobId.equalsIgnoreCase(itemJob);
    }

    public boolean isKit(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(markerKey, PersistentDataType.BYTE);
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("jobs.kits.enabled", true);
    }

    private boolean hasKitConfiguration(String jobId) {
        return plugin.getConfig().isConfigurationSection("jobs.kits.professions." + jobId + ".tiers");
    }

    private String color(String value) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> ensureActiveKit(event.getPlayer()));
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> ensureActiveKit(event.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        saveCurrent(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        try {
            String job = activeJob(player.getUniqueId());
            if (job != null) saveAndRemove(player, job);
        } catch (Exception exception) {
            plugin.getLogger().severe("Falha ao preservar kit na morte de " + player.getName() + ": " + exception.getMessage());
        }
        event.getDrops().removeIf(this::isKit);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (isKit(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cSeu kit profissional não pode ser descartado ou transferido.");
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        boolean clickedTop = event.getClickedInventory() == top;
        if (clickedTop && (isKit(event.getCursor()) || isKit(event.getCurrentItem()))) {
            event.setCancelled(true);
            player.sendMessage("§cSeu kit profissional não pode ser guardado em baús ou outras interfaces.");
            return;
        }
        if (event.isShiftClick() && isKit(event.getCurrentItem())) {
            event.setCancelled(true);
            player.sendMessage("§cSeu kit profissional não pode ser transferido.");
            return;
        }
        if (clickedTop && event.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY) {
            ItemStack hotbar = player.getInventory().getItem(event.getHotbarButton());
            if (isKit(hotbar)) event.setCancelled(true);
        }
        if (clickedTop && event.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND
                && isKit(player.getInventory().getItemInOffHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getRawSlots().stream().anyMatch(slot -> slot < event.getView().getTopInventory().getSize())
                && event.getNewItems().values().stream().anyMatch(this::isKit)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (isKit(event.getItem())) event.setCancelled(true);
    }

    @EventHandler
    public void onItemBreak(PlayerItemBreakEvent event) {
        if (!isKit(event.getBrokenItem())) return;
        Player player = event.getPlayer();
        try {
            String job = activeJob(player.getUniqueId());
            if (job != null) deleteState(player.getUniqueId(), job);
        } catch (Exception exception) {
            plugin.getLogger().warning("Não foi possível limpar o estado do kit quebrado: " + exception.getMessage());
        }
        Bukkit.getScheduler().runTask(plugin, () -> ensureActiveKit(player));
    }
    
    private record KitLocation(int slot, ItemStack item) {}
}
