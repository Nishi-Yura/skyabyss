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
import net.minecraft.world.item.ItemStack;
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
//   小ダンジョン 六つ (MiniDungeons): 部屋 3 つの層と主の間。部屋に寄ると敵が出て、その部屋の敵がみな倒れるまで扉は閉じ、倒れると開く。
//              荷・温石の台・碑。主は毎回出て (名づき・小ダンジョンの印)、倒すと越えて、章の場所のクリア (SiteSpawner.credit) が数えられる
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
            check(f, jp.revati.skyabyss.world.LodestoneLabels.describe(p, a.mark(0, "leave")) != null
                    && jp.revati.skyabyss.world.LodestoneLabels.describe(p, a.mark(0, "leave").above(3)) == null, "the return stone has no name tag (or a plain spot got one)");
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
            // 鍵持ちを倒して扉へ寄ると開く (鍵の日 DungeonMood.KEYS は鍵持ちが二体なので、いる分を全部倒す)
            Vec3 rb = a.markCenter(0, "room_b");
            p.moveTo(rb.x, rb.y, rb.z, 0f, 0f);
            f0.wakeRooms(level, List.of(p));
            List<Mob> keys = level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(40), m -> m.getTags().contains(Dungeons.KEY_TAG) && m.getTags().contains(a.foeTag()));
            check(f, keys.size() >= f0.keyNeed, "too few key bearers in room b (" + keys.size() + " of " + f0.keyNeed + ")");
            for (Mob key : keys) {
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
            for (Dungeon other : Dungeon.mids()) {
                if (other != d) {
                    quickRun(f, server, level, other, q, runs);
                }
            }
            for (Dungeon mini : Dungeon.minis()) {
                miniRun(f, server, level, mini, q, runs);
            }
            // 深く潜る・坑の様子・育つ装飾品 (2026-11-05 のダンジョンの頁)
            deepAndMoods(f, server, level, q, runs);
            // 続きから: 続きのある人と無い人がいっしょに入ると、浅い方から
            p.getPersistentData().putInt(Dungeons.PROGRESS + d.id, 1);
            Dungeons.endForTest(a);
            runs.remove(a);
            check(f, Dungeons.runOf(p) == null, "the run did not end");
            DungeonRun c = Dungeons.startForTest(server, d, List.of(p));
            if (c != null) {
                runs.add(c);
                check(f, c.checkpoint == 1, "a player with progress did not start on level 2");
                // 全滅: 最初の層から (層は作り直し、続きの記録も 0)。立っている人がいれば全滅ではない
                check(f, !Dungeons.wiped(List.of(p), c), "a standing player counted as a wipe");
                DungeonFloor old0 = c.floor(0);
                c.floor(0).woke.add("a");
                Dungeons.wipe(level, c);
                check(f, c.checkpoint == 0 && c.wipes == 1 && c.floor(0) != old0 && c.floor(0).woke.isEmpty() && c.floor(1).poured < 0,
                        "a wipe did not rebuild the run from the first floor (checkpoint " + c.checkpoint + ")");
                p.getPersistentData().putInt(Dungeons.PROGRESS + d.id, 0);
                // 起きた部屋に敵が残っていれば、帯に「敵 残り n」
                Dungeons.moveTo(p, c, 0);
                Vec3 rw = c.markCenter(0, "room_a");
                p.moveTo(rw.x, rw.y, rw.z, 0f, 0f);
                c.floor(0).wakeRooms(level, List.of(p));
                int near = c.floor(0).leftNear(level, p);
                check(f, near > 0 && near == c.floor(0).roomLeft(level, "a"), "the room count for the band is wrong (" + near + ")");
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

    static void deepAndMoods(List<String> f, MinecraftServer server, ServerLevel level, FakePlayer p, List<DungeonRun> runs) {
        // 坑の様子: 一つのダンジョンは日ごとに四つを巡り、同じ日の四つのダンジョンはみな違う
        java.util.Set<DungeonMood> days = java.util.EnumSet.noneOf(DungeonMood.class);
        java.util.Set<DungeonMood> same = java.util.EnumSet.noneOf(DungeonMood.class);
        for (int i = 0; i < 4; i++) {
            days.add(DungeonMood.of(1000 + i, Dungeon.BONE_PIT));
            same.add(DungeonMood.of(1000, Dungeon.values()[i]));
        }
        check(f, days.size() == 4 && same.size() == 4, "dungeon moods do not rotate by day and dungeon");
        // 主の箱の階級: ふつう レア/エピック、深く潜ると一つ上
        check(f, Dungeons.boxTier(Dungeon.BONE_PIT, false) == jp.revati.skyabyss.item.GearTier.RARE && Dungeons.boxTier(Dungeon.BONE_PIT, true) == jp.revati.skyabyss.item.GearTier.EPIC
                && Dungeons.boxTier(Dungeon.MOLTEN_MINE, false) == jp.revati.skyabyss.item.GearTier.EPIC && Dungeons.boxTier(Dungeon.MOLTEN_MINE, true) == jp.revati.skyabyss.item.GearTier.HEROIC,
                "the deep boss box is not one tier up");
        p.getPersistentData().remove(Dungeons.PROGRESS + Dungeon.BONE_PIT.id);
        DungeonRun run = Dungeons.startForTest(server, Dungeon.BONE_PIT, List.of(p), true);
        if (run == null) {
            f.add("a deep run did not start");
            return;
        }
        runs.add(run);
        DungeonFloor f0 = run.floor(0);
        check(f, run.deep && Mechanics.tidePeriod(f0) == Mechanics.DEEP_TIDE_PERIOD && Mechanics.lavaPeriod(f0) == Mechanics.DEEP_LAVA_PERIOD
                && Mechanics.charge(f0) == Mechanics.DEEP_CHARGE, "a deep run does not speed up the mechanisms");
        // 深く潜った敵: 体力 +40% の修飾子と印、傷 +40%
        run.mood = DungeonMood.KEYS;
        String keyMark = null;
        for (List<String> marks : f0.roomFoes.values()) {
            for (String m : marks) {
                if (m.endsWith("_key")) {
                    keyMark = m;
                }
            }
        }
        if (keyMark == null) {
            f.add("bone pit level 1 has no key bearer");
            return;
        }
        net.minecraft.world.entity.Mob key = f0.spawnFoe(level, keyMark);
        check(f, key != null && key.getTags().contains(Dungeons.DEEP_TAG)
                && key.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).getModifier(Dungeons.DEEP_HEALTH) != null
                && key.getHealth() == key.getMaxHealth(), "a deep foe did not get +40% health");
        long bearers = level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new net.minecraft.world.phys.AABB(f0.at(keyMark)).inflate(6),
                m -> m.getTags().contains(Dungeons.KEY_TAG) && m.getTags().contains(run.foeTag())).size();
        check(f, bearers == 2 && f0.keyNeed == 2, "a key day did not bring two key bearers (" + bearers + ")");
        // 宝の日: 主の間に宝箱がもう一つ
        DungeonFloor fb = run.floor(Dungeon.BOSS_FLOOR);
        fb.pourNow(level);
        fb.treasure(level);
        check(f, level.getBlockState(fb.at("boss")).is(net.minecraft.world.level.block.Blocks.CHEST), "a treasure day did not leave a chest in the boss hall");
        // 育つ装飾品: 5 回で II (×1.25)、15 回で III (×1.5)。深く潜ると 2 回ぶん
        ItemStack trinket = new ItemStack(DungeonGear.item(Dungeon.BONE_PIT));
        DungeonGear.setClears(trinket, 1);
        p.getInventory().clearContent();
        p.getInventory().add(trinket);
        DungeonGear.grow(p, Dungeon.BONE_PIT, 2);
        DungeonGear.grow(p, Dungeon.BONE_PIT, 2);
        ItemStack held = p.getInventory().getItem(0);
        check(f, DungeonGear.clears(held) == 5 && DungeonGear.stage(held) == 1 && DungeonGear.scale(held) == 1.25, "a trinket did not grow to II at 5 clears ("
                + DungeonGear.clears(held) + ")");
        var row = ((jp.revati.skyabyss.item.wild.WildAccessoryItem) held.getItem()).row();
        var scaled = DungeonGear.scaled(held, row.fx());
        check(f, Math.abs(scaled.get(0).p(0) - row.fx().get(0).p(0) * 1.25) < 1e-9, "a grown trinket did not scale its numbers");
        DungeonGear.setClears(held, 15);
        check(f, DungeonGear.stage(held) == 2 && DungeonGear.scale(held) == 1.5, "a trinket at 15 clears is not III");
        p.getInventory().clearContent();
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
        for (int k = 0; k < d.floors().length; k++) {
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
        DungeonFloor fb = run.floor(d.bossFloor());
        Vec3 bc = run.markCenter(d.bossFloor(), "boss");
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

    // 小ダンジョン一つを通す (部屋 A〜C の全滅の扉・荷・主・章の場所のクリア)
    static void miniRun(List<String> f, MinecraftServer server, ServerLevel level, Dungeon d, FakePlayer p, List<DungeonRun> runs) {
        jp.revati.skyabyss.guide.SiteProgressData progress = jp.revati.skyabyss.guide.SiteProgressData.get(server);
        progress.forget(p.getUUID(), d.id);
        DungeonRun run = Dungeons.startForTest(server, d, List.of(p));
        if (run == null) {
            f.add(d.id + ": could not start");
            return;
        }
        runs.add(run);
        check(f, run.checkpoint == 0 && run.mood == null && !run.deep, d.id + ": a mini run should start on its first level with no mood");
        Dungeons.moveTo(p, run, 0);
        DungeonFloor f0 = run.floor(0);
        check(f, f0.ready() && run.floorOf(p) == 0, d.terrain(0) + ": the level was not poured / entered");
        check(f, level.getBlockState(run.mark(0, "leave")).is(Blocks.LODESTONE), d.terrain(0) + ": no return stone");
        String[] rooms = {"a", "b", "c"};
        for (int i = 0; i < rooms.length; i++) {
            String room = rooms[i];
            String door = "door_" + (i + 1) + "_clear" + room;
            if (!f0.terrain.has(door + "_lo")) {
                f.add(d.terrain(0) + ": lacks " + door);
                continue;
            }
            check(f, bars(level, f0, door) > 0, d.terrain(0) + ": " + door + " is not barred");
            Vec3 rc = run.markCenter(0, "room_" + room);
            p.moveTo(rc.x, rc.y, rc.z, 0f, 0f);
            f0.doors(level, List.of(p));
            check(f, !f0.opened.contains(door), d.terrain(0) + ": " + door + " opened before room " + room + " woke");
            f0.wakeRooms(level, List.of(p));
            List<Mob> foes = level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(48), m -> m.isAlive() && m.getTags().contains(run.foeTag()));
            int want = f0.roomFoes.getOrDefault(room, List.of()).size();
            check(f, f0.woke.contains(room) && want >= 3 && foes.size() >= want, d.terrain(0) + ": room " + room + " did not wake (" + foes.size() + " of " + want + ")");
            f0.doors(level, List.of(p));
            check(f, !f0.opened.contains(door), d.terrain(0) + ": " + door + " opened with foes alive");
            for (Mob m : foes) {
                m.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
            }
            f0.doors(level, List.of(p));
            check(f, f0.opened.contains(door) && bars(level, f0, door) == 0, d.terrain(0) + ": " + door + " did not open after room " + room + " fell");
        }
        if (f0.terrain.has("pack")) {
            check(f, level.getBlockState(run.mark(0, "pack")).is(Blocks.BARREL) && MiniDungeons.pack(p, run, 0, run.mark(0, "pack")), d.terrain(0) + ": the old pack does not open");
        }
        if (d == Dungeon.MINI_FORGE_PIT) {
            check(f, f0.terrain.has("warm_stand") && level.getBlockState(run.mark(0, "warm_stand")).is(Blocks.SMOOTH_BASALT)
                    && MiniDungeons.pack(p, run, 0, run.mark(0, "warm_stand")), d.terrain(0) + ": the warm stand is missing");
        }
        check(f, f0.terrain.has("chest_1") && level.getBlockEntity(run.mark(0, "chest_1")) instanceof ChestBlockEntity, d.terrain(0) + ": no chest in room b");
        // 主の間
        int bf = d.bossFloor();
        Dungeons.moveTo(p, run, bf);
        DungeonFloor fb = run.floor(bf);
        check(f, fb.ready() && run.floorOf(p) == bf, d.terrain(bf) + ": the master's hall was not poured / entered");
        for (String k : fb.terrain.marksStarting("tablet:")) {
            check(f, level.getBlockState(fb.at(k)).getBlock() instanceof jp.revati.skyabyss.tablet.TabletBlock, d.terrain(bf) + ": " + k + " is not a tablet");
        }
        Vec3 bc = run.markCenter(bf, "boss");
        p.moveTo(bc.x, bc.y, bc.z + 6, 0f, 0f);
        fb.bossTick(level, level.getGameTime(), List.of(p));
        var boss = fb.boss == null ? null : level.getEntity(fb.boss);
        check(f, boss instanceof LivingEntity && boss.getTags().contains(MiniDungeons.BOSS_TAG)
                && BuiltInRegistries.ENTITY_TYPE.getKey(boss.getType()).toString().equals(d.boss), d.id + ": the master " + d.boss + " did not appear");
        if (boss instanceof LivingEntity le) {
            le.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
        }
        check(f, fb.bossDown && run.cleared, d.id + ": killing the master did not clear the run");
        // 偽の人は写しの中の人に数えられないので、越えた報いは直に渡して確かめる
        MiniDungeons.reward(run, level, List.of(p));
        check(f, progress.cleared(p.getUUID(), d.id), d.id + ": the clear did not count for the chapter site");
        Dungeons.endForTest(run);
        runs.remove(run);
        progress.forget(p.getUUID(), d.id);
    }

    // 扉の箱の中の鉄格子の数
    static int bars(ServerLevel level, DungeonFloor fl, String door) {
        int n = 0;
        for (BlockPos q : BlockPos.betweenClosed(fl.at(door + "_lo"), fl.at(door + "_hi"))) {
            if (level.getBlockState(q).is(Blocks.IRON_BARS)) {
                n++;
            }
        }
        return n;
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
        for (Dungeon d : Dungeon.mids()) {
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
        if (d.mini) {
            miniMarks(f, d);
            return;
        }
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
        for (int k = 0; k < d.floors().length; k++) {
            TowerTerrain t;
            try {
                t = DungeonWorld.terrain(d, k);
            } catch (RuntimeException ex) {
                f.add(d.terrain(k) + " cannot be read: " + ex.getMessage());
                continue;
            }
            List<String> need = k == d.bossFloor() ? List.of("entry", "boss", "exit") : List.of("entry", "leave", "cp", "next");
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

    // 小ダンジョンの層の印: 部屋の層に entry・leave・cp・next・room_a..c、主の間に entry・boss・exit。敵の種は表にある
    static void miniMarks(List<String> f, Dungeon d) {
        for (int k = 0; k < d.floors().length; k++) {
            TowerTerrain t;
            try {
                t = DungeonWorld.terrain(d, k);
            } catch (RuntimeException ex) {
                f.add(d.terrain(k) + " cannot be read: " + ex.getMessage());
                continue;
            }
            List<String> need = k == d.bossFloor() ? List.of("entry", "boss", "exit") : List.of("entry", "leave", "cp", "next", "room_a", "room_b", "room_c");
            for (String m : need) {
                check(f, t.has(m), d.terrain(k) + " lacks mark " + m);
            }
            for (String m : t.marksStarting("foe_")) {
                String[] p = m.split("_", 4);
                String id = p.length == 4 ? d.foes.get(p[3]) : null;
                check(f, id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.parse(id)), d.terrain(k) + " foe mark " + m + " has no entity");
            }
        }
        check(f, d.boss != null && BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.parse(d.boss)), d.id + ": the master " + d.boss + " is not registered");
    }

    static void report(List<String> f, int built) {
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST dungeons PASSED ({} dungeons built; entrances, copies, rooms, key / mid / crystal doors, tide and lava boxes, traps, checkpoints, four bosses, clear reward)", built);
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST dungeons FAILED: {}", m));
        }
    }
}
