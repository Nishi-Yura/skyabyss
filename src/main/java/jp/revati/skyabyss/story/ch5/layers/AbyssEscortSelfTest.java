package jp.revati.skyabyss.story.ch5.layers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.abyss.arts.AbyssArt;
import jp.revati.skyabyss.opening.site.Blueprint;
import jp.revati.skyabyss.party.PartyData;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.story.ch5.AbyssBuild;
import jp.revati.skyabyss.story.ch5.AbyssEntrance;
import jp.revati.skyabyss.story.ch5.AbyssWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

// SELFTEST abyss escort: 形 B の層 (塩・灰) の護りの下り道 (docs/design/36 の 8.1・8.4。story/ch5/layers/AbyssEscort)。SKYABYSS_SELFTEST_ONLY="abyss escort"
//   形: 設計図の上で、着く所から道すじ (esc_<層>_<n>) の一マスずつ・待ち場・門の前・主の間・帰りの鎖に歩いて届く (井戸は風と梯子でつながり)
//   一人 (塩): 下り口に立つと始まり、杭 (右クリック)・井戸の灯り (右クリック)・錨を守る (45 秒と縄張りの主) で印が一つずつ入り、
//             逃げの道を走りきると印三つ・送った記録・段 salt_lord・格子の門が上がる。板の道と梯子が残る
//   やり直し: 待ち場 2 で気力が尽きると、待ち場 1 へ戻って (確かにした数はそのまま) 歩き直す。逃げの道で崩れに呑まれると、待ち場 3 からやり直し (崩した床は戻る)
//   二人のパーティ (灰): 迷路の出口の火・三つの術の封 (光・重さ・鼓動を封の前で)・縄張りを払う (二つの群れと主) → 闇の波を越えて、二人とも印三つと段 ash_lord
public final class AbyssEscortSelfTest {
    static final int MAX_STEPS = 20000;

    private AbyssEscortSelfTest() {
    }

