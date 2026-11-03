package jp.revati.skyabyss.story.ch1;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.world.continent.ContinentLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 鉄の跡 (IronTrail) の自己テスト。段の名前は "iron trail"。
//  - 鉄くずの置き場は三つで、町から坑へ近づく順に並ぶ (町か坑がない世界は確かめるものがない)
//  - 建てると欠けた金床が地面に置かれる
//  - 見つけると一つずつ数え、同じものは二度数えない。三つで段 track が済み、段 pit へ
public final class IronTrailSelfTest {
    private IronTrailSelfTest() {
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        List<BlockPos> spots = IronTrail.spots(server);
        BlockPos town = ContinentLayout.get().town("whale");
        BlockPos pit = IronTrail.pit(server, town);
        if (town == null || pit == null) {
            SkyAbyss.LOGGER.info("SELFTEST ch1 iron trail PASSED (no whale town or bone pit in this world; nothing to check)");
            return;
        }
        if (spots.size() != 3) {
            f.add("expected 3 scraps, got " + spots.size());
        }
        double last = -1;
        for (BlockPos s : spots) {
            double d = Math.hypot(s.getX() - town.getX(), s.getZ() - town.getZ());
            if (d <= last) {
                f.add("the scraps are not ordered from the town toward the pit");
            }
            last = d;
        }
        // 建てる (空の高い所の床の上で、置き場の形だけ確かめる)
        BlockPos floor = level.getSharedSpawnPos().atY(240);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 12; z++) {
                level.setBlock(floor.offset(x, -1, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            }
        }
        BlockPos plate = IronTrail.build(level, floor, floor.offset(0, 0, 40));
        if (!level.getBlockState(plate).is(Blocks.DAMAGED_ANVIL)) {
            f.add("building a scrap did not put the broken anvil on the ground (" + level.getBlockState(plate) + ")");
        }
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 12; z++) {
                level.setBlock(floor.offset(x, -1, z), Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(floor.offset(x, 0, z), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        // 見つける
        FakePlayer p = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-0000000e1a01"), "iron_trail"));
        Lore.reset(p);
        Quests.forgetAll(p);
        IronTrail.forget(p);
        Quests.start(p, IronTrail.QUEST);
        Quests.poll(p, true);
        if (!IronTrail.tracking(p)) {
            f.add("the iron trail quest did not start at the track stage");
        }
        boolean a = IronTrail.find(p, 0);
        boolean again = IronTrail.find(p, 0);
        IronTrail.find(p, 2);
        IronTrail.find(p, 1);
        Quests.poll(p, true);
        if (!a || again) {
            f.add("a scrap was not counted once (" + a + ", again " + again + ")");
        }
        if (IronTrail.tracking(p)) {
            f.add("three scraps did not finish the track stage");
        }
        QuestLog.Entry e = QuestLog.of(p).entry(IronTrail.QUEST);
        if (e == null || e.done) {
            f.add("the iron trail quest ended instead of moving to the pit");
        }
        IronTrail.forget(p);
        Quests.forgetAll(p);
        f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST ch1 iron trail FAILED: {}", m));
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST ch1 iron trail PASSED (3 scraps from the town to the bone pit, broken anvil placed, each counted once, track stage done)");
        }
    }
}
