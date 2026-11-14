package net.automation.core.cutscene.spec;

public final class SpecLimits {

    private SpecLimits() {}

    public static final int MAX_PAGES = 500;
    public static final int MAX_CHOICES = 12;
    public static final int MAX_BRANCHES = 16;
    public static final int MAX_LIST = 32;
    public static final int MAX_IMAGES = 16;
    public static final int MAX_EFFECTS = 64;
    public static final int MAX_LABEL_LENGTH = 64;
    public static final int MAX_CONDITION_DEPTH = 8;
    public static final int MAX_CONDITION_CHILDREN = 32;
    public static final int MAX_COMMANDS = 32;
    public static final int MAX_COMMAND_LENGTH = 1024;
    public static final int MAX_FLAG_LENGTH = 64;
    public static final int MAX_FLAGS_PER_PLAYER = 512;
    public static final int MAX_SEEN_PER_PLAYER = 512;

    public static final int MAX_JSON_CHARS = 250_000;

    public static final int DEFAULT_MAX_SECONDS = 3600;
    public static final int MAX_SECONDS = 6 * 3600;

    public static final int MAX_ACTIONS_PER_SECOND = 48;
    public static final int MAX_ACTIONS_PER_SESSION = 400;

    public static final int MAX_ACTION_PERMISSION = 2;
}
