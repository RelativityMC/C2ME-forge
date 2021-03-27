package org.yatopiamc.c2me.mixin.threading.worldgen;

import net.minecraft.block.BlockState;
import net.minecraft.util.WeightedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.blockstateprovider.WeightedBlockStateProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Random;

@Mixin(WeightedBlockStateProvider.class)
public class MixinWeightedBlockStateProvider {

    @Shadow @Final private WeightedList<BlockState> weightedList;

    /**
     * @author ishland
     * @reason thread-safe getBlockState
     */
    @Overwrite
    public BlockState getState(Random random, BlockPos pos) {
        return new WeightedList<>(weightedList.entries).getOne(random);
    }

}
