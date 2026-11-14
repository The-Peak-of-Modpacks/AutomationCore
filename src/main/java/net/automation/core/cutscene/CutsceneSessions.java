package net.automation.core.cutscene;

import net.automation.core.cutscene.spec.CutsceneSpec;
import net.automation.core.cutscene.spec.SpecLimits;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.Nullable;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CutsceneSessions {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private CutsceneSessions() {}

    public static final class Session {

        public final long token;
        public final CutsceneSpec spec;
        public final long expiresAtTick;

        public final boolean replay;
        public final Set<ResourceLocation> actions;
        public final Set<ResourceLocation> next;
        private long windowStart;
        private int windowCount;
        private int total;

        Session(long token, CutsceneSpec spec, long expiresAtTick, boolean replay) {
            this.token = token;
            this.spec = spec;
            this.expiresAtTick = expiresAtTick;
            this.replay = replay;
            this.actions = replay ? Set.of() : spec.allowedActions();
            this.next = replay ? Set.of() : spec.allowedNext();
        }

        synchronized boolean allowAction(long tick) {
            if (tick - windowStart >= 20) {
                windowStart = tick;
                windowCount = 0;
            }
            if (windowCount >= SpecLimits.MAX_ACTIONS_PER_SECOND || total >= SpecLimits.MAX_ACTIONS_PER_SESSION) {
                return false;
            }
            windowCount++;
            total++;
            return true;
        }
    }

    public static Session begin(ServerPlayer player, CutsceneSpec spec, boolean replay) {
        long tick = player.server.getTickCount();
        Session session = new Session(RANDOM.nextLong(), spec, tick + spec.maxSeconds() * 20L, replay);
        SESSIONS.put(player.getUUID(), session);
        return session;
    }

    public static @Nullable Session of(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) return null;
        if (player.server.getTickCount() > session.expiresAtTick) {
            SESSIONS.remove(player.getUUID(), session);
            return null;
        }
        return session;
    }

    public static @Nullable Session check(ServerPlayer player, long token, ResourceLocation cutscene) {
        Session session = of(player);
        if (session == null || session.token != token || !session.spec.id().equals(cutscene)) return null;
        return session;
    }

    public static boolean allowAction(ServerPlayer player, Session session) {
        return session.allowAction(player.server.getTickCount());
    }

    public static void end(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
    }

    public static void end(UUID player) {
        SESSIONS.remove(player);
    }
}
