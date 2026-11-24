package br.com.xklpack.bug;

import br.com.xklpack.XKLPack;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.io.IOException;
import java.util.Locale;

public final class BugAdminListener implements Listener {
    private final XKLPack plugin;

    public BugAdminListener(XKLPack plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAdminCommand(PlayerCommandPreprocessEvent event) {
        String[] args = event.getMessage().trim().split("\\s+");
        if (args.length < 2 || !args[0].equalsIgnoreCase("/xkladmin")
                || !args[1].equalsIgnoreCase("bugs")) return;
        Player player = event.getPlayer();
        if (!player.hasPermission("xklpack.admin.panel")) {
            player.sendMessage("§cVocê não tem permissão para consultar relatórios.");
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        if (args.length >= 3 && args[2].equalsIgnoreCase("limpar")) {
            try {
                int removed = plugin.getBugReportService().clear();
                player.sendMessage("§aForam removidos " + removed + " relatório(s).");
            } catch (IOException exception) {
                player.sendMessage("§cFalha ao limpar relatórios: " + exception.getMessage());
            }
            return;
        }
        var reports = plugin.getBugReportService().summaries(10);
        player.sendMessage("§6§lXKL ADMIN §8» §fRelatórios de bugs (" 
                + plugin.getBugReportService().reports().size() + ")");
        if (reports.isEmpty()) {
            player.sendMessage("§7Nenhum relatório registrado.");
            return;
        }
        reports.forEach(player::sendMessage);
        player.sendMessage("§7Use §e/xkladmin bugs limpar §7para apagar todos.");
    }
}
