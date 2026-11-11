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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 呪術師の通常攻撃の呪印 (2026-11-11 の声「呪術師とかも、遠距離攻撃出来た方がいいんじゃない？弱いと思う。
// 例だけど、呪術師の基本攻撃は狙ったところから攻撃出るみたいなのにすればどう？魔法使いとの差別化」)。
// 魔法使いの魔弾 (StaffBolts) は杖の先から飛ぶ。呪術師は弾を飛ばさず、狙った所の足もとに呪印が浮き、少し遅れて噴き上がる:
//   - 狙い: 目から RANGE までの先。敵を指していればその足もと、地面を指していればそこ。空を指していれば DROP まで下の地面。何もなければ出ない
//   - 溜め・マナ・連打よけは魔弾と同じ (READY 以上・最大マナの 4%・MIN_GAP)。撃つと溜めは空になる
//   - 遅れ: DELAY tick で噴き上がる。その時に印の中 (横 RADIUS・上 HEIGHT) にいる敵、最大 MAX_TARGETS 体に、撃った時の溜めのまま近接の一撃と同じ傷
//     (combat/MeleeHits を通す)。会心は起こさない。撃った時と持ち物が変わっていたら当たらない
//   - 敵を狙って置いた印は、遅れのあいだその敵の足もとについていく (2026-11-11 の声「呪術師弱すぎるかも。雑魚敵に最初めっちゃ苦戦する」:
//     走って寄ってくる雑魚から印が外れていた)。地面に置いた印は動かない (置いて待つ術はそのまま)
//   - 演出: 撃つ = 鈴の音と手元の潮の粉。印 = 潮の青緑の輪と、内で回る三角の呪の線 (遅れのあいだ縮む)。噴く = 足もとから魂の火と黒紫の粉の柱・低い鈴と牙の音
// 見た目は少しずつ変わる (2026-11-11 の声「呪術師の遠距離攻撃も見た目かわっていく？」→「変えるようにして」。魔弾 StaffBolts と同じ作り):
//   - 段 (レベル 10 ごと。Look): 三角の印 → 二重の輪 (逆に回る) → 五芒の印 → 鈴の紋 (四方の小さな輪) → 渦の紋 (三本の腕)。色も段ごと
//   - 一発ごと (三発で一巡): 噴き方が 柱 → 外へ広がる波 → 縁から突き上げる牙 と替わり、鈴の高さも替わる
// 手元は client/weapon/StaffBoltClient が左クリックを見て、この合図 (Cast) を送る (溜めが足りるときだけ)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class HexMarks {
    public static final double RANGE = 16.0;
    public static final double DROP = 6.0;
    public static final int DELAY = 8;
    public static final double RADIUS = 1.8;
    public static final double HEIGHT = 2.6;
    public static final int MAX_TARGETS = 3;
    static final int MANA_HINT_GAP = 60;
    // 色: 潮の青緑 (輪)・黒紫 (呪の線と柱)・淡い緑 (手元)
    static final DustParticleOptions TIDE = new DustParticleOptions(new Vector3f(0.30f, 0.85f, 0.78f), 0.9f);
    static final DustParticleOptions CURSE = new DustParticleOptions(new Vector3f(0.45f, 0.18f, 0.62f), 1.0f);
    static final DustParticleOptions PALE = new DustParticleOptions(new Vector3f(0.70f, 1.00f, 0.88f), 0.6f);

    // 一発ごとの番 (三発で一巡)
    private static final Map<UUID, Integer> SHOT = PlayerMaps.track(new HashMap<>());
    private static final Map<UUID, Long> LAST = PlayerMaps.track(new HashMap<>());
    private static final Map<UUID, Long> MANA_HINT = PlayerMaps.track(new HashMap<>());
    private static final List<Mark> MARKS = new ArrayList<>();
    // aim が敵を指したとき、その敵 (cast がすぐ読む)
    private static final Map<UUID, LivingEntity> AIMED = PlayerMaps.track(new HashMap<>());

    // 浮いている呪印
    static final class Mark {
        final ServerPlayer owner;
        final ServerLevel level;
        Vec3 at;
        // 狙った敵 (地面に置いたときは null)。遅れのあいだ印がついていく
        @Nullable
        LivingEntity foe;
        final int ticker;
        final Item item;
        final int tier;
        final int shot;
        int age;
        final List<LivingEntity> hits = new ArrayList<>();
        boolean done;

        Mark(ServerPlayer owner, Vec3 at, int ticker, Item item, int tier, int shot) {
            this.tier = tier;
            this.shot = shot;
            this.owner = owner;
            this.level = owner.serverLevel();
            this.at = at;
            this.ticker = ticker;
            this.item = item;
        }
    }

    private HexMarks() {
    }

    // 人 → サーバー: 呪印を置く
    public record Cast() implements CustomPacketPayload {
        public static final Type<Cast> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "hex_mark"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Cast> STREAM_CODEC = StreamCodec.unit(new Cast());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("hex-1").playToServer(Cast.TYPE, Cast.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer p) {
                cast(p);
            }
        }));
    }

    // 狙った所 (印の真ん中の足もと)。何もなければ null
    @Nullable
    static Vec3 aim(ServerPlayer p) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getViewVector(1f);
        Vec3 end = eye.add(look.scale(RANGE));
        BlockHitResult block = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 reach = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        EntityHitResult ent = ProjectileUtil.getEntityHitResult(p.level(), p, eye, reach, new AABB(eye, reach).inflate(1.0),
                e -> e instanceof LivingEntity le && SkillActions.isFoe(p, le), 0.3f);
        if (ent != null) {
            AIMED.put(p.getUUID(), (LivingEntity) ent.getEntity());
            return ent.getEntity().position();
        }
        AIMED.remove(p.getUUID());
        Vec3 from = block.getType() == HitResult.Type.MISS ? end
                : block.getLocation().add(block.getDirection().getStepX() * 0.05, block.getDirection().getStepY() * 0.05, block.getDirection().getStepZ() * 0.05);
        // 下へ地面を探す (壁を指したら、その前の床)
        BlockHitResult down = p.level().clip(new ClipContext(from, from.add(0, -DROP, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (down.getType() == HitResult.Type.MISS) {
            return null;
        }
        return down.getLocation();
    }

    // 一発の重さ (マナ。魔弾と同じ)
    public static double cost(ServerPlayer p) {
        return StaffBolts.cost(p);
    }

    // 置く。置けたら印、置けなければ null
    @Nullable
    static Mark cast(ServerPlayer p) {
        if (!p.isAlive() || p.isSpectator() || Job.of(p.getMainHandItem()) != Job.SHAMAN) {
            return null;
        }
        if (jp.revati.skyabyss.rpg.skill.JobWeaponLock.wrongJob(p, p.getMainHandItem()) != null) {
            jp.revati.skyabyss.rpg.skill.JobWeaponLock.tell(p);
            return null;
        }
        long now = p.level().getGameTime();
        Long last = LAST.get(p.getUUID());
        if (last != null && now - last < StaffBolts.MIN_GAP) {
            return null;
        }
        if (p.getAttackStrengthScale(0.5f) < StaffBolts.READY) {
            return null;
        }
        Vec3 at = aim(p);
        if (at == null) {
            return null;
        }
        double cost = cost(p);
        if (SkillManager.get(p).mana < cost) {
            Long h = MANA_HINT.get(p.getUUID());
            if (h == null || now - h >= MANA_HINT_GAP) {
                MANA_HINT.put(p.getUUID(), now);
                jp.revati.skyabyss.notice.Notice.hint(p, "message.skyabyss.hex_mark.no_mana");
            }
            return null;
        }
        LAST.put(p.getUUID(), now);
        SkillManager.setMana(p, SkillManager.get(p).mana - cost);
        int shot = SHOT.merge(p.getUUID(), 1, (a, one) -> (a + one) % 3);
        int tier = tier(jp.revati.skyabyss.rpg.Growth.get(p).level);
        Mark m = new Mark(p, at, p.attackStrengthTicker, p.getMainHandItem().getItem(), tier, shot);
        m.foe = AIMED.remove(p.getUUID());
        MARKS.add(m);
        castFx(m.level, p, at, m.tier, m.shot);
        p.resetAttackStrengthTicker();
        return m;
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        if (!MARKS.isEmpty()) {
            tickAll(event.getServer());
        }
    }

    static void tickAll(MinecraftServer server) {
        for (Iterator<Mark> it = MARKS.iterator(); it.hasNext();) {
            Mark m = it.next();
            step(m);
            if (m.done) {
                it.remove();
            }
        }
    }

    static void step(Mark m) {
        if (m.owner.isRemoved() || m.owner.level() != m.level) {
            m.done = true;
            return;
        }
        m.age++;
        if (m.foe != null && m.foe.isAlive() && m.foe.level() == m.level) {
            m.at = m.foe.position();
        }
        if (m.age < DELAY) {
            markFx(m.level, m.at, m.age, m.tier);
            return;
        }
        erupt(m);
        m.done = true;
    }

    // 噴き上がる: 印の中の敵 (近い順に MAX_TARGETS 体) に傷
    static void erupt(Mark m) {
        ServerPlayer p = m.owner;
        eruptFx(m.level, m.at, m.tier, m.shot);
        if (!p.isAlive() || p.getMainHandItem().getItem() != m.item) {
            return;
        }
        AABB box = new AABB(m.at.x - RADIUS, m.at.y - 0.5, m.at.z - RADIUS, m.at.x + RADIUS, m.at.y + HEIGHT, m.at.z + RADIUS);
        List<LivingEntity> foes = new ArrayList<>(m.level.getEntitiesOfClass(LivingEntity.class, box, e -> SkillActions.isFoe(p, e)
                && Math.hypot(e.getX() - m.at.x, e.getZ() - m.at.z) <= RADIUS + e.getBbWidth() * 0.5));
        foes.sort((a, b) -> Double.compare(a.distanceToSqr(m.at), b.distanceToSqr(m.at)));
        int ticker = p.attackStrengthTicker;
        float fall = p.fallDistance;
        try {
            for (int i = 0; i < Math.min(MAX_TARGETS, foes.size()); i++) {
                LivingEntity foe = foes.get(i);
                p.attackStrengthTicker = m.ticker;
                p.fallDistance = 0f;
                jp.revati.skyabyss.weapon.WeaponStrikes.attackNow(p, foe);
                m.hits.add(foe);
            }
        } finally {
            p.attackStrengthTicker = ticker;
            p.fallDistance = fall;
        }
    }

    // ---------------------------------------------------------------- 演出
    // 段ごとの色 (輪・呪の線・噴くときの粉)
    record Look(DustParticleOptions ring, DustParticleOptions line, DustParticleOptions burst) {
    }

    static DustParticleOptions dust(float r, float g, float b, float size) {
        return new DustParticleOptions(new Vector3f(r, g, b), size);
    }

    static final Look[] LOOKS = {
        // 0 三角の印 (Lv 1〜9): 潮の青緑の輪と黒紫の三角
        new Look(TIDE, CURSE, CURSE),
        // 1 二重の輪 (Lv 10〜19): 海の緑と藍の、逆に回る二つの輪
        new Look(dust(0.25f, 0.75f, 0.55f, 0.9f), dust(0.25f, 0.25f, 0.70f, 1.0f), dust(0.30f, 0.30f, 0.80f, 1.0f)),
        // 2 五芒の印 (Lv 20〜29): 淡い金の輪と紫の星
        new Look(dust(0.95f, 0.85f, 0.55f, 0.9f), dust(0.62f, 0.25f, 0.80f, 1.0f), dust(0.75f, 0.40f, 0.90f, 1.0f)),
        // 3 鈴の紋 (Lv 30〜39): 青銅の輪と、四方の小さな鈴の輪
        new Look(dust(0.80f, 0.55f, 0.30f, 0.9f), dust(0.35f, 0.85f, 0.75f, 0.9f), dust(0.85f, 0.65f, 0.35f, 1.0f)),
        // 4 渦の紋 (Lv 40〜): 深い青の輪と、三本の黒紫の渦の腕と白い芯
        new Look(dust(0.20f, 0.35f, 0.85f, 1.0f), dust(0.30f, 0.08f, 0.40f, 1.1f), dust(0.90f, 0.95f, 1.00f, 0.9f)),
    };

    // レベルの段 (10 ごと。0〜4)
    public static int tier(int level) {
        return Math.max(0, Math.min(LOOKS.length - 1, level / 10));
    }

    // 撃つ鈴の高さ (一発ごとに替わる)
    static final float[] PITCH = {1.8f, 1.6f, 2.0f};

    static void castFx(ServerLevel level, ServerPlayer p, Vec3 at, int tier, int shot) {
        Vec3 hand = p.getEyePosition().add(p.getViewVector(1f).scale(0.9)).add(0, -0.35, 0);
        level.sendParticles(PALE, hand.x, hand.y, hand.z, 4, 0.08, 0.08, 0.08, 0);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.35f, PITCH[shot]);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.PLAYERS, 0.5f, 1.6f);
        markFx(level, at, 0, tier);
    }

    // 浮いた印: 外の輪と、段ごとの内の紋。遅れのあいだに少しずつ縮む
    static void markFx(ServerLevel level, Vec3 at, int age, int tier) {
        Look look = LOOKS[tier];
        double y = at.y + 0.08;
        double shrink = 1.0 - 0.25 * age / (double) DELAY;
        double r = RADIUS * shrink;
        if (age % 2 == 0) {
            circle(level, at.x, y, at.z, r, 16, look.ring(), age * 6);
        }
        double spin = Math.toRadians(age * 18);
        switch (tier) {
            case 1 -> {
                // 二重の輪: 内の輪は逆へ回る
                circle(level, at.x, y, at.z, r * 0.6, 10, look.line(), -age * 24);
            }
            case 2 -> {
                // 五芒の印: 星の線 (頂点を一つ飛ばしに結ぶ)
                polygon(level, at, y, r * 0.8, 5, 2, spin, look.line());
            }
            case 3 -> {
                // 鈴の紋: 四方に小さな鈴の輪、真ん中に一つ
                for (int k = 0; k < 4; k++) {
                    double a = spin + k * Math.PI / 2;
                    circle(level, at.x + Math.cos(a) * r * 0.6, y, at.z + Math.sin(a) * r * 0.6, 0.22, 5, look.line(), age * 30);
                }
                circle(level, at.x, y, at.z, 0.3, 6, look.ring(), -age * 30);
            }
            case 4 -> {
                // 渦の紋: 三本の腕が内へ巻き込む
                for (int arm = 0; arm < 3; arm++) {
                    for (int j = 0; j < 5; j++) {
                        double a = spin + arm * Math.PI * 2 / 3 + j * 0.45;
                        double rr = r * (0.85 - j * 0.16);
                        level.sendParticles(look.line(), at.x + Math.cos(a) * rr, y, at.z + Math.sin(a) * rr, 1, 0, 0, 0, 0);
                    }
                }
                level.sendParticles(look.burst(), at.x, y + 0.05, at.z, 1, 0, 0, 0, 0);
            }
            default -> {
                // 三角の印 (回る)
                polygon(level, at, y, r * 0.75, 3, 1, spin, look.line());
            }
        }
        if (age == DELAY - 2) {
            level.sendParticles(ParticleTypes.WITCH, at.x, y + 0.2, at.z, 6, r * 0.4, 0.05, r * 0.4, 0);
        }
    }

    // 噴く: 一発ごとに形が替わる (柱 → 外へ広がる波 → 下から突き上げる牙)。色は段のもの
    static void eruptFx(ServerLevel level, Vec3 at, int tier, int shot) {
        Look look = LOOKS[tier];
        switch (shot) {
            case 1 -> {
                // 波: 地面を三重の輪が外へ走る
                for (int w = 1; w <= 3; w++) {
                    circle(level, at.x, at.y + 0.15, at.z, RADIUS * w / 3.0, 8 + w * 4, w == 3 ? look.ring() : look.burst(), w * 20);
                }
                for (int k = 0; k < 8; k++) {
                    double a = Math.toRadians(k * 45);
                    level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y + 0.15, at.z, 0, Math.cos(a), 0.05, Math.sin(a), 0.18);
                }
                level.playSound(null, at.x, at.y, at.z, SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 0.6f, 1.4f);
            }
            case 2 -> {
                // 牙: 印の縁の五か所から、短い柱が突き上がる
                for (int k = 0; k < 5; k++) {
                    double a = Math.toRadians(k * 72 + 18);
                    double x = at.x + Math.cos(a) * RADIUS * 0.6;
                    double z = at.z + Math.sin(a) * RADIUS * 0.6;
                    for (int j = 0; j < 4; j++) {
                        level.sendParticles(j == 3 ? look.ring() : look.burst(), x, at.y + 0.2 + j * 0.3, z, 1, 0.03, 0, 0.03, 0);
                    }
                    level.sendParticles(ParticleTypes.CRIT, x, at.y + 0.3, z, 2, 0.05, 0.3, 0.05, 0.05);
                }
                level.playSound(null, at.x, at.y, at.z, SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.PLAYERS, 0.8f, 0.95f);
            }
            default -> {
                // 柱: 足もとから魂の火と粉の柱
                for (int k = 0; k < 10; k++) {
                    double a = Math.toRadians(k * 36);
                    double rr = RADIUS * 0.55;
                    level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x + Math.cos(a) * rr, at.y + 0.1, at.z + Math.sin(a) * rr, 0, 0, 1, 0, 0.12);
                }
                for (int j = 0; j < 6; j++) {
                    level.sendParticles(look.burst(), at.x, at.y + 0.3 + j * 0.4, at.z, 3, 0.25, 0.1, 0.25, 0);
                }
                level.playSound(null, at.x, at.y, at.z, SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.PLAYERS, 0.7f, 1.25f);
            }
        }
        level.sendParticles(look.ring(), at.x, at.y + 0.2, at.z, 14, RADIUS * 0.5, 0.1, RADIUS * 0.5, 0);
        level.sendParticles(ParticleTypes.SCULK_SOUL, at.x, at.y + 0.4, at.z, 3, 0.3, 0.2, 0.3, 0.02);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.35f, PITCH[shot] - 1.1f);
    }

    // 地面に平らな輪 (n 点、phase 度ずらす)
    static void circle(ServerLevel level, double cx, double y, double cz, double r, int n, DustParticleOptions dust, double phase) {
        for (int k = 0; k < n; k++) {
            double a = Math.toRadians(phase + k * 360.0 / n);
            level.sendParticles(dust, cx + Math.cos(a) * r, y, cz + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    // 地面に平らな多角形 (n 頂点を step 飛ばしに結ぶ。step 1 = 多角形、5 と 2 = 五芒星)
    static void polygon(ServerLevel level, Vec3 at, double y, double r, int n, int step, double spin, DustParticleOptions dust) {
        for (int k = 0; k < n; k++) {
            double a0 = spin + k * Math.PI * 2 / n;
            double a1 = spin + (k + step) * Math.PI * 2 / n;
            for (int j = 0; j <= 3; j++) {
                double t = j / 3.0;
                double x = Math.cos(a0) * r * (1 - t) + Math.cos(a1) * r * t;
                double z = Math.sin(a0) * r * (1 - t) + Math.sin(a1) * r * t;
                level.sendParticles(dust, at.x + x, y, at.z + z, 1, 0, 0, 0, 0);
            }
        }
    }

    // ---------------------------------------------------------------- 自己テスト用
    static void forgetForTest(ServerPlayer p) {
        LAST.remove(p.getUUID());
    }

    // 置いて、噴き上がるまで進める。当たった敵 (置けなければ fired = false)
    static List<LivingEntity> castAndErupt(ServerPlayer p, boolean[] fired) {
        return castAndErupt(p, fired, null);
    }

    // midway: 遅れの半ばに一度だけ走らせる (狙った敵が動くかを確かめる)
    static List<LivingEntity> castAndErupt(ServerPlayer p, boolean[] fired, @Nullable Runnable midway) {
        Mark m = cast(p);
        fired[0] = m != null;
        if (m == null) {
            return List.of();
        }
        MARKS.remove(m);
        for (int i = 0; i <= DELAY + 1 && !m.done; i++) {
            if (i == DELAY / 2 && midway != null) {
                midway.run();
            }
            step(m);
        }
        return m.hits;
    }
}
