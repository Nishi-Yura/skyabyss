package jp.revati.skyabyss.story.ch5.warden;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.rpg.skill.SkillManager;
import jp.revati.skyabyss.story.ch5.AbyssBuild;
import jp.revati.skyabyss.story.ch5.AbyssEntrance;
import jp.revati.skyabyss.story.ch5.AbyssWorld;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.town.TownGuests;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;

// SELFTEST abyss nightmare: 番人の悪夢 (docs/design/36 の 9)。
//   段の表 (強さが増える・決まりが一つずつ増える・普通の戦いは変わらない)、入る条件 (倒す前は入れない・心臓の後・台のそば・
//   番人がいない)、段の進み (組ごと・人ごと・越えた段の次だけ)、報酬 (初めてだけ・繰り返しは少し・段 5 と 10)、
//   保存の往復、閉じたあとの後片付け (写し・灯籠・番人が残らない)
public final class NightmareSelfTest {
    private NightmareSelfTest() {
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        BlockPos heart = AbyssBuild.mark("heart");
        WardenArena.Data wd = WardenArena.Data.get(server);
        boolean clearedWas = wd.cleared;
        Nightmare.Data d = Nightmare.Data.get(server);
        Map<UUID, Integer> groupsWas = new HashMap<>(d.groups);
        Map<UUID, Integer> playersWas = new HashMap<>(d.players);
        Set<UUID> crownedWas = new HashSet<>(d.crowned);
        FakePlayer p = null;
        try {
            checkTable(f);
            check(f, Nightmare.tier() == NightmareTier.NORMAL && !Nightmare.active(), "a nightmare is running before the test");
            if (level == null || heart == null) {
                f.add("the abyss dimension or the heart mark is missing");
                report(f);
                return;
            }
            WardenSelfTest.load(server);
            // 普通の戦いは段を持たない (数字は今まで通り)
            HeartWarden plain = WardenArena.wake(level);
            check(f, plain != null && plain.tier() == NightmareTier.NORMAL, "the normal fight has a nightmare tier");
            check(f, plain != null && Math.abs(plain.getMaxHealth() - HeartWarden.BASE_HP) < 1, "the normal fight's health changed: " + (plain == null ? null : plain.getMaxHealth()));
            check(f, AbyssBeacons.usable(7) && Nightmare.tier().lanternLife() == 1.0, "the normal fight should use all lanterns at full time");
            WardenArena.clearRoom(level);

            // 入る条件: 倒す前は入れない (闇の台のまま)
            p = fake(level, "nightmare_gate");
            p.moveTo(heart.getX() + 0.5, heart.getY() + 1, heart.getZ() + 0.5);
            wd.cleared = false;
            wd.setDirty();
            check(f, Nightmare.check(p) == Nightmare.Result.LOCKED, "the nightmare opened before the warden fell");
            wd.cleared = true;
            wd.setDirty();
            // メインの段が心臓の前は入れない
            Quests.forgetAll(p);
            Quests.start(p, AbyssEntrance.QUEST);
            check(f, Nightmare.check(p) == Nightmare.Result.NOT_READY, "the nightmare opened before the heart talk");
            for (int i = 0; i < 10 && !"report".equals(TownGuests.stageOf(p, AbyssEntrance.QUEST)); i++) {
                Quests.forceStage(p, AbyssEntrance.QUEST);
            }
            check(f, Nightmare.passedHeart(p), "the report stage should count as past the heart");
            // 台から離れていれば入れない
            p.moveTo(heart.getX() + 30.5, heart.getY() + 1, heart.getZ() + 0.5);
            check(f, Nightmare.check(p) == Nightmare.Result.FAR, "a far player could enter");
            p.moveTo(heart.getX() + 0.5, heart.getY() + 1, heart.getZ() + 0.5);

            // 入る: 段 1 (番人が立つ・体力は段の倍率・記録の段は組の次)
            UUID group = Groups.keyOf(p);
            check(f, Nightmare.next(server, group).n() == 1, "a new group should start at tier 1");
            check(f, Nightmare.enter(p) == Nightmare.Result.OK, "the first entry was refused: " + Nightmare.check(p));
            HeartWarden w1 = WardenArena.current(level);
            check(f, Nightmare.active() && Nightmare.session().tier().n() == 1, "the session should be tier 1");
            check(f, w1 != null && w1.tier().n() == 1, "the warden did not take tier 1");
            check(f, w1 != null && Math.abs(w1.getMaxHealth() - HeartWarden.BASE_HP * 1.1) < 1, "tier 1 health: " + (w1 == null ? null : w1.getMaxHealth()));
            check(f, Nightmare.tier().lanternLife() == 0.75, "tier 1 should shorten the lanterns");
            long now = level.getGameTime();
            AbyssBeacons.light(level, 0, now);
            AbyssBeacons.tickAt(level, now + 600 - 1);
            check(f, AbyssBeacons.lit(0), "tier 1 lantern went out before 30 s");
            AbyssBeacons.tickAt(level, now + 600);
            check(f, !AbyssBeacons.lit(0), "tier 1 lantern did not go out at 30 s");
            check(f, Nightmare.check(p) == Nightmare.Result.BUSY, "a second entry should wait for the warden");

            // 倒す: 段 1 の記録 (組・人)。報酬は別に確かめる
            w1.getEntityData().set(HeartWarden.LIT, true);
            w1.invulnerableTime = 0;
            w1.hurt(level.damageSources().generic(), 100000);
            check(f, w1.isDeadOrDying() && !Nightmare.active(), "the warden did not fall or the session stayed open");
            check(f, Nightmare.groupBest(server, group) == 1 && Nightmare.next(server, group).n() == 2, "the group did not record tier 1");
            w1.discard();

            // 報酬: 初めては点と経験値、繰り返しは経験値だけ。段 5 は特別 (点 +3)
            UUID g2 = UUID.randomUUID();
            long score0 = p.getScore();
            int points0 = SkillManager.get(p).bonusPoints;
            boolean first = Nightmare.settle(server, g2, NightmareTier.of(1), List.of(p));
            check(f, first, "the first clear of tier 1 was not first");
            check(f, SkillManager.get(p).bonusPoints == points0 + 1, "the first clear should give 1 point");
            check(f, p.getScore() - score0 == Nightmare.FIRST_XP, "the first clear should give 100 xp per tier 1: " + (p.getScore() - score0));
            long score1 = p.getScore();
            int points1 = SkillManager.get(p).bonusPoints;
            boolean again = Nightmare.settle(server, g2, NightmareTier.of(1), List.of(p));
            check(f, !again && SkillManager.get(p).bonusPoints == points1, "a repeat clear gave points again");
            check(f, p.getScore() - score1 == Nightmare.REPEAT_XP, "a repeat clear should give the small xp: " + (p.getScore() - score1));
            UUID g3 = UUID.randomUUID();
            int points2 = SkillManager.get(p).bonusPoints;
            Nightmare.settle(server, g3, NightmareTier.of(5), List.of(p));
            check(f, SkillManager.get(p).bonusPoints == points2 + Nightmare.pointsFor(5) && Nightmare.pointsFor(5) == 3, "tier 5 special points are wrong");
            check(f, Nightmare.pointsFor(10) == 5 && Nightmare.pointsFor(3) == 1, "the point table is wrong");
            // 人ごとの記録は越えた最大 (組が違っても)
            check(f, Nightmare.playerBest(server, p.getUUID()) >= 5, "the player's best tier was not kept");
            check(f, Nightmare.groupBest(server, g2) == 1 && Nightmare.next(server, g2).n() == 2, "the second group's record is wrong");
            // 段を飛ばして進めない: 次の段の表を見て、次の段は一つ上だけ
            Nightmare.settle(server, g2, NightmareTier.of(2), List.of(p));
            check(f, Nightmare.groupBest(server, g2) == 2 && Nightmare.next(server, g2).n() == 3, "tier 2 did not follow tier 1");
            check(f, Nightmare.next(server, UUID.randomUUID()).n() == 1, "a new group should not inherit a record");

            // 保存の往復
            Nightmare.Data tmp = new Nightmare.Data();
            tmp.groups.put(g2, 2);
            tmp.players.put(p.getUUID(), 5);
            tmp.crowned.add(g3);
            CompoundTag tag = tmp.save(new CompoundTag(), server.registryAccess());
            Nightmare.Data back = Nightmare.Data.load(tag, server.registryAccess());
            check(f, back.groups.get(g2) == 2 && back.players.get(p.getUUID()) == 5 && back.crowned.contains(g3), "the record did not survive a save");

            // 閉じる: 段 3 の悪夢を始めて、写しを出し、灯籠を点けて、閉じる。何も残らない
            UUID g4 = UUID.randomUUID();
            NightmareTier t3 = NightmareTier.of(3);
            WardenArena.wake(level, t3);
            Nightmare.begin(g4, t3);
            HeartWarden w3 = WardenArena.current(level);
            check(f, w3 != null && w3.tier() == t3 && Math.abs(w3.getMaxHealth() - HeartWarden.BASE_HP * 1.3) < 1, "tier 3 health is wrong");
            w3.setHealth(w3.getMaxHealth() * 0.5f);
            w3.updatePhase(level);
            check(f, shades(level, w3) == 2, "tier 3 should bring two shades at phase 2");
            AbyssBeacons.light(level, 0, level.getGameTime());
            Nightmare.abort(level);
            WardenArena.clearRoom(level);
            check(f, !Nightmare.active() && Nightmare.tier() == NightmareTier.NORMAL, "the nightmare did not close");
            check(f, shades(level, w3) == 0 && WardenArena.current(level) == null, "the shades or the warden stayed after the nightmare closed");
            check(f, AbyssBeacons.litCount() == 0, "a lantern stayed lit after the nightmare closed");
            check(f, Nightmare.groupBest(server, g4) == 0, "a closed nightmare should not record a tier");
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST abyss nightmare crashed", e);
            f.add("crashed: " + e);
        } finally {
            if (level != null && heart != null) {
                for (HeartWarden h : level.getEntitiesOfClass(HeartWarden.class, new net.minecraft.world.phys.AABB(heart).inflate(64), e -> true)) {
                    h.discard();
                }
                Nightmare.abort(level);
                AbyssBeacons.reset(level);
                WardenSelfTest.hold(level, heart, false);
            }
            if (p != null) {
                Quests.forgetAll(p);
            }
            wd.cleared = clearedWas;
            wd.setDirty();
            d.groups.clear();
            d.groups.putAll(groupsWas);
            d.players.clear();
            d.players.putAll(playersWas);
            d.crowned.clear();
            d.crowned.addAll(crownedWas);
            d.setDirty();
        }
        report(f);
    }

