package jp.revati.skyabyss.trial.raid;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.lore.LoreManager;
import jp.revati.skyabyss.SignFit;
import jp.revati.skyabyss.tablet.TabletBlockEntity;
import jp.revati.skyabyss.tablet.Tablets;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// レイドの部屋の物語 (RaidLore)。SKYABYSS_SELFTEST_ONLY="raid lore"
// 十二の部屋をそれぞれ遠くの空に建て、入口の間に碑があり文のキーが刻まれているか、心の声の二行と碑の四行の文があるか、
// 碑を読む (右クリックと同じ Tablets.read) と碑文の伝承が記されるか、越えたとき (RaidLore.onCleared) にも記されるかを見る
public final class RaidLoreSelfTest {
    private RaidLoreSelfTest() {
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        var ja = SignFit.lang("ja_jp");
        FakePlayer reader = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("5c1e0a2b-0000-4000-8000-00000000ad11"), "[raid_lore_a]"));
        FakePlayer clearer = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("5c1e0a2b-0000-4000-8000-00000000ad12"), "[raid_lore_b]"));
        int i = 0;
        try {
            for (var e : RaidLore.SIGNS.entrySet()) {
                String id = e.getKey();
                String key = e.getValue();
                String loreId = "chapter1/inscription_" + key.substring(key.lastIndexOf("lore_") + 5);
                if (LoreManager.catalog().entry(loreId) == null) {
                    f.add(id + ": no lore entry " + loreId);
                }
                for (int n = 1; n <= 4; n++) {
                    if (ja.getOrDefault(key + "." + n, "").isEmpty()) {
                        f.add(id + ": inscription line " + key + "." + n + " is missing");
                    }
                }
                for (int n = 1; n <= 2; n++) {
                    if (ja.getOrDefault("message.skyabyss.raid_lore." + id + "." + n, "").isEmpty()) {
                        f.add(id + ": inner voice line " + n + " is missing");
                    }
                }
                RaidRoom room = RaidRooms.create(id);
                if (room == null) {
                    f.add(id + ": not registered");
                    continue;
                }
                UUID group = UUID.nameUUIDFromBytes(("raid lore " + id).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                RaidCtx ctx = new RaidCtx(level, group, "test", id, 0, 1, new BlockPos(29000 + 96 * i++, 230, 29000), 7L);
                try {
                    room.build(ctx);
                    if (ctx.cell() == null) {
                        f.add(id + ": no cell");
                        continue;
                    }
                    BlockPos at = RaidLore.tabletPos(ctx.cell());
                    if (!(level.getBlockEntity(at) instanceof TabletBlockEntity t) || !key.equals(t.key())) {
                        f.add(id + ": the entrance has no inscription " + key + " at " + at.toShortString());
                    } else if (level.getBlockState(at.west()).isAir()) {
                        f.add(id + ": the inscription is not hung on the entrance wall");
                    }
                    if (at.distSqr(ctx.cell().rest()) > 16) {
                        f.add(id + ": the inscription is too far from the resting spot");
                    }
                    // 読む (右クリック = TabletBlock.useWithoutItem → Tablets.read) → 伝承に記される
                    Lore.lock(reader, loreId);
                    Tablets.read(reader, key);
                    if (!Lore.state(reader).has(loreId)) {
                        f.add(id + ": reading the inscription did not record " + loreId);
                    }
                    // 越えた → 読まなかった人にも記される
                    Lore.lock(clearer, loreId);
                    RaidLore.onCleared(clearer, id);
                    if (!Lore.state(clearer).has(loreId)) {
                        f.add(id + ": clearing the room did not record " + loreId);
                    }
                } finally {
                    try {
                        room.end(ctx);
                    } catch (RuntimeException ignored) {
                        // 片付けの失敗は数えない
                    }
                    ctx.clearMobs();
                    RaidKit.clear(level, ctx.cell() == null ? new net.minecraft.world.phys.AABB(ctx.origin()) : ctx.cell().bounds());
                    RaidKit.release(level, group);
                }
            }
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST raid lore crashed", e);
            f.add("crashed: " + e);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST raid lore PASSED ({} rooms: entrance inscription placed and keyed, 4 inscription lines + 2 inner voice lines, "
                    + "reading records the lore entry, clearing records it too)", RaidLore.SIGNS.size());
        } else {
            f.forEach(x -> SkyAbyss.LOGGER.error("SELFTEST raid lore FAILED: {}", x));
        }
    }
}
