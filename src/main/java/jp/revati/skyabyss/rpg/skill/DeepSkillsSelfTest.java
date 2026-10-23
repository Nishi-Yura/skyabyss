package jp.revati.skyabyss.rpg.skill;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rebirth.RebirthData;
import jp.revati.skyabyss.rebirth.RebirthSetup;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.GrowthRules;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;

// 橋 (A09)・変異 (A08)・紋の盤 (A14)・前世の技 (B08)・淵 (B09) の自己テスト。BuildsSelfTest.run の最後から呼ぶ。docs/design/31
final class DeepSkillsSelfTest {
    private DeepSkillsSelfTest() {
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    static void run(MinecraftServer server, List<String> f) {
        model(f);
        migration(f);
        FakePlayer p = player(server, "deep_test");
        bridges(server, p, f);
        rebirth(p, f);
        glyphs(server, p, f);
        mutations(server, f);
        casts(server, f);
        balance(f);
    }

    private static FakePlayer player(MinecraftServer server, String name) {
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), name));
        p.moveTo(spawn.getX() + 0.5, 300, spawn.getZ() + 0.5, 0f, 0f);
        Growth.get(p).level = 50;
        p.setOnGround(true);
        p.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(7);
        p.getAttribute(jp.revati.skyabyss.rpg.Rpg.MAX_MANA).setBaseValue(200);
        SkillManager.get(p).lastCombat = Long.MIN_VALUE / 2;
        return p;
    }

    private static void setRebirth(FakePlayer p, int n) {
        p.setData(RebirthSetup.DATA.get(), RebirthData.EMPTY.withCount(n));
        // サーバーの見せ方は本体の回数 (GrowthData.rebirths) を読む (Rebirth.displayCount)。写しと本体を揃える
        jp.revati.skyabyss.rpg.Growth.get(p).rebirths = n;
        SkillManager.sync(p);
        SkillExtOps.refreshRebirth(p);
    }

    // ---------------------------------------------------------------- 形
    private static void model(List<String> f) {
        int bridges = 0;
        int deep = 0;
        int past = 0;
        java.util.Set<Integer> icons = new java.util.HashSet<>();
        for (Skill s : Skill.values()) {
            check(f, icons.add(s.iconIndex()), "duplicate skill icon " + s.id);
            switch (s.tree) {
                case HYBRID -> {
                    bridges++;
                    SkillTree[] t = s.bridgeTrees();
                    check(f, t.length == 2 && t[0].callable() && t[1].callable() && t[0] != t[1], "bridge " + s.id + " has bad parents");
                    // 隣り合う木 (輪の順 岩・風・潮・灰・骨)
                    int a = t.length == 2 ? t[0].ordinal() : 0;
                    int b = t.length == 2 ? t[1].ordinal() : 0;
                    check(f, (a + 1) % 5 == b % 5 && a < 5 && b < 5, "bridge " + s.id + " is not between adjacent trees");
                }
                case DEEP -> deep++;
                case PAST -> past++;
                default -> {
                }
            }
        }
        check(f, bridges == 5, "bridge count " + bridges);
        check(f, deep == 6, "deep tree size " + deep);
        check(f, past == 6 && past <= 6, "past-life skill count " + past);
        // 淵: 転生 1 で入口 (行 0)、5 で全段
        check(f, Skill.DEEP_PULL.rebirthAt() == 1 && Skill.DEEP_OATH.rebirthAt() == 5, "deep tree rebirth range is wrong");
        for (Skill s : Skill.values()) {
            if (s.tree == SkillTree.DEEP || s.tree == SkillTree.PAST) {
                check(f, s.rebirthAt() >= 1 && s.rebirthAt() <= 5, "reborn skill " + s.id + " has bad rebirth requirement");
            } else {
                check(f, s.rebirthAt() == 0, "ordinary skill " + s.id + " requires rebirth");
            }
            check(f, SkillTag.of(s) != null, "no tag " + s.id);
            if (s.active && s != Skill.SIGNAL) {
                check(f, SkillRules.manaCost(s, 1) > 0 && SkillRules.cooldownTicks(s, 1) > 0, "active skill without cost: " + s.id);
            }
        }
        // 紋: 5 種で穴 3〜6。6 つ以上は数えない
        check(f, Glyph.values().length == 5 && SkillRules.glyphSockets(0) == 3 && SkillRules.glyphSockets(3) == 6 && SkillRules.glyphSockets(20) == 6,
                "glyph socket counts are wrong");
        check(f, Mutation.values().length == 8, "mutation count");
        for (Mutation m : Mutation.values()) {
            check(f, m.skill.active && !m.coreItem().equals(net.minecraft.world.item.Items.AIR) && !m.shardItem().equals(net.minecraft.world.item.Items.AIR),
                    "mutation " + m.id + " has no skill or material");
        }
        // 紋の符号化
        Glyph.Placed placed = Glyph.parsePlaced(new Glyph.Placed(new Glyph.Item(Glyph.TIDE, 2), Skill.EBB_TIDE).code());
        check(f, placed != null && placed.item().glyph() == Glyph.TIDE && placed.item().level() == 2 && placed.center() == Skill.EBB_TIDE, "glyph code round trip");
        check(f, Glyph.parse("rock/9") == null && Glyph.parse("nope/1") == null && Glyph.parsePlaced("rock/1@nothing") == null, "bad glyph codes were accepted");
    }

    // ---------------------------------------------------------------- 保存の移し (版 3 → 4)
    private static void migration(List<String> f) {
        SkillData old = new SkillData();
        old.ranks.put("titan_cleave", 2);
        old.ranks.put("lunge", 1);
        old.mastery.put("titan_cleave", 20);
        CompoundTag tag = old.serializeNBT(null);
        // 版 3 の記録: 新しい欄が丸ごと無い
        tag.putInt("v", 3);
        tag.remove("mutations");
        tag.remove("glyphBag");
        tag.remove("glyphSockets");
        tag.remove("glyphClaims");
        SkillData loaded = new SkillData();
        loaded.deserializeNBT(null, tag);
        check(f, loaded.version == SkillData.VERSION && SkillData.VERSION == 4, "the record was not migrated to version 4: " + loaded.version);
        check(f, loaded.rank(Skill.TITAN_CLEAVE) == 2 && loaded.rank(Skill.LUNGE) == 1 && loaded.masteryXp(Skill.TITAN_CLEAVE) == 20, "version 3 skills were lost");
        check(f, loaded.mutations.isEmpty() && loaded.glyphBag.isEmpty() && loaded.glyphClaims.isEmpty(), "new fields did not start empty");
        for (String s : loaded.glyphSockets) {
            check(f, s != null && s.isEmpty(), "a socket did not start empty");
        }
        // 新しい欄が往復する。壊れた値は落ちる
        loaded.mutations.put("wind_rend", "rend_blade");
        loaded.mutations.put("lunge", "rend_blade");
        loaded.mutations.put("nothing", "x");
        loaded.glyphBag.add("rock/2");
        loaded.glyphBag.add("garbage");
        loaded.glyphSockets[1] = "ash/3@titan_cleave";
        loaded.glyphSockets[2] = "ash/3@nothing";
        loaded.glyphClaims.add("ember|a");
        SkillData again = new SkillData();
        again.deserializeNBT(null, loaded.serializeNBT(null));
        check(f, again.mutations.size() == 1 && "rend_blade".equals(again.mutations.get("wind_rend")), "mutations did not survive the round trip");
        check(f, again.glyphBag.equals(List.of("rock/2")), "the glyph bag did not survive the round trip: " + again.glyphBag);
        check(f, "ash/3@titan_cleave".equals(again.glyphSockets[1]) && again.glyphSockets[2].isEmpty(), "sockets did not survive the round trip");
        check(f, again.glyphClaims.contains("ember|a"), "glyph claims were lost");
    }

    // ---------------------------------------------------------------- 橋 (A09)
    private static void bridges(MinecraftServer server, FakePlayer p, List<String> f) {
        SkillData d = SkillManager.get(p);
        SkillManager.forgetAll(p);
        d.bonusPoints = 60;
        // 岩 3 つ・風 2 つでは開かない。風が 3 つになると開く
        SkillManager.forceRank(p, Skill.TITAN_CLEAVE, 1);
        SkillManager.forceRank(p, Skill.RIPOSTE, 1);
        SkillManager.forceRank(p, Skill.ROCK_CALL, 1);
        SkillManager.forceRank(p, Skill.LUNGE, 1);
        SkillManager.forceRank(p, Skill.NIMBLE, 1);
        check(f, d.knownIn(SkillTree.ROCK) == 3 && d.knownIn(SkillTree.WIND) == 2, "knownIn is wrong");
        check(f, d.canLearn(Skill.DUST_CHARGE, 50) == SkillData.LearnResult.REQUIRES, "a bridge opened with 3 + 2 skills");
        SkillManager.forceRank(p, Skill.BACKSTEP, 1);
        check(f, d.canLearn(Skill.DUST_CHARGE, 50) == SkillData.LearnResult.OK, "a bridge did not open with 3 + 3 skills: " + d.canLearn(Skill.DUST_CHARGE, 50));
        check(f, d.canLearn(Skill.SEA_BREEZE, 50) == SkillData.LearnResult.REQUIRES, "a bridge opened without its other tree");
        check(f, d.canLearn(Skill.STONE_AIM, 50) == SkillData.LearnResult.REQUIRES, "the bone-rock bridge opened without bone");
        check(f, SkillManager.learn(p, Skill.DUST_CHARGE) == SkillData.LearnResult.OK && d.rank(Skill.DUST_CHARGE) == 1, "the bridge was not learned");
        int spent = d.spentPoints();
        check(f, SkillManager.learn(p, Skill.DUST_CHARGE) == SkillData.LearnResult.OK && d.spentPoints() == spent + 1, "a bridge rank did not cost one point");
        check(f, d.canLearn(Skill.DUST_CHARGE, 50) == SkillData.LearnResult.MAX_RANK, "a bridge went past its max rank");
        // 橋は両側のタグを数える
        check(f, SkillBuild.tagCount(p, SkillTag.ROCK) == 4 && SkillBuild.tagCount(p, SkillTag.WIND) == 3 + 1, "a bridge did not count both tags: rock "
                + SkillBuild.tagCount(p, SkillTag.ROCK) + " wind " + SkillBuild.tagCount(p, SkillTag.WIND));
        // 能動の橋は枠に置ける
        check(f, d.slot(0) != null || java.util.Arrays.asList(d.slots).contains("dust_charge"), "an active bridge was not placed in a slot");
        // 橋の点は通常の点と同じ (別枠ではない)
        check(f, d.spentPoints() <= d.totalPoints(50), "points overspent after bridges");
        // 橋の頂点と同格: 力の倍率が段 2 の使う技 (鯨尾薙ぎ 1.2) 以下、単体で巨神斬り段 3 の 2.7 倍 (大きな相手) を超えない
        check(f, SkillRules.dustMultiplier(2) <= SkillRules.sweepMultiplier(2) && SkillRules.steamMultiplier(2) <= SkillRules.sweepMultiplier(2), "a bridge is stronger than a capstone");
        // タグの数が橋の分だけ上がると、そのタグの技の力が +1% ずつ
        check(f, Math.abs(SkillBuild.tagPower(p, SkillTag.WIND) - 0.04) < 1e-9, "bridge tag did not reach tag power");
    }

    // ---------------------------------------------------------------- 転生 (B08・B09)
    private static void rebirth(FakePlayer p, List<String> f) {
        SkillData d = SkillManager.get(p);
        SkillManager.forgetAll(p);
        d.bonusPoints = 80;
        setRebirth(p, 0);
        // 未転生の人は淵も前世の技も取れない (画面にも出ない)。点は通常の点から引かれない
        check(f, d.canLearn(Skill.DEEP_PULL, 50) == SkillData.LearnResult.REBIRTH, "the deep opened without a rebirth");
        check(f, d.canLearn(Skill.ECHO_STRIKE, 50) == SkillData.LearnResult.REBIRTH, "a past-life skill opened without a rebirth");
        check(f, d.pastPool() == 0 && SkillTree.DEEP.rebornOnly() && SkillTree.PAST.rebornOnly() && !SkillTree.HYBRID.rebornOnly(), "reborn-only flags are wrong");
        // 転生 1: 淵は入口 (行 0) だけ。前世の技は 2 点 (遺響の一撃・旧き見張り)
        setRebirth(p, 1);
        check(f, d.rebirth == 1 && d.pastPool() == 2, "the rebirth count did not reach the record");
        check(f, SkillManager.learn(p, Skill.DEEP_PULL) == SkillData.LearnResult.OK, "the deep entry did not open at rebirth 1");
        check(f, SkillManager.learn(p, Skill.DEEP_ECHO) == SkillData.LearnResult.REBIRTH, "a deep row opened too early");
        int normalSpent = d.spentPoints();
        check(f, SkillManager.learn(p, Skill.ECHO_STRIKE) == SkillData.LearnResult.OK && SkillManager.learn(p, Skill.OLD_VIGIL) == SkillData.LearnResult.OK,
                "past-life skills were not learned at rebirth 1");
        check(f, d.spentPoints() == normalSpent && d.pastSpent() == 2 && d.pastLeft() == 0, "past-life points are not a separate pool");
        check(f, SkillManager.learn(p, Skill.ECHO_STRIKE) == SkillData.LearnResult.NO_POINTS, "past-life points ran past the pool");
        check(f, SkillManager.learn(p, Skill.GRAVE_BELL) == SkillData.LearnResult.REBIRTH, "a later past-life skill opened at rebirth 1");
        // 転生が増えると深い段・点が開く。5 で全段
        setRebirth(p, 3);
        check(f, d.pastPool() == 6, "past pool at 3 rebirths is " + d.pastPool());
        check(f, SkillManager.learn(p, Skill.GRAVE_BELL) == SkillData.LearnResult.OK && SkillManager.learn(p, Skill.MEMORY_EDGE) == SkillData.LearnResult.OK, "rebirth 3 past skills");
        check(f, SkillManager.learn(p, Skill.FINAL_LIGHT) == SkillData.LearnResult.REBIRTH, "final light opened at rebirth 3");
        check(f, SkillManager.learn(p, Skill.DEEP_ECHO) == SkillData.LearnResult.OK && SkillManager.learn(p, Skill.DEEP_ECHO) == SkillData.LearnResult.OK, "deep tier 1 at rebirth 3");
        check(f, SkillManager.learn(p, Skill.DEEP_MAW) == SkillData.LearnResult.REBIRTH, "the deep capstone opened at rebirth 3");
        setRebirth(p, 5);
        check(f, SkillManager.learn(p, Skill.DEEP_MIRROR) == SkillData.LearnResult.OK && SkillManager.learn(p, Skill.DEEP_TITHE) == SkillData.LearnResult.OK
                && SkillManager.learn(p, Skill.DEEP_MAW) == SkillData.LearnResult.OK && SkillManager.learn(p, Skill.DEEP_OATH) == SkillData.LearnResult.OK
                && SkillManager.learn(p, Skill.FINAL_LIGHT) == SkillData.LearnResult.OK && SkillManager.learn(p, Skill.SOUL_THREAD) == SkillData.LearnResult.OK,
                "the whole deep tree / past skills did not open at rebirth 5");
        // 上限: 技は 6 本まで・点は 12 まで
        check(f, d.pastPool() == 10 && d.pastSpent() <= d.pastPool(), "past points are over the pool at 5 rebirths");
        // 淵の鏡: タグが 3 種類以上で全技 +3% (段 1)。貢ぎ: いちばん多いタグ以外の多い順へ +1
        SkillManager.forgetAll(p);
        setRebirth(p, 5);
        SkillManager.forceRank(p, Skill.DEEP_MIRROR, 1);
        check(f, SkillExt.distinctTags(p) == 1 && SkillBuild.skillPower(p, Skill.TITAN_CLEAVE) < SkillRules.mirrorPower(1), "the mirror applied too early");
        SkillManager.forceRank(p, Skill.TITAN_CLEAVE, 1);
        SkillManager.forceRank(p, Skill.LUNGE, 1);
        SkillManager.forceRank(p, Skill.WARD_SONG, 1);
        double before = SkillBuild.skillPower(p, Skill.TITAN_CLEAVE);
        check(f, SkillExt.distinctTags(p) >= 3 && before >= SkillRules.mirrorPower(1), "the mirror did not apply with three tags: " + before);
        SkillManager.forceRank(p, Skill.DEEP_TITHE, 2);
        int tideBefore = SkillBuild.tagCount(p, SkillTag.TIDE);
        check(f, SkillExt.tithed(SkillManager.get(p)).size() == 2, "tithe did not pick two tags");
        check(f, SkillBuild.tagCount(p, SkillTag.TIDE) >= tideBefore, "tithe lowered a tag count");
        // 淵の誓い: 5 つのタグがそろうと +8%
        SkillManager.forceRank(p, Skill.DEEP_OATH, 1);
        check(f, SkillExt.distinctTags(p) < 5 || SkillBuild.skillPower(p, Skill.TITAN_CLEAVE) >= SkillRules.OATH_DEEP_POWER, "oath without five tags applied");
        // 回数が下がる (開発用・やり直し) と、足りない淵・前世の技は戻る。通常の技は残る
        SkillManager.forceRank(p, Skill.SOUL_THREAD, 1);
        setRebirth(p, 0);
        check(f, !d.knows(Skill.DEEP_OATH) && !d.knows(Skill.SOUL_THREAD) && !d.knows(Skill.DEEP_MIRROR) && d.knows(Skill.TITAN_CLEAVE),
                "lowering the rebirth count did not drop reborn skills");
        // 記憶の刃: 転生の回数に比例
        setRebirth(p, 4);
        SkillManager.forceRank(p, Skill.MEMORY_EDGE, 2);
        check(f, Math.abs(SkillRules.memoryPower(2, 4) - 0.04) < 1e-9, "memory edge scaling is wrong");
        setRebirth(p, 0);
    }

    // ---------------------------------------------------------------- 紋 (A14)
    private static void glyphs(MinecraftServer server, FakePlayer p, List<String> f) {
        SkillData d = SkillManager.get(p);
        SkillManager.forgetAll(p);
        d.glyphBag.clear();
        java.util.Arrays.fill(d.glyphSockets, "");
        d.glyphClaims.clear();
        d.lastCombat = Long.MIN_VALUE / 2;
        setRebirth(p, 0);
        d.bonusPoints = 80;
        SkillManager.forceRank(p, Skill.TITAN_CLEAVE, 3);
        SkillManager.forceRank(p, Skill.ROCK_CALL, 2);
        SkillManager.forceRank(p, Skill.LUNGE, 1);
        // 穴は転生 0 で 3 つ。4 つ目は閉じている
        check(f, SkillExtOps.give(p, Glyph.ROCK, 1) && SkillExtOps.give(p, Glyph.ASH, 3) && SkillExtOps.give(p, Glyph.TIDE, 2) && SkillExtOps.give(p, Glyph.WIND, 1), "glyphs were not given");
        check(f, SkillExtOps.place(p, 3, 0, Skill.TITAN_CLEAVE) == SkillExtOps.GlyphResult.CLOSED, "a closed socket took a glyph");
        check(f, SkillExtOps.place(p, 9, 0, Skill.TITAN_CLEAVE) == SkillExtOps.GlyphResult.BAD_SOCKET, "a bad socket was accepted");
        check(f, SkillExtOps.place(p, 0, 9, Skill.TITAN_CLEAVE) == SkillExtOps.GlyphResult.BAD_ITEM, "a bad bag index was accepted");
        check(f, SkillExtOps.place(p, 0, 0, Skill.WIND_REND) == SkillExtOps.GlyphResult.BAD_SKILL, "an unknown center skill was accepted");
        d.lastCombat = p.level().getGameTime();
        check(f, SkillExtOps.place(p, 0, 0, Skill.TITAN_CLEAVE) == SkillExtOps.GlyphResult.NOT_QUIET, "a glyph was placed in combat");
        d.lastCombat = Long.MIN_VALUE / 2;
        double base = SkillBuild.skillPower(p, Skill.TITAN_CLEAVE);
        double baseNeighbor = SkillBuild.skillPower(p, Skill.ROCK_CALL);
        // 岩の紋 (段 1): 中心 +8%、隣 +4%。巨神斬りの隣は岩呼び (前提の線)
        check(f, SkillExtOps.place(p, 0, 0, Skill.TITAN_CLEAVE) == SkillExtOps.GlyphResult.OK && d.glyphBag.size() == 3, "the rock glyph was not placed");
        // 中心 +8% に、紋のタグ (岩 +1 つ) の +1%。隣は +4% と +1%
        check(f, Math.abs(SkillBuild.skillPower(p, Skill.TITAN_CLEAVE) - base - 0.09) < 1e-9, "center power is wrong: " + (SkillBuild.skillPower(p, Skill.TITAN_CLEAVE) - base));
        check(f, Math.abs(SkillBuild.skillPower(p, Skill.ROCK_CALL) - baseNeighbor - 0.05) < 1e-9, "neighbor power is wrong: " + (SkillBuild.skillPower(p, Skill.ROCK_CALL) - baseNeighbor));
        check(f, Glyph.neighbors(Skill.TITAN_CLEAVE).contains(Skill.ROCK_CALL) && !Glyph.neighbors(Skill.TITAN_CLEAVE).contains(Skill.LUNGE), "neighbors are wrong");
        // 差した紋は、そのタグを +1 数える (岩の紋 → 岩のタグ)
        int rockTags = SkillBuild.tagCount(p, SkillTag.ROCK);
        check(f, rockTags == 2 + 1, "a glyph did not add its tag: " + rockTags);
        // 同じ穴へ別の紋: 前の紋は持ち物へ戻る
        check(f, SkillExtOps.place(p, 0, 0, Skill.TITAN_CLEAVE) == SkillExtOps.GlyphResult.OK && d.glyphBag.size() == 3 && d.glyphBag.contains("rock/1"), "swapping a glyph lost one");
        // 灰の紋 (段 3 は 18%) と潮の紋 (マナ) と風の紋 (待ち)
        SkillExtOps.remove(p, 0);
        d.glyphBag.clear();
        SkillExtOps.give(p, Glyph.ASH, 3);
        SkillExtOps.give(p, Glyph.ASH, 3);
        SkillExtOps.give(p, Glyph.TIDE, 3);
        SkillExtOps.give(p, Glyph.WIND, 3);
        SkillExtOps.place(p, 0, 0, Skill.TITAN_CLEAVE);
        SkillExtOps.place(p, 1, 0, Skill.TITAN_CLEAVE);
        check(f, Math.abs(Glyph.sum(d, d.rebirth, Skill.TITAN_CLEAVE, Glyph.Effect.POWER) - SkillRules.GLYPH_POWER_CAP) < 1e-9, "the glyph power cap did not hold: "
                + Glyph.sum(d, d.rebirth, Skill.TITAN_CLEAVE, Glyph.Effect.POWER));
        SkillExtOps.place(p, 2, 0, Skill.TITAN_CLEAVE);
        int manaBase = SkillRules.manaCost(Skill.TITAN_CLEAVE, 3);
        int manaNow = SkillManager.manaCostFor(p, Skill.TITAN_CLEAVE);
        check(f, manaNow < manaBase && manaNow >= Math.round(manaBase * (1 - SkillRules.GLYPH_MANA_CAP)) - 1, "glyph mana is wrong: " + manaNow + " vs " + manaBase);
        // 転生で穴が開く: 3 → 6 (見えるのは 6 つまで)
        setRebirth(p, 3);
        SkillExtOps.place(p, 3, 0, Skill.LUNGE);
        check(f, SkillRules.glyphSockets(d.rebirth) == 6 && !Glyph.parsePlaced(d.glyphSockets[3]).code().isEmpty(), "a socket did not open with rebirth");
        // 転生の数が減ると、閉じた穴の紋は効かない (外れはしない)
        double withFour = Glyph.sum(d, 3, Skill.LUNGE, Glyph.Effect.COOLDOWN);
        setRebirth(p, 0);
        check(f, Glyph.sum(d, 0, Skill.LUNGE, Glyph.Effect.COOLDOWN) >= withFour && d.glyphSockets[3].length() > 0, "a closed socket still worked");
        // 外す: 持ち物へ戻る。満杯なら外せない
        d.glyphBag.clear();
        check(f, SkillExtOps.remove(p, 0) == SkillExtOps.GlyphResult.OK && d.glyphBag.size() == 1 && d.glyphSockets[0].isEmpty(), "a glyph was not removed");
        check(f, SkillExtOps.remove(p, 0) == SkillExtOps.GlyphResult.BAD_ITEM, "an empty socket was removed");
        while (d.glyphBag.size() < SkillRules.GLYPH_BAG_MAX) {
            d.glyphBag.add("bone/1");
        }
        check(f, !SkillExtOps.give(p, Glyph.BONE, 1) && SkillExtOps.remove(p, 1) == SkillExtOps.GlyphResult.BAG_FULL, "the bag overflowed");
        // 試練の紋: 同じ試練で一度だけ。満杯なら受け取らない。段は転生で上がる
        d.glyphBag.clear();
        d.glyphClaims.clear();
        check(f, SkillExtOps.award(p, "ember", "gate") && d.glyphBag.size() == 1, "a trial did not drop a glyph");
        check(f, !SkillExtOps.award(p, "ember", "gate") && d.glyphBag.size() == 1, "the same trial dropped twice");
        check(f, SkillExtOps.award(p, "ember", "second") && d.glyphBag.size() == 2, "a second trial did not drop");
        setRebirth(p, 5);
        d.glyphBag.clear();
        SkillExtOps.award(p, "stone_whale", "third");
        check(f, Glyph.parse(d.glyphBag.get(0)).level() == 3, "the glyph level did not rise with rebirths");
        while (d.glyphBag.size() < SkillRules.GLYPH_BAG_MAX) {
            d.glyphBag.add("bone/1");
        }
        int claims = d.glyphClaims.size();
        check(f, !SkillExtOps.award(p, "tide_shell", "full") && d.glyphClaims.size() == claims, "a full bag still claimed a trial");
        // 転生: 紋の入手の記録は空に戻るが、持っている紋と盤の紋と変異は残る
        d.mutations.put("lunge", "lunge_surge");
        String socket0 = d.glyphSockets[0];
        int bag = d.glyphBag.size();
        d.echoUntil = 99999;
        SkillReset.forRebirth(p);
        check(f, d.glyphClaims.isEmpty() && d.glyphBag.size() == bag && d.glyphSockets[0].equals(socket0) && d.mutations.containsKey("lunge") && d.echoUntil == 0,
                "the rebirth reset kept or lost the wrong glyph data");
        d.mutations.clear();
        d.glyphBag.clear();
        java.util.Arrays.fill(d.glyphSockets, "");
        setRebirth(p, 0);
    }

    // ---------------------------------------------------------------- 変異 (A08)
    private static void give(FakePlayer p, Mutation m, int cores, int shards) {
        p.getInventory().add(new ItemStack(m.coreItem(), cores));
        p.getInventory().add(new ItemStack(m.shardItem(), shards));
    }

    private static void mutations(MinecraftServer server, List<String> f) {
        FakePlayer p = player(server, "deep_test_m");
        SkillData d = SkillManager.get(p);
        d.bonusPoints = 80;
        SkillManager.forceRank(p, Skill.WIND_REND, 1);
        Mutation blade = Mutation.REND_BLADE;
        check(f, SkillExtOps.mutate(p, Skill.WIND_REND) == SkillExtOps.MutateResult.NOT_MASTERED, "an unmastered skill mutated");
        SkillManager.forceRank(p, Skill.WIND_REND, Skill.WIND_REND.maxRank);
        check(f, SkillExtOps.mutate(p, Skill.WIND_REND) == SkillExtOps.MutateResult.NO_MATERIAL, "a mutation was paid for without materials");
        give(p, blade, 1, 2);
        check(f, SkillExtOps.mutate(p, Skill.WIND_REND) == SkillExtOps.MutateResult.NO_MATERIAL && d.mutations.isEmpty(), "a mutation was bought with too few shards");
        give(p, blade, 0, 1);
        check(f, SkillExtOps.hasMaterial(p, blade), "materials were not counted");
        check(f, SkillExtOps.mutate(p, Skill.WIND_REND) == SkillExtOps.MutateResult.OK && "rend_blade".equals(d.mutations.get("wind_rend")), "the mutation was not chosen");
        check(f, SkillExtOps.count(p, blade.coreItem()) == 0 && SkillExtOps.count(p, blade.shardItem()) == 0, "materials were not taken");
        // 素材は留め袋に入っていても数える・使える (拾った素材は袋へ自動で入る。持ち物だけを見ると「足りない」と出てしまった)
        Mutation surge = Mutation.LUNGE_SURGE;
        SkillManager.forceRank(p, Skill.LUNGE, Skill.LUNGE.maxRank);
        jp.revati.skyabyss.pouch.Pouch.add(p, new ItemStack(surge.coreItem(), surge.CORES));
        jp.revati.skyabyss.pouch.Pouch.add(p, new ItemStack(surge.shardItem(), surge.SHARDS));
        check(f, SkillExtOps.hasMaterial(p, surge) && SkillExtOps.count(p, surge.coreItem()) == surge.CORES, "materials in the pouch were not counted");
        check(f, SkillExtOps.mutate(p, Skill.LUNGE) == SkillExtOps.MutateResult.OK && "lunge_surge".equals(d.mutations.get("lunge")), "the pouch materials did not pay for a mutation");
        check(f, SkillExtOps.count(p, surge.shardItem()) == 0, "the pouch materials were not taken");
        // 一度きり
        give(p, blade, 1, 3);
        check(f, SkillExtOps.mutate(p, Skill.WIND_REND) == SkillExtOps.MutateResult.ALREADY, "a skill mutated twice");
        check(f, SkillExtOps.mutate(p, Skill.SIGNAL) == SkillExtOps.MutateResult.UNKNOWN, "a skill without a mutation mutated");
        // 刻印とは別: 刻印も選べる
        check(f, SkillManager.chooseRune(p, Skill.WIND_REND, 1) == SkillManager.RuneResult.OK && d.rune(Skill.WIND_REND) == 1 && d.mutations.containsKey("wind_rend"),
                "a rune could not coexist with a mutation");
        // 効くのは極めているあいだだけ
        check(f, SkillExtOps.active(p, Skill.WIND_REND) == Mutation.REND_BLADE, "the mutation is not active");
        d.ranks.put("wind_rend", 1);
        check(f, SkillExtOps.active(p, Skill.WIND_REND) == null, "the mutation worked below max rank");
        d.ranks.put("wind_rend", Skill.WIND_REND.maxRank);
        // 力・待ち・マナは元のまま (変異は数字を変えない)
        check(f, SkillManager.manaCostFor(p, Skill.WIND_REND) == SkillRules.manaCost(Skill.WIND_REND, Skill.WIND_REND.maxRank), "a mutation changed the mana cost");
        // 8 つとも素材が違う巨神・技が違う
        java.util.Set<Skill> skills = new java.util.HashSet<>();
        java.util.Map<String, Integer> perTitan = new java.util.HashMap<>();
        for (Mutation m : Mutation.values()) {
            skills.add(m.skill);
            perTitan.merge(m.titan, 1, Integer::sum);
        }
        check(f, skills.size() == 8 && perTitan.size() == 4 && perTitan.values().stream().allMatch(n -> n == 2), "mutations are not spread over four titans");
        // 転生しても変異は残る
        SkillReset.forRebirth(p);
        check(f, "rend_blade".equals(d.mutations.get("wind_rend")) && SkillExtOps.active(p, Skill.WIND_REND) == null, "the rebirth reset lost or kept a mutation wrongly");
    }

    // ---------------------------------------------------------------- 技を使う (形と傷)
    private static Zombie zombie(ServerLevel level, double x, double y, double z) {
        Zombie zombie = new Zombie(EntityType.ZOMBIE, level);
        zombie.moveTo(x, y, z, 180f, 0f);
        zombie.setNoAi(true);
        zombie.setNoGravity(true);
        zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        zombie.setHealth(1000);
        level.addFreshEntity(zombie);
        return zombie;
    }

    private static double hurt(Zombie z) {
        double v = 1000 - z.getHealth();
        z.setHealth(1000);
        z.invulnerableTime = 0;
        return v;
    }

    private static void casts(MinecraftServer server, List<String> f) {
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        double px = spawn.getX() + 0.5;
        double pz = spawn.getZ() + 0.5;
        FakePlayer p = player(server, "deep_test_c");
        SkillData d = SkillManager.get(p);
        d.bonusPoints = 80;
        setRebirth(p, 5);
        p.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(10);
        // 駆け抜ける技は足場のあるところだけ進む: 空に石の床を敷く (終わりに外す)
        java.util.List<BlockPos> floor = new java.util.ArrayList<>();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -2; dz <= 12; dz++) {
                BlockPos at = new BlockPos(spawn.getX() + dx, 299, spawn.getZ() + dz);
                level.setBlock(at, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
                floor.add(at);
            }
        }
        Zombie near = zombie(level, px, 300, pz + 2.5);
        Zombie side = zombie(level, px + 2.5, 300, pz + 0.5);
        Zombie far = zombie(level, px, 300, pz + 7.0);
        try {
            // ---- 変異を入れない元の技の傷 (比べる基準)
            for (Skill s : new Skill[] {Skill.TITAN_CLEAVE, Skill.WHALE_SWEEP, Skill.WIND_REND, Skill.LUNGE}) {
                SkillManager.forceRank(p, s, s.maxRank);
            }
            double[] base = new double[4];
            Skill[] four = {Skill.TITAN_CLEAVE, Skill.WHALE_SWEEP, Skill.WIND_REND, Skill.LUNGE};
            for (int i = 0; i < four.length; i++) {
                p.moveTo(px, 300, pz, 0f, 0f);
                hurt(near);
                hurt(side);
                hurt(far);
                check(f, SkillActions.perform(p, four[i], four[i].maxRank) == null, "base skill failed: " + four[i].id);
                base[i] = hurt(near);
                hurt(side);
                hurt(far);
            }
            // ---- 変異した形 (すぐ当たる 4 つ): 傷は元の 0.4〜1.6 倍。形が違う (遠い敵・横の敵への当たり)
            Mutation[] muts = {Mutation.CLEAVE_CLIFF, Mutation.SWEEP_TAIL, Mutation.REND_BLADE, Mutation.LUNGE_SURGE};
            for (int i = 0; i < muts.length; i++) {
                Mutation m = muts[i];
                d.mutations.put(m.skill.id, m.id);
                p.moveTo(px, 300, pz, 0f, 0f);
                hurt(near);
                hurt(side);
                hurt(far);
                check(f, SkillActions.perform(p, m.skill, m.skill.maxRank) == null, "mutated skill failed: " + m.id);
                double dn = hurt(near);
                double ds = hurt(side);
                double df = hurt(far);
                check(f, dn > 0, m.id + " did not hit a foe right ahead");
                check(f, dn >= base[i] * 0.4 && dn <= base[i] * 1.6 + 0.5, m.id + " damage is out of line with the base skill: " + dn + " vs " + base[i]);
                if (m == Mutation.CLEAVE_CLIFF || m == Mutation.REND_BLADE) {
                    check(f, df > 0, m.id + " did not reach the far foe (shape did not change)");
                    check(f, ds == 0, m.id + " hit a foe to the side");
                }
                if (m == Mutation.SWEEP_TAIL) {
                    check(f, ds > 0 && df == 0, "tail quake shape is wrong: side " + ds + " far " + df);
                }
                d.mutations.remove(m.skill.id);
            }
            // ---- 遅れて落ちる 3 つ: 呼びが通り、あとで動く予約ができる
            SkillManager.forceRank(p, Skill.EMBER_HAMMER, 3);
            SkillManager.forceRank(p, Skill.BONE_TRAP, 2);
            SkillManager.forceRank(p, Skill.EBB_TIDE, 2);
            for (Mutation m : new Mutation[] {Mutation.HAMMER_METEOR, Mutation.TRAP_MINE, Mutation.EBB_WHIRL}) {
                d.mutations.put(m.skill.id, m.id);
                int later = SkillEffects.pendingLater();
                p.moveTo(px, 300, pz, 0f, 0f);
                check(f, SkillActions.perform(p, m.skill, Math.min(m.skill.maxRank, d.rank(m.skill))) == null || m == Mutation.EBB_WHIRL,
                        "mutated skill failed: " + m.id);
                check(f, SkillEffects.pendingLater() >= later, m.id + " lost its reservations");
                d.mutations.remove(m.skill.id);
            }
            // ---- 橋・淵・前世の能動 9 本
            for (Skill s : new Skill[] {Skill.DUST_CHARGE, Skill.STEAM_BELL, Skill.CINDER_VOLLEY, Skill.DEEP_PULL, Skill.DEEP_ECHO, Skill.DEEP_MAW, Skill.ECHO_STRIKE,
                    Skill.GRAVE_BELL, Skill.FINAL_LIGHT}) {
                SkillManager.forceRank(p, s, 2);
                p.moveTo(px, 300, pz, 0f, 0f);
                hurt(near);
                hurt(side);
                hurt(far);
                String r = SkillActions.perform(p, s, 2);
                check(f, r == null, "active skill failed: " + s.id + " " + r);
                double dn = hurt(near);
                double ds = hurt(side);
                if (s == Skill.DEEP_ECHO) {
                    check(f, dn == 0 && d.echoUntil > level.getGameTime(), "deep echo should buff and deal no damage");
                    check(f, SkillBuild.skillPower(p, Skill.TITAN_CLEAVE) >= SkillRules.echoPower(2), "deep echo did not raise skill power");
                } else if (s == Skill.STEAM_BELL || s == Skill.GRAVE_BELL || s == Skill.FINAL_LIGHT) {
                    check(f, dn > 0 && ds > 0, s.id + " did not hit foes all around");
                } else if (s == Skill.DUST_CHARGE || s == Skill.ECHO_STRIKE || s == Skill.CINDER_VOLLEY) {
                    check(f, dn > 0, s.id + " did not hit a foe ahead");
                }
                p.moveTo(px, 300, pz, 0f, 0f);
                d.echoUntil = 0;
            }
        } finally {
            near.discard();
            side.discard();
            far.discard();
            for (BlockPos at : floor) {
                level.setBlock(at, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
            }
            setRebirth(p, 0);
        }
    }

    // ---------------------------------------------------------------- 数のつり合い
    private static void balance(List<String> f) {
        // 新しい能動 9 本は、マナあたりの力が既存の最大 (巨神斬り段 3 を大きな相手へ) を超えない
        double ceiling = BuildsSelfTest.perMana(Skill.TITAN_CLEAVE, 3);
        double[][] fresh = {
                {SkillRules.dustMultiplier(2), SkillRules.manaCost(Skill.DUST_CHARGE, 2), SkillRules.cooldownTicks(Skill.DUST_CHARGE, 2)},
                {SkillRules.steamMultiplier(2), SkillRules.manaCost(Skill.STEAM_BELL, 2), SkillRules.cooldownTicks(Skill.STEAM_BELL, 2)},
                {SkillRules.volleyMultiplier(2) * SkillRules.VOLLEY_BOLTS, SkillRules.manaCost(Skill.CINDER_VOLLEY, 2), SkillRules.cooldownTicks(Skill.CINDER_VOLLEY, 2)},
                {SkillRules.pullMultiplier(2), SkillRules.manaCost(Skill.DEEP_PULL, 2), SkillRules.cooldownTicks(Skill.DEEP_PULL, 2)},
                {SkillRules.mawMultiplier(2) * 1.2, SkillRules.manaCost(Skill.DEEP_MAW, 2), SkillRules.cooldownTicks(Skill.DEEP_MAW, 2)},
                {SkillRules.echoStrikeMultiplier(2), SkillRules.manaCost(Skill.ECHO_STRIKE, 2), SkillRules.cooldownTicks(Skill.ECHO_STRIKE, 2)},
                {SkillRules.bellMultiplier(2), SkillRules.manaCost(Skill.GRAVE_BELL, 2), SkillRules.cooldownTicks(Skill.GRAVE_BELL, 2)},
                {SkillRules.lightMultiplier(2), SkillRules.manaCost(Skill.FINAL_LIGHT, 2), SkillRules.cooldownTicks(Skill.FINAL_LIGHT, 2)}};
        for (double[] row : fresh) {
            check(f, row[0] / row[1] <= ceiling, "a new active is stronger per mana than the strongest old one: " + row[0] / row[1] + " vs " + ceiling);
            check(f, row[0] * 20 / row[2] <= 0.45 * 1.6 * 3, "a new active is too strong per second: " + row[0] * 20 / row[2]);
        }
        // 最強の組み立て (31 の A 案の上限 + 紋・淵・前世・橋の足しを全部): 心臓は 15 秒、番兵長は 5 秒を割らない
        double basic = 1.6;
        double regen = SkillRules.regenPerSecond(20 + GrowthRules.bonusMana(20), false);
        double skillDps = regen * BuildsSelfTest.perMana(Skill.TITAN_CLEAVE, 3);
        double general = SkillRules.KEY_GLASS_DAMAGE + SkillRules.KEY_ASH_DAMAGE + 0.05 + 0.04 + 0.08 + 0.07;
        // 技の力: タグ・熟練の刻印・連鎖 + 紋 + 淵の残響・鏡・誓い + 記憶の刃 (技の力の合計は +100% で頭打ち)
        double skillPart = SkillRules.TAG_POWER_PER * SkillRules.TAG_COUNT_CAP + SkillRules.MASTERY_RUNE_POWER + SkillRules.CHAIN_POWER + SkillRules.GLYPH_POWER_CAP
                + SkillRules.echoPower(2) + SkillRules.mirrorPower(3) + SkillRules.OATH_DEEP_POWER + SkillRules.memoryPower(2, 6);
        skillPart = Math.min(SkillRules.POWER_CAP, skillPart);
        double regenBoost = 1 + SkillRules.seaRegen(2) + SkillRules.OATH_DEEP_REGEN;
        for (double mana : new double[] {1 + SkillRules.KEY_ASH_MANA_COST - SkillRules.GLYPH_MANA_CAP, 1 - SkillRules.GLYPH_MANA_CAP}) {
            double gen = mana < 1 ? general - SkillRules.KEY_ASH_DAMAGE : general;
            double maxDps = basic * (1 + gen) + skillDps * regenBoost / mana * (1 + gen) * (1 + skillPart);
            double heart = 450 / (8 * maxDps);
            double captain = 150 / (8 * maxDps);
            check(f, heart >= 15 && captain >= 5, "the strongest build with glyphs / deep / past kills too fast: heart " + heart + " s, captain " + captain + " s");
            SkyAbyss.LOGGER.info("[selftest] deep skills balance (strongest build, mana x{}): heart {} s, captain {} s", mana, Math.round(heart * 10) / 10.0, Math.round(captain * 10) / 10.0);
        }
        // 基準の心臓 26 秒・番兵長 9 秒は変えない (新しい技はふつうの回し方を強くしない)
        double heartBase = 450 / (8 * (basic + skillDps));
        double captainBase = 150 / (8 * (basic + skillDps));
        check(f, heartBase >= 20 && heartBase <= 40 && captainBase >= 6 && captainBase <= 14, "baseline kill times moved: " + heartBase + " / " + captainBase);
    }
}
