package jp.revati.skyabyss.item.extra;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import jp.revati.skyabyss.rpg.skill.Skill;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.RandomSource;

// 技の修飾ルーン一枚の中身 (品のデータ skyabyss:skill_rune)。1 つの技に 1 枚。
//  cooldown=false: その技の力 (傷・支えの量) に ×(milli/1000) を掛ける。1.10〜1.30 (刻み 0.05)
//  cooldown=true:  その技の待ちに ×(milli/1000) を掛ける。0.80〜0.90
public record RuneData(String skill, boolean cooldown, int milli) {
    public static final int[] POWER_STEPS = {1100, 1150, 1200, 1250, 1300};
    public static final int[] COOLDOWN_STEPS = {900, 850, 800};
    // 力の修飾を出す確率 (残りは待ち)
    public static final float POWER_CHANCE = 0.65f;

    public static final Codec<RuneData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("skill").forGetter(RuneData::skill),
            Codec.BOOL.optionalFieldOf("cooldown", false).forGetter(RuneData::cooldown),
            Codec.INT.fieldOf("milli").forGetter(RuneData::milli)).apply(i, RuneData::new));
    public static final StreamCodec<ByteBuf, RuneData> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    // 修飾ルーンのある技 (使う技だけ。パッシブ・要は倍率の意味がない)
    public static List<Skill> skills() {
        List<Skill> out = new ArrayList<>();
        for (Skill s : Skill.values()) {
            if (s.kind == Skill.Kind.ACTIVE && s.tree != jp.revati.skyabyss.rpg.skill.SkillTree.TRAVELER && !s.tree.sealed) {
                out.add(s);
            }
        }
        return out;
    }

    public static RuneData roll(RandomSource random) {
        List<Skill> list = skills();
        return roll(list.get(random.nextInt(list.size())), random);
    }

    public static RuneData roll(Skill skill, RandomSource random) {
        if (random.nextFloat() < POWER_CHANCE) {
            return new RuneData(skill.id, false, POWER_STEPS[random.nextInt(POWER_STEPS.length)]);
        }
        return new RuneData(skill.id, true, COOLDOWN_STEPS[random.nextInt(COOLDOWN_STEPS.length)]);
    }

    public Gem toGem() {
        return new Gem(cooldown ? Gem.RUNE_COOLDOWN : Gem.RUNE_POWER, skill, milli);
    }

    public static RuneData fromGem(Gem g) {
        return new RuneData(g.arg(), Gem.RUNE_COOLDOWN.equals(g.kind()), g.milli());
    }

    public boolean valid() {
        return Skill.byId(skill) != null && milli > 0;
    }
}
