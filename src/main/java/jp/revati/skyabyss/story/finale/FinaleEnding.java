package jp.revati.skyabyss.story.finale;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.registry.ModSounds;
import jp.revati.skyabyss.sky.ChapterSky;
import jp.revati.skyabyss.story.Chapters;
import jp.revati.skyabyss.story.arc.ChapterCards;
import jp.revati.skyabyss.story.ch6.MoonGate;
import jp.revati.skyabyss.story.ch6.MoonMemories;
import jp.revati.skyabyss.story.reel.StoryReels;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 終章の結末 (docs/design/50 の 4.4)。巡礼者の月を倒したあと、座のそばでチャットの選びを出す:
//   鎮める calm   心臓を癒し、引き合いを戻す。月が遠のき空が晴れ、巨神の歌がときどき聞こえる。都の鐘は明るく鳴る
//   壊す   break  心臓を壊し、巡礼者を封じる。空に裂け目が走り、巨神は深く眠る (背は今までどおり挑める)。都の鐘は低く鳴る
//   受け入れる accept  巡礼者を地上に招く。翼が昼の空を巡り、都の鐘は鳴りやむ。月の記憶の断片 12 と伝承「見る者」をそろえた人だけ
// 選ぶと結末の語り (finale_end_<結末>) が流れ、見終えると人ごとに記録し (保存は人のデータ)、本人の空だけ変える (EndingPayload → client/sky/EndingSkyClient)。
// 座にまた触れると、選び直すか、巡礼者に挑み直すかを選べる。雲の中の目は、結末を選んだ人にはもう出ない (CloudEyeEvents)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class FinaleEnding {
    public static final String KEY = "skyabyss_finale_ending";
    // 見終えた結末 (並びは ENDINGS の順、"," でつなぐ)。三つそろうと裏ボス「初代の空見」の頼みが始まる (docs/design/53 の 3 章)
    public static final String SEEN = "skyabyss_finale_seen";
    public static final List<String> ENDINGS = List.of("calm", "break", "accept");
    public static final String REEL = "finale_end_";
    static final String COMMAND = "skyabyss_ending";
    static final int SONG_EVERY = 4800;
    static final double CAPITAL_NEAR = 220;
    static final String TOLLED = "skyabyss_finale_tolled";
    private static final Map<UUID, String> SENT = new HashMap<>();

    private FinaleEnding() {
    }

    static {
        for (String e : ENDINGS) {
            StoryReels.onDone(REEL + e, p -> apply(p, e));
        }
    }

    // その人の選んだ結末 (まだなら null)
    public static String chosen(ServerPlayer p) {
        String s = p.getPersistentData().getString(KEY);
        return ENDINGS.contains(s) ? s : null;
    }

    // 見終えた結末 (前の版から遊んでいる人は、今の結末を見たものとして数える)
    public static java.util.Set<String> seen(ServerPlayer p) {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        for (String e : p.getPersistentData().getString(SEEN).split(",")) {
            if (ENDINGS.contains(e)) {
                out.add(e);
            }
        }
        String now = chosen(p);
        if (now != null) {
            out.add(now);
        }
        return out;
    }

    static void markSeen(ServerPlayer p, String e) {
        java.util.Set<String> s = seen(p);
        s.add(e);
        p.getPersistentData().putString(SEEN, String.join(",", ENDINGS.stream().filter(s::contains).toList()));
    }

    // 受け入れるを選べるか: 月の記憶の断片 12 と、伝承「見る者」
    public static boolean mayAccept(ServerPlayer p) {
        return MoonMemories.count(p) >= MoonMemories.COUNT && Lore.state(p).has(MoonMemories.TRUTH);
    }

    // 選べるか: 座の頼みを終えた人が、戦いのない座にいる
    public static boolean mayChoose(ServerPlayer p) {
        QuestLog.Entry e = QuestLog.of(p).entry(PilgrimSeat.QUEST);
        int f = SeatArena.form();
        return e != null && e.done && FinaleWorld.in(p) && SeatArena.box().contains(p.position()) && (f == 0 || f == 4);
    }

    // チャットの選び。retry = 巡礼者に挑み直す、も出す
    public static void offer(ServerPlayer p, boolean retry) {
        Notice.narrate(p, chosen(p) == null ? "message.skyabyss.ending.choose" : "message.skyabyss.ending.rechoose");
        Component line = Component.empty().append(option("calm", 0xBFE0FF)).append("  ").append(option("break", 0xC8A0E0));
        if (mayAccept(p)) {
            line = line.copy().append("  ").append(option("accept", 0xF2D78A));
        }
        if (retry) {
            line = line.copy().append("  ").append(Component.translatable("message.skyabyss.ending.retry")
                    .withStyle(Style.EMPTY.withColor(0xB0B0B8).withUnderlined(true).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + COMMAND + " retry"))));
        }
        p.sendSystemMessage(line);
    }

    static Component option(String e, int color) {
        return Component.translatable("message.skyabyss.ending.option." + e).withStyle(Style.EMPTY.withColor(color).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + COMMAND + " " + e))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.skyabyss.ending.option." + e + ".hint"))));
    }

    // 選んだ: 結末の語りを流す (見終えると apply)
    public static boolean choose(ServerPlayer p, String e) {
        if (!ENDINGS.contains(e) || !mayChoose(p) || e.equals("accept") && !mayAccept(p)) {
            return false;
        }
        StoryReels.play(p, REEL + e);
        return true;
    }

    // 結末を記録して、空と都を変える
    public static void apply(ServerPlayer p, String e) {
        boolean first = chosen(p) == null;
        p.getPersistentData().putString(KEY, e);
        markSeen(p, e);
        Lore.unlock(p, "finale/ending_" + e, true);
        Notice.place(p, Component.translatable("message.skyabyss.ending." + e + ".title"), Component.translatable("message.skyabyss.ending." + e + ".sub"));
        Notice.narrate(p, "message.skyabyss.ending." + e + ".after");
        if (first) {
            ChapterCards.aftermath(p, Chapters.FINALE, Component.translatable("chapter.skyabyss.aftermath.finale." + e));
        }
        Quests.signal(p, "finale_ending");
        sync(p);
    }

    // その人の空の段 (ChapterSky.stage が先に聞く)。まだ選んでいなければ null
    public static ChapterSky.Stage stage(ServerPlayer p) {
        String e = chosen(p);
        if (e == null) {
            return null;
        }
        return switch (e) {
            case "calm" -> new ChapterSky.Stage(-1f, 0x9CC8F0, 0.12f);
            case "break" -> new ChapterSky.Stage(-0.5f, 0x8C74A8, 0.16f);
            default -> new ChapterSky.Stage(-0.7f, 0xF2D78A, 0.14f);
        };
    }

    static void sync(ServerPlayer p) {
        if (p instanceof net.neoforged.neoforge.common.util.FakePlayer || p.connection == null) {
            return;
        }
        String e = chosen(p);
        String v = e == null ? "" : e;
        SENT.put(p.getUUID(), v);
        PacketDistributor.sendToPlayer(p, new EndingPayload(v));
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            SENT.remove(p.getUUID());
            sync(p);
        }
    }

    // 結末のあとの世界の音: 鎮めた人には巨神の歌 (外で、ときどき)、都の鐘は朝と夕に (受け入れた人には鳴らない)
    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.tickCount % 20 != 0 || p instanceof net.neoforged.neoforge.common.util.FakePlayer) {
            return;
        }
        String e = chosen(p);
        String v = e == null ? "" : e;
        if (!v.equals(SENT.get(p.getUUID()))) {
            sync(p);
        }
        if (e == null || p.level().dimension() != Level.OVERWORLD) {
            return;
        }
        ServerLevel level = p.serverLevel();
        if (e.equals("calm") && level.canSeeSky(p.blockPosition().above()) && level.random.nextInt(SONG_EVERY / 20) == 0) {
            p.connection.send(new ClientboundSoundPacket(ModSounds.TITAN_SONG, SoundSource.AMBIENT, p.getX() - 60, p.getY() + 70, p.getZ() + 50,
                    6.0f, 1.0f, level.random.nextLong()));
        }
        long t = level.getDayTime() % 24000;
        if (!e.equals("accept") && (t >= 0 && t < 20 || t >= 12000 && t < 12020)) {
            BlockPos gate = MoonGate.gatePos(p.server);
            // 昼夜が止まっていても鳴り続けないよう、1 分に一度まで
            long last = p.getPersistentData().getLong(TOLLED);
            if (gate != null && p.distanceToSqr(gate.getX(), gate.getY(), gate.getZ()) < CAPITAL_NEAR * CAPITAL_NEAR
                    && (last > level.getGameTime() || level.getGameTime() - last > 1200)) {
                p.getPersistentData().putLong(TOLLED, level.getGameTime());
                toll(p, e.equals("calm") ? 1.0f : 0.6f, 3);
            }
        }
    }

    static void toll(ServerPlayer p, float pitch, int n) {
        for (int i = 0; i < n; i++) {
            jp.revati.skyabyss.story.ch1.Tasks.later(i * 30, () -> {
                if (!p.hasDisconnected()) {
                    p.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.AMBIENT, 1.0f, pitch);
                }
            });
        }
    }

    @SubscribeEvent
    static void onCommands(RegisterCommandsEvent event) {
        var root = Commands.literal(COMMAND);
        for (String e : ENDINGS) {
            root = root.then(Commands.literal(e).executes(c -> choose(c.getSource().getPlayerOrException(), e) ? 1 : 0));
        }
        root = root.then(Commands.literal("retry").executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            if (mayChoose(p) && SeatArena.form() == 0) {
                SeatArena.start(p.serverLevel(), 1);
                return 1;
            }
            return 0;
        }));
        event.getDispatcher().register(root);
    }

    // サーバーが止まったとき (FinaleReset)
    static void resetAll() {
        SENT.clear();
    }
}
