package br.com.xklpack.shop;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class AdminShopConfigTest {
    @Test
    void everyProductHasValidMaterialAndPositivePrice() throws Exception {
        YamlConfiguration config = loadConfig();
        ConfigurationSection items = config.getConfigurationSection("items");
        assertNotNull(items);
        assertTrue(items.getKeys(false).size() >= 130, "O catálogo completo importado precisa ter pelo menos 130 produtos.");

        for (String id : items.getKeys(false)) {
            String path = "items." + id + ".";
            String materialName = config.getString(path + "material", "");
            Material material = Material.matchMaterial(materialName);
            if (material == null) {
                // Paper 26.3 expõe CHAIN pela Registry.ITEM/ItemType, não pelo enum Material.
                assertEquals("CHAIN", materialName, "ItemType sem suporte de Material: " + id);
            } else {
                assertNotEquals(Material.AIR, material, "Material inválido: " + id);
            }
            assertTrue(new BigDecimal(config.getString(path + "price", "0")).signum() > 0,
                    "Preço inválido: " + id);
            assertTrue(config.getInt(path + "amount", 1) > 0, "Quantidade inválida: " + id);
        }
    }

    @Test
    void constructionCategoryHasAllSixSubcategories() throws Exception {
        YamlConfiguration config = loadConfig();
        assertEquals(java.util.List.of("pedras", "tijolos", "tabuas", "troncos", "vidros", "extras"),
                config.getStringList("categories.construcao.children"));
        assertNotNull(config.getConfigurationSection("categories.armas"));
        assertNotNull(config.getConfigurationSection("categories.armaduras"));
    }

    @Test
    void redstoneHasFourRequestedSubcategories() throws Exception {
        YamlConfiguration config = loadConfig();
        assertEquals(java.util.List.of("redstone_componentes", "redstone_maquinas", "redstone_carrinhos", "redstone_mecanismos"),
                config.getStringList("categories.redstone.children"));
        assertEquals("redstone_componentes", config.getString("items.repeater.category"));
        assertEquals("redstone_maquinas", config.getString("items.hopper.category"));
    }

    @Test
    void gemsAreReservedForTools() throws Exception {
        YamlConfiguration config = loadConfig();
        ConfigurationSection items = config.getConfigurationSection("items");
        assertNotNull(items);
        for (String id : items.getKeys(false)) {
            String path = "items." + id + ".";
            String currency = config.getString(path + "currency", "coins");
            String category = config.getString(path + "category", "");
            if ("gems".equalsIgnoreCase(currency)) {
                assertEquals("ferramentas", category, id + " usa Gemas fora da categoria de ferramentas");
            }
        }
    }

    @Test
    void excavatorIsAStoreOnlySpecialProduct() throws Exception {
        YamlConfiguration config = loadConfig();
        assertEquals("excavator_3x3", config.getString("items.excavator_3x3.special"));
        assertEquals("NETHERITE_PICKAXE", config.getString("items.excavator_3x3.material"));
        assertEquals("gems", config.getString("items.excavator_3x3.currency"));
    }

    private YamlConfiguration loadConfig() throws Exception {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("admin-shop.yml");
        assertNotNull(stream, "admin-shop.yml precisa estar no classpath");
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            YamlConfiguration config = new YamlConfiguration();
            config.load(reader);
            return config;
        }
    }
}
