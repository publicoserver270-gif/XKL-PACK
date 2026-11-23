package br.com.xklpack.pouch;

import br.com.xklpack.XKLPack;
import br.com.xklpack.bank.BankService;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.bukkit.persistence.PersistentDataType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MoneyPouchService {
    private final XKLPack plugin;
    private final BankService bank;
    private final NamespacedKey pouchIdKey;
    private final NamespacedKey tokenKey;

    public MoneyPouchService(XKLPack plugin, BankService bank) {
        this.plugin = plugin;
        this.bank = bank;
        this.pouchIdKey = new NamespacedKey(plugin, "money_pouch_id");
        this.tokenKey = new NamespacedKey(plugin, "money_pouch_token");
    }

    public void initialize() throws SQLException {
        try (var statement = plugin.getEconomyStorage().connection().createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS money_pouch_first_claims (
                    player_uuid TEXT PRIMARY KEY,
                    pouch_id TEXT NOT NULL UNIQUE,
                    claimed_at INTEGER NOT NULL
                )
                """);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS money_pouch_pending (
                    pouch_id TEXT PRIMARY KEY,
                    player_uuid TEXT NOT NULL,
                    item_data BLOB NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """);
        }
    }

    /** Entrega uma única Trouxa XKL no primeiro saque físico do jogador. */
    public boolean giveFirstPouch(Player player) throws SQLException {
        if (!plugin.getBankConfig().getBoolean("money-pouch.enabled", true)
                || !plugin.getBankConfig().getBoolean("money-pouch.auto-give-on-first-withdrawal", true)) {
            return false;
        }

        UUID pouchId = UUID.randomUUID();
        String token = UUID.randomUUID().toString();
        Connection connection = plugin.getEconomyStorage().connection();
        synchronized (plugin.getEconomyStorage()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement check = connection.prepareStatement(
                        "SELECT 1 FROM money_pouch_first_claims WHERE player_uuid=?")) {
                    check.setString(1, player.getUniqueId().toString());
                    try (ResultSet rs = check.executeQuery()) {
                        if (rs.next()) {
                            connection.rollback();
                            return false;
                        }
                    }
                }
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO money_pouch_first_claims(player_uuid,pouch_id,claimed_at) VALUES(?,?,?)")) {
                    insert.setString(1, player.getUniqueId().toString());
                    insert.setString(2, pouchId.toString());
                    insert.setLong(3, System.currentTimeMillis());
                    insert.executeUpdate();
                }
                connection.commit();
            } catch (Exception ex) {
                connection.rollback();
                if (ex instanceof SQLException sql) throw sql;
                throw new SQLException("Não foi possível registrar a primeira Trouxa XKL.", ex);
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }

        ItemStack pouch = createItem(pouchId, token);
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(pouch);
        // A Trouxa é protegida contra descarte; se não houver espaço, fica guardada
        // no inventário extra do jogador até o próximo login, em vez de cair no chão.
        if (!overflow.isEmpty()) {
            Map<Integer, ItemStack> enderOverflow = player.getEnderChest().addItem(overflow.values().toArray(new ItemStack[0]));
            for (ItemStack remaining : enderOverflow.values()) storePending(player, remaining);
            player.sendMessage(enderOverflow.isEmpty()
                    ? "§eSeu inventário estava cheio. A Trouxa XKL foi enviada ao seu baú do Ender."
                    : "§eSua Trouxa ficou guardada com segurança e será entregue quando houver espaço.");
        }
        player.sendMessage("§6§lBANCO XKL §8» §aVocê recebeu sua Trouxa XKL. Ela usa o armazenamento normal de uma Trouxa do Minecraft.");
        return true;
    }

    public ItemStack createPouch(Player owner) {
        return createItem(UUID.randomUUID(), UUID.randomUUID().toString());
    }

    private ItemStack createItem(UUID id, String token) {
        ItemStack item = new ItemStack(Material.BUNDLE, 1);
        ItemMeta baseMeta = item.getItemMeta();
        if (!(baseMeta instanceof BundleMeta meta)) {
            throw new IllegalStateException("A API do servidor não oferece suporte à Trouxa (BundleMeta).");
        }
        meta.setDisplayName(color("&6&lTrouxa XKL"));
        meta.setLore(List.of(
                color("&7Armazena somente moedas físicas oficiais."),
                color("&7Capacidade normal da Trouxa do Minecraft."),
                color("&8Item protegido pelo XKL PACK")
        ));
        meta.getPersistentDataContainer().set(pouchIdKey, PersistentDataType.STRING, id.toString());
        meta.getPersistentDataContainer().set(tokenKey, PersistentDataType.STRING, token);
        meta.setItems(List.of());
        item.setItemMeta(meta);
        return item;
    }

    public boolean isPouch(ItemStack item) {
        if (item == null || item.getType() != Material.BUNDLE || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(pouchIdKey, PersistentDataType.STRING)
                && item.getItemMeta().getPersistentDataContainer().has(tokenKey, PersistentDataType.STRING);
    }

    public boolean containsOnlyOfficialCoins(ItemStack pouch) {
        if (!isPouch(pouch) || !(pouch.getItemMeta() instanceof BundleMeta meta)) return false;
        for (ItemStack stored : meta.getItems()) {
            if (bank.identify(stored) == null) return false;
        }
        return true;
    }

    public boolean isOfficialCoin(ItemStack item) {
        return bank.identify(item) != null;
    }

    /** Persiste uma Trouxa que não coube nem no inventário nem no Ender Chest. */
    public void storePending(Player player, ItemStack pouch) throws SQLException {
        if (!isPouch(pouch)) return;
        String id = pouch.getItemMeta().getPersistentDataContainer().get(pouchIdKey, PersistentDataType.STRING);
        if (id == null) throw new SQLException("Trouxa sem identificador persistente.");
        byte[] data;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (BukkitObjectOutputStream output = new BukkitObjectOutputStream(bytes)) {
                output.writeObject(pouch.clone());
            }
            data = bytes.toByteArray();
        } catch (Exception exception) {
            throw new SQLException("Não foi possível preservar a Trouxa pendente.", exception);
        }
        synchronized (plugin.getEconomyStorage()) {
            try (PreparedStatement statement = plugin.getEconomyStorage().connection().prepareStatement("""
                    INSERT INTO money_pouch_pending(pouch_id, player_uuid, item_data, updated_at)
                    VALUES(?, ?, ?, ?)
                    ON CONFLICT(pouch_id) DO UPDATE SET
                      player_uuid=excluded.player_uuid,
                      item_data=excluded.item_data,
                      updated_at=excluded.updated_at
                    """)) {
                statement.setString(1, id);
                statement.setString(2, player.getUniqueId().toString());
                statement.setBytes(3, data);
                statement.setLong(4, System.currentTimeMillis());
                statement.executeUpdate();
            }
        }
    }

    /** Tenta entregar todas as Trouxas pendentes; o que não couber continua salvo no SQLite. */
    public void deliverPending(Player player) {
        synchronized (plugin.getEconomyStorage()) {
            try (PreparedStatement query = plugin.getEconomyStorage().connection().prepareStatement(
                    "SELECT pouch_id, item_data FROM money_pouch_pending WHERE player_uuid=? ORDER BY updated_at")) {
                query.setString(1, player.getUniqueId().toString());
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) {
                        String id = rows.getString("pouch_id");
                        ItemStack pouch;
                        try (BukkitObjectInputStream input = new BukkitObjectInputStream(
                                new ByteArrayInputStream(rows.getBytes("item_data")))) {
                            Object value = input.readObject();
                            pouch = value instanceof ItemStack item ? item : null;
                        } catch (Exception exception) {
                            plugin.getLogger().severe("Trouxa pendente corrompida (" + id + "): " + exception.getMessage());
                            continue;
                        }
                        if (!isPouch(pouch)) continue;
                        Map<Integer, ItemStack> overflow = player.getInventory().addItem(pouch);
                        if (!overflow.isEmpty()) overflow = player.getEnderChest().addItem(overflow.values().toArray(new ItemStack[0]));
                        try (PreparedStatement update = plugin.getEconomyStorage().connection().prepareStatement(
                                overflow.isEmpty()
                                        ? "DELETE FROM money_pouch_pending WHERE pouch_id=?"
                                        : "UPDATE money_pouch_pending SET item_data=?, updated_at=? WHERE pouch_id=?")) {
                            if (overflow.isEmpty()) {
                                update.setString(1, id);
                            } else {
                                ItemStack remaining = overflow.values().iterator().next();
                                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                                try (BukkitObjectOutputStream output = new BukkitObjectOutputStream(bytes)) {
                                    output.writeObject(remaining);
                                }
                                update.setBytes(1, bytes.toByteArray());
                                update.setLong(2, System.currentTimeMillis());
                                update.setString(3, id);
                            }
                            update.executeUpdate();
                        }
                        if (overflow.isEmpty()) player.sendMessage("§aSua Trouxa XKL pendente foi recuperada.");
                    }
                }
            } catch (Exception exception) {
                plugin.getLogger().severe("Falha ao entregar Trouxas pendentes para " + player.getName() + ": " + exception.getMessage());
            }
        }
    }

    public void restorePouch(Player player, ItemStack pouch) {
        if (pouch == null || !isPouch(pouch)) return;
        try {
            storePending(player, pouch);
            plugin.getServer().getScheduler().runTask(plugin, () -> deliverPending(player));
        } catch (SQLException exception) {
            plugin.getLogger().severe("Não foi possível salvar Trouxa para recuperação: " + exception.getMessage());
        }
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }
}
