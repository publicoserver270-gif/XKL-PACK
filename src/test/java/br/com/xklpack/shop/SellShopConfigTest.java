package br.com.xklpack.shop;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SellShopConfigTest {
    @Test
    void everyConfiguredSellItemIsAValidUniqueItemAndTotemIsExcluded() throws Exception {
        YamlConfiguration config = loadConfig();
        ConfigurationSection items = config.getConfigurationSection("items");
        assertNotNull(items);
        Set<Material> seen = new HashSet<>();

        for (String id : items.getKeys(false)) {
            String path = "items." + id + ".";
            Material material = Material.matchMaterial(config.getString(path + "material", ""));
            assertNotNull(material, "Material inválido na entrada " + id);
            assertNotEquals("AIR", material.name(), "AIR não pode ser vendido");
            assertNotEquals(Material.TOTEM_OF_UNDYING, material, "Totem não pode ser vendido");
            assertTrue(seen.add(material), "Material duplicado na tabela: " + material);
            assertTrue(new BigDecimal(config.getString(path + "sell-price", "0")).signum() > 0,
                    "Preço de venda precisa ser positivo: " + id);
        }
    }

    @Test
    void nuggetsAreOneNinthOfTheirMatchingIngots() throws Exception {
        YamlConfiguration config = loadConfig();
        assertNinth(config, "copper_nugget", "copper_ingot");
        assertNinth(config, "iron_nugget", "iron_ingot");
        assertNinth(config, "gold_nugget", "gold_ingot");
    }

    @Test
    void compressedBlocksAreWorthMoreThanNineMatchingIngots() throws Exception {
        YamlConfiguration config = loadConfig();
        assertBlockWorthMore(config, "iron_block", "iron_ingot");
        assertBlockWorthMore(config, "gold_block", "gold_ingot");
        assertBlockWorthMore(config, "copper_block", "copper_ingot");
        assertBlockWorthMore(config, "diamond_block", "diamond");
    }

    private void assertNinth(YamlConfiguration config, String nugget, String ingot) {
        BigDecimal nuggetPrice = price(config, nugget);
        BigDecimal ingotPrice = price(config, ingot);
        BigDecimal difference = nuggetPrice.multiply(BigDecimal.valueOf(9)).subtract(ingotPrice).abs();
        assertTrue(difference.compareTo(new BigDecimal("0.0000001")) <= 0,
                nugget + " precisa valer exatamente 1/9 de " + ingot);
    }

    private void assertBlockWorthMore(YamlConfiguration config, String block, String ingot) {
        assertTrue(price(config, block).compareTo(price(config, ingot).multiply(BigDecimal.valueOf(9))) > 0,
                block + " precisa valer mais que nove unidades de " + ingot);
    }

    private BigDecimal price(YamlConfiguration config, String id) {
        return new BigDecimal(config.getString("items." + id + ".sell-price", "0"));
    }

    private YamlConfiguration loadConfig() throws Exception {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("sell-shop.yml");
        assertNotNull(stream, "sell-shop.yml precisa estar no classpath");
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            YamlConfiguration config = new YamlConfiguration();
            config.load(reader);
            return config;
        }
    }
}
