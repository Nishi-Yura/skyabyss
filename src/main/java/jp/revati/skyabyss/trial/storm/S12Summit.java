package jp.revati.skyabyss.trial.storm;

import static jp.revati.skyabyss.titan.storm.back.StormDeck2.Y;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.abyss.Abyss;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.storm.StormSetup;
import jp.revati.skyabyss.titan.storm.back.StormBackSetup;
import jp.revati.skyabyss.titan.storm.back.StormDeck2;
import jp.revati.skyabyss.titan.storm.back.StormFx;
import jp.revati.skyabyss.titan.storm.back.StormStrike;
import jp.revati.skyabyss.titan.storm.mob.FeatherWarden;
import jp.revati.skyabyss.titan.storm.mob.StormMobSetup;
import jp.revati.skyabyss.trial.Trial.Mods;
import jp.revati.skyabyss.trial.TrialFx;
import jp.revati.skyabyss.trial.TrialRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// S12 羽守長の頂 (戦う・★★★。docs/design/29 の 3.3)。丸い頂の上で、羽守長 (大きな羽守) と戦う。頂は、真ん中の芯 (いつもある。羽の嵐が来ない) と、
// 入口・出口への筋、縁の輪と、六つの扇の羽の足場でできている。足場は羽に運ばれて動く。
//   決まり (教える): 足場は、半分 (一つおきの三つの扇) ずつ運び去られ、また戻ってくる。運び去られる扇は、黄色く瞬いて 2.5 秒の予告のあとに消える
//     (その間は、もう半分の扇が戻っている)。運び去られる扇に立っていたら、残っている扇か芯へ移る。落ちたら控えへ戻る (傷は軽い)。
//   ひねり: 羽守長は「羽の嵐」を起こす。橙に光った扇に、2 秒の予告のあと羽の嵐が降る (人に傷)。芯は羽の嵐が来ない。扇から扇へ、動く足場を渡って避ける。
//     半分まで削ると、取り巻き (雷蛾か羽守) が加わる。
//   羽守長を倒すと、足場がすべて戻り、出口の橋の戸が開く。
// 人数: 羽守長の体力が人数で増える (一人あたり +25%)・羽の嵐の間隔が短くなる・取り巻きが増える。一人でも、芯と扇を使って戦える
// 修飾: 配置 3 (扇の動きの順)・数字 2 (足場の入れ替えの間 5.5/4/2.8 秒・羽の嵐の間隔 14/11/8 秒)・敵の組 2 (羽守長と雷蛾 / 羽守長と羽守)・障害 2 (横風・空の雷)・任意の目標 (落ちずに)
public final class S12Summit extends StormCell {
    static final int[] HOLD = {110, 80, 55};
    static final int[] STORM = {280, 220, 160};
    static final int FLICKER = 50;
    static final int STORM_WARN = 40;
    static final float STORM_DAMAGE = 7f;
    static final double START_R = 12;
    static final double HP = 3.2;
    static final int SKY_EVERY = 150;
    static final int GUST_EVERY = 240;
    static final int GUST_WARN = 40;
    static final int GUST_BLOW = 30;
    static final double GUST_PUSH = 0.07;

    private static final BlockState YELLOW = Blocks.YELLOW_CONCRETE.defaultBlockState();
    private static final BlockState ORANGE = Blocks.ORANGE_CONCRETE.defaultBlockState();
    private static final List<List<BlockPos>> WEDGES = new ArrayList<>();

    static {
        for (int w = 0; w < 6; w++) {
            WEDGES.add(StormDeck2.s12Pads(w));
        }
    }

    public S12Summit() {
        super("S12", new Ports(StormDeck2.S12_GATE, false, StormDeck2.S12_EXIT_GATE, StormDeck2.S12_STONE, null, "", StormDeck2.S12_AREA, List.of()));
    }

    // ---------------------------------------------------------------- 数字
    // 足場の入れ替えの間 (tick。一方だけが残っている時間)
    public static int hold(Mods m) {
        return Abyss.shorter(m, HOLD[m.number(0)]);
    }

