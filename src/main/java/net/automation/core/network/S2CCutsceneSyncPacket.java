package net.automation.core.network;

import net.automation.core.client.ClientPacketHandlers;
import net.automation.core.cutscene.spec.SpecLimits;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record S2CCutsceneSyncPacket(boolean isReset, ResourceLocation id, String json) {

    private static final ResourceLocation NONE = new ResourceLocation("minecraft", "none");

    public static S2CCutsceneSyncPacket reset() {
        return new S2CCutsceneSyncPacket(true, NONE, "");
    }

    public static S2CCutsceneSyncPacket of(ResourceLocation id, String json) {
        return new S2CCutsceneSyncPacket(false, id, json);
    }

    public static void encode(S2CCutsceneSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.isReset());
        if (msg.isReset()) return;
        buf.writeResourceLocation(msg.id());
        buf.writeUtf(msg.json(), SpecLimits.MAX_JSON_CHARS);
    }

    public static S2CCutsceneSyncPacket decode(FriendlyByteBuf buf) {
        if (buf.readBoolean()) return reset();
        return new S2CCutsceneSyncPacket(false, buf.readResourceLocation(), buf.readUtf(SpecLimits.MAX_JSON_CHARS));
    }

    public static void handle(S2CCutsceneSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.syncCutscene(msg));
    }
}
