package net.automation.core.client.cutscene;

import net.automation.core.AutomationCore;
import net.automation.core.client.cutscene.CutsceneDefinition.TextSettings;
import net.automation.core.client.cutscene.CutscenePage.Choice;
import net.automation.core.client.cutscene.CutscenePage.CutsceneImage;
import net.automation.core.client.cutscene.background.CutsceneBackground;
import net.automation.core.network.AutomationNetwork;
import net.automation.core.network.C2SCutsceneActionPacket;
import net.automation.core.network.C2SCutsceneFinishedPacket;
import net.automation.core.network.C2SCutsceneRequestPacket;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Quaternionf;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class CutsceneScreen extends Screen {

    private static final float PAGE_FADE_OUT = 0.4f;
    private static final float LETTER_FADE_IN = 0.15f;
    private static final float LETTER_RISE = 3f;
    private static final float CHOICE_DELAY = 0.25f;
    private static final float CHOICE_FADE_IN = 0.4f;
    private static final float CHOICE_SCALE = 1.15f;
    private static final float TEXT_Z = 400;
    private static final float NOT_COMPLETED = Float.MAX_VALUE;
    private static final ResourceLocation PLAYER = new ResourceLocation("minecraft", "player");

    private final ResourceLocation id;
    private final CutsceneDefinition definition;
    private final CutsceneManager.Session session;
    private final Map<String, Integer> labels = new HashMap<>();
    private final long startMillis = Util.getMillis();
    private final RandomSource random = RandomSource.create();

    private int page = 0;
    private float pageStart;

    private float completedAt = NOT_COMPLETED;
    private float pageEndingAt = -1;
    private int targetPage;
    private float outroAt = -1;
    private boolean pageActionsSent;
    private boolean closed;
    private boolean finishedSent;
    private boolean failed;

    private final Set<Integer> firedEffects = new HashSet<>();
    private float flashStart = -100f;
    private float flashDuration = 0.1f;
    private int flashColor = 0xFFFFFF;
    private float shakeStart = -100f;
    private float shakeDuration = 0.1f;
    private float shakeStrength;
    private final Set<CutsceneImage> broken = new HashSet<>();

    private CutsceneMusic music;
    private float pushedMusicVolume = -1;

    private List<Glyph> glyphs = List.of();
    private int lineCount;
    private int soundedGlyphs;
    private final List<ChoiceBox> choiceBoxes = new ArrayList<>();
    private final Map<CutscenePage.CutsceneImage, Optional<LivingEntity>> entities = new HashMap<>();
    private final Map<CutscenePage.CutsceneImage, ItemStack> items = new HashMap<>();

    private record Glyph(FormattedCharSequence text, float x, int line, float revealAt, boolean silent) {}

    private record ChoiceBox(int index, float x0, float y0, float x1, float y1) {

        boolean contains(double x, double y) {
            return x >= x0 && x <= x1 && y >= y0 && y <= y1;
        }
    }

    public CutsceneScreen(ResourceLocation id, CutsceneDefinition definition, CutsceneManager.Session session) {
        super(Component.empty());
        this.id = id;
        this.definition = definition;
        this.session = session;
        this.pageStart = definition.textStart();
        List<CutscenePage> pages = definition.pages();
        for (int i = 0; i < pages.size(); i++) {
            int index = i;
            pages.get(i).id().ifPresent(label -> {
                if (labels.putIfAbsent(label, index) != null) {
                    AutomationCore.LOGGER.warn("Cutscene {} has more than one page with id '{}'", id, label);
                }
            });
        }
        
        int first = firstPlayable(0);
        if (first < 0) {
            outroAt = 0f;
        } else {
            page = first;
            targetPage = first;
        }
    }

    private int firstPlayable(int from) {
        List<CutscenePage> pages = definition.pages();
        for (int i = Math.max(0, from); i < pages.size(); i++) {
            if (session.met(pages.get(i).requires())) return i;
        }
        return -1;
    }

    private List<Choice> visibleChoices() {
        List<Choice> all = currentPage().choices();
        if (all.isEmpty()) return all;
        List<Choice> out = new ArrayList<>(all.size());
        for (Choice choice : all) if (session.met(choice.requires())) out.add(choice);
        return out;
    }

    private int nextTarget(CutscenePage current) {
        for (CutscenePage.Branch branch : current.branches()) {
            if (session.met(Optional.of(branch.requires()))) return resolve(Optional.of(branch.gotoLabel()), page + 1);
        }
        return resolve(current.gotoLabel(), page + 1);
    }

    @Override
    protected void init() {
        layoutPage();
    }

    private float now() {
        return (Util.getMillis() - startMillis) / 1000f;
    }

    private CutscenePage currentPage() {
        return definition.pages().get(page);
    }

    private void layoutPage() {
        CutsceneDefinition.TextSettings text = definition.text();
        int wrapWidth = Math.max(40, (int) (width * text.maxWidth() / Math.max(0.1f, text.scale())));

        List<FormattedCharSequence> lines = font.split(currentPage().content(), wrapWidth);

        List<Glyph> result = new ArrayList<>();
        float letterTime = 1f / Math.max(0.01f, definition.charsPerSecond());
        float[] time = { 0 };
        for (int line = 0; line < lines.size(); line++) {
            FormattedCharSequence sequence = lines.get(line);
            float[] x = { -font.width(sequence) / 2f };
            int lineIndex = line;
            sequence.accept((index, style, codepoint) -> {
                FormattedCharSequence glyph = FormattedCharSequence.codepoint(codepoint, style);
                result.add(new Glyph(glyph, x[0], lineIndex, time[0], Character.isWhitespace(codepoint)));
                x[0] += font.width(glyph);
                time[0] += letterTime + pauseAfter(codepoint);
                return true;
            });
        }
        glyphs = result;
        lineCount = lines.size();
    }

    private float pauseAfter(int codepoint) {
        return switch (codepoint) {
            case '.', '!', '?', '…' -> definition.punctuationPause();
            case ',', ';', ':', '—' -> definition.punctuationPause() * 0.5f;
            default -> 0;
        };
    }

    private float lineHeight() {
        return font.lineHeight + definition.text().lineSpacing();
    }

    private float textTop() {
        float centre = currentPage().textY().orElse(0.5f) * height;
        return centre - lineCount * lineHeight() * definition.text().scale() / 2f;
    }

    private float textBottom() {
        return textTop() + lineCount * lineHeight() * definition.text().scale();
    }

    private float pageRevealEnd() {
        float end = glyphs.isEmpty() ? 0 : glyphs.get(glyphs.size() - 1).revealAt();
        return Math.min(end, completedAt);
    }

    private boolean pageFullyShown(float time) {
        return time - pageStart >= pageRevealEnd();
    }

    private boolean choicesVisible(float time) {
        return !visibleChoices().isEmpty() && outroAt < 0 && pageEndingAt < 0 &&
                (time - pageStart - pageRevealEnd()) >= CHOICE_DELAY;
    }

    private void update(float time) {
        if (outroAt >= 0) {
            if (time - outroAt >= definition.fadeOut()) close();
            return;
        }
        if (pageEndingAt >= 0) {
            if (time - pageEndingAt >= PAGE_FADE_OUT) nextPage(time);
            return;
        }
        if (!pageActionsSent && time >= pageStart) {
            pageActionsSent = true;
            sendActions(currentPage().actions());
        }
        runEffects(time);
        if (definition.autoAdvance() >= 0 && visibleChoices().isEmpty() && time >= pageStart &&
                (time - pageStart - pageRevealEnd()) >= definition.autoAdvance()) {
            advance(time);
        }
    }

    private void runEffects(float time) {
        float pageTime = time - pageStart;
        if (pageTime < 0) return;
        List<CutscenePage.Effect> effects = currentPage().effects();
        for (int i = 0; i < effects.size(); i++) {
            CutscenePage.Effect effect = effects.get(i);
            if (effect.at() > pageTime || !firedEffects.add(i)) continue;
            switch (effect.type()) {
                case SOUND -> {
                    if (minecraft == null) break;
                    SoundEvent event = BuiltInRegistries.SOUND_EVENT.getOptional(effect.sound().get())
                            .orElseGet(() -> SoundEvent.createVariableRangeEvent(effect.sound().get()));
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(event,
                            Mth.clamp(effect.pitch(), 0.1f, 4f), Mth.clamp(effect.volume(), 0f, 4f)));
                }
                case FLASH -> {
                    flashStart = time;
                    flashDuration = Math.max(0.05f, effect.duration());
                    flashColor = effect.color() & 0xFFFFFF;
                }
                case SHAKE -> {
                    shakeStart = time;
                    shakeDuration = Math.max(0.05f, effect.duration());
                    shakeStrength = Mth.clamp(effect.strength(), 0f, 32f);
                }
            }
        }
    }

    private void advance(float time) {
        if (outroAt >= 0 || pageEndingAt >= 0 || time < pageStart) return;
        if (!pageFullyShown(time)) {
            completedAt = time - pageStart;
            soundedGlyphs = glyphs.size();
            return;
        }
        CutscenePage current = currentPage();
        if (!current.choices().isEmpty()) return;
        leavePage(time, current.end() ? -1 : nextTarget(current));
    }

    private void select(int index) {
        float time = now();
        if (!choicesVisible(time) || index < 0 || index >= visibleChoices().size()) return;
        CutscenePage.Choice choice = visibleChoices().get(index);
        if (minecraft != null) {
            minecraft.getSoundManager()
                    .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 0.8f, 0.25f));
        }
        sendActions(choice.actions());
        if (choice.cutscene().isPresent()) {
            
            if (!session.replay()) {
                AutomationNetwork.sendToServer(
                        new C2SCutsceneRequestPacket(session.token(), id, choice.cutscene().get()));
            }
            startOutro(time);
        } else if (choice.end()) {
            startOutro(time);
        } else {
            leavePage(time, resolve(choice.gotoLabel(), page + 1));
        }
    }

    private int resolve(Optional<String> label, int fallback) {
        if (label.isEmpty()) return fallback;
        Integer target = labels.get(label.get());
        if (target == null) {
            AutomationCore.LOGGER.warn("Cutscene {} jumps to unknown page id '{}'", id, label.get());
            return fallback;
        }
        return target;
    }

    private void leavePage(float time, int target) {
        int playable = target < 0 ? -1 : firstPlayable(target);
        if (playable < 0 || playable >= definition.pages().size()) {
            startOutro(time);
        } else {
            targetPage = playable;
            pageEndingAt = time;
        }
    }

    private void nextPage(float time) {
        page = targetPage;
        pageStart = time;
        pageEndingAt = -1;
        completedAt = NOT_COMPLETED;
        soundedGlyphs = 0;
        pageActionsSent = false;
        firedEffects.clear();
        layoutPage();
    }

    private void startOutro(float time) {
        if (outroAt >= 0) return;
        outroAt = time;
        sendActions(definition.onFinish());
    }

    private void sendActions(List<ResourceLocation> actions) {
        
        if (session.replay()) return;
        for (ResourceLocation action : actions) {
            AutomationNetwork.sendToServer(new C2SCutsceneActionPacket(session.token(), id, action));
        }
    }

    private void close() {
        if (closed) return;
        closed = true;
        onClose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float time = now();
        if (choicesVisible(time)) {
            for (ChoiceBox box : choiceBoxes) {
                if (box.contains(mouseX, mouseY)) {
                    select(box.index());
                    break;
                }
            }
            return true;
        }
        advance(time);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
            select(keyCode - GLFW.GLFW_KEY_1);
            return true;
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                if (definition.skippable()) startOutro(now());
                return true;
            }
            case GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                advance(now());
                return true;
            }
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return definition.pauseGame();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (failed) return;
        try {
            renderCutscene(graphics, mouseX, mouseY);
        } catch (RuntimeException e) {
            
            failed = true;
            AutomationCore.LOGGER.error("The cutscene {} could not be drawn, so it was closed", id, e);
            close();
        }
    }

    private void renderCutscene(GuiGraphics graphics, int mouseX, int mouseY) {
        float time = now();
        update(time);
        if (closed) return;

        float global = outroAt < 0 ? 1f : 1f - clamp01((time - outroAt) / Math.max(0.01f, definition.fadeOut()));
        float black = 1f - smooth(clamp01((time - definition.blackDuration()) / Math.max(0.01f, definition.fadeIn())));
        float pageAlpha = pageEndingAt < 0 ? 1f : 1f - clamp01((time - pageEndingAt) / PAGE_FADE_OUT);

        float shake = shakeStart < 0 || time - shakeStart > shakeDuration ? 0f :
                shakeStrength * (1f - (time - shakeStart) / shakeDuration);
        graphics.pose().pushPose();
        if (shake > 0.01f) {
            graphics.pose().translate((random.nextFloat() - 0.5f) * 2f * shake,
                    (random.nextFloat() - 0.5f) * 2f * shake, 0);
        }
        definition.background().render(graphics,
                new CutsceneBackground.BackgroundContext(width, height, time, global, mouseX, mouseY));
        if (black > 0) graphics.fill(0, 0, width, height, argb(black * global, 0x000000));
        renderImages(graphics, time, pageAlpha * global);

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, TEXT_Z);
        renderText(graphics, time, pageAlpha, global);
        renderChoices(graphics, time, mouseX, mouseY, global);
        renderHints(graphics, time, global);
        graphics.pose().popPose();
        graphics.pose().popPose();
        float flash = flashStart < 0 || time - flashStart > flashDuration ? 0f :
                1f - (time - flashStart) / flashDuration;
        if (flash > 0.01f) graphics.fill(0, 0, width, height, argb(flash * global, flashColor));
        updateMusic(time, global);
    }

    private void renderImages(GuiGraphics graphics, float time, float alpha) {
        float pageTime = time - pageStart;
        if (pageTime < 0 || alpha <= 0.01f) return;
        for (CutscenePage.CutsceneImage image : currentPage().images()) {
            CutscenePage.Animation animation = image.animation();
            float t = pageTime - animation.delay();
            if (t < 0) continue;
            float appear = smooth(clamp01(t / Math.max(0.01f, animation.fadeIn())));
            float a = appear * alpha;
            if (a <= 0.01f) continue;
            if (broken.contains(image)) continue;
            float cx = image.x() * width + (1f - appear) * animation.slideX();
            float cy = image.y() * height + (1f - appear) * animation.slideY() + Mth.sin(time * 2f) * animation.bob();
            try {

                if (image.texture().isPresent()) {
                    renderTexture(graphics, image, cx, cy, a);
                } else if (image.item().isPresent()) {
                    ItemStack stack = items.computeIfAbsent(image,
                            img -> new ItemStack(BuiltInRegistries.ITEM.get(img.item().get())));
                    float scale = image.scaleOr(4f);
                    PoseStack pose = graphics.pose();
                    pose.pushPose();
                    pose.translate(cx, cy, 0);
                    pose.scale(scale, scale, 1);
                    graphics.setColor(a, a, a, 1f);
                    graphics.renderItem(stack, -8, -8);
                    graphics.setColor(1f, 1f, 1f, 1f);
                    pose.popPose();
                } else {
                    entityFor(image).ifPresent(entity -> renderEntity(graphics, image, entity, cx, cy, time, a));
                }
            } catch (RuntimeException e) {
                graphics.setColor(1f, 1f, 1f, 1f);
                if (broken.add(image))
                    AutomationCore.LOGGER.error("A picture of the cutscene {} could not be drawn", id, e);
            }
        }
    }

    private void renderTexture(GuiGraphics graphics, CutsceneImage image, float cx, float cy, float alpha) {
        int tint = image.tint();
        int w = image.width(), h = image.height();
        int x0 = Math.round(cx - w / 2f), y0 = Math.round(cy - h / 2f);
        RenderSystem.enableBlend();
        graphics.setColor(((tint >> 16) & 0xFF) / 255f, ((tint >> 8) & 0xFF) / 255f, (tint & 0xFF) / 255f,
                ((tint >>> 24) / 255f) * alpha);
        ResourceLocation texture = image.texture().get();
        if (image.region().isPresent()) {
            CutscenePage.Region r = image.region().get();
            graphics.blit(texture, x0, y0, w, h, r.u(), r.v(), r.width(), r.height(), r.textureWidth(),
                    r.textureHeight());
        } else {
            graphics.blit(texture, x0, y0, w, h, 0f, 0f, 1, 1, 1, 1);
        }
        graphics.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    private Optional<LivingEntity> entityFor(CutsceneImage image) {
        if (minecraft == null || minecraft.level == null) return Optional.empty();
        ResourceLocation type = image.entity().get();
        if (type.equals(PLAYER)) return Optional.ofNullable(minecraft.player);
        return entities.computeIfAbsent(image, img -> {
            Optional<LivingEntity> entity;
            try {
                entity = EntityType.byString(type.toString())
                        .map(t -> t.create(minecraft.level))
                        .filter(LivingEntity.class::isInstance)
                        .map(LivingEntity.class::cast);
            } catch (RuntimeException e) {
                AutomationCore.LOGGER.error("Cutscene {} could not make the entity {}", id, type, e);
                entity = Optional.empty();
            }
            if (entity.isEmpty()) AutomationCore.LOGGER.warn("Cutscene {} can't show entity {}", id, type);
            return entity;
        });
    }

    private void renderEntity(GuiGraphics graphics, CutsceneImage image, LivingEntity entity, float x, float y,
                              float time, float alpha) {
        float angle = 180f + image.yaw() + time * image.rotation();
        float bodyRot = entity.yBodyRot, bodyRotO = entity.yBodyRotO, yRot = entity.getYRot(),
                xRot = entity.getXRot(), headRot = entity.yHeadRot, headRotO = entity.yHeadRotO;
        entity.yBodyRot = entity.yBodyRotO = angle;
        entity.setYRot(angle);
        entity.setXRot(0);
        entity.yHeadRot = entity.yHeadRotO = angle;

        graphics.setColor(alpha, alpha, alpha, 1f);
        InventoryScreen.renderEntityInInventory(graphics, Math.round(x), Math.round(y),
                Math.round(image.scaleOr(50f)), new Quaternionf().rotateZ((float) Math.PI), null, entity);
        graphics.setColor(1f, 1f, 1f, 1f);

        entity.yBodyRot = bodyRot;
        entity.yBodyRotO = bodyRotO;
        entity.setYRot(yRot);
        entity.setXRot(xRot);
        entity.yHeadRot = headRot;
        entity.yHeadRotO = headRotO;
    }

    private void renderText(GuiGraphics graphics, float time, float pageAlpha, float global) {
        float pageTime = time - pageStart;
        if (pageTime < 0) return;

        TextSettings text = definition.text();
        float lineHeight = lineHeight();

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(width / 2f, textTop(), 0);
        pose.scale(text.scale(), text.scale(), 1);

        int revealed = 0;
        for (int i = 0; i < glyphs.size(); i++) {
            Glyph glyph = glyphs.get(i);
            float revealAt = Math.min(glyph.revealAt(), completedAt);
            if (revealAt > pageTime) break;
            revealed++;

            float letterAlpha = clamp01((pageTime - revealAt) / LETTER_FADE_IN);
            float alpha = letterAlpha * pageAlpha * global;
            if (alpha < 0.02f || glyph.silent()) continue;

            float wave = Mth.sin(time * text.waveSpeed() + i * text.waveFrequency()) * text.waveAmplitude();
            pose.pushPose();
            pose.translate(glyph.x(), glyph.line() * lineHeight + wave + (1f - letterAlpha) * LETTER_RISE, 0);
            graphics.drawString(font, glyph.text(), 0, 0, argb(alpha, text.color().getValue()), text.shadow());
            pose.popPose();
        }
        pose.popPose();

        playTypeSound(revealed);
    }

    private void renderChoices(GuiGraphics graphics, float time, int mouseX, int mouseY, float global) {
        choiceBoxes.clear();
        if (!choicesVisible(time)) return;
        float alpha = smooth(clamp01((time - pageStart - pageRevealEnd() - CHOICE_DELAY) / CHOICE_FADE_IN)) * global;
        if (alpha < 0.05f) return;

        List<Choice> choices = visibleChoices();
        float rowHeight = (font.lineHeight + 7) * CHOICE_SCALE;
        float y = textBottom() + 18;
        PoseStack pose = graphics.pose();
        for (int i = 0; i < choices.size(); i++) {
            Component label = Component.literal((i + 1) + ".  ").append(choices.get(i).text());
            float w = font.width(label) * CHOICE_SCALE;
            float x0 = width / 2f - w / 2f, y0 = y + i * rowHeight;
            ChoiceBox box = new ChoiceBox(i, x0 - 8, y0 - 4, x0 + w + 8, y0 + font.lineHeight * CHOICE_SCALE + 4);
            choiceBoxes.add(box);
            boolean hovered = box.contains(mouseX, mouseY);

            if (hovered) {
                graphics.fill(Math.round(box.x0()), Math.round(box.y0()), Math.round(box.x1()), Math.round(box.y1()),
                        argb(0.18f * alpha, 0xFFFFFF));
            }
            pose.pushPose();
            pose.translate(x0 + (hovered ? 2 : 0), y0, 0);
            pose.scale(CHOICE_SCALE, CHOICE_SCALE, 1);
            graphics.drawString(font, label, 0, 0, argb(alpha, hovered ? 0xFFD27F : 0xD8D8D8), true);
            pose.popPose();
        }
    }

    private void playTypeSound(int revealed) {
        if (revealed <= soundedGlyphs || pageEndingAt >= 0 || outroAt >= 0 || minecraft == null) return;
        CutsceneDefinition.SoundSettings sound = definition.sound().orElse(null);
        int newest = revealed - 1;
        boolean due = sound != null && !glyphs.get(newest).silent() &&
                newest % Math.max(1, sound.everyNLetters()) == 0;
        soundedGlyphs = revealed;
        if (!due) return;

        SoundEvent event = BuiltInRegistries.SOUND_EVENT.getOptional(sound.id())
                .orElseGet(() -> SoundEvent.createVariableRangeEvent(sound.id()));
        float pitch = sound.pitch() + (random.nextFloat() * 2f - 1f) * sound.pitchVariance();
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, sound.volume()));
    }

    private void renderHints(GuiGraphics graphics, float time, float global) {
        if (global < 0.05f) return;
        if (definition.skippable()) {
            Component skip = Component.translatableWithFallback("cutscene.automation_core.skip", "Press ESC to skip");
            graphics.drawString(font, skip, width - font.width(skip) - 8, height - font.lineHeight - 6,
                    argb(0.45f * global, 0xFFFFFF), false);
        }

        boolean waiting = definition.autoAdvance() < 0 && visibleChoices().isEmpty() && outroAt < 0 &&
                pageEndingAt < 0 && time >= pageStart && (time - pageStart - pageRevealEnd()) > 0.3f;
        if (waiting) {
            float pulse = 0.55f + 0.35f * Mth.sin(time * 4f);
            PoseStack pose = graphics.pose();
            pose.pushPose();
            pose.translate(width / 2f, textBottom() + 8 + Mth.sin(time * 3f) * 2f, 0);
            Component arrow = Component.literal("▼");
            graphics.drawString(font, arrow, -font.width(arrow) / 2, 0, argb(pulse * global, 0xFFFFFF), false);
            pose.popPose();
        }
    }

    private void updateMusic(float time, float global) {
        CutsceneDefinition.MusicSettings settings = definition.music().orElse(null);
        if (settings == null || minecraft == null) return;
        if (settings.stopGameMusic()) minecraft.getMusicManager().stopPlaying();

        if (music == null) {
            boolean willPause = isPauseScreen() && minecraft.hasSingleplayerServer() &&
                    !minecraft.getSingleplayerServer().isPublished();
            if (time < settings.start() || (willPause && !minecraft.isPaused() && time < 0.5f)) return;
            SoundEvent event = BuiltInRegistries.SOUND_EVENT.getOptional(settings.sound())
                    .orElseGet(() -> SoundEvent.createVariableRangeEvent(settings.sound()));
            music = new CutsceneMusic(event, settings.source(), settings.pitch(), settings.loop());
            minecraft.getSoundManager().play(music);
        }

        float fade = smooth(clamp01((time - settings.start()) / Math.max(0.01f, settings.fadeIn())));
        float volume = settings.volume() * fade * global;
        music.setVolume(volume);

        if (minecraft.isPaused() && Math.abs(volume - pushedMusicVolume) > 0.005f) {
            minecraft.getSoundManager().updateSourceVolume(SoundSource.MUSIC,
                    minecraft.options.getSoundSourceVolume(SoundSource.MUSIC));
            pushedMusicVolume = volume;
        }
    }

    @Override
    public void tick() {
        
        if (minecraft == null || minecraft.player == null || !minecraft.player.isAlive()) close();
    }

    @Override
    public void removed() {
        if (music != null && minecraft != null) minecraft.getSoundManager().stop(music);
        music = null;
        
        if (!finishedSent && minecraft != null && minecraft.getConnection() != null) {
            finishedSent = true;
            AutomationNetwork.sendToServer(new C2SCutsceneFinishedPacket(session.token(), id));
        }
    }

    private static float clamp01(float value) {
        return Mth.clamp(value, 0f, 1f);
    }

    private static float smooth(float t) {
        return t * t * (3f - 2f * t);
    }

    private static int argb(float alpha, int rgb) {
        return ((int) (clamp01(alpha) * 255) << 24) | (rgb & 0xFFFFFF);
    }
}