    // 羽の嵐の間隔 (tick)。人数で短い (最大 4 人分で 20%)
    public static int stormEvery(Mods m, int players) {
        double scale = 1.0 - 0.05 * Math.min(4, Math.max(0, players - 1));
        return Math.max(100, (int) Math.round(Abyss.shorter(m, STORM[m.number(1)]) * scale / 5.0) * 5);
    }

    // 羽守長の体力 (羽守の何倍か)
    public static double hpMult(Run run, int players) {
        double mult = HP * (1 + 0.25 * (Math.max(1, players) - 1));
        mult = run.mods.abyss() ? mult * 1.2 : mult;
        return run.mods.quiet() ? mult * 0.7 : mult;
    }

    public static int support(Run run, int players) {
        if (run.mods.quiet()) {
            return 0;
        }
        return 1 + (players >= 3 ? 1 : 0) + (players >= 5 ? 1 : 0);
    }

    // 扇 w が今あるか (状態から)。状態: 0 すべてある / 1 A (偶数の扇) だけ / 2 A と B が両方 (A が瞬く) / 3 B (奇数の扇) だけ / 4 両方 (B が瞬く)
    public static boolean present(int pstate, int w) {
        return switch (pstate) {
            case 1 -> w % 2 == 0;
            case 3 -> w % 2 == 1;
            default -> true;
        };
    }

    // 瞬いている扇か
    public static boolean flickering(int pstate, int w) {
        return pstate == 2 && w % 2 == 0 || pstate == 4 && w % 2 == 1;
    }

    private static List<UUID> mobs(Run run) {
        return run.get("mobs", ArrayList::new);
    }

    private static int pstate(Run run) {
        return run.getInt("pstate");
    }

    // ---------------------------------------------------------------- 動き
    @Override
    public void arm(ServerLevel back, Run run) {
        resetArena(back, run);
    }

    private void resetArena(ServerLevel back, Run run) {
        discardAll(back, mobs(run));
        run.put("phase", 0);
        run.put("pstate", 0);
        run.put("pNext", 0L);
        run.put("stormAt", 0L);
        run.put("stormW", -1);
        run.put("nextStorm", 0L);
        run.put("nextSky", 0L);
        run.put("gustAt", 0L);
        run.put("emptySince", 0L);
        run.put("supported", false);
        run.put("chief", null);
        GroupGates.seal(back, run.group, StormDeck2.s12AllPads());
        if (run.cleared) {
            GroupGates.open(back, run.group, StormDeck2.S12_EXIT_GATE);
        } else {
            GroupGates.release(back, run.group, StormDeck2.S12_EXIT_GATE);
        }
    }

    @Override
    public void disarm(ServerLevel back, Run run) {
        discardAll(back, mobs(run));
        GroupGates.release(back, run.group, StormDeck2.s12AllPads());
        if (!run.cleared) {
            GroupGates.release(back, run.group, StormDeck2.S12_EXIT_GATE);
        }
    }

    private static boolean onArena(ServerPlayer p) {
        return Math.hypot(p.getX() - (StormDeck2.S12_ARENA.cx() + 0.5), p.getZ() - (StormDeck2.S12_ARENA.cz() + 0.5)) <= StormDeck2.S12_ARENA_R + 1 && p.getY() >= Y + 0.5;
    }

    // 位置がいる扇の番号 (芯・筋・縁の輪・扇の外は -1)
    static int wedgeAt(double x, double z) {
        return StormDeck2.s12Wedge((int) Math.floor(x) - StormDeck2.S12_ARENA.cx(), (int) Math.floor(z) - StormDeck2.S12_ARENA.cz());
    }

