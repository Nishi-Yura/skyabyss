package jp.revati.skyabyss.rpg.skill;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

// 技の変異 (A08)。1 つの技を巨神の素材で「形を変える」。技ごとに 1 つ、一度選んだら外せない (刻印とは別。刻印も付けられる)。
// 技を極めている (最大の段) ときだけ選べ、効くのも極めているあいだ。力・待ち・マナは元の技と同じで、形 (当たる範囲・動き・見た目) が変わる。
// 素材: その巨神の核 1 つ + 鱗・欠片 3 つ (持ち物から払う)。数式は MutationActions
public enum Mutation {
    CLEAVE_CLIFF("cleave_cliff", Skill.TITAN_CLEAVE, "stone_whale"),
    SWEEP_TAIL("sweep_tail", Skill.WHALE_SWEEP, "stone_whale"),
    REND_BLADE("rend_blade", Skill.WIND_REND, "thunder_wing"),
    RAIN_COMET("rain_comet", Skill.SKY_RAIN, "thunder_wing"),
    EBB_WHIRL("ebb_whirl", Skill.EBB_TIDE, "tide_shell"),
    LUNGE_SURGE("lunge_surge", Skill.LUNGE, "tide_shell"),
    HAMMER_METEOR("hammer_meteor", Skill.EMBER_HAMMER, "ember"),
    TRAP_MINE("trap_mine", Skill.BONE_TRAP, "ember");

    public static final int CORES = 1;
    public static final int SHARDS = 3;

    public final String id;
    public final Skill skill;
    // 素材の巨神 (品の id は <titan>_core・<titan>_shard)
    public final String titan;

    Mutation(String id, Skill skill, String titan) {
        this.id = id;
        this.skill = skill;
        this.titan = titan;
    }

    public String nameKey() {
        return "mutation.skyabyss." + id;
    }

    public String effectKey() {
        return "mutation.skyabyss." + id + ".effect";
    }

    public Item coreItem() {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, titan + "_core"));
    }

    public Item shardItem() {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, titan + "_shard"));
    }

    public static Mutation byId(String id) {
        for (Mutation m : values()) {
            if (m.id.equals(id)) {
                return m;
            }
        }
        return null;
    }

    public static Mutation of(Skill skill) {
        for (Mutation m : values()) {
            if (m.skill == skill) {
                return m;
            }
        }
        return null;
    }
}
