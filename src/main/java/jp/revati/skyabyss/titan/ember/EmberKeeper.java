package jp.revati.skyabyss.titan.ember;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.ProgressData;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.TitanKeeper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

// 余燼の巨神の出現と見守り (13 の 2.1節と 2.8節)。仕組みは巨神で共通の TitanKeeper (記録は TitanWorldData の Titans/ember)。余燼に固有のこと:
// - 出現: ワールドの誰かが雷翼の心臓を止めた日の、次の日の出 (朝 0〜6000 のあいだ)。地の中を泳ぐ姿で現れ、最初の夜に初めて眠る。
//   選んだ人から 200〜320 ブロック離れた陸 (海・深海・川を除き、地表が海面より 2 以上高い) を、読み込まれているチャンクから探す。
//   見つからなければ、1 分後に次の人でやり直す。通知はしない。出現点から 160 ブロック以内の人に、地の底のうなりを一度だけ鳴らす
// - 呼び直しの位置も同じ探し方。陸が見つからなければ 2 分待ってやり直す (TitanKeeper)
// - 背中 (ember_back) に人がいる間は呼び直さない
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class EmberKeeper {
    public static final String SLOT = EmberIds.TITAN;
    static final double SPAWN_MIN = 200;
    static final double SPAWN_MAX = 320;
    static final int DAWN_END = 6000;
    private static final int RETRY = 1200;

    private static long nextTry;

    public static final TitanKeeper<EmberTitanEntity> KEEPER = new TitanKeeper<>(SLOT, EmberTitanEntity.class,
            "skyabyss_ember", "ember titan") {
        @Override
        protected EmberTitanEntity create(ServerLevel level, double x, double z, @Nullable CompoundTag state) {
            EmberTitanEntity titan = EmberTitanSetup.EMBER_TITAN.get().create(level);
            if (titan == null) {
                throw new IllegalStateException("Failed to create the ember titan");
            }
            double surface = EmberGround.surfaceY(level, x, z);
            double y = (Double.isNaN(surface) ? level.getSeaLevel() : surface) - EmberTitanEntity.BURROW_SINK;
            titan.moveTo(x, y, z, level.random.nextFloat() * 360f, 0f);
            if (state != null && !state.isEmpty()) {
                titan.restoreState(state);
            }
            return titan;
        }

        @Override
        protected CompoundTag saveState(EmberTitanEntity titan) {
            return titan.saveState();
        }

        @Override
        @Nullable
        protected Vec3 relocationPoint(ServerLevel level, ServerPlayer target) {
            return EmberGround.findLandPoint(level, level.random, target.getX(), target.getZ(), SPAWN_MIN, SPAWN_MAX, 32);
        }

        @Override
        protected boolean someoneAboard(ServerLevel overworld) {
            ServerLevel back = overworld.getServer().getLevel(EmberIds.BACK);
            return back != null && !back.players().isEmpty();
        }

        @Override
        protected void announce(ServerLevel level, EmberTitanEntity titan) {
            // 知らせない。最初の出会いは「丘が動いた」「灰が降る」と自分で気づく形にする (呼び直しも黙って行う)
        }
    };

    private EmberKeeper() {
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }
        KEEPER.tick(level);
        long now = level.getGameTime();
        if (now % 200 != 0 || now < nextTry || KEEPER.slot(level).spawned || !canAppear(level)) {
            return;
        }
        ServerPlayer player = TitanKeeper.randomEligible(level);
        Vec3 at = player == null ? null
                : EmberGround.findLandPoint(level, level.random, player.getX(), player.getZ(), SPAWN_MIN, SPAWN_MAX, 32);
        if (at != null) {
            EmberTitanEntity titan = KEEPER.spawn(level, at.x, at.z, null);
            murmur(level, titan);
            return;
        }
        nextTry = now + RETRY;
    }

    // 出現の条件 (2.1節): 雷翼の心臓をワールドの誰かが止めた (ゲームルール skyabyssStoryGate で切れる)、その次の日の出。
    // 「次の」は、止めたあとに一度夜 (12000 以降) を見てから、か、止めた日 (総経過 tick の日) から 2 日以上たっていること
    static boolean canAppear(ServerLevel level) {
        if (!Progression.worldAllows(level.getServer(), Titans.EMBER)) {
            armed = false;
            return false;
        }
        long tod = EmberGround.timeOfDay(level.getDayTime());
        if (tod >= 12000 || level.getGameTime() / 24000L > stoppedDay(level) + 1) {
            armed = true;
        }
        return armed && tod < DAWN_END;
    }

    // 止めたあとに夜を見たか (サーバーを止めると忘れる。そのときは次の夜を待つだけ)
    private static boolean armed;

    // 雷翼を止めた日 (記録がなければ -1: 決まりを切っている・古いワールド)
    static long stoppedDay(ServerLevel level) {
        Long day = ProgressData.get(level.getServer()).worldFirstDay.get(Titans.THUNDER_WING);
        return day == null ? -1 : day;
    }

    // 出現のとき、出現点から 160 ブロック以内の人に地の底のうなりを一度だけ鳴らす (本人にだけ聞こえる)
    static void murmur(ServerLevel level, EmberTitanEntity titan) {
        for (ServerPlayer player : level.players()) {
            if (TitanKeeper.eligible(player) && player.distanceToSqr(titan.getX(), player.getY(), titan.getZ()) < 160 * 160) {
                player.playNotifySound(EmberTitanSetup.RUMBLE.get(), SoundSource.AMBIENT, 0.7f, 0.6f);
            }
        }
    }

    @SubscribeEvent
    static void onJoin(EntityJoinLevelEvent event) {
        KEEPER.handleJoin(event);
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        KEEPER.reset();
        nextTry = 0;
        armed = false;
    }
}
