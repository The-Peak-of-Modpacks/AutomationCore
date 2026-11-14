package net.automation.core.cutscene.spec;

import net.minecraft.resources.ResourceLocation;

public interface Facts {

    boolean tag(String tag);

    boolean flag(String flag);

    boolean seen(ResourceLocation cutscene);

    boolean advancement(ResourceLocation advancement);

    boolean hasItem(ResourceLocation item);

    ResourceLocation dimension();

    int permission();
}
