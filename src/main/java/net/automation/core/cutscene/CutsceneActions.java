package net.automation.core.cutscene;

import net.automation.core.AutomationCore;
import net.automation.core.cutscene.spec.ActionSpec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CutsceneActions {

    private static final long LOG_EVERY_MS = 5000L;
    private static final Map<UUID, Long> LAST_LOGGED = new ConcurrentHashMap<>();

    private CutsceneActions() {}

    public static void trigger(ServerPlayer player, long token, ResourceLocation cutscene, ResourceLocation actionId) {
        CutsceneSessions.Session session = CutsceneSessions.check(player, token, cutscene);
        if (session == null) {
            refuse(player, "it is not in that cutscene", cutscene, actionId);
            return;
        }
        if (session.replay) return;
        if (!CutsceneSessions.allowAction(player, session)) {
            refuse(player, "it is asking too often", cutscene, actionId);
            return;
        }
        if (!session.actions.contains(actionId)) {
            refuse(player, "that cutscene does not run that action", cutscene, actionId);
            return;
        }
        ActionSpec action = CutsceneData.action(actionId);
        if (action == null) {
            AutomationCore.LOGGER.warn("Cutscene {} asked for the action {}, which no file defines", cutscene,
                    actionId);
            return;
        }
        if (action.cutscene().isPresent() && !action.cutscene().get().equals(cutscene)) {
            refuse(player, "the action belongs to another cutscene", cutscene, actionId);
            return;
        }
        if (action.requires().isPresent() && !action.requires().get().test(new PlayerFacts(player))) return;

        long now = player.level().getGameTime();
        if (action.once() && CutsceneProgress.used(player, actionId)) return;
        if (!action.once() && action.cooldownTicks() > 0 && CutsceneProgress.cooldownUntil(player, actionId) > now)
            return;
        if (action.exclusiveGroup().isPresent() && CutsceneProgress.groupUsed(player, action.exclusiveGroup().get())) {
            return;
        }

        CutsceneProgress.markUsed(player, actionId);
        action.exclusiveGroup().ifPresent(group -> CutsceneProgress.markGroup(player, group));
        if (action.cooldownTicks() > 0) CutsceneProgress.setCooldown(player, actionId, now + action.cooldownTicks());
        for (String flag : action.setFlags()) CutsceneProgress.setFlag(player, flag);
        for (String flag : action.clearFlags()) CutsceneProgress.clearFlag(player, flag);

        var source = player.createCommandSourceStack().withPermission(action.effectivePermission())
                .withSuppressedOutput();
        for (String command : action.commands()) {
            try {
                player.server.getCommands().performPrefixedCommand(source, command);
            } catch (RuntimeException e) {
                AutomationCore.LOGGER.error("The action {} could not run \"{}\"", actionId, command, e);
            }
        }
    }

    private static void refuse(ServerPlayer player, String why, ResourceLocation cutscene, ResourceLocation action) {
        long now = System.currentTimeMillis();
        Long last = LAST_LOGGED.get(player.getUUID());
        if (last != null && now - last < LOG_EVERY_MS) return;
        LAST_LOGGED.put(player.getUUID(), now);
        AutomationCore.LOGGER.warn("Refused the cutscene action {} from {} (cutscene {}): {}", action,
                player.getGameProfile().getName(), cutscene, why);
    }

    static void forget(UUID player) {
        LAST_LOGGED.remove(player);
    }
}
