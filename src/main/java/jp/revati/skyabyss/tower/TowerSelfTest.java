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
                && jp.revati.skyabyss.loot.box.LootBoxes.kindFor(new net.minecraft.world.item.ItemStack(jp.revati.skyabyss.tower.gear.TowerGearSetup.CHAIN.get())) == box
                && jp.revati.skyabyss.loot.box.LootBoxes.kindFor(new net.minecraft.world.item.ItemStack(
                        jp.revati.skyabyss.tower.gear.TowerGearSetup.ARMOR.get(jp.revati.skyabyss.tower.gear.TowerGear.HALO).get(1).get())) == box
                && jp.revati.skyabyss.loot.box.LootBoxes.pool(box).size() == jp.revati.skyabyss.tower.gear.TowerGearSetup.all().size(), "tower gear does not go into the tower box");
        int shards0 = jp.revati.skyabyss.pouch.Pouch.countAll(p, jp.revati.skyabyss.tower.gear.TowerGearSetup.SHARD.get());
        // 天井の一つ手前にしておくと、この回で必ず箱が出る
        p.getPersistentData().putInt(jp.revati.skyabyss.tower.gear.TowerGear.BOX_DRY, jp.revati.skyabyss.tower.gear.TowerGear.BOX_PITY - 1);
        int boxes0 = box == null ? 0 : jp.revati.skyabyss.pouch.Pouch.countAll(p, jp.revati.skyabyss.loot.box.LootBoxSetup.item(box));
        long coins0 = jp.revati.skyabyss.facility.Coins.count(p);
        Towers.reward(p, null);
        check(f, jp.revati.skyabyss.pouch.Pouch.countAll(p, jp.revati.skyabyss.tower.gear.TowerGearSetup.SHARD.get()) == shards0 + jp.revati.skyabyss.tower.gear.TowerGear.SHARDS_PER_CLEAR
                && box != null && jp.revati.skyabyss.pouch.Pouch.countAll(p, jp.revati.skyabyss.loot.box.LootBoxSetup.item(box)) == boxes0 + 1
                && jp.revati.skyabyss.facility.Coins.count(p) == coins0 + jp.revati.skyabyss.tower.gear.TowerGear.COINS_PER_CLEAR, "the clear reward was not given");
        boolean mythic = true;
        for (var d : jp.revati.skyabyss.tower.gear.TowerGearSetup.all()) {
            mythic &= d.get() instanceof jp.revati.skyabyss.item.ModGear g && g.tier() == jp.revati.skyabyss.item.GearTier.MYTHIC;
        }
        check(f, mythic, "a tower gear item is not mythic");
        check(f, p.getPersistentData().getInt(jp.revati.skyabyss.tower.gear.TowerGear.BOX_DRY) == 0, "the pity count did not reset after a box");
        int dryBoxes = 0;
        for (int i = 0; i < jp.revati.skyabyss.tower.gear.TowerGear.BOX_PITY; i++) {
            dryBoxes += Towers.rollBox(p, false) ? 1 : 0;
        }
        check(f, dryBoxes >= 1, "ten clears in a row gave no tower box (the pity did not work)");
        p.getPersistentData().remove(jp.revati.skyabyss.tower.gear.TowerGear.BOX_DRY);
        if (box != null) {
            var opened = jp.revati.skyabyss.loot.box.LootBoxes.roll(box, null, p.getRandom());
            check(f, !opened.isEmpty() && jp.revati.skyabyss.loot.box.LootBoxes.kindFor(opened) == box, "the tower box did not roll tower gear");
        }
        var shop = jp.revati.skyabyss.facility.ShopManager.shop(TowerDesk.SHOP);
        check(f, shop != null && shop.goods().size() == jp.revati.skyabyss.tower.gear.TowerGearSetup.all().size() + 2, "the tower shop is missing or incomplete");
        armor(f, p);
        wagers(f, run);
        arts(f, p);
        p.getPersistentData().remove(Towers.CLEARS);
        p.getPersistentData().remove(Towers.BEST);
    }

    // 塔の防具の 4 部位の効果 (光輪の衣のマナの戻りと待ち、渡りの装束の速さ)
    static void armor(List<String> f, FakePlayer p) {
        var halo = jp.revati.skyabyss.tower.gear.TowerGearSetup.ARMOR.get(jp.revati.skyabyss.tower.gear.TowerGear.HALO);
        net.minecraft.world.entity.EquipmentSlot[] slots = {net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET};
        int lv = jp.revati.skyabyss.rpg.Growth.get(p).level;
        jp.revati.skyabyss.rpg.Growth.get(p).level = 50;
        for (int i = 0; i < 4; i++) {
            p.setItemSlot(slots[i], new net.minecraft.world.item.ItemStack(halo.get(i).get()));
        }
        check(f, jp.revati.skyabyss.tower.gear.TowerArmorItem.piecesWorn(p, jp.revati.skyabyss.tower.gear.TowerGear.HALO) == 4
                && jp.revati.skyabyss.tower.gear.TowerGearEvents.manaRegenBonus(p) > 0 && jp.revati.skyabyss.item.wild.WildGearEvents.cooldownScale(p) < 1,
                "the halo vestment's four-piece effect does not apply");
        for (net.minecraft.world.entity.EquipmentSlot s : slots) {
            p.setItemSlot(s, net.minecraft.world.item.ItemStack.EMPTY);
        }
        jp.revati.skyabyss.rpg.Growth.get(p).level = lv;
    }

    // 週の賭け (今週の六つ・欠片の倍率・時間の限り) と記録の板 (人数の最速・組合の順位)
    static void wagers(List<String> f, TowerRun run) {
        List<String> week = TowerWagers.thisWeek();
        check(f, week.size() == TowerWagers.PER_WEEK && TowerWagers.POOL.containsAll(week) && week.stream().distinct().count() == week.size()
                && week.equals(TowerWagers.thisWeek()), "this week's wagers are not six fixed picks from the pool");
        check(f, Math.abs(TowerWagers.shardScale(java.util.Set.of("a", "b")) - 1.6) < 1e-9, "the wager shard bonus is wrong");
        run.wagers.add("short_time");
        check(f, run.limit() == TowerWagers.SHORT_LIMIT && run.arena.timedOut(run.startedTick + TowerWagers.SHORT_LIMIT), "the short time wager does not shorten the limit");
        run.wagers.clear();
        TowerRecords r = new TowerRecords();
        boolean first = r.record(3, 9000, List.of("a", "b", "c"), java.util.Collections.singletonList(new String[]{"g1", "Guild"}));
        boolean slower = r.record(3, 9500, List.of("d", "e", "f"), java.util.Collections.singletonList(new String[]{"g1", "Guild"}));
        r.record(2, 12000, List.of("x", "y"), java.util.Collections.singletonList(new String[]{"g2", "Other"}));
        check(f, first && !slower && r.best(3).ticks() == 9000 && r.ranking(5).get(0).getKey().equals("g1") && r.ranking(5).get(0).getValue() == 2,
                "the record board did not keep the fastest run or count guild clears");
    }

    // 秘伝 (rpg/skill/SecretArts): 段は III まで、枠は二つ、職ごとに 4 つ、どの秘伝の上乗せも動く
    static void arts(List<String> f, FakePlayer p) {
        p.getPersistentData().remove(jp.revati.skyabyss.rpg.skill.SecretArts.ARTS);
        p.getPersistentData().remove(jp.revati.skyabyss.rpg.skill.SecretArts.SLOTS_KEY);
        var cut = jp.revati.skyabyss.rpg.skill.SecretArts.Art.SECOND_CUT;
        boolean ok = jp.revati.skyabyss.rpg.skill.SecretArts.learn(p, cut) && jp.revati.skyabyss.rpg.skill.SecretArts.learn(p, cut)
                && jp.revati.skyabyss.rpg.skill.SecretArts.learn(p, cut) && !jp.revati.skyabyss.rpg.skill.SecretArts.learn(p, cut);
        check(f, ok && jp.revati.skyabyss.rpg.skill.SecretArts.tier(p, cut) == 3, "a secret art did not stop at tier III");
        jp.revati.skyabyss.rpg.skill.SecretArts.learn(p, jp.revati.skyabyss.rpg.skill.SecretArts.Art.QUAKE_CALL);
        jp.revati.skyabyss.rpg.skill.SecretArts.learn(p, jp.revati.skyabyss.rpg.skill.SecretArts.Art.WHALE_WHIRL);
        check(f, jp.revati.skyabyss.rpg.skill.SecretArts.equipped(p).size() == jp.revati.skyabyss.rpg.skill.SecretArts.SLOTS
                && !jp.revati.skyabyss.rpg.skill.SecretArts.equip(p, jp.revati.skyabyss.rpg.skill.SecretArts.Art.WHALE_WHIRL), "the secret art slots are not two");
        // 技の画面と帯が見る「いまの変化」(SkillChanges) に枠の秘伝が出る。枠に無い秘伝は出ない
        var ch = jp.revati.skyabyss.rpg.skill.SkillChanges.of(p, cut.skill);
        check(f, ch.stream().anyMatch(c -> c.kind() == jp.revati.skyabyss.rpg.skill.SkillChanges.Kind.SECRET && c.active())
                && jp.revati.skyabyss.rpg.skill.SkillChanges.markColor(p, cut.skill) != 0, "a slotted secret art is not shown as a skill change");
        check(f, jp.revati.skyabyss.rpg.skill.SkillChanges.of(p, jp.revati.skyabyss.rpg.skill.SecretArts.Art.WHALE_WHIRL.skill).stream()
                .noneMatch(c -> c.kind() == jp.revati.skyabyss.rpg.skill.SkillChanges.Kind.SECRET), "an unslotted secret art is shown as a skill change");
        java.util.Map<jp.revati.skyabyss.rpg.skill.Job, Integer> per = new java.util.EnumMap<>(jp.revati.skyabyss.rpg.skill.Job.class);
        for (var a : jp.revati.skyabyss.rpg.skill.SecretArts.Art.values()) {
            per.merge(a.job, 1, Integer::sum);
            try {
                jp.revati.skyabyss.rpg.skill.SecretArts.applyForTest(p, a, 3);
            } catch (RuntimeException ex) {
                f.add("secret art " + a.id + " crashed: " + ex);
            }
        }
        check(f, per.size() == 6 && per.values().stream().allMatch(n -> n == 4), "not four secret arts per job: " + per);
        p.getPersistentData().remove(jp.revati.skyabyss.rpg.skill.SecretArts.ARTS);
        p.getPersistentData().remove(jp.revati.skyabyss.rpg.skill.SecretArts.SLOTS_KEY);
    }

    static void report(List<String> f) {
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST tower PASSED (dimension, bridge marks, eligibility and daily entry, slots per squad, bridge poured, time limit, master and eyes, party HP, wake shield, health floors, warning zones, falling moon, clear, rewards, shop, tower armor, wagers, record board, secret arts, skill changes, end)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST tower FAILED: {}", m));
        }
    }
}
