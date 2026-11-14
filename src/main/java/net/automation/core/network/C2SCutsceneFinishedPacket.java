package net.automation.core.network;

import net.automation.core.cutscene.Cutscenes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record C2SCutsceneFinishedPacket(long token, ResourceLocation cutscene) {

    public static void encode(C2SCutsceneFinishedPacket msg, FriendlyByteBuf buf) {
        buf.writeLong(msg.token());
        buf.writeResourceLocation(msg.cutscene());
    }

    public static C2SCutsceneFinishedPacket decode(FriendlyByteBuf buf) {
        return new C2SCutsceneFinishedPacket(buf.readLong(), buf.readResourceLocation());
    }

    public static void handle(C2SCutsceneFinishedPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) Cutscenes.finish(player, msg.token(), msg.cutscene());
    }
}
