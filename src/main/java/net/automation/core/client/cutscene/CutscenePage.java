package net.automation.core.client.cutscene;

import net.automation.core.client.cutscene.background.BackgroundEffects;
import net.automation.core.cutscene.spec.Condition;
import net.automation.core.cutscene.spec.SpecLimits;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Optional;

public record CutscenePage(Optional<String> id, Component content, Optional<Float> textY, List<CutsceneImage> images,
                           List<ResourceLocation> actions, List<Choice> choices, Optional<String> gotoLabel,
                           boolean end, Optional<Condition> requires, List<Branch> branches, List<Effect> effects) {

    private static final Codec<CutscenePage> OBJECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("id").forGetter(CutscenePage::id),
            ExtraCodecs.COMPONENT.fieldOf("content").forGetter(CutscenePage::content),
            Codec.FLOAT.optionalFieldOf("text_y").forGetter(CutscenePage::textY),
            ConditionCodec.limited(CutsceneImage.CODEC, SpecLimits.MAX_IMAGES, "images")
                    .optionalFieldOf("images", List.of()).forGetter(CutscenePage::images),
            ConditionCodec.limited(ResourceLocation.CODEC, SpecLimits.MAX_LIST, "actions")
                    .optionalFieldOf("actions", List.of()).forGetter(CutscenePage::actions),
            ConditionCodec.limited(Choice.CODEC, SpecLimits.MAX_CHOICES, "choices")
                    .optionalFieldOf("choices", List.of()).forGetter(CutscenePage::choices),
            Codec.STRING.optionalFieldOf("goto").forGetter(CutscenePage::gotoLabel),
            Codec.BOOL.optionalFieldOf("end", false).forGetter(CutscenePage::end),
            ConditionCodec.CODEC.optionalFieldOf("requires").forGetter(CutscenePage::requires),
            ConditionCodec.limited(Branch.CODEC, SpecLimits.MAX_BRANCHES, "branches")
                    .optionalFieldOf("branches", List.of()).forGetter(CutscenePage::branches),
            ConditionCodec.limited(Effect.CODEC, SpecLimits.MAX_EFFECTS, "effects")
                    .optionalFieldOf("effects", List.of()).forGetter(CutscenePage::effects))
            .apply(i, CutscenePage::new));

    public static final Codec<CutscenePage> CODEC = Codec
            .either(OBJECT_CODEC, ExtraCodecs.COMPONENT).xmap(
                    either -> either.map(
                            page -> page,
                            CutscenePage::text),
                    page -> page.isPlain() ? Either.right(page.content()) : Either.left(page));

    public static CutscenePage text(Component content) {
        return new CutscenePage(Optional.empty(), content, Optional.empty(), List.of(), List.of(), List.of(),
                Optional.empty(), false, Optional.empty(), List.of(), List.of());
    }

    private boolean isPlain() {
        return id.isEmpty() && textY.isEmpty() && images.isEmpty() && actions.isEmpty() && choices.isEmpty() &&
                gotoLabel.isEmpty() && !end && requires.isEmpty() && branches.isEmpty() && effects.isEmpty();
    }

    public record Branch(Condition requires, String gotoLabel) {

        public static final Codec<Branch> CODEC = RecordCodecBuilder.create(i -> i.group(
                ConditionCodec.CODEC.fieldOf("requires").forGetter(Branch::requires),
                Codec.STRING.fieldOf("goto").forGetter(Branch::gotoLabel))
                .apply(i, Branch::new));
    }

    public record Effect(float at, Type type, Optional<ResourceLocation> sound, float volume, float pitch, int color,
                         float duration, float strength) {

        public enum Type {

            SOUND("sound"),
            FLASH("flash"),
            SHAKE("shake");

            public final String key;

            Type(String key) {
                this.key = key;
            }

            static final Codec<Type> CODEC = Codec.STRING.comapFlatMap(
                    name -> java.util.Arrays.stream(values()).filter(t -> t.key.equals(name)).findFirst()
                            .map(DataResult::success)
                            .orElseGet(() -> DataResult
                                    .error(() -> "Unknown effect '" + name + "', known: sound, flash, shake")),
                    t -> t.key);
        }

        public static final Codec<Effect> CODEC = RecordCodecBuilder.<Effect>create(i -> i.group(
                Codec.FLOAT.optionalFieldOf("at", 0f).forGetter(Effect::at),
                Type.CODEC.fieldOf("type").forGetter(Effect::type),
                ResourceLocation.CODEC.optionalFieldOf("sound").forGetter(Effect::sound),
                Codec.FLOAT.optionalFieldOf("volume", 1f).forGetter(Effect::volume),
                Codec.FLOAT.optionalFieldOf("pitch", 1f).forGetter(Effect::pitch),
                BackgroundEffects.COLOR.optionalFieldOf("color", 0xFFFFFFFF).forGetter(Effect::color),
                Codec.FLOAT.optionalFieldOf("duration", 0.6f).forGetter(Effect::duration),
                Codec.FLOAT.optionalFieldOf("strength", 4f).forGetter(Effect::strength))
                .apply(i, Effect::new)).flatXmap(Effect::check, Effect::check);

        private static DataResult<Effect> check(Effect effect) {
            return effect.type == Type.SOUND && effect.sound.isEmpty() ?
                    DataResult.error(() -> "A sound effect needs a sound") : DataResult.success(effect);
        }
    }

    public record Choice(Component text, List<ResourceLocation> actions, Optional<String> gotoLabel,
                         Optional<ResourceLocation> cutscene, boolean end, Optional<Condition> requires) {

        public static final Codec<Choice> CODEC = RecordCodecBuilder.create(i -> i.group(
                ExtraCodecs.COMPONENT.fieldOf("text").forGetter(Choice::text),
                ConditionCodec.limited(ResourceLocation.CODEC, SpecLimits.MAX_LIST, "actions")
                        .optionalFieldOf("actions", List.of()).forGetter(Choice::actions),
                Codec.STRING.optionalFieldOf("goto").forGetter(Choice::gotoLabel),
                ResourceLocation.CODEC.optionalFieldOf("cutscene").forGetter(Choice::cutscene),
                Codec.BOOL.optionalFieldOf("end", false).forGetter(Choice::end),
                ConditionCodec.CODEC.optionalFieldOf("requires").forGetter(Choice::requires))
                .apply(i, Choice::new));
    }

    public record CutsceneImage(Optional<ResourceLocation> texture, Optional<ResourceLocation> item,
                                Optional<ResourceLocation> entity, float x, float y, int width, int height,
                                float scale, float yaw, float rotation, int tint, Optional<Region> region,
                                Animation animation) {

        public static final Codec<CutsceneImage> CODEC = RecordCodecBuilder.<CutsceneImage>create(i -> i.group(
                ResourceLocation.CODEC.optionalFieldOf("texture").forGetter(CutsceneImage::texture),
                ResourceLocation.CODEC.optionalFieldOf("item").forGetter(CutsceneImage::item),
                ResourceLocation.CODEC.optionalFieldOf("entity").forGetter(CutsceneImage::entity),
                Codec.FLOAT.optionalFieldOf("x", 0.5f).forGetter(CutsceneImage::x),
                Codec.FLOAT.optionalFieldOf("y", 0.3f).forGetter(CutsceneImage::y),
                Codec.INT.optionalFieldOf("width", 64).forGetter(CutsceneImage::width),
                Codec.INT.optionalFieldOf("height", 64).forGetter(CutsceneImage::height),
                Codec.FLOAT.optionalFieldOf("scale", 0f).forGetter(CutsceneImage::scale),
                Codec.FLOAT.optionalFieldOf("yaw", 0f).forGetter(CutsceneImage::yaw),
                Codec.FLOAT.optionalFieldOf("rotation", 0f).forGetter(CutsceneImage::rotation),
                BackgroundEffects.COLOR.optionalFieldOf("tint", 0xFFFFFFFF).forGetter(CutsceneImage::tint),
                Region.CODEC.optionalFieldOf("region").forGetter(CutsceneImage::region),
                Animation.CODEC.optionalFieldOf("animation", Animation.DEFAULT)
                        .forGetter(CutsceneImage::animation))
                .apply(i, CutsceneImage::new)).flatXmap(CutsceneImage::check, CutsceneImage::check);

        private static DataResult<CutsceneImage> check(CutsceneImage image) {
            return (image.texture.isPresent() ? 1 : 0) + (image.item.isPresent() ? 1 : 0) +
                    (image.entity.isPresent() ? 1 : 0) == 1 ? DataResult.success(image) :
                            DataResult.error(() -> "A cutscene image needs exactly one of texture, item or entity");
        }

        public float scaleOr(float fallback) {
            return scale > 0 ? scale : fallback;
        }
    }

    public record Region(int u, int v, int width, int height, int textureWidth, int textureHeight) {

        public static final Codec<Region> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("u", 0).forGetter(Region::u),
                Codec.INT.optionalFieldOf("v", 0).forGetter(Region::v),
                Codec.INT.fieldOf("width").forGetter(Region::width),
                Codec.INT.fieldOf("height").forGetter(Region::height),
                Codec.INT.optionalFieldOf("texture_width", 256).forGetter(Region::textureWidth),
                Codec.INT.optionalFieldOf("texture_height", 256).forGetter(Region::textureHeight))
                .apply(i, Region::new));
    }

    public record Animation(float delay, float fadeIn, float slideX, float slideY, float bob) {

        public static final Animation DEFAULT = new Animation(0f, 0.8f, 0f, 8f, 0f);

        public static final Codec<Animation> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.optionalFieldOf("delay", DEFAULT.delay).forGetter(Animation::delay),
                Codec.FLOAT.optionalFieldOf("fade_in", DEFAULT.fadeIn).forGetter(Animation::fadeIn),
                Codec.FLOAT.optionalFieldOf("slide_x", DEFAULT.slideX).forGetter(Animation::slideX),
                Codec.FLOAT.optionalFieldOf("slide_y", DEFAULT.slideY).forGetter(Animation::slideY),
                Codec.FLOAT.optionalFieldOf("bob", DEFAULT.bob).forGetter(Animation::bob))
                .apply(i, Animation::new));
    }
}
