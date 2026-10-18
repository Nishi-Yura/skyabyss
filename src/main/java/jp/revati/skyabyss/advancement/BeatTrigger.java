package jp.revati.skyabyss.advancement;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

// 物語の一区切り (beat) を伝える、この Mod 独自の進捗の条件。
// 進捗の JSON では {"trigger": "skyabyss:beat", "conditions": {"event": "<名前>"}} と書く。名前は Beats.fire で送るものと同じ。
// 名前の付け方は Beats の先頭の表を見る
public class BeatTrigger extends SimpleCriterionTrigger<BeatTrigger.Instance> {
    @Override
    public Codec<Instance> codec() {
        return Instance.CODEC;
    }

    public void trigger(ServerPlayer player, String event) {
        trigger(player, instance -> instance.event.equals(event));
    }

    public record Instance(Optional<ContextAwarePredicate> player, String event) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                Codec.STRING.fieldOf("event").forGetter(Instance::event)).apply(i, Instance::new));
    }
}
