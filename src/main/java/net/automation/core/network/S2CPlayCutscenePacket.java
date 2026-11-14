package net.automation.core.network;

import net.automation.core.client.ClientPacketHandlers;
import net.automation.core.cutscene.spec.SpecLimits;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record S2CPlayCutscenePacket(ResourceLocation id, long token, List<String> trueConditions, boolean replay) {

    private static final int MAX_CONDITION_TEXT = 2048;

    public static void encode(S2CPlayCutscenePacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.id());
        buf.writeLong(msg.token());
        buf.writeBoolean(msg.replay());
        int count = Math.min(msg.trueConditions().size(), SpecLimits.MAX_PAGES * 4);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) buf.writeUtf(msg.trueConditions().get(i), MAX_CONDITION_TEXT);
    }

    public static S2CPlayCutscenePacket decode(FriendlyByteBuf buf) {
        ResourceLocation id = buf.readResourceLocation();
        long token = buf.readLong();
        boolean replay = buf.readBoolean();
        int count = buf.readVarInt();
        if (count < 0 || count > SpecLimits.MAX_PAGES * 4) throw new IllegalArgumentException("too many conditions");
        List<String> conditions = new ArrayList<>();
        for (int i = 0; i < count; i++) conditions.add(buf.readUtf(MAX_CONDITION_TEXT));
        return new S2CPlayCutscenePacket(id, token, List.copyOf(conditions), replay);
    }

    public static void handle(S2CPlayCutscenePacket msg, Supplier<NetworkEvent.Context> ctx) {
        
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.playCutscene(msg));
    }
}
