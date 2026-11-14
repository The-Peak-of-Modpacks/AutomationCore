package net.automation.core.network;

import net.automation.core.cutscene.CutsceneActions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record C2SCutsceneActionPacket(long token, ResourceLocation cutscene, ResourceLocation action) {

    public static void encode(C2SCutsceneActionPacket msg, FriendlyByteBuf buf) {
        buf.writeLong(msg.token());
        buf.writeResourceLocation(msg.cutscene());
        buf.writeResourceLocation(msg.action());
    }

    public static C2SCutsceneActionPacket decode(FriendlyByteBuf buf) {
        return new C2SCutsceneActionPacket(buf.readLong(), buf.readResourceLocation(), buf.readResourceLocation());
    }

    public static void handle(C2SCutsceneActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) CutsceneActions.trigger(player, msg.token(), msg.cutscene(), msg.action());
    }
}
