package jp.revati.skyabyss.trial.raid.mid;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidRoom;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 闘の試練の中で中ボスが現れる部屋 (docs/design/32 の 9.5、00 の 277)。闘の部屋 (inner) を包み、
//   1. 波の段: 元の闘の部屋のまま (部屋の仕掛けを使う波)
//   2. 中ボスの段: 元の部屋を越えたら、部屋のその時の姿を控え、中ボスの間の仕掛け (砂の床と岩の小島・避雷の柱と栓・焼き入れの槽と炉の扉) を
//      部屋に建てて (MidRoom.fitInto)、中ボスを部屋に出す。中ボスは段なし (怒りの段に入らない) で体力 7 割
//   中ボスを倒すと越える。中ボスの段で全員が倒れたら、控えから部屋を戻して波の段から最初から
// 中ボスの技・報い (中ボスの落とし物) は中ボスの間 (MidRoom) のまま。順路の報い (RaidRewards) は闘の部屋と中ボスの間の両方 (RaidRun.complete)
public final class FightBossRoom extends RaidRoom {
    // 中ボスの体力の割合
    public static final double HEALTH = 0.7;
    // 段なし (この印の中ボスは怒りの段に入らない。WhaleboneCaptain・AshSmith が見る)
    public static final String SINGLE_STAGE = "skyabyss_single_stage";
    static final ResourceLocation HEALTH_ID = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "fight_room_boss_health");

    private final String titan;
    private final RaidRoom inner;
    @Nullable
    private MidRoom mid;
    private boolean bossStage;
    // 中ボスの段に入る前の部屋の姿 (位置 → ブロック)
    private final Map<BlockPos, BlockState> before = new HashMap<>();

    public FightBossRoom(String titan, RaidRoom inner) {
        this.titan = titan;
        this.inner = inner;
    }

    public RaidRoom inner() {
        return inner;
    }

    // 包んだ闘の部屋なら中の部屋 (自己テスト・撮影が元の部屋の型で扱うため)
    public static RaidRoom unwrap(RaidRoom r) {
        return r instanceof FightBossRoom f ? f.inner : r;
    }

    // 自己テスト用: 波を越えたことにして中ボスの段へ
    public void devBeginBoss(RaidCtx ctx) {
        if (!bossStage) {
            beginBoss(ctx);
        }
    }

    @Nullable
    public MidRoom midRoom() {
        return mid;
    }

    // 自己テスト用: 中ボスの段なら中ボスを倒す (by がいればその人の一撃として)。中ボスの段だったら true
    public static boolean finishBoss(RaidRoom r, @Nullable ServerPlayer by) {
        if (!(r instanceof FightBossRoom f) || !f.bossStage || f.mid == null || f.mid.boss == null || !f.mid.boss.isAlive()) {
            return false;
        }
        Mob b = f.mid.boss;
        if (by != null) {
            b.hurt(by.damageSources().playerAttack(by), b.getMaxHealth() * 10);
        }
        if (b.isAlive()) {
            b.kill();
        }
        return true;
    }

    public boolean bossStage() {
        return bossStage;
    }

    @Nullable
    public Mob boss() {
        return mid == null ? null : mid.boss;
    }

    @Override
    public void build(RaidCtx ctx) {
        inner.build(ctx);
    }

    @Override
    public void start(RaidCtx ctx) {
        bossStage = false;
        inner.start(ctx);
    }

    @Override
    public void tick(RaidCtx ctx) {
        if (!bossStage) {
            inner.tick(ctx);
            if (inner.isComplete()) {
                beginBoss(ctx);
            }
            return;
        }
        if (mid != null) {
            mid.tick(ctx);
            keepInside(ctx);
        }
    }

    // 波を越えた: 部屋を控えて、中ボスの仕掛けを建て、中ボスを出す
    void beginBoss(RaidCtx ctx) {
        bossStage = true;
        ctx.clearMobs();
        snapshot(ctx);
        mid = MidRooms.create(titan) instanceof MidRoom m ? m : null;
        if (mid == null) {
            SkyAbyss.LOGGER.warn("Fight room {}: no mid-boss for {}", ctx.roomId(), titan);
            return;
        }
        mid.inRoom = true;
        mid.fitInto(ctx);
        mid.start(ctx);
        tame(mid.boss);
        keepInside(ctx);
        SkyAbyss.LOGGER.info("Fight room {}: waves cleared, the mid-boss appears in the room", ctx.roomId());
    }

    // 体力 7 割と段なしの印 (湧かせ直したときも)
    static void tame(@Nullable Mob boss) {
        if (boss == null || boss.getTags().contains(SINGLE_STAGE)) {
            return;
        }
        boss.addTag(SINGLE_STAGE);
        var hp = boss.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null && !hp.hasModifier(HEALTH_ID)) {
            hp.addPermanentModifier(new AttributeModifier(HEALTH_ID, HEALTH - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            boss.setHealth(boss.getMaxHealth());
        }
    }

    // 中ボスの間の大きさで湧かせた仲間が部屋の外 (壁の中) にいたら、中ボスのそばへ寄せる
    private void keepInside(RaidCtx ctx) {
        if (mid == null) {
            return;
        }
        tame(mid.boss);
        AABB in = ctx.cell().inner().inflate(-0.5, 2, -0.5);
        Vec3 c = Vec3.atBottomCenterOf(ctx.cell().floor(0, 0));
        for (Mob m : ctx.aliveMobs()) {
            if (!in.contains(m.position())) {
                m.moveTo(c.x + (ctx.level().random.nextDouble() - 0.5) * 4, c.y, c.z + (ctx.level().random.nextDouble() - 0.5) * 4);
            }
        }
    }

    private void snapshot(RaidCtx ctx) {
        before.clear();
        AABB b = ctx.cell().bounds();
        for (int x = (int) Math.floor(b.minX); x < Math.ceil(b.maxX); x++) {
            for (int y = (int) Math.floor(b.minY); y < Math.ceil(b.maxY); y++) {
                for (int z = (int) Math.floor(b.minZ); z < Math.ceil(b.maxZ); z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    before.put(p, ctx.level().getBlockState(p));
                }
            }
        }
    }

    private void restore(RaidCtx ctx) {
        for (Map.Entry<BlockPos, BlockState> e : before.entrySet()) {
            if (ctx.level().getBlockState(e.getKey()) != e.getValue()) {
                ctx.level().setBlock(e.getKey(), e.getValue(), 2);
            }
        }
        before.clear();
    }

    @Override
    public boolean isComplete() {
        return bossStage && mid != null && mid.isComplete();
    }

    @Override
    public void reset(RaidCtx ctx) {
        if (mid != null) {
            mid.reset(ctx);
            mid.end(ctx);
            mid = null;
        }
        if (!before.isEmpty()) {
            restore(ctx);
        }
        bossStage = false;
        inner.reset(ctx);
    }

    @Override
    public void end(RaidCtx ctx) {
        if (mid != null) {
            mid.end(ctx);
        }
        inner.end(ctx);
    }

    @Override
    public boolean onUse(RaidCtx ctx, ServerPlayer player, BlockPos pos, boolean attack) {
        return bossStage ? mid != null && mid.onUse(ctx, player, pos, attack) : inner.onUse(ctx, player, pos, attack);
    }

    // ---------------------------------------------------------------- 目的の帯 (段ごとに元の部屋か中ボスの間のもの)
    private RaidRoom shown() {
        return bossStage && mid != null ? mid : inner;
    }

    @Override
    public String objectiveKey() {
        return shown().objectiveKey();
    }

    @Override
    public Object[] objectiveArgs() {
        return shown().objectiveArgs();
    }

    @Override
    public int counter() {
        return shown().counter();
    }

    @Override
    public int counterMax() {
        return shown().counterMax();
    }

    @Override
    public String secondaryKey() {
        return shown().secondaryKey();
    }

    @Override
    public int secondary() {
        return shown().secondary();
    }

    @Override
    public int secondaryMax() {
        return shown().secondaryMax();
    }

    @Override
    public int timerTicks() {
        return shown().timerTicks();
    }

    @Override
    @Nullable
    public BlockPos guideTarget(RaidCtx ctx) {
        return bossStage ? null : inner.guideTarget(ctx);
    }
}
