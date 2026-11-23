package br.com.xklpack.jobs;

import br.com.xklpack.XKLPack;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

/**
 * Machado especial do Lenhador: ao atingir o nível 200, derruba o tronco
 * conectado da árvore. Cada bloco extra passa novamente pelo evento de quebra,
 * permitindo que plugins de proteção impeçam a remoção de blocos não autorizados.
 */
public final class TreeFellerListener implements Listener {
    private static final int MAX_LOGS = 128;
    private static final int MAX_RADIUS = 8;

    private final XKLPack plugin;
    private final JobService jobs;
    private final KitService kits;
    private final Set<UUID> processing = new HashSet<>();

    public TreeFellerListener(XKLPack plugin, JobService jobs, KitService kits) {
        this.plugin = plugin;
        this.jobs = jobs;
        this.kits = kits;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTreeBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!kits.isOwnedProfessionKit(tool, player, "lenhador")) return;
        if (processing.contains(player.getUniqueId())) return;

        try {
            if (!"lenhador".equals(jobs.getActiveJob(player.getUniqueId()))) return;
            if (jobs.getProgress(player.getUniqueId(), "lenhador").level() < 200) return;
        } catch (SQLException exception) {
            plugin.getLogger().warning("Não foi possível verificar o nível do Lenhador: " + exception.getMessage());
            return;
        }

        if (!isLog(event.getBlock().getType())) return;
        Set<Block> tree = findConnectedLogs(event.getBlock());
        if (tree.size() <= 1) return;

        UUID playerId = player.getUniqueId();
        processing.add(playerId);
        try {
            for (Block extra : tree) {
                if (extra.equals(event.getBlock()) || extra.getType().isAir()) continue;
                BlockBreakEvent extraBreak = new BlockBreakEvent(extra, player);
                plugin.getServer().getPluginManager().callEvent(extraBreak);
                if (extraBreak.isCancelled()) continue;

                boolean broken = extra.breakNaturally(tool);
                if (broken && extraBreak.getExpToDrop() > 0) {
                    extra.getWorld().spawn(extra.getLocation(), org.bukkit.entity.ExperienceOrb.class,
                            orb -> orb.setExperience(extraBreak.getExpToDrop()));
                }
            }
        } finally {
            processing.remove(playerId);
        }
    }

    private Set<Block> findConnectedLogs(Block origin) {
        Set<Block> found = new HashSet<>();
        Set<String> visited = new HashSet<>();
        Queue<Block> queue = new ArrayDeque<>();
        queue.add(origin);
        visited.add(key(origin));

        while (!queue.isEmpty() && found.size() < MAX_LOGS) {
            Block current = queue.poll();
            if (!isLog(current.getType())) continue;
            found.add(current);

            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        if (x == 0 && y == 0 && z == 0) continue;
                        int dx = current.getX() + x - origin.getX();
                        int dy = current.getY() + y - origin.getY();
                        int dz = current.getZ() + z - origin.getZ();
                        if (Math.abs(dx) > MAX_RADIUS || Math.abs(dz) > MAX_RADIUS
                                || Math.abs(dy) > MAX_RADIUS * 2) continue;
                        Block next = current.getRelative(x, y, z);
                        String key = key(next);
                        if (visited.add(key) && isLog(next.getType())) queue.add(next);
                    }
                }
            }
        }
        return found;
    }

    private boolean isLog(Material material) {
        String name = material.name();
        return name.endsWith("_LOG") || name.endsWith("_WOOD")
                || name.endsWith("_STEM") || name.endsWith("_HYPHAE");
    }

    private String key(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }
}
