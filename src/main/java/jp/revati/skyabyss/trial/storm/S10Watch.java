package jp.revati.skyabyss.trial.storm;

import static jp.revati.skyabyss.titan.storm.back.StormDeck.Y;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.abyss.Abyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.titan.storm.StormSetup;
import jp.revati.skyabyss.titan.storm.back.GaleFeather;
import jp.revati.skyabyss.titan.storm.back.StormBackClock;
import jp.revati.skyabyss.titan.storm.back.StormBackSetup;
import jp.revati.skyabyss.titan.storm.back.StormDeck;
import jp.revati.skyabyss.titan.storm.back.StormDeck.Island;
import jp.revati.skyabyss.titan.storm.back.StormStrike;
import jp.revati.skyabyss.titan.storm.back.Updraft;
import jp.revati.skyabyss.titan.storm.mob.StormMobSetup;
import jp.revati.skyabyss.trial.Trial.Mods;
import jp.revati.skyabyss.trial.TrialFx;
import jp.revati.skyabyss.trial.TrialRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// S10 羽守の見張り台 (戦う・★★。docs/design/29 の 3.3)。尾の上の空に浮かぶ五つの台 (始めの台 + 四つの見張り台)。台と台のあいだは空気 9 マスで、
// 台の西の縁の「風の穴」の昇り風と、風切りの羽根の滑空でしか越えられない。
//   決まり (教える): 見張り台に人が着くと、羽守 (と雷蛾) が湧く。守りを全部倒すと、その台の風の穴が目を覚まして (光って)、次の台へ運ぶ。
//     穴は守りが残っているあいだは眠ったまま (灰色)。始めの台の穴は最初から目覚めている。
//   ひねり: 後ろの台ほど守りが厚く、雷蛾 (宙を舞う) や雷乞い (遠くから雷の輪) が加わる。凪の羽守は持ち場を歩き、嵐では突っ込む。
//   足を踏み外したら休み場へ戻される。最後の台の守りを倒せば済み (石は最後の台)。
// 人数: 守りのいる台が増える (一人は 2 台 = 台 2・4、二〜三人は 3 台、四人以上は 4 台)。羽守が二人ごとに一体多い。台ごとに手分けできる
// 修飾: 配置 3 (守りの湧く向き)・数字 2 (羽守の数 −1/0/+1・守りのいる台の数 −1/0/+1)・敵の組 2 (羽守と雷蛾 / 羽守と雷乞い)・障害 2 (横風・空の雷)・任意の目標 (無傷で)
public final class S10Watch extends StormCell implements StormCell.PerTick {
    static final int PLATFORMS = StormDeck.S10_PLATFORMS.length;
    // 空の雷 (障害): 間隔・予告
    static final int SKY_EVERY = 120;
    static final int SKY_WARN = 24;
    // 横風 (障害): 周期・予告・吹く長さ
    static final int GUST_CYCLE = 150;
    static final int GUST_WARN = 40;
    static final int GUST_BLOW = 45;
    static final double GUST_PUSH = 0.05;

    private static final BlockState ON = Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState();

    public S10Watch() {
        super("S10", new Ports(StormDeck.S10_GATE, false, List.of(), StormDeck.S10_STONE, null, "", StormDeck.S10_AREA, List.of()));
    }

    // ---------------------------------------------------------------- 数字
    // 守りのいる台の数 (1〜4)。人数で増える
    public static int fights(Run run, int players) {
        int base = players <= 1 ? 2 : players <= 3 ? 3 : 4;
        int n = base + run.mods.number(1) - 1;
        if (run.mods.abyss()) {
            n++;
        }
        return Math.max(1, Math.min(PLATFORMS - 1, n));
    }

    // 台 p (1〜4) に守りがいるか。いる台は後ろから数える (4 は必ず)
    public static boolean fight(Run run, int players, int p) {
        return p > PLATFORMS - 1 - fights(run, players);
    }

    // 台 p の羽守の数
    public static int wardens(Run run, int players, int p) {
        int w = 1 + (p >= 3 ? 1 : 0) + (run.mods.number(0) - 1) + extra(players, 2);
        w = Math.max(1, Abyss.plusOne(run.mods, w));
        return run.mods.quiet() ? 1 : w;
    }

