package jp.revati.skyabyss.npc;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.opening.site.SiteData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 旅の職人 (P4) をどこに立たせるか。データで決める: 章ごとの「立つ所」の表 (Spot) に、場所の ID と部品の印を書くだけ。
//   章が開いた人の拠点の場所 (SiteData の章の小要素) のうち、siteId が Spot.siteId で、印 Spot.mark が付いているものが立つ所。
//   その場所の近く (48 ブロック) に Spot.gate を満たす人が来たら、まだいなければ立たせる (家 = その印、まわり 3 ブロックを歩く)。
//   装いは章 (NpcEntity.variant = 1〜4。client/npc/NpcRenderer が絵を替える)。同じ人 (役) で、前のことは覚えていない (会話は dialogue/artisan.json)。
//   いなくなったら (コマンドで消した等)、立たせてから 1 日たてば立て直す (NpcSpawner と同じ)。マルチでは、場所は拠点ごとなので、近くの人が共有する。
//
// 章の担当 (P11・P15・P19) は、自分の章の部品に印 (例 "artisan") を付けて場所の記録の extra に残し、ここへ Spot を register する (一章は下の static)。
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ArtisanSpawner {
    // chapter: 1〜4 (装いと variant)、siteId: 章の小要素の ID、mark: 場所の extra の名前、gate: その人にもう立たせてよいか
    public record Spot(int chapter, String siteId, String mark, Predicate<ServerPlayer> gate) {
    }

    private static final int CHECK_INTERVAL = 100;
    private static final double NEAR = 48;
    private static final double LIVE_RADIUS = 24;
    private static final long RESPAWN_AFTER = 24000;
    private static final List<Spot> SPOTS = new CopyOnWriteArrayList<>();

    static {
        // 一章: 失せ物の荷車 (ch1_lost_cart) の傍らの道 (印 "artisan")。イサナと会ったあとに立つ (「日 2 の朝、イサナの話のあとに職人の荷が見つかる」)
        register(new Spot(1, "ch1_lost_cart", "artisan", p -> Lore.state(p).has("chapter1/whale_chaser")));
    }

    private ArtisanSpawner() {
    }

    public static void register(Spot spot) {
        SPOTS.removeIf(s -> s.chapter() == spot.chapter());
        SPOTS.add(spot);
    }

    // 自己テスト用: 章の立つ所を外す
    public static void unregister(int chapter) {
        SPOTS.removeIf(s -> s.chapter() == chapter);
    }

    public static List<Spot> spots() {
        return List.copyOf(SPOTS);
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator() || !player.isAlive()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (level.dimension() != Level.OVERWORLD || (level.getGameTime() + (player.getId() & 0xFF)) % CHECK_INTERVAL != 7) {
            return;
        }
        visit(level, player);
    }

    // 自己テストからも呼ぶ: この人の近くの立つ所に、いなければ職人を立たせる。立たせた数
    public static int visit(ServerLevel level, ServerPlayer player) {
        int placed = 0;
        for (Spot spot : SPOTS) {
            if (!spot.gate().test(player)) {
                continue;
            }
            for (BlockPos mark : marks(level, spot)) {
                double dx = mark.getX() - player.getX();
                double dz = mark.getZ() - player.getZ();
                if (dx * dx + dz * dz <= NEAR * NEAR && ensure(level, mark, spot.chapter()) != null) {
                    placed++;
                }
            }
        }
        return placed;
    }

    // 立つ所の印の位置 (建て終わった場所の extra)
    public static List<BlockPos> marks(ServerLevel level, Spot spot) {
        List<BlockPos> out = new java.util.ArrayList<>();
        for (SiteData.Entry e : SiteData.get(level.getServer().overworld()).allEntries()) {
            if (e.built && spot.siteId().equals(e.siteId)) {
                BlockPos at = e.extra(spot.mark());
                if (at != null) {
                    out.add(at);
                }
            }
        }
        return out;
    }

    // mark に章 chapter の装いの職人がいなければ立たせる (立たせたら返す。すでにいる・読み込み前・立てる地面がない・立てたばかり なら null)
    @Nullable
    public static NpcEntity ensure(ServerLevel level, BlockPos mark, int chapter) {
        if (!NpcSpawner.entitiesLoaded(level, mark) || live(level, mark, chapter) != null) {
            return null;
        }
        NpcWorldData data = NpcWorldData.get(level.getServer().overworld());
        String key = NpcSetup.ARTISAN_ID + "#" + chapter;
        long now = level.getGameTime();
        if (!NpcSpawner.canRespawn(data, mark, key, now)) {
            return null;
        }
        BlockPos at = NpcSpawner.ground(level, mark, 2, 3);
        if (at == null) {
            at = NpcSpawner.ground(level, mark.offset(1, 0, 1), 3, 4);
        }
        if (at == null) {
            return null;
        }
        NpcEntity npc = NpcSpawner.place(level, NpcSetup.ARTISAN.get(), at, mark);
        if (npc == null) {
            return null;
        }
        npc.setVariant(chapter);
        data.markSpawned(mark.asLong(), key, now);
        return npc;
    }

    // mark のそばに、章 chapter の装いの職人が立っているか
    @Nullable
    public static NpcEntity live(ServerLevel level, BlockPos mark, int chapter) {
        List<NpcEntity> found = level.getEntities(NpcSetup.ARTISAN.get(), new AABB(mark).inflate(LIVE_RADIUS), e -> e.isAlive() && e.variant() == chapter);
        return found.isEmpty() ? null : found.get(0);
    }
}
