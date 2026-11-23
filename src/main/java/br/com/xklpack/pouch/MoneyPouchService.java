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
            player.getEnderChest().addItem(overflow.values().toArray(new ItemStack[0]));
            player.sendMessage("§eSeu inventário estava cheio. A Trouxa XKL foi enviada ao seu baú do Ender.");
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

    public void restorePouch(Player player, ItemStack pouch) {
        if (pouch == null || !isPouch(pouch)) return;
        if (!player.getInventory().containsAtLeast(pouch, 1)) {
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(pouch);
            if (!overflow.isEmpty()) {
                // Não descarta uma Trouxa protegida; tenta novamente no próximo tick.
                plugin.getServer().getScheduler().runTask(plugin, () -> restorePouch(player, pouch));
            }
        }
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }
}
