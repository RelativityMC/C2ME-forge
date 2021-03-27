package org.yatopiamc.c2me.common.util;

import net.minecraftforge.fml.loading.FMLLoader;

import java.nio.file.Path;

public class ConfigDirUtil {

    public static Path getConfigDir() {
        final Path config = FMLLoader.getGamePath().resolve("config");
        config.toFile().mkdirs();
        return config;
    }

}
