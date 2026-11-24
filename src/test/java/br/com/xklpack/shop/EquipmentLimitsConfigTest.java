package br.com.xklpack.shop;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EquipmentLimitsConfigTest {
    @Test
    void equipmentLimitsAreEditableAndUseTheActiveProfessionByDefault() throws Exception {
        YamlConfiguration config = loadConfig();
        assertTrue(config.getBoolean("equipment-limits.enabled"));
        assertEquals("active-profession", config.getString("equipment-limits.level-source"));
        assertLimits(config.getConfigurationSection("equipment-limits.owned-slots"));
        assertLimits(config.getConfigurationSection("equipment-limits.equipped-slots"));
    }

    private void assertLimits(ConfigurationSection section) {
        assertNotNull(section);
        assertEquals(List.of("1", "5", "10", "20", "30"),
                section.getKeys(false).stream().sorted(java.util.Comparator.comparingInt(Integer::parseInt)).toList());
        assertEquals(1, section.getInt("1"));
        assertEquals(2, section.getInt("5"));
        assertEquals(3, section.getInt("10"));
        assertEquals(4, section.getInt("20"));
        assertEquals(5, section.getInt("30"));
    }

    private YamlConfiguration loadConfig() throws Exception {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("config.yml");
        assertNotNull(stream);
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            YamlConfiguration config = new YamlConfiguration();
            config.load(reader);
            return config;
        }
    }
}
