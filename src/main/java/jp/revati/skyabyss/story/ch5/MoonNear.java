package jp.revati.skyabyss.story.ch5;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.sky.ChapterSky;
import jp.revati.skyabyss.story.Chapters;
import jp.revati.skyabyss.town.TownGuests;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 五章の終わりから六章 (月) への引き (docs/design/36 の 7 章の終わり)。
//   深淵のメインを終えると、メイン「近づく月」(main_moon_near) が始まる: 夜、外で月を見上げる → 語り部に話す (六章で続く)
//   見上げた: 段が look の人が、地上で夜 (13000〜23000)、頭の上に空が見え、上を向いている (縦の向き -25 度より上) と合図 moon_seen と一言
//   五章の空: 月の近さに少し足し、空をうすく銀青へ寄せる (錨が緩んで月が寄る)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class MoonNear {
    public static final String QUEST = "main_moon_near";
    public static final String SIGNAL = "moon_seen";
    static final ChapterSky.Stage SKY = new ChapterSky.Stage(0.12f, 0xB8C4DC, 0.05f);

    static {
        ChapterSky.setStage(Chapters.ABYSS, SKY);
    }

    private MoonNear() {
    }

    // 見上げているか (自己テストからも)
    public static boolean looksAtMoon(ServerPlayer p) {
        if (p.level().dimension() != Level.OVERWORLD || p.getXRot() > -25f) {
            return false;
        }
        long t = p.level().getDayTime() % 24000L;
        return t >= 13000 && t <= 23000 && p.level().canSeeSky(BlockPos.containing(p.getEyePosition()));
    }

    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.tickCount % 20 != 0) {
            return;
        }
        if (!"look".equals(TownGuests.stageOf(p, QUEST)) || !looksAtMoon(p)) {
            return;
        }
        Notice.narrate(p, "message.skyabyss.moon_near.seen");
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.AMBIENT, 1.0f, 0.5f);
        Quests.signal(p, SIGNAL);
    }
}
