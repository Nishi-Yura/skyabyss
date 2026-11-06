package jp.revati.skyabyss.combat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.skill.Job;
import jp.revati.skyabyss.rpg.skill.SkillActions;
import jp.revati.skyabyss.rpg.skill.SkillManager;
import jp.revati.skyabyss.util.PlayerMaps;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 魔法使いの通常攻撃の魔弾 (2026-11-06 の声「魔法使いが弱すぎるので、通常攻撃もある程度、遠距離攻撃出来てもいいのでは」、
// 2026-11-07 の声「少しだけマナ減ってもいいかも。突貫工事すぎるので、演出もこったり、作りもきちんとしてほしい。
// 今連打で攻撃いれれちゃうし、ジャンプで攻撃売ったらクリティカルでちゃう。即着も強すぎるかな」)。
// 杖 (職が魔法使いの武器) を持って、手の届く敵のいない所を左クリックすると、杖の先から星の光の弾が飛ぶ:
//   - 溜め: 振りの溜めが READY (9 割) 以上のときだけ撃てる。足りなければ撃たない (連打では出ない)。撃つと溜めは空になる
//   - マナ: 一発 COST_SHARE (最大マナの 4%。最低 1) を使う。足りなければ撃たない (MANA_HINT_GAP ごとに一度だけ知らせる)
//   - 飛ぶ: SPEED ブロック / tick で RANGE まで (着くまで少しかかる)。壁で消える。最初に触れた敵に当たる (当たりの太さ FAT)
//   - 傷: 当たった時に、撃った時の溜めのまま近接の一撃と同じ傷 (combat/MeleeHits を通す: 同じ敵を殴れる間・武器の効き目)。
//         跳んで撃っても会心にならない (落ちている間の会心はここでは起こさない)。撃った時と持ち物が変わっていたら当たらない
//   - 演出: 撃つ = 杖の先 (目から 1.3 先) に小さな光の輪と火花・澄んだ鐘の音。飛ぶ = 粉の光の芯と回る輪・後ろに金の粉の尾 (離れてから光の粒)。
//            当たる = 六つの方向へ光の筋と火花が散る星形と砕ける音。届かず消える = 小さくほどける。一人称で画面を覆わないよう、大きな粒は目の前に出さない
// 手元は client/weapon/StaffBoltClient が左クリックを見て、この合図 (Cast) を送る (溜めが足りるときだけ)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class StaffBolts {
    public static final double RANGE = 14.0;
    public static final double SPEED = 1.1;
    public static final int LIFE = (int) Math.ceil(RANGE / SPEED);
    public static final float READY = 0.9f;
    public static final double COST_SHARE = 0.04;
    // 続けて撃てる間 (tick)。溜めの決まりとは別の、合図の連打よけ
    static final int MIN_GAP = 4;
    public static final int MIN_GAP_CLIENT = MIN_GAP;
    static final int MANA_HINT_GAP = 60;
    // 当たりの太さ
    static final double FAT = 0.35;
    // 色: 星の芯 (青白) と尾の粉 (金)
    static final DustParticleOptions HALO = new DustParticleOptions(new Vector3f(0.70f, 0.86f, 1.00f), 0.7f);
    static final DustParticleOptions TAIL = new DustParticleOptions(new Vector3f(1.00f, 0.84f, 0.42f), 0.55f);
    static final DustParticleOptions RING = new DustParticleOptions(new Vector3f(0.82f, 0.92f, 1.00f), 0.6f);
    static final DustParticleOptions CORE = new DustParticleOptions(new Vector3f(0.95f, 0.98f, 1.00f), 1.0f);

    private static final Map<UUID, Long> LAST = PlayerMaps.track(new HashMap<>());
    private static final Map<UUID, Long> MANA_HINT = PlayerMaps.track(new HashMap<>());
    private static final List<Bolt> BOLTS = new ArrayList<>();

    // 飛んでいる魔弾
    static final class Bolt {
        final ServerPlayer owner;
        final ServerLevel level;
        final Vec3 dir;
        final int ticker;
        final Item item;
        Vec3 pos;
        int age;
        @Nullable
        Entity hit;
        boolean done;

        Bolt(ServerPlayer owner, Vec3 pos, Vec3 dir, int ticker, Item item) {
            this.owner = owner;
            this.level = owner.serverLevel();
            this.pos = pos;
            this.dir = dir;
            this.ticker = ticker;
            this.item = item;
        }
    }

    private StaffBolts() {
    }

    // 人 → サーバー: 魔弾を撃つ
    public record Cast() implements CustomPacketPayload {
        public static final Type<Cast> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "staff_bolt"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Cast> STREAM_CODEC = StreamCodec.unit(new Cast());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("staff-1").playToServer(Cast.TYPE, Cast.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer p) {
                cast(p);
            }
        }));
    }

    // 一発の重さ (マナ)
    public static double cost(ServerPlayer p) {
        return Math.max(1.0, Math.round(SkillManager.maxMana(p) * COST_SHARE));
    }

    // 撃つ。撃てたら飛んでいる弾、撃てなければ null
    @Nullable
    static Bolt cast(ServerPlayer p) {
        if (!p.isAlive() || p.isSpectator() || Job.of(p.getMainHandItem()) != Job.MAGE) {
            return null;
        }
        long now = p.level().getGameTime();
        Long last = LAST.get(p.getUUID());
        if (last != null && now - last < MIN_GAP) {
            return null;
        }
        // 溜めが足りなければ撃たない (連打では出ない)
        if (p.getAttackStrengthScale(0.5f) < READY) {
            return null;
        }
        double cost = cost(p);
        if (SkillManager.get(p).mana < cost) {
            Long h = MANA_HINT.get(p.getUUID());
            if (h == null || now - h >= MANA_HINT_GAP) {
                MANA_HINT.put(p.getUUID(), now);
                jp.revati.skyabyss.notice.Notice.hint(p, "message.skyabyss.staff_bolt.no_mana");
            }
            return null;
        }
        LAST.put(p.getUUID(), now);
        SkillManager.setMana(p, SkillManager.get(p).mana - cost);
        Vec3 look = p.getViewVector(1f);
        Vec3 tip = tip(p, look);
        Bolt b = new Bolt(p, tip, look, p.attackStrengthTicker, p.getMainHandItem().getItem());
        BOLTS.add(b);
        castFx(b.level, tip, look);
        // 撃つと溜めは空になる (振りと同じ)
        p.resetAttackStrengthTicker();
        return b;
    }

    // 杖の先 (目の前、利き手の側へ少し、少し下)
    static Vec3 tip(ServerPlayer p, Vec3 look) {
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : right.normalize();
        double side = p.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? 0.32 : -0.32;
        // 一人称で目の前に大きく出ないよう、目から 1.3 ブロック先から飛ばす
        return p.getEyePosition().add(look.scale(1.3)).add(right.scale(side)).add(0, -0.25, 0);
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        if (!BOLTS.isEmpty()) {
            tickAll(event.getServer());
        }
    }

    static void tickAll(MinecraftServer server) {
        for (Iterator<Bolt> it = BOLTS.iterator(); it.hasNext();) {
            Bolt b = it.next();
            step(b);
            if (b.done) {
                it.remove();
            }
        }
    }

    // 一 tick 進める (二つの小さな歩みで、壁と敵を見る)
    static void step(Bolt b) {
        ServerPlayer owner = b.owner;
        if (owner.isRemoved() || owner.level() != b.level) {
            b.done = true;
            return;
        }
        for (int half = 0; half < 2 && !b.done; half++) {
            Vec3 next = b.pos.add(b.dir.scale(SPEED * 0.5));
            HitResult wall = b.level.clip(new ClipContext(b.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
            Vec3 to = wall.getType() == HitResult.Type.MISS ? next : wall.getLocation();
            LivingEntity foe = firstFoe(b, b.pos, to);
            if (foe != null) {
                Vec3 at = foe.getBoundingBox().clip(b.pos, to).orElse(foe.getBoundingBox().getCenter());
                b.pos = at;
                strike(b, foe);
                impactFx(b.level, at, b.dir);
                b.done = true;
                return;
            }
            flightFx(b.level, b.pos, to, b.dir, b.age * 2 + half);
            b.pos = to;
            if (wall.getType() != HitResult.Type.MISS) {
                fizzleFx(b.level, b.pos, true);
                b.done = true;
                return;
            }
        }
        b.age++;
        if (b.age >= LIFE) {
            fizzleFx(b.level, b.pos, false);
            b.done = true;
        }
    }

    @Nullable
    static LivingEntity firstFoe(Bolt b, Vec3 from, Vec3 to) {
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : b.level.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(FAT + 1.0), e -> SkillActions.isFoe(b.owner, e))) {
            var hit = e.getBoundingBox().inflate(FAT).clip(from, to);
            if (hit.isPresent()) {
                double d = hit.get().distanceToSqr(from);
                if (d < bd) {
                    bd = d;
                    best = e;
                }
            }
        }
        return best;
    }

    // 当てる: 撃った時の溜めで、近接の一撃と同じ傷。会心は起こさない。持ち物が変わっていたら当たらない
    static void strike(Bolt b, LivingEntity foe) {
        ServerPlayer p = b.owner;
        b.hit = foe;
        if (!p.isAlive() || p.getMainHandItem().getItem() != b.item) {
            return;
        }
        int ticker = p.attackStrengthTicker;
        float fall = p.fallDistance;
        p.attackStrengthTicker = b.ticker;
        // 落ちている間の会心 (Player.attack が見る fallDistance) をこの一撃では起こさない
        p.fallDistance = 0f;
        try {
            jp.revati.skyabyss.weapon.WeaponStrikes.attackNow(p, foe);
        } finally {
            p.attackStrengthTicker = ticker;
            p.fallDistance = fall;
        }
    }

    // ---------------------------------------------------------------- 演出
    static void castFx(ServerLevel level, Vec3 at, Vec3 dir) {
        // 杖の先に小さな光の輪と火花 (大きな光の粒は目の前で画面を覆うので使わない)
        ring(level, at, dir, 0.22, 8, RING, 0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 3, 0.06, 0.06, 0.06, 0.03);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.55f, 1.7f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.5f);
    }

    static void flightFx(ServerLevel level, Vec3 from, Vec3 to, Vec3 dir, int beat) {
        Vec3 c = from.add(to).scale(0.5);
        // 芯 (粉の光。少し離れてから、光の粒を一つ足す)
        level.sendParticles(CORE, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        level.sendParticles(HALO, to.x, to.y, to.z, 1, 0, 0, 0, 0);
        if (beat >= 3) {
            level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        }
        // 回る輪 (四つの点。歩みごとに 37 度ずつ回る)
        ring(level, c, dir, 0.18, 4, RING, beat * 37);
        // 尾の粉 (後ろへ少しこぼれる)
        Vec3 back = from.subtract(dir.scale(0.25));
        level.sendParticles(TAIL, back.x, back.y, back.z, 2, 0.05, 0.05, 0.05, 0);
        if (beat % 3 == 0) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, back.x, back.y, back.z, 1, 0.04, 0.04, 0.04, 0.02);
        }
    }

    static void impactFx(ServerLevel level, Vec3 at, Vec3 dir) {
        // 星形: 弾の向きに垂直な面の六方向へ火花が散り、真ん中に光が一つ灯る (光の粒は飛ばさない: 人の方へ流れて画面を覆った)
        Vec3[] axes = basis(dir);
        for (int k = 0; k < 6; k++) {
            double a = Math.toRadians(k * 60);
            Vec3 d = axes[0].scale(Math.cos(a)).add(axes[1].scale(Math.sin(a)));
            for (int j = 1; j <= 3; j++) {
                Vec3 q = at.add(d.scale(0.16 * j));
                level.sendParticles(j == 3 ? TAIL : RING, q.x, q.y, q.z, 1, 0, 0, 0, 0);
            }
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 0, d.x, d.y, d.z, 0.35);
        }
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        ring(level, at, dir, 0.4, 12, RING, 0);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, at.x, at.y, at.z, 6, 0.15, 0.15, 0.15, 0.12);
        level.sendParticles(TAIL, at.x, at.y, at.z, 8, 0.25, 0.25, 0.25, 0);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.8f, 1.45f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6f, 2.0f);
    }

    static void fizzleFx(ServerLevel level, Vec3 at, boolean wall) {
        level.sendParticles(HALO, at.x, at.y, at.z, 5, 0.12, 0.12, 0.12, 0);
        level.sendParticles(RING, at.x, at.y, at.z, 3, 0.08, 0.08, 0.08, 0);
        if (wall) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 4, 0.1, 0.1, 0.1, 0.1);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_STEP, SoundSource.PLAYERS, 0.6f, 1.6f);
        }
    }

    // 向き dir に垂直な面に、半径 r の点を n 個 (phase 度ずらす)
    static void ring(ServerLevel level, Vec3 c, Vec3 dir, double r, int n, DustParticleOptions dust, double phase) {
        Vec3[] axes = basis(dir);
        for (int k = 0; k < n; k++) {
            double a = Math.toRadians(phase + k * 360.0 / n);
            Vec3 p = c.add(axes[0].scale(Math.cos(a) * r)).add(axes[1].scale(Math.sin(a) * r));
            level.sendParticles(dust, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    // dir に垂直な二本の軸
    static Vec3[] basis(Vec3 dir) {
        Vec3 up = Math.abs(dir.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = dir.cross(up).normalize();
        Vec3 v = u.cross(dir).normalize();
        return new Vec3[] {u, v};
    }

    // ---------------------------------------------------------------- 自己テスト用
    static void forgetForTest(ServerPlayer p) {
        LAST.remove(p.getUUID());
    }

    // 撃って、当たるか消えるまで進める。当たった敵 (外れたら null。撃てなければ null で fired = false)
    static Entity castAndFly(ServerPlayer p, boolean[] fired) {
        Bolt b = cast(p);
        fired[0] = b != null;
        if (b == null) {
            return null;
        }
        BOLTS.remove(b);
        for (int i = 0; i <= LIFE + 1 && !b.done; i++) {
            step(b);
        }
        return b.hit;
    }

    // 当たるまでの tick (撃ってから。距離 d のとき)
    static int ticksTo(double d) {
        return (int) Math.ceil(d / SPEED);
    }
}