    // 台 p の支え (雷蛾 / 雷乞い) の数
    public static int support(Run run, int players, int p) {
        if (run.mods.quiet()) {
            return 0;
        }
        if (run.mods.enemy() == 1) {
            return (p >= 3 ? 1 : 0) + (p == 4 && players >= 2 ? 1 : 0);
        }
        return (p >= 2 ? 1 : 0) + (p == 4 ? 1 : 0) + (players >= 3 ? 1 : 0);
    }

    // ---------------------------------------------------------------- 状態
    private static boolean[] arrived(Run run) {
        return run.get("arrived", () -> new boolean[PLATFORMS]);
    }

    // 台 p を片付けたか (守りのない台は着いたら片付いたことになる)
    private static boolean[] cleared(Run run) {
        return run.get("cleared", () -> new boolean[PLATFORMS]);
    }

    private static List<UUID> mobs(Run run, int p) {
        return run.get("mobs." + p, ArrayList::new);
    }

    // 風の穴 i (台 i → 台 i + 1) が目覚めているか。始めの穴はいつも。そのほかは台 i を片付けたら
    static boolean ventOpen(Run run, int i) {
        return i == 0 || cleared(run)[i];
    }

    @Override
    public void arm(ServerLevel back, Run run) {
        run.put("nextSky", 0L);
        arrived(run)[0] = true;
        cleared(run)[0] = true;
    }

    @Override
    public void disarm(ServerLevel back, Run run) {
        for (int p = 0; p < PLATFORMS; p++) {
            discardAll(back, mobs(run, p));
        }
    }

    @Override
    public void tick(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        int n = count(inside);
        if (!run.data.containsKey("welcomed")) {
            run.put("welcomed", true);
            bar(inside, ModText.objective("message.skyabyss.trial.s10.start"));
        }
        // 風切りの羽根
        for (ServerPlayer p : inside) {
            if (!GaleFeather.holds(p) && p.isAlive() && !p.isSpectator() && p.getInventory().add(new ItemStack(StormBackSetup.GALE_FEATHER.get()))) {
                Notice.bar(p, ModText.hint("message.skyabyss.trial.s10.feather"));
                back.playSound(null, p.blockPosition(), StormSetup.GALE.get(), SoundSource.PLAYERS, 0.8f, 1.4f);
            }
        }
        // 落ちた: 休み場へ
        for (ServerPlayer p : inside) {
            if (fell(p, Y)) {
                toRest(back, run, p, restByX(p, "s10a", "s10m", -55, false), "message.skyabyss.trial.s10.fell");
            }
        }
        if (run.mods.goal() == 0 && run.getInt("hits") > 0) {
            run.goalOk = false;
        }
        if (run.cleared) {
            return;
        }
        boolean peaceful = back.getDifficulty() == Difficulty.PEACEFUL;
        boolean[] arrived = arrived(run);
        boolean[] cleared = cleared(run);
        boolean visual = false;
        for (int p = 1; p < PLATFORMS; p++) {
            Island is = StormDeck.S10_PLATFORMS[p];
            // 着いた (初めて): 守りが湧く。守りのない台は片付いたことになる
            if (!arrived[p]) {
                for (ServerPlayer pl : inside) {
                    if (is.over(pl.getX(), pl.getZ()) && pl.getY() >= Y + 0.5 && pl.onGround()) {
                        arrived[p] = true;
                        break;
                    }
                }
                if (arrived[p]) {
                    if (fight(run, n, p) && !peaceful) {
                        spawnGuards(back, run, n, p);
                        bar(inside, ModText.battle("message.skyabyss.trial.s10.guards", p));
                    } else {
                        cleared[p] = true;
                        visual = true;
                        if (p < PLATFORMS - 1) {
                            bar(inside, ModText.progress("message.skyabyss.trial.s10.quiet"));
                            back.playSound(null, StormDeck.s10Vent(p), StormSetup.GALE.get(), SoundSource.BLOCKS, 1.0f, 1.2f);
                        }
                    }
                }
            }
            // 守りを倒した
            if (arrived[p] && !cleared[p]) {
                leash(back, run, p);
                if (alive(back, mobs(run, p)) == 0) {
                    cleared[p] = true;
                    visual = true;
                    if (p < PLATFORMS - 1) {
                        bar(inside, ModText.progress("message.skyabyss.trial.s10.cleared_platform"));
                        back.playSound(null, StormDeck.s10Vent(p), StormSetup.GALE.get(), SoundSource.BLOCKS, 1.4f, 1.0f);
                        TrialFx.sound(back, run.group, StormDeck.s10Vent(p), SoundEvents.BELL_RESONATE, 1.0f, 1.2f);
                    }
                }
            }
        }
        if (visual) {
            TrialRuntime.refresh(back, run);
        }
        // 最後の台の守りを倒した
        if (cleared[PLATFORMS - 1]) {
            finish(back, run, inside, now, "message.skyabyss.trial.s10.cleared");
            return;
        }
        // 空の雷 (障害): 守りのいる台で 2 秒の予告の輪
        if (run.mods.hasHazard(1) && now >= run.getLong("nextSky") && !peaceful) {
            List<ServerPlayer> fighting = where(inside, pl -> {
                for (int p = 1; p < PLATFORMS; p++) {
                    if (arrived[p] && !cleared[p] && StormDeck.S10_PLATFORMS[p].over(pl.getX(), pl.getZ())) {
                        return true;
                    }
                }
                return false;
            });
            if (!fighting.isEmpty()) {
                ServerPlayer target = fighting.get(back.random.nextInt(fighting.size()));
                run.put("nextSky", now + SKY_EVERY);
                StormStrike.call(back, target.position(), SKY_WARN, 8f, null, run.group);
            }
        }
    }