    private void setState(ServerLevel back, Run run, int state) {
        int old = pstate(run);
        run.put("pstate", state);
        for (int w = 0; w < 6; w++) {
            boolean was = present(old, w);
            boolean now = present(state, w);
            if (was != now) {
                if (now) {
                    GroupGates.seal(back, run.group, WEDGES.get(w));
                } else {
                    GroupGates.open(back, run.group, WEDGES.get(w));
                    BlockPos c = WEDGES.get(w).get(WEDGES.get(w).size() / 2);
                    TrialFx.particles(back, run.group, ParticleTypes.CLOUD, c.getX() + 0.5, Y + 0.5, c.getZ() + 0.5, 30, 2.5, 0.3, 2.5, 0.05);
                    TrialFx.sound(back, run.group, c, StormSetup.GALE.get(), 1.2f, 1.1f);
                }
            }
        }
        TrialRuntime.refresh(back, run);
    }

    @Override
    public void tick(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        int n = count(inside);
        if (!run.data.containsKey("welcomed")) {
            run.put("welcomed", true);
            bar(inside, ModText.objective("message.skyabyss.trial.s12.start"));
        }
        // 落ちた: 控えへ
        for (ServerPlayer p : inside) {
            if (fell(p, Y)) {
                toRest(back, run, p, "s12a", "message.skyabyss.trial.s12.fell");
                run.put("falls", run.getInt("falls") + 1);
                if (run.mods.goal() == 0) {
                    run.goalOk = false;
                }
            }
        }
        if (run.cleared) {
            return;
        }
        if (back.getDifficulty() == Difficulty.PEACEFUL) {
            if (now % 100 == 0) {
                bar(inside, ModText.hint("message.skyabyss.trial.s12.peaceful"));
            }
            return;
        }
        int phase = run.getInt("phase");
        int onArena = 0;
        for (ServerPlayer p : inside) {
            if (onArena(p)) {
                onArena++;
            }
        }
        if (phase == 0) {
            // 頂の真ん中のそばに人が来たら、羽守長が現れる
            for (ServerPlayer p : inside) {
                if (Math.hypot(p.getX() - (StormDeck2.S12_ARENA.cx() + 0.5), p.getZ() - (StormDeck2.S12_ARENA.cz() + 0.5)) < START_R && p.getY() >= Y + 0.5) {
                    startFight(back, run, inside, n, now);
                    return;
                }
            }
            return;
        }
        // 頂に誰もいないまま 8 秒: 最初に戻る (羽守長は消える)
        if (onArena == 0) {
            long since = run.getLong("emptySince");
            if (since == 0) {
                run.put("emptySince", now);
            } else if (now - since >= 160) {
                bar(inside, ModText.hint("message.skyabyss.trial.s12.reset"));
                resetArena(back, run);
                TrialRuntime.refresh(back, run);
                return;
            }
        } else {
            run.put("emptySince", 0L);
        }
        Mob chief = chief(back, run);
        leash(back, run);
        // 羽守長を倒した
        if (chief == null || !chief.isAlive()) {
            win(back, run, inside, now);
            return;
        }
        pads(back, run, now);
        stormStep(back, run, inside, now, n);
        // 取り巻き (体力が半分を切ったら一度だけ)
        if (!(Boolean) run.data.getOrDefault("supported", false) && chief.getHealth() < chief.getMaxHealth() * 0.5) {
            run.put("supported", true);
            support(back, run, n);
            bar(inside, ModText.battle("message.skyabyss.trial.s12.support"));
        }
        // 障害
        if (run.mods.hasHazard(1) && now >= run.getLong("nextSky") && !inside.isEmpty()) {
            run.put("nextSky", now + SKY_EVERY);
            ServerPlayer target = inside.get(back.random.nextInt(inside.size()));
            StormStrike.call(back, target.position(), 24, 8f, null, run.group);
        }
        if (run.mods.hasHazard(0)) {
            gust(back, run, inside, now);
        }
    }

    private Mob chief(ServerLevel back, Run run) {
        UUID id = (UUID) run.data.get("chief");
        if (id == null) {
            return null;
        }
        Entity e = back.getEntity(id);
        return e instanceof Mob m ? m : null;
    }

