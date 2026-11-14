package net.automation.core.cutscene.spec;

import net.minecraft.resources.ResourceLocation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public sealed interface Condition {

    boolean test(Facts facts);

    String key();

    JsonElement json();

    record Always(JsonElement json) implements Condition {

        @Override
        public boolean test(Facts facts) {
            return true;
        }

        @Override
        public String key() {
            return json.toString();
        }
    }

    record Tag(String tag, JsonElement json) implements Condition {

        @Override
        public boolean test(Facts facts) {
            return facts.tag(tag);
        }

        @Override
        public String key() {
            return json.toString();
        }
    }

    record Flag(String flag, JsonElement json) implements Condition {

        @Override
        public boolean test(Facts facts) {
            return facts.flag(flag);
        }

        @Override
        public String key() {
            return json.toString();
        }
    }

    record Seen(ResourceLocation cutscene, JsonElement json) implements Condition {

        @Override
        public boolean test(Facts facts) {
            return facts.seen(cutscene);
        }

        @Override
        public String key() {
            return json.toString();
        }
    }

    record Advancement(ResourceLocation advancement, JsonElement json) implements Condition {

        @Override
        public boolean test(Facts facts) {
            return facts.advancement(advancement);
        }

        @Override
        public String key() {
            return json.toString();
        }
    }

    record HasItem(ResourceLocation item, JsonElement json) implements Condition {

        @Override
        public boolean test(Facts facts) {
            return facts.hasItem(item);
        }

        @Override
        public String key() {
            return json.toString();
        }
    }

    record InDimension(ResourceLocation dimension, JsonElement json) implements Condition {

        @Override
        public boolean test(Facts facts) {
            return dimension.equals(facts.dimension());
        }

        @Override
        public String key() {
            return json.toString();
        }
    }

    record Permission(int level, JsonElement json) implements Condition {

        @Override
        public boolean test(Facts facts) {
            return facts.permission() >= level;
        }

        @Override
        public String key() {
            return json.toString();
        }
    }

    record All(List<Condition> all, JsonElement json) implements Condition {

        @Override
        public boolean test(Facts facts) {
            for (Condition c : all) if (!c.test(facts)) return false;
            return true;
        }

        @Override
        public String key() {
            return json.toString();
        }
    }

    record Any(List<Condition> any, JsonElement json) implements Condition {

        @Override
        public boolean test(Facts facts) {
            for (Condition c : any) if (c.test(facts)) return true;
            return false;
        }

        @Override
        public String key() {
            return json.toString();
        }
    }

    record Not(Condition not, JsonElement json) implements Condition {

        @Override
        public boolean test(Facts facts) {
            return !not.test(facts);
        }

        @Override
        public String key() {
            return json.toString();
        }
    }

    static Condition parse(JsonElement json) {
        return parse(json, 0);
    }

    private static Condition parse(JsonElement json, int depth) {
        if (depth > SpecLimits.MAX_CONDITION_DEPTH) throw new SpecException("condition nested too deeply");
        if (!json.isJsonObject()) throw new SpecException("a condition is an object such as {\"flag\": \"x\"}");
        JsonObject o = json.getAsJsonObject();
        if (o.size() != 1) throw new SpecException("a condition has exactly one key, not " + o.keySet());
        String type = o.keySet().iterator().next();
        JsonElement value = o.get(type);
        switch (type) {
            case "tag":
                return new Tag(name(value, "tag"), json);
            case "flag":
                return new Flag(name(value, "flag"), json);
            case "seen":
                return new Seen(id(value, "seen"), json);
            case "advancement":
                return new Advancement(id(value, "advancement"), json);
            case "item":
                return new HasItem(id(value, "item"), json);
            case "dimension":
                return new InDimension(id(value, "dimension"), json);
            case "permission":
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                    throw new SpecException("\"permission\" is a number from 0 to 4");
                }
                int level = value.getAsInt();
                if (level < 0 || level > 4) throw new SpecException("\"permission\" is a number from 0 to 4");
                return new Permission(level, json);
            case "all":
                return new All(list(value, "all", depth), json);
            case "any":
                return new Any(list(value, "any", depth), json);
            case "not":
                return new Not(parse(value, depth + 1), json);
            case "always":
                return new Always(json);
            default:
                throw new SpecException("unknown condition \"" + type + "\"");
        }
    }

    private static List<Condition> list(JsonElement value, String what, int depth) {
        if (!value.isJsonArray()) throw new SpecException("\"" + what + "\" is a list of conditions");
        JsonArray array = value.getAsJsonArray();
        if (array.size() > SpecLimits.MAX_CONDITION_CHILDREN) {
            throw new SpecException("\"" + what + "\" has too many conditions");
        }
        List<Condition> out = new ArrayList<>();
        for (JsonElement e : array) out.add(parse(e, depth + 1));
        return List.copyOf(out);
    }

    private static String name(JsonElement value, String what) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new SpecException("\"" + what + "\" is a name");
        }
        String s = value.getAsString();
        if (s.isEmpty() || s.length() > SpecLimits.MAX_FLAG_LENGTH) {
            throw new SpecException("\"" + what + "\" is not a usable name");
        }
        return s;
    }

    private static ResourceLocation id(JsonElement value, String what) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new SpecException("\"" + what + "\" is an id such as mod:name");
        }
        ResourceLocation id = ResourceLocation.tryParse(value.getAsString());
        if (id == null) throw new SpecException("\"" + what + "\" is not an id: " + value.getAsString());
        return id;
    }
}
