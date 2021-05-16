package org.yatopiamc.c2me.mixin.threading.worldgen;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.gen.feature.template.Template;
import net.minecraft.world.gen.feature.template.TemplateManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Mixin(TemplateManager.class)
public class MixinStructureManager {

    @Mutable
    @Shadow
    @Final
    private Map<ResourceLocation, Template> structureRepository;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onPostInit(CallbackInfo info) {
        this.structureRepository = new ConcurrentHashMap<>();
    }

    @Redirect(method = "get", at = @At(value = "INVOKE", target = "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"))
    private <K, V> V onGetStructureComputeIfAbsent(Map<K, V> map, K key, Function<? super K, ? extends V> mappingFunction) {
        if (map.containsKey(key)) return map.get(key);
        final V value = mappingFunction.apply(key);
        if (value == null) return null;
        map.put(key, value);
        return value;
    }

}
