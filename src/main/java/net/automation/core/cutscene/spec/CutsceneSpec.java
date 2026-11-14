package net.automation.core.cutscene.spec;

import net.minecraft.resources.ResourceLocation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record CutsceneSpec(ResourceLocation id, List<PageSpec> pages, List<ResourceLocation> onFinish,
                           Optional<Condition> requires, boolean skipIfSeen, int maxSeconds) {

    public record PageSpec(Optional<String> label, List<ResourceLocation> actions, List<ChoiceSpec> choices,
                           Optional<String> gotoLabel, List<BranchSpec> branches, Optional<Condition> requires,
                           boolean end) {}

    public record ChoiceSpec(List<ResourceLocation> actions, Optional<String> gotoLabel,
                             Optional<ResourceLocation> next, boolean end, Optional<Condition> requires) {}

    public record BranchSpec(Condition requires, String gotoLabel) {}

    public Set<ResourceLocation> allowedActions() {
        Set<ResourceLocation> out = new LinkedHashSet<>(onFinish);
        for (PageSpec page : pages) {
            out.addAll(page.actions());
            for (ChoiceSpec choice : page.choices()) out.addAll(choice.actions());
        }
        return out;
    }

    public Set<ResourceLocation> allowedNext() {
        Set<ResourceLocation> out = new LinkedHashSet<>();
        for (PageSpec page : pages) {
            for (ChoiceSpec choice : page.choices()) choice.next().ifPresent(out::add);
        }
        return out;
    }

    public List<Condition> conditions() {
        Map<String, Condition> out = new LinkedHashMap<>();
        requires.ifPresent(c -> out.putIfAbsent(c.key(), c));
        for (PageSpec page : pages) {
            page.requires().ifPresent(c -> out.putIfAbsent(c.key(), c));
            for (BranchSpec branch : page.branches()) out.putIfAbsent(branch.requires().key(), branch.requires());
            for (ChoiceSpec choice : page.choices()) choice.requires().ifPresent(c -> out.putIfAbsent(c.key(), c));
        }
        return new ArrayList<>(out.values());
    }

    public Map<String, Integer> labels() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (int i = 0; i < pages.size(); i++) {
            int index = i;
            pages.get(i).label().ifPresent(label -> out.putIfAbsent(label, index));
        }
        return out;
    }

    public static CutsceneSpec parse(ResourceLocation id, JsonElement json) {
        if (!json.isJsonObject()) throw new SpecException("a cutscene is an object");
        JsonObject root = json.getAsJsonObject();
        if (!root.has("pages") || !root.get("pages").isJsonArray()) throw new SpecException("it has no \"pages\" list");
        JsonArray pagesJson = root.getAsJsonArray("pages");
        if (pagesJson.isEmpty()) throw new SpecException("it has no pages");
        if (pagesJson.size() > SpecLimits.MAX_PAGES) {
            throw new SpecException(
                    "it has " + pagesJson.size() + " pages, and " + SpecLimits.MAX_PAGES + " is the most");
        }

        List<PageSpec> pages = new ArrayList<>();
        for (int i = 0; i < pagesJson.size(); i++) {
            try {
                pages.add(parsePage(pagesJson.get(i)));
            } catch (SpecException e) {
                throw new SpecException("page " + (i + 1) + ": " + e.getMessage());
            }
        }

        int seconds = SpecLimits.DEFAULT_MAX_SECONDS;
        if (root.has("max_seconds")) {
            seconds = Math.max(10, Math.min(SpecLimits.MAX_SECONDS, number(root.get("max_seconds"), "max_seconds")));
        }
        return new CutsceneSpec(id, List.copyOf(pages), ids(root, "on_finish"), condition(root, "requires"),
                root.has("skip_if_seen") && bool(root.get("skip_if_seen"), "skip_if_seen"), seconds);
    }

    private static PageSpec parsePage(JsonElement element) {
        
        if (!element.isJsonObject() || !element.getAsJsonObject().has("content")) {
            return new PageSpec(Optional.empty(), List.of(), List.of(), Optional.empty(), List.of(), Optional.empty(),
                    false);
        }
        JsonObject o = element.getAsJsonObject();
        Optional<String> label = Optional.empty();
        if (o.has("id")) label = Optional.of(label(o.get("id")));

        List<ChoiceSpec> choices = new ArrayList<>();
        if (o.has("choices")) {
            JsonArray array = array(o.get("choices"), "choices");
            if (array.size() > SpecLimits.MAX_CHOICES) throw new SpecException("too many choices");
            for (JsonElement choice : array) choices.add(parseChoice(choice));
        }
        List<BranchSpec> branches = new ArrayList<>();
        if (o.has("branches")) {
            JsonArray array = array(o.get("branches"), "branches");
            if (array.size() > SpecLimits.MAX_BRANCHES) throw new SpecException("too many branches");
            for (JsonElement branch : array) {
                if (!branch.isJsonObject() || !branch.getAsJsonObject().has("requires") ||
                        !branch.getAsJsonObject().has("goto")) {
                    throw new SpecException("a branch has \"requires\" and \"goto\"");
                }
                JsonObject b = branch.getAsJsonObject();
                branches.add(new BranchSpec(Condition.parse(b.get("requires")), label(b.get("goto"))));
            }
        }
        return new PageSpec(label, ids(o, "actions"), List.copyOf(choices), optionalLabel(o, "goto"),
                List.copyOf(branches), condition(o, "requires"), o.has("end") && bool(o.get("end"), "end"));
    }

    private static ChoiceSpec parseChoice(JsonElement element) {
        if (!element.isJsonObject()) throw new SpecException("a choice is an object");
        JsonObject o = element.getAsJsonObject();
        Optional<ResourceLocation> next = Optional.empty();
        if (o.has("cutscene")) next = Optional.of(id(o.get("cutscene"), "cutscene"));
        return new ChoiceSpec(ids(o, "actions"), optionalLabel(o, "goto"), next,
                o.has("end") && bool(o.get("end"), "end"), condition(o, "requires"));
    }

    static Optional<Condition> condition(JsonObject o, String key) {
        return o.has(key) ? Optional.of(Condition.parse(o.get(key))) : Optional.empty();
    }

    static List<ResourceLocation> ids(JsonObject o, String key) {
        if (!o.has(key)) return List.of();
        JsonArray array = array(o.get(key), key);
        if (array.size() > SpecLimits.MAX_LIST) throw new SpecException("\"" + key + "\" has too many entries");
        List<ResourceLocation> out = new ArrayList<>();
        for (JsonElement e : array) out.add(id(e, key));
        return List.copyOf(out);
    }

    static ResourceLocation id(JsonElement e, String what) {
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) {
            throw new SpecException("\"" + what + "\" holds ids such as mod:name");
        }
        ResourceLocation id = ResourceLocation.tryParse(e.getAsString());
        if (id == null) throw new SpecException("\"" + what + "\" has a bad id: " + e.getAsString());
        return id;
    }

    static JsonArray array(JsonElement e, String what) {
        if (!e.isJsonArray()) throw new SpecException("\"" + what + "\" is a list");
        return e.getAsJsonArray();
    }

    static boolean bool(JsonElement e, String what) {
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean())
            throw new SpecException("\"" + what + "\" is true or false");
        return e.getAsBoolean();
    }

    static int number(JsonElement e, String what) {
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber())
            throw new SpecException("\"" + what + "\" is a number");
        return e.getAsInt();
    }

    private static Optional<String> optionalLabel(JsonObject o, String key) {
        return o.has(key) ? Optional.of(label(o.get(key))) : Optional.empty();
    }

    private static String label(JsonElement e) {
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) throw new SpecException("a page id is a name");
        String s = e.getAsString();
        if (s.isEmpty() || s.length() > SpecLimits.MAX_LABEL_LENGTH)
            throw new SpecException("a page id is 1 to " + SpecLimits.MAX_LABEL_LENGTH + " characters");
        return s;
    }
}
