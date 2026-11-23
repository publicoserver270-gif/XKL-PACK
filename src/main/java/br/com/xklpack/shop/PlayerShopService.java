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
    }

    /**
     * Verifica colisão com a área reservada de outra loja. Não substitui a
     * validação de proteção/claim, que deve ocorrer antes de consumir alvará.
     */
    public boolean overlapsExistingShop(UUID worldId, int x, int z) throws SQLException {
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
            statement.setInt(3, areaRadius);
            statement.setInt(4, z);
            statement.setInt(5, areaRadius);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    /**
     * Reserva o ponto e grava o prazo exato de dois minutos. O chamador é
     * responsável por validar terreno, espaço e autenticidade do alvará antes.
     */
    public long createPreparingShop(UUID owner, UUID worldId, String worldName,
                                    int x, int y, int z) throws SQLException {
        if (overlapsExistingShop(worldId, x, z)) {
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
                statement.setInt(7, areaRadius);
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

    public int areaRadius() {
        return areaRadius;
    }
}
