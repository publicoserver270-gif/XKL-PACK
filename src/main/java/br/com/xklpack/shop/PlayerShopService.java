package br.com.xklpack.shop;

import br.com.xklpack.XKLPack;
import br.com.xklpack.bank.BankService;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.World;
import org.bukkit.entity.Player;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

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
        ensureColumn(connection, "player_shops", "vault_coins", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(connection, "player_shops", "area_size", "INTEGER NOT NULL DEFAULT 9");
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
        String sql = "SELECT id, owner_uuid, world_uuid, world_name, center_x, center_y, center_z, area_radius, area_size, merchant_name, activation_at, stock_x, stock_y, stock_z, merchant_entity_uuid FROM player_shops WHERE status='PREPARING' AND activation_at<=?";
        try (PreparedStatement statement = plugin.getEconomyStorage().connection().prepareStatement(sql)) {
            statement.setLong(1, now);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(new ShopRecord(
                        rows.getLong("id"), UUID.fromString(rows.getString("owner_uuid")),
                        UUID.fromString(rows.getString("world_uuid")), rows.getString("world_name"),
                        rows.getInt("center_x"), rows.getInt("center_y"), rows.getInt("center_z"),
                        rows.getInt("area_radius"), rows.getInt("area_size"), rows.getString("merchant_name"),
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
                             int x, int y, int z, int radius, int size, String merchantName,
                             long activationAt, int stockX, int stockY, int stockZ,
                             String merchantEntityUuid) {}

    /**
     * Verifica colisão com a área reservada de outra loja. Não substitui a
     * validação de proteção/claim, que deve ocorrer antes de consumir alvará.
     */
    public boolean overlapsExistingShop(UUID worldId, int x, int z, int requestedRadius) throws SQLException {
        return overlapsExistingShopSize(worldId, x, z, Math.max(1, requestedRadius * 2 + 1));
    }

    public boolean overlapsExistingShopSize(UUID worldId, int x, int z, int requestedAreaSize) throws SQLException {
        String sql = """
                SELECT 1 FROM player_shops
                WHERE world_uuid = ?
                  AND status <> 'REMOVED'
                  AND (center_x - ((area_size - 1) / 2)) <= (? + ((? - 1) / 2) + ?)
                  AND (center_x - ((area_size - 1) / 2) + area_size - 1) >= (? - ((? - 1) / 2))
                  AND (center_z - ((area_size - 1) / 2)) <= (? + ((? - 1) / 2) + ?)
                  AND (center_z - ((area_size - 1) / 2) + area_size - 1) >= (? - ((? - 1) / 2))
                LIMIT 1
                """;
        try (PreparedStatement statement = plugin.getEconomyStorage().connection().prepareStatement(sql)) {
            int requestedSize = Math.max(1, requestedAreaSize);
            statement.setString(1, worldId.toString());
            statement.setInt(2, x);
            statement.setInt(3, requestedSize);
            statement.setInt(4, 0);
            statement.setInt(5, x);
            statement.setInt(6, requestedSize);
            statement.setInt(7, z);
            statement.setInt(8, requestedSize);
            statement.setInt(9, 0);
            statement.setInt(10, z);
            statement.setInt(11, requestedSize);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    public boolean overlapsExistingShop(UUID worldId, int x, int z) throws SQLException {
        return overlapsExistingShopSize(worldId, x, z, areaRadius * 2 + 1);
    }

    /**
     * Reserva o ponto e grava o prazo exato de dois minutos. O chamador é
     * responsável por validar terreno, espaço e autenticidade do alvará antes.
     */
    public long createPreparingShop(UUID owner, UUID worldId, String worldName,
                                    int x, int y, int z) throws SQLException {
        return createPreparingShop(owner, worldId, worldName, x, y, z, areaRadius, areaRadius * 2 + 1);
    }

    public long createPreparingShop(UUID owner, UUID worldId, String worldName,
                                    int x, int y, int z, int requestedRadius) throws SQLException {
        return createPreparingShop(owner, worldId, worldName, x, y, z, requestedRadius, requestedRadius * 2 + 1);
    }

    public long createPreparingShop(UUID owner, UUID worldId, String worldName,
                                    int x, int y, int z, int requestedRadius, int requestedSize) throws SQLException {
        int safeRadius = Math.max(1, requestedRadius);
        int safeSize = Math.max(1, requestedSize);
        if (overlapsExistingShopSize(worldId, x, z, safeSize)) {
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
                     area_radius, area_size, status, created_at, activation_at, merchant_name)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PREPARING', ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, owner.toString());
                statement.setString(2, worldId.toString());
                statement.setString(3, worldName);
                statement.setInt(4, x);
                statement.setInt(5, y);
                statement.setInt(6, z);
                statement.setInt(7, safeRadius);
                statement.setInt(8, safeSize);
                statement.setLong(9, now);
                statement.setLong(10, activationAt);
                statement.setString(11, merchantName);
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
                VALUES(?,?,0,0,0,0)
                ON CONFLICT(shop_id, player_uuid) DO NOTHING
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


    public record Offer(long id, long shopId, ItemStack item, int amount, int price, boolean enabled) {}

    public boolean canManageProducts(long shopId, UUID player) throws SQLException {
        if (isOwner(shopId, player)) return true;
        try (PreparedStatement ps = plugin.getEconomyStorage().connection().prepareStatement(
                "SELECT can_manage_products FROM player_shop_allies WHERE shop_id=? AND player_uuid=?")) {
            ps.setLong(1, shopId); ps.setString(2, player.toString());
            try (ResultSet rs = ps.executeQuery()) { return rs.next() && rs.getInt(1) == 1; }
        }
    }

    public boolean canWithdraw(long shopId, UUID player) throws SQLException {
        if (isOwner(shopId, player)) return true;
        try (PreparedStatement ps = plugin.getEconomyStorage().connection().prepareStatement(
                "SELECT can_withdraw FROM player_shop_allies WHERE shop_id=? AND player_uuid=?")) {
            ps.setLong(1, shopId); ps.setString(2, player.toString());
            try (ResultSet rs = ps.executeQuery()) { return rs.next() && rs.getInt(1) == 1; }
        }
    }

    public long addOffer(long shopId, UUID actor, ItemStack item, int amount, int price) throws SQLException {
        if (!canManageProducts(shopId, actor)) throw new IllegalArgumentException("Você não tem permissão para gerenciar produtos.");
        if (!isAuthorizedForStock(shopId, actor)) throw new IllegalArgumentException("Você também precisa da permissão de estoque para cadastrar itens.");
        if (item == null || item.getType().isAir() || amount < 1 || price < 1)
            throw new IllegalArgumentException("Item, quantidade e preço precisam ser válidos.");
        ItemStack template = item.clone();
        template.setAmount(1);
        String data = Base64.getEncoder().encodeToString(template.serializeAsBytes());
        String fingerprint = Integer.toHexString(java.util.Arrays.hashCode(template.serializeAsBytes()));
        Inventory stock = stockInventory(shopId);
        ItemStack[] current = stock.getContents();
        Inventory simulated = Bukkit.createInventory(null, stock.getSize());
        ItemStack[] copy = new ItemStack[current.length];
        for (int i = 0; i < current.length; i++) copy[i] = current[i] == null ? null : current[i].clone();
        simulated.setContents(copy);
        ItemStack stockItems = item.clone();
        stockItems.setAmount(amount);
        if (!simulated.addItem(stockItems).isEmpty())
            throw new IllegalArgumentException("O baú ESTOQUE está cheio. Libere espaço antes de cadastrar o produto.");
        long offerId;
        try (PreparedStatement ps = plugin.getEconomyStorage().connection().prepareStatement(
                "INSERT INTO player_shop_offers(shop_id,item_fingerprint,item_data,amount,price_coins,enabled) VALUES(?,?,?,?,?,1)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, shopId); ps.setString(2, fingerprint); ps.setString(3, data);
            ps.setInt(4, amount); ps.setInt(5, price); ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (!rs.next()) throw new SQLException("Não foi possível criar o produto.");
                offerId = rs.getLong(1);
            }
        }
        stock.setContents(simulated.getContents());
        return offerId;
    }

    public List<Offer> listOffers(long shopId, boolean enabledOnly) throws SQLException {
        List<Offer> result = new ArrayList<>();
        String sql = "SELECT id,shop_id,item_data,amount,price_coins,enabled FROM player_shop_offers WHERE shop_id=?"
                + (enabledOnly ? " AND enabled=1" : "") + " ORDER BY id";
        try (PreparedStatement ps = plugin.getEconomyStorage().connection().prepareStatement(sql)) {
            ps.setLong(1, shopId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    byte[] bytes = Base64.getDecoder().decode(rs.getString("item_data"));
                    ItemStack item = ItemStack.deserializeBytes(bytes);
                    result.add(new Offer(rs.getLong("id"), rs.getLong("shop_id"), item,
                            rs.getInt("amount"), rs.getInt("price_coins"), rs.getInt("enabled") == 1));
                }
            }
        }
        return result;
    }

    private Inventory stockInventory(long shopId) throws SQLException {
        String sql = "SELECT stock_world_uuid,stock_x,stock_y,stock_z,status FROM player_shops WHERE id=?";
        try (PreparedStatement ps = plugin.getEconomyStorage().connection().prepareStatement(sql)) {
            ps.setLong(1, shopId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !List.of("ACTIVE", "CLOSED").contains(rs.getString("status")))
                    throw new IllegalArgumentException("A loja precisa estar ativa para gerenciar o estoque.");
                String worldId = rs.getString("stock_world_uuid");
                if (worldId == null) throw new IllegalArgumentException("O baú ESTOQUE ainda não foi criado.");
                World world = Bukkit.getWorld(UUID.fromString(worldId));
                if (world == null) throw new IllegalArgumentException("O mundo do estoque não está carregado.");
                BlockState state = world.getBlockAt(rs.getInt("stock_x"), rs.getInt("stock_y"), rs.getInt("stock_z")).getState();
                if (!(state instanceof Container container)) throw new IllegalStateException("O baú ESTOQUE não foi encontrado.");
                return container.getInventory();
            }
        }
    }

    public void removeOffer(long shopId, long offerId, UUID actor) throws SQLException {
        if (!canManageProducts(shopId, actor)) throw new IllegalArgumentException("Você não tem permissão para gerenciar produtos.");
        try (PreparedStatement ps = plugin.getEconomyStorage().connection().prepareStatement(
                "DELETE FROM player_shop_offers WHERE id=? AND shop_id=?")) {
            ps.setLong(1, offerId); ps.setLong(2, shopId);
            if (ps.executeUpdate() == 0) throw new IllegalArgumentException("Produto não encontrado.");
        }
    }



    public boolean canConfigure(long shopId, UUID player) throws SQLException {
        if (isOwner(shopId, player)) return true;
        try (PreparedStatement ps = plugin.getEconomyStorage().connection().prepareStatement(
                "SELECT can_configure FROM player_shop_allies WHERE shop_id=? AND player_uuid=?")) {
            ps.setLong(1, shopId); ps.setString(2, player.toString());
            try (ResultSet rs = ps.executeQuery()) { return rs.next() && rs.getInt(1) == 1; }
        }
    }

    public void updateOfferPrice(long shopId, long offerId, UUID actor, int price) throws SQLException {
        if (!canManageProducts(shopId, actor)) throw new IllegalArgumentException("Você não tem permissão para gerenciar produtos.");
        if (price < 1) throw new IllegalArgumentException("O preço precisa ser maior que zero.");
        try (PreparedStatement ps = plugin.getEconomyStorage().connection().prepareStatement(
                "UPDATE player_shop_offers SET price_coins=? WHERE id=? AND shop_id=? AND amount>0")) {
            ps.setInt(1, price); ps.setLong(2, offerId); ps.setLong(3, shopId);
            if (ps.executeUpdate() != 1) throw new IllegalArgumentException("Produto ativo não encontrado.");
        }
    }

    public boolean setShopOpen(long shopId, UUID owner, boolean open) throws SQLException {
        if (!canConfigure(shopId, owner)) throw new IllegalArgumentException("Você não tem permissão para configurar esta loja.");
        try (PreparedStatement ps = plugin.getEconomyStorage().connection().prepareStatement(
                "UPDATE player_shops SET status=? WHERE id=? AND status IN ('ACTIVE','CLOSED')")) {
            ps.setString(1, open ? "ACTIVE" : "CLOSED"); ps.setLong(2, shopId);
            if (ps.executeUpdate() != 1) throw new IllegalArgumentException("A loja ainda não está ativa.");
        }
        return open;
    }

    public boolean isShopOpen(long shopId) throws SQLException {
        try (PreparedStatement ps = plugin.getEconomyStorage().connection().prepareStatement(
                "SELECT 1 FROM player_shops WHERE id=? AND status='ACTIVE'")) {
            ps.setLong(1, shopId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    public long vaultBalance(long shopId, UUID actor) throws SQLException {
        if (!canWithdraw(shopId, actor)) throw new IllegalArgumentException("Você não tem permissão para consultar o cofre.");
        try (PreparedStatement ps = plugin.getEconomyStorage().connection().prepareStatement(
                "SELECT vault_coins FROM player_shops WHERE id=? AND status IN ('ACTIVE','CLOSED')")) {
            ps.setLong(1, shopId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Loja não encontrada ou ainda não ativada.");
                return rs.getLong(1);
            }
        }
    }

    public long withdrawVault(long shopId, UUID actor) throws SQLException {
        if (!canWithdraw(shopId, actor)) throw new IllegalArgumentException("Você não tem permissão para retirar o dinheiro.");
        Connection c = plugin.getEconomyStorage().connection();
        boolean auto = c.getAutoCommit(); c.setAutoCommit(false);
        try {
            long amount;
            try (PreparedStatement ps = c.prepareStatement("SELECT vault_coins FROM player_shops WHERE id=?")) {
                ps.setLong(1, shopId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new IllegalArgumentException("Loja não encontrada.");
                    amount = rs.getLong(1);
                }
            }
            if (amount <= 0) throw new IllegalArgumentException("O cofre está vazio.");
            try (PreparedStatement ps = c.prepareStatement("UPDATE player_shops SET vault_coins=0 WHERE id=? AND vault_coins=?")) {
                ps.setLong(1, shopId); ps.setLong(2, amount);
                if (ps.executeUpdate() != 1) throw new SQLException("O cofre foi alterado; tente novamente.");
            }
            c.commit();
            return amount;
        } catch (Exception e) {
            c.rollback();
            if (e instanceof SQLException se) throw se;
            if (e instanceof IllegalArgumentException ia) throw ia;
            throw new SQLException(e);
        } finally { c.setAutoCommit(auto); }
    }

    public record PurchaseResult(int itemAmount, long price, long change) {}

    public PurchaseResult purchase(long shopId, long offerId, Player buyer, BankService bank) throws SQLException {
        synchronized (plugin.getEconomyStorage()) {
            Connection c = plugin.getEconomyStorage().connection();
            Offer offer = listOffers(shopId, true).stream().filter(o -> o.id() == offerId).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Produto indisponível."));
            if (offer.amount() <= 0) throw new IllegalArgumentException("Produto sem estoque.");
            long tender = 0;
            ItemStack[] before = buyer.getInventory().getStorageContents();
            for (int i = 0; i < before.length; i++) {
                BankService.Denomination d = bank.identify(before[i]);
                if (d == null) continue;
                tender = Math.addExact(tender, (long) d.value() * before[i].getAmount());
            }
            long price = offer.price();
            if (tender < price) throw new IllegalArgumentException("Moedas físicas insuficientes. Preço: " + price + " coins.");
            long change = tender - price;
            List<ItemStack> changeItems = new ArrayList<>();
            long remaining = change;
            for (BankService.Denomination d : bank.denominations()) {
                long count = remaining / d.value();
                while (count > 0) {
                    int stack = (int)Math.min(count, d.material().getMaxStackSize());
                    changeItems.add(bank.createCoin(d, stack)); count -= stack;
                }
                remaining %= d.value();
            }
            if (remaining != 0) throw new IllegalStateException("Não foi possível formar o troco com as denominações configuradas.");
            Inventory stock = stockInventory(shopId);
            ItemStack[] stockBefore = stock.getContents();
            Inventory stockSimulated = Bukkit.createInventory(null, stock.getSize());
            ItemStack[] stockCopy = new ItemStack[stockBefore.length];
            for (int i = 0; i < stockBefore.length; i++) stockCopy[i] = stockBefore[i] == null ? null : stockBefore[i].clone();
            stockSimulated.setContents(stockCopy);
            int stockRemaining = offer.amount();
            for (int i = 0; i < stockCopy.length && stockRemaining > 0; i++) {
                ItemStack stack = stockSimulated.getItem(i);
                if (stack == null || !stack.isSimilar(offer.item())) continue;
                int take = Math.min(stack.getAmount(), stockRemaining);
                int left = stack.getAmount() - take;
                if (left <= 0) stockSimulated.setItem(i, null);
                else { stack.setAmount(left); stockSimulated.setItem(i, stack); }
                stockRemaining -= take;
            }
            if (stockRemaining > 0) throw new IllegalArgumentException("O estoque físico do baú está incompleto. Avise o dono da loja.");
            ItemStack product = offer.item().clone();
            product.setAmount(offer.amount());
            org.bukkit.inventory.Inventory simulated = Bukkit.createInventory(null, 36);
            ItemStack[] copy = new ItemStack[before.length];
            for (int i=0;i<before.length;i++) copy[i] = before[i] == null ? null : before[i].clone();
            simulated.setContents(copy);
            for (int i=0;i<copy.length;i++) {
                BankService.Denomination d = bank.identify(simulated.getItem(i));
                if (d != null) simulated.setItem(i, null);
            }
            Map<Integer, ItemStack> overflow = simulated.addItem(product);
            if (!overflow.isEmpty()) throw new IllegalArgumentException("Libere espaço no inventário para receber o produto.");
            overflow = simulated.addItem(changeItems.toArray(new ItemStack[0]));
            if (!overflow.isEmpty()) throw new IllegalArgumentException("Libere espaço no inventário para receber o troco.");
            ItemStack[] after = simulated.getStorageContents();
            boolean auto = c.getAutoCommit(); c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE player_shop_offers SET amount=amount-?, enabled=CASE WHEN amount-?=0 THEN 0 ELSE enabled END WHERE id=? AND shop_id=? AND enabled=1 AND amount>=?")) {
                    ps.setInt(1, offer.amount()); ps.setInt(2, offer.amount()); ps.setLong(3, offerId); ps.setLong(4, shopId); ps.setInt(5, offer.amount());
                    if (ps.executeUpdate() != 1) throw new IllegalArgumentException("Estoque insuficiente.");
                }
                try (PreparedStatement ps = c.prepareStatement("UPDATE player_shops SET vault_coins=vault_coins+? WHERE id=? AND status='ACTIVE'")) {
                    ps.setLong(1, price); ps.setLong(2, shopId);
                    if (ps.executeUpdate() != 1) throw new IllegalArgumentException("Loja não está ativa.");
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO player_shop_sales(shop_id,buyer_uuid,offer_id,item_amount,physical_coin_value,created_at) VALUES(?,?,?,?,?,?)")) {
                    ps.setLong(1, shopId); ps.setString(2, buyer.getUniqueId().toString());
                    ps.setLong(3, offerId); ps.setInt(4, offer.amount()); ps.setLong(5, price);
                    ps.setLong(6, System.currentTimeMillis()); ps.executeUpdate();
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                if (e instanceof SQLException se) throw se;
                if (e instanceof IllegalArgumentException ia) throw ia;
                throw new SQLException("Falha ao concluir compra.", e);
            } finally { c.setAutoCommit(auto); }
            stock.setContents(stockSimulated.getContents());
            buyer.getInventory().setStorageContents(after);
            return new PurchaseResult(offer.amount(), price, change);
        }
    }

    public int areaRadius() {
        return areaRadius;
    }
}
