package org.yatopiamc.c2me.mixin.threading.worldgen;

import net.minecraft.block.Block;
import net.minecraft.world.gen.feature.template.Template;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(Template.Palette.class)
public class MixinStructurePalettedBlockInfoList {

    @Mutable
    @Shadow @Final private Map<Block, List<Template.BlockInfo>> cache;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo info) {
        this.cache = new ConcurrentHashMap<>();
    }

}
