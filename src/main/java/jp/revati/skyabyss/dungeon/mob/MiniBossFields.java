package jp.revati.skyabyss.dungeon.mob;

import java.util.Map;
import java.util.WeakHashMap;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.combat.BossCue;
import jp.revati.skyabyss.combat.FieldHazards;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

// 小ダンジョンの主に、後半の場の技 (combat/FieldHazards) を重ねる (2026-11-08 の声「ミニボス手抜きだから、攻撃要素増やして」)。
// 地下のダンジョンの写し (DungeonWorld) の中の、番兵長・潮の番人・羽守・焼けた番人・古い灰被りだけ。体力が HALF を切ると GAP ごとに出す。
// 主の体のクラスはそれぞれ違うので、体には手を入れず、ここで tick ごとに見る
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class MiniBossFields {
    public static final float HALF = 0.6f;
    public static final int GAP = 110;
    static final Map<String, BlockState> LOOKS = Map.of(
            "elder_sentry", Blocks.BONE_BLOCK.defaultBlockState(),
            "shore_warden", Blocks.PRISMARINE.defaultBlockState(),
            "feather_warden", Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState(),
            "scorched_warden", Blocks.BLACKSTONE.defaultBlockState(),
            "elder_thrall", Blocks.MAGMA_BLOCK.defaultBlockState());
    private static final Map<Mob, FieldHazards> FIELDS = new WeakHashMap<>();

    private MiniBossFields() {
    }

    @SubscribeEvent
    static void onTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level) || level.dimension() != jp.revati.skyabyss.dungeon.DungeonWorld.LEVEL) {
            return;
        }
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath();
        BlockState look = LOOKS.get(id);
        if (look == null || !mob.isAlive()) {
            return;
        }
        FieldHazards f = FIELDS.computeIfAbsent(mob, m -> of(m, id, look));
        f.tick(mob.getHealth() < mob.getMaxHealth() * HALF, GAP);
    }

    static FieldHazards of(Mob mob, String id, BlockState look) {
        return new FieldHazards(mob, () -> look, (attack, ticks) -> BossCue.send(mob, id, attack, ticks),
                () -> (float) Math.max(6, mob.getAttributeValue(Attributes.ATTACK_DAMAGE)),
                () -> mob.level().damageSources().mobAttack(mob),
                p -> !p.isSpectator() && !p.isCreative() && jp.revati.skyabyss.titan.group.GroupOwned.visibleTo(mob, p));
    }

    // 自己テスト用
    public static FieldHazards fieldOf(Mob mob) {
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath();
        return FIELDS.computeIfAbsent(mob, m -> of(m, id, LOOKS.getOrDefault(id, Blocks.STONE.defaultBlockState())));
    }
}
