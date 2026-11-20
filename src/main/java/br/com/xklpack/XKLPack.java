package br.com.xklpack;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Bootstrap do XKL PACK. Os módulos serão registrados aqui conforme forem
 * implementados e testados.
 */
public final class XKLPack extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info("XKL PACK iniciado. Módulos funcionais serão ativados por etapas.");
    }

    @Override
    public void onDisable() {
        getLogger().info("XKL PACK finalizado.");
    }
}
