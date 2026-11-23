package br.com.xklpack.shop;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.UUID;

public final class PlayerShopCommand implements CommandExecutor {
    private final PlayerShopService shops;

    public PlayerShopCommand(PlayerShopService shops) {
        this.shops = shops;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Este comando só pode ser usado por jogadores.");
            return true;
        }
        try {
            if (args.length == 0 || args[0].equalsIgnoreCase("lojas")) {
                var ids = shops.listOwnedShops(player.getUniqueId());
                player.sendMessage("§6Suas lojas: §f" + (ids.isEmpty() ? "nenhuma" : ids.toString()));
                player.sendMessage("§7Use /lojaplayer aliado <adicionar|remover|listar> <id> [jogador]");
                return true;
            }
            if (!args[0].equalsIgnoreCase("aliado") || args.length < 3) {
                player.sendMessage("§cUso: /lojaplayer aliado <adicionar|remover|listar> <id> [jogador]");
                return true;
            }
            long shopId = Long.parseLong(args[2]);
            if (args[1].equalsIgnoreCase("listar")) {
                var allies = shops.listAllies(shopId, player.getUniqueId());
                player.sendMessage("§6Aliados da loja #" + shopId + ": §f" + allies);
                return true;
            }
            if (args.length < 4) {
                player.sendMessage("§cInforme o nome do jogador.");
                return true;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[3]);
            UUID targetId = target.getUniqueId();
            if (args[1].equalsIgnoreCase("adicionar") || args[1].equalsIgnoreCase("add")) {
                shops.addAlly(shopId, player.getUniqueId(), targetId);
                player.sendMessage("§aAliado adicionado. Ele recebeu acesso ao estoque, produtos, configuração e retirada.");
            } else if (args[1].equalsIgnoreCase("remover") || args[1].equalsIgnoreCase("remove")) {
                shops.removeAlly(shopId, player.getUniqueId(), targetId);
                player.sendMessage("§aAliado removido da loja.");
            } else {
                player.sendMessage("§cAção inválida. Use adicionar, remover ou listar.");
            }
        } catch (NumberFormatException exception) {
            player.sendMessage("§cID de loja inválido.");
        } catch (IllegalArgumentException exception) {
            player.sendMessage("§c" + exception.getMessage());
        } catch (SQLException exception) {
            player.sendMessage("§cNão foi possível executar a operação.");
        }
        return true;
    }
}
