package org.yatopiamc.c2me.mixin.optimizations.thread_local_biome_sampler;

import net.minecraft.util.registry.Registry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.provider.OverworldBiomeProvider;
import net.minecraft.world.gen.layer.Layer;
import net.minecraft.world.gen.layer.LayerUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OverworldBiomeProvider.class)
public class MixinOverworldBiomeProvider {

    @Shadow @Final private Registry<Biome> biomes;
    private ThreadLocal<Layer> layerThreadLocal = new ThreadLocal<>();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(long p_i241958_1_, boolean p_i241958_3_, boolean p_i241958_4_, Registry<Biome> p_i241958_5_, CallbackInfo ci) {
        this.layerThreadLocal = ThreadLocal.withInitial(() -> LayerUtil.getDefaultLayer(p_i241958_1_, p_i241958_3_, p_i241958_4_ ? 6 : 4, 4)); // [VanillaCopy]
    }

    /**
     * @author ishland
     * @reason use thread_local sampler
     */
    @Overwrite
    public Biome getNoiseBiome(int p_225526_1_, int p_225526_2_, int p_225526_3_) {
        return this.layerThreadLocal.get().get(this.biomes, p_225526_1_, p_225526_3_);
    }

}
