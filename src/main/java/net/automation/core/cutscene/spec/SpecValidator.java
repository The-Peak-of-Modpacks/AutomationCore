package net.automation.core.cutscene.spec;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SpecValidator {

    private SpecValidator() {}

    public enum Level {
        ERROR,
        WARNING
    }

    public record Problem(Level level, String where, String message) {

        @Override
        public String toString() {
            return (level == Level.ERROR ? "error" : "warning") + " in " + where + ": " + message;
        }
    }

    public static List<Problem> validate(Map<ResourceLocation, CutsceneSpec> cutscenes,
                                         Map<ResourceLocation, ActionSpec> actions,
                                         Map<ResourceLocation, TriggerSpec> triggers) {
        List<Problem> out = new ArrayList<>();
        for (CutsceneSpec cutscene : cutscenes.values()) validate(cutscene, cutscenes, actions, out);
        for (var entry : actions.entrySet()) {
            ActionSpec action = entry.getValue();
            String where = "action " + entry.getKey();
            action.cutscene().ifPresent(id -> {
                CutsceneSpec bound = cutscenes.get(id);
                if (bound == null) {
                    out.add(new Problem(Level.ERROR, where,
                            "belongs to the cutscene " + id + ", which does not exist"));
                } else if (!bound.allowedActions().contains(entry.getKey())) {
                    out.add(new Problem(Level.WARNING, where, "belongs to " + id + ", which never runs it"));
                }
            });
            if (action.commands().isEmpty() && action.setFlags().isEmpty() && action.clearFlags().isEmpty()) {
                out.add(new Problem(Level.WARNING, where, "does nothing: no commands and no flags"));
            }
            if (action.permissionLevel() > SpecLimits.MAX_ACTION_PERMISSION) {
                out.add(new Problem(Level.WARNING, where, "asks for permission level " + action.permissionLevel() +
                        "; its commands will run at " + SpecLimits.MAX_ACTION_PERMISSION));
            }
        }
        for (var entry : triggers.entrySet()) {
            if (!cutscenes.containsKey(entry.getValue().cutscene())) {
                out.add(new Problem(Level.ERROR, "trigger " + entry.getKey(), "plays the cutscene " +
                        entry.getValue().cutscene() + ", which does not exist"));
            }
        }
        return out;
    }

    private static void validate(CutsceneSpec cutscene, Map<ResourceLocation, CutsceneSpec> cutscenes,
                                 Map<ResourceLocation, ActionSpec> actions, List<Problem> out) {
        String where = "cutscene " + cutscene.id();
        Map<String, Integer> labels = cutscene.labels();

        Set<String> seen = new HashSet<>();
        for (CutsceneSpec.PageSpec page : cutscene.pages()) {
            page.label().ifPresent(label -> {
                if (!seen.add(label))
                    out.add(new Problem(Level.ERROR, where, "two pages are called \"" + label + "\""));
            });
        }

        Set<Integer> reachable = new HashSet<>();
        reachable.add(0);
        int last = cutscene.pages().size() - 1;
        for (int i = 0; i <= last; i++) {
            CutsceneSpec.PageSpec page = cutscene.pages().get(i);
            String at = where + ", page " + (i + 1);
            boolean falls = page.choices().isEmpty() && !page.end() && page.gotoLabel().isEmpty() &&
                    page.branches().isEmpty();
            if (falls && i < last) reachable.add(i + 1);
            if (page.gotoLabel().isPresent()) jump(page.gotoLabel().get(), labels, reachable, at, out);
            for (CutsceneSpec.BranchSpec branch : page.branches()) jump(branch.gotoLabel(), labels, reachable, at, out);
            if (!page.branches().isEmpty() && page.gotoLabel().isEmpty() && !page.end() && i < last)
                reachable.add(i + 1);
            for (CutsceneSpec.ChoiceSpec choice : page.choices()) {
                choice.gotoLabel().ifPresent(label -> jump(label, labels, reachable, at, out));
                if (choice.gotoLabel().isEmpty() && choice.next().isEmpty() && !choice.end() && i < last)
                    reachable.add(i + 1);
                choice.next().ifPresent(next -> {
                    if (!cutscenes.containsKey(next)) {
                        out.add(new Problem(Level.ERROR, at,
                                "a choice leads to the cutscene " + next + ", which does not exist"));
                    }
                });
            }
            if (!page.choices().isEmpty() && page.choices().stream().allMatch(c -> c.requires().isPresent())) {
                out.add(new Problem(Level.WARNING, at,
                        "every choice has a condition, so a player can be left with none to pick"));
            }
        }
        for (int i = 1; i <= last; i++) {
            if (!reachable.contains(i)) {
                out.add(new Problem(Level.WARNING, where + ", page " + (i + 1), "nothing leads to this page"));
            }
        }

        for (ResourceLocation action : cutscene.allowedActions()) {
            ActionSpec spec = actions.get(action);
            if (spec == null) {
                out.add(new Problem(Level.ERROR, where, "runs the action " + action + ", which no file defines"));
            } else if (spec.cutscene().isPresent() && !spec.cutscene().get().equals(cutscene.id())) {
                out.add(new Problem(Level.ERROR, where, "runs " + action + ", which belongs to " +
                        spec.cutscene().get() + ", so it will be refused"));
            }
        }
    }

    private static void jump(String label, Map<String, Integer> labels, Set<Integer> reachable, String at,
                             List<Problem> out) {
        Integer target = labels.get(label);
        if (target == null) {
            out.add(new Problem(Level.ERROR, at, "jumps to \"" + label + "\", which is not a page"));
        } else {
            reachable.add(target);
        }
    }
}
