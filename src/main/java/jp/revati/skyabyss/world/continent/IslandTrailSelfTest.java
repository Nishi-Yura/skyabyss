package jp.revati.skyabyss.world.continent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.util.FakePlayer;

// はじまりの島の道 (IslandTrail) と道の均し (ContinentRoads) の検査。SKYABYSS_SELFTEST_ONLY="island trail"。
//   道: 陸の上の道は 2 ブロックごとの高さの差が 1 以内 (段々・崖にならない)。島の足跡は道から離れている
//   島 (大陸の世界だけ): 目的の順 (足跡 → 野盗 → 休み場 → 祠 → 渡し守)、野盗を 3 人退けると峠の柵が開く、祠で渡し守の印、渡ると目的が消える
public final class IslandTrailSelfTest {
    private IslandTrailSelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        return List.of(SelfTestRunner.Step.once("island trail", IslandTrailSelfTest::run));
    }

    private static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        roads(f);
        ServerLevel level = server.overworld();
        boolean island = IslandTrail.active(level);
        if (island) {
            trail(f, level);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST island trail PASSED (road grade{})", island ? ", island order, bandits open the barricade, job choice at the shrine readies the ferryman, crossing" : "; no island here");
        } else {
            f.forEach(s -> SkyAbyss.LOGGER.error("SELFTEST island trail FAILED: {}", s));
        }
    }

    // 道の高さ: 陸の上で 2 ブロックごとの差が 1 以内。足跡は道から 45 以上離す
    static void roads(List<String> f) {
        ContinentMap map = ContinentMap.get();
        int bad = 0;
        String first = null;
        for (List<BlockPos> line : ContinentLayout.get().roads()) {
            for (int i = 0; i + 1 < line.size(); i++) {
                BlockPos a = line.get(i);
                BlockPos b = line.get(i + 1);
                double len = Math.hypot(b.getX() - a.getX(), b.getZ() - a.getZ());
                int prev = Integer.MIN_VALUE;
                for (double t = 0; t <= len; t += 2) {
                    int x = (int) Math.round(a.getX() + (b.getX() - a.getX()) * t / len);
                    int z = (int) Math.round(a.getZ() + (b.getZ() - a.getZ()) * t / len);
                    int y = map.surface(x, z);
                    if (y < ContinentChunkGenerator.SEA_LEVEL) {
                        prev = Integer.MIN_VALUE;
                        continue;
                    }
                    if (prev != Integer.MIN_VALUE && Math.abs(y - prev) > 1) {
                        bad++;
                        if (first == null) {
                            first = x + "," + z + " (" + prev + " -> " + y + ")";
                        }
                    }
                    prev = y;
                }
            }
        }
        if (bad > 3) {
            f.add("road has " + bad + " steps over 1 block (first at " + first + ")");
        }
        for (BlockPos p : ContinentLayout.get().islandPrints()) {
            double best = Double.MAX_VALUE;
            List<BlockPos> line = ContinentLayout.get().roads().get(0);
            for (BlockPos q : line) {
                best = Math.min(best, Math.hypot(p.getX() - q.getX(), p.getZ() - q.getZ()));
            }
            if (best < 45) {
                f.add("island footprint at " + p.toShortString() + " is only " + (int) best + " from the road");
            }
        }
    }

    static void trail(List<String> f, ServerLevel level) {
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "island_t"));
        BlockPos start = ContinentLayout.get().start();
        p.setPos(start.getX() + 0.5, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, start.getX(), start.getZ()), start.getZ() + 0.5);
        expect(f, p, "island_prints");
        BlockPos pr = ContinentLayout.get().islandPrints().get(0);
        p.setPos(pr.getX() + 0.5, p.getY(), pr.getZ() + 0.5);
        IslandTrail.update(level, p);
        expect(f, p, "island_bandits");
        if (IslandTrail.has(p, IslandTrail.BANDITS)) {
            f.add("bandits counted before any kill");
        }
        p.getPersistentData().putInt(IslandTrail.KILLS, IslandTrail.BANDITS_NEEDED);
        IslandTrail.update(level, p);
        expect(f, p, "island_rest");
        if (!IslandTrail.gateOpen(level)) {
            f.add("the barricade did not open after the bandits");
        }
        if (IslandTrail.gateColumns().length < 20) {
            f.add("the barricade is too short (" + IslandTrail.gateColumns().length + " columns)");
        }
        BlockPos r = IslandTrail.restPos(level, 0);
        p.setPos(r.getX() + 0.5, r.getY(), r.getZ() + 0.5);
        IslandTrail.update(level, p);
        expect(f, p, "island_shrine");
        if (jp.revati.skyabyss.npc.NpcBonds.of(p).hasFlag(IslandTrail.FERRYMAN_ROLE, IslandTrail.READY_FLAG)) {
            f.add("the ferryman is ready before the shrine");
        }
        BlockPos s = IslandTrail.restPos(level, 1);
        p.setPos(s.getX() + 0.5, s.getY(), s.getZ() + 0.5);
        // 祠に立つだけでは済まない: 職を選ぶと済む (始めの武器と、職の木の 1 行目の技)
        jp.revati.skyabyss.rpg.skill.JobChoice.reset(p);
        IslandTrail.update(level, p);
        expect(f, p, "island_shrine");
        jp.revati.skyabyss.rpg.skill.Job job = jp.revati.skyabyss.rpg.skill.Job.SHAMAN;
        if (!jp.revati.skyabyss.rpg.skill.JobChoice.choose(p, job) || jp.revati.skyabyss.rpg.skill.JobChoice.choose(p, jp.revati.skyabyss.rpg.skill.Job.WARRIOR)) {
            f.add("choosing a job at the shrine did not work once (and only once)");
        }
        if (!p.getInventory().contains(new net.minecraft.world.item.ItemStack(jp.revati.skyabyss.rpg.skill.JobChoice.BELL_STAFF.get()))) {
            f.add("the shaman did not get the bell staff");
        }
        jp.revati.skyabyss.rpg.skill.Skill first = jp.revati.skyabyss.rpg.skill.JobChoice.firstSkill(job);
        if (first == null || !jp.revati.skyabyss.rpg.skill.SkillManager.get(p).knows(first)) {
            f.add("the first job skill was not learned: " + first);
        }
        IslandTrail.update(level, p);
        expect(f, p, "take_ferry");
        if (!jp.revati.skyabyss.npc.NpcBonds.of(p).hasFlag(IslandTrail.FERRYMAN_ROLE, IslandTrail.READY_FLAG)) {
            f.add("the shrine did not ready the ferryman");
        }
        ContinentOutskirts.ferry(p);
        if (IslandTrail.objective(p) != null) {
            f.add("an island objective remains after the crossing");
        }
        if (jp.revati.skyabyss.town.RegionFolk.byId(IslandTrail.FERRYMAN) == null) {
            f.add("the island ferryman is not a region folk");
        }
        p.discard();
    }

    static void expect(List<String> f, FakePlayer p, String id) {
        Object[] o = IslandTrail.objective(p);
        String got = o == null ? "null" : (String) o[0];
        if (!id.equals(got)) {
            f.add("expected objective " + id + " but got " + got + " (done " + IslandTrail.done(p) + ")");
        }
    }
}
