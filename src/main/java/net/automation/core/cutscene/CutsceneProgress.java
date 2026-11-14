package net.automation.core.cutscene;

import net.automation.core.cutscene.spec.SpecLimits;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

public final class CutsceneProgress {

    private static final String ROOT = "automation_core_cutscenes";
    private static final String FLAGS = "flags";
    private static final String SEEN = "seen";
    private static final String USED = "used";
    private static final String GROUPS = "groups";
    private static final String FIRED = "fired";
    private static final String COOLDOWNS = "cooldowns";

    private CutsceneProgress() {}

    private static CompoundTag root(Player player) {
        CompoundTag persistent = player.getPersistentData();
        if (!persistent.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            persistent.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        CompoundTag persisted = persistent.getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.contains(ROOT, Tag.TAG_COMPOUND)) persisted.put(ROOT, new CompoundTag());
        return persisted.getCompound(ROOT);
    }

    private static boolean has(Player player, String list, String value) {
        return root(player).getList(list, Tag.TAG_STRING).contains(StringTag.valueOf(value));
    }

    private static boolean add(Player player, String list, String value, int limit) {
        CompoundTag root = root(player);
        ListTag tags = root.getList(list, Tag.TAG_STRING);
        StringTag tag = StringTag.valueOf(value);
        if (tags.contains(tag)) return true;
        if (tags.size() >= limit) return false;
        tags.add(tag);
        root.put(list, tags);
        return true;
    }

    private static void remove(Player player, String list, String value) {
        CompoundTag root = root(player);
        ListTag tags = root.getList(list, Tag.TAG_STRING);
        if (tags.remove(StringTag.valueOf(value))) root.put(list, tags);
    }

    public static boolean flag(Player player, String flag) {
        return has(player, FLAGS, flag);
    }

    public static boolean setFlag(Player player, String flag) {
        return add(player, FLAGS, flag, SpecLimits.MAX_FLAGS_PER_PLAYER);
    }

    public static void clearFlag(Player player, String flag) {
        remove(player, FLAGS, flag);
    }

    public static java.util.List<String> flags(Player player) {
        ListTag tags = root(player).getList(FLAGS, Tag.TAG_STRING);
        java.util.List<String> out = new java.util.ArrayList<>();
        for (int i = 0; i < tags.size(); i++) out.add(tags.getString(i));
        return out;
    }

    public static boolean seen(Player player, ResourceLocation cutscene) {
        return has(player, SEEN, cutscene.toString());
    }

    public static void markSeen(Player player, ResourceLocation cutscene) {
        add(player, SEEN, cutscene.toString(), SpecLimits.MAX_SEEN_PER_PLAYER);
    }

    public static void forgetSeen(Player player, ResourceLocation cutscene) {
        remove(player, SEEN, cutscene.toString());
    }

    public static boolean used(Player player, ResourceLocation action) {
        return has(player, USED, action.toString());
    }

    public static void markUsed(Player player, ResourceLocation action) {
        add(player, USED, action.toString(), SpecLimits.MAX_SEEN_PER_PLAYER);
    }

    public static boolean groupUsed(Player player, String group) {
        return has(player, GROUPS, group);
    }

    public static void markGroup(Player player, String group) {
        add(player, GROUPS, group, SpecLimits.MAX_SEEN_PER_PLAYER);
    }

    public static boolean fired(Player player, String key) {
        return has(player, FIRED, key);
    }

    public static void markFired(Player player, String key) {
        add(player, FIRED, key, SpecLimits.MAX_SEEN_PER_PLAYER);
    }

    public static long cooldownUntil(Player player, ResourceLocation action) {
        return root(player).getCompound(COOLDOWNS).getLong(action.toString());
    }

    public static void setCooldown(Player player, ResourceLocation action, long until) {
        CompoundTag root = root(player);
        CompoundTag cooldowns = root.getCompound(COOLDOWNS);
        if (cooldowns.size() >= SpecLimits.MAX_SEEN_PER_PLAYER && !cooldowns.contains(action.toString())) return;
        cooldowns.putLong(action.toString(), until);
        root.put(COOLDOWNS, cooldowns);
    }

    public static int resetActions(Player player) {
        CompoundTag root = root(player);
        int cleared = root.getList(USED, Tag.TAG_STRING).size();
        root.remove(USED);
        root.remove(GROUPS);
        root.remove(COOLDOWNS);
        root.remove(FIRED);
        return cleared;
    }

    public static void resetAll(Player player) {
        player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).remove(ROOT);
    }

    public static java.util.List<String> seenList(Player player) {
        ListTag tags = root(player).getList(SEEN, Tag.TAG_STRING);
        java.util.List<String> out = new java.util.ArrayList<>();
        for (int i = 0; i < tags.size(); i++) out.add(tags.getString(i));
        return out;
    }
}
