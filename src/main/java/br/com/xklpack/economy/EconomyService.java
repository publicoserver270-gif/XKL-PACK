package br.com.xklpack.economy;

import br.com.xklpack.XKLPack;
import org.bukkit.configuration.ConfigurationSection;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.util.*;

public final class EconomyService {
    private final XKLPack plugin;
    private final EconomyStorage storage;

    public EconomyService(XKLPack plugin, EconomyStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public Set<String> getCurrencies() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("economy.currencies");
        return section == null ? Set.of() : section.getKeys(false);
    }

    public String getCurrencyName(String currency) {
        String id = normalizeCurrency(currency);
        return plugin.getConfig().getString("economy.currencies." + id + ".display-name", id);
    }

    public int getCurrencyDecimals(String currency) {
        String id = normalizeCurrency(currency);
        int decimals = plugin.getConfig().getInt("economy.currencies." + id + ".decimals", 2);
        if (decimals < 0 || decimals > 8) {
            throw new IllegalStateException("A moeda " + id + " precisa ter entre 0 e 8 casas decimais.");
        }
        return decimals;
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || !currency.matches("[a-zA-Z0-9_-]{1,32}")) {
            throw new IllegalArgumentException("Identificador de moeda inválido.");
        }
        return currency.toLowerCase(Locale.ROOT);
    }

    private BigDecimal validateAmount(String currency, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("O valor precisa ser maior que zero.");
        }
        try {
            return amount.setScale(getCurrencyDecimals(currency), RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Valor inválido para essa moeda.");
        }
    }

    public boolean isCurrencyEnabled(String currency) {
        try {
            return getCurrencies().contains(normalizeCurrency(currency));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public BigDecimal parseAmount(String currency, String input) {
        if (!isCurrencyEnabled(currency)) throw new IllegalArgumentException("Moeda não encontrada.");
        try {
            BigDecimal amount = new BigDecimal(input).setScale(getCurrencyDecimals(currency), RoundingMode.UNNECESSARY);
            if (amount.signum() <= 0) throw new IllegalArgumentException("O valor precisa ser maior que zero.");
            return amount;
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException("Valor inválido para essa moeda.");
        }
    }

    public BigDecimal getBalance(UUID playerId, String currency) throws SQLException {
        currency = requireCurrency(currency);
        synchronized (storage) {
            try (PreparedStatement statement = storage.connection().prepareStatement(
                    "SELECT amount FROM balances WHERE player_uuid = ? AND currency = ?")) {
                statement.setString(1, playerId.toString());
                statement.setString(2, currency);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? new BigDecimal(result.getString("amount")) : BigDecimal.ZERO.setScale(getCurrencyDecimals(currency));
                }
            }
        }
    }

    public void transfer(UUID senderId, String senderName, UUID receiverId, String receiverName,
                         String currency, BigDecimal amount) throws SQLException {
        currency = requireCurrency(currency);
        amount = validateAmount(currency, amount);
        if (!plugin.getConfig().getBoolean("economy.currencies." + currency + ".transfers-enabled", true)) {
            throw new IllegalArgumentException("Transferências dessa moeda estão desativadas.");
        }
        if (senderId.equals(receiverId)) throw new IllegalArgumentException("Você não pode transferir para si mesmo.");
        synchronized (storage) {
            Connection connection = storage.connection();
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                touchPlayer(connection, senderId, senderName);
                touchPlayer(connection, receiverId, receiverName);
                BigDecimal senderBalance = readBalance(connection, senderId, currency);
                if (senderBalance.compareTo(amount) < 0) {
                    throw new IllegalArgumentException("Saldo insuficiente. Seu saldo: " + format(currency, senderBalance));
                }
                writeBalance(connection, senderId, currency, senderBalance.subtract(amount));
                BigDecimal receiverBalance = readBalance(connection, receiverId, currency);
                writeBalance(connection, receiverId, currency, receiverBalance.add(amount));
                recordTransaction(connection, senderId, receiverId, currency, amount, "TRANSFER");
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                if (exception instanceof SQLException sqlException) throw sqlException;
                if (exception instanceof IllegalArgumentException illegalArgumentException) throw illegalArgumentException;
                throw new SQLException("Falha ao realizar transferência.", exception);
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    /** Único caminho de emissão de coins: pagamento de salário pelo módulo de profissões. */
    public void paySalary(UUID playerId, String playerName, BigDecimal amount) throws SQLException {
        creditFromAuthorizedSource(playerId, playerName, "coins", amount, "JOB_SALARY");
    }

    /** Único caminho de emissão de gemas: recompensa por atividade profissional elegível. */
    public void rewardProfessionActivity(UUID playerId, String playerName, BigDecimal amount) throws SQLException {
        creditFromAuthorizedSource(playerId, playerName, "gems", amount, "JOB_ACTIVITY");
    }

    /** Gemas só podem ser debitadas pelo fluxo de compra de ferramentas. */
    public boolean spendGemsForTool(UUID playerId, String playerName, BigDecimal amount) throws SQLException {
        return debitForPurpose(playerId, playerName, "gems", amount, "TOOL_PURCHASE");
    }

    private void creditFromAuthorizedSource(UUID playerId, String playerName, String currency,
                                            BigDecimal amount, String type) throws SQLException {
        currency = requireCurrency(currency);
        amount = validateAmount(currency, amount);
        synchronized (storage) {
            Connection connection = storage.connection();
            boolean previous = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                touchPlayer(connection, playerId, playerName);
                BigDecimal balance = readBalance(connection, playerId, currency);
                writeBalance(connection, playerId, currency, balance.add(amount));
                recordTransaction(connection, null, playerId, currency, amount, type);
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                if (exception instanceof SQLException sqlException) throw sqlException;
                throw new SQLException("Falha ao registrar recompensa.", exception);
            } finally {
                connection.setAutoCommit(previous);
            }
        }
    }

    private boolean debitForPurpose(UUID playerId, String playerName, String currency,
                                    BigDecimal amount, String type) throws SQLException {
        currency = requireCurrency(currency);
        amount = validateAmount(currency, amount);
        synchronized (storage) {
            Connection connection = storage.connection();
            boolean previous = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                touchPlayer(connection, playerId, playerName);
                BigDecimal balance = readBalance(connection, playerId, currency);
                if (balance.compareTo(amount) < 0) {
                    connection.rollback();
                    return false;
                }
                writeBalance(connection, playerId, currency, balance.subtract(amount));
                recordTransaction(connection, playerId, null, currency, amount, type);
                connection.commit();
                return true;
            } catch (Exception exception) {
                connection.rollback();
                if (exception instanceof SQLException sqlException) throw sqlException;
                throw new SQLException("Falha ao debitar saldo.", exception);
            } finally {
                connection.setAutoCommit(previous);
            }
        }
    }

    public List<RankingEntry> getTop(String currency, int limit, Set<UUID> onlineOnly) throws SQLException {
        currency = requireCurrency(currency);
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("O limite do ranking deve estar entre 1 e 100.");
        List<RankingEntry> entries = new ArrayList<>();
        synchronized (storage) {
            try (PreparedStatement statement = storage.connection().prepareStatement("""
                    SELECT p.uuid, p.last_name, b.amount
                    FROM balances b JOIN player_profiles p ON p.uuid = b.player_uuid
                    WHERE b.currency = ?
                    """)) {
                statement.setString(1, currency);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        UUID uuid = UUID.fromString(result.getString("uuid"));
                        if (onlineOnly != null && !onlineOnly.contains(uuid)) continue;
                        entries.add(new RankingEntry(uuid, result.getString("last_name"),
                                new BigDecimal(result.getString("amount"))));
                    }
                }
            }
        }
        entries.sort(Comparator.comparing(RankingEntry::balance).reversed());
        return entries.stream().limit(limit).toList();
    }

    public String format(String currency, BigDecimal amount) {
        return amount.setScale(getCurrencyDecimals(currency), RoundingMode.DOWN).toPlainString()
                + " " + getCurrencyName(currency);
    }

    private String requireCurrency(String currency) {
        String id = normalizeCurrency(currency);
        if (!isCurrencyEnabled(id)) throw new IllegalArgumentException("Moeda não encontrada: " + id);
        return id;
    }

    private BigDecimal readBalance(Connection connection, UUID playerId, String currency) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT amount FROM balances WHERE player_uuid = ? AND currency = ?")) {
            statement.setString(1, playerId.toString());
            statement.setString(2, currency);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? new BigDecimal(result.getString("amount"))
                        : BigDecimal.ZERO.setScale(getCurrencyDecimals(currency));
            }
        }
    }

    private void touchPlayer(Connection connection, UUID uuid, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO player_profiles(uuid, last_name, last_seen) VALUES(?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET last_name = excluded.last_name, last_seen = excluded.last_seen
                """)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, name);
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    private void writeBalance(Connection connection, UUID uuid, String currency, BigDecimal amount) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO balances(player_uuid, currency, amount) VALUES(?, ?, ?)
                ON CONFLICT(player_uuid, currency) DO UPDATE SET amount = excluded.amount
                """)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, currency);
            statement.setString(3, amount.toPlainString());
            statement.executeUpdate();
        }
    }

    private void recordTransaction(Connection connection, UUID sender, UUID receiver,
                                   String currency, BigDecimal amount, String type) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO economy_transactions(sender_uuid, receiver_uuid, currency, amount, transaction_type, created_at)
                VALUES(?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, sender == null ? null : sender.toString());
            statement.setString(2, receiver == null ? null : receiver.toString());
            statement.setString(3, currency);
            statement.setString(4, amount.toPlainString());
            statement.setString(5, type);
            statement.setLong(6, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    public record RankingEntry(UUID uuid, String playerName, BigDecimal balance) {}
}
