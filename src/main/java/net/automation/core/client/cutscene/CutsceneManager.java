package net.automation.core.client.cutscene;

import net.automation.core.AutomationCore;
import net.automation.core.cutscene.spec.Condition;
import net.automation.core.network.S2CCutsceneSyncPacket;
import net.automation.core.network.S2CPlayCutscenePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class CutsceneManager {

    private static final Map<ResourceLocation, CutsceneDefinition> CUTSCENES = new HashMap<>();

    private CutsceneManager() {}

    public record Session(long token, Set<String> trueConditions, boolean replay) {

        public boolean met(Optional<Condition> condition) {
            return condition.isEmpty() || trueConditions.contains(condition.get().key());
        }
    }

    public static void clear() {
        CUTSCENES.clear();
    }

    public static void sync(S2CCutsceneSyncPacket msg) {
        if (msg.isReset()) {
            CUTSCENES.clear();
            return;
        }
        try {
            JsonElement json = JsonParser.parseString(msg.json());
            CutsceneDefinition.CODEC.parse(JsonOps.INSTANCE, json)
                    .resultOrPartial(error -> AutomationCore.LOGGER.error("Could not read the cutscene {}: {}",
                            msg.id(), error))
                    .ifPresent(definition -> CUTSCENES.put(msg.id(), definition));
        } catch (JsonParseException e) {
            AutomationCore.LOGGER.error("The cutscene {} is not valid JSON: {}", msg.id(), e.getMessage());
        }
    }

    public static Optional<CutsceneDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(CUTSCENES.get(id));
    }

    public static Set<ResourceLocation> ids() {
        return CUTSCENES.keySet();
    }

    public static boolean play(S2CPlayCutscenePacket msg) {
        Optional<CutsceneDefinition> definition = get(msg.id());
        if (definition.isEmpty() || definition.get().pages().isEmpty()) {
            AutomationCore.LOGGER.warn("The server asked for the cutscene {}, which this client does not have",
                    msg.id());
            return false;
        }
        Session session = new Session(msg.token(), Set.copyOf(msg.trueConditions()), msg.replay());
        Minecraft.getInstance().setScreen(new CutsceneScreen(msg.id(), definition.get(), session));
        return true;
    }
}
