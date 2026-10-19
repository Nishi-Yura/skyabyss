package jp.revati.skyabyss.item.extra;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.registry.ModItems;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

// 自由度の品の落ち方 (docs/design/03 の 2.1・31 の A06・A07)。装備そのものの確率 (図鑑・天井) は変えない。
//  心臓 (一人ずつ別々に抽選): 巨神の属性の欠片 55% (1〜2 個)、岩鯨はさらに骨の欠片 25%、技の修飾ルーン 10%
//  試練 (済ませた人ごと): ルーン 初めて 12%・以後 4%・極の修飾は 2 倍。欠片は初めてのときだけ 35%
public final class GearFreedomLoot {
    public static final float HEART_SHARD_CHANCE = 0.55f;
    public static final float HEART_BONE_CHANCE = 0.25f;
    public static final float HEART_RUNE_CHANCE = 0.10f;
    public static final float TRIAL_RUNE_FIRST = 0.12f;
    public static final float TRIAL_RUNE_REPEAT = 0.04f;
    public static final float TRIAL_SHARD_FIRST = 0.35f;

    private GearFreedomLoot() {
    }

    // 巨神の欠片の属性 (岩鯨 = 岩、雷翼 = 風、潮殻 = 潮、余燼 = 灰)
    public static Element elementOf(String titan) {
        return switch (titan) {
            case "thunder_wing" -> Element.WIND;
            case "tide_shell" -> Element.TIDE;
            case "ember" -> Element.ASH;
            default -> Element.STONE;
        };
    }

    // 心臓を倒した一人ぶん
    public static List<ItemStack> heartDrops(String titan, RandomSource random) {
        List<ItemStack> out = new ArrayList<>();
        if (random.nextFloat() < HEART_SHARD_CHANCE) {
            out.add(new ItemStack(ModItems.socketShard(elementOf(titan)).get(), 1 + random.nextInt(2)));
        }
        if ("stone_whale".equals(titan) && random.nextFloat() < HEART_BONE_CHANCE) {
            out.add(new ItemStack(ModItems.SOCKET_SHARD_BONE.get()));
        }
        if (random.nextFloat() < HEART_RUNE_CHANCE) {
            out.add(GearFreedomItems.rune(RuneData.roll(random)));
        }
        return out;
    }

    // 試練を済ませた一人ぶん (出なければ空)
    public static List<ItemStack> trialDrops(String titan, boolean first, boolean extreme, RandomSource random) {
        List<ItemStack> out = new ArrayList<>();
        float rune = first ? TRIAL_RUNE_FIRST : TRIAL_RUNE_REPEAT;
        if (extreme) {
            rune *= 2;
        }
        if (random.nextFloat() < rune) {
            out.add(GearFreedomItems.rune(RuneData.roll(random)));
        }
        if (first && random.nextFloat() < TRIAL_SHARD_FIRST) {
            out.add(new ItemStack(ModItems.socketShard(elementOf(titan)).get()));
        }
        return out;
    }
}
