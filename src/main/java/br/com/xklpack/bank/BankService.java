package br.com.xklpack.bank;

import br.com.xklpack.XKLPack;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.util.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class BankService {
    private final XKLPack plugin;
    private final NamespacedKey coinKey;
    private final NamespacedKey denominationKey;
    private final NamespacedKey signatureKey;
    private final byte[] signingSecret;

    public BankService(XKLPack plugin) {
        this.plugin = plugin;
        this.coinKey = new NamespacedKey(plugin, "physical_coin");
        this.denominationKey = new NamespacedKey(plugin, "coin_denomination");
        this.signatureKey = new NamespacedKey(plugin, "coin_signature");
        this.signingSecret = loadOrCreateSigningSecret(plugin);
    }

    private byte[] loadOrCreateSigningSecret(XKLPack plugin) {
        Path path = plugin.getDataFolder().toPath().resolve("coin-signing.key");
        try {
            Files.createDirectories(path.getParent());
            if (Files.exists(path)) {
                byte[] existing = Files.readAllBytes(path);
                if (existing.length != 32) {
                    throw new IllegalStateException("O arquivo coin-signing.key está inválido. Restaure o segredo original; não gere outro com moedas em circulação.");
                }
                return existing;
            }
            byte[] generated = new byte[32];
            new SecureRandom().nextBytes(generated);
            Files.write(path, generated);
            plugin.getLogger().warning("Segredo HMAC das moedas físicas criado. Faça backup do arquivo coin-signing.key; sem ele, as moedas existentes não serão autenticadas.");
            return generated;
        } catch (IOException exception) {
            throw new IllegalStateException("Não foi possível carregar/criar o segredo de autenticação das moedas físicas.", exception);
        }
    }

    private String legacySignature(Denomination denomination) {
        String payload = "xklpack-coin-v1|" + denomination.id() + "|"
                + denomination.material().name() + "|" + denomination.value();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingSecret, "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Falha ao autenticar moeda física.", exception);
        }
    }

    private String signature(Denomination denomination) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingSecret, "HmacSHA256"));
            // O valor fica na configuração; a assinatura autentica a identidade e o material.
            // Assim, o administrador pode reajustar a denominação sem invalidar moedas legítimas.
            String payload = "xklpack-coin-v1|" + denomination.id() + "|"
                    + denomination.material().name();
            return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Não foi possível assinar moeda física.", exception);
        }
    }

    public record Denomination(String id, Material material, int value, int order, String name) {}

    public List<Denomination> denominations() {
        var section = plugin.getConfig().getConfigurationSection("economy.currencies.coins");
        var yaml = plugin.getConfig();
        // A lista vem do arquivo dedicado, carregado pelo plugin.
        var coins = plugin.getPhysicalCoinsConfig().getConfigurationSection("physical-coins.denominations");
        List<Denomination> result = new ArrayList<>();
        if (coins == null) return result;
        for (String id : coins.getKeys(false)) {
            String path = "physical-coins.denominations." + id + ".";
            Material material = Material.matchMaterial(plugin.getPhysicalCoinsConfig().getString(path + "material", ""));
            int value = plugin.getPhysicalCoinsConfig().getInt(path + "value", 0);
            if (material == null || value <= 0) {
                plugin.getLogger().warning("Moeda física ignorada por configuração inválida: " + id);
                continue;
            }
            result.add(new Denomination(id, material, value,
                    plugin.getPhysicalCoinsConfig().getInt(path + "order", 999),
                    plugin.getPhysicalCoinsConfig().getString(path + "display-name", id)));
        }
        result.sort(Comparator.comparingInt(Denomination::order).reversed());
        return result;
    }

    public ItemStack createCoin(Denomination denomination, int amount) {
        ItemStack item = new ItemStack(denomination.material(), amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(denomination.name()));
        meta.setLore(List.of(color("&7Valor: &e" + denomination.value() + " Coins"),
                color("&8Moeda oficial do Banco XKL")));
        meta.getPersistentDataContainer().set(coinKey, PersistentDataType.STRING, "xklpack_coin_v1");
        meta.getPersistentDataContainer().set(denominationKey, PersistentDataType.STRING, denomination.id());
        meta.getPersistentDataContainer().set(signatureKey, PersistentDataType.STRING, signature(denomination));
        if (plugin.getPhysicalCoinsConfig().getBoolean("physical-coins.enchantment-glint", true)) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
        return item;
    }

    public Denomination identify(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (!"xklpack_coin_v1".equals(meta.getPersistentDataContainer().get(coinKey, PersistentDataType.STRING))) return null;
        String id = meta.getPersistentDataContainer().get(denominationKey, PersistentDataType.STRING);
        String suppliedSignature = meta.getPersistentDataContainer().get(signatureKey, PersistentDataType.STRING);
        if (id == null || suppliedSignature == null) return null;
        Denomination denomination = denominations().stream()
                .filter(d -> d.id().equals(id) && d.material() == item.getType())
                .findFirst().orElse(null);
        if (denomination == null) return null;
        byte[] supplied = suppliedSignature.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] expected = signature(denomination).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        boolean valid = MessageDigest.isEqual(expected, supplied);
        boolean legacyMigrated = false;
        if (!valid) {
            byte[] legacy = legacySignature(denomination).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            valid = MessageDigest.isEqual(legacy, supplied);
            if (valid) {
                meta.getPersistentDataContainer().set(signatureKey, PersistentDataType.STRING, signature(denomination));
                legacyMigrated = true;
            }
        }
        if (!valid) return null;

        // Atualiza o valor exibido quando a administração reajusta uma denominação.
        String expectedName = color(denomination.name());
        List<String> expectedLore = List.of(color("&7Valor: &e" + denomination.value() + " Coins"),
                color("&8Moeda oficial do Banco XKL"));
        if (legacyMigrated || !expectedName.equals(meta.getDisplayName()) || !expectedLore.equals(meta.getLore())) {
            meta.setDisplayName(expectedName);
            meta.setLore(expectedLore);
            item.setItemMeta(meta);
        }
        return denomination;
    }

    public BigDecimal balance(UUID uuid) throws SQLException {
        synchronized (plugin.getEconomyStorage()) {
            try (PreparedStatement ps = plugin.getEconomyStorage().connection().prepareStatement(
                    "SELECT amount FROM balances WHERE player_uuid=? AND currency='coins'")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? new BigDecimal(rs.getString(1)) : BigDecimal.ZERO.setScale(2);
                }
            }
        }
    }

    public BigDecimal depositAll(Player player) throws SQLException {
        synchronized (plugin.getEconomyStorage()) {
            Map<Denomination, Integer> found = new LinkedHashMap<>();
            int totalItems = 0;
            BigDecimal total = BigDecimal.ZERO.setScale(2);
            for (ItemStack item : player.getInventory().getStorageContents()) {
                Denomination d = identify(item);
                if (d == null) continue;
                found.merge(d, item.getAmount(), Integer::sum);
                totalItems += item.getAmount();
                total = total.add(BigDecimal.valueOf((long) d.value() * item.getAmount()));
            }
            if (totalItems == 0) return BigDecimal.ZERO;
            BigDecimal feePercent = feePercent("atm.fees.deposit-percent", 0.0);
            BigDecimal fee = total.multiply(feePercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.CEILING);
            BigDecimal credited = total.subtract(fee).setScale(2, RoundingMode.DOWN);
            if (credited.signum() <= 0) {
                throw new IllegalArgumentException("O valor é muito baixo para o depósito com taxa.");
            }

            // Remove apenas itens autenticados. Em falha SQL, restaura os itens removidos.
            for (int slot = 0; slot < player.getInventory().getStorageContents().length; slot++) {
                ItemStack item = player.getInventory().getItem(slot);
                if (identify(item) != null) player.getInventory().setItem(slot, null);
            }
            try {
                Connection c = plugin.getEconomyStorage().connection();
                boolean auto = c.getAutoCommit();
                c.setAutoCommit(false);
                try {
                    touch(c, player);
                    BigDecimal old = read(c, player.getUniqueId());
                    write(c, player.getUniqueId(), old.add(credited));
                    log(c, null, player.getUniqueId(), credited, "ATM_DEPOSIT");
                    if (fee.signum() > 0) log(c, null, player.getUniqueId(), fee, "ATM_FEE");
                    c.commit();
                } catch (Exception e) {
                    c.rollback();
                    throw e;
                } finally { c.setAutoCommit(auto); }
            } catch (Exception ex) {
                for (var entry : found.entrySet()) {
                    int left = entry.getValue();
                    while (left > 0) {
                        int amount = Math.min(left, entry.getKey().material().getMaxStackSize());
                        Map<Integer, ItemStack> overflow = player.getInventory().addItem(createCoin(entry.getKey(), amount));
                        overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
                        left -= amount;
                    }
                }
                if (ex instanceof SQLException sql) throw sql;
                throw new SQLException("Falha ao depositar moedas.", ex);
            }
            return credited;
        }
    }

    public BigDecimal withdraw(Player player, BigDecimal requested) throws SQLException {
        if (requested == null || requested.signum() <= 0 || requested.stripTrailingZeros().scale() > 0)
            throw new IllegalArgumentException("Digite um valor inteiro maior que zero.");
        synchronized (plugin.getEconomyStorage()) {
            BigDecimal feePercent = feePercent("atm.fees.withdraw-percent", 2.0);
            BigDecimal fee = requested.multiply(feePercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.CEILING);
            BigDecimal payout = requested.subtract(fee).setScale(0, RoundingMode.DOWN);
            BigDecimal chargedFee = requested.subtract(payout);
            if (payout.signum() <= 0) throw new IllegalArgumentException("O valor é muito baixo para o saque com taxa.");
            List<Denomination> list = denominations();
            List<ItemStack> items = new ArrayList<>();
            long remaining = payout.longValueExact();
            for (Denomination d : list) {
                int count = (int) Math.min(Integer.MAX_VALUE, remaining / d.value());
                while (count > 0) {
                    int stack = Math.min(count, d.material().getMaxStackSize());
                    items.add(createCoin(d, stack));
                    count -= stack;
                }
                remaining %= d.value();
            }
            if (remaining != 0) throw new IllegalStateException("As denominações configuradas não conseguem formar esse valor.");
            // Simula a entrega em um inventário temporário antes de debitar o saldo.
            // Isso considera compatibilidade de material, tamanho máximo de pilha e espaços reais.
            org.bukkit.inventory.Inventory simulated = org.bukkit.Bukkit.createInventory(null, 36);
            ItemStack[] originalContents = player.getInventory().getStorageContents();
            ItemStack[] copiedContents = new ItemStack[originalContents.length];
            for (int i = 0; i < originalContents.length; i++) {
                copiedContents[i] = originalContents[i] == null ? null : originalContents[i].clone();
            }
            simulated.setContents(copiedContents);
            Map<Integer, ItemStack> overflow = simulated.addItem(items.toArray(new ItemStack[0]));
            if (!overflow.isEmpty()) {
                throw new IllegalArgumentException("Libere espaço no inventário antes de sacar.");
            }
            ItemStack[] deliveryContents = simulated.getContents();
            Connection c = plugin.getEconomyStorage().connection();
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                touch(c, player);
                BigDecimal old = read(c, player.getUniqueId());
                if (old.compareTo(requested) < 0) throw new IllegalArgumentException("Saldo insuficiente. Saldo: " + old.toPlainString());
                write(c, player.getUniqueId(), old.subtract(requested));
                log(c, player.getUniqueId(), null, requested, "ATM_WITHDRAW");
                if (chargedFee.signum() > 0) log(c, player.getUniqueId(), null, chargedFee, "ATM_FEE");
                c.commit();
            } catch (Exception e) {
                c.rollback();
                if (e instanceof SQLException sql) throw sql;
                if (e instanceof IllegalArgumentException ia) throw ia;
                throw new SQLException("Falha no saque.", e);
            } finally { c.setAutoCommit(auto); }
            // A cópia simulada já foi validada; aplica a entrega de uma só vez após o commit.
            player.getInventory().setStorageContents(deliveryContents);
            return payout;
        }
    }

    public List<String> statement(UUID uuid) throws SQLException {
        List<String> rows = new ArrayList<>();
        synchronized (plugin.getEconomyStorage()) {
            long retentionDays = Math.max(1L, plugin.getBankConfig().getLong("atm.statement.retention-days", 7L));
            long cutoff = System.currentTimeMillis() - retentionDays * 24L * 60L * 60L * 1000L;
            try (PreparedStatement cleanup = plugin.getEconomyStorage().connection().prepareStatement(
                    "DELETE FROM economy_transactions WHERE transaction_type LIKE 'ATM_%' AND created_at < ?")) {
                cleanup.setLong(1, cutoff);
                cleanup.executeUpdate();
            }
            try (PreparedStatement ps = plugin.getEconomyStorage().connection().prepareStatement(
                    "SELECT transaction_type, amount, created_at FROM economy_transactions WHERE (sender_uuid=? OR receiver_uuid=?) AND transaction_type LIKE 'ATM_%' AND created_at >= ? ORDER BY id DESC")) {
                ps.setString(1, uuid.toString()); ps.setString(2, uuid.toString());
                ps.setLong(3, cutoff);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) rows.add("&7" + new java.text.SimpleDateFormat("dd/MM HH:mm").format(new java.util.Date(rs.getLong("created_at")))
                            + " &f" + rs.getString("transaction_type") + " &e" + rs.getString("amount"));
                }
            }
        }
        return rows;
    }

    private BigDecimal feePercent(String path, double defaultValue) {
        BigDecimal percent = BigDecimal.valueOf(plugin.getBankConfig().getDouble(path, defaultValue));
        if (percent.compareTo(BigDecimal.ZERO) < 0 || percent.compareTo(BigDecimal.valueOf(100)) >= 0) {
            throw new IllegalStateException("A taxa " + path + " precisa ser maior ou igual a 0% e menor que 100%.");
        }
        return percent;
    }

    private BigDecimal read(Connection c, UUID uuid) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT amount FROM balances WHERE player_uuid=? AND currency='coins'")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? new BigDecimal(rs.getString(1)) : BigDecimal.ZERO.setScale(2); }
        }
    }
    private void write(Connection c, UUID uuid, BigDecimal amount) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO balances(player_uuid,currency,amount) VALUES(?,'coins',?) ON CONFLICT(player_uuid,currency) DO UPDATE SET amount=excluded.amount")) {
            ps.setString(1, uuid.toString()); ps.setString(2, amount.toPlainString()); ps.executeUpdate();
        }
    }
    private void touch(Connection c, Player p) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO player_profiles(uuid,last_name,last_seen) VALUES(?,?,?) ON CONFLICT(uuid) DO UPDATE SET last_name=excluded.last_name,last_seen=excluded.last_seen")) {
            ps.setString(1,p.getUniqueId().toString()); ps.setString(2,p.getName()); ps.setLong(3,System.currentTimeMillis()); ps.executeUpdate();
        }
    }
    private void log(Connection c, UUID sender, UUID receiver, BigDecimal amount, String type) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO economy_transactions(sender_uuid,receiver_uuid,currency,amount,transaction_type,created_at) VALUES(?,?, 'coins',?,?,?)")) {
            ps.setString(1,sender == null ? null : sender.toString()); ps.setString(2,receiver == null ? null : receiver.toString());
            ps.setString(3,amount.toPlainString()); ps.setString(4,type); ps.setLong(5,System.currentTimeMillis()); ps.executeUpdate();
        }
    }
    private String color(String text) { return org.bukkit.ChatColor.translateAlternateColorCodes('&', text == null ? "" : text); }
}
