package jp.revati.skyabyss.titan.ember;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.story.Chapters;
import jp.revati.skyabyss.story.SkyKey;
import jp.revati.skyabyss.story.arc.ArcData;
import jp.revati.skyabyss.story.arc.ChapterCards;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 四章の余韻と、巨神編の終わりの余韻 (docs/design/13 の 12.4、14 の 3、01 の 4.2)。画面の札と語りだけ。チャットには流さない。
//
//   余燼の心臓を止めた人 (段階 4) が、地上 (オーバーワールド) にいるあいだに、順番に一度ずつ (人ごとの記録は永続データ。死んでも残る):
//     1 余韻の札     ChapterCards.aftermath(EMBER)「四章　余燼　終」。天の鍵の場面 (約 26 秒) の最中・直後は待つ。鍵がなければ地上に 10 秒いたあと
//     2 語り部の一言  札の 6 秒後。巨神編の最後の一言
//     3 終わりの合図  語りのあと 10 秒。天の鍵がそろっていれば、足もとのずっと下で鍵と同じ拍が一つ打つ (音・ごく小さな揺れ・粒)。巨神編の終わり。
//                     五章 (深淵) へは何も押しつけない (鍵が下を指すだけ)
//   会話・戦いの直後は待つ (SkyKey.busy)。鍵のあとの余韻は SkyKey.assemble が afterKey を呼んで知らせる
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class EmberAftermath {
    public static final String KEY = "skyabyss_ember_end";
    // 天の鍵の場面の長さ (SkyKeyScene。20 秒の終わりの札まで + 余裕)
    public static final long SCENE_TICKS = 640;
    public static final long CALM_TICKS = 200;
    public static final long TELL_DELAY = 120;
    public static final long END_DELAY = 200;
    public static final String LINE_TELLER = "message.skyabyss.ember_ch4.aftermath.teller";
    public static final String LINE_END = "message.skyabyss.ember_ch4.aftermath.end";

    private EmberAftermath() {
    }

    // 人ごとの進み (永続データ)
    private static CompoundTag state(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(KEY, 10)) {
            root.put(KEY, new CompoundTag());
        }
        return root.getCompound(KEY);
    }

    public static boolean cardDone(ServerPlayer player) {
        return state(player).contains("card_at");
    }

    public static boolean tellerDone(ServerPlayer player) {
        return state(player).getBoolean("told");
    }

    public static boolean endDone(ServerPlayer player) {
        return state(player).getBoolean("end");
    }

    // 天の鍵がそろったとき (SkyKey.assemble から)。場面が終わるまで余韻を待たせる
    public static void afterKey(ServerPlayer player) {
        CompoundTag st = state(player);
        if (!st.contains("key_at")) {
            st.putLong("key_at", player.serverLevel().getGameTime());
        }
    }

    // 自己テスト・コマンド用: 記録を消す
    public static void reset(ServerPlayer player) {
        player.getPersistentData().remove(KEY);
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer) && player.tickCount % 20 == 11) {
            poll(player);
        }
    }

    // 次の一つを進める。進めたら 1・2・3 (どの段か)、待つなら 0
    public static int poll(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || !(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD
                || Progression.stage(player) < Chapters.EMBER.stage() + 1) {
            return 0;
        }
        CompoundTag st = state(player);
        long now = level.getGameTime();
        if (!st.contains("since")) {
            st.putLong("since", now);
        }
        long ready = st.getLong("since") + CALM_TICKS;
        if (st.contains("key_at")) {
            ready = Math.max(ready, st.getLong("key_at") + SCENE_TICKS);
        }
        if (now < ready || SkyKey.busy(player)) {
            return 0;
        }
        if (!st.contains("card_at")) {
            // すでに出していれば (ほかの経路・古い記録) 札は出さずに進める
            if (!ArcData.of(player).aftermathShown(Chapters.EMBER)) {
                ChapterCards.aftermath(player, Chapters.EMBER);
            }
            st.putLong("card_at", now);
            return 1;
        }
        long last = st.getLong("card_at");
        if (!st.getBoolean("told")) {
            if (now < last + TELL_DELAY) {
                return 0;
            }
            Notice.narrate(player, LINE_TELLER);
            st.putBoolean("told", true);
            st.putLong("told_at", now);
            return 2;
        }
        if (!st.getBoolean("end") && SkyKey.assembled(player) && now >= st.getLong("told_at") + END_DELAY) {
            end(player, level);
            st.putBoolean("end", true);
            return 3;
        }
        return 0;
    }

    // 巨神編の終わりの合図: 足もとのずっと下で、鍵と同じ拍が一つ
    static void end(ServerPlayer player, ServerLevel level) {
        player.playNotifySound(EmberTitanSetup.FAR_BEAT.get(), SoundSource.AMBIENT, 1.0f, 0.9f);
        EmberSurfaceNetwork.send(player, new EmberSurfaceNetwork.Tremor(50, 0.2f));
        level.sendParticles(player, ParticleTypes.WHITE_ASH, false, player.getX(), player.getY() + 0.1, player.getZ(), 20, 1.5, 0.0, 1.5, 0.0);
        Notice.narrate(player, LINE_END);
    }
}