    private void startFight(ServerLevel back, Run run, List<ServerPlayer> inside, int n, long now) {
        run.put("phase", 1);
        run.put("players", n);
        double cx = StormDeck2.S12_ARENA.cx() + 0.5;
        double cz = StormDeck2.S12_ARENA.cz() + 0.5;
        FeatherWarden boss = spawn(back, StormMobSetup.FEATHER_WARDEN.get(), run.group, cx, Y + 1, cz);
        if (boss != null) {
            boss.getAttribute(Attributes.MAX_HEALTH).setBaseValue(FeatherWarden.HEALTH * hpMult(run, n));
            boss.setHealth(boss.getMaxHealth());
            boss.setCustomName(Component.translatable("trial.skyabyss.s12.chief"));
            boss.setCustomNameVisible(true);
            mobs(run).add(boss.getUUID());
            run.put("chief", boss.getUUID());
        } else {
            // 湧かせられなかった: 始めない (次の tick でもう一度)
            run.put("phase", 0);
            return;
        }
        TrialFx.sound(back, run.group, StormDeck2.S12_ARENA.center(), SoundEvents.RAVAGER_ROAR, 1.5f, 0.7f);
        bar(inside, ModText.battle("message.skyabyss.trial.s12.chief"));
        // 足場の入れ替えを始める: まず B (奇数の扇) が瞬いて、運び去られる
        run.put("pstate", 0);
        setState(back, run, 4);
        run.put("pNext", now + FLICKER);
        run.put("nextStorm", now + 160L);
        run.put("nextSky", now + 240L);
        run.put("gustAt", now + 300L);
    }

    // 足場の入れ替え (状態 4 → 1 → 2 → 3 → 4 …。瞬く間は両方ある)
    private void pads(ServerLevel back, Run run, long now) {
        if (now < run.getLong("pNext")) {
            // 瞬く扇の音
            if ((pstate(run) == 2 || pstate(run) == 4) && now % 10 == 0) {
                TrialFx.sound(back, run.group, StormDeck2.S12_ARENA.center(), SoundEvents.NOTE_BLOCK_CHIME.value(), 0.8f, 1.9f);
            }
            return;
        }
        int s = pstate(run);
        int next = switch (s) {
            case 4 -> 1;
            case 1 -> 2;
            case 2 -> 3;
            case 3 -> 4;
            default -> 4;
        };
        long wait = next == 2 || next == 4 ? FLICKER : hold(run.mods);
        if (next == 1 || next == 3) {
            wait = hold(run.mods);
        }
        setState(back, run, next);
        run.put("pNext", now + wait);
    }

