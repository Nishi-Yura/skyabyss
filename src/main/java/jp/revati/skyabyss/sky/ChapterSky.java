package jp.revati.skyabyss.sky;

import java.util.function.ToDoubleFunction;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.titan.TitanTravel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

// 1章の空の演出の共通部分。
// - 「1章に入った」: 巨神の背に一度でも乗った (背のディメンションに入った。伝承の書の「空の遺跡」が記されているのも同じ意味)
// - 「月の近さ」(0〜1): ループと月の接近は M4 で入る。それまでは日数で増える仮の値を使い、M4 が setMoonSource で差し替える
// - 拠点の目安を覚える: 復活地点と、プレイヤーがブロックを置いたチャンク (削られた山頂の安全策。PeakScar)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ChapterSky {
    public static final String CHAPTER_ONE_LORE = "chapter1/sky_ruins";

    // 月の近さの出どころ。null なら日数による仮の値 (provisionalMoon)
    @Nullable
    private static ToDoubleFunction<ServerLevel> moonSource;

    private ChapterSky() {
    }

    // M4 (ループ本体) が月の近さを渡す口。値は 0 (初日の大きさ) 〜 1 (落ちる直前)
    public static void setMoonSource(@Nullable ToDoubleFunction<ServerLevel> source) {
        moonSource = source;
    }

    // 月の近さ (0〜1)。コマンドの上書きがあればそれを使う
    public static float moonNearness(ServerLevel level) {
        float override = ChapterSkyData.get(level).moonOverride();
        if (override >= 0f) {
            return Math.min(1f, override);
        }
        if (moonSource != null) {
            return (float) Math.max(0.0, Math.min(1.0, moonSource.applyAsDouble(level)));
        }
        return provisionalMoon(level.getServer().overworld().getDayTime(), Config.LOOP_LENGTH_DAYS.get());
    }

    // 仮の月の近さ: ループの長さ (既定7日) を一周として、日ごとに 1/長さ ずつ近づく。
    // 1日目 = 1/7、4日目 = 4/7 (半分を超える)、7日目 = 1。日の区切りは朝 (dayTime 0)。
    // 寝て夜を飛ばすと月も進む (見た目の夜の数で数える)
    static float provisionalMoon(long dayTime, int loopDays) {
        long day = Math.floorDiv(dayTime, 24000L);
        int inLoop = (int) Math.floorMod(day, (long) loopDays);
        return (inLoop + 1) / (float) loopDays;
    }

    public static boolean inChapterOne(ServerPlayer player) {
        ChapterSkyData.Entry entry = ChapterSkyData.get(player.serverLevel()).entry(player.getUUID());
        return entry.boarded || Lore.state(player).has(CHAPTER_ONE_LORE);
    }

    static void markBoarded(ServerPlayer player) {
        ChapterSkyData data = ChapterSkyData.get(player.serverLevel());
        ChapterSkyData.Entry entry = data.entry(player.getUUID());
        if (!entry.boarded) {
            entry.boarded = true;
            data.changed();
        }
    }

    // オーバーワールドの復活地点を覚える (ログアウト中の人の拠点も避けるため)
    static void rememberHome(ServerPlayer player, @Nullable BlockPos pos, @Nullable net.minecraft.resources.ResourceKey<Level> dimension) {
        if (pos == null || dimension != Level.OVERWORLD) {
            return;
        }
        ChapterSkyData data = ChapterSkyData.get(player.serverLevel());
        ChapterSkyData.Entry entry = data.entry(player.getUUID());
        if (!pos.equals(entry.home)) {
            entry.home = pos.immutable();
            data.changed();
        }
    }

    @SubscribeEvent
    static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getTo() == TitanTravel.TITAN_BACK && event.getEntity() instanceof ServerPlayer player) {
            markBoarded(player);
        }
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (player.level().dimension() == TitanTravel.TITAN_BACK) {
                markBoarded(player);
            }
            rememberHome(player, player.getRespawnPosition(), player.getRespawnDimension());
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            rememberHome(player, player.getRespawnPosition(), player.getRespawnDimension());
        }
    }

    @SubscribeEvent
    static void onSetSpawn(PlayerSetSpawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            rememberHome(player, event.getNewSpawn(), event.getSpawnLevel());
        }
    }

    // プレイヤーがブロックを置いたチャンクを覚える (そのチャンクとその周りは削らない)
    @SubscribeEvent
    static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player && event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
            ChapterSkyData.get(level).markPlaced(ChunkPos.asLong(event.getPos()));
        }
    }
}
