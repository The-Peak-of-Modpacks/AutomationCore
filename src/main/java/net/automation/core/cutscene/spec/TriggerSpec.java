package net.automation.core.cutscene.spec;

import net.minecraft.resources.ResourceLocation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Optional;

public record TriggerSpec(Type on, Optional<ResourceLocation> value, ResourceLocation cutscene, boolean once,
                          Optional<Condition> requires, int delayTicks) {

    public enum Type {

        FIRST_JOIN("first_join", false),
        LOGIN("login", false),
        RESPAWN("respawn", false),
        ADVANCEMENT("advancement", true),
        DIMENSION_ENTER("dimension_enter", true);

        public final String key;
        public final boolean needsValue;

        Type(String key, boolean needsValue) {
            this.key = key;
            this.needsValue = needsValue;
        }

        static Type byKey(String key) {
            for (Type t : values()) if (t.key.equals(key)) return t;
            throw new SpecException("unknown trigger \"" + key + "\"");
        }
    }

    public static final int MAX_DELAY = 24000;

    public static TriggerSpec parse(JsonElement json) {
        if (!json.isJsonObject()) throw new SpecException("a trigger is an object");
        JsonObject o = json.getAsJsonObject();
        if (!o.has("on") || !o.get("on").isJsonPrimitive()) throw new SpecException("it has no \"on\"");
        Type type = Type.byKey(o.get("on").getAsString());
        Optional<ResourceLocation> value = o.has("value") ?
                Optional.of(CutsceneSpec.id(o.get("value"), "value")) : Optional.empty();
        if (type.needsValue && value.isEmpty()) throw new SpecException("\"" + type.key + "\" needs a \"value\"");
        if (!o.has("cutscene")) throw new SpecException("it has no \"cutscene\"");
        int delay = o.has("delay_ticks") ? CutsceneSpec.number(o.get("delay_ticks"), "delay_ticks") : 0;
        return new TriggerSpec(type, value, CutsceneSpec.id(o.get("cutscene"), "cutscene"),
                !o.has("once") || CutsceneSpec.bool(o.get("once"), "once"), CutsceneSpec.condition(o, "requires"),
                Math.max(0, Math.min(MAX_DELAY, delay)));
    }
}
