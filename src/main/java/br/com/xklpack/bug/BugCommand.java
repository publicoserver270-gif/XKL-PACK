package br.com.xklpack.bug;

import br.com.xklpack.XKLPack;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.Arrays;

public final class BugCommand implements CommandExecutor {
    private final XKLPack plugin;

    public BugCommand(XKLPack plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Este comando só pode ser usado por jogadores.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage("§cUse: /bug <descrição do problema>");
            return true;
        }
        String description = String.join(" ", args).trim();
        if (description.length() < 8 || description.length() > 500) {
            player.sendMessage("§cA descrição precisa ter entre 8 e 500 caracteres.");
            return true;
        }
        try {
            plugin.getBugReportService().save(player, description);
            player.sendMessage("§aSeu relatório foi enviado à administração. Obrigado por ajudar!");
        } catch (IOException exception) {
            plugin.getLogger().severe("Falha ao salvar bug report de " + player.getName() + ": " + exception.getMessage());
            player.sendMessage("§cNão foi possível salvar seu relatório. Tente novamente mais tarde.");
        }
        return true;
    }
}