    public static void load(MinecraftServer server) {
        AbyssLayersV2SelfTest.load(server);
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        if (level == null) {
            SkyAbyss.LOGGER.error("SELFTEST abyss escort FAILED: the abyss dimension is missing");
            return;
        }
        AbyssEscort.paused = true;
        try {
            AbyssEscort.stopAll(level);
            layout(f);
            solo(f, server, level);
            party(f, server, level);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST abyss escort crashed", e);
            f.add("crashed: " + e);
        } finally {
            AbyssEscort.stopAll(level);
            AbyssEscort.lookup = null;
            AbyssEscort.paused = false;
            for (String layer : AbyssEscort.LAYERS) {
                AbyssEscort.resetGadgets(level, layer);
            }
            LayerFights.resetForTest(level);
            LayerPuzzles.resetForTest(server);
            for (int cx = -5; cx <= 4; cx++) {
                for (int cz = -5; cz <= 4; cz++) {
                    level.setChunkForced(cx, cz, false);
                }
            }
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST abyss escort PASSED (type B layout reachable on foot; solo salt escort: stake, well lamp, anchor hold, "
                    + "collapse escape → three marks, escort record, salt_lord, gate rises; endurance failure returns to the last resting place; "
                    + "swallowed by the collapse restarts at the third resting place and restores the floor; party of two on ash: maze fire, "
                    + "three-arts seal, clear the ember ground, dark wave → both reach ash_lord)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST abyss escort FAILED: {}", m));
        }
    }

    // ---------------------------------------------------------------- 形 (設計図の上で歩く)
    static void layout(List<String> f) {
        Blueprint bp = Blueprint.get(AbyssBuild.BLUEPRINT);
        Map<Long, BlockState> b = new HashMap<>(bp.cells.size() * 2);
        for (Blueprint.Cell c : bp.cells) {
            b.put(BlockPos.asLong(c.x(), c.y(), c.z()), c.state());
        }
        Set<Long> open = new HashSet<>();
        AbyssLayersV2SelfTest.box(open, "seal_ash");
        for (String layer : AbyssEscort.LAYERS) {
            BlockPos g = AbyssBuild.mark("gate_" + layer);
            if (g == null) {
                f.add("gate_" + layer + " is missing");
                return;
            }
            for (int x = -1; x <= 1; x++) {
                for (int y = 0; y <= 2; y++) {
                    open.add(BlockPos.asLong(g.getX() + x, g.getY() + y, g.getZ()));
                    open.add(BlockPos.asLong(g.getX() + x, g.getY() + y, g.getZ() - 1));
                }
            }
        }
        Map<Long, BlockPos> links = new HashMap<>();
        AbyssLayersV2SelfTest.link(links, "well_salt", "well_salt_top");
        for (String layer : AbyssEscort.LAYERS) {
            BlockPos start = AbyssBuild.mark("arrive_" + layer);
            if (start == null) {
                f.add("arrive_" + layer + " is missing");
                continue;
            }
            Set<Long> seen = AbyssLayersV2SelfTest.walk(b, open, links, start);
            BlockPos la = AbyssBuild.mark("ladder_salt_a");
            BlockPos lb = AbyssBuild.mark("ladder_salt_b");
            int missing = 0;
            String first = null;
            for (int i = 0;; i++) {
                BlockPos n = AbyssBuild.mark("esc_" + layer + "_" + i);
                if (n == null) {
                    check(f, i > 100, layer + " escort trail is too short: " + i);
                    break;
                }
                boolean ladder = la != null && n.getX() == la.getX() && n.getZ() == la.getZ() && n.getY() > la.getY() && n.getY() <= lb.getY() + 1;
                if (!ladder && !seen.contains(n.asLong())) {
                    missing++;
                    first = first == null ? i + " " + n : first;
                }
            }
            check(f, missing == 0, layer + ": " + missing + " escort trail cells are not reached on foot (first " + first + ")");
            for (String m : List.of("escstart_" + layer, "escwait_" + layer + "_1", "escwait_" + layer + "_2", "escwait_" + layer + "_3", "escend_" + layer,
                    "gate_" + layer + "_in", "arena_" + layer, "descend_" + layer, "back_" + layer, "mark_" + layer + "_1", "mark_" + layer + "_2",
                    "mark_" + layer + "_3")) {
                check(f, AbyssLayersV2SelfTest.reached(seen, AbyssBuild.mark(m)), layer + ": " + m + " is not reached on foot");
            }
        }
    }

    // ---------------------------------------------------------------- 一人 (塩)
    static void solo(List<String> f, MinecraftServer server, ServerLevel level) {
        FakePlayer a = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "abyss_escort_solo"));
        LayerData d = LayerData.get(server);
        try {
            check(f, toStage(a, "salt_marks"), "could not put the solo player on salt_marks: " + Layers.stage(a));
            BlockPos s = AbyssBuild.mark("escstart_salt");
            place(a, Vec3.atBottomCenterOf(AbyssBuild.mark("camp_salt")));
            AbyssGuide.Line l = AbyssGuide.lineFor(a);
            check(f, l.key().endsWith(".escort_meet") && s.equals(l.target()), "before the escort the guide should point at the north mouth: " + l);
            place(a, Vec3.atBottomCenterOf(s));
            long now = level.getGameTime() + 100000;
            check(f, AbyssEscort.tryStart(level, "salt", now, List.of(a)), "standing at the salt mouth did not start the escort");
            AbyssEscort.Run run = AbyssEscort.run("salt");
            if (run == null) {
                return;
            }
            check(f, AbyssEscort.nodeCount(run) > 200 && AbyssEscort.waitIndex(run, 1) > 0 && AbyssEscort.waitIndex(run, 2) > AbyssEscort.waitIndex(run, 1)
                    && AbyssEscort.waitIndex(run, 3) > AbyssEscort.waitIndex(run, 2) && AbyssEscort.endIndex(run) > AbyssEscort.waitIndex(run, 3),
                    "the salt trail / resting places are out of order");
            check(f, AbyssEscort.busy("abyss_porter"), "Roku is not marked busy while on the road");
            boolean failedOnce = false;
            boolean escapeFailed = false;
            boolean guided = false;
            int steps = 0;
            for (; steps < MAX_STEPS && run.state() != AbyssEscort.State.DONE; steps++) {
                now++;
                Vec3 at = AbyssEscort.npcPos(run);
                boolean behind = run.state() == AbyssEscort.State.ESCAPE && !escapeFailed;
                place(a, behind ? waitPos(run, 3) : at.add(0.6, 0, 0.6));
                if (run.state() == AbyssEscort.State.WAIT && run.task() == AbyssEscort.Task.STAKE && !guided) {
                    guided = true;
                    AbyssGuide.Line g = AbyssGuide.lineFor(a);
                    check(f, g.key().endsWith(".escort_stake") && AbyssBuild.mark("stake_salt").equals(g.target()), "at the crust the guide should point at the stake: " + g);
                }
                if (run.state() == AbyssEscort.State.WAIT) {
                    if (run.secured() == 1 && !failedOnce) {
                        // 待ち場 2 で気力が尽きる → 待ち場 1 へ戻って歩き直す
                        AbyssEscort.setEndurance(run, 0);
                        tickAt(level, run, now - now % 10 + 10, List.of(a));
                        now = now - now % 10 + 10;
                        failedOnce = true;
                        check(f, run.state() == AbyssEscort.State.WALK && run.secured() == 1 && Math.abs(run.at() - AbyssEscort.waitIndex(run, 1)) < 0.01
                                && run.endurance() >= 99 && run.fails() == 1 && AbyssEscort.mobsForTest(run).isEmpty(),
                                "a worn-out escort did not reset to the first resting place: " + run.state() + " at " + run.at() + " cp " + run.secured());
                        continue;
                    }
                    work(a, level, run, now);
                }
                if (run.state() == AbyssEscort.State.ESCAPE && behind && AbyssEscort.front(run) > AbyssEscort.waitIndex(run, 3) + 4) {
                    // 崩れの後ろに残った: 床が崩れているか見て、呑まれるまで待つ
                    BlockPos cell = AbyssBuild.mark("esc_salt_" + (AbyssEscort.waitIndex(run, 3) + 2));
                    check(f, cell != null && level.getBlockState(cell.below()).isAir(), "the collapse did not break the floor behind");
                }
                AbyssEscort.State before = run.state();
                AbyssEscort.tick(level, run, now, List.of(a));
                if (before == AbyssEscort.State.ESCAPE && run.state() == AbyssEscort.State.ARMED && !escapeFailed) {
                    escapeFailed = true;
                    BlockPos cell = AbyssBuild.mark("esc_salt_" + (AbyssEscort.waitIndex(run, 3) + 2));
                    check(f, Math.abs(run.at() - AbyssEscort.waitIndex(run, 3)) < 0.01, "a swallowed escape did not restart at the third resting place: " + run.at());
                    check(f, cell != null && !level.getBlockState(cell.below()).isAir(), "the collapsed floor was not restored after the failed escape");
                }
            }
            check(f, failedOnce, "the endurance failure was never tried");
            check(f, escapeFailed, "the escape failure was never tried");
            check(f, run.state() == AbyssEscort.State.DONE, "the solo salt escort did not finish (" + steps + " steps, state " + run.state() + ", cp "
                    + run.secured() + ", at " + run.at() + ")");
            check(f, AbyssMarks.count(a, "salt") == 3 && d.has(a.getUUID(), "escort:salt"), "the solo player lacks marks or the escort record: "
                    + AbyssMarks.count(a, "salt"));
            Quests.poll(a, true);
            check(f, "salt_lord".equals(Layers.stage(a)), "the solo escort did not advance to salt_lord: " + Layers.stage(a));
            check(f, AbyssMarks.canPassGate(a, "salt"), "the solo player cannot pass the salt gate");
            BlockPos br = AbyssBuild.mark("bridge_salt_a");
            BlockPos ld = AbyssBuild.mark("ladder_salt_a");
            check(f, br != null && level.getBlockState(br).is(Blocks.SPRUCE_PLANKS), "the plank bridge over the salt crust is missing");
            check(f, ld != null && level.getBlockState(ld).is(Blocks.LADDER), "the well ladder is missing");
            // 格子の門が上がる
            BlockPos g = AbyssBuild.mark("gate_salt");
            BlockPos out = AbyssBuild.mark("gate_salt_out");
            AbyssMarks.setGate(level, g, false);
            place(a, Vec3.atBottomCenterOf(out));
            AbyssMarks.gateTick(level, now + 10, List.of(a));
            check(f, AbyssMarks.gateRaised(level, "salt"), "the salt bar gate did not rise for the escort");
            AbyssMarks.setGate(level, g, false);
            AbyssEscort.end(level, run, true);
            check(f, AbyssEscort.run("salt") == null && !AbyssEscort.busy("abyss_porter"), "ending the run did not free Roku");
        } finally {
            d.forget(a.getUUID());
            Quests.forgetAll(a);
        }
    }

    // ---------------------------------------------------------------- 二人のパーティ (灰)
    static void party(List<String> f, MinecraftServer server, ServerLevel level) {
        FakePlayer b = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "abyss_escort_b"));
        FakePlayer c = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "abyss_escort_c"));
        PartyData pd = PartyData.get(server);
        PartyData.Party pt = pd.create(b.getUUID(), "b");
        pd.add(pt, c.getUUID(), "c");
        LayerData d = LayerData.get(server);
        Map<UUID, ServerPlayer> online = Map.of(b.getUUID(), b, c.getUUID(), c);
        AbyssEscort.lookup = online::get;
        try {
            check(f, toStage(b, "ash_marks") && toStage(c, "ash_marks"), "could not put the pair on ash_marks: " + Layers.stage(b) + "/" + Layers.stage(c));
            BlockPos s = AbyssBuild.mark("escstart_ash");
            place(b, Vec3.atBottomCenterOf(s));
            place(c, Vec3.atBottomCenterOf(s).add(1, 0, 0));
            long now = level.getGameTime() + 200000;
            check(f, AbyssEscort.tryStart(level, "ash", now, List.of(b, c)), "standing at the ash mouth did not start the escort");
            AbyssEscort.Run run = AbyssEscort.run("ash");
            if (run == null) {
                return;
            }
            int steps = 0;
            for (; steps < MAX_STEPS && run.state() != AbyssEscort.State.DONE; steps++) {
                now++;
                Vec3 at = AbyssEscort.npcPos(run);
                place(b, at.add(0.6, 0, 0.6));
                place(c, at.add(-0.6, 0, -0.6));
                if (run.state() == AbyssEscort.State.WAIT) {
                    work(b, level, run, now);
                }
                AbyssEscort.tick(level, run, now, List.of(b, c));
            }
            check(f, run.state() == AbyssEscort.State.DONE, "the party ash escort did not finish (" + steps + " steps, state " + run.state() + ", cp "
                    + run.secured() + ", at " + run.at() + ")");
            check(f, run.participants().contains(c.getUUID()), "the second player did not join the escort");
            for (FakePlayer q : List.of(b, c)) {
                Quests.poll(q, true);
                check(f, AbyssMarks.count(q, "ash") == 3 && d.has(q.getUUID(), "escort:ash"), q.getGameProfile().getName() + " lacks ash marks or the record");
                check(f, "ash_lord".equals(Layers.stage(q)), q.getGameProfile().getName() + " did not advance to ash_lord: " + Layers.stage(q));
            }
            check(f, d.isOpen("seal_ash"), "the three-arts seal is not open after the escort");
        } finally {
            pd.disband(pt);
            d.forget(b.getUUID());
            d.forget(c.getUUID());
            Quests.forgetAll(b);
            Quests.forgetAll(c);
        }
    }

    // 待ち場の仕事を、人がするように (杭・灯りは右クリック、封は三つの術、守り・払いは敵を倒す)
    static void work(ServerPlayer p, ServerLevel level, AbyssEscort.Run run, long now) {
        switch (run.task()) {
            case STAKE -> AbyssEscort.use(p, AbyssBuild.mark("stake_salt"));
            case BEACON -> AbyssEscort.use(p, AbyssBuild.mark("beacon_" + run.layer));
            case SEAL -> {
                for (AbyssArt art : new AbyssArt[] {AbyssArt.LIGHT, AbyssArt.WEIGHT, AbyssArt.HEARTBEAT}) {
                    LayerPuzzles.onCast(p, art);
                }
                LayerPuzzles.seal(level, p.level().getGameTime());
            }
            case HOLD -> AbyssEscort.skipHold(run, now);
            default -> {
            }
        }
        for (UUID u : AbyssEscort.mobsForTest(run)) {
            Entity e = level.getEntity(u);
            if (e != null) {
                e.discard();
            }
        }
    }

    static void tickAt(ServerLevel level, AbyssEscort.Run run, long now, List<ServerPlayer> players) {
        AbyssEscort.tick(level, run, now, players);
    }

    static Vec3 waitPos(AbyssEscort.Run run, int k) {
        BlockPos w = AbyssBuild.mark("escwait_" + run.layer + "_" + k);
        return w == null ? AbyssEscort.npcPos(run) : Vec3.atBottomCenterOf(w);
    }

    static void place(ServerPlayer p, Vec3 at) {
        p.moveTo(at.x, at.y, at.z, p.getYRot(), 0f);
    }

    // メインの段をその段まで進める
    static boolean toStage(ServerPlayer p, String stage) {
        Quests.forgetAll(p);
        Quests.start(p, AbyssEntrance.QUEST);
        for (int i = 0; i < 40 && !stage.equals(Layers.stage(p)); i++) {
            Quests.forceStage(p, AbyssEntrance.QUEST);
        }
        return stage.equals(Layers.stage(p));
    }

    static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }
}
