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
        assertEquals(List.of("1", "11", "26", "51", "101", "160"),
                config.getConfigurationSection(path.substring(0, path.length() - 1)).getKeys(false).stream()
                        .sorted(java.util.Comparator.comparingInt(Integer::parseInt)).toList());
        assertEquals("WOODEN_PICKAXE", config.getString(path + "1.material"));   // até nível 10
        assertEquals("STONE_PICKAXE", config.getString(path + "11.material"));  // níveis 11–25
        assertEquals("IRON_PICKAXE", config.getString(path + "26.material"));   // níveis 26–50
        assertEquals("GOLDEN_PICKAXE", config.getString(path + "51.material")); // níveis 51–100
        assertEquals("DIAMOND_PICKAXE", config.getString(path + "101.material"));// níveis 101–159
        assertEquals("DIAMOND_PICKAXE", config.getString(path + "160.material"));// nível 160
        assertEquals(160, loadShopConfig().getInt("items.excavator_3x3.required-level"),
                "A Picareta Escavadora 3x3 deve ser liberada para compra no nível 160.");
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

    private YamlConfiguration loadShopConfig() throws Exception {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("admin-shop.yml");
        assertNotNull(stream, "admin-shop.yml precisa estar no classpath");
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            YamlConfiguration config = new YamlConfiguration();
            config.load(reader);
            return config;
        }
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