    // 守りを湧かせる (台の四隅の柱を避けた四方)。向きは配置で回る
    private void spawnGuards(ServerLevel back, Run run, int players, int p) {
        Island is = StormDeck.S10_PLATFORMS[p];
        int w = wardens(run, players, p);
        int s = support(run, players, p);
        double base = Math.toRadians(45 + 90 * run.mods.layout());
        for (int i = 0; i < w; i++) {
            double a = base + Math.PI * 2 * i / w;
            Mob m = spawn(back, StormMobSetup.FEATHER_WARDEN.get(), run.group, is.cx() + 0.5 + Math.cos(a) * 3.5, Y + 1, is.cz() + 0.5 + Math.sin(a) * 3.5);
            if (m != null) {
                mobs(run, p).add(m.getUUID());
            }
        }
        for (int i = 0; i < s; i++) {
            double a = base + Math.PI * (0.5 + 2.0 * i / Math.max(1, s));
            Mob m = run.mods.enemy() == 1
                    ? spawn(back, StormMobSetup.THUNDER_SUPPLICANT.get(), run.group, is.cx() + 0.5 + Math.cos(a) * 4.5, Y + 1, is.cz() + 0.5 + Math.sin(a) * 4.5)
                    : spawn(back, StormMobSetup.STATIC_MOTH.get(), run.group, is.cx() + 0.5 + Math.cos(a) * 3, Y + 4, is.cz() + 0.5 + Math.sin(a) * 3);
            if (m != null) {
                mobs(run, p).add(m.getUUID());
            }
        }
        back.playSound(null, BlockPos.containing(is.cx(), Y + 2, is.cz()), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 0.8f, 1.1f);
    }

    // 台から出た守りを戻す
    private void leash(ServerLevel back, Run run, int p) {
        Island is = StormDeck.S10_PLATFORMS[p];
        for (UUID id : new ArrayList<>(mobs(run, p))) {
            Entity e = back.getEntity(id);
            if (!(e instanceof Mob m) || !m.isAlive()) {
                continue;
            }
            if (m.getY() < Y - 4 || Math.abs(m.getX() - is.cx()) > is.size() + 5 || Math.abs(m.getZ() - is.cz()) > is.size() + 5) {
                m.teleportTo(is.cx() + 0.5, m instanceof jp.revati.skyabyss.titan.storm.mob.StaticMoth ? Y + 4 : Y + 1, is.cz() + 0.5);
                m.setDeltaMovement(Vec3.ZERO);
            }
        }
    }

