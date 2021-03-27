package org.yatopiamc.c2me;

import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.connect.IMixinConnector;

@SuppressWarnings("unused")
public class C2MEMixinConnector implements IMixinConnector {
    @Override
    public void connect() {
        System.out.println("Initializing C2ME Mixins");
        Mixins.addConfiguration("c2me.mixins.json");
    }
}
