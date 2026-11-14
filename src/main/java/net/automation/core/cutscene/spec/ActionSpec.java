package net.automation.core.cutscene.spec;

import net.minecraft.resources.ResourceLocation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record ActionSpec(Optional<ResourceLocation> cutscene, boolean once, Optional<String> exclusiveGroup,
                         int cooldownTicks, Optional<Condition> requires, List<String> setFlags,
                         List<String> clearFlags, int permissionLevel, List<String> commands) {

    public static ActionSpec parse(JsonElement json) {
        if (!json.isJsonObject()) throw new SpecException("an action is an object");
        JsonObject o = json.getAsJsonObject();

        Optional<ResourceLocation> cutscene = o.has("cutscene") ?
                Optional.of(CutsceneSpec.id(o.get("cutscene"), "cutscene")) : Optional.empty();
        boolean once = !o.has("once") || CutsceneSpec.bool(o.get("once"), "once");
        Optional<String> group = Optional.empty();
        if (o.has("exclusive_group")) group = Optional.of(name(o.get("exclusive_group"), "exclusive_group"));
        int cooldown = o.has("cooldown_ticks") ?
                Math.max(0, CutsceneSpec.number(o.get("cooldown_ticks"), "cooldown_ticks")) : 0;
        int permission = o.has("permission_level") ?
                CutsceneSpec.number(o.get("permission_level"), "permission_level") : 2;
        if (permission < 0 || permission > 4) throw new SpecException("\"permission_level\" is a number from 0 to 4");

        List<String> commands = new ArrayList<>();
        if (o.has("commands")) {
            JsonArray array = CutsceneSpec.array(o.get("commands"), "commands");
            if (array.size() > SpecLimits.MAX_COMMANDS) throw new SpecException("it has too many commands");
            for (JsonElement e : array) {
                if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) {
                    throw new SpecException("\"commands\" holds command text");
                }
                String command = e.getAsString();
                if (command.length() > SpecLimits.MAX_COMMAND_LENGTH) throw new SpecException("a command is too long");
                commands.add(command);
            }
        }
        return new ActionSpec(cutscene, once, group, cooldown, CutsceneSpec.condition(o, "requires"),
                names(o, "set_flags"), names(o, "clear_flags"), permission, List.copyOf(commands));
    }

    private static List<String> names(JsonObject o, String key) {
        if (!o.has(key)) return List.of();
        JsonArray array = CutsceneSpec.array(o.get(key), key);
        if (array.size() > SpecLimits.MAX_LIST) throw new SpecException("\"" + key + "\" has too many entries");
        List<String> out = new ArrayList<>();
        for (JsonElement e : array) out.add(name(e, key));
        return List.copyOf(out);
    }

    private static String name(JsonElement e, String what) {
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString())
            throw new SpecException("\"" + what + "\" holds names");
        String s = e.getAsString();
        if (s.isEmpty() || s.length() > SpecLimits.MAX_FLAG_LENGTH)
            throw new SpecException("\"" + what + "\" has an unusable name");
        return s;
    }

    public int effectivePermission() {
        return Math.min(permissionLevel, SpecLimits.MAX_ACTION_PERMISSION);
    }
}
