package br.com.xklpack.bank;

import br.com.xklpack.XKLPack;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class AtmNpcCommand implements CommandExecutor {
    private final XKLPack plugin;

    public AtmNpcCommand(XKLPack plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("xklpack.admin.atm")) {
            sender.sendMessage("§cVocê não tem permissão para administrar os caixas eletrônicos.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender, label);
            return true;
        }
        if (!args[0].equalsIgnoreCase("npc")) {
            help(sender, label);
            return true;
        }
        if (args.length < 2) {
            help(sender, label);
            return true;
        }

        String action = args[1].toLowerCase();
        if (action.equals("list")) {
            List<Integer> ids = linkedIds();
            sender.sendMessage("§6§lNPCs vinculados ao Banco XKL");
            if (ids.isEmpty()) sender.sendMessage("§7Nenhum NPC vinculado.");
            else ids.forEach(id -> sender.sendMessage("§e- NPC §f#" + id));
            return true;
        }
        if (!action.equals("link") && !action.equals("unlink")) {
            help(sender, label);
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("§cInforme o ID do NPC. Exemplo: /" + label + " npc " + action + " 12");
            return true;
        }

        final int id;
        try {
            id = Integer.parseInt(args[2]);
            if (id < 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            sender.sendMessage("§cID inválido. Informe o número do NPC do Citizens.");
            return true;
        }

        List<Integer> ids = new ArrayList<>(linkedIds());
        if (action.equals("link")) {
            if (ids.contains(id)) {
                sender.sendMessage("§eO NPC #" + id + " já está vinculado ao Banco XKL.");
                return true;
            }
            ids.add(id);
            if (save(ids)) sender.sendMessage("§aNPC #" + id + " vinculado! Clique com o botão direito nele para abrir o Banco XKL.");
        } else {
            if (!ids.remove(Integer.valueOf(id))) {
                sender.sendMessage("§eO NPC #" + id + " não estava vinculado.");
                return true;
            }
            if (save(ids)) sender.sendMessage("§aVínculo do NPC #" + id + " removido.");
        }
        return true;
    }

    private List<Integer> linkedIds() {
        return new ArrayList<>(plugin.getBankConfig().getIntegerList("citizens.linked-npcs"));
    }

    private boolean save(List<Integer> ids) {
        plugin.getBankConfig().set("citizens.linked-npcs", ids);
        try {
            plugin.getBankConfig().save(new File(plugin.getDataFolder(), "bank/settings.yml"));
            return true;
        } catch (IOException ex) {
            plugin.getLogger().severe("Não foi possível salvar os NPCs vinculados: " + ex.getMessage());
            return false;
        }
    }

    private void help(CommandSender sender, String label) {
        sender.sendMessage("§6§lBanco XKL - Administração");
        sender.sendMessage("§e/" + label + " npc link <id> §7- Vincula um NPC ao caixa.");
        sender.sendMessage("§e/" + label + " npc unlink <id> §7- Remove o vínculo.");
        sender.sendMessage("§e/" + label + " npc list §7- Lista os NPCs vinculados.");
        sender.sendMessage("§7Use /npc list no Citizens para consultar os IDs.");
    }
}
