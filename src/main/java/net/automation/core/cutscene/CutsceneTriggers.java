package net.automation.core.cutscene;

import net.automation.core.AutomationCore;
import net.automation.core.cutscene.spec.TriggerSpec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = AutomationCore.MOD_ID)
public final class CutsceneTriggers {

    private static final String JOINED = "__joined";
    private static final int MAX_PENDING = 256;

    private record Pending(UUID player, ResourceLocation cutscene, long at) {}

    private static final List<Pending> PENDING = new ArrayList<>();

    private CutsceneTriggers() {}

    private static void fire(ServerPlayer player, TriggerSpec.Type type, ResourceLocation value) {
        long now = player.server.getTickCount();
        PlayerFacts facts = new PlayerFacts(player);
        for (var entry : CutsceneData.triggers().entrySet()) {
            TriggerSpec trigger = entry.getValue();
            if (trigger.on() != type) continue;
            if (trigger.value().isPresent() && !trigger.value().get().equals(value)) continue;
            String key = entry.getKey().toString();
            if (trigger.once() && CutsceneProgress.fired(player, key)) continue;
            if (trigger.requires().isPresent() && !trigger.requires().get().test(facts)) continue;
            if (CutsceneData.cutscene(trigger.cutscene()) == null) continue;
            if (trigger.once()) CutsceneProgress.markFired(player, key);
            synchronized (PENDING) {
                if (PENDING.size() < MAX_PENDING) {
                    PENDING.add(new Pending(player.getUUID(), trigger.cutscene(), now + trigger.delayTicks()));
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!CutsceneProgress.fired(player, JOINED)) {
            CutsceneProgress.markFired(player, JOINED);
            fire(player, TriggerSpec.Type.FIRST_JOIN, null);
        }
        fire(player, TriggerSpec.Type.LOGIN, null);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !event.isEndConquered()) {
            fire(player, TriggerSpec.Type.RESPAWN, null);
        }
    }

    @SubscribeEvent
    public static void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            fire(player, TriggerSpec.Type.ADVANCEMENT, event.getAdvancement().getId());
        }
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            fire(player, TriggerSpec.Type.DIMENSION_ENTER, event.getTo().location());
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        synchronized (PENDING) {
            if (PENDING.isEmpty()) return;
            long now = event.getServer().getTickCount();
            for (Iterator<Pending> it = PENDING.iterator(); it.hasNext();) {
                Pending pending = it.next();
                if (pending.at() > now) continue;
                it.remove();
                ServerPlayer player = event.getServer().getPlayerList().getPlayer(pending.player());
                if (player != null) Cutscenes.play(player, pending.cutscene());
            }
        }
    }

    static void forget(UUID player) {
        synchronized (PENDING) {
            PENDING.removeIf(p -> p.player().equals(player));
        }
    }
}
