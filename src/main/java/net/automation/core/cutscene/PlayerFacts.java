package net.automation.core.cutscene;

import net.automation.core.cutscene.spec.Facts;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public record PlayerFacts(ServerPlayer player) implements Facts {

    @Override
    public boolean tag(String tag) {
        return player.getTags().contains(tag);
    }

    @Override
    public boolean flag(String flag) {
        return CutsceneProgress.flag(player, flag);
    }

    @Override
    public boolean seen(ResourceLocation cutscene) {
        return CutsceneProgress.seen(player, cutscene);
    }

    @Override
    public boolean advancement(ResourceLocation advancement) {
        var adv = player.server.getAdvancements().getAdvancement(advancement);
        return adv != null && player.getAdvancements().getOrStartProgress(adv).isDone();
    }

    @Override
    public boolean hasItem(ResourceLocation item) {
        Item wanted = BuiltInRegistries.ITEM.getOptional(item).orElse(null);
        if (wanted == null) return false;
        for (ItemStack stack : player.getInventory().items) if (stack.is(wanted)) return true;
        for (ItemStack stack : player.getInventory().offhand) if (stack.is(wanted)) return true;
        for (ItemStack stack : player.getInventory().armor) if (stack.is(wanted)) return true;
        return false;
    }

    @Override
    public ResourceLocation dimension() {
        return player.level().dimension().location();
    }

    @Override
    public int permission() {
        for (int level = 4; level > 0; level--) if (player.hasPermissions(level)) return level;
        return 0;
    }
}
