package net.automation.core.network;

import net.automation.core.cutscene.Cutscenes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record C2SCutsceneRequestPacket(long token, ResourceLocation from, ResourceLocation to) {

    public static void encode(C2SCutsceneRequestPacket msg, FriendlyByteBuf buf) {
        buf.writeLong(msg.token());
        buf.writeResourceLocation(msg.from());
        buf.writeResourceLocation(msg.to());
    }

    public static C2SCutsceneRequestPacket decode(FriendlyByteBuf buf) {
        return new C2SCutsceneRequestPacket(buf.readLong(), buf.readResourceLocation(), buf.readResourceLocation());
    }

    public static void handle(C2SCutsceneRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) Cutscenes.request(player, msg.token(), msg.from(), msg.to());
    }
}
