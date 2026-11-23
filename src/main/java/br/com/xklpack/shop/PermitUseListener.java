package br.com.xklpack.shop;

import br.com.xklpack.XKLPack;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;

/**
 * Consome alvarás para reservar a área comercial.
 * Integrações de proteção ainda não suportadas bloqueiam a criação por segurança.
 */
public final class PermitUseListener implements Listener {
    private static final String[] PROTECTION_PLUGINS = {
            "WorldGuard", "GriefPrevention", "Lands", "Towny", "Residence",
            "RedProtect", "PlotSquared", "GriefDefender", "SuperiorSkyblock2"
    };

    private final XKLPack plugin;
    private final PermitShopCommand permits;
    private final PlayerShopService shops;

    public PermitUseListener(XKLPack plugin, PermitShopCommand permits, PlayerShopService shops) {
        this.plugin = plugin;
        this.permits = permits;
        this.shops = shops;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        Player player = event.getPlayer();
        ItemStack hand = event.getItem();
        String permitId = permits.identifyPermit(hand);
        if (permitId == null) return;
        event.setCancelled(true);

        String path = "permits." + permitId + ".";
        if (!plugin.getPermitsConfig().getBoolean(path + "enabled", true)) {
            player.sendMessage("§cEste alvará não está mais disponível.");
            return;
        }

        // Não tenta adivinhar proprietário de regiões. Sem adaptador confiável,
        // qualquer plugin de proteção conhecido faz a operação falhar com segurança.
        for (String protection : PROTECTION_PLUGINS) {
            if (plugin.getServer().getPluginManager().getPlugin(protection) != null) {
                player.sendMessage("§cNão foi possível validar a propriedade desta área com o sistema de proteção instalado.");
                player.sendMessage("§7A criação foi bloqueada para não invadir terreno protegido. Nenhum alvará foi consumido.");
                return;
            }
        }

        Block clicked = event.getClickedBlock();
        Block target = clicked.getRelative(event.getBlockFace());
        if (!target.getType().isAir() || !target.getRelative(0, 1, 0).getType().isAir()
                || !target.getRelative(0, -1, 0).getType().isSolid()) {
            player.sendMessage("§cEscolha um local com chão sólido e espaço livre para o comerciante.");
            return;
        }

        int size = Math.max(1, plugin.getPermitsConfig().getInt(path + "area-size",
                plugin.getPermitsConfig().getInt(path + "area-radius", 4) * 2 + 1));
        int radius = Math.max(1, (size - 1) / 2);
        try {
            if (shops.overlapsExistingShop(target.getWorld().getUID(), target.getBlockX(), target.getBlockZ(), radius)) {
                player.sendMessage("§cEsta área cruza o espaço reservado de outra loja.");
                return;
            }
            shops.createPreparingShop(player.getUniqueId(), target.getWorld().getUID(),
                    target.getWorld().getName(), target.getBlockX(), target.getBlockY(),
                    target.getBlockZ(), radius);
            if (hand != null && hand.getAmount() > 1) hand.setAmount(hand.getAmount() - 1);
            else player.getInventory().setItemInMainHand(null);
            player.sendMessage("§aÁrea comercial reservada!");
            player.sendMessage("§7Tipo: §f" + plugin.getPermitsConfig().getString(path + "name", permitId));
            player.sendMessage("§7Tamanho: §f" + size + "x" + size + " blocos");
            player.sendMessage("§eO comerciante será preparado em 2 minutos, assim que o sistema de NPC estiver ativo.");
        } catch (IllegalArgumentException exception) {
            player.sendMessage("§c" + exception.getMessage());
        } catch (SQLException exception) {
            plugin.getLogger().severe("Falha ao reservar loja: " + exception.getMessage());
            player.sendMessage("§cNão foi possível reservar a loja. Seu alvará não foi consumido.");
        }
    }
}
