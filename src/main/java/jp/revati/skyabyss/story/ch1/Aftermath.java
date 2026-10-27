package jp.revati.skyabyss.story.ch1;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.opening.OpeningDirector;
import jp.revati.skyabyss.registry.ModSounds;
import jp.revati.skyabyss.story.Chapters;
import jp.revati.skyabyss.story.arc.ChapterCards;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 一章の余韻 (01 の 4.2): 岩鯨の心臓を止めると、章の終わりの札「一章　岩鯨　終」(碑文「この鼓動は、地の底から預かったもの」)、
// 語り部の一言、そして空の歌が一節だけ欠けて鳴る (途中で切れる)。段階が 1 になった (= 二章に入った) 人に、一度だけ。
// 札は story/arc/ChapterCards.aftermath (場面の間は預かる)。すでに遊んでいた人 (legacy) と、段階がさらに進んだ人には出さない。
// 引き (二章の予兆 1 拍目) は 二章の担当が Foreshadow に足す
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Aftermath {
    // 札から語り部の字幕までの間 (tick)、字幕から欠けた歌までの間、歌が切れるまでの長さ
    static final int NARRATE_AFTER = 140;
    static final int SONG_AFTER = 260;
    static final int SONG_CUT = 100;

    private Aftermath() {
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer || player.isSpectator()) {
            return;
        }
        int phase = (player.getUUID().hashCode() & 0x7fffffff) % 20;
        if (player.tickCount % 20 != phase || player.tickCount < 100) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }
        if (Chapters.of(player) != Chapters.TIDE || OpeningDirector.state(player).legacy()) {
            return;
        }
        deliver(player);
    }

    // 余韻を出す (済んでいれば何もしない)。受け付けたら true
    static boolean deliver(ServerPlayer player) {
        // 場面・心臓の報酬・天職の選択が済んで地上に戻ってから (済むまでは何もしない。語りと歌は札に合わせて出る)
        if (!ChapterCards.settled(player) || !ChapterCards.aftermath(player, Chapters.WHALE)) {
            return false;
        }
        ChapterCards.epilogue(player, SONG_AFTER + SONG_CUT);
        Tasks.later(NARRATE_AFTER, () -> Notice.narrate(player, "message.skyabyss.ch1.aftermath.storyteller"));
        Tasks.later(SONG_AFTER, () -> brokenSong(player));
        return true;
    }

    // 空の歌が、一節だけ欠けて鳴る: 高い所から鳴りはじめ、途中でふっと切れる
    static void brokenSong(ServerPlayer player) {
        if (player.isRemoved() || player.connection == null) {
            return;
        }
        player.connection.send(new ClientboundSoundPacket(ModSounds.TITAN_SONG, SoundSource.AMBIENT, player.getX() - 40, player.getY() + 80, player.getZ() + 40,
                8.0f, 0.85f, player.getRandom().nextLong()));
        Tasks.later(SONG_CUT, () -> {
            if (!player.isRemoved() && player.connection != null) {
                player.connection.send(new ClientboundStopSoundPacket(ModSounds.TITAN_SONG.getId(), SoundSource.AMBIENT));
            }
        });
    }
}
