package net.automation.core.cutscene;

import net.automation.core.AutomationCore;
import net.automation.core.cutscene.spec.Condition;
import net.automation.core.cutscene.spec.CutsceneSpec;
import net.automation.core.cutscene.spec.SpecValidator;
import net.automation.core.network.AutomationNetwork;
import net.automation.core.network.S2CCutsceneSyncPacket;
import net.automation.core.network.S2CPlayCutscenePacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Mod.EventBusSubscriber(modid = AutomationCore.MOD_ID)
public final class Cutscenes {

    private Cutscenes() {}

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_CUTSCENES = (context,
                                                                                     builder) -> SharedSuggestionProvider
                                                                                             .suggestResource(
                                                                                                     CutsceneData
                                                                                                             .cutscenes()
                                                                                                             .keySet(),
                                                                                                     builder);

    public static boolean play(ServerPlayer player, ResourceLocation id) {
        return start(player, id, false, false);
    }

    public static boolean playForced(ServerPlayer player, ResourceLocation id) {
        return start(player, id, true, false);
    }

    public static boolean replay(ServerPlayer player, ResourceLocation id) {
        return start(player, id, true, true);
    }

    private static boolean start(ServerPlayer player, ResourceLocation id, boolean force, boolean replay) {
        CutsceneSpec spec = CutsceneData.cutscene(id);
        if (spec == null) {
            AutomationCore.LOGGER.warn("Tried to play the cutscene {}, which no file defines", id);
            return false;
        }
        PlayerFacts facts = new PlayerFacts(player);
        if (!force) {
            if (spec.skipIfSeen() && CutsceneProgress.seen(player, id)) return false;
            if (spec.requires().isPresent() && !spec.requires().get().test(facts)) return false;
        }
        CutsceneSessions.Session session = CutsceneSessions.begin(player, spec, replay);
        List<String> trueConditions = new ArrayList<>();
        for (Condition condition : spec.conditions()) if (condition.test(facts)) trueConditions.add(condition.key());
        AutomationNetwork.sendToPlayer(player, new S2CPlayCutscenePacket(id, session.token, trueConditions, replay));
        return true;
    }

    public static void finish(ServerPlayer player, long token, ResourceLocation cutscene) {
        CutsceneSessions.Session session = CutsceneSessions.check(player, token, cutscene);
        if (session == null) return;
        if (!session.replay) CutsceneProgress.markSeen(player, cutscene);
        CutsceneSessions.end(player);
    }

    public static void request(ServerPlayer player, long token, ResourceLocation from, ResourceLocation to) {
        CutsceneSessions.Session session = CutsceneSessions.check(player, token, from);
        if (session == null || session.replay || !session.next.contains(to)) {
            AutomationCore.LOGGER.warn("Refused {} going from the cutscene {} to {}", player.getGameProfile().getName(),
                    from, to);
            return;
        }
        if (!session.replay) CutsceneProgress.markSeen(player, from);
        start(player, to, false, false);
    }

    public static void sync(ServerPlayer player) {
        AutomationNetwork.sendToPlayer(player, S2CCutsceneSyncPacket.reset());
        CutsceneData.json().forEach((id, json) -> AutomationNetwork.sendToPlayer(player,
                S2CCutsceneSyncPacket.of(id, json)));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) sync(event.getPlayer());
        else event.getPlayerList().getPlayers().forEach(Cutscenes::sync);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        CutsceneSessions.end(event.getEntity().getUUID());
        CutsceneActions.forget(event.getEntity().getUUID());
        CutsceneTriggers.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("cutscene")
                
