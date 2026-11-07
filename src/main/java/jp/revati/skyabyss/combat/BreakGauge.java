package jp.revati.skyabyss.combat;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.util.Players;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 崩しゲージ (docs/design/47 の 2.1)。2026-11-04 の声「構え ×0.3 と隙 ×1.6 では倍率が見えない」→ 表 3-1 のとおり。
//   主 (BossOpenings.planted) は、ふだん人から受ける傷 ×NORMAL (BossOpenings.multiplier)。
//   溜まる: 満ちた近接の一撃 (溜め MeleeHits.FULL 超え) は与えた傷のぶん。満ちていない近接は溜まらない。技・矢・魔法は ×SKILL。
//           技のあとの隙 (BossOpenings.isOpen) は ×OPENING、背中 (後ろ BACK_COS より後ろ) から ×BACK。仕掛けを解くと (solved) 大きさの SOLVED を一度に
//   減る: DECAY_AFTER 当てないと、1 秒に大きさの DECAY ずつ
//   大きさ: 最大体力 × SIZE (人数で体力の増える主は、ゲージも同じに増える)。崩れるたびに、次は ×GROW[崩れた回数]
//   崩れ: 満ちると BREAK_TICKS のあいだ崩れる。技を止める (予告を消し、新しい予告と直の傷を出さず、歩かない)。受ける傷 ×BROKEN
//   見せ方: ボスバーの下の細い帯 (client/combat/BreakGaugeClient)。満ちる直前に金に脈打ち、崩れたら割れる音と光、崩れの残りで帯が縮む
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class BreakGauge {
    public static final float NORMAL = 0.8f;
    public static final float BROKEN = 2.0f;
    public static final double SKILL = 0.7;
    public static final double OPENING = 3.0;
    public static final double BACK = 1.5;
    public static final double BACK_COS = -0.5;
    public static final double SOLVED = 0.5;
    public static final int DECAY_AFTER = 80;
    public static final double DECAY = 0.08;
    public static final double SIZE = 0.22;
    public static final double[] GROW = {1.0, 1.25, 1.5};
    public static final int BREAK_TICKS = 100;
    static final int SYNC_EVERY = 5;
    // 帯を見せたまま何もしない主の記録を消すまで (戦いが終わった・人が離れた)
    static final int FORGET_AFTER = 1200;
    static final String TIP_KEY = "skyabyss_break_tip";

    // サーバー -> クライアント: 主の帯 (fill = 溜まり 0〜1、broken = 崩れの残り 0〜1、flash = いま崩れた)。fill が負なら帯を消す
    public record Payload(int entityId, float fill, float broken, boolean flash) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "break_gauge"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Payload::entityId, ByteBufCodecs.FLOAT, Payload::fill, ByteBufCodecs.FLOAT, Payload::broken,
                ByteBufCodecs.BOOL, Payload::flash, Payload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    static final class State {
        final LivingEntity boss;
        double fill;
        int breaks;
        long brokenUntil;
        long lastFill;
        long lastTouch;
        boolean flash;

        State(LivingEntity boss) {
            this.boss = boss;
        }
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private BreakGauge() {
    }

    // ---------------------------------------------------------------- 読む

    // 今のゲージの大きさ (崩れた回数で大きくなる)
    public static double size(LivingEntity boss, int breaks) {
        return boss.getMaxHealth() * SIZE * GROW[Math.min(breaks, GROW.length - 1)];
    }

    public static boolean broken(LivingEntity boss) {
        State st = STATES.get(boss.getUUID());
        return st != null && boss.level().getGameTime() < st.brokenUntil;
    }

    // 溜まり (0〜1)
    public static double fill(LivingEntity boss) {
        State st = STATES.get(boss.getUUID());
        return st == null ? 0 : Math.min(1, st.fill / size(boss, st.breaks));
    }

    public static int breaks(LivingEntity boss) {
        State st = STATES.get(boss.getUUID());
        return st == null ? 0 : st.breaks;
    }

    // この一撃の溜まりの掛け目。満ちていない近接は 0
    public static double share(LivingEntity boss, Player attacker, Entity direct) {
        if (MeleeHits.weakHit(attacker)) {
            return 0;
        }
        boolean full = MeleeHits.fullHit(attacker) && direct == attacker;
        double k = full ? 1.0 : SKILL;
        // 灰の契り (鍛冶師の型の要): 満ちた一撃で帯がよく溜まる
        if (full && ashPact(attacker)) {
            k *= jp.revati.skyabyss.rpg.skill.SkillRules.KEY_ASH_BREAK_FILL;
        }
        if (BossOpenings.isOpen(boss)) {
            k *= OPENING;
        }
        if (behind(boss, attacker)) {
            k *= BACK;
        }
        return k;
    }

    static boolean ashPact(Player p) {
        return jp.revati.skyabyss.rpg.skill.SkillBuild.has(jp.revati.skyabyss.rpg.skill.SkillManager.get(p), jp.revati.skyabyss.rpg.skill.SkillKeystone.ASH_PACT);
    }

    // 人が主の後ろにいるか (体の向きから BACK_COS より後ろ)
    public static boolean behind(LivingEntity boss, Entity from) {
        Vec3 to = new Vec3(from.getX() - boss.getX(), 0, from.getZ() - boss.getZ());
        if (to.lengthSqr() < 1.0E-4) {
            return false;
        }
        Vec3 look = Vec3.directionFromRotation(0, boss.yBodyRot);
        return look.dot(to.normalize()) < BACK_COS;
    }

    // ---------------------------------------------------------------- 溜める・崩す

    static State state(LivingEntity boss) {
        State st = STATES.computeIfAbsent(boss.getUUID(), k -> new State(boss));
        st.lastTouch = boss.level().getGameTime();
        return st;
    }

    // 溜める (崩れている間は溜まらない)。崩れたら true
    public static boolean add(LivingEntity boss, double amount) {
        State st = state(boss);
        long now = boss.level().getGameTime();
        if (now < st.brokenUntil || amount <= 0) {
            return false;
        }
        st.fill += amount;
        st.lastFill = now;
        if (st.fill >= size(boss, st.breaks)) {
            breakNow(st, now);
            return true;
        }
        return false;
    }

    // 主ごとの仕掛けを解いた (心臓の領域など): 大きさの SOLVED を一度に。大きな隙も開ける
    public static void solved(LivingEntity boss) {
        solved(boss, BossOpenings.BIG_OPEN_TICKS);
    }

    // 主が自分で持つ「解かれた」弱い時間 (月の主の倒れる・膝をつく・崩れる・固まる): その長さの隙と、大きさの SOLVED
    public static void solved(LivingEntity boss, int ticks) {
        BossOpenings.open(boss, ticks);
        State st = state(boss);
        add(boss, size(boss, st.breaks) * SOLVED);
    }

    static void breakNow(State st, long now) {
        LivingEntity boss = st.boss;
        st.fill = 0;
        st.breaks++;
        st.brokenUntil = now + BREAK_TICKS;
        st.flash = true;
        Telegraph.cancelAll(boss);
        if (boss.level() instanceof ServerLevel level) {
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2.0f, 0.6f);
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.8f, 0.7f);
            double y = boss.getY() + boss.getBbHeight() * 0.6;
            for (ServerPlayer p : Players.snapshot(level.players())) {
                if (p.distanceToSqr(boss) < 96 * 96) {
                    level.sendParticles(p, ParticleTypes.END_ROD, true, boss.getX(), y, boss.getZ(), 40, boss.getBbWidth() * 0.5,
                            boss.getBbHeight() * 0.3, boss.getBbWidth() * 0.5, 0.25);
                    level.sendParticles(p, ParticleTypes.CRIT, true, boss.getX(), y, boss.getZ(), 30, boss.getBbWidth() * 0.4,
                            boss.getBbHeight() * 0.3, boss.getBbWidth() * 0.4, 0.5);
                }
            }
        }
        sync(st);
    }

    // 崩れている主: 歩かない (落ちるのはそのまま)
    static void hold(LivingEntity boss) {
        if (boss instanceof Mob m) {
            m.getNavigation().stop();
        }
        Vec3 v = boss.getDeltaMovement();
        boss.setDeltaMovement(0, v.y, 0);
        if (boss.tickCount % 10 == 0 && boss.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CRIT, boss.getX(), boss.getY() + boss.getBbHeight() + 0.3, boss.getZ(), 4,
                    boss.getBbWidth() * 0.3, 0.1, boss.getBbWidth() * 0.3, 0.05);
        }
    }

    // ---------------------------------------------------------------- 口

    @SubscribeEvent
    static void onDamaged(LivingDamageEvent.Post event) {
        LivingEntity boss = event.getEntity();
        if (boss.level().isClientSide() || boss instanceof Player || !(event.getSource().getEntity() instanceof Player attacker)
                || !BossOpenings.planted(boss) || !boss.isAlive()) {
            return;
        }
        double k = share(boss, attacker, event.getSource().getDirectEntity());
        state(boss);
        if (k > 0 && event.getNewDamage() > 0) {
            add(boss, event.getNewDamage() * k);
            if (attacker instanceof ServerPlayer sp && !sp.getPersistentData().getBoolean(TIP_KEY)) {
                sp.getPersistentData().putBoolean(TIP_KEY, true);
                Notice.hint(sp, "message.skyabyss.break.tip");
            }
        }
    }

    // 崩れている主の傷は人に届かない (崩れる前に放った矢も)
    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onOutgoing(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof LivingEntity from && !(from instanceof Player) && !from.level().isClientSide() && broken(from)) {
            event.setCanceled(true);
        }
    }

    // 崩れている主は、技の動き (振りかぶり・弾・粒) も出さない (2026-11-08 の声「スタンしてるとき、敵が攻撃使っちゃう。ダメージはこないけど」)。
    // 主の tick を飛ばし、落ちることと、傷の点滅・無敵の時間の減りだけ進める (story/ch6/field/MomentStop と同じやり方)。死んだ主は止めない
    @SubscribeEvent
    static void onEntityTick(net.neoforged.neoforge.event.tick.EntityTickEvent.Pre event) {
        if (STATES.isEmpty() || !(event.getEntity() instanceof LivingEntity le) || le.level().isClientSide() || le instanceof Player
                || !le.isAlive() || le.getHealth() <= 0 || !broken(le)) {
            return;
        }
        event.setCanceled(true);
        le.setOldPosAndRot();
        if (le.invulnerableTime > 0) {
            le.invulnerableTime--;
        }
        if (le.hurtTime > 0) {
            le.hurtTime--;
        }
        if (!le.onGround() && !le.isNoGravity()) {
            double vy = (le.getDeltaMovement().y - 0.08) * 0.98;
            le.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(0, vy, 0));
            le.setDeltaMovement(0, le.onGround() ? 0 : vy, 0);
        } else {
            le.setDeltaMovement(Vec3.ZERO);
        }
    }

    // Telegraph.start から: この持ち主は今、技を出せない
    public static boolean silenced(Entity owner) {
        return owner instanceof LivingEntity le && !le.level().isClientSide() && broken(le);
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        if (STATES.isEmpty()) {
            return;
        }
        for (Iterator<State> it = STATES.values().iterator(); it.hasNext();) {
            State st = it.next();
            LivingEntity boss = st.boss;
            long now = boss.level().getGameTime();
            if (boss.isRemoved() || !boss.isAlive() || now - st.lastTouch > FORGET_AFTER) {
                send(boss, new Payload(boss.getId(), -1f, 0f, false));
                it.remove();
                continue;
            }
            boolean isBroken = now < st.brokenUntil;
            if (isBroken) {
                hold(boss);
                st.lastTouch = now;
            } else if (st.fill > 0 && now - st.lastFill > DECAY_AFTER) {
                st.fill = Math.max(0, st.fill - size(boss, st.breaks) * DECAY / 20.0);
            }
            if (st.flash || now % SYNC_EVERY == 0) {
                sync(st);
            }
        }
    }

    static void sync(State st) {
        LivingEntity boss = st.boss;
        long now = boss.level().getGameTime();
        float broken = now < st.brokenUntil ? (float) (st.brokenUntil - now) / BREAK_TICKS : 0f;
        send(boss, new Payload(boss.getId(), (float) Math.min(1, st.fill / size(boss, st.breaks)), broken, st.flash));
        st.flash = false;
    }

    static void send(LivingEntity boss, Payload payload) {
        if (boss.level() instanceof ServerLevel) {
            PacketDistributor.sendToPlayersTrackingEntity(boss, payload);
        }
    }

    // 自己テスト用
    static void forget(LivingEntity boss) {
        STATES.remove(boss.getUUID());
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        STATES.clear();
    }
}
