package jp.revati.skyabyss.dungeon;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.tower.TowerTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 地下のダンジョンの自己テスト (docs/design/55)。段: "dungeons"。
//   層の印     どの層にも entry、一・二層目に leave・cp・next、主の間に boss・exit。扉は _lo と _hi の組。敵の種はみな表にあり、敵の型がある
//   写し       二つの組は別の区画。層を流すと帰り石 (天然磁石) と宝箱 (中身つき) が立つ。宝箱の受け取りの鍵は区画によらない
//   進み       部屋に寄ると敵が出る。鍵持ちを倒して扉に寄ると扉が開く。中ボスを倒すと扉が開く。罠を踏むと予告が出る
//   続きから   次の層へ移ると checkpoint と人ごとの続きが進む。主を倒すと報酬 (越えた回数・続きが消える・箱の日)
public final class DungeonSelfTest {
    private DungeonSelfTest() {
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        List<DungeonRun> runs = new ArrayList<>();
        int built = 0;
        try {
            ServerLevel level = DungeonWorld.level(server);
            check(f, level != null, "the dungeon dimension is missing");
            if (level == null) {
                report(f, 0);
                return;
            }
            for (Dungeon d : Dungeon.values()) {
                if (Dungeon.class.getResource("/data/" + SkyAbyss.MODID + "/dungeon/" + d.terrain(0) + ".json") == null) {
                    f.add(d.id + " is not built yet");
                    continue;
                }
                built++;
                marks(f, d);
            }
            FakePlayer p = FakePlayerFactory.get(server.overworld(), new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-0000000d0e01"), "dungeon_test"));
            FakePlayer q = FakePlayerFactory.get(server.overworld(), new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-0000000d0e02"), "dungeon_test_b"));
            Dungeon d = Dungeon.BONE_PIT;
            p.getPersistentData().remove(Dungeons.PROGRESS + d.id);
            p.getPersistentData().remove(Dungeons.CLEARS + d.id);
            p.getPersistentData().remove(Dungeons.BOX_DAY + d.id);
            q.getPersistentData().remove(Dungeons.PROGRESS + d.id);
            DungeonRun a = Dungeons.startForTest(server, d, List.of(p));
            DungeonRun b = Dungeons.startForTest(server, d, List.of(q));
            if (a != null) {
                runs.add(a);
            }
            if (b != null) {
                runs.add(b);
            }
            check(f, a != null && b != null && a.slot != b.slot && Dungeons.runOf(p) == a, "two squads did not get their own copies");
            if (a == null) {
                report(f, built);
                return;
            }
            check(f, a.checkpoint == 0 && DungeonWorld.in(p), "the squad did not arrive on the first level");
            check(f, level.getBlockState(a.mark(0, "leave")).is(Blocks.LODESTONE), "the first level was not poured (no return stone)");
            BlockPos chest = a.mark(0, "chest_1");
            check(f, level.getBlockEntity(chest) instanceof ChestBlockEntity c && (!c.isEmpty() || jp.revati.skyabyss.loot.SharedChests.known(level, chest)),
                    "the first chest has nothing for players");
            check(f, b != null && DungeonWorld.chestKey(level, a.mark(0, "chest_1")).equals(DungeonWorld.chestKey(level, b.mark(0, "chest_1"))),
                    "the chest wait differs between copies");
            DungeonFloor f0 = a.floor(0);
            // 部屋 A に寄ると敵が出る
            Vec3 ra = a.markCenter(0, "room_a");
            p.moveTo(ra.x, ra.y, ra.z, 0f, 0f);
            f0.wakeRooms(level, List.of(p));
            int foes = level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(40), m -> m.getTags().contains(a.foeTag())).size();
            check(f, f0.woke.contains("a") && foes >= f0.roomFoes.get("a").size(), "room a did not wake (" + foes + " foes)");
            // 鍵持ちを倒して扉へ寄ると開く
            Vec3 rb = a.markCenter(0, "room_b");
            p.moveTo(rb.x, rb.y, rb.z, 0f, 0f);
            f0.wakeRooms(level, List.of(p));
            Mob key = null;
            for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(40), m -> m.getTags().contains(Dungeons.KEY_TAG) && m.getTags().contains(a.foeTag()))) {
                key = m;
            }
            check(f, key != null, "no key bearer in room b");
            if (key != null) {
                key.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
            }
            check(f, f0.keyTaken, "killing the key bearer did not give the key");
            BlockPos lo = a.mark(0, "door_1_key_lo");
            check(f, level.getBlockState(lo).is(Blocks.IRON_BARS), "the key door is not barred");
            p.moveTo(lo.getX() + 2.5, lo.getY(), lo.getZ() - 2.5, 0f, 0f);
            f0.doors(level, List.of(p));
            check(f, !level.getBlockState(lo).is(Blocks.IRON_BARS) && f0.opened.contains("door_1_key"), "the key door did not open");
            // 罠を踏むと予告が出る
            int before = Telegraph.activeCount(level);
            Vec3 tr = a.markCenter(0, "trap_1_rocks");
            p.moveTo(tr.x, tr.y, tr.z, 0f, 0f);
            f0.traps(level, level.getGameTime(), List.of(p));
            check(f, Telegraph.activeCount(level) > before, "the rock trap did not warn");
            // 次の層へ: checkpoint と人ごとの続き
            Dungeons.moveTo(p, a, 1);
            check(f, a.checkpoint == 1 && Dungeons.progress(p, d) == 1 && a.floorOf(p) == 1, "moving down did not set the checkpoint");
            DungeonFloor f1 = a.floor(1);
            Vec3 rm = a.markCenter(1, "room_mid");
            p.moveTo(rm.x, rm.y, rm.z, 0f, 0f);
            f1.wakeRooms(level, List.of(p));
            Mob mid = f1.mid == null ? null : (Mob) level.getEntity(f1.mid);
            check(f, mid != null && mid.getTags().contains(jp.revati.skyabyss.world.continent.NamedFoes.TAG), "the mid boss did not appear");
            if (mid != null) {
                mid.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
            }
            f1.doors(level, List.of(p));
            check(f, f1.midDown && f1.opened.contains("door_2_mid"), "the mid boss door did not open");
            // 主の間: 主が出て、倒すと報酬
            Dungeons.moveTo(p, a, Dungeon.BOSS_FLOOR);
            DungeonFloor fb = a.floor(Dungeon.BOSS_FLOOR);
            boolean bossType = BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.parse(d.boss));
            check(f, bossType, "the boss " + d.boss + " is not registered");
            if (bossType) {
                Vec3 bc = a.markCenter(Dungeon.BOSS_FLOOR, "boss");
                p.moveTo(bc.x, bc.y, bc.z + 6, 0f, 0f);
                fb.bossTick(level, level.getGameTime(), List.of(p));
                var boss = fb.boss == null ? null : level.getEntity(fb.boss);
                check(f, boss instanceof LivingEntity, "the boss did not appear");
                if (boss instanceof LivingEntity le) {
                    le.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
                }
                // 偽の人は写しの中の人に数えられないので、報酬は直に渡して確かめる
                if (fb.bossDown) {
                    Dungeons.reward(p, d);
                }
                check(f, fb.bossDown && a.cleared, "killing the boss did not clear the run");
                check(f, Dungeons.clears(p, d) == 1 && Dungeons.progress(p, d) == 0 && !p.getPersistentData().getString(Dungeons.BOX_DAY + d.id).isEmpty(),
                        "the clear reward was not recorded");
            }
            // ほかの三つ: 層を流して扉・仕掛け・主を確かめる
            for (Dungeon other : Dungeon.values()) {
                if (other != d) {
                    quickRun(f, server, level, other, q, runs);
                }
            }
            // 続きから: 続きのある人と無い人がいっしょに入ると、浅い方から
            p.getPersistentData().putInt(Dungeons.PROGRESS + d.id, 1);
            Dungeons.endForTest(a);
            runs.remove(a);
            check(f, Dungeons.runOf(p) == null, "the run did not end");
            DungeonRun c = Dungeons.startForTest(server, d, List.of(p));
            if (c != null) {
                runs.add(c);
                check(f, c.checkpoint == 1, "a player with progress did not start on level 2");
            }
            p.getPersistentData().remove(Dungeons.PROGRESS + d.id);
            p.getPersistentData().remove(Dungeons.CLEARS + d.id);
            p.getPersistentData().remove(Dungeons.BOX_DAY + d.id);
            rumors(f, server);
            gear(f);
        } catch (Exception ex) {
            SkyAbyss.LOGGER.error("SELFTEST dungeons crashed", ex);
            f.add("crashed: " + ex);
        } finally {
            for (DungeonRun r : runs) {
                Dungeons.endForTest(r);
            }
        }
        report(f, built);
    }

    // 一つのダンジョンを流して: 扉は閉じている・結晶を満たすと結晶の扉が開く・満ち引きと溶岩の箱が入れ替わる・主が出て倒れると越える
    static void quickRun(List<String> f, MinecraftServer server, ServerLevel level, Dungeon d, FakePlayer p, List<DungeonRun> runs) {
        p.getPersistentData().remove(Dungeons.PROGRESS + d.id);
        DungeonRun run = Dungeons.startForTest(server, d, List.of(p));
        if (run == null) {
            f.add(d.id + ": could not start");
            return;
        }
        runs.add(run);
        for (int k = 0; k < Dungeon.FLOORS.length; k++) {
            Dungeons.moveTo(p, run, k);
            DungeonFloor fl = run.floor(k);
            check(f, fl.ready() && run.floorOf(p) == k, d.terrain(k) + ": the level was not poured / entered");
            for (String lo : fl.terrain.marksStarting("door_")) {
                if (lo.endsWith("_lo")) {
                    check(f, level.getBlockState(fl.at(lo)).is(Blocks.IRON_BARS), d.terrain(k) + ": " + lo + " is not barred");
                }
            }
            // 結晶: 3 回打つと満ちる。扉の結晶がみな満ちたら扉が開く
            for (String c : fl.terrain.marksStarting("crystal_")) {
                for (int i = 0; i < Mechanics.CHARGE; i++) {
                    Mechanics.strike(fl, level, fl.at(c));
                }
                check(f, fl.charge.getOrDefault(c, 0) >= Mechanics.CHARGE, d.terrain(k) + ": " + c + " did not charge");
            }
            fl.doors(level, List.of(p));
            for (String lo : fl.terrain.marksStarting("door_")) {
                if (lo.endsWith("_crystal_lo")) {
                    check(f, fl.opened.contains(lo.substring(0, lo.length() - 3)), d.terrain(k) + ": the crystal door " + lo + " did not open");
                }
            }
            // 満ち引き・溶岩: 満たすと箱の下の段が液に、引くと空きに
            for (String lo : fl.terrain.marksStarting("tide_")) {
                if (lo.endsWith("_lo")) {
                    String box = lo.substring(0, lo.length() - 3);
                    Mechanics.set(fl, level, box, true, Blocks.WATER.defaultBlockState());
                    int wet = count(level, fl, box, Blocks.WATER);
                    Mechanics.set(fl, level, box, false, Blocks.WATER.defaultBlockState());
                    check(f, wet > 0 && count(level, fl, box, Blocks.WATER) == 0, d.terrain(k) + ": " + box + " did not flood and drain (" + wet + ")");
                }
            }
            for (String lo : fl.terrain.marksStarting("lava_")) {
                if (lo.endsWith("_lo")) {
                    String box = lo.substring(0, lo.length() - 3);
                    Mechanics.set(fl, level, box, true, Blocks.LAVA.defaultBlockState());
                    int hot = count(level, fl, box, Blocks.LAVA);
                    Mechanics.set(fl, level, box, false, Blocks.LAVA.defaultBlockState());
                    check(f, hot > 0 && count(level, fl, box, Blocks.LAVA) == 0, d.terrain(k) + ": " + box + " did not fill and clear (" + hot + ")");
                }
            }
        }
        DungeonFloor fb = run.floor(Dungeon.BOSS_FLOOR);
        Vec3 bc = run.markCenter(Dungeon.BOSS_FLOOR, "boss");
        p.moveTo(bc.x, bc.y, bc.z + 6, 0f, 0f);
        fb.bossTick(level, level.getGameTime(), List.of(p));
        var boss = fb.boss == null ? null : level.getEntity(fb.boss);
        check(f, boss instanceof jp.revati.skyabyss.dungeon.mob.DungeonBoss, d.id + ": the boss " + d.boss + " did not appear");
        if (boss instanceof LivingEntity le) {
            le.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
        }
        check(f, fb.bossDown && run.cleared, d.id + ": killing the boss did not clear the run");
        Dungeons.endForTest(run);
        runs.remove(run);
        p.getPersistentData().remove(Dungeons.PROGRESS + d.id);
    }

    static int count(ServerLevel level, DungeonFloor fl, String box, net.minecraft.world.level.block.Block b) {
        int n = 0;
        for (BlockPos q : BlockPos.betweenClosed(fl.at(box + "_lo"), fl.at(box + "_hi"))) {
            if (level.getBlockState(q).is(b)) {
                n++;
            }
        }
        return n;
    }

    // 装飾品 4 品 (DungeonGear): 装飾の枠の品として登録され、階級・必要 Lv・効き目・体に描く場所があり、メッシュ・32x32 の絵・文 (ja/en) がそろう。
    // 野の装備の表には入らない (落とし物・封の箱から出ない)
    static void gear(List<String> f) {
        java.util.Map<String, String> ja = jp.revati.skyabyss.SignFit.lang("ja_jp");
        java.util.Map<String, String> en = jp.revati.skyabyss.SignFit.lang("en_us");
        for (Dungeon d : Dungeon.values()) {
            var item = DungeonGear.item(d);
            String id = BuiltInRegistries.ITEM.getKey(item).getPath();
            check(f, item instanceof jp.revati.skyabyss.item.wild.WildAccessoryItem a && !a.row().charm() && !a.row().fx().isEmpty() && a.row().mount() != null
                    && jp.revati.skyabyss.item.GearLevel.required(item) == a.row().level(), id + " is not a working trinket");
            check(f, jp.revati.skyabyss.item.wild.WildGearTable.ACCESSORIES.stream().noneMatch(x -> x.id().equals(id)), id + " leaked into the wild gear table");
            String a = "/assets/" + SkyAbyss.MODID + "/";
            check(f, DungeonGear.class.getResource(a + "lowpoly/items/" + id + "/rare.json") != null && DungeonGear.class.getResource(a + "models/item_flat/" + id + ".json") != null
                    && DungeonGear.class.getResource(a + "textures/item/" + id + ".png") != null, id + " lacks its mesh, flat model or icon");
            check(f, !ja.getOrDefault("item.skyabyss." + id + ".desc", "").isEmpty() && !en.getOrDefault("item.skyabyss." + id + ".desc", "").isEmpty(), id + " lacks text");
        }
    }

    // 町の噂の頼み (quest/town/rumor_<id>): 入口に着く → 主を倒す → 案内人に知らせると終わって鐘銭が増える
    static final String[][] RUMORS = {{"bone_pit", "whale"}, {"tide_cave", "tide"}, {"storm_hollow", "storm"}, {"molten_mine", "ember"}};

    static void rumors(List<String> f, MinecraftServer server) {
        for (String[] r : RUMORS) {
            String qid = "town/rumor_" + r[0];
            String giver = "guest_town_guide_" + r[1];
            var def = jp.revati.skyabyss.quest.QuestManager.quest(qid);
            if (def == null) {
                f.add(qid + ": not loaded");
                continue;
            }
            check(f, ("skyabyss:" + giver).equals(def.giver()), qid + ": the giver is not " + giver);
            FakePlayer p = new FakePlayer(server.overworld(), new GameProfile(UUID.randomUUID(), "rumor_" + r[1]));
            jp.revati.skyabyss.quest.Quests.start(p, qid);
            check(f, "go".equals(jp.revati.skyabyss.town.TownGuests.stageOf(p, qid)), qid + ": did not start at go");
            jp.revati.skyabyss.quest.Quests.signal(p, "poi:dungeon_" + r[0]);
            check(f, "clear".equals(jp.revati.skyabyss.town.TownGuests.stageOf(p, qid)), qid + ": reaching the entrance did not advance");
            jp.revati.skyabyss.quest.Quests.signal(p, "dungeon:" + r[0]);
            check(f, "back".equals(jp.revati.skyabyss.town.TownGuests.stageOf(p, qid)), qid + ": the boss signal did not advance");
            int coins = jp.revati.skyabyss.facility.Coins.count(p);
            jp.revati.skyabyss.quest.Quests.signal(p, "town_hand:" + giver);
            var done = jp.revati.skyabyss.quest.QuestLog.of(p).entry(qid);
            check(f, done != null && done.done && jp.revati.skyabyss.facility.Coins.count(p) > coins, qid + ": reporting did not finish with bell coins");
            p.discard();
        }
    }

    // 層の印と敵の種
    static void marks(List<String> f, Dungeon d) {
        jp.revati.skyabyss.opening.site.Blueprint bp = null;
        try {
            bp = jp.revati.skyabyss.opening.site.Blueprint.get("poi_" + d.poiKind());
        } catch (RuntimeException ex) {
            f.add("poi_" + d.poiKind() + " cannot be read: " + ex.getMessage());
        }
        if (bp != null) {
            for (String m : List.of("gate", "back", "rest", "rumor", "tablet")) {
                check(f, bp.marks.containsKey(m), "poi_" + d.poiKind() + " lacks mark " + m);
            }
        }
        check(f, jp.revati.skyabyss.world.continent.ContinentPois.placeOf(d.poiKind()) != null, d.poiKind() + " is not in pois.json");
        for (int k = 0; k < Dungeon.FLOORS.length; k++) {
            TowerTerrain t;
            try {
                t = DungeonWorld.terrain(d, k);
            } catch (RuntimeException ex) {
                f.add(d.terrain(k) + " cannot be read: " + ex.getMessage());
                continue;
            }
            List<String> need = k == Dungeon.BOSS_FLOOR ? List.of("entry", "boss", "exit") : List.of("entry", "leave", "cp", "next");
            for (String m : need) {
                check(f, t.has(m), d.terrain(k) + " lacks mark " + m);
            }
            for (String lo : t.marksStarting("door_")) {
                if (lo.endsWith("_lo")) {
                    check(f, t.has(lo.substring(0, lo.length() - 3) + "_hi"), d.terrain(k) + " door " + lo + " has no _hi");
                }
            }
            for (String m : t.marksStarting("foe_")) {
                String[] p = m.split("_", 4);
                check(f, p.length == 4, d.terrain(k) + " bad foe mark " + m);
                if (p.length < 4) {
                    continue;
                }
                String kind = p[3].endsWith("_key") ? p[3].substring(0, p[3].length() - 4) : p[3];
                String id = kind.equals("mid") ? d.midBoss : d.foes.get(kind);
                check(f, id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.parse(id)), d.terrain(k) + " foe kind " + kind + " has no entity");
            }
        }
    }

    static void report(List<String> f, int built) {
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST dungeons PASSED ({} dungeons built; entrances, copies, rooms, key / mid / crystal doors, tide and lava boxes, traps, checkpoints, four bosses, clear reward)", built);
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST dungeons FAILED: {}", m));
        }
    }
}
