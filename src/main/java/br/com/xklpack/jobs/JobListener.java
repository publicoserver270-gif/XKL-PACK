package br.com.xklpack.jobs;

import br.com.xklpack.XKLPack;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;

import java.sql.SQLException;

public final class JobListener implements Listener {
    private final XKLPack plugin;
    private final JobService jobs;

    public JobListener(XKLPack plugin, JobService jobs) {
        this.plugin = plugin;
        this.jobs = jobs;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        String job = activeJob(player);
        if (job == null) return;
        Block block = event.getBlock();
        Material type = block.getType();
        int xp = 0;
        if (job.equals("minerador") && (type.name().endsWith("_ORE") || type == Material.ANCIENT_DEBRIS)) {
            xp = plugin.getConfig().getInt("jobs.activity-xp.minerador", 5);
        } else if (job.equals("lenhador") && (type.name().endsWith("_LOG") || type.name().endsWith("_STEM")
                || type.name().endsWith("_HYPHAE"))) {
            xp = plugin.getConfig().getInt("jobs.activity-xp.lenhador", 3);
        } else if (job.equals("fazendeiro") && isMatureCrop(block)) {
            xp = plugin.getConfig().getInt("jobs.activity-xp.fazendeiro", 2);
        }
        award(player, job, xp);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || ! "cacador".equals(activeJob(killer))) return;
        Entity entity = event.getEntity();
        if (entity instanceof Player) return;
        award(killer, "cacador", plugin.getConfig().getInt("jobs.activity-xp.cacador", 4));
    }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (!"pescador".equals(activeJob(event.getPlayer()))) return;
        award(event.getPlayer(), "pescador", plugin.getConfig().getInt("jobs.activity-xp.pescador", 5));
    }

    private boolean isMatureCrop(Block block) {
        if (!(block.getBlockData() instanceof Ageable ageable)) return false;
        return ageable.getAge() >= ageable.getMaximumAge();
    }

    private String activeJob(Player player) {
        try {
            return jobs.getActiveJob(player.getUniqueId());
        } catch (SQLException exception) {
            plugin.getLogger().warning("Não foi possível consultar a profissão de " + player.getName() + ": " + exception.getMessage());
            return null;
        }
    }

    private void award(Player player, String job, int xp) {
        if (xp <= 0) return;
        try {
            JobService.ProgressResult result = jobs.addProgress(player.getUniqueId(), player.getName(), job, xp);
            if (result.newLevel() > result.oldLevel()) {
                player.sendMessage("§6§lPROFISSÃO! §eVocê alcançou o nível " + result.newLevel() + " em " + jobs.getJobName(job) + "!");
            }
            if (result.gemsAwarded() > 0) {
                player.sendMessage("§d+" + result.gemsAwarded() + " Gemas §7por sua atividade profissional!");
            }
            if (result.newLevel() != result.oldLevel() || result.gemsAwarded() > 0) {
                player.sendMessage("§7Progresso: §f" + result.xp() + "§7/§f" + jobs.xpRequired(result.newLevel()) + " XP");
            }
        } catch (SQLException exception) {
            plugin.getLogger().severe("Falha ao salvar atividade profissional: " + exception.getMessage());
            player.sendMessage("§cSua atividade foi detectada, mas não foi possível salvar o progresso. Avise a administração.");
        }
    }
}