    // 毎 tick: 目覚めた風の穴の昇り風と、横風
    @Override
    public void perTick(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        double h = StormBackClock.liftHeight(back);
        for (ServerPlayer p : inside) {
            if (p.isSpectator() || p.getAbilities().flying || !p.isAlive()) {
                continue;
            }
            for (int i = 0; i < PLATFORMS - 1; i++) {
                if (!ventOpen(run, i)) {
                    continue;
                }
                BlockPos c = StormDeck.s10Vent(i);
                double base = c.getY() + 1;
                double dx = p.getX() - (c.getX() + 0.5);
                double dz = p.getZ() - (c.getZ() + 0.5);
                if (dx * dx + dz * dz <= StormDeck.VENT_R * StormDeck.VENT_R && p.getY() >= base - 0.2 && p.getY() <= base + h + 1.5) {
                    Updraft.lift(p, base + h);
                    if (now % 4 == 0) {
                        puff(back, run.group, ParticleTypes.CLOUD, c.getX() + 0.5, c.getY() + 1 + back.random.nextDouble() * h, c.getZ() + 0.5, 1, 0.4, 0.0, 0.4, 0.0);
                    }
                    break;
                }
            }
        }
        if (run.mods.hasHazard(0)) {
            gust(back, run, inside, now);
        }
        // 穴の粒 (目覚めた穴は光の柱)
        if (now % 10 == 0) {
            for (int i = 0; i < PLATFORMS - 1; i++) {
                BlockPos c = StormDeck.s10Vent(i);
                if (ventOpen(run, i)) {
                    puff(back, run.group, ParticleTypes.END_ROD, c.getX() + 0.5, c.getY() + 1 + h, c.getZ() + 0.5, 1, 0.4, 0.0, 0.4, 0.0);
                }
            }
        }
    }

    // 横風 (障害): 台と台のあいだの空中。予告 → 吹く (滑空している人を南北へ押す)
    private void gust(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        long t = Math.max(0, now - run.startTick);
        int ph = (int) Math.floorMod(t, (long) GUST_CYCLE);
        int dir = ((t / GUST_CYCLE) & 1) == 0 ? 1 : -1;
        for (ServerPlayer p : inside) {
            if (p.onGround() || p.getY() < Y - 1 || onAnyPlatform(p)) {
                continue;
            }
            if (ph < GUST_WARN) {
                if (now % 6 == 0) {
                    puff(back, run.group, ParticleTypes.CLOUD, p.getX(), p.getY() + 1, p.getZ() - dir * 6, 4, 0.4, 0.8, 1.0, 0.0);
                }
                if (ph == 0) {
                    back.playSound(null, p.blockPosition(), StormSetup.CROSSWIND.get(), SoundSource.AMBIENT, 1.2f, 1f);
                }
            } else if (ph < GUST_WARN + GUST_BLOW && GaleFeather.gliding(p)) {
                Vec3 d = Updraft.actualMotion(p);
                p.setDeltaMovement(d.x, d.y, d.z + GUST_PUSH * dir);
                p.hurtMarked = true;
            }
        }
    }

    private static boolean onAnyPlatform(ServerPlayer p) {
        for (Island is : StormDeck.S10_PLATFORMS) {
            if (is.over(p.getX(), p.getZ())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void overlay(ServerLevel back, Run run, BiConsumer<BlockPos, BlockState> out) {
        for (int i = 1; i < PLATFORMS - 1; i++) {
            if (!ventOpen(run, i)) {
                continue;
            }
            BlockPos c = StormDeck.s10Vent(i);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    out.accept(c.offset(dx, 0, dz), dx == 0 && dz == 0 ? Blocks.SEA_LANTERN.defaultBlockState() : ON);
                }
            }
        }
        // 始めの穴は最初から目覚めている
        BlockPos c0 = StormDeck.s10Vent(0);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                out.accept(c0.offset(dx, 0, dz), dx == 0 && dz == 0 ? Blocks.SEA_LANTERN.defaultBlockState() : ON);
            }
        }
    }

    // ---------------------------------------------------------------- 検査
    @Override
    public List<String> audit(ServerLevel back) {
        List<String> bad = new ArrayList<>();
        for (int i = 0; i < PLATFORMS; i++) {
            Island is = StormDeck.S10_PLATFORMS[i];
            for (BlockPos p : is.floor()) {
                if (back.getBlockState(p).isAir()) {
                    bad.add("S10 platform " + i + " has a hole at " + p);
                    break;
                }
            }
            if (i < PLATFORMS - 1) {
                if (back.getBlockState(StormDeck.s10Vent(i)).isAir()) {
                    bad.add("S10 vent " + i + " is missing");
                }
                double margin = StormDeck.glideMargin(StormDeck.s10Vent(i), StormDeck.S10_PLATFORMS[i + 1], StormBackClock.CALM_LIFT);
                if (margin > 0.72) {
                    bad.add("S10 hop " + i + " is too long for the calm updraft (margin " + String.format("%.2f", margin) + ")");
                }
            }
        }
        if (!jp.revati.skyabyss.trial.TrialGoal.isMark(back.getBlockState(StormDeck.S10_STONE))) {
            bad.add("S10 stone missing");
        }
        return bad;
    }
}
