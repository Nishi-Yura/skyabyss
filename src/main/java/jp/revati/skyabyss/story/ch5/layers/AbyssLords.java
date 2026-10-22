package jp.revati.skyabyss.story.ch5.layers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.abyss.arts.AbyssArt;
import jp.revati.skyabyss.abyss.arts.AbyssArts;
import jp.revati.skyabyss.abyss.arts.AbyssLight;
import jp.revati.skyabyss.abyss.mobs.AbyssMobSpawner;
import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.story.ch5.AbyssBuild;
import jp.revati.skyabyss.story.ch5.AbyssWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 層の主 (docs/design/36 の 8 章「層の主」)。層の終わりの主の間 (arena_<層>) で、心臓の番人の決まりを一つずつ教える:
//   骨  肋の主 (肋の番の大きいもの)          灯り: 間の四つの灯台 (lamp_<層>_0..3。右クリックで 40 秒灯る) の光の中か、光の術の灯りの中でだけ刃が通る (外は 1/4)。
//                                           主はときどき灯台を吹き消しに来る (灯台のまわりに赤い輪。輪の中に人が立っていれば灯りは守れる。立った人は傷)
//   塩  白殻の主 (白い貝守の大きいもの)       重さ: 一人ずつの足もとに赤い輪 (重さの落ちる所)。輪の中の人は押しつぶされる (重さの術の中なら軽い)
//   雷  鼓動まねの主 (鼓動まねの大きいもの)    鼓動: 三つの写しに分かれる。鼓動の音が鳴るのは本物だけ (鼓動の術を使った人には本物の輪郭も)。写しを叩くと弾けて赤い輪
//   灰  灯り喰いの主 (灯り喰いの大きいもの)    闇: 闇の波で間の灯りと光の術の灯りが全部消え、闇のあいだ主は刃をほとんど通さない。光の術で主を照らせば通る
// 主の間に、まだ倒していない人が入ると目覚める (戦いは間にいる人で。体力は人数で増える)。だれもいなくなって 30 秒で眠る (体力は戻る)
// 倒れると間にいた人とそのパーティに "lord:<層>" と合図 abyss_lord_<層>。奥の下りの鎖が使えるようになる
// 予告は赤だけ (巨神の決まり)。戦いのあいだ、目的の帯に戦いの一言 (AbyssSections.objective)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class AbyssLords {
    public static final String TAG = "skyabyss_lord";
    public static final String COPY_TAG = "skyabyss_lord_copy";
    static final String[] MOB = {"rib_guard", "white_shell_keeper", "heartbeat_mimic", "lamp_eater"};
    static final double[] HP = {260, 320, 300, 380};          // 一人のときの体力
    static final double[] SCALE = {1.9, 2.0, 1.7, 1.8};
    static final double PER_MATE = 0.6;                       // 一人ふえるごとに体力 +60%
    static final int LAMP_LIFE = 800;
    static final double LAMP_R = 6;
    static final float DIM = 0.25f;                           // 光の外・闇の中の刃の通り
    static final int OBJ_TICKS = 60;

    static final class Fight {
        final String layer;
        final int li;
        UUID lord;
        final List<UUID> copies = new ArrayList<>();
        final ServerBossEvent bar;
        long nextMech;
        long darkUntil;
        long emptySince = -1;
        long started;
        final long[] lampUntil = new long[4];
        String flash = "";
        long flashUntil;

        Fight(String layer) {
            this.layer = layer;
            this.li = Layers.index(layer);
            this.bar = new ServerBossEvent(Component.translatable("abyss_v2.skyabyss.lord." + layer), BossEvent.BossBarColor.RED,
                    BossEvent.BossBarOverlay.NOTCHED_10);
        }
    }

    private static final Map<String, Fight> FIGHTS = new HashMap<>();
    private static final Map<UUID, Long> HEARD = new HashMap<>();
    private static boolean listening;

    private AbyssLords() {
    }

    @Nullable
    public static LivingEntity lord(ServerLevel level, String layer) {
        Fight f = FIGHTS.get(layer);
        if (f == null || f.lord == null) {
            return null;
        }
        return level.getEntity(f.lord) instanceof LivingEntity le && le.isAlive() ? le : null;
    }

    public static boolean active(String layer) {
        return FIGHTS.containsKey(layer);
    }

    static List<ServerPlayer> inArena(ServerLevel level, String layer) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : List.copyOf(level.players())) {
            if (p.isAlive() && !p.isSpectator() && Layers.inArena(layer, p.blockPosition())) {
                out.add(p);
            }
        }
        return out;
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        if (level == null || !AbyssBuild.built(server)) {
            return;
        }
        if (!listening) {
            listening = true;
            AbyssArts.ON_CAST.add((p, a) -> {
                if (a == AbyssArt.HEARTBEAT) {
                    HEARD.put(p.getUUID(), p.level().getGameTime());
                }
            });
        }
        if (level.players().isEmpty() && FIGHTS.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        for (String layer : Layers.ORDER) {
            tick(level, layer, now);
        }
    }

    static void tick(ServerLevel level, String layer, long now) {
        Fight f = FIGHTS.get(layer);
        List<ServerPlayer> in = inArena(level, layer);
        if (f == null) {
            if (now % 10 != 0) {
                return;
            }
            for (ServerPlayer p : in) {
                if (!AbyssMarks.lordBeaten(p, layer)) {
                    wake(level, layer, in, now);
                    return;
                }
            }
            return;
        }
        // 眠る (だれもいない 30 秒)
        if (in.isEmpty()) {
            if (f.emptySince < 0) {
                f.emptySince = now;
            }
            if (now - f.emptySince > 600) {
                sleep(level, f);
            }
            return;
        }
        f.emptySince = -1;
        LivingEntity lord = level.getEntity(f.lord) instanceof LivingEntity le ? le : null;
        if (lord == null) {
            sleep(level, f);
            return;
        }
        if (!lord.isAlive()) {
            return; // 倒れた (onDeath で片付け)
        }
        // 間から出ない
        BlockPos c = AbyssBuild.mark("arena_" + layer);
        if (c != null && !Layers.inArena(layer, lord.blockPosition())) {
            lord.teleportTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
        }
        f.bar.setProgress(Math.max(0f, Math.min(1f, lord.getHealth() / lord.getMaxHealth())));
        for (ServerPlayer p : in) {
            f.bar.addPlayer(p);
        }
        for (ServerPlayer p : List.copyOf(f.bar.getPlayers())) {
            if (!in.contains(p)) {
                f.bar.removePlayer(p);
            }
        }
        lamps(level, f, now);
        switch (f.li) {
            case 0 -> bone(level, f, lord, in, now);
            case 1 -> salt(level, f, lord, in, now);
            case 2 -> storm(level, f, lord, in, now);
            default -> ash(level, f, lord, in, now);
        }
        // 帯の一言 (仕掛けの一言が出ている間はそちら)
        String line = f.flashUntil > now ? f.flash : "objective.skyabyss.abyss_v2.lord_" + layer;
        for (ServerPlayer p : in) {
            AbyssSections.objective(p, line, OBJ_TICKS);
        }
    }

    static void flash(Fight f, String key, long now, int ticks) {
        f.flash = key;
        f.flashUntil = now + ticks;
    }

    // ---------------------------------------------------------------- 目覚め・眠り・倒れ
    static void wake(ServerLevel level, String layer, List<ServerPlayer> in, long now) {
        BlockPos at = AbyssBuild.mark("lord_" + layer);
        if (at == null) {
            return;
        }
        int li = Layers.index(layer);
        Fight f = new Fight(layer);
        Entity probe = AbyssMobSpawner.spawnAt(level, MOB[li], at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 3);
        if (!(probe instanceof LivingEntity le)) {
            return;
        }
        le.addTag(TAG);
        le.addTag(TAG + "_" + layer);
        le.addTag(LayerFights.TAG);
        double want = HP[li] * (1 + PER_MATE * Math.max(0, in.size() - 1));
        var hp = le.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(want);
            le.setHealth(le.getMaxHealth());
        }
        var sc = le.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE);
        if (sc != null) {
            sc.setBaseValue(SCALE[li]);
        }
        le.setCustomName(Component.translatable("abyss_v2.skyabyss.lord." + layer));
        if (le instanceof net.minecraft.world.entity.Mob m) {
            m.setPersistenceRequired();
        }
        f.lord = le.getUUID();
        f.started = now;
        f.nextMech = now + 120;
        FIGHTS.put(layer, f);
        level.playSound(null, at, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 1.6f, 0.8f);
        for (ServerPlayer p : in) {
            Notice.alert(p, "message.skyabyss.abyss_v2.lord_wake." + layer);
            LayerFights.later(level.getServer(), 60, () -> Notice.narrate(p, "message.skyabyss.abyss_v2.lord_teach." + layer));
        }
        // 骨のほかの間は、灯台を灯しておく (飾り)。骨の間は消えたまま始まる
        for (int k = 0; k < 4; k++) {
            setLamp(level, layer, k, li != 0);
            f.lampUntil[k] = li != 0 ? Long.MAX_VALUE : 0;
        }
    }

    static void sleep(ServerLevel level, Fight f) {
        Entity e = f.lord == null ? null : level.getEntity(f.lord);
        if (e != null) {
            e.discard();
        }
        for (UUID u : f.copies) {
            Entity c = level.getEntity(u);
            if (c != null) {
                c.discard();
            }
        }
        f.bar.removeAllPlayers();
        FIGHTS.remove(f.layer);
    }

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel level) || !dead.getTags().contains(TAG)) {
            return;
        }
        for (Map.Entry<String, Fight> e : List.copyOf(FIGHTS.entrySet())) {
            Fight f = e.getValue();
            if (dead.getUUID().equals(f.lord)) {
                defeat(level, f);
                return;
            }
        }
    }

    // 倒れた: 間にいた人とパーティに記録と合図。写しを消し、下りの鎖を見せる
    public static void defeat(ServerLevel level, Fight f) {
        List<ServerPlayer> in = inArena(level, f.layer);
        LayerData d = LayerData.get(level.getServer());
        for (ServerPlayer p : in) {
            for (UUID who : AbyssMarks.withParty(p)) {
                d.set(who, "lord:" + f.layer);
            }
            Quests.signalWithParty(level, List.of(p), "abyss_lord_" + f.layer);
            Notice.objective(p, "message.skyabyss.abyss_v2.lord_fall." + f.layer);
            AbyssSections.objective(p, null, 0);
        }
        for (UUID u : f.copies) {
            Entity c = level.getEntity(u);
            if (c != null) {
                c.discard();
            }
        }
        f.bar.removeAllPlayers();
        FIGHTS.remove(f.layer);
        BlockPos dsc = AbyssBuild.mark("descend_" + f.layer);
        if (dsc != null) {
            level.playSound(null, dsc, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 2f, 0.6f);
            level.sendParticles(ParticleTypes.SOUL, dsc.getX() + 0.5, dsc.getY() + 2, dsc.getZ() + 0.5, 30, 0.4, 2, 0.4, 0.02);
        }
    }

    // 開発用・自己テスト: 今の主をすぐ倒したことにする
    public static boolean defeatNow(ServerLevel level, String layer) {
        Fight f = FIGHTS.get(layer);
        if (f == null) {
            return false;
        }
        Entity e = level.getEntity(f.lord);
        defeat(level, f);
        if (e != null) {
            e.discard();
        }
        return true;
    }

    public static void resetForTest(ServerLevel level) {
        for (Fight f : List.copyOf(FIGHTS.values())) {
            sleep(level, f);
        }
    }

    // ---------------------------------------------------------------- 刃の通り
    @SubscribeEvent
    static void onHurt(LivingIncomingDamageEvent event) {
        LivingEntity e = event.getEntity();
        if (!(e.level() instanceof ServerLevel level)) {
            return;
        }
        if (e.getTags().contains(COPY_TAG)) {
            // 写し: 弾けて赤い輪
            Vec3 at = e.position();
            e.discard();
            event.setCanceled(true);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 1f, 1.4f);
            Telegraph.in(level, TelegraphShape.circle(at.x, at.z, 2.8)).floor(at.y).warn(18).danger().owner(null)
                    .onHit((lvl, p) -> p.hurt(lvl.damageSources().magic(), 5f)).start();
            return;
        }
        if (!e.getTags().contains(TAG)) {
            return;
        }
        for (Fight f : FIGHTS.values()) {
            if (!e.getUUID().equals(f.lord)) {
                continue;
            }
            long now = level.getGameTime();
            boolean dim = false;
            if (f.li == 0) {
                dim = !litAround(level, f, e.position(), now) && AbyssLight.lampAt(level, e.blockPosition()) < 4;
            } else if (f.li == 3) {
                dim = f.darkUntil > now && AbyssLight.lampAt(level, e.blockPosition()) < 4;
            }
            if (dim) {
                event.setAmount(event.getAmount() * DIM);
                level.sendParticles(ParticleTypes.SMOKE, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 6, 0.3, 0.3, 0.3, 0.01);
                if (event.getSource().getEntity() instanceof ServerPlayer p && now % 40 < 20) {
                    p.displayClientMessage(Component.translatable("message.skyabyss.abyss_v2.lord_dim." + f.layer), true);
                }
            }
            return;
        }
    }

    // ---------------------------------------------------------------- 灯台 (どの間にもある。骨の間では戦いの芯)
    static void setLamp(ServerLevel level, String layer, int k, boolean on) {
        BlockPos m = AbyssBuild.mark("lamp_" + layer + "_" + k);
        if (m == null || !level.isLoaded(m)) {
            return;
        }
        BlockState st = level.getBlockState(m);
        if (st.hasProperty(CampfireBlock.LIT) && st.getValue(CampfireBlock.LIT) != on) {
            level.setBlock(m, st.setValue(CampfireBlock.LIT, on), Block.UPDATE_ALL);
        }
    }

    static void lamps(ServerLevel level, Fight f, long now) {
        for (int k = 0; k < 4; k++) {
            if (f.lampUntil[k] != 0 && f.lampUntil[k] <= now) {
                f.lampUntil[k] = 0;
                setLamp(level, f.layer, k, false);
            }
        }
    }

    static boolean litAround(ServerLevel level, Fight f, Vec3 at, long now) {
        for (int k = 0; k < 4; k++) {
            BlockPos m = AbyssBuild.mark("lamp_" + f.layer + "_" + k);
            if (m != null && f.lampUntil[k] > now && at.distanceTo(Vec3.atCenterOf(m)) <= LAMP_R) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getLevel().dimension() != AbyssWorld.LEVEL) {
            return;
        }
        for (String layer : Layers.ORDER) {
            for (int k = 0; k < 4; k++) {
                BlockPos m = AbyssBuild.mark("lamp_" + layer + "_" + k);
                if (m != null && (event.getPos().equals(m) || event.getPos().equals(m.below()))) {
                    event.setCanceled(true);
                    Fight f = FIGHTS.get(layer);
                    long now = p.level().getGameTime();
                    setLamp(p.serverLevel(), layer, k, true);
                    if (f != null) {
                        f.lampUntil[k] = now + LAMP_LIFE;
                    }
                    p.level().playSound(null, m, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.8f, 0.9f);
                    return;
                }
            }
        }
    }

    // ---------------------------------------------------------------- 骨: 灯りを吹き消しに来る
    static void bone(ServerLevel level, Fight f, LivingEntity lord, List<ServerPlayer> in, long now) {
        if (now < f.nextMech) {
            return;
        }
        f.nextMech = now + 240;
        int best = -1;
        double bd = Double.MAX_VALUE;
        for (int k = 0; k < 4; k++) {
            BlockPos m = AbyssBuild.mark("lamp_" + f.layer + "_" + k);
            if (m != null && f.lampUntil[k] > now) {
                double d = lord.position().distanceTo(Vec3.atCenterOf(m));
                if (d < bd) {
                    bd = d;
                    best = k;
                }
            }
        }
        if (best < 0) {
            flash(f, "objective.skyabyss.abyss_v2.flash_light_lamps", now, 160);
            return;
        }
        int k = best;
        BlockPos m = AbyssBuild.mark("lamp_" + f.layer + "_" + k);
        Vec3 c = Vec3.atBottomCenterOf(m.below(2));
        flash(f, "objective.skyabyss.abyss_v2.flash_guard_lamp", now, 80);
        level.playSound(null, m, SoundEvents.WARDEN_SNIFF, SoundSource.HOSTILE, 1.4f, 0.8f);
        Telegraph.in(level, TelegraphShape.circle(c.x, c.z, 2.6)).floor(c.y).warn(50).danger().owner(lord)
                .onImpact(lvl -> {
                    boolean guarded = false;
                    for (ServerPlayer p : inArena(lvl, f.layer)) {
                        if (Math.hypot(p.getX() - c.x, p.getZ() - c.z) <= 2.6) {
                            guarded = true;
                        }
                    }
                    if (!guarded) {
                        f.lampUntil[k] = 0;
                        setLamp(lvl, f.layer, k, false);
                        lvl.playSound(null, m, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.4f, 0.6f);
                    }
                })
                .onHit((lvl, p) -> p.hurt(lvl.damageSources().mobAttack(lord), 5f)).start();
    }

    // ---------------------------------------------------------------- 塩: 重さの落ちる所 (番人の戦いでも使う)
    static void salt(ServerLevel level, Fight f, LivingEntity lord, List<ServerPlayer> in, long now) {
        if (now < f.nextMech) {
            return;
        }
        f.nextMech = now + 170;
        flash(f, "objective.skyabyss.abyss_v2.flash_weight", now, 70);
        crush(level, in, lord, 9f);
        level.playSound(null, lord.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.2f, 0.5f);
    }

    // 一人ずつの足もとに赤い輪。輪の中の人は押しつぶされる (重さの術の中なら 1/4・遅くならない)
    public static void crush(ServerLevel level, List<ServerPlayer> targets, @Nullable LivingEntity owner, float hit) {
        for (ServerPlayer t : targets) {
            Vec3 c = t.position();
            Telegraph.in(level, TelegraphShape.circle(c.x, c.z, 3.2)).floor(c.y).warn(32).danger().owner(owner)
                    .onImpact(lvl -> lvl.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.8f, 0.4f))
                    .onHit((lvl, p) -> {
                        boolean light = AbyssArts.weightRelief(p) >= 0.3;
                        p.hurt(owner != null ? lvl.damageSources().mobAttack(owner) : lvl.damageSources().magic(), light ? hit * 0.25f : hit);
                        if (!light) {
                            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2, false, true));
                            p.setDeltaMovement(p.getDeltaMovement().x * 0.2, -0.8, p.getDeltaMovement().z * 0.2);
                            p.hurtMarked = true;
                        }
                    }).start();
        }
    }

    // ---------------------------------------------------------------- 雷: 写しに分かれる
    static void storm(ServerLevel level, Fight f, LivingEntity lord, List<ServerPlayer> in, long now) {
        f.copies.removeIf(u -> !(level.getEntity(u) instanceof LivingEntity c) || !c.isAlive());
        // 本物の鼓動 (だれにでも聞こえる、位置の分かる音)。鼓動の術を使った人 (20 秒) には本物の輪郭
        if (now % 30 == 0) {
            level.playSound(null, lord.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 2.2f, 1f);
            for (ServerPlayer p : in) {
                Long h = HEARD.get(p.getUUID());
                if (h != null && now - h < 400) {
                    lord.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false));
                    level.sendParticles(p, ParticleTypes.HEART, true, lord.getX(), lord.getY() + lord.getBbHeight() + 0.4, lord.getZ(), 2, 0.3, 0.2, 0.3, 0);
                }
            }
        }
        if (now < f.nextMech) {
            return;
        }
        f.nextMech = now + 400;
        if (!f.copies.isEmpty()) {
            return;
        }
        BlockPos c = AbyssBuild.mark("arena_" + f.layer);
        if (c == null) {
            return;
        }
        flash(f, "objective.skyabyss.abyss_v2.flash_beat", now, 200);
        level.playSound(null, lord.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 1.6f, 0.7f);
        // 四つの所に本物と写しを散らす
        int real = level.random.nextInt(4);
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2 + Math.PI / 4;
            double x = c.getX() + 0.5 + Math.cos(a) * 7, z = c.getZ() + 0.5 + Math.sin(a) * 7;
            if (i == real) {
                lord.teleportTo(x, c.getY(), z);
                continue;
            }
            Entity copy = AbyssMobSpawner.spawnAt(level, MOB[2], x, c.getY(), z, 3);
            if (copy instanceof LivingEntity le) {
                le.addTag(COPY_TAG);
                le.addTag(LayerFights.TAG);
                var sc = le.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE);
                if (sc != null) {
                    sc.setBaseValue(SCALE[2]);
                }
                le.setCustomName(lord.getCustomName());
                f.copies.add(le.getUUID());
                LayerFights.later(level.getServer(), 300, () -> {
                    Entity gone = level.getEntity(le.getUUID());
                    if (gone != null) {
                        gone.discard();
                    }
                });
            }
        }
    }

    // ---------------------------------------------------------------- 灰: 闇の波 (番人の戦いでも使う)
    static void ash(ServerLevel level, Fight f, LivingEntity lord, List<ServerPlayer> in, long now) {
        if (now < f.nextMech) {
            if (f.darkUntil > now) {
                flash(f, "objective.skyabyss.abyss_v2.flash_dark", now, 20);
            }
            return;
        }
        f.nextMech = now + 420;
        BlockPos c = AbyssBuild.mark("arena_" + f.layer);
        if (c == null) {
            return;
        }
        // 予告: 主が三度吠え、床の縁に赤い輪 (45 tick)。終わると闇
        for (ServerPlayer p : in) {
            Notice.bar(p, Component.translatable("message.skyabyss.abyss_v2.dark_coming"));
        }
        level.playSound(null, lord.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 1.4f, 0.7f);
        Telegraph.in(level, TelegraphShape.ring(c.getX() + 0.5, c.getZ() + 0.5, Layers.ARENA_R - 2.5, Layers.ARENA_R)).floor(c.getY()).warn(45).danger().owner(lord)
                .onImpact(lvl -> darkWave(lvl, f, Vec3.atBottomCenterOf(c), inArena(lvl, f.layer)))
                .onHit((lvl, p) -> p.hurt(lvl.damageSources().mobAttack(lord), 4f)).start();
    }

    // 闇の波: まわりの灯りを全部消し、人に闇 (8 秒)。この間、灯り喰いの主は光の中でしか刃が通らない
    public static void darkWave(ServerLevel level, @Nullable Fight f, Vec3 center, List<ServerPlayer> in) {
        AbyssLight.snuffNear(level, center, 20);
        if (f != null) {
            for (int k = 0; k < 4; k++) {
                f.lampUntil[k] = 0;
                setLamp(level, f.layer, k, false);
            }
            f.darkUntil = level.getGameTime() + 200;
            flash(f, "objective.skyabyss.abyss_v2.flash_dark", level.getGameTime(), 200);
        }
        for (ServerPlayer p : in) {
            p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 160, 0, false, false));
        }
        level.playSound(null, center.x, center.y, center.z, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 2f, 0.5f);
    }
}
