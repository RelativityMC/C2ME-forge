package org.yatopiamc.c2me;

import com.google.common.collect.ImmutableMap;
import net.minecraft.util.SharedConstants;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yatopiamc.c2me.common.config.C2MEConfig;
import org.yatopiamc.c2me.metrics.Metrics;

import java.util.Locale;

@Mod("c2me")
public class C2MEMod {
    public static final Logger LOGGER = LogManager.getLogger("C2ME");

    {
        onInitialize();
    }

    public void onInitialize() {
        final Metrics metrics = new Metrics(10514);
        metrics.addCustomChart(new Metrics.SimplePie("environmentType", () -> FMLLoader.getDist().name().toLowerCase(Locale.ENGLISH)));
        metrics.addCustomChart(new Metrics.SimplePie("useThreadedWorldGeneration", () -> String.valueOf(C2MEConfig.threadedWorldGenConfig.enabled)));
        metrics.addCustomChart(new Metrics.SimplePie("useThreadedWorldFeatureGeneration", () -> String.valueOf(C2MEConfig.threadedWorldGenConfig.allowThreadedFeatures)));
        metrics.addCustomChart(new Metrics.DrilldownPie("detailedMinecraftVersion", () -> ImmutableMap.of(SharedConstants.getCurrentVersion().getReleaseTarget(), ImmutableMap.of(SharedConstants.getCurrentVersion().getName(), 1))));
    }
}
