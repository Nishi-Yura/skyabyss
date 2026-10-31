package jp.revati.skyabyss.tower;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.story.finale.FinaleEnding;
import jp.revati.skyabyss.tower.mob.TowerMaster;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// あいだの塔の自己テスト (docs/design/54 の 2 章。橋の上の一戦)。段: "tower"
//   次元・橋の印 / 資格と一日一回 / 挑戦: 区画に流し込む (橋・帰りの天然磁石・印の石)・時間切れの判定・終わり / 区画は隊ごとに別
//   主の一戦 (TowerFight): 主と側の目 6 つが湧く・人数の体力・目覚めは傷が通らない・体力が月の前で止まる・予告の印の内外 /
//   落ちる月 (主が傷を受けない・渡りきると全滅・壊すと次の段へ) / 主が倒れると勝ちと記録
//   報酬 (docs/design/54 の 2.6): 塔の武器は塔の箱に入る・箱の表は 6 本・越えた人に箱 1 と欠片 10・塔の店の品
public final class TowerSelfTest {
    private TowerSelfTest() {
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        List<TowerRun> runs = new ArrayList<>();
        try {
            ServerLevel level = TowerWorld.level(server);
            check(f, level != null, "the tower dimension is missing");
            if (level == null) {
                report(f);
                return;
            }
            TowerTerrain t = TowerTerrain.of(TowerWorld.ARENA);
            for (String m : List.of("entry", "heal", "return", "sigil_1", "sigil_2", "sigil_3", "eye_l_1", "eye_r_1", "eye_l_2", "eye_r_2", "eye_big_l", "eye_big_r",
                    "hand_l", "hand_r", "healer_l", "healer_r", "moon", "boss", "bridge_n", "bridge_s")) {
                check(f, t.has(m), "the bridge lacks mark " + m);
            }
            FakePlayer p = FakePlayerFactory.get(server.overworld(), new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-0000000070e1"), "tower_test"));
            FakePlayer q = FakePlayerFactory.get(server.overworld(), new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-0000000070e2"), "tower_test_b"));
            p.getPersistentData().remove(FinaleEnding.KEY);
            p.getPersistentData().remove(Towers.DAY);
            check(f, !Towers.eligible(p), "someone without an ending can enter the tower");
            p.getPersistentData().putString(FinaleEnding.KEY, "calm");
            check(f, Towers.eligible(p) && !Towers.usedToday(p), "an eligible player is not eligible");
            Towers.markUsed(p);
            check(f, Towers.usedToday(p), "the daily entry was not recorded");
            p.getPersistentData().remove(Towers.DAY);

            TowerRun a = Towers.start(server, List.of(p), true);
            TowerRun b = Towers.start(server, List.of(q), true);
            if (a != null) {
                runs.add(a);
            }
            if (b != null) {
                runs.add(b);
            }
            check(f, a != null && b != null && a.slot != b.slot && Towers.runOf(p) == a, "two squads did not get their own slots");
            check(f, !Towers.usedToday(p), "a dev start used the daily entry");
            if (a != null) {
                check(f, level.getBlockState(a.mark("return")).is(Blocks.LODESTONE) && level.getBlockState(a.mark("sigil_2").below()).is(Blocks.CRYING_OBSIDIAN),
                        "the bridge was not poured in the slot");
                check(f, !a.arena.timedOut(a.startedTick + TowerArena.LIMIT - 1) && a.arena.timedOut(a.startedTick + TowerArena.LIMIT), "the 30 minute limit is wrong");
                fight(f, level, a, p);
                Towers.end(a);
                runs.remove(a);
                check(f, Towers.runOf(p) == null && !Towers.RUNS.containsKey(a.id), "the run did not end");
            }
            p.getPersistentData().remove(FinaleEnding.KEY);
        } catch (Exception ex) {
            SkyAbyss.LOGGER.error("SELFTEST tower crashed", ex);
            f.add("crashed: " + ex);
        } finally {
            for (TowerRun r : runs) {
                Towers.end(r);
            }
        }
        report(f);
    }

    // 主の一戦 (人のいない区画で、段を手で進める)
    static void fight(List<String> f, ServerLevel level, TowerRun run, FakePlayer p) {
        TowerFight fg = run.arena.fight;
        TowerMaster m = fg.master();
        check(f, m != null && m.isAlive() && fg.eyes.size() == 6 && fg.eyes.stream().allMatch(e -> e.mob.isAlive()), "the master and six side eyes did not spawn");
        if (m == null) {
            return;
        }
        check(f, Math.abs(TowerFight.masterHp(1) - TowerMaster.HP) < 1 && Math.abs(TowerFight.masterHp(12) - TowerMaster.HP * (1 + 0.85 * 11)) < 1
                && Math.abs(TowerFight.masterHp(20) - TowerFight.masterHp(12)) < 1, "the master's HP does not follow the squad size");
        check(f, Math.abs(m.getMaxHealth() * (1 / m.toughness()) - TowerFight.masterHp(1)) < 2, "the master was not tuned to its HP");
        check(f, !m.hurt(level.damageSources().generic(), 50), "the master can be hurt while it wakes");
        long now = level.getGameTime();
        fg.start = now - TowerFight.INTRO - 1;
        fg.tick(level, now);
        check(f, !m.shielded(), "the master stays shielded after waking");
        m.setHealth(1);
        check(f, m.getHealth() >= m.getMaxHealth() * 0.5f - 1, "the master's health did not stop before the first moon");
        // 予告の印
        TowerFight.Zone c = TowerFight.Zone.circle(new net.minecraft.world.phys.Vec3(0, 100, 0), 4, 0, 1, TowerFight.GOLD);
        TowerFight.Zone b = TowerFight.Zone.box(new net.minecraft.world.phys.Vec3(-2, 100, -5), new net.minecraft.world.phys.Vec3(2, 100, 5), 0, 1, TowerFight.GOLD);
        check(f, c.contains(new net.minecraft.world.phys.Vec3(3, 100, 0)) && !c.contains(new net.minecraft.world.phys.Vec3(3, 100, 3))
                && !c.contains(new net.minecraft.world.phys.Vec3(0, 106, 0)) && b.contains(new net.minecraft.world.phys.Vec3(1, 100, 4)) && !b.contains(new net.minecraft.world.phys.Vec3(3, 100, 0)),
                "the warning zones are wrong");
        // 落ちる月: 体力が止まった所で月が出る
        fg.tick(level, now + 1);
        check(f, fg.moon != null && fg.moon.isAlive() && m.shielded(), "the first moon did not rise at 50%");
        if (fg.moon != null) {
            fg.moonBorn = now - TowerFight.MOON_TICKS;
            fg.tickMoon(level, now + 2, List.of());
            check(f, "moon_lost".equals(fg.failed), "a moon that crossed the bridge did not wipe the squad");
            fg.failed = null;
            fg.moon.kill();
            fg.tickMoon(level, now + 3, List.of());
            check(f, fg.moon == null && fg.moonsDone == 1 && !m.shielded(), "breaking the moon did not lift the master's shield");
            m.setHealth(1);
            check(f, m.getHealth() >= m.getMaxHealth() * 0.2f - 1 && m.getHealth() < m.getMaxHealth() * 0.5f, "the master's health did not stop before the second moon");
        }
        // 勝ち
        fg.moon = null;
        fg.moonsDone = 2;
        m.setFloor(0);
        m.kill();
        fg.tick(level, now + 4);
        Towers.tick(level, run, now + 4);
        check(f, fg.won && run.cleared, "the run was not cleared when the master fell");
        // 報酬
        jp.revati.skyabyss.loot.box.BoxKind box = jp.revati.skyabyss.loot.box.BoxKind.byId("box_tower");
        check(f, box != null && jp.revati.skyabyss.loot.box.LootBoxes.kindFor(new net.minecraft.world.item.ItemStack(jp.revati.skyabyss.tower.gear.TowerGearSetup.SWORD.get())) == box
                && jp.revati.skyabyss.loot.box.LootBoxes.pool(box).size() == jp.revati.skyabyss.tower.gear.TowerGear.WEAPONS.size(), "tower gear does not go into the tower box");
        int shards0 = jp.revati.skyabyss.pouch.Pouch.countAll(p, jp.revati.skyabyss.tower.gear.TowerGearSetup.SHARD.get());
        int boxes0 = box == null ? 0 : jp.revati.skyabyss.pouch.Pouch.countAll(p, jp.revati.skyabyss.loot.box.LootBoxSetup.item(box));
        Towers.reward(p);
        check(f, jp.revati.skyabyss.pouch.Pouch.countAll(p, jp.revati.skyabyss.tower.gear.TowerGearSetup.SHARD.get()) == shards0 + jp.revati.skyabyss.tower.gear.TowerGear.SHARDS_PER_CLEAR
                && box != null && jp.revati.skyabyss.pouch.Pouch.countAll(p, jp.revati.skyabyss.loot.box.LootBoxSetup.item(box)) == boxes0 + 1, "the clear reward was not given");
        var shop = jp.revati.skyabyss.facility.ShopManager.shop(TowerDesk.SHOP);
        check(f, shop != null && shop.goods().size() == 7, "the tower shop is missing or incomplete");
        p.getPersistentData().remove(Towers.CLEARS);
        p.getPersistentData().remove(Towers.BEST);
    }

    static void report(List<String> f) {
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST tower PASSED (dimension, bridge marks, eligibility and daily entry, slots per squad, bridge poured, time limit, master and eyes, party HP, wake shield, health floors, warning zones, falling moon, clear, rewards, shop, end)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST tower FAILED: {}", m));
        }
    }
}
