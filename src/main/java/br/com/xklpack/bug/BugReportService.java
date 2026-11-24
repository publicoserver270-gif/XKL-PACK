package br.com.xklpack.bug;

import br.com.xklpack.XKLPack;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public final class BugReportService {
    private final XKLPack plugin;
    private final File reportsDirectory;

    public BugReportService(XKLPack plugin) {
        this.plugin = plugin;
        this.reportsDirectory = new File(plugin.getDataFolder(), "reports");
    }

    public File save(Player player, String description) throws IOException {
        if (!reportsDirectory.exists() && !reportsDirectory.mkdirs()) {
            throw new IOException("Não foi possível criar a pasta de relatórios.");
        }
        long now = System.currentTimeMillis();
        String id = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date(now))
                + "-" + player.getUniqueId().toString().substring(0, 8);
        File file = new File(reportsDirectory, id + ".yml");
        YamlConfiguration report = new YamlConfiguration();
        report.set("player.name", player.getName());
        report.set("player.uuid", player.getUniqueId().toString());
        report.set("date", new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date(now)));
        report.set("time", new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date(now)));
        report.set("timestamp", now);
        report.set("description", description);
        report.set("world", player.getWorld().getName());
        report.set("location.x", player.getLocation().getBlockX());
        report.set("location.y", player.getLocation().getBlockY());
        report.set("location.z", player.getLocation().getBlockZ());
        report.set("server.version", plugin.getServer().getVersion());
        report.save(file);
        return file;
    }

    public List<File> reports() {
        File[] files = reportsDirectory.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) return List.of();
        List<File> result = new ArrayList<>(List.of(files));
        result.sort(Comparator.comparingLong(File::lastModified).reversed());
        return result;
    }

    public int clear() throws IOException {
        if (!reportsDirectory.exists()) return 0;
        int deleted = 0;
        for (File file : reports()) {
            if (file.delete()) deleted++;
            else throw new IOException("Não foi possível apagar " + file.getName());
        }
        return deleted;
    }

    public List<String> summaries(int limit) {
        List<String> result = new ArrayList<>();
        for (File file : reports().stream().limit(Math.max(1, limit)).toList()) {
            YamlConfiguration report = YamlConfiguration.loadConfiguration(file);
            String player = report.getString("player.name", "desconhecido");
            String date = report.getString("date", "?");
            String time = report.getString("time", "?");
            String description = report.getString("description", "");
            if (description.length() > 90) description = description.substring(0, 87) + "...";
            result.add("§e" + file.getName() + " §7• §f" + player + " §8(" + date + " " + time + ")");
            result.add("§7  " + description);
        }
        return result;
    }
}
