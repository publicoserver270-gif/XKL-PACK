package br.com.xklpack.jobs;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProfessionKitConfigTest {
    private static final List<String> EXPECTED_TIERS = List.of("1", "5", "10", "20", "40", "80", "160");

    @Test
    void allFiveProfessionsHaveTheSameConfiguredEvolutionMilestones() throws Exception {
        YamlConfiguration config = loadConfig();
        ConfigurationSection professions = config.getConfigurationSection("jobs.kits.professions");
        assertNotNull(professions);
        assertEquals(5, professions.getKeys(false).size());
        for (String job : professions.getKeys(false)) {
            ConfigurationSection tiers = professions.getConfigurationSection(job + ".tiers");
            assertNotNull(tiers, "Profissão sem tiers: " + job);
            assertEquals(EXPECTED_TIERS, tiers.getKeys(false).stream().sorted(
                    java.util.Comparator.comparingInt(Integer::parseInt)).toList(), job);
            for (String tier : tiers.getKeys(false)) {
                String materialName = tiers.getString(tier + ".material", "");
                assertNotNull(Material.matchMaterial(materialName), "Material inválido em " + job + " nível " + tier);
            }
        }
    }

    @Test
    void mineradorToolProgressesFromWoodToNetherite() throws Exception {
        YamlConfiguration config = loadConfig();
        String path = "jobs.kits.professions.minerador.tiers.";
        assertEquals("WOODEN_PICKAXE", config.getString(path + "1.material"));
        assertEquals("STONE_PICKAXE", config.getString(path + "5.material"));
        assertEquals("IRON_PICKAXE", config.getString(path + "10.material"));
        assertEquals("GOLDEN_PICKAXE", config.getString(path + "20.material"));
        assertEquals("DIAMOND_PICKAXE", config.getString(path + "40.material"));
        assertEquals("NETHERITE_PICKAXE", config.getString(path + "80.material"));
        assertEquals("NETHERITE_PICKAXE", config.getString(path + "160.material"));
    }

    @Test
    void fishermanKeepsRodAndImprovesItsEnchantments() throws Exception {
        YamlConfiguration config = loadConfig();
        String path = "jobs.kits.professions.pescador.tiers.";
        for (String tier : EXPECTED_TIERS) {
            assertEquals("FISHING_ROD", config.getString(path + tier + ".material"));
        }
        assertEquals(6, config.getInt(path + "160.enchantments.lure"));
        assertEquals(6, config.getInt(path + "160.enchantments.luck_of_the_sea"));
    }

    private YamlConfiguration loadConfig() throws Exception {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("config.yml");
        assertNotNull(stream, "config.yml precisa estar no classpath");
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            YamlConfiguration config = new YamlConfiguration();
            config.load(reader);
            return config;
        }
    }
}
