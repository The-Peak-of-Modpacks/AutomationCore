package net.automation.core.network;

import net.automation.core.AutomationCore;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class AutomationNetwork {

    private static final String PROTOCOL = "2";

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(AutomationCore.id("main"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();

    private AutomationNetwork() {}

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(S2CPlayCutscenePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CPlayCutscenePacket::encode)
                .decoder(S2CPlayCutscenePacket::decode)
                .consumerMainThread(S2CPlayCutscenePacket::handle)
                .add();
        CHANNEL.messageBuilder(S2CCutsceneSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CCutsceneSyncPacket::encode)
                .decoder(S2CCutsceneSyncPacket::decode)
                .consumerMainThread(S2CCutsceneSyncPacket::handle)
                .add();
        CHANNEL.messageBuilder(C2SCutsceneActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SCutsceneActionPacket::encode)
                .decoder(C2SCutsceneActionPacket::decode)
                .consumerMainThread(C2SCutsceneActionPacket::handle)
                .add();
        CHANNEL.messageBuilder(C2SCutsceneRequestPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SCutsceneRequestPacket::encode)
                .decoder(C2SCutsceneRequestPacket::decode)
                .consumerMainThread(C2SCutsceneRequestPacket::handle)
                .add();
        CHANNEL.messageBuilder(C2SCutsceneFinishedPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SCutsceneFinishedPacket::encode)
                .decoder(C2SCutsceneFinishedPacket::decode)
                .consumerMainThread(C2SCutsceneFinishedPacket::handle)
                .add();
    }

    public static void sendToPlayer(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }
}
