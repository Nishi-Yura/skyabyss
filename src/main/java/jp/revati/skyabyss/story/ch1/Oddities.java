package jp.revati.skyabyss.story.ch1;

import java.util.Optional;

import org.joml.Vector3f;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.cinematic.Cinematics;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.opening.OpeningDirector;
import jp.revati.skyabyss.opening.OpeningSetup;
import jp.revati.skyabyss.opening.OpeningSites;
import jp.revati.skyabyss.quest.Repeats;
import jp.revati.skyabyss.story.Chapters;
import jp.revati.skyabyss.story.arc.ArcData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 小さな異変の束 (E7。docs/design/17 の 1.5): 序章と一章の間、一日に一つだけ、昼のうちに、ささやかな「違い」を起こす (日 1 から)。
// 鳴き声が一分途切れる／遠い鐘が逆の方向から一度／足跡が一歩多い／昼なのに息が白い。見せるのは音・粒・字幕だけ。ダメージ・拘束・モブの湧きは触らない。
// 決まり (17 の 5 章): 戦闘中・体力が低い・敵が近い・水中・睡眠中・影の最中・足音や歌の演出の最中・場面の最中は後ろへずらす。予兆の拍と続けて起こさない。
//   horrorEventFrequency 0 では起こさない。人ごと・本人にだけ。種類は日ごとに替わり、同じ種類は使い切るまで繰り返さない。3 つ目で「小さな違い」の頁
public final class Oddities {
    public enum Kind {
        QUIET, BELL, STEP, COLD
    }

    // その日の何 tick 目から起こしてよいか (人ごとに 5000〜10999 の間でずらす。最初の空の影の窓 4800〜10500 とは、影の最中を避けることで重ならない)
    static final int WINDOW_FROM = 5000;
    static final int WINDOW_SPAN = 6000;
    // 起きたあと、予兆の拍から空ける間隔
    static final long BEAT_GAP = 2400;
    // 目覚めから、最初の異変まで
    static final long FIRST_AFTER = 3600;
    // 自己テスト用に下げられる
    static long firstAfter = FIRST_AFTER;
    private static final DustParticleOptions HOLLOW = new DustParticleOptions(new Vector3f(0.16f, 0.12f, 0.09f), 0.9f);

    private Oddities() {
    }

    // 日 day に、使った種類のビット mask のとき、どの種類にするか (純粋)。日ごとに回し、使い切るまで同じものを選ばない
    public static Kind pick(long day, int mask) {
        Kind[] all = Kind.values();
        int start = (int) Math.floorMod(day * 3 + 1, (long) all.length);
        for (int i = 0; i < all.length; i++) {
            Kind k = all[(start + i) % all.length];
            if ((mask & (1 << k.ordinal())) == 0) {
                return k;
            }
        }
        return all[start];
    }

    // その人のその日の起こしてよい時刻 (一日の中の tick。純粋)
    public static int dueTick(java.util.UUID player, long day) {
        int h = (int) ((player.getLeastSignificantBits() * 31 + day * 0x9E3779B1L) & 0x7fffffff);
        return WINDOW_FROM + (h % WINDOW_SPAN);
    }

