package net.automation.core.cutscene;

import net.automation.core.AutomationCore;
import net.automation.core.cutscene.spec.ActionSpec;
import net.automation.core.cutscene.spec.CutsceneSpec;
import net.automation.core.cutscene.spec.SpecException;
import net.automation.core.cutscene.spec.SpecLimits;
import net.automation.core.cutscene.spec.SpecValidator;
import net.automation.core.cutscene.spec.TriggerSpec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = AutomationCore.MOD_ID)
public final class CutsceneData {

    private static volatile Map<ResourceLocation, CutsceneSpec> cutscenes = Map.of();
    private static volatile Map<ResourceLocation, String> json = Map.of();
    private static volatile Map<ResourceLocation, ActionSpec> actions = Map.of();
    private static volatile Map<ResourceLocation, TriggerSpec> triggers = Map.of();
    private static volatile boolean dirty;

    private CutsceneData() {}

    public static Map<ResourceLocation, CutsceneSpec> cutscenes() {
        return cutscenes;
    }

    public static CutsceneSpec cutscene(ResourceLocation id) {
        return cutscenes.get(id);
    }

    public static Map<ResourceLocation, String> json() {
        return json;
    }

    public static Map<ResourceLocation, ActionSpec> actions() {
        return actions;
    }

    public static ActionSpec action(ResourceLocation id) {
        return actions.get(id);
    }

    public static Map<ResourceLocation, TriggerSpec> triggers() {
        return triggers;
    }

    public static List<SpecValidator.Problem> validate() {
        return SpecValidator.validate(cutscenes, actions, triggers);
    }

    @SubscribeEvent
    public static void addListeners(AddReloadListenerEvent event) {
        event.addListener(new CutsceneLoader());
        event.addListener(new ActionLoader());
        event.addListener(new TriggerLoader());
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !dirty) return;
        dirty = false;
        var problems = validate();
        for (var problem : problems) {
            if (problem.level() == SpecValidator.Level.ERROR) AutomationCore.LOGGER.error("[cutscenes] {}", problem);
            else AutomationCore.LOGGER.warn("[cutscenes] {}", problem);
        }
        AutomationCore.LOGGER.info("Cutscenes: {} cutscene(s), {} action(s), {} trigger(s), {} problem(s)",
                cutscenes.size(), actions.size(), triggers.size(), problems.size());
    }

    private static final class CutsceneLoader extends SimpleJsonResourceReloadListener {

        CutsceneLoader() {
            super(new Gson(), "cutscenes");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager,
                             ProfilerFiller profiler) {
            Map<ResourceLocation, CutsceneSpec> specs = new HashMap<>();
            Map<ResourceLocation, String> texts = new HashMap<>();
            files.forEach((id, file) -> {
                try {
                    String text = file.toString();
                    if (text.length() > SpecLimits.MAX_JSON_CHARS) {
                        throw new SpecException("the file is " + text.length() + " characters, and " +
                                SpecLimits.MAX_JSON_CHARS + " is the most");
                    }
                    specs.put(id, CutsceneSpec.parse(id, file));
                    texts.put(id, text);
                } catch (SpecException | IllegalStateException | UnsupportedOperationException e) {
                    AutomationCore.LOGGER.error("[cutscenes] could not read the cutscene {}: {}", id, e.getMessage());
                }
            });
            cutscenes = Map.copyOf(specs);
            json = Map.copyOf(texts);
            dirty = true;
        }
    }

    private static final class ActionLoader extends SimpleJsonResourceReloadListener {

        ActionLoader() {
            super(new Gson(), "cutscene_actions");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager,
                             ProfilerFiller profiler) {
            Map<ResourceLocation, ActionSpec> specs = new HashMap<>();
            files.forEach((id, file) -> {
                try {
                    specs.put(id, ActionSpec.parse(file));
                } catch (SpecException | IllegalStateException | UnsupportedOperationException e) {
                    AutomationCore.LOGGER.error("[cutscenes] could not read the action {}: {}", id, e.getMessage());
                }
            });
            actions = Map.copyOf(specs);
            dirty = true;
        }
    }

    private static final class TriggerLoader extends SimpleJsonResourceReloadListener {

        TriggerLoader() {
            super(new Gson(), "cutscene_triggers");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager,
                             ProfilerFiller profiler) {
            Map<ResourceLocation, TriggerSpec> specs = new HashMap<>();
            files.forEach((id, file) -> {
                try {
                    specs.put(id, TriggerSpec.parse(file));
                } catch (SpecException | IllegalStateException | UnsupportedOperationException e) {
                    AutomationCore.LOGGER.error("[cutscenes] could not read the trigger {}: {}", id, e.getMessage());
                }
            });
            triggers = Map.copyOf(specs);
            dirty = true;
        }
    }
}
