package jp.revati.skyabyss.abyss.arts;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.KeyDefaults;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.skill.SkillData;
import jp.revati.skyabyss.rpg.skill.SkillManager;
import jp.revati.skyabyss.rpg.skill.SkillTree;
import jp.revati.skyabyss.story.ch5.AbyssBuild;
import jp.revati.skyabyss.story.ch5.AbyssPressure;
import jp.revati.skyabyss.story.ch5.AbyssWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.util.FakePlayer;

// 深淵術 (docs/design/36 の 5 節)の自己テスト。SKYABYSS_SELFTEST_ONLY="abyss arts"。
//   鍵と溜まりの印の決まり / 灯りの明るさ・上限・消え方 / 覚える・木を開く・上げる（点） / 使う（マナ・待ち・効き）/ 圧への接ぎ
public final class AbyssArtsSelfTest {
    private static final List<String> F = new ArrayList<>();

    private AbyssArtsSelfTest() {
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            F.add(what);
        }
    }

    public static void runSelfTest(MinecraftServer server) {
        F.clear();
        try {
            keysAndPools();
            lamps(server);
            playerFlow(server);
            check(AbyssPressure.weightRelief != null && AbyssPressure.lightBonus != null, "pressure hooks are not set");
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST abyss arts crashed", e);
            F.add("crashed: " + e);
        }
        if (F.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST abyss arts PASSED (keys, pools, light, learn, tree, rank, cast, cooldown, mana, pressure hooks)");
        } else {
            SkyAbyss.LOGGER.error("SELFTEST abyss arts FAILED");
            F.forEach(x -> SkyAbyss.LOGGER.error("SELFTEST abyss arts FAILED: {}", x));
        }
    }

    // 鍵は他の割り当てと重ならない・溜まりの印は四つとも建て図にある・術の溜まりは一対一
    private static void keysAndPools() {
        Set<Integer> keys = new HashSet<>(KeyDefaults.all().values());
        check(keys.size() == KeyDefaults.all().size(), "a key default is used twice");
        Set<Integer> seen = new HashSet<>();
        for (AbyssArt a : AbyssArt.values()) {
            check(seen.add(a.key), "art key " + a.id + " repeats");
            check(!KeyDefaults.VANILLA.containsKey(a.key), "art key " + a.id + " is a vanilla key");
            check(AbyssArt.byPool(a.pool) == a, "pool " + a.pool + " does not map back to " + a.id);
            check(AbyssBuild.mark(a.pool) != null, "pool mark " + a.pool + " is missing in the layers");
        }
        check(AbyssBuild.mark("pool_ash") != null, "pool_ash mark is missing in the layers");
        check(AbyssArt.byPool("pool_ash") == null, "pool_ash should teach no art");
    }

    // 灯り: 中心 15・半径の端 0・間は直線・人ごとに三つまで・期限で消える
    private static void lamps(MinecraftServer server) {
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        check(level != null, "the abyss dimension is missing");
        if (level == null) {
            return;
        }
        AbyssLight.clear();
        UUID owner = UUID.randomUUID();
        BlockPos at = new BlockPos(0, 100, 0);
        AbyssLight.add(level, at, 8, 1000, owner);
        check(AbyssLight.lampAt(level, at) == 15, "the lamp centre is not 15");
        check(AbyssLight.lampAt(level, at.offset(4, 0, 0)) == 8, "the lamp at 4 blocks is not 8 (" + AbyssLight.lampAt(level, at.offset(4, 0, 0)) + ")");
        check(AbyssLight.lampAt(level, at.offset(8, 0, 0)) == 0, "the lamp edge is not 0");
        check(AbyssLight.lightAt(level, at) >= 15, "lightAt does not take the lamp");
        for (int i = 0; i < 4; i++) {
            AbyssLight.add(level, at.offset(20 + i, 0, 0), 4, 1000, owner);
        }
        check(AbyssLight.count() == AbyssLight.MAX_PER_PLAYER, "one player kept " + AbyssLight.count() + " lamps (expected " + AbyssLight.MAX_PER_PLAYER + ")");
        check(AbyssLight.lampAt(level, at) == 0, "the oldest lamp did not go out first");
        AbyssLight.tick(1000);
        check(AbyssLight.count() == 0, "lamps did not go out at their time");
        AbyssLight.clear();
    }

    // 人の流れ: 覚える・木を開く（前は使えない）・上げる（点）・使う（マナ・待ち）・重さの緩み
    private static void playerFlow(MinecraftServer server) {
        ServerLevel level = server.overworld();
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "arts_test"));
        p.moveTo(level.getSharedSpawnPos().getX() + 0.5, 300, level.getSharedSpawnPos().getZ() + 0.5, 0f, 0f);
        Growth.get(p).level = 50;
        p.setOnGround(true);
        p.getAttribute(jp.revati.skyabyss.rpg.Rpg.MAX_MANA).setBaseValue(200);
        ArtData d = AbyssArts.data(p);
        check(!d.has(AbyssArt.LIGHT), "a new player already knows a light art");

        check(AbyssArts.learn(p, AbyssArt.LIGHT) && d.rank(AbyssArt.LIGHT) == 1, "learning the light art failed");
        check(!AbyssArts.learn(p, AbyssArt.LIGHT), "the light art was learned twice");

        SkillData sd = SkillManager.get(p);
        check(AbyssArts.up(p, AbyssArt.LIGHT) == AbyssArts.Result.NO_TREE, "ranked up before the tree was open");
        check(AbyssArts.openTree(p) && sd.unlockedTrees.contains(AbyssArts.TREE_ID) && sd.treeOpen(SkillTree.ABYSS), "the arts tree did not open");
        check(!AbyssArts.openTree(p), "the tree opened twice");

        sd.bonusPoints += 10;
        check(AbyssArts.up(p, AbyssArt.LIGHT) == AbyssArts.Result.RANKED && d.rank(AbyssArt.LIGHT) == 2, "rank 1 to 2 failed");
        check(sd.artPoints == 1, "rank 2 should cost 1 point (spent " + sd.artPoints + ")");
        check(AbyssArts.up(p, AbyssArt.LIGHT) == AbyssArts.Result.RANKED && d.rank(AbyssArt.LIGHT) == 3, "rank 2 to 3 failed");
        check(sd.artPoints == 3, "rank 3 should cost 2 more points (spent " + sd.artPoints + ")");
        check(AbyssArts.up(p, AbyssArt.LIGHT) == AbyssArts.Result.MAX_RANK, "ranked past 3");
        sd.bonusPoints -= 10;

        SkillManager.settleMana(p);
        sd.mana = 100;
        check(AbyssArts.cast(p, AbyssArt.WEIGHT) == AbyssArts.Result.NOT_LEARNED, "an unlearned art was cast");
        check(AbyssArts.learn(p, AbyssArt.WEIGHT), "learning the weight art failed");
        check(AbyssArts.cast(p, AbyssArt.WEIGHT) == AbyssArts.Result.OK, "the weight art did not cast");
        check(Math.abs(sd.mana - (100 - AbyssArt.WEIGHT.mana)) < 0.01, "the weight art cost " + (100 - sd.mana) + " mana");
        check(AbyssArts.weightRelief(p) == AbyssArt.WEIGHT_RELIEF[0], "relief after casting the weight art is " + AbyssArts.weightRelief(p));
        check(AbyssArts.cast(p, AbyssArt.WEIGHT) == AbyssArts.Result.COOLDOWN, "the weight art came back before its wait");
        check(AbyssArts.cooldownLeft(p, AbyssArt.WEIGHT, level.getGameTime()) > 0, "no wait was set after the weight art");

        sd.mana = 1;
        check(AbyssArts.learn(p, AbyssArt.HEARTBEAT), "learning the heartbeat art failed");
        check(AbyssArts.cast(p, AbyssArt.HEARTBEAT) == AbyssArts.Result.NO_MANA, "heartbeat cast without mana");

        check(AbyssPressure.weightRelief.applyAsDouble(p) == AbyssArts.weightRelief(p), "the pressure hook does not read the relief");
        check(AbyssPressure.lightBonus.applyAsInt(p) == AbyssLight.lampAt(p.level(), p.blockPosition()), "the pressure hook does not read the lamp light");

        // 後始末 (ほかの自己テストに残さない)
        SkillManager.forgetAll(p);
        sd.unlockedTrees.remove(AbyssArts.TREE_ID);
        sd.artPoints = 0;
        sd.bonusPoints = 0;
        for (int i = 0; i < d.ranks.length; i++) {
            d.ranks[i] = 0;
        }
        AbyssLight.clear();
    }
}
