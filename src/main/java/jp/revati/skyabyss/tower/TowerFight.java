package jp.revati.skyabyss.tower;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.party.Downed;
import jp.revati.skyabyss.rpg.skill.SkillManager;
import jp.revati.skyabyss.story.finale.LampIsle;
import jp.revati.skyabyss.story.finale.mob.CloudMobsSetup;
import jp.revati.skyabyss.tower.mob.TowerEye;
import jp.revati.skyabyss.tower.mob.TowerMaster;
import jp.revati.skyabyss.tower.mob.TowerMoon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import org.joml.Vector3f;

// あいだの塔の主「あいだに座すもの」との一戦の進め方 (docs/design/54 の 2.5。手本は Wynncraft の Annihilation)。TowerArena が毎 tick 呼ぶ。
//   主       北の広場の床に座る (TowerMaster)。体力 = HP × (1 + 0.85 × (人数 − 1))。目覚め (INTRO) のあいだは傷が通らない
//   技       一度に一つ。予告 (地面の印) のあいだに外へ出れば当たらない。休みは引き合いの高まり (60%・30%) で短くなり、傷が 15% ずつ増える
//              右手の払い (天の手のまわり) / 左手の払い (淵の手のまわり、重い) / 淵の吐息 (広場・真ん中の筋・遠い人の三つ) /
//              三つの印の光 (橋の真ん中の印の上に順に光の柱) / 追う印 (赤い輪が一人を追う) / 退けの声 (近くの人を押し戻し、マナを吸う) /
//              星屑の雨 (80% から。3 回) / 合掌 (月 1 回目のあと。北の端を挟む) / 天の三矢 (40% から。一つ目は離れ、二つ目三つ目は皆で受ける) /
//              鎖の子 (4 手に 1 回。打たれると回復が 25% 落ちる)
//   群れ     70% で一度。橋に天と淵の群れ、左右の台に主を癒す繋ぎ手 2 体 (動かない)
//   落ちる月 50% と 20% で体力が止まり、主が月を呼ぶ。月は橋を南へ進んで大きくなる。月が生きているあいだ主は傷を受けない。
//            南の端に着いたら全滅 (failed)。繋ぎ手がいなければ 2 体立てる (月を癒す)
//   側の目   左右の台の目 (小 4・大 2) が狙った人へ光の糸 → 光線。間に壁があれば当たらない。壊せる
//   勝ち     主が倒れたら won。TowerArena / Towers が記録と帰り道を進める
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TowerFight {
    public static final double PARTY_STEP = 0.85;
    public static final int INTRO = 100;
    static final int REST = 50;
    // 落ちる月が北の広場から南の端まで渡りきる tick
    public static final int MOON_TICKS = 1800;
    static final double MOON_START_Z = -34;
    static final double MOON_HOVER = 1.5;
    static final String PHLEGM_TAG = "skyabyss_tower_phlegm";
    static final int PHLEGM_TICKS = 160;
    static final Map<UUID, Long> PHLEGM = new HashMap<>();
    static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0f, 0.82f, 0.4f), 1.6f);
    static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.6f, 0.35f, 1.0f), 1.6f);
    static final DustParticleOptions WHITE = new DustParticleOptions(new Vector3f(1.0f, 0.97f, 0.9f), 1.6f);
    static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(1.0f, 0.2f, 0.15f), 1.5f);
    static final DustParticleOptions BIND = new DustParticleOptions(new Vector3f(0.55f, 1.0f, 0.75f), 1.0f);

    final TowerRun run;
    final int n;
    final RandomSource rng = RandomSource.create();
    final ServerBossEvent bar = jp.revati.skyabyss.music.MusicArea.bossBar(new ServerBossEvent(Component.translatable("entity.skyabyss.tower_master"),
            BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10));
    TowerMaster master;
    final List<Eye> eyes = new ArrayList<>();
    final List<Mob> binders = new ArrayList<>();
    final List<Mob> adds = new ArrayList<>();
    final List<Zone> zones = new ArrayList<>();
    final List<Chaser> chasers = new ArrayList<>();
    final List<Later> laters = new ArrayList<>();
    final Set<String> told = new HashSet<>();
    TowerMoon moon;
    long moonBorn;
    int moonsDone;
    int stoke;
    boolean swarmDone;
    String move = "";
    long moveEnd;
    long restUntil;
    int moves;
    String last = "";
    long start = -1;
    boolean woke;
    // 全滅の理由 (落ちる月が渡りきった)。null なら続く
    String failed;
    boolean won;
    long wonAt;

    TowerFight(TowerRun run) {
        this.run = run;
        this.n = Math.max(1, Math.min(Towers.MAX_SQUAD, run.members.size()));
    }

    // 主の体力 (人数で伸びる。心臓と同じ式を 12 人まで)
    public static double masterHp(int players) {
        return TowerMaster.HP * (1 + PARTY_STEP * (Math.max(1, Math.min(Towers.MAX_SQUAD, players)) - 1));
    }

    // 体力が止まる所 (落ちる月の前)。月を 2 回壊したら止まらない
    static float floorFrac(int moonsDone) {
        return moonsDone == 0 ? 0.5f : moonsDone == 1 ? 0.2f : 0f;
    }

    // 区画の橋の、印の座標系の点 (x・z は橋の真ん中から、y は橋の上面から)
    Vec3 rel(double x, double y, double z) {
        BlockPos o = TowerWorld.origin(run.slot);
        return new Vec3(o.getX() + 0.5 + x, o.getY() + y, o.getZ() + 0.5 + z);
    }

    double stokeMult() {
        return 1 + 0.15 * stoke;
    }

    double pace() {
        return stoke == 0 ? 1.0 : stoke == 1 ? 0.8 : 0.65;
    }

    // ---------------------------------------------------------------- 湧かせる
    void spawn(ServerLevel level) {
        start = level.getGameTime();
        TowerMaster m = CloudMobsSetup.TOWER_MASTER.get().create(level);
        if (m == null) {
            return;
        }
        Vec3 at = rel(0, 1, -52);
        m.moveTo(at.x, 300, at.z, 0f, 0f);
        m.tuneCloud(1, masterHp(n), TowerMaster.DMG);
        m.moveTo(at.x, at.y, at.z, 0f, 0f);
        m.addTag(TowerWorld.TAG);
        m.addTag(TowerWorld.TAG + "_" + run.id);
        m.setShielded(true);
        m.setFloor(m.getMaxHealth() * floorFrac(0));
        level.addFreshEntity(m);
        master = m;
        String[][] marks = {{"eye_l_1", "s"}, {"eye_r_1", "s"}, {"eye_l_2", "s"}, {"eye_r_2", "s"}, {"eye_big_l", "b"}, {"eye_big_r", "b"}};
        int i = 0;
        for (String[] mk : marks) {
            boolean big = mk[1].equals("b");
            Vec3 p = run.markCenter(mk[0]).add(0, TowerEye.HOVER, 0);
            Mob e = TowerWorld.foe(level, big ? CloudMobsSetup.TOWER_EYE_BIG.get() : CloudMobsSetup.TOWER_EYE.get(), p,
                    big ? TowerEye.HP_BIG : TowerEye.HP_SMALL, TowerEye.DMG, n, run.id);
            if (e instanceof TowerEye te) {
                te.setHome(p);
                eyes.add(new Eye(te, big, start + INTRO + 60 + i * 37L));
            }
            i++;
        }
    }

    // ---------------------------------------------------------------- 毎 tick
    void tick(ServerLevel level, long now) {
        if (master == null || start < 0) {
            return;
        }
        List<ServerPlayer> here = run.present();
        if (now % 20 == 0) {
            for (ServerPlayer p : run.online()) {
                if (p.level() == level && !won) {
                    bar.addPlayer(p);
                } else {
                    bar.removePlayer(p);
                }
            }
        }
        bar.setProgress(Math.max(0f, master.getHealth() / master.getMaxHealth()));
        for (Later l : List.copyOf(laters)) {
            if (now >= l.at) {
                laters.remove(l);
                l.run.run();
            }
        }
        if (won) {
            return;
        }
        if (!master.isAlive()) {
            win(level, now);
            return;
        }
        long since = now - start;
        // 目覚め
        if (since < INTRO) {
            if (since == 20) {
                for (ServerPlayer p : here) {
                    Notice.narrate(p, "message.skyabyss.tower.intro");
                }
            }
            if (since == 40) {
                master.playAction("roar");
                sound(level, master.chest(), CloudMobsSetup.PILGRIM_ROAR.get(), 6f, 0.6f);
            }
            return;
        }
        if (!woke) {
            woke = true;
            master.setShielded(false);
            restUntil = now + 40;
        }
        tickZones(level, now, here);
        tickChasers(level, now);
        tickEyes(level, now, here);
        tickBinders(level, now);
        tickMoon(level, now, here);
        if (failed != null) {
            return;
        }
        float f = master.getHealth() / master.getMaxHealth();
        if (!swarmDone && f <= 0.7f) {
            swarm(level, here);
        }
        if (stoke < 1 && f <= 0.6f || stoke < 2 && f <= 0.3f) {
            stoke++;
            master.playAction("roar");
            sound(level, master.chest(), CloudMobsSetup.PILGRIM_ROAR.get(), 6f, 0.8f);
            level.sendParticles(RED, master.head().x, master.head().y, master.head().z + 3, 80, 9, 9, 1, 0);
            for (ServerPlayer p : here) {
                Notice.narrate(p, "message.skyabyss.tower.stoke");
            }
        }
        if (moon == null && moonsDone < 2 && master.getHealth() <= master.getMaxHealth() * floorFrac(moonsDone) + 0.5f) {
            startMoon(level, now, here);
        }
        // 技
        if (!move.isEmpty() && now >= moveEnd) {
            move = "";
            restUntil = now + (long) (REST * pace() * (moon != null ? 1.6 : 1.0));
        }
        if (move.isEmpty() && now >= restUntil && !targets(here).isEmpty()) {
            begin(level, now, pick(f), here);
        }
    }

    // 立っている人 (倒れている人は狙わない)
    static List<ServerPlayer> targets(List<ServerPlayer> here) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : here) {
            if (!Downed.isDowned(p) && !p.isCreative()) {
                out.add(p);
            }
        }
        return out;
    }

    String pick(float f) {
        if (moon != null) {
            return rng.nextBoolean() ? "stars" : "breath";
        }
        moves++;
        if (moves % 4 == 0 && adds.stream().filter(LivingEntity::isAlive).count() < 2 + n) {
            return "kids";
        }
        List<String> pool = new ArrayList<>(List.of("rake", "flense", "breath", "beams", "sigil", "exhort"));
        if (f <= 0.8f) {
            pool.add("stars");
        }
        if (moonsDone >= 1) {
            pool.add("clap");
        }
        if (f <= 0.4f) {
            pool.add("arrows");
        }
        pool.remove(last);
        return pool.get(rng.nextInt(pool.size()));
    }

    void begin(ServerLevel level, long now, String m, List<ServerPlayer> here) {
        List<ServerPlayer> tg = targets(here);
        if (tg.isEmpty()) {
            return;
        }
        move = m;
        last = m;
        int len = 40;
        double k = stokeMult();
        switch (m) {
            case "rake" -> {
                master.playAction("rake");
                sound(level, heavenHand(), CloudMobsSetup.KEEPER_SLAM_TELL.get(), 4f, 0.6f);
                zones.add(Zone.circle(heavenHand(), 13, now + 30, 1.0 * k, GOLD).push(1.2));
                len = 52;
            }
            case "flense" -> {
                master.playAction("flense");
                sound(level, abyssHand(), CloudMobsSetup.KEEPER_SLAM_TELL.get(), 4f, 0.45f);
                zones.add(Zone.circle(abyssHand(), 13, now + 36, 1.5 * k, VIOLET).push(1.4));
                len = 60;
            }
            case "clap" -> {
                master.playAction("clap");
                sound(level, rel(0, 1, -40), CloudMobsSetup.KEEPER_WAVE_TELL.get(), 5f, 0.6f);
                zones.add(Zone.box(rel(-24, 1, -50), rel(24, 1, -28), now + 50, 2.2 * k, WHITE).push(1.6));
                len = 72;
            }
            case "breath" -> {
                master.playAction("breath");
                sound(level, master.head(), CloudMobsSetup.EYE_RAIN_TELL.get(), 5f, 0.5f);
                int kind = rng.nextInt(3);
                if (kind == 0) {
                    zones.add(Zone.box(rel(-12, 1, -50), rel(12, 1, -24), now + 30, 1.3 * k, VIOLET));
                } else if (kind == 1) {
                    zones.add(Zone.box(rel(-2.5, 1, -30), rel(2.5, 1, 50), now + 30, 1.6 * k, VIOLET));
                } else {
                    ServerPlayer far = tg.get(0);
                    for (ServerPlayer p : tg) {
                        if (p.distanceToSqr(master) > far.distanceToSqr(master)) {
                            far = p;
                        }
                    }
                    zones.add(Zone.circle(far.position(), 4, now + 35, 1.4 * k, VIOLET).follow(far, now + 20));
                }
                len = 60;
            }
            case "beams" -> {
                master.playAction("sigil");
                for (int i = 0; i < 3; i++) {
                    Vec3 s = run.markCenter("sigil_" + (i + 1));
                    zones.add(Zone.circle(s, 3.5, now + 40 + i * 15L, 1.8 * k, GOLD).pillar());
                }
                sound(level, master.head(), CloudMobsSetup.PILGRIM_RAIN_TELL.get(), 5f, 0.7f);
                len = 80;
            }
            case "sigil" -> {
                master.playAction("sigil");
                int count = moonsDone >= 1 && tg.size() >= 3 ? 2 : 1;
                List<ServerPlayer> pool = new ArrayList<>(tg);
                for (int i = 0; i < count && !pool.isEmpty(); i++) {
                    ServerPlayer p = pool.remove(rng.nextInt(pool.size()));
                    Vec3 st = p.position().add((rng.nextDouble() - 0.5) * 8, 0, (rng.nextDouble() - 0.5) * 8);
                    chasers.add(new Chaser(p.getUUID(), st.x, p.getY(), st.z, now + 30, now + 190));
                    tell(p, "sigil");
                }
                sound(level, master.head(), CloudMobsSetup.EYE_STARE.get(), 5f, 0.6f);
                len = 50;
            }
            case "exhort" -> {
                master.playAction("roar");
                sound(level, master.chest(), CloudMobsSetup.KEEPER_SILENCE.get(), 5f, 0.6f);
                level.sendParticles(VIOLET, master.chest().x, master.chest().y, master.chest().z + 4, 60, 8, 6, 2, 0);
                laters.add(new Later(now + 30, () -> exhort(level)));
                len = 52;
            }
            case "stars" -> {
                master.playAction("stars");
                sound(level, master.head(), CloudMobsSetup.PILGRIM_RAIN_TELL.get(), 5f, 1.0f);
                int extra = Math.min(8, 2 + n / 2);
                for (int w = 0; w < 3; w++) {
                    long at = now + 25 + w * 30L;
                    for (ServerPlayer p : tg) {
                        zones.add(Zone.circle(p.position(), 2.5, at, 0.9 * k, GOLD).follow(p, at - 18));
                    }
                    for (int i = 0; i < extra; i++) {
                        zones.add(Zone.circle(rel(-6 + rng.nextDouble() * 12, 1, -42 + rng.nextDouble() * 78), 2.5, at, 0.9 * k, GOLD));
                    }
                }
                len = 100;
            }
            case "arrows" -> {
                master.playAction("arrows");
                ServerPlayer p = tg.get(rng.nextInt(tg.size()));
                for (ServerPlayer q : here) {
                    Notice.narrate(q, "message.skyabyss.tower.arrows", p.getDisplayName());
                }
                double share = 1.2 * Math.min(tg.size(), 3) * k;
                zones.add(Zone.circle(p.position(), 5, now + 40, 1.6 * k, WHITE).follow(p, now + 25));
                zones.add(Zone.circle(p.position(), 4, now + 80, share, GOLD).follow(p, now + 65).share());
                zones.add(Zone.circle(p.position(), 4, now + 110, share, GOLD).follow(p, now + 95).share());
                sound(level, master.head(), CloudMobsSetup.PILGRIM_BEAM.get(), 5f, 0.6f);
                len = 120;
            }
            case "kids" -> {
                master.playAction("sigil");
                sound(level, rel(0, 1, -40), CloudMobsSetup.KEEPER_CALL.get(), 5f, 0.7f);
                for (int i = 0; i < 1 + n / 3; i++) {
                    Mob a = TowerWorld.foe(level, CloudMobsSetup.ASH_STRIKER.get(), rel(-10 + rng.nextDouble() * 20, 1, -44 + rng.nextDouble() * 10), 160, 16, n, run.id);
                    if (a != null) {
                        a.addTag(PHLEGM_TAG);
                        adds.add(a);
                        level.sendParticles(VIOLET, a.getX(), a.getY() + 1, a.getZ(), 20, 0.4, 0.8, 0.4, 0);
                    }
                }
                len = 40;
            }
            default -> {
            }
        }
        moveEnd = now + len;
    }

    // 天の手 (金・東。橋から見て右) と淵の手 (黒い石・西) の置き場
    Vec3 heavenHand() {
        return run.markCenter("hand_r");
    }

    Vec3 abyssHand() {
        return run.markCenter("hand_l");
    }

    // 退けの声: 主から 34 ブロックの内の人を南へ押し戻し、マナを 3 割吸う
    void exhort(ServerLevel level) {
        if (master == null || !master.isAlive()) {
            return;
        }
        for (ServerPlayer p : run.present()) {
            if (p.position().subtract(master.position()).horizontalDistance() < 34) {
                p.push(0, 0.5, 1.6);
                p.hurtMarked = true;
                SkillManager.setMana(p, SkillManager.get(p).mana * 0.7);
                tell(p, "exhort");
            }
        }
        level.sendParticles(ParticleTypes.SONIC_BOOM, master.getX(), master.getY() + 2, master.getZ() + 16, 6, 8, 0.5, 2, 0);
    }

    // ---------------------------------------------------------------- 70%: 天と淵の群れと、主を癒す繋ぎ手
    void swarm(ServerLevel level, List<ServerPlayer> here) {
        swarmDone = true;
        int count = Math.min(12, 3 + n);
        for (int i = 0; i < count; i++) {
            EntityType<? extends Mob> type = i % 2 == 0 ? CloudMobsSetup.FALLEN_PLUME.get() : CloudMobsSetup.BELL_MOURNER.get();
            Mob a = TowerWorld.foe(level, type, rel(-5 + rng.nextDouble() * 10, 1, -20 + rng.nextDouble() * 50), i % 2 == 0 ? 120 : 160, i % 2 == 0 ? 14 : 16, n, run.id);
            if (a != null) {
                adds.add(a);
                level.sendParticles(i % 2 == 0 ? GOLD : VIOLET, a.getX(), a.getY() + 1, a.getZ(), 20, 0.4, 0.8, 0.4, 0);
            }
        }
        raiseBinders(level);
        master.playAction("roar");
        sound(level, master.chest(), CloudMobsSetup.PILGRIM_ROAR.get(), 6f, 1.0f);
        for (ServerPlayer p : here) {
            Notice.narrate(p, "message.skyabyss.tower.swarm");
        }
    }

    void raiseBinders(ServerLevel level) {
        binders.removeIf(b -> !b.isAlive());
        if (!binders.isEmpty()) {
            return;
        }
        for (String mk : List.of("healer_l", "healer_r")) {
            Mob b = TowerWorld.foe(level, CloudMobsSetup.NET_CASTER.get(), run.markCenter(mk), 300, 10, n, run.id);
            if (b != null) {
                b.setNoAi(true);
                binders.add(b);
                level.sendParticles(BIND, b.getX(), b.getY() + 1, b.getZ(), 30, 0.5, 1.0, 0.5, 0);
            }
        }
    }

    // 繋ぎ手: 生きているあいだ、毎秒、月 (いれば) か主を癒す
    void tickBinders(ServerLevel level, long now) {
        binders.removeIf(b -> !b.isAlive());
        if (binders.isEmpty()) {
            return;
        }
        LivingEntity to = moon != null && moon.isAlive() ? moon : master;
        Vec3 dst = to == master ? master.chest() : to.position().add(0, to.getBbHeight() * 0.5, 0);
        for (Mob b : binders) {
            if (now % 20 == 0) {
                to.heal(to.getMaxHealth() * (to == master ? 0.004f : 0.003f));
            }
            if (now % 4 == 0) {
                Vec3 from = b.position().add(0, 1.4, 0);
                double d = from.distanceTo(dst);
                for (double s = 0; s < d; s += 1.5) {
                    Vec3 q = from.lerp(dst, s / d);
                    level.sendParticles(BIND, q.x, q.y, q.z, 1, 0, 0, 0, 0);
                }
            }
        }
    }

    // ---------------------------------------------------------------- 落ちる月
    void startMoon(ServerLevel level, long now, List<ServerPlayer> here) {
        master.setShielded(true);
        master.playAction("moon");
        move = "moon";
        moveEnd = now + 80;
        zones.clear();
        chasers.clear();
        sound(level, master.head(), CloudMobsSetup.MOON_FALL_TELL.get(), 6f, 0.6f);
        for (ServerPlayer p : here) {
            Notice.narrate(p, "message.skyabyss.tower.moon");
        }
        Mob m = TowerWorld.foe(level, CloudMobsSetup.TOWER_MOON.get(), rel(0, MOON_HOVER, MOON_START_Z), TowerMoon.HP * (1 + PARTY_STEP * (n - 1)) / TowerWorld.foeScale(n),
                TowerMoon.DMG, n, run.id);
        if (m instanceof TowerMoon tm) {
            moon = tm;
            moonBorn = now;
            tm.setSize(1.0);
            tm.playAction("call");
        }
        raiseBinders(level);
    }

    // 月の進み (0 = 広場、1 = 南の端)
    double moonProgress(long now) {
        return Math.min(1.0, (now - moonBorn) / (double) MOON_TICKS);
    }

    void tickMoon(ServerLevel level, long now, List<ServerPlayer> here) {
        if (moon == null) {
            return;
        }
        if (!moon.isAlive()) {
            moon = null;
            moonsDone++;
            master.setShielded(false);
            master.setFloor(master.getMaxHealth() * floorFrac(moonsDone));
            sound(level, master.chest(), CloudMobsSetup.MOON_FALL.get(), 6f, 1.2f);
            for (ServerPlayer p : here) {
                Notice.narrate(p, "message.skyabyss.tower.moon_gone");
            }
            restUntil = now + 60;
            return;
        }
        double prog = moonProgress(now);
        double south = run.markCenter("bridge_s").z;
        double z = rel(0, 0, MOON_START_Z).z + (south - rel(0, 0, MOON_START_Z).z) * prog;
        Vec3 at = rel(0, MOON_HOVER, 0);
        moon.setPos(at.x, at.y, z);
        moon.setSize(1.0 + prog * 1.2);
        if (now % 5 == 0) {
            level.sendParticles(GOLD, at.x, at.y + 3 * (1 + prog), z - 2, 6, 2, 2, 0.5, 0);
            level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.2, z - 3, 3, 3, 0, 0.5, 0.01);
        }
        if (now % 20 == 0) {
            double r = 3 + 2.5 * prog;
            for (ServerPlayer p : targets(here)) {
                if (p.position().subtract(moon.position()).horizontalDistance() < r) {
                    p.hurt(moon.attackSource(), moon.hit(0.3));
                }
            }
        }
        if (prog >= 1.0) {
            failed = "moon_lost";
        }
    }

    // ---------------------------------------------------------------- 地面の印 (予告 → 当たり)
    void tickZones(ServerLevel level, long now, List<ServerPlayer> here) {
        if (zones.isEmpty()) {
            return;
        }
        List<ServerPlayer> tg = targets(here);
        for (Zone z : List.copyOf(zones)) {
            if (z.follow != null && now < z.lockAt) {
                ServerPlayer p = level.getServer().getPlayerList().getPlayer(z.follow);
                if (p != null && p.level() == level && p.isAlive()) {
                    z.cx = p.getX();
                    z.cz = p.getZ();
                    z.y = p.getY();
                }
            }
            if (now < z.at) {
                if ((z.at - now) % (z.at - now <= 10 ? 2 : 4) == 0) {
                    z.draw(level, rng);
                }
                continue;
            }
            zones.remove(z);
            List<ServerPlayer> in = new ArrayList<>();
            for (ServerPlayer p : tg) {
                if (z.contains(p.position())) {
                    in.add(p);
                }
            }
            float amount = master.hit(z.factor);
            if (z.share && !in.isEmpty()) {
                amount /= in.size();
            }
            for (ServerPlayer p : in) {
                p.hurt(master.attackSource(), amount);
                if (z.push > 0) {
                    Vec3 away = new Vec3(p.getX() - z.cx, 0, p.getZ() - z.cz);
                    away = away.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : away.normalize();
                    if (z.kind == Zone.BOX) {
                        away = new Vec3(0, 0, 1);
                    }
                    p.push(away.x * z.push, 0.35, away.z * z.push);
                    p.hurtMarked = true;
                }
            }
            z.burst(level);
            sound(level, z.center(), z.pillar ? CloudMobsSetup.PILGRIM_BEAM.get() : z.color == VIOLET ? CloudMobsSetup.EYE_RAIN.get() : CloudMobsSetup.KEEPER_SLAM.get(),
                    z.r > 8 || z.kind == Zone.BOX ? 4f : 2f, z.color == VIOLET ? 0.7f : 1.0f);
        }
    }

    // ---------------------------------------------------------------- 追う印
    void tickChasers(ServerLevel level, long now) {
        for (Chaser c : List.copyOf(chasers)) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(c.target);
            if (p == null || p.level() != level || !p.isAlive() || now >= c.until) {
                chasers.remove(c);
                continue;
            }
            Vec3 d = new Vec3(p.getX() - c.x, 0, p.getZ() - c.z);
            double len = d.length();
            if (now >= c.from && len > 0.05) {
                double step = Math.min(len, 0.17);
                c.x += d.x / len * step;
                c.z += d.z / len * step;
            }
            c.y = p.getY();
            if (now % 3 == 0) {
                for (int i = 0; i < 14; i++) {
                    double a = i / 14.0 * Math.PI * 2 + now * 0.05;
                    level.sendParticles(RED, c.x + Math.cos(a) * 2.2, c.y + 0.15, c.z + Math.sin(a) * 2.2, 1, 0, 0, 0, 0);
                }
            }
            if (now >= c.from && now % 10 == 0 && len < 2.2 && !Downed.isDowned(p)) {
                p.hurt(master.attackSource(), master.hit(0.3 * stokeMult()));
                Vec3 h = master.head();
                Vec3 to = p.position().add(0, 1, 0);
                double dist = h.distanceTo(to);
                for (double s = 0; s < dist; s += 1.0) {
                    Vec3 q = h.lerp(to, s / dist);
                    level.sendParticles(RED, q.x, q.y, q.z, 1, 0, 0, 0, 0);
                }
            }
        }
    }

    // ---------------------------------------------------------------- 側の目
    void tickEyes(ServerLevel level, long now, List<ServerPlayer> here) {
        List<ServerPlayer> tg = targets(here);
        for (Eye e : eyes) {
            if (!e.mob.isAlive()) {
                continue;
            }
            Vec3 from = e.mob.eye();
            if (e.target == null) {
                if (now < e.next) {
                    continue;
                }
                List<ServerPlayer> seen = new ArrayList<>();
                for (ServerPlayer p : tg) {
                    if (p.distanceToSqr(from) < 60 * 60 && LampIsle.visible(level, from, p.getEyePosition())) {
                        seen.add(p);
                    }
                }
                if (seen.isEmpty()) {
                    e.next = now + 40;
                    continue;
                }
                e.target = seen.get(rng.nextInt(seen.size())).getUUID();
                e.strikeAt = now + 30;
                e.mob.playAction("stare");
                sound(level, from, CloudMobsSetup.EYE_STARE.get(), 2.5f, e.big ? 0.7f : 1.0f);
                continue;
            }
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(e.target);
            if (p == null || p.level() != level || !p.isAlive()) {
                e.target = null;
                e.next = now + 40;
                continue;
            }
            Vec3 to = p.getEyePosition();
            e.mob.face(to);
            if (now < e.strikeAt) {
                if (now % 2 == 0) {
                    int k = (int) Math.max(4, from.distanceTo(to) * (1.0 - (e.strikeAt - now) / 30.0) * 2 + 4);
                    for (int i = 0; i <= k; i++) {
                        Vec3 q = from.lerp(to, i / (double) k);
                        level.sendParticles(ParticleTypes.WAX_ON, q.x, q.y, q.z, 1, 0, 0, 0, 0);
                    }
                }
                continue;
            }
            boolean seen = LampIsle.visible(level, from, to);
            Vec3 end = seen ? to : from.add(to.subtract(from).normalize().scale(3));
            double dist = from.distanceTo(end);
            for (double s = 0; s <= dist; s += 0.6) {
                Vec3 q = from.lerp(end, s / Math.max(0.01, dist));
                level.sendParticles(ParticleTypes.END_ROD, q.x, q.y, q.z, 1, 0.03, 0.03, 0.03, 0.005);
            }
            sound(level, from, CloudMobsSetup.EYE_BEAM.get(), 2.5f, e.big ? 0.7f : 1.1f);
            if (seen && !Downed.isDowned(p)) {
                p.hurt(e.mob.attackSource(), e.mob.hit((e.big ? 1.2 : 0.7) * stokeMult()));
            }
            e.target = null;
            e.next = now + 140 + rng.nextInt(100) + (e.big ? 40 : 0);
        }
    }

    // ---------------------------------------------------------------- 勝ち
    void win(ServerLevel level, long now) {
        won = true;
        wonAt = now;
        bar.removeAllPlayers();
        zones.clear();
        chasers.clear();
        if (moon != null) {
            moon.discard();
            moon = null;
        }
        laters.add(new Later(now + 40, () -> {
            for (Eye e : eyes) {
                e.mob.discard();
            }
            for (Mob m : adds) {
                m.discard();
            }
            for (Mob m : binders) {
                m.discard();
            }
        }));
    }

    void close() {
        bar.removeAllPlayers();
    }

    void tell(ServerPlayer p, String what) {
        if (told.add(p.getUUID() + what)) {
            Notice.hint(p, "message.skyabyss.tower." + what);
        }
    }

    static void sound(ServerLevel level, Vec3 at, SoundEvent s, float vol, float pitch) {
        level.playSound(null, at.x, at.y, at.z, s, SoundSource.HOSTILE, vol, pitch);
    }

    // ---------------------------------------------------------------- 鎖の子: 打たれた人は回復が 25% 落ちる (8 秒)
    @SubscribeEvent
    static void onDamaged(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer p && event.getSource().getEntity() instanceof Mob m && m.getTags().contains(PHLEGM_TAG)) {
            boolean fresh = PHLEGM.getOrDefault(p.getUUID(), 0L) < p.level().getGameTime();
            PHLEGM.put(p.getUUID(), p.level().getGameTime() + PHLEGM_TICKS);
            if (fresh) {
                Notice.hint(p, "message.skyabyss.tower.phlegm");
            }
        }
    }

    @SubscribeEvent
    static void onHeal(LivingHealEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && PHLEGM.getOrDefault(p.getUUID(), 0L) >= p.level().getGameTime()) {
            event.setAmount(event.getAmount() * 0.75f);
        }
    }

    // ---------------------------------------------------------------- 部品
    static final class Eye {
        final TowerEye mob;
        final boolean big;
        long next;
        UUID target;
        long strikeAt;

        Eye(TowerEye mob, boolean big, long next) {
            this.mob = mob;
            this.big = big;
            this.next = next;
        }
    }

    static final class Chaser {
        final UUID target;
        double x;
        double y;
        double z;
        final long from;
        final long until;

        Chaser(UUID target, double x, double y, double z, long from, long until) {
            this.target = target;
            this.x = x;
            this.y = y;
            this.z = z;
            this.from = from;
            this.until = until;
        }
    }

    record Later(long at, Runnable run) {
    }

    // 予告の印。円 (cx, cz, r) か箱 (x0..x1, z0..z1)。at の tick に当たる。follow の人を lockAt まで追う。share は当たった人で傷を分ける
    static final class Zone {
        static final int CIRCLE = 0;
        static final int BOX = 1;
        final int kind;
        double cx;
        double cz;
        double y;
        final double r;
        final double x0;
        final double x1;
        final double z0;
        final double z1;
        final long at;
        final double factor;
        final DustParticleOptions color;
        double push;
        boolean share;
        boolean pillar;
        UUID follow;
        long lockAt;

        private Zone(int kind, double cx, double y, double cz, double r, double x0, double x1, double z0, double z1, long at, double factor, DustParticleOptions color) {
            this.kind = kind;
            this.cx = cx;
            this.y = y;
            this.cz = cz;
            this.r = r;
            this.x0 = x0;
            this.x1 = x1;
            this.z0 = z0;
            this.z1 = z1;
            this.at = at;
            this.factor = factor;
            this.color = color;
        }

        static Zone circle(Vec3 c, double r, long at, double factor, DustParticleOptions color) {
            return new Zone(CIRCLE, c.x, c.y, c.z, r, 0, 0, 0, 0, at, factor, color);
        }

        static Zone box(Vec3 a, Vec3 b, long at, double factor, DustParticleOptions color) {
            return new Zone(BOX, (a.x + b.x) / 2, a.y, (a.z + b.z) / 2, 0, Math.min(a.x, b.x), Math.max(a.x, b.x), Math.min(a.z, b.z), Math.max(a.z, b.z),
                    at, factor, color);
        }

        Zone push(double p) {
            push = p;
            return this;
        }

        Zone share() {
            share = true;
            return this;
        }

        Zone pillar() {
            pillar = true;
            return this;
        }

        Zone follow(ServerPlayer p, long until) {
            follow = p.getUUID();
            lockAt = until;
            return this;
        }

        Vec3 center() {
            return new Vec3(cx, y, cz);
        }

        boolean contains(Vec3 p) {
            if (Math.abs(p.y - y) > 3.5) {
                return false;
            }
            if (kind == BOX) {
                return p.x >= x0 && p.x <= x1 && p.z >= z0 && p.z <= z1;
            }
            double dx = p.x - cx;
            double dz = p.z - cz;
            return dx * dx + dz * dz <= r * r;
        }

        void draw(ServerLevel level, RandomSource rng) {
            double yy = y + 0.15;
            if (kind == CIRCLE) {
                int k = (int) Math.max(14, r * 6);
                for (int i = 0; i < k; i++) {
                    double a = i / (double) k * Math.PI * 2;
                    level.sendParticles(color, cx + Math.cos(a) * r, yy, cz + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
                for (int i = 0; i < (int) (r * 1.5); i++) {
                    double a = rng.nextDouble() * Math.PI * 2;
                    double d = Math.sqrt(rng.nextDouble()) * r;
                    level.sendParticles(color, cx + Math.cos(a) * d, yy, cz + Math.sin(a) * d, 1, 0, 0, 0, 0);
                }
                if (pillar) {
                    level.sendParticles(ParticleTypes.END_ROD, cx, yy + 0.5, cz, 2, 0.3, 0.3, 0.3, 0.01);
                }
                return;
            }
            for (double x = x0; x <= x1; x += 1.2) {
                level.sendParticles(color, x, yy, z0, 1, 0, 0, 0, 0);
                level.sendParticles(color, x, yy, z1, 1, 0, 0, 0, 0);
            }
            for (double z = z0; z <= z1; z += 1.2) {
                level.sendParticles(color, x0, yy, z, 1, 0, 0, 0, 0);
                level.sendParticles(color, x1, yy, z, 1, 0, 0, 0, 0);
            }
            int fill = (int) Math.min(40, (x1 - x0) * (z1 - z0) / 12);
            for (int i = 0; i < fill; i++) {
                level.sendParticles(color, x0 + rng.nextDouble() * (x1 - x0), yy, z0 + rng.nextDouble() * (z1 - z0), 1, 0, 0, 0, 0);
            }
        }

        void burst(ServerLevel level) {
            ParticleOptions big = color == VIOLET ? ParticleTypes.SQUID_INK : ParticleTypes.END_ROD;
            if (kind == CIRCLE) {
                if (pillar) {
                    for (double h = 0; h < 14; h += 0.5) {
                        level.sendParticles(ParticleTypes.END_ROD, cx, y + h, cz, 2, 0.4, 0.1, 0.4, 0.01);
                    }
                }
                level.sendParticles(big, cx, y + 0.5, cz, (int) (r * 10), r * 0.5, 0.4, r * 0.5, 0.05);
                level.sendParticles(color, cx, y + 0.8, cz, (int) (r * 12), r * 0.5, 0.6, r * 0.5, 0);
                return;
            }
            level.sendParticles(big, cx, y + 0.5, cz, 80, (x1 - x0) * 0.35, 0.4, (z1 - z0) * 0.35, 0.05);
            level.sendParticles(color, cx, y + 0.8, cz, 120, (x1 - x0) * 0.35, 0.6, (z1 - z0) * 0.35, 0);
        }
    }

    // 開発用・自己テスト: 主
    public TowerMaster master() {
        return master;
    }

    // 開発用: 技をすぐ出す (撮影・確かめ用。狙いは塔の中の隊の人みな。観戦の人も狙うが傷は入らない)
    public void devMove(ServerLevel level, String m) {
        long now = level.getGameTime();
        List<ServerPlayer> all = run.online().stream().filter(p -> p.level() == level).toList();
        if (master == null || all.isEmpty()) {
            return;
        }
        switch (m) {
            case "moon" -> {
                if (moon == null) {
                    startMoon(level, now, all);
                }
            }
            case "swarm" -> swarm(level, all);
            default -> begin(level, now, m, all);
        }
    }
}
