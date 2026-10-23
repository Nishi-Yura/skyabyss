package jp.revati.skyabyss.story.ch5.layers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.abyss.mobs.AbyssMobSpawner;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.story.ch5.AbyssBuild;
import jp.revati.skyabyss.story.ch5.AbyssWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 層の戦い (docs/design/36 の 8 章): 巣 (三つの波)・縄張り (大きい敵一体と取り巻き)・待ち伏せ・前の私の影。
//   巣 nest_bone・nest_storm: 払っていない人が 7 以内に入ると三つの波。全部倒すと、そのとき巣の 16 以内にいた人とそのパーティに "clear:<巣>"
//   縄張り terr_salt・terr_ash: 払っていない人が 10 以内に入ると主 (体力 3 倍・大きさ 1.5) と取り巻き。主が倒れると 24 以内の人とパーティに "clear:<縄張り>"
//     縄張りの主はボスバー (二段。半分で二段目) と三つの予告つきの技 (叩きつけ r3.8・突進 長さ 10・足もとの吐きかけ r2.2。eliteMove)
//   待ち伏せ ambush_bone (骨の広間)・bridge_bone_a/b (肋の橋): 人ごとに一度ずつ (5 分あけてまた)
//   だれもいなくなって 30 秒たつと、巣と縄張りの敵は消えて元に戻る
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class LayerFights {
    public static final String TAG = "skyabyss_layer_fight";
    static final String[][][] NEST_WAVES = {
            // 骨の巣
            {{"bone_picker", "bone_picker", "bone_picker", "bone_picker"}, {"bone_picker", "bone_picker", "bone_picker", "bone_flute"},
                    {"rib_guard", "bone_picker", "bone_picker"}},
            // 雷の巣
            {{"charged_moth", "charged_moth", "charged_moth", "charged_moth"}, {"charged_moth", "charged_moth", "scorched_bird", "charged_moth"},
                    {"lightning_idol", "charged_moth", "charged_moth", "scorched_bird"}},
    };
    static final String[] NESTS = {"nest_bone", "nest_storm"};
    static final String[] TERRS = {"terr_salt", "terr_ash"};
    static final String[][] TERR_MOBS = {{"white_shell_keeper", "salt_husk", "salt_husk"}, {"ember_keeper", "soot_child", "soot_child"}};

    static final class Run {
        final String name;
        int wave = -1;
        final List<UUID> mobs = new ArrayList<>();
        long emptySince = -1;
        long nextAt;
        // 縄張りの主のボスバーと技
        @Nullable
        net.minecraft.server.level.ServerBossEvent bar;
        long nextMove;
        int moveN;

        Run(String name) {
            this.name = name;
        }
    }

    private static final Map<String, Run> RUNS = new HashMap<>();
    private static final Map<String, Long> AMBUSHED = new HashMap<>();
    private static final List<Pending> LATER = new ArrayList<>();

    private record Pending(int at, Runnable run) {
    }

    private LayerFights() {
    }

    static void later(MinecraftServer server, int ticks, Runnable r) {
        LATER.add(new Pending(server.getTickCount() + ticks, r));
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int t = server.getTickCount();
        if (!LATER.isEmpty()) {
            List<Pending> due = new ArrayList<>();
            LATER.removeIf(e -> e.at() <= t && due.add(e));
            due.forEach(e -> e.run().run());
        }
        if (t % 10 != 4) {
            return;
        }
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        if (level == null || level.players().isEmpty() || !AbyssBuild.built(server)) {
            return;
        }
        long now = level.getGameTime();
        for (int k = 0; k < NESTS.length; k++) {
            nest(level, k, now);
        }
        for (int k = 0; k < TERRS.length; k++) {
            territory(level, k, now);
        }
        ambush(level, now);
    }

    // ---------------------------------------------------------------- 巣
    static void nest(ServerLevel level, int k, long now) {
        String name = NESTS[k];
        BlockPos at = AbyssBuild.mark(name);
        if (at == null || !level.isLoaded(at)) {
            return;
        }
        LayerData d = LayerData.get(level.getServer());
        List<ServerPlayer> inside = near(level, at, 16);
        Run run = RUNS.get(name);
        if (run == null) {
            for (ServerPlayer p : inside) {
                if (p.blockPosition().closerThan(at, 7) && !d.has(p.getUUID(), "clear:" + name)) {
                    run = new Run(name);
                    RUNS.put(name, run);
                    for (ServerPlayer q : inside) {
                        Notice.alert(q, "message.skyabyss.abyss_v2.nest_wake." + name);
                    }
                    break;
                }
            }
            if (run == null) {
                return;
            }
        }
        if (idle(level, run, inside, now)) {
            return;
        }
        if (alive(level, run) > 0 || now < run.nextAt) {
            return;
        }
        run.wave++;
        if (run.wave >= NEST_WAVES[k].length) {
            for (ServerPlayer p : inside) {
                for (UUID who : AbyssMarks.withParty(p)) {
                    d.set(who, "clear:" + name);
                }
                Notice.objective(p, "message.skyabyss.abyss_v2.nest_clear." + name);
                Quests.signalWithParty(level, List.of(p), "abyss_clear_" + name);
            }
            RUNS.remove(name);
            return;
        }
        for (ServerPlayer p : inside) {
            p.displayClientMessage(Component.translatable("message.skyabyss.abyss_v2.wave", run.wave + 1, NEST_WAVES[k].length), true);
        }
        int n = 0;
        int extra = Math.max(0, inside.size() - 1);
        List<String> ids = new ArrayList<>(List.of(NEST_WAVES[k][run.wave]));
        for (int i = 0; i < extra; i++) {
            ids.add(ids.get(0));
        }
        for (String id : ids) {
            double a = (n++) * Math.PI * 2 / ids.size();
            Entity e = AbyssMobSpawner.spawnAt(level, id, at.getX() + 0.5 + Math.cos(a) * 4, at.getY(), at.getZ() + 0.5 + Math.sin(a) * 4, 1 + k);
            if (e != null) {
                e.addTag(TAG);
                run.mobs.add(e.getUUID());
            }
        }
        run.nextAt = now + 40;
        level.playSound(null, at, SoundEvents.SKELETON_HORSE_AMBIENT, SoundSource.HOSTILE, 1.2f, 0.6f);
    }

    // ---------------------------------------------------------------- 縄張り
    static void territory(ServerLevel level, int k, long now) {
        String name = TERRS[k];
        BlockPos at = AbyssBuild.mark(name);
        if (at == null || !level.isLoaded(at)) {
            return;
        }
        LayerData d = LayerData.get(level.getServer());
        List<ServerPlayer> inside = near(level, at, 24);
        Run run = RUNS.get(name);
        if (run == null) {
            for (ServerPlayer p : inside) {
                if (p.blockPosition().closerThan(at, 10) && !d.has(p.getUUID(), "clear:" + name)) {
                    run = new Run(name);
                    RUNS.put(name, run);
                    String[] ids = TERR_MOBS[k];
                    LivingEntity boss = elite(level, ids[0], at, 3.0, 1.5, "abyss_v2.skyabyss.elite." + name);
                    if (boss != null) {
                        run.mobs.add(boss.getUUID());
                    }
                    for (int i = 1; i < ids.length; i++) {
                        Entity e = AbyssMobSpawner.spawnAt(level, ids[i], at.getX() + 0.5 + (i == 1 ? 4 : -4), at.getY(), at.getZ() + 0.5, 2);
                        if (e != null) {
                            e.addTag(TAG);
                        }
                    }
                    for (ServerPlayer q : inside) {
                        Notice.alert(q, "message.skyabyss.abyss_v2.terr_wake." + name);
                    }
                    break;
                }
            }
            return;
        }
        if (idle(level, run, inside, now)) {
            return;
        }
        Entity boss = run.mobs.isEmpty() ? null : level.getEntity(run.mobs.get(0));
        if (boss instanceof LivingEntity le && le.isAlive()) {
            eliteTick(level, run, le, inside, now);
        }
        if (boss == null || !boss.isAlive()) {
            if (run.bar != null) {
                run.bar.removeAllPlayers();
            }
            for (ServerPlayer p : inside) {
                for (UUID who : AbyssMarks.withParty(p)) {
                    d.set(who, "clear:" + name);
                }
                Notice.objective(p, "message.skyabyss.abyss_v2.terr_clear." + name);
                Quests.signalWithParty(level, List.of(p), "abyss_clear_" + name);
            }
            RUNS.remove(name);
        }
    }

    // 縄張りの主: ボスバーと技 (0.5 秒ごと)
    static void eliteTick(ServerLevel level, Run run, LivingEntity boss, List<ServerPlayer> inside, long now) {
        if (run.bar == null) {
            run.bar = new net.minecraft.server.level.ServerBossEvent(boss.getCustomName() != null ? boss.getCustomName() : boss.getName(),
                    net.minecraft.world.BossEvent.BossBarColor.RED, net.minecraft.world.BossEvent.BossBarOverlay.NOTCHED_10);
            run.nextMove = now + 40;
        }
        float ratio = boss.getHealth() / boss.getMaxHealth();
        run.bar.setProgress(Math.max(0f, Math.min(1f, ratio)));
        int phase = ratio > 0.5f ? 0 : 1;
        jp.revati.skyabyss.story.ch5.AbyssBossBars.phase(run.bar, phase);
        for (ServerPlayer p : inside) {
            run.bar.addPlayer(p);
        }
        for (ServerPlayer p : List.copyOf(run.bar.getPlayers())) {
            if (!inside.contains(p)) {
                run.bar.removePlayer(p);
            }
        }
        if (now < run.nextMove || inside.isEmpty()) {
            return;
        }
        ServerPlayer target = LordMoves.nearest(boss, inside);
        if (target == null || target.distanceTo(boss) > 18) {
            return;
        }
        int kind = run.moveN++ % 3;
        double y = boss.getY();
        double bx = boss.getX(), bz = boss.getZ();
        double ang = Math.atan2(target.getZ() - bz, target.getX() - bx);
        float dmg = (run.name.equals("terr_ash") ? 7f : 6f) * (1f + 0.2f * phase);
        int li = run.name.equals("terr_ash") ? 3 : 1;
        int warn;
        level.playSound(null, boss.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 0.9f, 1.3f);
        LordMoves.element(level, li, bx, y + 1, bz, 12, 0.6);
        Telegraph.Hit hit = (lvl, p) -> {
            p.hurt(lvl.damageSources().mobAttack(boss), dmg);
            if (li == 3) {
                p.igniteForTicks(40);
            } else {
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 40, 1, false, true));
            }
        };
        switch (kind) {
            case 0 -> {
                warn = 26;
                LordAnim.windup(boss, "attack_elite_slam", warn);
                Telegraph.in(level, TelegraphShape.circle(bx, bz, 3.8)).floor(y).warn(warn).danger().owner(boss)
                        .onImpact(lvl -> {
                            LordAnim.strike(boss, "attack_elite_slam");
                            LordMoves.element(lvl, li, bx, y + 0.3, bz, 30, 2);
                            lvl.playSound(null, bx, y, bz, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.7f, 1.2f);
                        }).onHit(hit).start();
            }
            case 1 -> {
                warn = 24;
                LordAnim.windup(boss, "attack_elite_charge", warn);
                Telegraph.in(level, TelegraphShape.line(bx, bz, ang, 10, 1.4)).floor(y).warn(warn).danger().owner(boss)
                        .onImpact(lvl -> {
                            LordAnim.strike(boss, "attack_elite_charge");
                            for (double dd = 1; dd <= 10; dd += 1.5) {
                                LordMoves.element(lvl, li, bx + Math.cos(ang) * dd, y + 0.4, bz + Math.sin(ang) * dd, 3, 0.3);
                            }
                            boss.teleportTo(bx + Math.cos(ang) * 8, y, bz + Math.sin(ang) * 8);
                        }).onHit(hit).start();
            }
            default -> {
                warn = 28;
                LordAnim.windup(boss, "attack_elite_spit", warn);
                for (ServerPlayer p : inside) {
                    double px = p.getX(), pz = p.getZ();
                    Telegraph.in(level, TelegraphShape.circle(px, pz, 2.2)).floor(p.getY()).warn(warn).danger().owner(boss)
                            .onImpact(lvl -> {
                                LordAnim.strike(boss, "attack_elite_spit");
                                LordMoves.element(lvl, li, px, p.getY() + 0.3, pz, 16, 1);
                            }).onHit(hit).start();
                }
            }
        }
        run.nextMove = now + warn + (phase == 0 ? 70 : 50);
    }

    // 主を一体 (体力の倍・大きさ・名前)
    @Nullable
    public static LivingEntity elite(ServerLevel level, String id, BlockPos at, double hp, double scale, String nameKey) {
        Entity e = AbyssMobSpawner.spawnAt(level, id, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 2);
        if (!(e instanceof LivingEntity le)) {
            return null;
        }
        e.addTag(TAG);
        AttributeInstance h = le.getAttribute(Attributes.MAX_HEALTH);
        // 縄張りの主は雑魚の下げ (MobTuning) を外す (ボスバーのある主)
        jp.revati.skyabyss.combat.MobTuning.untune(le);
        if (h != null) {
            h.setBaseValue(h.getBaseValue() * hp);
            le.setHealth(le.getMaxHealth());
        }
        AttributeInstance s = le.getAttribute(Attributes.SCALE);
        if (s != null) {
            s.setBaseValue(scale);
        }
        le.setCustomName(Component.translatable(nameKey));
        le.setCustomNameVisible(true);
        if (le instanceof net.minecraft.world.entity.Mob m) {
            m.setPersistenceRequired();
        }
        return le;
    }

    // だれもいなくなって 30 秒: 敵を消して元に戻す (true = 戻した)
    static boolean idle(ServerLevel level, Run run, List<ServerPlayer> inside, long now) {
        if (!inside.isEmpty()) {
            run.emptySince = -1;
            return false;
        }
        if (run.emptySince < 0) {
            run.emptySince = now;
        }
        if (now - run.emptySince < 600) {
            return true;
        }
        for (UUID u : run.mobs) {
            Entity e = level.getEntity(u);
            if (e != null) {
                e.discard();
            }
        }
        if (run.bar != null) {
            run.bar.removeAllPlayers();
        }
        RUNS.remove(run.name);
        return true;
    }

    static int alive(ServerLevel level, Run run) {
        int n = 0;
        for (UUID u : run.mobs) {
            Entity e = level.getEntity(u);
            if (e != null && e.isAlive()) {
                n++;
            }
        }
        return n;
    }

    static List<ServerPlayer> near(ServerLevel level, BlockPos at, double r) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : List.copyOf(level.players())) {
            if (p.isAlive() && !p.isSpectator() && p.blockPosition().closerThan(at, r) && Math.abs(p.getY() - at.getY()) < 8) {
                out.add(p);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- 待ち伏せ
    static void ambush(ServerLevel level, long now) {
        BlockPos hall = AbyssBuild.mark("ambush_bone");
        BlockPos a = AbyssBuild.mark("bridge_bone_a");
        BlockPos b = AbyssBuild.mark("bridge_bone_b");
        for (ServerPlayer p : List.copyOf(level.players())) {
            if (hall != null && p.blockPosition().closerThan(hall, 3.5) && fresh(p, "hall", now)) {
                Notice.alert(p, "message.skyabyss.abyss_v2.ambush_bone");
                for (int i = 0; i < 3; i++) {
                    double ang = i * 2.1;
                    AbyssMobSpawner.spawnAt(level, "bone_picker", hall.getX() + 0.5 + Math.cos(ang) * 4, hall.getY(), hall.getZ() + 0.5 + Math.sin(ang) * 2, 1);
                }
            }
            if (a != null && b != null && LayerPuzzles.distToSegment(p.position(), a.getBottomCenter(), b.getBottomCenter()) < 1.6
                    && Math.abs(p.getY() - a.getY()) < 1.5 && fresh(p, "bridge", now)) {
                Notice.alert(p, "message.skyabyss.abyss_v2.ambush_bridge");
                for (BlockPos end : new BlockPos[] {a, b}) {
                    for (int i = 0; i < 2; i++) {
                        AbyssMobSpawner.spawnAt(level, "bone_picker", end.getX() + 0.5, end.getY(), end.getZ() + 0.5, 1);
                    }
                }
            }
        }
    }

    static boolean fresh(ServerPlayer p, String what, long now) {
        String k = p.getUUID() + what;
        Long last = AMBUSHED.get(k);
        if (last != null && now - last < 6000) {
            return false;
        }
        AMBUSHED.put(k, now);
        return true;
    }

    // ---------------------------------------------------------------- 前の私の影 (雷の層の違う戸の奥)
    static void shadow(ServerPlayer p, BlockPos closet) {
        ServerLevel level = p.serverLevel();
        Notice.narrate(p, "message.skyabyss.abyss_v2.shadow_1");
        later(level.getServer(), 50, () -> Notice.narrate(p, "message.skyabyss.abyss_v2.shadow_2"));
        later(level.getServer(), 100, () -> {
            Entity e = AbyssMobSpawner.spawnAt(level, "past_shadow", closet.getX() + 0.5, closet.getY(), closet.getZ() + 0.5, 2);
            if (e != null) {
                e.addTag(TAG);
                e.addTag("skyabyss_storm_shadow");
            }
        });
    }

    @SubscribeEvent
    static void onDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (event.getEntity().getTags().contains("skyabyss_storm_shadow") && event.getSource().getEntity() instanceof ServerPlayer p) {
            if (LayerData.get(p.server).set(p.getUUID(), "took:shadow_storm")) {
                Notice.narrate(p, "message.skyabyss.abyss_v2.shadow_gone");
            }
            Quests.signalWithParty(p.serverLevel(), List.of(p), "abyss_shadow_storm");
        }
    }

    // 開発用・自己テスト
    public static void resetForTest(ServerLevel level) {
        for (Run r : RUNS.values()) {
            for (UUID u : r.mobs) {
                Entity e = level.getEntity(u);
                if (e != null) {
                    e.discard();
                }
            }
        }
        RUNS.clear();
        AMBUSHED.clear();
        for (Entity e : level.getEntitiesOfClass(Entity.class, new AABB(-200, -320, -200, 200, 260, 200), e -> e.getTags().contains(TAG))) {
            e.discard();
        }
    }
}
