package br.com.xklpack.pouch;

import br.com.xklpack.XKLPack;
import br.com.xklpack.bank.BankService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MoneyPouchService {
    private final XKLPack plugin;
    private final BankService bank;
    private final NamespacedKey pouchIdKey;
    private final NamespacedKey tokenKey;
    private final Map<UUID, UUID> openPouches = new ConcurrentHashMap<>();

    public MoneyPouchService(XKLPack plugin, BankService bank) {
        this.plugin = plugin;
        this.bank = bank;
        this.pouchIdKey = new NamespacedKey(plugin, "money_pouch_id");
        this.tokenKey = new NamespacedKey(plugin, "money_pouch_token");
    }

    public void initialize() throws SQLException {
        try (var statement = plugin.getEconomyStorage().connection().createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS money_pouches (
                    pouch_id TEXT PRIMARY KEY,
                    owner_uuid TEXT NOT NULL,
                    access_token TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS money_pouch_items (
                    pouch_id TEXT NOT NULL,
                    slot INTEGER NOT NULL,
                    denomination_id TEXT NOT NULL,
                    amount INTEGER NOT NULL,
                    PRIMARY KEY (pouch_id, slot),
                    FOREIGN KEY (pouch_id) REFERENCES money_pouches(pouch_id) ON DELETE CASCADE
                )
                """);
        }
    }

    public ItemStack createPouch(Player owner) throws SQLException {
        UUID id = UUID.randomUUID();
        String token = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = plugin.getEconomyStorage().connection().prepareStatement(
                "INSERT INTO money_pouches(pouch_id,owner_uuid,access_token,created_at,updated_at) VALUES(?,?,?,?,?)")) {
            ps.setString(1, id.toString());
            ps.setString(2, owner.getUniqueId().toString());
            ps.setString(3, token);
            ps.setLong(4, now);
            ps.setLong(5, now);
            ps.executeUpdate();
        }
        return createItem(id, token);
    }

    private ItemStack createItem(UUID id, String token) {
        Material material = Material.matchMaterial(plugin.getBankConfig().getString("money-pouch.material", "LEATHER"));
        if (material == null || !material.isItem()) material = Material.LEATHER;
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color("&6&lSaco de Coins XKL"));
        meta.setLore(java.util.List.of(color("&7Guarda somente moedas físicas oficiais."),
                color("&eClique direito para abrir."),
                color("&8Identificador protegido do XKL PACK")));
        meta.getPersistentDataContainer().set(pouchIdKey, PersistentDataType.STRING, id.toString());
        meta.getPersistentDataContainer().set(tokenKey, PersistentDataType.STRING, token);
        meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isPouch(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(pouchIdKey, PersistentDataType.STRING);
    }

    public void open(Player player, ItemStack pouchItem) throws SQLException {
        ItemMeta meta = pouchItem.getItemMeta();
        String rawId = meta.getPersistentDataContainer().get(pouchIdKey, PersistentDataType.STRING);
        String oldToken = meta.getPersistentDataContainer().get(tokenKey, PersistentDataType.STRING);
        UUID id;
        try { id = UUID.fromString(rawId); }
        catch (Exception invalid) { throw new IllegalArgumentException("Este saco não é válido."); }
        if (oldToken == null) throw new IllegalArgumentException("Este saco não possui autenticação válida.");
        if (openPouches.putIfAbsent(id, player.getUniqueId()) != null)
            throw new IllegalArgumentException("Este saco já está aberto em outra sessão.");

        String newToken = UUID.randomUUID().toString();
        boolean valid = false;
        try (PreparedStatement ps = plugin.getEconomyStorage().connection().prepareStatement(
                "UPDATE money_pouches SET access_token=?, updated_at=? WHERE pouch_id=? AND owner_uuid=? AND access_token=?")) {
            ps.setString(1, newToken);
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, id.toString());
            ps.setString(4, player.getUniqueId().toString());
            ps.setString(5, oldToken);
            valid = ps.executeUpdate() == 1;
        } catch (SQLException ex) {
            openPouches.remove(id, player.getUniqueId());
            throw ex;
        }
        if (!valid) {
            openPouches.remove(id, player.getUniqueId());
            throw new IllegalArgumentException("Saco inválido, duplicado ou pertencente a outro jogador.");
        }

        meta.getPersistentDataContainer().set(tokenKey, PersistentDataType.STRING, newToken);
        pouchItem.setItemMeta(meta);

        int rows = Math.max(1, Math.min(6, plugin.getBankConfig().getInt("money-pouch.inventory-rows", 3)));
        PouchHolder holder = new PouchHolder(id, player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, rows * 9,
                color(plugin.getBankConfig().getString("money-pouch.title", "&8Saco de Coins")));
        holder.inventory(inventory);
        try (PreparedStatement ps = plugin.getEconomyStorage().connection().prepareStatement(
                "SELECT slot, denomination_id, amount FROM money_pouch_items WHERE pouch_id=? ORDER BY slot")) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int slot = rs.getInt("slot");
                    String denominationId = rs.getString("denomination_id");
                    var denomination = bank.denominations().stream()
                            .filter(d -> d.id().equals(denominationId)).findFirst().orElse(null);
                    int amount = rs.getInt("amount");
                    if (denomination != null && slot >= 0 && slot < inventory.getSize()
                            && amount > 0 && amount <= denomination.material().getMaxStackSize()) {
                        inventory.setItem(slot, bank.createCoin(denomination, amount));
                    }
                }
            }
        } catch (Exception ex) {
            openPouches.remove(id, player.getUniqueId());
            if (ex instanceof SQLException sql) throw sql;
            throw new SQLException("Não foi possível carregar o conteúdo do saco.", ex);
        }
        player.openInventory(inventory);
    }

    public void saveAndClose(Player player, PouchHolder holder, Inventory inventory) {
        UUID id = holder.pouchId();
        if (!player.getUniqueId().equals(holder.ownerId())) return;
        try {
            Connection connection = plugin.getEconomyStorage().connection();
            synchronized (plugin.getEconomyStorage()) {
                boolean auto = connection.getAutoCommit();
                connection.setAutoCommit(false);
                try {
                    try (PreparedStatement delete = connection.prepareStatement(
                            "DELETE FROM money_pouch_items WHERE pouch_id=?")) {
                        delete.setString(1, id.toString());
                        delete.executeUpdate();
                    }
                    try (PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO money_pouch_items(pouch_id,slot,denomination_id,amount) VALUES(?,?,?,?)")) {
                        for (int slot = 0; slot < inventory.getSize(); slot++) {
                            ItemStack item = inventory.getItem(slot);
                            if (item == null || item.getType().isAir()) continue;
                            BankService.Denomination denomination = bank.identify(item);
                            if (denomination == null || item.getAmount() > denomination.material().getMaxStackSize())
                                throw new SQLException("Item inválido detectado no saco; conteúdo não foi salvo.");
                            insert.setString(1, id.toString());
                            insert.setInt(2, slot);
                            insert.setString(3, denomination.id());
                            insert.setInt(4, item.getAmount());
                            insert.addBatch();
                        }
                        insert.executeBatch();
                    }
                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE money_pouches SET updated_at=? WHERE pouch_id=?")) {
                        update.setLong(1, System.currentTimeMillis());
                        update.setString(2, id.toString());
                        update.executeUpdate();
                    }
                    connection.commit();
                } catch (Exception ex) {
                    connection.rollback();
                    throw ex;
                } finally { connection.setAutoCommit(auto); }
            }
        } catch (Exception ex) {
            player.sendMessage("§cErro ao salvar o saco de moedas. Avise a administração: " + ex.getMessage());
            plugin.getLogger().severe("Falha ao salvar saco " + id + ": " + ex.getMessage());
        } finally {
            openPouches.remove(id, player.getUniqueId());
        }
    }

    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text); }
}
