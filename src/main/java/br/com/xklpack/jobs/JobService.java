package br.com.xklpack.jobs;

import br.com.xklpack.XKLPack;
import br.com.xklpack.economy.EconomyService;

import java.math.BigDecimal;
import java.sql.*;
import java.util.Locale;
import java.util.UUID;

public final class JobService {
    private final XKLPack plugin;
    private final EconomyService economy;

    public JobService(XKLPack plugin, EconomyService economy) {
        this.plugin = plugin;
        this.economy = economy;
    }

    public void initialize(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS job_progress (
                    player_uuid TEXT NOT NULL,
                    job_id TEXT NOT NULL,
                    level INTEGER NOT NULL DEFAULT 1,
                    xp INTEGER NOT NULL DEFAULT 0,
                    total_xp INTEGER NOT NULL DEFAULT 0,
                    gem_xp INTEGER NOT NULL DEFAULT 0,
                    active INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(player_uuid, job_id)
                )
                """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_job_active ON job_progress(player_uuid, active)");
        }
    }

    public void selectJob(UUID uuid, String jobId) throws SQLException {
        if (!isValidJob(jobId)) throw new IllegalArgumentException("Profissão inválida.");
        synchronized (plugin.getEconomyStorage()) {
            Connection connection = plugin.getEconomyStorage().connection();
            try (PreparedStatement off = connection.prepareStatement(
                    "UPDATE job_progress SET active = 0 WHERE player_uuid = ?")) {
                off.setString(1, uuid.toString());
                off.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO job_progress(player_uuid, job_id, active) VALUES(?, ?, 1)
                    ON CONFLICT(player_uuid, job_id) DO UPDATE SET active = 1
                    """)) {
                insert.setString(1, uuid.toString());
                insert.setString(2, jobId);
                insert.executeUpdate();
            }
        }
    }

    public void leaveJob(UUID uuid) throws SQLException {
        synchronized (plugin.getEconomyStorage()) {
            try (PreparedStatement statement = plugin.getEconomyStorage().connection().prepareStatement(
                    "UPDATE job_progress SET active = 0 WHERE player_uuid = ?")) {
                statement.setString(1, uuid.toString());
                statement.executeUpdate();
            }
        }
    }

    public String getActiveJob(UUID uuid) throws SQLException {
        synchronized (plugin.getEconomyStorage()) {
            try (PreparedStatement statement = plugin.getEconomyStorage().connection().prepareStatement(
                    "SELECT job_id FROM job_progress WHERE player_uuid = ? AND active = 1 LIMIT 1")) {
                statement.setString(1, uuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? result.getString("job_id") : null;
                }
            }
        }
    }

    public JobProgress getProgress(UUID uuid, String jobId) throws SQLException {
        synchronized (plugin.getEconomyStorage()) {
            try (PreparedStatement statement = plugin.getEconomyStorage().connection().prepareStatement(
                    "SELECT level, xp, total_xp, gem_xp, active FROM job_progress WHERE player_uuid = ? AND job_id = ?")) {
                statement.setString(1, uuid.toString());
                statement.setString(2, jobId);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) return new JobProgress(1, 0, 0, 0, false);
                    return new JobProgress(result.getInt("level"), result.getInt("xp"),
                            result.getInt("total_xp"), result.getInt("gem_xp"), result.getInt("active") == 1);
                }
            }
        }
    }

    public ProgressResult addProgress(UUID uuid, String playerName, String jobId, int amount) throws SQLException {
        if (amount <= 0 || !isValidJob(jobId)) return new ProgressResult(1, 1, 0, 0, 0);
        int oldLevel;
        int newLevel;
        int newXp;
        int totalXp;
        int gems;
        synchronized (plugin.getEconomyStorage()) {
            Connection connection = plugin.getEconomyStorage().connection();
            JobProgress current = getProgress(uuid, jobId);
            oldLevel = current.level();
            newLevel = oldLevel;
            newXp = current.xp() + amount;
            totalXp = current.totalXp() + amount;
            int maxLevel = plugin.getConfig().getInt("jobs.max-level", 30);
            while (newLevel < maxLevel && newXp >= xpRequired(newLevel)) {
                newXp -= xpRequired(newLevel);
                newLevel++;
            }
            int gemEvery = Math.max(1, plugin.getConfig().getInt("jobs.gems.every-xp", 100));
            int gemProgress = current.gemXp() + amount;
            gems = gemProgress / gemEvery;
            gemProgress %= gemEvery;
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO job_progress(player_uuid, job_id, level, xp, total_xp, gem_xp, active)
                    VALUES(?, ?, ?, ?, ?, ?, 0)
                    ON CONFLICT(player_uuid, job_id) DO UPDATE SET
                      level = excluded.level, xp = excluded.xp, total_xp = excluded.total_xp, gem_xp = excluded.gem_xp
                    """)) {
                statement.setString(1, uuid.toString());
                statement.setString(2, jobId);
                statement.setInt(3, newLevel);
                statement.setInt(4, newXp);
                statement.setInt(5, totalXp);
                statement.setInt(6, gemProgress);
                statement.executeUpdate();
            }
        }
        for (int i = 0; i < gems; i++) {
            economy.rewardProfessionActivity(uuid, playerName,
                    BigDecimal.valueOf(plugin.getConfig().getInt("jobs.gems.amount", 1)));
        }
        return new ProgressResult(oldLevel, newLevel, newXp, totalXp, gems);
    }

    public int xpRequired(int currentLevel) {
        return Math.max(1, plugin.getConfig().getInt("jobs.leveling.base-xp", 100)
                + Math.max(0, currentLevel - 1) * plugin.getConfig().getInt("jobs.leveling.xp-per-level", 25));
    }

    public boolean isValidJob(String jobId) {
        return jobId != null && plugin.getConfig().isConfigurationSection("jobs.professions." + jobId.toLowerCase(Locale.ROOT));
    }

    public String getJobName(String jobId) {
        return plugin.getConfig().getString("jobs.professions." + jobId + ".name", jobId);
    }

    public record JobProgress(int level, int xp, int totalXp, int gemXp, boolean active) {}
    public record ProgressResult(int oldLevel, int newLevel, int xp, int totalXp, int gemsAwarded) {}
}
