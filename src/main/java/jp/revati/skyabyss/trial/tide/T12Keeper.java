package jp.revati.skyabyss.trial.tide;

import static jp.revati.skyabyss.titan.tide.back.TideDeck.G;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.BiConsumer;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.abyss.Abyss;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.titan.tide.back.PearlKeeper;
import jp.revati.skyabyss.titan.tide.back.TideBackSetup;
import jp.revati.skyabyss.titan.tide.back.TideDeck;
import jp.revati.skyabyss.titan.tide.mob.TideMob;
import jp.revati.skyabyss.titan.tide.mob.TideMobSetup;
import jp.revati.skyabyss.trial.Trial.Def;
import jp.revati.skyabyss.trial.Trial.Mods;
import jp.revati.skyabyss.trial.TrialCatalog;
import jp.revati.skyabyss.trial.TrialCell;
import jp.revati.skyabyss.trial.TrialFx;
import jp.revati.skyabyss.trial.TrialRock;
import jp.revati.skyabyss.trial.TrialRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// T12 真珠守りの試し (闘・戦う・★★★。docs/design/29 の 3.2)。息の間の頂の「真珠守り」と同じ守り手 (PearlKeeper) が、小さな群れを従えて現れる。
// 一つの広間 (円い床。半径 10.4) で決着をつける。真ん中の台に守り手の真珠がある。
//   決まりを教える: 守り手は、予告 (床の印) のあとに技を出す。最初は二つの技だけ (組 0 = 吹き上げと泡の弾 / 組 1 = 殻の突進と吹き下ろしの輪)。
//   技のあとの守り手はぐったりし、そのあいだは傷が 1.5 倍通る (叩く間)。
//   ひねる: 体力が 2/3 と 1/3 になると守り手は殻に籠もる (傷を受けない)。そのたびに群れが湧き (近くにいる人の脇)、四つの技がすべて解かれる。
//   群れは真珠へ向かい (人が近くにいなければ)、そばに居座ると真珠が割れる。群れを払ってから、殻から出た守り手を叩く。
//   一人・二人: 幕で狭い広間 (半径 6.8 の内側。守り手の舞台も小さい)。 三人から: 全面 (舞台の半径 8.5)・群れが増える。
// 修飾: 配置 3 (真珠の台の向きと群れの湧く間隔)・数字 2 (群れの数 2・3・4 / 守り手の体力 小・標準・大)・敵の組 2 (技の組と群れの種類)・障害 (高波)・任意の目標 (真珠を割らずに)
public final class T12Keeper implements TrialCell {
    public static final int Y = G;
    public static final int CX = TideDeck.T12_CX;
    public static final int CZ = TideDeck.T12_CZ;
    public static final double R_HALL = 10.4;
    public static final double R_FULL = 8.5;
    public static final double R_SMALL = 6.0;
    // 幕 (小さな広間の外側の輪)
    static final double CURTAIN_R1 = 6.8;
    static final double CURTAIN_R2 = 7.8;
    static final int[] BROOD = {2, 3, 4};
    static final double[] HP = {0.30, 0.40, 0.50};
    static final int PEARL_HP = 3;
    static final int CRACK_EVERY = 60;
    static final int ROCK_EVERY = 240;
    // 技の組 (敵の組 0 / 1)
    static final List<List<String>> MOVES = List.of(List.of("updraft", "volley"), List.of("charge", "downdraft"));

    private final Def def = TrialCatalog.get("T12");
    private final Ports ports = new Ports(TideDeck.T12_GATE, false, TideDeck.T12_EXIT, TideDeck.T12_STONE, TideDeck.T12_PAD, TideDeck.REST_H2,
            new net.minecraft.world.phys.AABB(75, Y + 1, 75, 96, Y + 15, 103), TideDeckRests.of("t12"));

    @Override
    public Def def() {
        return def;
    }

    @Override
    public Ports ports() {
        return ports;
    }

    // ---------------------------------------------------------------- 形・数字
    // 床の円の中か (広間の床を張る範囲)
    public static boolean inHall(int x, int z) {
        return Math.hypot(x - CX, z - CZ) <= R_HALL;
    }

    // 幕のマス (小さな広間のとき、入ったあとに閉じる輪)
    public static List<BlockPos> curtain() {
        List<BlockPos> out = new ArrayList<>();
        for (int dx = -9; dx <= 9; dx++) {
            for (int dz = -9; dz <= 9; dz++) {
                double r = Math.hypot(dx, dz);
                if (r >= CURTAIN_R1 && r < CURTAIN_R2) {
                    for (int y = Y + 1; y <= Y + 9; y++) {
                        out.add(new BlockPos(CX + dx, y, CZ + dz));
                    }
                }
            }
        }
        return List.copyOf(out);
    }

