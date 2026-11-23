package br.com.xklpack.pvp;

import br.com.xklpack.XKLPack;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.List;

public final class PvPListener implements Listener {
    private static final long NIGHT_START = 12_542L;
    private static final long NIGHT_END = 23_460L;
    private final XKLPack plugin;

    public PvPListener(XKLPack plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = attacker(event.getDamager());
        if (attacker == null || attacker.equals(victim)) return;
        if (!plugin.getConfig().getBoolean("pvp.enabled", true)) return;

        List<String> worlds = plugin.getConfig().getStringList("pvp.worlds");
        if (!worlds.isEmpty() && !worlds.contains(victim.getWorld().getName())) return;
        if (!isNight(victim.getWorld())) {
            event.setCancelled(true);
            if (plugin.getConfig().getBoolean("pvp.messages", true)) {
                attacker.sendMessage("§cO PvP está desativado durante o dia.");
            }
        }
    }

    public static boolean isNight(World world) {
        long time = Math.floorMod(world.getTime(), 24_000L);
        return time >= NIGHT_START && time < NIGHT_END;
    }

    private Player attacker(Entity entity) {
        if (entity instanceof Player player) return player;
        if (entity instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) return player;
        }
        return null;
    }
}