    // 段の表: 上の段ほど強く、決まりが一つずつ増える。普通の戦いは表の外で、数字は今のまま
    private static void checkTable(List<String> f) {
        List<NightmareTier> t = NightmareTier.TABLE;
        check(f, t.size() == NightmareTier.MAX, "the tier table should have 10 tiers: " + t.size());
        NightmareTier prev = NightmareTier.NORMAL;
        for (int i = 0; i < t.size(); i++) {
            NightmareTier x = t.get(i);
            int n = i + 1;
            check(f, x.n() == n, "tier " + n + " has number " + x.n());
            check(f, x.hp() > prev.hp(), "tier " + n + " health did not grow");
            check(f, x.dmg() >= prev.dmg(), "tier " + n + " damage fell");
            check(f, x.lanterns() <= prev.lanterns() && x.lanterns() >= 4, "tier " + n + " lanterns: " + x.lanterns());
            check(f, x.lanternLife() <= prev.lanternLife(), "tier " + n + " lanterns last longer");
            check(f, x.darkShare() <= prev.darkShare(), "tier " + n + " dark share rose");
            check(f, x.snuffTicks() <= prev.snuffTicks() && x.snuffTicks() >= 20, "tier " + n + " snuff warning is out of range");
            check(f, x.waves() >= prev.waves(), "tier " + n + " waves fell");
            check(f, keeps(prev.clawTwice(), x.clawTwice()) && keeps(prev.shadesStrike(), x.shadesStrike()) && keeps(prev.darkRegen(), x.darkRegen())
                    && keeps(prev.lungeFollow(), x.lungeFollow()) && keeps(prev.heartRing(), x.heartRing()), "tier " + n + " dropped a rule from the tier below");
            check(f, x.changesFrom(prev), "tier " + n + " adds no rule");
            check(f, x.nightmare(), "tier " + n + " is not a nightmare tier");
            prev = x;
        }
        NightmareTier n0 = NightmareTier.NORMAL;
        check(f, n0.hp() == 1.0 && n0.dmg() == 1.0 && n0.lanternLife() == 1.0 && n0.lanterns() == 8 && n0.waves() == 1 && n0.snuffTicks() == HeartWarden.SNUFF_TICKS
                && Math.abs(n0.darkShare() - HeartWarden.DARK_SHARE) < 1e-6 && !n0.nightmare(), "the normal numbers changed");
        check(f, NightmareTier.of(0) == n0 && NightmareTier.of(-1) == n0 && NightmareTier.of(11) == t.get(9) && NightmareTier.of(1) == t.get(0), "the tier lookup is wrong");
        check(f, HeartWarden.BASE_HP == 600, "the normal health changed");
    }

    // 後の段は前の段の決まりを落とさない (オン のまま)
    private static boolean keeps(boolean before, boolean after) {
        return !before || after;
    }

    private static int shades(ServerLevel level, HeartWarden w) {
        return level.getEntitiesOfClass(HeartWarden.class, w.getBoundingBox().inflate(48), HeartWarden::decoy).size();
    }

    private static FakePlayer fake(ServerLevel level, String name) {
        return new FakePlayer(level, new GameProfile(UUID.randomUUID(), name));
    }

    private static void report(List<String> f) {
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST abyss nightmare PASSED (tier table, normal fight unchanged, gate, group and player records, rewards once, save, clean close)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST abyss nightmare FAILED: {}", m));
        }
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }
}
