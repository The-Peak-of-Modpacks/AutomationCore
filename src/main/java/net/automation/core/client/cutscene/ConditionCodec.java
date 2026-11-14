package net.automation.core.client.cutscene;

import net.automation.core.cutscene.spec.Condition;
import net.automation.core.cutscene.spec.SpecException;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;

import java.util.List;

public final class ConditionCodec {

    private ConditionCodec() {}

    public static final Codec<Condition> CODEC = Codec.PASSTHROUGH.comapFlatMap(dynamic -> {
        try {
            return DataResult.success(Condition.parse(dynamic.convert(JsonOps.INSTANCE).getValue()));
        } catch (SpecException e) {
            return DataResult.error(e::getMessage);
        }
    }, condition -> new Dynamic<>(JsonOps.INSTANCE, condition.json()));

    public static <T> Codec<List<T>> limited(Codec<T> element, int max, String what) {
        return element.listOf().flatXmap(
                list -> list.size() <= max ? DataResult.success(list) :
                        DataResult
                                .error(() -> "too many " + what + ": " + list.size() + ", and " + max + " is the most"),
                list -> list.size() <= max ? DataResult.success(list) :
                        DataResult.error(() -> "too many " + what));
    }
}
