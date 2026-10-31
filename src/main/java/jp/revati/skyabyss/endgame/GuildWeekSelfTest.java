package jp.revati.skyabyss.endgame;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.guild.GuildData;
import jp.revati.skyabyss.facility.guild.Guilds;
import jp.revati.skyabyss.world.continent.ContinentMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 組合の週 (GuildWeek) の自己テスト。SKYABYSS_SELFTEST_ONLY="guild week"
//   土地の点が組合ごとに数わる / 週が替わると一番の組合が旗を持ち、塔の上位が誉れになる / 倉庫に週の品 / 保存と読み戻し
public final class GuildWeekSelfTest {
    private GuildWeekSelfTest() {
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        FakePlayer a = FakePlayerFactory.get(server.overworld(), new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-0000000c3a01"), "week_test_a"));
        FakePlayer b = FakePlayerFactory.get(server.overworld(), new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-0000000c3a02"), "week_test_b"));
        GuildWeek w = GuildWeek.get(server);
        try {
            Guilds.leave(a);
            Guilds.leave(b);
            check(f, Guilds.create(a, "旗取りの組", "FLG", 1, 2) == Guilds.Result.OK, "the flag guild was not created");
            check(f, Guilds.create(b, "塔登りの組", "TWR", 3, 4) == Guilds.Result.OK, "the tower guild was not created");
            GuildData.Guild ga = Guilds.of(a);
            GuildData.Guild gb = Guilds.of(b);
            w.roll(server);
            w.points.clear();
            w.tower.clear();
            RegionPoints.add(a, ContinentMap.WHALE, 9);
            RegionPoints.add(b, ContinentMap.WHALE, 4);
            RegionPoints.add(b, ContinentMap.TIDE, 2);
            check(f, w.points.get(ContinentMap.WHALE).getOrDefault(ga.id, 0) == 9 && w.points.get(ContinentMap.WHALE).getOrDefault(gb.id, 0) == 4,
                    "region points were not counted per guild");
            GuildWeek.towerClear(server, gb.id, gb.name);
            GuildWeek.towerClear(server, gb.id, gb.name);
            GuildWeek.towerClear(server, ga.id, ga.name);
            w.settleForTest(server);
            check(f, ga.id.equals(w.flag(ContinentMap.WHALE)) && gb.id.equals(w.flag(ContinentMap.TIDE)) && w.flag(ContinentMap.STORM) == null,
                    "the flags went to the wrong guilds");
            check(f, GuildWeek.holdsFlag(a, ContinentMap.WHALE) && !GuildWeek.holdsFlag(b, ContinentMap.WHALE), "holding a flag is wrong");
            check(f, w.honor(gb.id) == 1 && w.honor(ga.id) == 2, "the tower honors are wrong");
            int shards = 0;
            int boxes = 0;
            for (ItemStack s : ga.storage) {
                if (s.getItem().builtInRegistryHolder().key().location().toString().equals(jp.revati.skyabyss.tower.gear.TowerGear.SHARD)) {
                    shards += s.getCount();
                } else if (!s.isEmpty()) {
                    boxes += s.getCount();
                }
            }
            check(f, shards == GuildWeek.FLAG_SHARDS + GuildWeek.TOWER_SHARDS[1] && boxes == 1, "the flag guild's storage got " + shards + " shards, " + boxes + " boxes");
            GuildWeek back = GuildWeek.load(w.save(new net.minecraft.nbt.CompoundTag(), server.registryAccess()), server.registryAccess());
            check(f, ga.id.equals(back.flag(ContinentMap.WHALE)) && back.honor(gb.id) == 1, "the guild week was not saved");
        } catch (RuntimeException ex) {
            f.add("guild week test crashed: " + ex);
            SkyAbyss.LOGGER.error("guild week test", ex);
        } finally {
            if (Guilds.of(a) != null) {
                Guilds.disband(a);
            }
            if (Guilds.of(b) != null) {
                Guilds.disband(b);
            }
            w.flags.clear();
            w.honor.clear();
            w.points.clear();
            w.tower.clear();
            w.setDirty();
            for (FakePlayer p : List.of(a, b)) {
                p.getPersistentData().remove(RegionPoints.FAME);
                p.getPersistentData().remove(RegionPoints.WEEK);
            }
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST guild week PASSED (points per guild, flags, tower honors, storage goods, save)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST guild week FAILED: {}", m));
        }
    }
}
