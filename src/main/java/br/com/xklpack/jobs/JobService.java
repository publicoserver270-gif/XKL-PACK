package br.com.xklpack.jobs;

import br.com.xklpack.XKLPack;
import br.com.xklpack.economy.EconomyService;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
            statement.execute("""
                CREATE TABLE IF NOT EXISTS job_salary_progress (
                    player_uuid TEXT NOT NULL,
                    job_id TEXT NOT NULL,
                    completed_goals INTEGER NOT NULL DEFAULT 0,
                    production_progress INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(player_uuid, job_id)
                )
                """);
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

    public SalaryProgress getSalaryProgress(UUID uuid, String jobId) throws SQLException {
        synchronized (plugin.getEconomyStorage()) {
            try (PreparedStatement statement = plugin.getEconomyStorage().connection().prepareStatement(
                    "SELECT completed_goals, production_progress FROM job_salary_progress WHERE player_uuid = ? AND job_id = ?")) {
                statement.setString(1, uuid.toString());
                statement.setString(2, jobId);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next()
                            ? new SalaryProgress(result.getInt("completed_goals"), result.getInt("production_progress"))
                            : new SalaryProgress(0, 0);
                }
            }
        }
    }

    /**
     * Registra XP de uma atividade profissional, calcula gemas e avança a meta salarial.
     * Cada atividade válida equivale a uma unidade de produção; a meta é independente por profissão.
     */
    public ProgressResult addProgress(UUID uuid, String playerName, String jobId, int amount) throws SQLException {
        if (amount <= 0 || !isValidJob(jobId)) return new ProgressResult(1, 1, 0, 0, 0, BigDecimal.ZERO, 0, 0, 0);
        int oldLevel;
        int newLevel;
        int newXp;
        int totalXp;
        int gems;
        int goalsCompleted;
        int goalProgress;
        int completedGoals;
        BigDecimal salaryPaid = BigDecimal.ZERO;

        synchronized (plugin.getEconomyStorage()) {
            Connection connection = plugin.getEconomyStorage().connection();
            JobProgress current = getProgress(uuid, jobId);
            if (!current.active()) return new ProgressResult(current.level(), current.level(),
                    current.xp(), current.totalXp(), 0, BigDecimal.ZERO, 0, 0, 0);

            oldLevel = current.level();
            newLevel = oldLevel;
            newXp = current.xp() + amount;
            totalXp = current.totalXp() + amount;
            int maxLevel = Math.max(1, plugin.getConfig().getInt("jobs.max-level", 140));
            while (newLevel < maxLevel && newXp >= xpRequired(newLevel)) {
                newXp -= xpRequired(newLevel);
                newLevel++;
            }
            if (newLevel >= maxLevel) newXp = Math.min(newXp, xpRequired(maxLevel));

            int gemEvery = Math.max(1, plugin.getConfig().getInt("jobs.gems.every-xp", 100));
            int gemProgress = current.gemXp() + amount;
            gems = gemProgress / gemEvery;
            gemProgress %= gemEvery;

            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO job_progress(player_uuid, job_id, level, xp, total_xp, gem_xp, active)
                    VALUES(?, ?, ?, ?, ?, ?, 1)
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

            SalaryProgress salary = getSalaryProgress(uuid, jobId);
            completedGoals = salary.completedGoals();
            int goal = Math.max(1, plugin.getConfig().getInt("jobs.salary.production-goal", 100)
                    + completedGoals * plugin.getConfig().getInt("jobs.salary.goal-increase-per-cycle", 25));
            int production = salary.productionProgress() + 1;
            goalsCompleted = 0;
            while (production >= goal) {
                production -= goal;
                completedGoals++;
                goalsCompleted++;
                goal = Math.max(1, plugin.getConfig().getInt("jobs.salary.production-goal", 100)
                        + completedGoals * plugin.getConfig().getInt("jobs.salary.goal-increase-per-cycle", 25));
            }
            goalProgress = production;

            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO job_salary_progress(player_uuid, job_id, completed_goals, production_progress)
                    VALUES(?, ?, ?, ?)
                    ON CONFLICT(player_uuid, job_id) DO UPDATE SET
                      completed_goals = excluded.completed_goals,
                      production_progress = excluded.production_progress
                    """)) {
                statement.setString(1, uuid.toString());
                statement.setString(2, jobId);
                statement.setInt(3, completedGoals);
                statement.setInt(4, production);
                statement.executeUpdate();
            }

            if (goalsCompleted > 0) {
                // O salário é sempre o valor-base da meta multiplicado pelo multiplicador.
                // A meta de produção (100, 125, 150...) não aumenta o valor-base do salário.
                BigDecimal goalValue = BigDecimal.valueOf(
                        plugin.getConfig().getDouble("jobs.salary.base-reward", 500.0));
                double multiplierStart = plugin.getConfig().getDouble("jobs.salary.multiplier-start", 1.3);
                double multiplierIncrease = plugin.getConfig().getDouble("jobs.salary.multiplier-increase-every-40-goals", 0.1);
                int multiplierEvery = Math.max(1, plugin.getConfig().getInt("jobs.salary.multiplier-every-goals", 40));
                for (int i = 0; i < goalsCompleted; i++) {
                    int goalNumber = completedGoals - goalsCompleted + i + 1;
                    double multiplier = multiplierForGoalNumber(goalNumber, multiplierStart, multiplierIncrease, multiplierEvery);
                    BigDecimal payment = goalValue.multiply(BigDecimal.valueOf(multiplier))
                            .setScale(2, RoundingMode.HALF_UP);
                    economy.paySalary(uuid, playerName, payment);
                    salaryPaid = salaryPaid.add(payment);
                }
            }
        }

        if (gems > 0) {
            economy.rewardProfessionActivity(uuid, playerName,
                    BigDecimal.valueOf(gems).multiply(BigDecimal.valueOf(plugin.getConfig().getInt("jobs.gems.amount", 1))));
        }
        return new ProgressResult(oldLevel, newLevel, newXp, totalXp, gems,
                salaryPaid, goalsCompleted, goalProgress, completedGoals);
    }

    public int currentGoal(int completedGoals) {
        return Math.max(1, plugin.getConfig().getInt("jobs.salary.production-goal", 100)
                + completedGoals * plugin.getConfig().getInt("jobs.salary.goal-increase-per-cycle", 25));
    }

    public double currentMultiplier(int completedGoals) {
        int every = Math.max(1, plugin.getConfig().getInt("jobs.salary.multiplier-every-goals", 40));
        double start = plugin.getConfig().getDouble("jobs.salary.multiplier-start", 1.3);
        double increase = plugin.getConfig().getDouble("jobs.salary.multiplier-increase-every-40-goals", 0.1);
        return multiplierForGoalNumber(Math.max(0, completedGoals) + 1, start, increase, every);
    }

    /**
     * Returns the multiplier for a goal number using completed goals only.
     * With a 40-goal interval, goals 1-40 use the starting multiplier and goal 41
     * is the first to receive the increase.
     */
    public static double multiplierForGoalNumber(int goalNumber, double start, double increase, int everyGoals) {
        int interval = Math.max(1, everyGoals);
        int completedBeforeThisGoal = Math.max(0, goalNumber - 1);
        return start + (completedBeforeThisGoal / interval) * increase;
    }

    public int xpRequired(int currentLevel) {
        int base = Math.max(1, plugin.getConfig().getInt("jobs.leveling.base-xp", 100));
        int increase = Math.max(0, plugin.getConfig().getInt("jobs.leveling.xp-per-level", 25));
        return base + Math.max(0, currentLevel - 1) * increase;
    }

    public boolean isValidJob(String jobId) {
        return jobId != null && plugin.getConfig().isConfigurationSection("jobs.professions." + jobId.toLowerCase(Locale.ROOT));
    }

    public String getJobName(String jobId) {
        return plugin.getConfig().getString("jobs.professions." + jobId + ".name", jobId);
    }

    public record JobProgress(int level, int xp, int totalXp, int gemXp, boolean active) {}
    public record SalaryProgress(int completedGoals, int productionProgress) {}
    public record ProgressResult(int oldLevel, int newLevel, int xp, int totalXp, int gemsAwarded,
                                 BigDecimal salaryPaid, int goalsCompleted, int goalProgress, int completedGoals) {}
}
