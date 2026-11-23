package br.com.xklpack.shop;

import br.com.xklpack.XKLPack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

/**
 * Persistência inicial das lojas de jogadores.
 *
 * Esta camada não cria lojas por conta própria: a criação pública só poderá
 * chamar createPreparingShop depois de validar permissões e proteção do local.
 */
public final class PlayerShopService {
    public static final long PREPARATION_MILLIS = 120_000L;
    private static final List<String> MERCHANT_NAMES = List.of(
            "João", "Pedro", "Lucas", "Miguel", "Arthur", "Gabriel",
            "Davi", "Rafael", "Matheus", "Gustavo", "Felipe", "Bruno",
            "Henrique", "André", "Caio", "Eduardo", "Enzo", "Leonardo",
            "Thiago", "Samuel", "Bernardo", "Heitor", "Nicolas", "Daniel"
    );

    private final XKLPack plugin;
    private final int areaRadius;

    public PlayerShopService(XKLPack plugin) throws SQLException {
        this.plugin = plugin;
        this.areaRadius = Math.max(2, plugin.getConfig().getInt("player-shops.area-radius", 4));
        initialize(plugin.getEconomyStorage().connection());
    }

    private void initialize(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_shops (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        owner_uuid TEXT NOT NULL,
                        world_uuid TEXT NOT NULL,
                        world_name TEXT NOT NULL,
                        center_x INTEGER NOT NULL,
                        center_y INTEGER NOT NULL,
                        center_z INTEGER NOT NULL,
                        area_radius INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        activation_at INTEGER NOT NULL,
                        merchant_name TEXT NOT NULL UNIQUE,
                        citizens_npc_id INTEGER,
                        stock_world_uuid TEXT,
                        stock_x INTEGER,
                        stock_y INTEGER,
                        stock_z INTEGER,
                        UNIQUE(world_uuid, center_x, center_y, center_z)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_shop_allies (
                        shop_id INTEGER NOT NULL,
                        player_uuid TEXT NOT NULL,
                        can_stock INTEGER NOT NULL DEFAULT 0,
                        can_manage_products INTEGER NOT NULL DEFAULT 0,
                        can_withdraw INTEGER NOT NULL DEFAULT 0,
                        can_configure INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(shop_id, player_uuid),
                        FOREIGN KEY(shop_id) REFERENCES player_shops(id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_shop_offers (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        shop_id INTEGER NOT NULL,
                        item_fingerprint TEXT NOT NULL,
                        item_data TEXT NOT NULL,
                        amount INTEGER NOT NULL CHECK(amount > 0),
                        price_coins INTEGER NOT NULL CHECK(price_coins > 0),
                        enabled INTEGER NOT NULL DEFAULT 1,
                        FOREIGN KEY(shop_id) REFERENCES player_shops(id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_shop_sales (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        shop_id INTEGER NOT NULL,
                        buyer_uuid TEXT NOT NULL,
                        offer_id INTEGER NOT NULL,
                        item_amount INTEGER NOT NULL,
                        physical_coin_value INTEGER NOT NULL,
                        created_at INTEGER NOT NULL,
                        FOREIGN KEY(shop_id) REFERENCES player_shops(id)
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_player_shops_owner ON player_shops(owner_uuid)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_player_shops_world ON player_shops(world_uuid)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_player_shop_allies_player ON player_shop_allies(player_uuid)");
        }
        ensureColumn(connection, "player_shops", "merchant_entity_uuid", "TEXT");
    }

    private void ensureColumn(Connection connection, String table, String column, String type) throws SQLException {
        boolean found = false;
        try (Statement statement = connection.createStatement();
             ResultSet columns = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (columns.next()) if (column.equalsIgnoreCase(columns.getString("name"))) found = true;
        }
        if (!found) try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
        }
    }

    public List<ShopRecord> shopsDueForActivation(long now) throws SQLException {
        List<ShopRecord> result = new java.util.ArrayList<>();
        String sql = "SELECT id, owner_uuid, world_uuid, world_name, center_x, center_y, center_z, area_radius, merchant_name, activation_at, stock_x, stock_y, stock_z, merchant_entity_uuid FROM player_shops WHERE status='PREPARING' AND activation_at<=?";
        try (PreparedStatement statement = plugin.getEconomyStorage().connection().prepareStatement(sql)) {
            statement.setLong(1, now);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(new ShopRecord(
                        rows.getLong("id"), UUID.fromString(rows.getString("owner_uuid")),
                        UUID.fromString(rows.getString("world_uuid")), rows.getString("world_name"),
                        rows.getInt("center_x"), rows.getInt("center_y"), rows.getInt("center_z"),
                        rows.getInt("area_radius"), rows.getString("merchant_name"),
                        rows.getLong("activation_at"), rows.getInt("stock_x"), rows.getInt("stock_y"),
                        rows.getInt("stock_z"), rows.getString("merchant_entity_uuid")));
            }
        }
        return result;
    }

    public void saveShopRuntime(long shopId, UUID merchantEntity, int stockX, int stockY, int stockZ) throws SQLException {
        try (PreparedStatement statement = plugin.getEconomyStorage().connection().prepareStatement(
                "UPDATE player_shops SET status='ACTIVE', merchant_entity_uuid=?, stock_world_uuid=world_uuid, stock_x=?, stock_y=?, stock_z=? WHERE id=? AND status='PREPARING'")) {
            statement.setString(1, merchantEntity.toString());
            statement.setInt(2, stockX);
            statement.setInt(3, stockY);
            statement.setInt(4, stockZ);
            statement.setLong(5, shopId);
            statement.executeUpdate();
        }
    }

    public record ShopRecord(long id, UUID owner, UUID worldId, String worldName,
                             int x, int y, int z, int radius, String merchantName,
                             long activationAt, int stockX, int stockY, int stockZ,
                             String merchantEntityUuid) {}

    /**
     * Verifica colisão com a área reservada de outra loja. Não substitui a
     * validação de proteção/claim, que deve ocorrer antes de consumir alvará.
     */
    public boolean overlapsExistingShop(UUID worldId, int x, int z, int requestedRadius) throws SQLException {
        String sql = """
                SELECT 1 FROM player_shops
                WHERE world_uuid = ?
                  AND status <> 'REMOVED'
                  AND ABS(center_x - ?) <= area_radius + ?
                  AND ABS(center_z - ?) <= area_radius + ?
                LIMIT 1
                """;
        try (PreparedStatement statement = plugin.getEconomyStorage().connection().prepareStatement(sql)) {
            statement.setString(1, worldId.toString());
            statement.setInt(2, x);
            statement.setInt(3, Math.max(1, requestedRadius));
            statement.setInt(4, z);
            statement.setInt(5, Math.max(1, requestedRadius));
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    public boolean overlapsExistingShop(UUID worldId, int x, int z) throws SQLException {
        return overlapsExistingShop(worldId, x, z, areaRadius);
    }

    /**
     * Reserva o ponto e grava o prazo exato de dois minutos. O chamador é
     * responsável por validar terreno, espaço e autenticidade do alvará antes.
     */
    public long createPreparingShop(UUID owner, UUID worldId, String worldName,
                                    int x, int y, int z) throws SQLException {
        return createPreparingShop(owner, worldId, worldName, x, y, z, areaRadius);
    }

    public long createPreparingShop(UUID owner, UUID worldId, String worldName,
                                    int x, int y, int z, int requestedRadius) throws SQLException {
        int safeRadius = Math.max(1, requestedRadius);
        if (overlapsExistingShop(worldId, x, z, safeRadius)) {
            throw new IllegalArgumentException("Esta área já está reservada por outra loja.");
        }
        long now = System.currentTimeMillis();
        long activationAt = Math.addExact(now, PREPARATION_MILLIS);
        Connection connection = plugin.getEconomyStorage().connection();
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            String merchantName = chooseUnusedMerchantName(connection);
            if (merchantName == null) {
                throw new IllegalStateException("Não há nomes de comerciante disponíveis.");
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO player_shops
                    (owner_uuid, world_uuid, world_name, center_x, center_y, center_z,
                     area_radius, status, created_at, activation_at, merchant_name)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'PREPARING', ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, owner.toString());
                statement.setString(2, worldId.toString());
                statement.setString(3, worldName);
                statement.setInt(4, x);
                statement.setInt(5, y);
                statement.setInt(6, z);
                statement.setInt(7, safeRadius);
                statement.setLong(8, now);
                statement.setLong(9, activationAt);
                statement.setString(10, merchantName);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("Não foi possível obter o ID da loja criada.");
                    long id = keys.getLong(1);
                    connection.commit();
                    return id;
                }
            }
        } catch (SQLException | RuntimeException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private String chooseUnusedMerchantName(Connection connection) throws SQLException {
        // O nome nunca é reciclado, mesmo depois que uma loja é removida.
        // Sufixos permitem quantidade ilimitada de comerciantes sem repetir nomes.
        for (String base : MERCHANT_NAMES) {
            for (int suffix = 1; suffix < Integer.MAX_VALUE; suffix++) {
                String candidate = suffix == 1 ? base : base + " " + suffix;
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT 1 FROM player_shops WHERE merchant_name = ? LIMIT 1")) {
                    statement.setString(1, candidate);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) return candidate;
                    }
                }
            }
        }
        throw new IllegalStateException("Não foi possível gerar um nome único para o comerciante.");
    }


    public boolean isOwner(long shopId, UUID playerId) throws SQLException {
        try (PreparedStatement ps = plugin.getEconomyStorage().connection().prepareStatement(
                "SELECT 1 FROM player_shops WHERE id=? AND owner_uuid=? AND status<>'REMOVED'")) {
            ps.setLong(1, shopId); ps.setString(2, playerId.toString());
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    public boolean isAuthorizedForStock(long shopId, UUID playerId) throws SQLException {
        if (isOwner(shopId, playerId)) return true;
        try (PreparedStatement ps = plugin.getEconomyStorage().connection().prepareStatement(
                "SELECT can_stock FROM player_shop_allies WHERE shop_id=? AND player_uuid=?")) {
            ps.setLong(1, shopId); ps.setString(2, playerId.toString());
            try (ResultSet rs = ps.executeQuery()) { return rs.next() && rs.getInt(1) == 1; }
        }
    }

    public void addAlly(long shopId, UUID owner, UUID ally) throws SQLException {
        if (!isOwner(shopId, owner)) throw new IllegalArgumentException("Essa loja não pertence a você.");
        if (owner.equals(ally)) throw new IllegalArgumentException("O dono já possui todas as permissões.");
        try (PreparedStatement ps = plugin.getEconomyStorage().connection().prepareStatement("""
                INSERT INTO player_shop_allies(shop_id, player_uuid, can_stock, can_manage_products, can_withdraw, can_configure)
                VALUES(?,?,1,1,1,1)
                ON CONFLICT(shop_id, player_uuid) DO UPDATE SET can_stock=1, can_manage_products=1, can_withdraw=1, can_configure=1
                """)) {
            ps.setLong(1, shopId); ps.setString(2, ally.toString()); ps.executeUpdate();
        }
    }


    public void setAllyPermission(long shopId, UUID owner, UUID ally,
                                   String permission, boolean enabled) throws SQLException {
        if (!isOwner(shopId, owner)) throw new IllegalArgumentException("Essa loja não pertence a você.");
        if (owner.equals(ally)) throw new IllegalArgumentException("O dono não precisa de permissões de aliado.");
        String column = switch (permission.toLowerCase(java.util.Locale.ROOT)) {
            case "estoque", "stock" -> "can_stock";
            case "produtos", "products" -> "can_manage_products";
            case "retirar", "withdraw" -> "can_withdraw";
            case "configurar", "configure" -> "can_configure";
            default -> throw new IllegalArgumentException("Permissão inválida. Use estoque, produtos, retirar ou configurar.");
        };
        String sql = "UPDATE player_shop_allies SET " + column + "=? WHERE shop_id=? AND player_uuid=?";
        try (PreparedStatement ps = plugin.getEconomyStorage().connection().prepareStatement(sql)) {
            ps.setInt(1, enabled ? 1 : 0);
            ps.setLong(2, shopId);
            ps.setString(3, ally.toString());
            if (ps.executeUpdate() == 0) throw new IllegalArgumentException("Este jogador não é aliado desta loja.");
        }
    }

    public void removeAlly(long shopId, UUID owner, UUID ally) throws SQLException {
        if (!isOwner(shopId, owner)) throw new IllegalArgumentException("Essa loja não pertence a você.");
        try (PreparedStatement ps = plugin.getEconomyStorage().connection().prepareStatement(
                "DELETE FROM player_shop_allies WHERE shop_id=? AND player_uuid=?")) {
            ps.setLong(1, shopId); ps.setString(2, ally.toString()); ps.executeUpdate();
        }
    }

    public List<UUID> listAllies(long shopId, UUID owner) throws SQLException {
        if (!isOwner(shopId, owner)) throw new IllegalArgumentException("Essa loja não pertence a você.");
        List<UUID> result = new java.util.ArrayList<>();
        try (PreparedStatement ps = plugin.getEconomyStorage().connection().prepareStatement(
                "SELECT player_uuid FROM player_shop_allies WHERE shop_id=? ORDER BY player_uuid")) {
            ps.setLong(1, shopId);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) result.add(UUID.fromString(rs.getString(1))); }
        }
        return result;
    }

    public List<Long> listOwnedShops(UUID owner) throws SQLException {
        List<Long> result = new java.util.ArrayList<>();
        try (PreparedStatement ps = plugin.getEconomyStorage().connection().prepareStatement(
                "SELECT id FROM player_shops WHERE owner_uuid=? AND status<>'REMOVED' ORDER BY id")) {
            ps.setString(1, owner.toString());
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) result.add(rs.getLong(1)); }
        }
        return result;
    }

    public int areaRadius() {
        return areaRadius;
    }
}