    // 羽の嵐: 人のいる扇 (あるなら) が橙に瞬いて、2 秒後に降る。芯・筋・輪は来ない
    private void stormStep(ServerLevel back, Run run, List<ServerPlayer> inside, long now, int n) {
        long at = run.getLong("stormAt");
        int w = run.getInt("stormW");
        if (at != 0) {
            if (now < at) {
                if (now % 4 == 0) {
                    for (BlockPos c : WEDGES.get(w)) {
                        if ((c.getX() + c.getZ()) % 3 == 0) {
                            StormFx.to(run.group, () -> StormFx.forced(back, ParticleTypes.WHITE_ASH, c.getX() + 0.5, Y + 4 + back.random.nextDouble() * 2, c.getZ() + 0.5, 1, 0.3, 0.6, 0.3, 0.02));
                        }
                    }
                }
                return;
            }
            run.put("stormAt", 0L);
            run.put("stormW", -1);
            run.put("nextStorm", now + stormEvery(run.mods, n));
            BlockPos mid = WEDGES.get(w).get(WEDGES.get(w).size() / 2);
            TrialFx.sound(back, run.group, mid, StormSetup.CROSSWIND.get(), 1.6f, 0.9f);
            for (BlockPos c : WEDGES.get(w)) {
                if ((c.getX() + c.getZ()) % 2 == 0) {
                    StormFx.to(run.group, () -> StormFx.forced(back, ParticleTypes.CLOUD, c.getX() + 0.5, Y + 1.2, c.getZ() + 0.5, 2, 0.3, 0.5, 0.3, 0.12));
                }
            }
            for (ServerPlayer p : inside) {
                if (!p.isCreative() && wedgeAt(p.getX(), p.getZ()) == w && p.getY() >= Y && p.getY() < Y + 6 && present(pstate(run), w)) {
                    p.hurt(back.damageSources().lightningBolt(), STORM_DAMAGE);
                    p.setDeltaMovement(p.getDeltaMovement().add(0, 0.45, 0));
                    p.hurtMarked = true;
                }
            }
            TrialRuntime.refresh(back, run);
            return;
        }
        if (now >= run.getLong("nextStorm")) {
            // 人のいる扇のうち一つ (いなければ今ある扇から)
            List<Integer> cand = new ArrayList<>();
            for (ServerPlayer p : inside) {
                int pw = wedgeAt(p.getX(), p.getZ());
                if (pw >= 0 && p.getY() >= Y && present(pstate(run), pw) && !cand.contains(pw)) {
                    cand.add(pw);
                }
            }
            if (cand.isEmpty()) {
                run.put("nextStorm", now + 40);
                return;
            }
            run.put("stormW", cand.get(back.random.nextInt(cand.size())));
            run.put("stormAt", now + STORM_WARN);
            back.playSound(null, StormDeck2.S12_ARENA.center(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 1.0f, 1.3f);
            if (!run.data.containsKey("stormTold")) {
                run.put("stormTold", true);
                bar(inside, ModText.hint("message.skyabyss.trial.s12.storm"));
            }
            TrialRuntime.refresh(back, run);
        }
    }

    // 横風 (障害): 予告の矢 → 吹く (頂の上の人を一方向へ押す)
    private void gust(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        long at = run.getLong("gustAt");
        if (at == 0) {
            return;
        }
        int dir = run.getInt("gustDir");
        double dx = Math.cos(Math.PI / 2 * dir);
        double dz = Math.sin(Math.PI / 2 * dir);
        if (now < at - GUST_WARN) {
            return;
        }
        if (now < at) {
            if (now % 6 == 0) {
                for (ServerPlayer p : inside) {
                    if (onArena(p)) {
                        StormFx.to(run.group, () -> StormFx.forced(back, ParticleTypes.CLOUD, p.getX() - dx * 5, p.getY() + 1, p.getZ() - dz * 5, 0, dx, 0, dz, 0.8));
                    }
                }
            }
            return;
        }
        if (now < at + GUST_BLOW) {
            for (ServerPlayer p : inside) {
                if (onArena(p) && !p.getAbilities().flying) {
                    Vec3 v = p.getDeltaMovement();
                    p.setDeltaMovement(v.x + dx * GUST_PUSH, v.y, v.z + dz * GUST_PUSH);
                    p.hurtMarked = true;
                }
            }
            return;
        }
        run.put("gustAt", now + GUST_EVERY);
        run.put("gustDir", back.random.nextInt(4));
    }

    private void support(ServerLevel back, Run run, int players) {
        int c = support(run, players);
        double cx = StormDeck2.S12_ARENA.cx() + 0.5;
        double cz = StormDeck2.S12_ARENA.cz() + 0.5;
        for (int i = 0; i < c; i++) {
            double a = Math.PI * 2 * i / Math.max(1, c) + 0.6;
            Mob m = run.mods.enemy() == 1 ? spawn(back, StormMobSetup.FEATHER_WARDEN.get(), run.group, cx + Math.cos(a) * 3, Y + 1, cz + Math.sin(a) * 3)
                    : spawn(back, StormMobSetup.STATIC_MOTH.get(), run.group, cx + Math.cos(a) * 3, Y + 5, cz + Math.sin(a) * 3);
            if (m != null) {
                mobs(run).add(m.getUUID());
            }
        }
    }

    // 頂から出た敵を戻す
    private void leash(ServerLevel back, Run run) {
        double cx = StormDeck2.S12_ARENA.cx() + 0.5;
        double cz = StormDeck2.S12_ARENA.cz() + 0.5;
        for (UUID id : new ArrayList<>(mobs(run))) {
            Entity e = back.getEntity(id);
            if (!(e instanceof Mob m) || !m.isAlive()) {
                continue;
            }
            if (m.getY() < Y - 4 || Math.hypot(m.getX() - cx, m.getZ() - cz) > StormDeck2.S12_ARENA_R + 3) {
                m.teleportTo(cx, m instanceof jp.revati.skyabyss.titan.storm.mob.StaticMoth ? Y + 5 : Y + 1, cz);
                m.setDeltaMovement(Vec3.ZERO);
            }
        }
    }

    private void win(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        discardAll(back, mobs(run));
        run.cleared = true;
        run.clearedAt = now;
        run.put("pstate", 0);
        run.put("stormAt", 0L);
        GroupGates.seal(back, run.group, StormDeck2.s12AllPads());
        GroupGates.open(back, run.group, StormDeck2.S12_EXIT_GATE);
        TrialFx.sound(back, run.group, StormDeck2.S12_STONE, SoundEvents.BELL_RESONATE, 1.5f, 0.7f);
        bar(inside, ModText.progress("message.skyabyss.trial.s12.cleared"));
        TrialRuntime.refresh(back, run);
    }

    // ---------------------------------------------------------------- 見かけ
    @Override
    public void overlay(ServerLevel back, Run run, BiConsumer<BlockPos, BlockState> out) {
        int s = pstate(run);
        int stormW = run.getInt("stormW");
        boolean stormOn = run.getLong("stormAt") != 0;
        for (int w = 0; w < 6; w++) {
            if (!present(s, w)) {
                continue;
            }
            boolean fl = flickering(s, w);
            boolean storm = stormOn && stormW == w;
            for (BlockPos p : WEDGES.get(w)) {
                BlockState look = storm ? ORANGE : fl ? YELLOW : (p.getX() + p.getZ() & 1) == 0 ? Blocks.WHITE_CONCRETE.defaultBlockState() : Blocks.SMOOTH_QUARTZ.defaultBlockState();
                out.accept(p, look);
            }
        }
    }

    // ---------------------------------------------------------------- 検査
    @Override
    public List<String> audit(ServerLevel back) {
        List<String> bad = new ArrayList<>();
        for (BlockPos p : StormDeck2.s12AllPads()) {
            if (!GroupGates.isGate(back, p)) {
                bad.add("S12 pad block missing at " + p);
                break;
            }
        }
        // 芯の足もとは固い・筋は固い
        for (int dx = -3; dx <= 3; dx++) {
            BlockPos c = new BlockPos(StormDeck2.S12_ARENA.cx() + dx, Y, StormDeck2.S12_ARENA.cz());
            if (back.getBlockState(c).isAir() || GroupGates.isGate(back, c)) {
                bad.add("S12 core is not solid at " + c);
                break;
            }
        }
        for (int dz = 5; dz <= 12; dz++) {
            for (int sign : new int[] {-1, 1}) {
                BlockPos c = new BlockPos(StormDeck2.S12_ARENA.cx(), Y, StormDeck2.S12_ARENA.cz() + sign * dz);
                if (back.getBlockState(c).isAir() || GroupGates.isGate(back, c)) {
                    bad.add("S12 spoke is not solid at " + c);
                    dz = 99;
                    break;
                }
            }
        }
        for (int w = 0; w < 6; w++) {
            if (WEDGES.get(w).size() < 30) {
                bad.add("S12 wedge " + w + " is too small (" + WEDGES.get(w).size() + ")");
            }
        }
        if (!back.getBlockState(StormDeck2.S12_STONE).is(StormBackSetup.CHIME_STONE.get())) {
            bad.add("S12 stone missing");
        }
        return bad;
    }
}
