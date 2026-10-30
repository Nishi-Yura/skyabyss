package jp.revati.skyabyss.sky;

import java.util.function.ToDoubleFunction;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.story.Chapters;
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
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

// 1章の空の演出の共通部分。
// - 「1章に入った」: 巨神の背に一度でも乗った (背のディメンションに入った。伝承の書の「空の遺跡」が記されているのも同じ意味)
// - 「月の近さ」(0〜1): ループと月の接近は M4 で入る。それまでは日数で増える仮の値を使い、M4 が setMoonSource で差し替える
// - 拠点の目安を覚える: 復活地点と、プレイヤーがブロックを置いたチャンク (削られた山頂の安全策。PeakScar)
//
// ---------------------------------------------------------------- 章の段 (docs/design/01 の 4.1「空の異変の段」。中身は各章の担当 (P4) が足す)
// 今の動き (影・雲の目・山頂の削れ・見る者) は、章に直すと「巨神編 (一〜四章) の空」: inChapterOne = 巨神編に入った (旧「1 章」)。
// 章ごとの差は Stage で足す。何も登録しなければ全章 Stage.NONE で、今までと同じ動きのまま。
//   ChapterSky.setStage(Chapters.TIDE, new ChapterSky.Stage(0.05f, 0x8FB8C8, 0.06f));   // 自分の章の Setup から (静的でよい)
//   moonBias  : その章の人にとっての月の近さに足す値 (0〜1 の範囲で切る)。ChapterSky.moonNearness(level, player) が足す
//   tint      : 空と遠景の霧の色を、その色へ strength (0〜0.35) だけ寄せる。本人のクライアントにだけ送る (ChapterTintPayload)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ChapterSky {
    public static final String CHAPTER_ONE_LORE = "chapter1/sky_ruins";

    // 章ごとの空の段。moonBias は月の近さへの加算、tintRgb/tintStrength は空の色味 (strength 0 = なし)
    public record Stage(float moonBias, int tintRgb, float tintStrength) {
        public static final Stage NONE = new Stage(0f, 0, 0f);
    }

    private static final java.util.Map<Chapters, Stage> STAGES = new java.util.concurrent.ConcurrentHashMap<>();
    // 人ごとに最後に送った色味 (保存しない。ログインのたびに送り直す)
    private static final java.util.Map<java.util.UUID, Stage> SENT_TINT = new java.util.concurrent.ConcurrentHashMap<>();

    public static void setStage(Chapters chapter, Stage stage) {
        if (stage == null || stage.equals(Stage.NONE)) {
            STAGES.remove(chapter);
        } else {
            STAGES.put(chapter, stage);
        }
    }

    public static Stage stage(Chapters chapter) {
        return STAGES.getOrDefault(chapter, Stage.NONE);
    }

    // その人の章の段 (終章の結末を選んだ人は、結末の空。story/finale/FinaleEnding)
    public static Stage stage(ServerPlayer player) {
        Stage ending = jp.revati.skyabyss.story.finale.FinaleEnding.stage(player);
        if (ending != null) {
            return ending;
        }
        return stage(Chapters.of(player));
    }

    // その人にとっての月の近さ: 世界の値に、その人の章の moonBias を足す (0〜1)
    public static float moonNearness(ServerLevel level, ServerPlayer player) {
        return Math.max(0f, Math.min(1f, moonNearness(level) + stage(player).moonBias()));
    }

    // 自己テスト用: 登録を空にする
    static void clearStages() {
        STAGES.clear();
    }

    @SubscribeEvent
    static void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 100 == 17 && player.connection != null
                && !(player instanceof net.neoforged.neoforge.common.util.FakePlayer)) {
            syncTint(player, stage(player));
        }
    }

    // 色味が替わっていれば送る (替わっていなければ何もしない)。送ったら true
    static boolean syncTint(ServerPlayer player, Stage stage) {
        Stage shown = new Stage(0f, stage.tintRgb(), stage.tintStrength());
        Stage last = SENT_TINT.get(player.getUUID());
        if ((last == null && stage.tintStrength() <= 0f) || shown.equals(last)) {
            return false;
        }
        SENT_TINT.put(player.getUUID(), shown);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new ChapterTintPayload(stage.tintRgb(), stage.tintStrength()));
        return true;
    }

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
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        // 起き直したら、章の色味を送り直す (場面の暗い色味が残らないように)
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof net.neoforged.neoforge.common.util.FakePlayer)) {
            Stage st = stage(player);
            SENT_TINT.put(player.getUUID(), new Stage(0f, st.tintRgb(), st.tintStrength()));
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new ChapterTintPayload(st.tintRgb(), st.tintStrength()));
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
            SENT_TINT.remove(player.getUUID());
            rememberHome(player, player.getRespawnPosition(), player.getRespawnDimension());
        }
    }

    @SubscribeEvent
    static void onSetSpawn(PlayerSetSpawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            rememberHome(player, event.getNewSpawn(), event.getSpawnLevel());
        }
    }

    // プレイヤーがブロックを置いたチャンクを覚える (そのチャンクは削らない)。
    // EntityMultiPlaceEvent (ベッド・扉など) も EntityPlaceEvent の一種なので、ここに届く。FakePlayer (他の Mod の機械) も Player に入る
    @SubscribeEvent
    static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player && event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
            ChapterSkyData.get(level).markPlaced(ChunkPos.asLong(event.getPos()));
        }
    }

    // プレイヤーが固いブロックを掘ったチャンクも覚える (山の中をくり抜いた住まいを削らない)。
    // 草花・雪の層など、歩くだけで壊れるものは数えない
    @SubscribeEvent
    static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() != null && event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD
                && !event.getState().canBeReplaced() && !PeakScar.vegetation(event.getState())) {
            ChapterSkyData.get(level).markPlaced(ChunkPos.asLong(event.getPos()));
        }
    }

    // この Mod が入ってから生成されたチャンクに印を付ける (PeakScar: 印のない古いチャンクは慎重に扱う)
    @SubscribeEvent
    static void onChunkLoad(ChunkEvent.Load event) {
        if (event.isNewChunk() && event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD
                && event.getChunk() instanceof LevelChunk chunk) {
            chunk.setData(ChapterSkyRegistry.FRESH_CHUNK, true);
        }
    }

    // この Mod が入ってから生成されたチャンクか (プレイヤーが置いた記録がそろっているチャンク)
    public static boolean freshChunk(LevelChunk chunk) {
        return chunk.hasData(ChapterSkyRegistry.FRESH_CHUNK) && chunk.getData(ChapterSkyRegistry.FRESH_CHUNK);
    }
}