    // 昼のうちか (一日の時刻。起こしてよい範囲の判定。純粋)
    public static boolean daytime(long dayTime) {
        long t = Math.floorMod(dayTime, 24000L);
        return t >= WINDOW_FROM && t <= 11500;
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer || player.isSpectator()) {
            return;
        }
        int phase = (player.getUUID().hashCode() & 0x7fffffff) % 20;
        if (player.tickCount % 20 != phase || player.tickCount < 600) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }
        Chapters chapter = Chapters.of(player);
        if (chapter != Chapters.PROLOGUE && chapter != Chapters.WHALE) {
            return;
        }
        tryOdd(player, level);
    }

    // 条件が合えば今日の分を起こす。起こしたら true
    static boolean tryOdd(ServerPlayer player, ServerLevel level) {
        if (Config.HORROR_EVENT_FREQUENCY.get() <= 0 || level.getGameTime() < firstAfter) {
            return false;
        }
        long day = Repeats.day(level.getServer());
        Ch1Data data = Ch1Data.get(level.getServer().overworld());
        Ch1Data.Player st = data.player(player.getUUID());
        long tod = Math.floorMod(level.getDayTime(), 24000L);
        if (st.oddityDay == day || !daytime(level.getDayTime()) || tod < dueTick(player.getUUID(), day)) {
            return false;
        }
        if (Cinematics.active(player) || !OpeningDirector.quiet(level, player)) {
            return false;
        }
        long last = ArcData.of(player).lastBeatAt();
        if (last >= 0 && level.getGameTime() - last < BEAT_GAP) {
            return false;
        }
        Kind kind = pick(day, st.oddityMask);
        st.oddityDay = day;
        st.oddityMask |= 1 << kind.ordinal();
        st.oddityCount++;
        data.setDirty();
        fire(player, level, kind);
        if (st.oddityCount >= 3) {
            Lore.unlock(player, Ch1.WRONGS, true);
        }
        return true;
    }

    // 起こす (自己テストからも呼ぶ)
    static void fire(ServerPlayer player, ServerLevel level, Kind kind) {
        switch (kind) {
            case QUIET -> quiet(player, level);
            case BELL -> bell(player, level);
            case STEP -> step(player, level);
            case COLD -> cold(player, level);
        }
    }

    // 一分の静けさ: 環境音・動物の声を止め続け、近くの動物を固まらせる
    private static void quiet(ServerPlayer player, ServerLevel level) {
        Notice.narrate(player, "message.skyabyss.ch1.oddity.quiet");
        for (int i = 0; i < 120; i++) {
            Tasks.later(i * 10, () -> {
                if (player.isRemoved() || player.connection == null || player.level() != level) {
                    return;
                }
                player.connection.send(new ClientboundStopSoundPacket(null, SoundSource.AMBIENT));
                player.connection.send(new ClientboundStopSoundPacket(null, SoundSource.NEUTRAL));
                if (player.tickCount % 2 == 0) {
                    for (Animal animal : level.getEntitiesOfClass(Animal.class, player.getBoundingBox().inflate(24))) {
                        animal.getNavigation().stop();
                        animal.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 14, 6, false, false));
                    }
                }
            });
        }
    }

    // 逆の方角からの鐘: 宿りと反対側、90 ブロック先から、宿りの鐘と同じ音が一度だけ
    private static void bell(ServerPlayer player, ServerLevel level) {
        Optional<BlockPos> lodge = OpeningSites.bell(level.getServer()).or(() -> OpeningSites.shelter(level.getServer()));
        double ax;
        double az;
        if (lodge.isPresent()) {
            ax = player.getX() - (lodge.get().getX() + 0.5);
            az = player.getZ() - (lodge.get().getZ() + 0.5);
        } else {
            ax = level.random.nextDouble() - 0.5;
            az = level.random.nextDouble() - 0.5;
        }
        double len = Math.max(1.0e-3, Math.hypot(ax, az));
        double x = player.getX() + ax / len * 90;
        double z = player.getZ() + az / len * 90;
        double y = player.getY() + 4;
        if (player.connection != null) {
            player.connection.send(new ClientboundSoundPacket(OpeningSetup.BELL, SoundSource.AMBIENT, x, y, z, 1.0f, 1.0f, level.random.nextLong()));
        }
        Tasks.later(30, () -> Notice.narrate(player, "message.skyabyss.ch1.oddity.bell"));
    }

    // 足跡が一歩多い: 本人の通ってきた道の少し後ろに、足の形の粒がしばらく残る
    private static void step(ServerPlayer player, ServerLevel level) {
        Vec3 flat = player.getLookAngle().multiply(1, 0, 1);
        Vec3 look = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
        double bx = player.getX() - look.x * 5.0;
        double bz = player.getZ() - look.z * 5.0;
        for (int i = 0; i < 10; i++) {
            Tasks.later(10 + i * 8, () -> {
                if (player.isRemoved() || player.level() != level) {
                    return;
                }
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(bx), (int) Math.floor(bz));
                // かかと・土踏まず・つま先の 3 点
                for (int k = 0; k < 3; k++) {
                    level.sendParticles(player, HOLLOW, true, bx + look.x * (k * 0.35 - 0.35), y + 0.06, bz + look.z * (k * 0.35 - 0.35), 3, 0.05, 0, 0.05, 0);
                }
            });
        }
        Tasks.later(40, () -> Notice.narrate(player, "message.skyabyss.ch1.oddity.step"));
    }

    // 昼なのに息が白い: ほんの一呼吸ぶん、顔の前に白い息
    private static void cold(ServerPlayer player, ServerLevel level) {
        for (int i = 0; i < 5; i++) {
            Tasks.later(i * 5, () -> {
                if (player.isRemoved() || player.level() != level) {
                    return;
                }
                Vec3 face = player.getEyePosition().add(player.getLookAngle().scale(0.5));
                level.sendParticles(player, ParticleTypes.CLOUD, true, face.x, face.y - 0.1, face.z, 3, 0.04, 0.02, 0.04, 0.006);
            });
        }
        Notice.narrate(player, "message.skyabyss.ch1.oddity.cold");
    }
}