    // 真珠の台は、配置ごとに、中心から少しずれる (向き)
    public static BlockPos pearl(int layout) {
        return switch (layout) {
            case 1 -> new BlockPos(CX - 2, Y + 1, CZ - 1);
            case 2 -> new BlockPos(CX + 2, Y + 1, CZ - 1);
            default -> new BlockPos(CX, Y + 1, CZ - 2);
        };
    }

    public static List<BlockPos> pearls() {
        return List.of(pearl(0), pearl(1), pearl(2));
    }

    // 守り手の舞台の半径 (人数で。一人・二人は小さい)
    public static double arenaRadius(int players) {
        return players <= 2 ? R_SMALL : R_FULL;
    }

    public static boolean small(int players) {
        return players <= 2;
    }

    // 群れの数 (殻 shell = 1, 2。人数で増える)
    public static int brood(Run run, int shell, int players) {
        int n = BROOD[run.mods.number(0)] + (int) Math.ceil((Math.max(1, players) - 1) * 0.6) + (shell >= 2 ? 1 : 0);
        n = Abyss.plusOne(run.mods, n);
        return run.mods.quiet() ? Math.max(1, n - 2) : n;
    }

    public static double hpFraction(Mods m) {
        double f = HP[m.number(1)];
        return m.abyss() ? f * Abyss.NUMBER_STEP : f;
    }

    public static String broodKind(Run run, int j) {
        return run.mods.enemy() == 1 ? (j % 2 == 0 ? "scuttler" : "puffer") : (j % 3 == 2 ? "thrall" : "scuttler");
    }

    private static EntityType<? extends Mob> type(String kind) {
        return switch (kind) {
            case "puffer" -> TideMobSetup.BUBBLE_PUFFER.get();
            case "thrall" -> TideMobSetup.CORAL_THRALL.get();
            default -> TideMobSetup.REEF_SCUTTLER.get();
        };
    }

    // ---------------------------------------------------------------- 状態
    static final class S {
        int phase;
        UUID keeperId;
        final List<UUID> brood = new ArrayList<>();
        final List<ServerPlayer> fighters = new ArrayList<>();
        int pearlHp = PEARL_HP;
        int crackTicks;
        int shells;
        boolean wasShelled;
        boolean small;
        long emptySince;
        boolean dead;
        long view = -1;
    }

    private static S st(Run run) {
        return (S) run.data.get("s");
    }

    @Override
    public void arm(ServerLevel back, Run run) {
        S s = new S();
        run.put("s", s);
        run.put("nextRock", back.getGameTime() + 400L);
        GroupGates.open(back, run.group, curtain());
        if (run.cleared) {
            s.phase = 2;
            GroupGates.open(back, run.group, TideDeck.T12_EXIT);
        }
    }

    @Override
    public void disarm(ServerLevel back, Run run) {
        resetFight(back, run);
        GroupGates.release(back, run.group, curtain());
        if (!run.cleared) {
            GroupGates.release(back, run.group, TideDeck.T12_EXIT);
        }
    }

    private void clearBrood(ServerLevel back, S s) {
        for (UUID id : s.brood) {
            Entity e = back.getEntity(id);
            if (e != null) {
                e.discard();
            }
        }
        s.brood.clear();
    }

    private void resetFight(ServerLevel back, Run run) {
        if (!(run.data.get("s") instanceof S s)) {
            return;
        }
        clearBrood(back, s);
        if (s.keeperId != null) {
            Entity e = back.getEntity(s.keeperId);
            if (e != null) {
                e.discard();
            }
            s.keeperId = null;
        }
        s.fighters.clear();
        if (s.phase == 1) {
            s.phase = 0;
        }
        s.pearlHp = PEARL_HP;
        s.crackTicks = 0;
        s.shells = 0;
        s.wasShelled = false;
        s.emptySince = 0;
        s.view = -1;
        if (!run.cleared) {
            GroupGates.open(back, run.group, curtain());
        }
    }

    // 自己テスト用
    public static boolean fightOn(Run run) {
        return run.data.get("s") instanceof S s && s.phase == 1;
    }

