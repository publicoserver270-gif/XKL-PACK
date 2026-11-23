package br.com.xklpack.shop;

import br.com.xklpack.XKLPack;
import org.bukkit.Material;
import org.bukkit.Location;
import java.lang.reflect.Method;
import java.util.UUID;
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
            "WorldGuard", "GriefPrevention", "Lands", "Towny", "Residence", "EasyClaim", "DyClaim",
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


    private boolean isWorldGuardAreaOwnedBy(UUID playerId, Location origin, int size) {
        try {
            Class<?> worldGuardClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            Object worldGuard = worldGuardClass.getMethod("getInstance").invoke(null);
            Object platform = worldGuardClass.getMethod("getPlatform").invoke(worldGuard);
            Object container = platform.getClass().getMethod("getRegionContainer").invoke(platform);
            Object query = container.getClass().getMethod("createQuery").invoke(container);
            Class<?> adapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Method adapt = adapterClass.getMethod("adapt", Location.class);
            Method applicable = null;
            for (Method method : query.getClass().getMethods()) {
                if (method.getName().equals("getApplicableRegions") && method.getParameterCount() == 1) {
                    applicable = method; break;
                }
            }
            if (applicable == null) return false;
            for (int x = origin.getBlockX(); x < origin.getBlockX() + size; x++) {
                for (int z = origin.getBlockZ(); z < origin.getBlockZ() + size; z++) {
                    Location sample = new Location(origin.getWorld(), x + 0.5, origin.getY(), z + 0.5);
                    Object worldEditLocation = adapt.invoke(null, sample);
                    Object applicableSet = applicable.invoke(query, worldEditLocation);
                    Object regions = applicableSet.getClass().getMethod("getRegions").invoke(applicableSet);
                    if (!(regions instanceof Iterable<?> iterable)) return false;
                    for (Object region : iterable) {
                        Object regionId = region.getClass().getMethod("getId").invoke(region);
                        if ("__global__".equals(String.valueOf(regionId))) continue;
                        Object owners = region.getClass().getMethod("getOwners").invoke(region);
                        Method contains = owners.getClass().getMethod("contains", UUID.class);
                        if (!Boolean.TRUE.equals(contains.invoke(owners, playerId))) return false;
                    }
                }
            }
            return true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().warning("WorldGuard detectado, mas não foi possível confirmar a propriedade da área: " + exception.getMessage());
            return false;
        }
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

        // WorldGuard é consultado por reflexão para não obrigar o servidor a instalá-lo.
        // Qualquer outra proteção sem adaptador conhecido continua bloqueando a criação.
        for (String protection : PROTECTION_PLUGINS) {
            if (protection.equals("WorldGuard")) continue;
            if (plugin.getServer().getPluginManager().getPlugin(protection) != null) {
                player.sendMessage("§cA proteção " + protection + " ainda não possui integração segura no XKL PACK.");
                player.sendMessage("§7Criação bloqueada; seu alvará não foi consumido.");
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
            if (plugin.getServer().getPluginManager().getPlugin("WorldGuard") != null
                    && !isWorldGuardAreaOwnedBy(player.getUniqueId(), target.getLocation(), size)) {
                player.sendMessage("§cA área precisa estar totalmente sem proteção ou pertencer a você no WorldGuard.");
                player.sendMessage("§7Nenhum alvará foi consumido.");
                return;
            }
            int centerX = target.getBlockX() + (size - 1) / 2;
            int centerZ = target.getBlockZ() + (size - 1) / 2;
            if (shops.overlapsExistingShopSize(target.getWorld().getUID(), centerX, centerZ, size)) {
                player.sendMessage("§cEsta área cruza o espaço reservado de outra loja.");
                return;
            }
            long shopId = shops.createPreparingShop(player.getUniqueId(), target.getWorld().getUID(),
                    target.getWorld().getName(), centerX, target.getBlockY(), centerZ, radius, size);
            if (hand != null && hand.getAmount() > 1) hand.setAmount(hand.getAmount() - 1);
            else player.getInventory().setItemInMainHand(null);
            player.sendMessage("§aÁrea comercial reservada! ID da loja: §f#" + shopId);
            player.sendMessage("§7Gerencie aliados com /lojaplayer aliado adicionar " + shopId + " <jogador>.");
            player.sendMessage("§7Tipo: §f" + plugin.getPermitsConfig().getString(path + "name", permitId));
            player.sendMessage("§7Tamanho: §f" + size + "x" + size + " blocos");
            player.sendMessage("§eO comerciante e o estoque serão criados após 2 minutos.");
        } catch (IllegalArgumentException exception) {
            player.sendMessage("§c" + exception.getMessage());
        } catch (SQLException exception) {
            plugin.getLogger().severe("Falha ao reservar loja: " + exception.getMessage());
            player.sendMessage("§cNão foi possível reservar a loja. Seu alvará não foi consumido.");
        }
    }
}
