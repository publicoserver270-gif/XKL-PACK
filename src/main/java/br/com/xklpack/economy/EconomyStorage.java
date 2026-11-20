package br.com.xklpack.economy;

import br.com.xklpack.XKLPack;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class EconomyStorage implements AutoCloseable {
    private final XKLPack plugin;
    private Connection connection;

    public EconomyStorage(XKLPack plugin) {
        this.plugin = plugin;
    }

    public void open() throws SQLException, IOException {
        File databaseFile = new File(plugin.getDataFolder(),
                plugin.getConfig().getString("database.sqlite-file", "data/xklpack.db"));
        File parent = databaseFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Não foi possível criar a pasta " + parent.getAbsolutePath());
        }

        connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_profiles (
                        uuid TEXT PRIMARY KEY,
                        last_name TEXT NOT NULL,
                        last_seen INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS balances (
                        player_uuid TEXT NOT NULL,
                        currency TEXT NOT NULL,
                        amount TEXT NOT NULL DEFAULT '0',
                        PRIMARY KEY (player_uuid, currency),
                        FOREIGN KEY (player_uuid) REFERENCES player_profiles(uuid)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS economy_transactions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        sender_uuid TEXT,
                        receiver_uuid TEXT,
                        currency TEXT NOT NULL,
                        amount TEXT NOT NULL,
                        transaction_type TEXT NOT NULL,
                        created_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_balances_currency ON balances(currency)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_transactions_created ON economy_transactions(created_at)");
        }
    }

    public Connection connection() {
        if (connection == null) {
            throw new IllegalStateException("Banco de dados ainda não foi aberto.");
        }
        return connection;
    }

    @Override
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}
