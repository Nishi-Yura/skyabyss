package jp.revati.skyabyss.story.ch1;

import jp.revati.skyabyss.util.Players;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.registry.ModSounds;
import jp.revati.skyabyss.sky.SkyEvents;
import jp.revati.skyabyss.sky.SkyPass;
import jp.revati.skyabyss.titan.StoneWhaleEntity;
import jp.revati.skyabyss.titan.WhaleKeeper;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

// 一章の中盤の出来事 (docs/design/17 の 1.5、01 の 4.2): 岩鯨が初めて地上へ降りるとき、ちょうど空の影 (雲の上の巡礼者) が通る。
// **岩鯨が歌をやめ、世界が静かになる**。影が去ると、鎖が降りる。見せるのは「巨神でさえ黙る」こと (引き算の演出。場面のカメラは使わない: 動けるまま空を見上げられる)。
//
//   流れ: 岩鯨が降下を終える (StoneWhaleEntity の APPROACH → REST) 瞬間に onArrive が呼ばれる。ワールドで一度だけ (Ch1Data.hushState)。
//         1. 近くの人 (水平 320 ブロック以内) のために、空の影 (SkyEvents.startFor) を起こす (進行中の影があればそれに乗る)
//         2. 影のあいだ (約 63 秒): 岩鯨は鎖を垂らさず、歌わない (StoneWhaleEntity.holdArrival)。人の環境音・動物の声を止め続け、岩鯨の下の動物は固まる
//         3. 影が去ったら、鎖が降り、岩鯨がまた動きだす。近くにいた人に頁 ch1/whale_hush
//   horrorEventFrequency 0 では起こさない (済みとして先へ進める)。誰も近くにいなければ起こさず、次の降下に回す。サーバーが止まっても、時刻が過ぎれば次の見回りで終わる
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class WhaleHush {
    // 起こす範囲 (岩鯨から水平)
    static final double RANGE = 320;
    // 影が去ってから鎖を降ろすまでの余白
    static final int AFTER_PASS = 40;
    // 途中の字幕の時機 (始まりから)
    static final int MID_AT = 320;
    private static final int STOP_EVERY = 10;

    private WhaleHush() {
    }

    // 影を起こす口 (本物は SkyEvents。自己テストは世界に影を流さないよう差し替える)
    @FunctionalInterface
    interface SkyStarter {
        @Nullable
        SkyPass start(ServerLevel level, ServerPlayer witness);
    }

    static SkyStarter skyStarter = (level, witness) -> {
        SkyEvents.startFor(level, witness);
        return SkyEvents.activePass();
    };
    // 自己テスト用: 機械の偽のプレイヤーも居合わせた人に数える
    static boolean includeFakes;

    // 岩鯨が降下を終えた。出来事が始まったら、鎖を止めておく tick 数を返す (始まらなければ 0)
    public static int onArrive(StoneWhaleEntity whale, ServerLevel level) {
        return begin(level, whale, witnesses(level, whale));
    }

    static int begin(ServerLevel level, StoneWhaleEntity whale, List<ServerPlayer> witnesses) {
        Ch1Data data = Ch1Data.get(level.getServer().overworld());
        if (data.hushState != 0) {
            return 0;
        }
        if (Config.HORROR_EVENT_FREQUENCY.get() <= 0) {
            data.hushState = 2;
            data.setDirty();
            return 0;
        }
        if (witnesses.isEmpty()) {
            return 0;
        }
        ServerPlayer nearest = witnesses.get(0);
        for (ServerPlayer p : witnesses) {
            if (p.distanceToSqr(whale) < nearest.distanceToSqr(whale)) {
                nearest = p;
            }
        }
        SkyPass pass = skyStarter.start(level, nearest);
        if (pass == null) {
            return 0;
        }
        long now = level.getGameTime();
        data.hushState = 1;
        data.hushStart = now;
        data.hushEnd = pass.startTick() + pass.duration() + AFTER_PASS;
        data.setDirty();
        start(level, whale, witnesses);
        return (int) Math.max(40, data.hushEnd - now);
    }

    static List<ServerPlayer> witnesses(ServerLevel level, StoneWhaleEntity whale) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : Players.snapshot(level.players())) {
            if (!p.isSpectator() && (includeFakes || !(p instanceof FakePlayer)) && Math.hypot(p.getX() - whale.getX(), p.getZ() - whale.getZ()) <= RANGE) {
                out.add(p);
            }
        }
        return out;
    }

    private static void start(ServerLevel level, StoneWhaleEntity whale, List<ServerPlayer> witnesses) {
        for (ServerPlayer p : witnesses) {
            silence(p, true);
            Notice.narrate(p, "message.skyabyss.ch1.hush.start");
        }
        // 途中の字幕と、静けさの維持 (影が去るまで)
        Ch1Data data = Ch1Data.get(level.getServer().overworld());
        long total = Math.max(0, data.hushEnd - data.hushStart);
        for (int t = STOP_EVERY; t < total; t += STOP_EVERY) {
            Tasks.later(t, () -> {
                if (Ch1Data.get(level.getServer().overworld()).hushState != 1) {
                    return;
                }
                StoneWhaleEntity w = WhaleKeeper.current(level.getServer().overworld());
                for (ServerPlayer p : Players.snapshot(level.players())) {
                    if (!p.isSpectator() && !(p instanceof FakePlayer) && w != null && Math.hypot(p.getX() - w.getX(), p.getZ() - w.getZ()) <= RANGE + 60) {
                        silence(p, false);
                    }
                }
                if (w != null) {
                    for (Animal animal : level.getEntitiesOfClass(Animal.class, w.getBoundingBox().inflate(80, 200, 80))) {
                        animal.getNavigation().stop();
                        animal.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 14, 6, false, false));
                    }
                }
            });
        }
        Tasks.later(MID_AT, () -> {
            for (ServerPlayer p : witnesses) {
                if (!p.isRemoved()) {
                    Notice.narrate(p, "message.skyabyss.ch1.hush.mid");
                }
            }
        });
    }

    // 人の耳から、環境音・動物の声・岩鯨の歌を消す。first = 始まり (岩鯨の歌も止める)
    static void silence(ServerPlayer player, boolean first) {
        if (player.connection == null) {
            return;
        }
        player.connection.send(new ClientboundStopSoundPacket(null, SoundSource.AMBIENT));
        player.connection.send(new ClientboundStopSoundPacket(null, SoundSource.NEUTRAL));
        if (first) {
            player.connection.send(new ClientboundStopSoundPacket(ModSounds.TITAN_SONG.getId(), null));
        }
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD || level.getGameTime() % 20 != 11) {
            return;
        }
        Ch1Data data = Ch1Data.get(level.getServer().overworld());
        if (data.hushState == 1 && level.getGameTime() >= data.hushEnd) {
            finish(level, data);
        }
    }

    // 影が去った: 鎖を降ろし、近くにいた人に頁を渡す
    static void finish(ServerLevel level, Ch1Data data) {
        data.hushState = 2;
        data.setDirty();
        StoneWhaleEntity whale = WhaleKeeper.current(level.getServer().overworld());
        if (whale != null) {
            whale.releaseHold();
        }
        for (ServerPlayer p : Players.snapshot(level.players())) {
            if (p.isSpectator() || p instanceof FakePlayer) {
                continue;
            }
            boolean near = whale != null && Math.hypot(p.getX() - whale.getX(), p.getZ() - whale.getZ()) <= RANGE;
            if (near) {
                Notice.narrate(p, "message.skyabyss.ch1.hush.end");
                Tasks.later(100, () -> Lore.unlock(p, Ch1.HUSH, true));
            }
        }
    }

    // 自己テスト用: 状態を未着手に戻す
    static void reset(ServerLevel level) {
        Ch1Data data = Ch1Data.get(level.getServer().overworld());
        data.hushState = 0;
        data.hushStart = -1;
        data.hushEnd = -1;
        data.setDirty();
    }

    @Nullable
    static StoneWhaleEntity whale(ServerLevel level) {
        return WhaleKeeper.current(level.getServer().overworld());
    }
}
