package jp.revati.skyabyss.story;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.world.continent.ContinentLairs;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 章ごとの「縁の音」と「暮らしの変化」(docs/design/01 の 4.1・4.2。巨神編の一〜四章)。どちらも本人にだけ。
//   縁の音: 町の外の野にいるとき、12〜20 分おきに一度、その章の巨神の居場所の方角 (48 ブロック先) から合成音の予兆が届く。
//          一章 鯨の歌 (titan_song)・二章 遠い貝 (skyabyss_ch2:conch_far)・三章 風に逆らう雷 (storm_thunder_far)・四章 地の底のうなり (ember_rumble)
//   暮らしの変化 (鐘の都の中): 一章 鐘楼の鐘がときどき低く鳴る・二章 風に塩の粒が混じる・三章 風に逆らう小さなつむじ風。
//          その章で初めて都に入ったとき一行。四章は寝床のまわりの温かさ (titan/ember/EmberChapter)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ChapterOmens {
    static final int CHECK = 40;
    static final long OMEN_MIN = 20L * 60 * 12;
    static final long OMEN_MAX = 20L * 60 * 20;
    static final double OMEN_DIST = 48;
    static final String NEXT = "skyabyss_omen_next";
    static final String LIFE_SEEN = "skyabyss_life_seen_";
    static final double HOSTILE_NEAR = 16;
    private static final Map<UUID, Long> BELL_AT = new HashMap<>();

    private ChapterOmens() {
    }

    // 縁の音の名前 (章 → 音)。巨神編でなければ null
    public static ResourceLocation omenSound(Chapters c) {
        return switch (c) {
            case WHALE -> ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "titan_song");
            case TIDE -> ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID + "_ch2", "conch_far");
            case STORM -> ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "storm_thunder_far");
            case EMBER -> ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "ember_rumble");
            default -> null;
        };
    }

    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer || p.isSpectator() || (p.tickCount + p.getId()) % CHECK != 0
                || p.level().dimension() != Level.OVERWORLD || !(p.level() instanceof ServerLevel level)) {
            return;
        }
        Chapters c = Chapters.of(p);
        if (c.titan() == null || jp.revati.skyabyss.story.reel.StoryReels.watching(p) || jp.revati.skyabyss.cinematic.Cinematics.active(p)) {
            return;
        }
        if (jp.revati.skyabyss.opening.site.SiteGuard.inCapital(level, p.blockPosition())) {
            life(level, p, c);
        } else if (!jp.revati.skyabyss.opening.site.SiteGuard.inTown(level, p.blockPosition())) {
            omen(level, p, c);
        }
    }

    // ---------------------------------------------------------------- 縁の音
    static void omen(ServerLevel level, ServerPlayer p, Chapters c) {
        long now = level.getGameTime();
        long next = p.getPersistentData().getLong(NEXT);
        if (next == 0 || next > now + OMEN_MAX * 2) {
            p.getPersistentData().putLong(NEXT, now + span(level));
            return;
        }
        if (now < next || !level.getEntitiesOfClass(Monster.class, p.getBoundingBox().inflate(HOSTILE_NEAR)).isEmpty()) {
            return;
        }
        ContinentLairs.Lair lair = ContinentLairs.all().get(c.titan());
        ResourceLocation id = omenSound(c);
        // 名前空間の違う音 (二章の skyabyss_ch2) も名前で送る (画面の側が sounds.json から引く)
        SoundEvent ev = id == null ? null : SoundEvent.createVariableRangeEvent(id);
        if (lair == null || ev == null) {
            return;
        }
        double dx = lair.x() - p.getX();
        double dz = lair.z() - p.getZ();
        double d = Math.max(1, Math.hypot(dx, dz));
        double x = p.getX() + dx / d * OMEN_DIST;
        double z = p.getZ() + dz / d * OMEN_DIST;
        p.connection.send(new ClientboundSoundPacket(Holder.direct(ev), SoundSource.AMBIENT, x, p.getY() + 12, z, 4.0f, 0.9f + level.random.nextFloat() * 0.15f,
                level.random.nextLong()));
        p.getPersistentData().putLong(NEXT, now + span(level));
    }

    static long span(ServerLevel level) {
        return OMEN_MIN + (long) (level.random.nextDouble() * (OMEN_MAX - OMEN_MIN));
    }

    // ---------------------------------------------------------------- 暮らしの変化 (都の中)
    static void life(ServerLevel level, ServerPlayer p, Chapters c) {
        if (c.ordinal() > Chapters.STORM.ordinal()) {
            return;
        }
        String seen = LIFE_SEEN + c.id();
        if (!p.getPersistentData().getBoolean(seen)) {
            p.getPersistentData().putBoolean(seen, true);
            Notice.narrate(p, "message.skyabyss.life." + c.id());
        }
        switch (c) {
            case WHALE -> {
                // 鐘楼の鐘が、ときどき低く鳴る (鯨の歌と同じ高さ)。90〜150 秒に一度
                long now = level.getGameTime();
                long at = BELL_AT.getOrDefault(p.getUUID(), 0L);
                if (now >= at) {
                    BELL_AT.put(p.getUUID(), now + 1800 + level.random.nextInt(1200));
                    if (at != 0) {
                        p.playNotifySound(SoundEvents.BELL_RESONATE, SoundSource.AMBIENT, 0.6f, 0.5f);
                    }
                }
            }
            case TIDE -> drift(level, p, ParticleTypes.WHITE_ASH, 10, 0.0);
            case STORM -> drift(level, p, ParticleTypes.SMALL_GUST, 1, 0.02);
            default -> {
            }
        }
    }

    static void drift(ServerLevel level, ServerPlayer p, ParticleOptions particle, int count, double speed) {
        for (int i = 0; i < count; i++) {
            double x = p.getX() + (level.random.nextDouble() - 0.5) * 14;
            double y = p.getY() + 0.5 + level.random.nextDouble() * 4;
            double z = p.getZ() + (level.random.nextDouble() - 0.5) * 14;
            level.sendParticles(p, particle, false, x, y, z, 1, 0.1, 0.1, 0.1, speed);
        }
    }
}
