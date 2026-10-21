package jp.revati.skyabyss.opening.site;

import java.util.List;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.npc.NpcBonds;
import jp.revati.skyabyss.npc.NpcSetup;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.tablet.Tablets;
import jp.revati.skyabyss.util.Players;

// 都の閉じた施設が、章の進みで開く (docs/design/33 の 2.2「都が目を覚ます順」)。
//   組合 = イサナと会う (訓練場ははじめから開いている。docs/design/35 の 3.1) /
//   社と砦の本丸 = 心臓を二つ止めた (三章の始まり) / 館 = 心臓を三つ止めた (四章の始まり)
// 世界で一度: 誰かが段に着いたとき、鉄の戸と鉄格子を外し、碑の文を「開いた」文にする。記録は宿りの場所の extra ("opened_<施設>")。
// (章は人ごとだが、都は一つの共有の場所なので、最初に着いた人が開ける。閉じたままの人にだけ閉じて見せる上書きは背の仕組みに
//  限られているので、ここでは世界の状態にする)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class CapitalDoors {
    private CapitalDoors() {
    }

    public record Facility(String id, List<String> marks, Predicate<ServerPlayer> ready) {
    }

    public static final List<Facility> ALL = List.of(
            new Facility("guild", List.of("open_guild"), CapitalDoors::metIsana),
            new Facility("shrine", List.of("open_shrine"), p -> Progression.stage(p) >= 2),
            new Facility("keep", List.of("open_keep", "open_keep2"), p -> Progression.stage(p) >= 2),
            new Facility("hall", List.of("open_hall"), p -> Progression.stage(p) >= 3));

    // 戸のまわりを探す範囲 (印から水平 ±4、上 6)
    private static final int REACH = 4;
    private static final int HEIGHT = 6;

    static boolean metIsana(ServerPlayer p) {
        NpcBonds.Bond bond = NpcBonds.of(p).bonds().get(NpcSetup.WHALE_CHASER_ID);
        return bond != null && (bond.talks() > 0 || bond.flags().contains("met") || bond.echoes().contains("met"));
    }

    public static boolean opened(MinecraftServer server, String id) {
        SiteData.Entry e = SiteData.get(server.overworld()).first("shelter");
        return e != null && e.extra("opened_" + id) != null;
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 40 != 7) {
            return;
        }
        ServerLevel level = server.overworld();
        SiteData data = SiteData.get(level);
        SiteData.Entry e = data.first("shelter");
        if (e == null || !e.built) {
            return;
        }
        List<ServerPlayer> players = Players.snapshot(server.getPlayerList().getPlayers());
        if (players.isEmpty()) {
            return;
        }
        BlockPos bell = e.extra("bell");
        if (bell != null) {
            jp.revati.skyabyss.npc.NpcSpawner.keepCapitalStoryteller(level, bell);
        }
        for (Facility f : ALL) {
            if (e.extra("opened_" + f.id()) != null) {
                continue;
            }
            ServerPlayer by = null;
            for (ServerPlayer p : players) {
                if (!p.isSpectator() && f.ready().test(p)) {
                    by = p;
                    break;
                }
            }
            if (by == null) {
                continue;
            }
            if (open(level, e, f)) {
                e.extra("opened_" + f.id(), e.pos);
                data.setDirty();
                SkyAbyss.LOGGER.info("Capital: the {} opened (reached by {})", f.id(), by.getGameProfile().getName());
                for (ServerPlayer p : players) {
                    Notice.narrate(p, "message.skyabyss.capital.opened." + f.id());
                }
            }
        }
    }

    // 戸を外す。印のチャンクが読まれていなければ false (次の見回りで)
    static boolean open(ServerLevel level, SiteData.Entry e, Facility f) {
        boolean any = false;
        for (String m : f.marks()) {
            BlockPos at = e.extra(m);
            if (at == null) {
                continue;
            }
            if (!level.isLoaded(at)) {
                return false;
            }
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int dx = -REACH; dx <= REACH; dx++) {
                for (int dz = -REACH; dz <= REACH; dz++) {
                    for (int dy = -1; dy <= HEIGHT; dy++) {
                        p.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
                        BlockState st = level.getBlockState(p);
                        if (st.is(Blocks.IRON_DOOR) || st.is(Blocks.IRON_BARS)) {
                            level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                            any = true;
                        }
                    }
                }
            }
            level.playSound(null, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1f, 0.7f);
        }
        BlockPos sign = e.extra("sign_" + f.id());
        if (sign != null && level.isLoaded(sign) && level.getBlockState(sign).is(ModBlocks.TABLET.get())) {
            Tablets.write(level, sign, "sign.skyabyss.capital." + f.id() + "_open");
        }
        return any || f.marks().stream().allMatch(m -> e.extra(m) == null);
    }

    // 開発・自己テスト用: 施設を今すぐ開ける
    public static boolean force(MinecraftServer server, String id) {
        ServerLevel level = server.overworld();
        SiteData data = SiteData.get(level);
        SiteData.Entry e = data.first("shelter");
        if (e == null) {
            return false;
        }
        for (Facility f : ALL) {
            if (f.id().equals(id) && open(level, e, f)) {
                e.extra("opened_" + f.id(), e.pos);
                data.setDirty();
                return true;
            }
        }
        return false;
    }
}