    public static PearlKeeper keeperOf(ServerLevel back, Run run) {
        return run.data.get("s") instanceof S s && s.keeperId != null && back.getEntity(s.keeperId) instanceof PearlKeeper k ? k : null;
    }

    public static int pearlHp(Run run) {
        return run.data.get("s") instanceof S s ? s.pearlHp : PEARL_HP;
    }

    public static List<UUID> broodOf(Run run) {
        return run.data.get("s") instanceof S s ? s.brood : List.of();
    }

    public static boolean smallStage(Run run) {
        return run.data.get("s") instanceof S s && s.small;
    }

    private PearlKeeper keeper(ServerLevel back, S s) {
        return s.keeperId != null && back.getEntity(s.keeperId) instanceof PearlKeeper k ? k : null;
    }

    private static boolean inArena(ServerPlayer p) {
        return Math.hypot(p.getX() - (CX + 0.5), p.getZ() - (CZ + 0.5)) <= R_HALL + 0.5 && p.getY() >= Y + 0.9;
    }

    private void engage(ServerLevel back, Run run, S s, List<ServerPlayer> inside, long now) {
        Mods m = run.mods;
        int n = Math.max(1, s.fighters.size());
        s.small = small(n);
        PearlKeeper boss = TideBackSetup.PEARL_KEEPER.get().create(back);
        if (boss == null) {
            return;
        }
        boss.addTag(TideMob.TRIAL_TAG);
        boss.setGroupId(run.group);
        boss.setChallengers(n);
        boss.setDevFighters(s.fighters);
        boss.setTrialArena(CX + 0.5, CZ + 0.5, arenaRadius(n));
        boss.setAllowedActions(MOVES.get(Math.max(0, m.enemy())));
        var health = boss.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(PearlKeeper.HEALTH * hpFraction(m));
            boss.setHealth(boss.getMaxHealth());
        }
        boss.setDefeatHook(k -> s.dead = true);
        double hx = CX + 0.5;
        double hz = CZ + 3.5;
        boss.moveTo(hx, Y + 1, hz, 180f, 0);
        back.addFreshEntity(boss);
        boss.emerge(back, new net.minecraft.world.phys.Vec3(hx, Y + 1, hz));
        s.keeperId = boss.getUUID();
        s.phase = 1;
        s.pearlHp = PEARL_HP;
        s.dead = false;
        if (s.small) {
            GroupGates.seal(back, run.group, curtain());
        }
        TrialFx.bar(inside, ModText.battle("message.skyabyss.trial.t12.engage"));
        TrialFx.bar(inside, ModText.hint(s.small ? "message.skyabyss.trial.t12.curtain" : "message.skyabyss.trial.t12.full"));
        TrialFx.bar(inside, ModText.hint("message.skyabyss.trial.t12.moves" + Math.max(0, m.enemy())));
        TrialRuntime.refresh(back, run);
    }

    private void spawnBrood(ServerLevel back, Run run, S s, int shell, int players) {
        int total = brood(run, shell, players);
        Random rng = new Random(run.seed ^ (shell * 977L));
        double reach = arenaRadius(players) - 1.5;
        for (int j = 0; j < total; j++) {
            // 近くの人の脇に湧く (遠くから走らせない)
            double ax;
            double az;
            if (!s.fighters.isEmpty()) {
                ServerPlayer near = s.fighters.get(rng.nextInt(s.fighters.size()));
                double a = rng.nextDouble() * Math.PI * 2;
                ax = near.getX() + Math.cos(a) * (4.5 + rng.nextDouble() * 2.0);
                az = near.getZ() + Math.sin(a) * (4.5 + rng.nextDouble() * 2.0);
            } else {
                double a = rng.nextDouble() * Math.PI * 2;
                ax = CX + 0.5 + Math.cos(a) * reach;
                az = CZ + 0.5 + Math.sin(a) * reach;
            }
            double dx = ax - (CX + 0.5);
            double dz = az - (CZ + 0.5);
            double d = Math.hypot(dx, dz);
            if (d > reach && d > 1e-6) {
                ax = CX + 0.5 + dx / d * reach;
                az = CZ + 0.5 + dz / d * reach;
            }
            String kind = broodKind(run, j);
            BlockPos at = BlockPos.containing(ax, Y + 1, az);
            if (kind.equals("puffer")) {
                at = at.above(2);
            }
            Mob mob = TideSpawns.spawn(back, type(kind), at, run.group);
            if (mob != null) {
                s.brood.add(mob.getUUID());
            }
        }
        TrialFx.bar(Groups.online(back, run.group), ModText.battle("message.skyabyss.trial.t12.brood", shell, total));
        TrialFx.sound(back, run.group, new BlockPos(CX, Y + 1, CZ), SoundEvents.DROWNED_AMBIENT_WATER, 1.6f, 0.6f);
    }

    @Override
    public void tick(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        Mods m = run.mods;
        S s = st(run);
        if (!run.data.containsKey("welcomed")) {
            run.put("welcomed", true);
            TrialFx.bar(inside, ModText.objective("message.skyabyss.trial.t12.start"));
        }
        if (run.cleared || s.phase == 2) {
            return;
        }
        // 舞台の中の人 (守り手が狙う人。生きた一覧を守り手に渡している)
        s.fighters.clear();
        for (ServerPlayer p : inside) {
            // 幕で狭めたあとは、幕の内側の人だけが相手 (外にいる人は戦いに入らない)
            boolean in = s.phase == 0 || !s.small || Math.hypot(p.getX() - (CX + 0.5), p.getZ() - (CZ + 0.5)) < CURTAIN_R2;
            if (inArena(p) && p.isAlive() && in) {
                s.fighters.add(p);
            }
        }
        int n = Math.max(1, s.fighters.size());
        if (s.phase == 0) {
            // 始め: 人数で幕が決まる前に、人が舞台の真ん中へ寄った
            double engage = small(n) ? CURTAIN_R1 - 0.4 : R_FULL - 0.5;
            for (ServerPlayer p : s.fighters) {
                if (Math.hypot(p.getX() - (CX + 0.5), p.getZ() - (CZ + 0.5)) < engage) {
                    engage(back, run, s, inside, now);
                    break;
                }
            }
            return;
        }
        PearlKeeper boss = keeper(back, s);
        if (s.dead || boss != null && !boss.isAlive() && boss.isDeadOrDying()) {
            win(back, run, s, inside, now);
            return;
        }
        if (boss == null) {
            // 守り手が消えた (しまった): 戦いを戻す
            resetFight(back, run);
            TrialFx.bar(inside, ModText.hint("message.skyabyss.trial.t12.reset"));
            return;
        }
        // 全員が舞台を出て 6 秒で、最初に戻る
        if (s.fighters.isEmpty()) {
            if (s.emptySince == 0) {
                s.emptySince = now;
            } else if (now - s.emptySince > 120) {
                resetFight(back, run);
                TrialFx.bar(inside, ModText.hint("message.skyabyss.trial.t12.reset"));
                TrialRuntime.refresh(back, run);
                return;
            }
        } else {
            s.emptySince = 0;
            boss.setChallengers(Math.max(n, 1));
        }
        // 殻に籠もった: 群れが湧き、四つの技が解かれる
        boolean shelled = boss.mode() == PearlKeeper.MODE_SHELLED;
        if (shelled && !s.wasShelled) {
            s.shells++;
            boss.setAllowedActions(null);
            spawnBrood(back, run, s, s.shells, n);
        }
        s.wasShelled = shelled;
        // 群れ: 近くに人がいなければ、真珠へ向かう。真珠のそばに居座ると真珠が割れる
        BlockPos pearl = pearl(m.layout());
        int camping = 0;
        for (int i = s.brood.size() - 1; i >= 0; i--) {
            Entity e = back.getEntity(s.brood.get(i));
            if (!(e instanceof Mob mob) || !mob.isAlive()) {
                s.brood.remove(i);
                continue;
            }
            boolean nearPlayer = false;
            for (ServerPlayer p : s.fighters) {
                if (p.distanceToSqr(mob) < 49) {
                    nearPlayer = true;
                    break;
                }
            }
            double d = Math.hypot(mob.getX() - (pearl.getX() + 0.5), mob.getZ() - (pearl.getZ() + 0.5));
            boolean melee = !(mob.getType() == TideMobSetup.BUBBLE_PUFFER.get());
            if (!nearPlayer && d > 1.6 && melee) {
                mob.getNavigation().moveTo(pearl.getX() + 0.5, Y + 1, pearl.getZ() + 0.5, 1.1);
            }
            if (melee && d < 2.4) {
                camping++;
            }
        }
        if (camping > 0 && s.pearlHp > 0) {
            s.crackTicks += 5 * Math.min(camping, 3);
            if (s.crackTicks >= CRACK_EVERY) {
                s.crackTicks = 0;
                s.pearlHp--;
                TrialFx.sound(back, run.group, pearl, SoundEvents.AMETHYST_BLOCK_BREAK, 1.6f, 0.5f);
                TrialFx.particles(back, run.group, ParticleTypes.CRIT, pearl.getX() + 0.5, pearl.getY() + 1.2, pearl.getZ() + 0.5, 12, 0.4, 0.4, 0.4, 0.2);
                if (m.goal() == 0) {
                    run.goalOk = false;
                }
                TrialFx.bar(inside, s.pearlHp > 0 ? ModText.deny("message.skyabyss.trial.t12.cracked", s.pearlHp) : ModText.deny("message.skyabyss.trial.t12.pearl_gone"));
                TrialRuntime.refresh(back, run);
            }
        } else {
            s.crackTicks = Math.max(0, s.crackTicks - 5);
        }
        if (m.hasHazard(0)) {
            TrialRock.tick(back, run, s.fighters, now, ROCK_EVERY, 4f, ParticleTypes.BUBBLE, ParticleTypes.SPLASH, SoundEvents.PLAYER_SPLASH_HIGH_SPEED);
        }
        long view = s.pearlHp | (long) s.phase << 4;
        if (view != s.view) {
            s.view = view;
            TrialRuntime.refresh(back, run);
        }
    }

    private void win(ServerLevel back, Run run, S s, List<ServerPlayer> inside, long now) {
        clearBrood(back, s);
        s.phase = 2;
        run.cleared = true;
        run.clearedAt = now;
        GroupGates.open(back, run.group, curtain());
        GroupGates.open(back, run.group, TideDeck.T12_EXIT);
        TrialFx.sound(back, run.group, TideDeck.T12_STONE, SoundEvents.BELL_RESONATE, 1.5f, 0.8f);
        TrialFx.bar(inside, ModText.progress("message.skyabyss.trial.t12.defeated"));
        TrialRuntime.refresh(back, run);
    }

    // ---------------------------------------------------------------- 見かけ
    @Override
    public void overlay(ServerLevel back, Run run, BiConsumer<BlockPos, BlockState> out) {
        int layout = run.mods.layout();
        int hp = run.data.get("s") instanceof S s ? s.pearlHp : PEARL_HP;
        BlockState pearl = switch (hp) {
            case 3 -> Blocks.SEA_LANTERN.defaultBlockState();
            case 2 -> Blocks.PRISMARINE_BRICKS.defaultBlockState();
            case 1 -> Blocks.DARK_PRISMARINE.defaultBlockState();
            default -> Blocks.BLACKSTONE.defaultBlockState();
        };
        for (int l = 0; l < 3; l++) {
            BlockPos p = pearl(l);
            // 使わない配置の台は、ただの台 (真珠のないもの。世界の台と同じ石)
            out.accept(p, l == layout ? pearl : Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
        }
    }

    @Override
    public List<String> audit(ServerLevel back) {
        List<String> bad = new ArrayList<>();
        // 床の円: 入口 (戸の足もと) から舞台まで床が続く。四隅は石
        for (int x = 83; x <= 87; x++) {
            if (back.getBlockState(new BlockPos(x, Y, 75)).isAir() || !back.getBlockState(new BlockPos(x, Y + 1, 75)).isAir()) {
                bad.add("T12 entrance floor is missing at x " + x);
                break;
            }
        }
        for (int l = 0; l < 3; l++) {
            BlockPos p = pearl(l);
            if (back.getBlockState(p.below()).isAir()) {
                bad.add("T12 pearl pedestal " + l + " has no floor at " + p);
            }
            if (Math.hypot(p.getX() - CX, p.getZ() - CZ) > 3) {
                bad.add("T12 pearl " + l + " is too far from the middle");
            }
        }
        // 幕の輪は舞台の内側 (半径 6.8) より外、広間の床の内側にある
        for (BlockPos p : curtain()) {
            if (!inHall(p.getX(), p.getZ())) {
                bad.add("T12 curtain cell outside the hall at " + p);
                break;
            }
            if (!back.getBlockState(p).is(jp.revati.skyabyss.registry.ModBlocks.SEALED_GATE.get())) {
                bad.add("T12 curtain gate missing at " + p);
                break;
            }
        }
        if (R_SMALL >= CURTAIN_R1) {
            bad.add("T12 the small arena reaches the curtain");
        }
        if (!jp.revati.skyabyss.trial.TrialGoal.isMark(back.getBlockState(TideDeck.T12_STONE))) {
            bad.add("T12 stone missing");
        }
        return bad;
    }
}