                .then(Commands.literal("open")
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .suggests(SUGGEST_CUTSCENES)
                                .executes(context -> open(context))))
                .then(Commands.literal("play")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("id", ResourceLocationArgument.id())
                                        .suggests(SUGGEST_CUTSCENES)
                                        .executes(context -> playCommand(context, false))
                                        .then(Commands.literal("force")
                                                .executes(context -> playCommand(context, true))))))
                .then(Commands.literal("list")
                        .requires(source -> source.hasPermission(2))
                        .executes(Cutscenes::list))
                .then(Commands.literal("info")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .suggests(SUGGEST_CUTSCENES)
                                .executes(Cutscenes::info)))
                .then(Commands.literal("validate")
                        .requires(source -> source.hasPermission(2))
                        .executes(Cutscenes::validate))
                .then(Commands.literal("reset")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(context -> {
                                    Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
                                    int cleared = targets.stream().mapToInt(CutsceneProgress::resetActions).sum();
                                    reply(context, "Cleared " + cleared + " used cutscene action(s) for " +
                                            targets.size() + " player(s)");
                                    return targets.size();
                                })))
                .then(Commands.literal("flag")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.literal("list").executes(context -> {
                                    for (ServerPlayer p : EntityArgument.getPlayers(context, "targets")) {
                                        reply(context, p.getGameProfile().getName() + ": " +
                                                CutsceneProgress.flags(p));
                                    }
                                    return 1;
                                }))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .executes(context -> flag(context, true))))
                                .then(Commands.literal("clear")
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .executes(context -> flag(context, false))))))
                .then(Commands.literal("seen")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.literal("list").executes(context -> {
                                    for (ServerPlayer p : EntityArgument.getPlayers(context, "targets")) {
                                        reply(context, p.getGameProfile().getName() + ": " +
                                                CutsceneProgress.seenList(p));
                                    }
                                    return 1;
                                }))
                                .then(Commands.literal("forget")
                                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                                .suggests(SUGGEST_CUTSCENES)
                                                .executes(context -> {
                                                    ResourceLocation id = ResourceLocationArgument.getId(context, "id");
                                                    EntityArgument.getPlayers(context, "targets")
                                                            .forEach(p -> CutsceneProgress.forgetSeen(p, id));
                                                    return 1;
                                                }))))));
    }

    private static int open(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ResourceLocation id = ResourceLocationArgument.getId(context, "id");
        boolean operator = context.getSource().hasPermission(2);
        if (!operator && !CutsceneProgress.seen(player, id)) {
            reply(context, "You have not seen that yet.");
            return 0;
        }
        return replay(player, id) ? 1 : 0;
    }

    private static int playCommand(CommandContext<CommandSourceStack> context,
                                   boolean force) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
        ResourceLocation id = ResourceLocationArgument.getId(context, "id");
        int played = 0;
        for (ServerPlayer player : targets) if (force ? playForced(player, id) : play(player, id)) played++;
        reply(context, "Playing cutscene " + id + " for " + played + " of " + targets.size() + " player(s)");
        return played;
    }

    private static int flag(CommandContext<CommandSourceStack> context,
                            boolean set) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String name = StringArgumentType.getString(context, "name");
        for (ServerPlayer player : EntityArgument.getPlayers(context, "targets")) {
            if (set) CutsceneProgress.setFlag(player, name);
            else CutsceneProgress.clearFlag(player, name);
        }
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        var ids = new ArrayList<>(CutsceneData.cutscenes().keySet());
        ids.sort(ResourceLocation::compareTo);
        reply(context, ids.size() + " cutscene(s): " + ids);
        return ids.size();
    }

    private static int info(CommandContext<CommandSourceStack> context) {
        ResourceLocation id = ResourceLocationArgument.getId(context, "id");
        CutsceneSpec spec = CutsceneData.cutscene(id);
        if (spec == null) {
            reply(context, "No such cutscene.");
            return 0;
        }
        reply(context, id + ": " + spec.pages().size() + " page(s), actions " + spec.allowedActions() + ", leads to " +
                spec.allowedNext() + ", " + spec.conditions().size() + " condition(s)");
        return 1;
    }

    private static int validate(CommandContext<CommandSourceStack> context) {
        var problems = CutsceneData.validate();
        if (problems.isEmpty()) reply(context, "No problems in " + CutsceneData.cutscenes().size() + " cutscene(s).");
        for (SpecValidator.Problem problem : problems) reply(context, problem.toString());
        return problems.size();
    }

    private static void reply(CommandContext<CommandSourceStack> context, String text) {
        context.getSource().sendSuccess(() -> Component.literal(text), false);
    }
}
