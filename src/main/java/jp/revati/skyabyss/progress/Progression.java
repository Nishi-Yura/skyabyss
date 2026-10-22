package jp.revati.skyabyss.progress;

import jp.revati.skyabyss.util.Players;

import jp.revati.skyabyss.notice.Notice;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.loot.TitanMemoryData;
import jp.revati.skyabyss.rpg.GrowthRules;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 物語の進み (個人) と世界の進み (サーバー) の入口 (10 の 3〜4 章)。
// - 巨神は順番に: 前の巨神の心臓をワールドの誰かが止めるまで、次の巨神は現れない (worldAllows)
// - 乗れるのは、自分で前の巨神の心臓を止めた人だけ (canBoard)。仲間に連れていってもらう例外はない。
//   入口 (鎖・潮門・コマンド) で断り、ほかの道 (/tp、/execute in、ポータル、復活地点、ログイン) は
//   ディメンションを移る前 (EntityTravelToDimensionEvent) と、背中にいる間の見回り (1 秒ごと) で地上へ戻す
// - レベルの上限は、止めた巨神の数で決まる (levelCap)
// 決まりはゲームルールで切れる: skyabyssStoryGate (順番と乗る資格)、skyabyssStoryLevelCap (上限)。
// クリエイティブと観戦者は乗る資格を問わない (作る人・見る人のため。上限はかかる)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Progression {
    public static GameRules.Key<GameRules.BooleanValue> STORY_GATE;
    public static GameRules.Key<GameRules.BooleanValue> STORY_LEVEL_CAP;

    private Progression() {
    }

    @SubscribeEvent
    static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            STORY_GATE = GameRules.register("skyabyssStoryGate", GameRules.Category.MISC, GameRules.BooleanValue.create(true));
            STORY_LEVEL_CAP = GameRules.register("skyabyssStoryLevelCap", GameRules.Category.MISC, GameRules.BooleanValue.create(true));
        });
    }

    public static boolean gateOn(MinecraftServer server) {
        return STORY_GATE == null || server.getGameRules().getBoolean(STORY_GATE);
    }

    public static boolean capOn(MinecraftServer server) {
        return STORY_LEVEL_CAP == null || server.getGameRules().getBoolean(STORY_LEVEL_CAP);
    }

    // ---------------------------------------------------------------- 個人の進み
    // この人がその巨神の心臓を止めたか (この記録か、巨神の記憶の撃破の記録)
    public static boolean stopped(MinecraftServer server, UUID player, String titan) {
        if (ProgressData.get(server).personalStopped(player, titan)) {
            return true;
        }
        TitanMemoryData.Record r = TitanMemoryData.get(server).peek(player, titan);
        return r != null && r.clears > 0;
    }

    // 順番どおりに止めた巨神の数 (0〜4)
    public static int stage(MinecraftServer server, UUID player) {
        return ProgressRules.stage(t -> stopped(server, player, t));
    }

    public static int stage(ServerPlayer player) {
        return stage(player.server, player.getUUID());
    }

    // レベルの上限 (Growth.addXp が使う)。決まりを切っていれば 50
    public static int levelCap(ServerPlayer player) {
        if (player instanceof FakePlayer || !capOn(player.server)) {
            return GrowthRules.MAX_LEVEL;
        }
        return Math.min(GrowthRules.MAX_LEVEL, ProgressRules.levelCap(stage(player), wardenFelled(player)));
    }

    // 心臓の番人を倒したか (WardenArena.fallen が書く記録。古いセーブはメインの五章が段 warden を越えていれば)
    public static boolean wardenFelled(ServerPlayer player) {
        if (ProgressData.get(player.server).personalStopped(player.getUUID(), ProgressRules.WARDEN)) {
            return true;
        }
        jp.revati.skyabyss.quest.QuestLog.Entry e = jp.revati.skyabyss.quest.QuestLog.of(player).entry("main_abyss");
        jp.revati.skyabyss.quest.QuestDef def = jp.revati.skyabyss.quest.QuestManager.quest("main_abyss");
        if (e == null || def == null) {
            return false;
        }
        int warden = def.stages().indexOf(def.stage("warden"));
        return e.done || (warden >= 0 && e.stage > warden);
    }

    // 番人が倒れた: 間にいた人の上限を 50 へ (報酬の経験値より先に書く。心臓と同じ順)
    public static void markWardenFelled(ServerPlayer player) {
        ProgressData.get(player.server).markPersonal(player.getUUID(), ProgressRules.WARDEN);
    }

    // 上限に届いたときの知らせ (Growth.addXp から)
    public static void tellCapped(ServerPlayer player, int cap) {
        String next = nextTitan(player);
        if (next == null && cap < ProgressRules.ABYSS_CAP && !wardenFelled(player)) {
            Notice.narrate(player, "message.skyabyss.progress.capped_abyss", cap);
        } else if (next == null) {
            Notice.narrate(player, "message.skyabyss.progress.capped_end", cap);
        } else {
            Notice.narrate(player, "message.skyabyss.progress.capped", cap, titanName(next));
        }
    }

    // 次に挑む巨神 (すべて止めていれば null)
    @Nullable
    public static String nextTitan(ServerPlayer player) {
        int s = stage(player);
        return s < ProgressRules.TITAN_ORDER.size() ? ProgressRules.TITAN_ORDER.get(s) : null;
    }

    public static Component titanName(String titan) {
        return Component.translatable("progress.skyabyss.titan." + titan);
    }

    // ---------------------------------------------------------------- 世界の進み
    // その巨神がこのワールドに現れてよいか (前の巨神の心臓を誰かが止めた)
    public static boolean worldAllows(MinecraftServer server, String titan) {
        if (!gateOn(server)) {
            return true;
        }
        String prev = ProgressRules.previousTitan(titan);
        return prev == null || ProgressData.get(server).worldStopped(prev);
    }

    public static int worldStage(MinecraftServer server) {
        ProgressData data = ProgressData.get(server);
        return ProgressRules.stage(data::worldStopped);
    }

    // ---------------------------------------------------------------- 乗る資格
    public static boolean exempt(ServerPlayer player) {
        return player.isCreative() || player.isSpectator();
    }

    public static boolean canBoard(ServerPlayer player, String titan) {
        if (!gateOn(player.server) || exempt(player)) {
            return true;
        }
        return ProgressRules.canChallenge(stage(player), titan);
    }

    // 入口で確かめる。乗れなければ理由を見せて false
    public static boolean checkBoard(ServerPlayer player, String titan) {
        if (canBoard(player, titan)) {
            // 心臓を止めた直後の巨神は深い眠りにある (jp.revati.skyabyss.titan.end.TitanDormancy)。鼓動が戻るまで乗れない
            if (jp.revati.skyabyss.titan.end.TitanDormancy.isResting(player.server, titan)) {
                Notice.bar(player, ModText.deny("message.skyabyss.titan_end.sleeping", titanName(titan)));
                return false;
            }
            return true;
        }
        String prev = ProgressRules.previousTitan(titan);
        Notice.bar(player, ModText.deny("message.skyabyss.progress.not_ready", titanName(titan),
                prev == null ? Component.empty() : titanName(prev)));
        Notice.hint(player, "message.skyabyss.progress.not_ready_hint", titanName(prev == null ? titan : prev));
        return false;
    }

    // 背中のディメンションの巨神 (背中でなければ null)。後の巨神の背中は Titans.bindBack で足す
    @Nullable
    public static String titanOfBack(ResourceKey<Level> dimension) {
        return Titans.titanOfBack(dimension);
    }

    // ---------------------------------------------------------------- 心臓を止めた (TitanLoot など、撃破の報酬より先に呼ぶ)
    public static void onTitanStopped(MinecraftServer server, Collection<UUID> participants, String titan) {
        ProgressData data = ProgressData.get(server);
        long day = server.overworld().getGameTime() / 24000L;
        ServerPlayer worldFirst = null;
        for (UUID id : participants) {
            boolean before = stage(server, id) > ProgressRules.titanIndex(titan);
            data.markPersonal(id, titan);
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            boolean first = data.markWorld(titan, player != null ? player.getGameProfile().getName() : nameOf(server, id), day);
            if (player == null) {
                continue;
            }
            jp.revati.skyabyss.advancement.Beats.fire(player, "stopped:" + titan);
            if (first) {
                worldFirst = player;
            }
            if (!before && stage(server, id) > ProgressRules.titanIndex(titan)) {
                Notice.unlock(player, "message.skyabyss.progress.stage_up", titanName(titan),
                        ProgressRules.levelCap(stage(server, id)));
            }
            jp.revati.skyabyss.party.PartyNetwork.syncSoon(player);
        }
        // 見え方の判定を捨てる (止めた人から、眠りのあとは巨神が消える。32 の 3.3)
        jp.revati.skyabyss.titan.TitanVisibility.clearCache();
        // 参加者の進みを書き終えてから (同じ戦いの仲間も「止めた人」に数える)
        if (worldFirst != null) {
            announceWorld(server, worldFirst, titan);
        }
        // 巨神ごとの最速の記録・称号 (docs/design/31 の C21・C04)
        jp.revati.skyabyss.rebirth.Rebirths.onTitanStopped(server, participants, titan);
    }

    private static String nameOf(MinecraftServer server, UUID id) {
        if (server.getProfileCache() != null) {
            var profile = server.getProfileCache().get(id);
            if (profile.isPresent()) {
                return profile.get().getName();
            }
        }
        return "?";
    }

    // ワールドで初めて止めた: 同じ巨神をもう止めた人にだけ、画面の語りで一行 (チャットには流さない。
    // まだ止めていない人にその巨神の名前を明かさず、次の巨神の名前も出さない)
    private static void announceWorld(MinecraftServer server, ServerPlayer by, String titan) {
        int i = ProgressRules.titanIndex(titan);
        String key = i + 1 < ProgressRules.TITAN_ORDER.size()
                ? "message.skyabyss.progress.world_first" : "message.skyabyss.progress.world_first_last";
        for (ServerPlayer p : worldFirstAudience(Players.snapshot(server.getPlayerList().getPlayers()), by, i)) {
            Notice.narrate(p, key, by.getDisplayName(), titanName(titan));
        }
    }

    // 世界の最初の知らせを受ける人: 自分で、その巨神を止めたことのある、ほかの人
    public static List<ServerPlayer> worldFirstAudience(Iterable<ServerPlayer> players, ServerPlayer by, int titanIndex) {
        List<ServerPlayer> out = new java.util.ArrayList<>();
        for (ServerPlayer p : players) {
            if (p != by && stage(p) > titanIndex) {
                out.add(p);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- 抜け道を塞ぐ
    // ディメンションを移る前: 資格のない背中へは移らせない (/tp、/execute in、ほかの Mod のテレポート、ポータル)
    @SubscribeEvent
    static void onTravel(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        String titan = titanOfBack(event.getDimension());
        if (titan != null && !canBoard(player, titan)) {
            event.setCanceled(true);
            checkBoard(player, titan);
        }
    }

    // 背中にいる間の見回り (ログイン・復活地点・取り消せなかった移動): 資格がなければ地上へ戻す
    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer || player.tickCount % 20 != 13) {
            return;
        }
        String titan = titanOfBack(player.level().dimension());
        if (titan == null || canBoard(player, titan) || !player.isAlive()) {
            return;
        }
        SkyAbyss.LOGGER.info("{} was on the {} back without stopping the previous heart; sending them down", player.getGameProfile().getName(), titan);
        checkBoard(player, titan);
        Titans.sendDown(player, titan);
    }

    // ログイン: 古いワールドで止めていた巨神を、世界の進みにも写す
    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) {
            return;
        }
        ProgressData data = ProgressData.get(player.server);
        for (String titan : ProgressRules.TITAN_ORDER) {
            if (!data.worldStopped(titan) && stopped(player.server, player.getUUID(), titan)) {
                data.markWorld(titan, player.getGameProfile().getName(), player.server.overworld().getGameTime() / 24000L);
            }
        }
    }
}
