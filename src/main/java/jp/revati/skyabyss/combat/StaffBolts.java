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
//   - 模型: 段の形 (prop_sk_bolt_<段>、combat/prop/AttackProps.fly) が弾と一緒に飛ぶ。敵に当たったら消え、壁・届く所では着いて消える
//   - 演出: 撃つ = 杖の先 (目から 1.3 先) に小さな光の輪と火花・澄んだ鐘の音。飛ぶ = 粉の光の芯と回る輪・後ろに金の粉の尾 (離れてから光の粒)。
//            当たる = 六つの方向へ光の筋と火花が散る星形と砕ける音。届かず消える = 小さくほどける。一人称で画面を覆わないよう、大きな粒は目の前に出さない
// 見た目は少しずつ変わる (2026-11-11 の声「魔法使いの通常の攻撃の見た目も少しづつ変わった方がいいと思う。あきない」):
//   - 段 (レベル 10 ごと。Look): 星の粒 → 双子星 (二つの芯が螺旋に回る) → 彗星 (大きな芯と長い尾) → 三日月 (回る弧) → 星座 (芯と三つの衛星を線で結ぶ)
//   - 一発ごと (三発で一巡): 当たりの形が 六芒の星 → 十字 → 渦 と替わり、撃つ音の高さも替わる
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

    // 段ごとの色 (芯・暈・尾・輪)
    record Look(DustParticleOptions core, DustParticleOptions halo, DustParticleOptions tail, DustParticleOptions ring) {
    }

    static DustParticleOptions dust(float r, float g, float b, float size) {
        return new DustParticleOptions(new Vector3f(r, g, b), size);
    }

    static final Look[] LOOKS = {
        // 0 星の粒 (Lv 1〜9): 青白の芯と金の尾
        new Look(CORE, HALO, TAIL, RING),
        // 1 双子星 (Lv 10〜19): 水色と白の二つの芯、銀青の尾
        new Look(dust(0.55f, 0.95f, 1.00f, 0.9f), dust(1.00f, 1.00f, 1.00f, 0.8f), dust(0.62f, 0.74f, 0.95f, 0.5f), dust(0.70f, 0.95f, 1.00f, 0.6f)),
        // 2 彗星 (Lv 20〜29): 大きな薄紫の芯と、藤色から青へ長く引く尾
        new Look(dust(0.92f, 0.86f, 1.00f, 1.5f), dust(0.72f, 0.60f, 1.00f, 0.9f), dust(0.55f, 0.55f, 1.00f, 0.7f), dust(0.80f, 0.70f, 1.00f, 0.6f)),
        // 3 三日月 (Lv 30〜39): 白金の弧と琥珀の尾
        new Look(dust(1.00f, 0.97f, 0.85f, 1.1f), dust(1.00f, 0.90f, 0.55f, 0.8f), dust(1.00f, 0.66f, 0.30f, 0.6f), dust(1.00f, 0.92f, 0.62f, 0.7f)),
        // 4 星座 (Lv 40〜): 深い青の芯、白い衛星、金の線
        new Look(dust(0.35f, 0.50f, 1.00f, 1.3f), dust(1.00f, 1.00f, 1.00f, 0.8f), dust(1.00f, 0.84f, 0.42f, 0.6f), dust(0.85f, 0.90f, 1.00f, 0.55f)),
    };

    // レベルの段 (10 ごと。0〜4)
    public static int tier(int level) {
        return Math.max(0, Math.min(LOOKS.length - 1, level / 10));
    }

    // 一発ごとの番 (三発で一巡)
    private static final Map<UUID, Integer> SHOT = PlayerMaps.track(new HashMap<>());
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
        final int tier;
        final int shot;
        Vec3 pos;
        int age;
        @Nullable
        Entity hit;
        // 飛ぶ模型 (prop_sk_bolt_<段>)。当たって止まったら消す
        @Nullable
        Entity model;
        boolean done;

        Bolt(ServerPlayer owner, Vec3 pos, Vec3 dir, int ticker, Item item, int tier, int shot) {
            this.tier = tier;
            this.shot = shot;
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
        // 魔法使いでなければ杖は使えない (2026-11-11 の声「杖が他のジョブでも使えてしまう」。rpg/skill/JobWeaponLock)
        if (jp.revati.skyabyss.rpg.skill.JobWeaponLock.wrongJob(p, p.getMainHandItem()) != null) {
            jp.revati.skyabyss.rpg.skill.JobWeaponLock.tell(p);
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
        int shot = SHOT.merge(p.getUUID(), 1, (a, one) -> (a + one) % 3);
        Bolt b = new Bolt(p, tip, look, p.attackStrengthTicker, p.getMainHandItem().getItem(), tier(jp.revati.skyabyss.rpg.Growth.get(p).level), shot);
        BOLTS.add(b);
        b.model = model(b);
        castFx(b.level, tip, look, LOOKS[b.tier], b.shot);
        // 撃つと溜めは空になる (振りと同じ)
        p.resetAttackStrengthTicker();
        return b;
    }

    // 飛ぶ模型の大きさ (芯は模型の 0.25 ブロックの高さ。描く側の傾きの軸)
    static final float MODEL_SIZE = 1.5f;

    // 飛ぶ模型 (段の形)。壁か届く所までまっすぐ、弾と同じ速さで飛ぶ。敵に当たって止まったら step が消す
    @Nullable
    static Entity model(Bolt b) {
        Vec3 end = b.pos.add(b.dir.scale(SPEED * LIFE));
        HitResult wall = b.level.clip(new ClipContext(b.pos, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, b.owner));
        Vec3 to = wall.getType() == HitResult.Type.MISS ? end : wall.getLocation();
        int flight = Math.max(1, (int) Math.round(to.distanceTo(b.pos) / SPEED));
        Vec3 lift = new Vec3(0, 0.25 * MODEL_SIZE, 0);
        return jp.revati.skyabyss.combat.prop.AttackProps.fly(b.level, "sk_bolt_" + b.tier, b.owner, b.pos.subtract(lift), to.subtract(lift), flight, MODEL_SIZE);
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
            if (b.model != null) {
                b.model.discard();
            }
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
                impactFx(b.level, at, b.dir, LOOKS[b.tier], b.tier, b.shot);
                jp.revati.skyabyss.rpg.skill.SkillProps.elementMark(owner, foe.position(), 0.7f, 10);
                if (b.model != null) {
                    b.model.discard();
                }
                b.done = true;
                return;
            }
            flightFx(b.level, b.pos, to, b.dir, b.age * 2 + half, LOOKS[b.tier], b.tier);
            b.pos = to;
            if (wall.getType() != HitResult.Type.MISS) {
                fizzleFx(b.level, b.pos, true, LOOKS[b.tier]);
                b.done = true;
                return;
            }
        }
        b.age++;
        if (b.age >= LIFE) {
            fizzleFx(b.level, b.pos, false, LOOKS[b.tier]);
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
    // 撃つ音の高さ (一発ごとに替わる)
    static final float[] PITCH = {1.5f, 1.62f, 1.38f};

    static void castFx(ServerLevel level, Vec3 at, Vec3 dir, Look look, int shot) {
        // 杖の先に小さな光の輪と火花 (大きな光の粒は目の前で画面を覆うので使わない)
        ring(level, at, dir, 0.22, 8, look.ring(), shot * 15);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 3, 0.06, 0.06, 0.06, 0.03);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.55f, PITCH[shot] + 0.2f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, PITCH[shot]);
    }

    static void flightFx(ServerLevel level, Vec3 from, Vec3 to, Vec3 dir, int beat, Look look, int tier) {
        Vec3 c = from.add(to).scale(0.5);
        Vec3[] axes = basis(dir);
        switch (tier) {
            case 1 -> {
                // 双子星: 二つの芯が向かい合って螺旋に回る
                double a = Math.toRadians(beat * 40);
                for (int k = 0; k < 2; k++) {
                    double t = a + k * Math.PI;
                    Vec3 q = c.add(axes[0].scale(Math.cos(t) * 0.17)).add(axes[1].scale(Math.sin(t) * 0.17));
                    level.sendParticles(k == 0 ? look.core() : look.halo(), q.x, q.y, q.z, 1, 0, 0, 0, 0);
                }
            }
            case 2 -> {
                // 彗星: 大きな芯と、後ろへ長く引く尾
                level.sendParticles(look.core(), c.x, c.y, c.z, 1, 0, 0, 0, 0);
                for (int k = 1; k <= 4; k++) {
                    Vec3 q = c.subtract(dir.scale(0.22 * k));
                    level.sendParticles(k <= 2 ? look.halo() : look.tail(), q.x, q.y, q.z, 1, 0.02 * k, 0.02 * k, 0.02 * k, 0);
                }
            }
            case 3 -> {
                // 三日月: 160 度の弧がゆっくり回る
                level.sendParticles(look.halo(), c.x, c.y, c.z, 1, 0, 0, 0, 0);
                double base = Math.toRadians(beat * 12);
                for (int k = 0; k < 6; k++) {
                    double t = base + Math.toRadians(-80 + k * 32);
                    double r = 0.26 - Math.abs(k - 2.5) * 0.015;
                    Vec3 q = c.add(axes[0].scale(Math.cos(t) * r)).add(axes[1].scale(Math.sin(t) * r));
                    level.sendParticles(look.core(), q.x, q.y, q.z, 1, 0, 0, 0, 0);
                }
            }
            case 4 -> {
                // 星座: 芯と三つの衛星、芯から衛星へ細い線
                level.sendParticles(look.core(), c.x, c.y, c.z, 1, 0, 0, 0, 0);
                double base = Math.toRadians(beat * 25);
                for (int k = 0; k < 3; k++) {
                    double t = base + k * Math.PI * 2 / 3;
                    Vec3 d = axes[0].scale(Math.cos(t)).add(axes[1].scale(Math.sin(t)));
                    Vec3 q = c.add(d.scale(0.34));
                    level.sendParticles(look.halo(), q.x, q.y, q.z, 1, 0, 0, 0, 0);
                    if (beat % 2 == 0) {
                        Vec3 m = c.add(d.scale(0.17));
                        level.sendParticles(look.tail(), m.x, m.y, m.z, 1, 0, 0, 0, 0);
                    }
                }
            }
            default -> {
                // 星の粒: 粉の光の芯と回る輪 (四つの点。歩みごとに 37 度ずつ回る)
                level.sendParticles(look.core(), c.x, c.y, c.z, 1, 0, 0, 0, 0);
                level.sendParticles(look.halo(), to.x, to.y, to.z, 1, 0, 0, 0, 0);
                ring(level, c, dir, 0.18, 4, look.ring(), beat * 37);
            }
        }
        // 少し離れてから、光の粒を一つ足す
        if (beat >= 3) {
            level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        }
        // 尾の粉 (後ろへ少しこぼれる)
        Vec3 back = from.subtract(dir.scale(0.25));
        level.sendParticles(look.tail(), back.x, back.y, back.z, 2, 0.05, 0.05, 0.05, 0);
        if (beat % 3 == 0) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, back.x, back.y, back.z, 1, 0.04, 0.04, 0.04, 0.02);
        }
    }

    static void impactFx(ServerLevel level, Vec3 at, Vec3 dir, Look look, int tier, int shot) {
        // 一発ごとに形が替わる: 六芒の星 → 十字 → 渦 (光の粒は飛ばさない: 人の方へ流れて画面を覆った)
        Vec3[] axes = basis(dir);
        switch (shot) {
            case 1 -> {
                // 十字: 四方向へ長めの腕
                for (int k = 0; k < 4; k++) {
                    double a = Math.toRadians(45 + k * 90);
                    Vec3 d = axes[0].scale(Math.cos(a)).add(axes[1].scale(Math.sin(a)));
                    for (int j = 1; j <= 4; j++) {
                        Vec3 q = at.add(d.scale(0.15 * j));
                        level.sendParticles(j == 4 ? look.tail() : look.ring(), q.x, q.y, q.z, 1, 0, 0, 0, 0);
                    }
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 0, d.x, d.y, d.z, 0.4);
                }
            }
            case 2 -> {
                // 渦: 外へほどける二本の渦の腕
                for (int arm = 0; arm < 2; arm++) {
                    for (int j = 0; j < 7; j++) {
                        double a = Math.toRadians(arm * 180 + j * 38);
                        double r = 0.08 + j * 0.07;
                        Vec3 q = at.add(axes[0].scale(Math.cos(a) * r)).add(axes[1].scale(Math.sin(a) * r));
                        level.sendParticles(j >= 5 ? look.tail() : look.ring(), q.x, q.y, q.z, 1, 0, 0, 0, 0);
                    }
                }
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 4, 0.1, 0.1, 0.1, 0.15);
            }
            default -> {
                // 六芒の星: 弾の向きに垂直な面の六方向へ火花が散る
                for (int k = 0; k < 6; k++) {
                    double a = Math.toRadians(k * 60);
                    Vec3 d = axes[0].scale(Math.cos(a)).add(axes[1].scale(Math.sin(a)));
                    for (int j = 1; j <= 3; j++) {
                        Vec3 q = at.add(d.scale(0.16 * j));
                        level.sendParticles(j == 3 ? look.tail() : look.ring(), q.x, q.y, q.z, 1, 0, 0, 0, 0);
                    }
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 0, d.x, d.y, d.z, 0.35);
                }
            }
        }
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        ring(level, at, dir, 0.4, 12, look.ring(), shot * 15);
        // 星座の段は、外にもう一つ大きな輪
        if (tier >= 4) {
            ring(level, at, dir, 0.62, 16, look.halo(), 0);
        }
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, at.x, at.y, at.z, 6, 0.15, 0.15, 0.15, 0.12);
        level.sendParticles(look.tail(), at.x, at.y, at.z, 8, 0.25, 0.25, 0.25, 0);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.8f, PITCH[shot] - 0.05f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6f, 2.0f);
    }

    static void fizzleFx(ServerLevel level, Vec3 at, boolean wall, Look look) {
        level.sendParticles(look.halo(), at.x, at.y, at.z, 5, 0.12, 0.12, 0.12, 0);
        level.sendParticles(look.ring(), at.x, at.y, at.z, 3, 0.08, 0.08, 0.08, 0);
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
