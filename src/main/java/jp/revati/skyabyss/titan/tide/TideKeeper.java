package jp.revati.skyabyss.titan.tide;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.sky.SkyEventData;
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

// 潮殻の巨神の出現と見守り (08 の 2.1節と 2.7節)。仕組みは巨神で共通の TitanKeeper。潮殻に固有のこと:
// - 出現: ワールドの総経過 tick が 4 日分 (96000) を超え、空の通過が一度起きたあと (通過を切っている設定では通過を待たない)。
//   選んだ人から 200〜320 ブロック離れた、海のバイオームで水深 12 以上の点を、読み込まれているチャンクから探す。
//   見つからなければ、1 分後に次の人でやり直す。通知はしない。出現のとき、近くの海辺の人に遠い潮騒を一度だけ鳴らす
// - 呼び直しの位置も同じ探し方。海が見つからなければ、最後の位置のまま待つ (2 分後にやり直す)
// - 背中 (tide_back) に人がいる間は呼び直さない
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TideKeeper {
    public static final String SLOT = "tide_shell";
    static final long FIRST_APPEARANCE_TICK = 96000;
    static final double SPAWN_MIN = 200;
    static final double SPAWN_MAX = 320;
    static final int SPAWN_DEPTH = 12;
    private static final int RETRY = 1200;

    private static long nextTry;

    public static final TitanKeeper<TideShellEntity> KEEPER = new TitanKeeper<>(SLOT, TideShellEntity.class,
            "skyabyss_tide_shell", "tide shell titan") {
        @Override
        protected TideShellEntity create(ServerLevel level, double x, double z, @Nullable CompoundTag state) {
            TideShellEntity titan = TideSetup.TIDE_SHELL.get().create(level);
            if (titan == null) {
                throw new IllegalStateException("Failed to create the tide shell titan");
            }
            titan.moveTo(x, level.getSeaLevel() - TideShellEntity.CRUISE_DRAFT_MAX, z, level.random.nextFloat() * 360f, 0f);
            if (state != null && !state.isEmpty()) {
                titan.restoreState(state);
            }
            return titan;
        }

        @Override
        protected CompoundTag saveState(TideShellEntity titan) {
            return titan.saveState();
        }

        @Override
        @Nullable
        protected Vec3 relocationPoint(ServerLevel level, ServerPlayer target) {
            return TideOcean.findOceanPoint(level, level.random, target.getX(), target.getZ(), SPAWN_MIN, SPAWN_MAX, SPAWN_DEPTH, 32);
        }

        @Override
        protected boolean someoneAboard(ServerLevel overworld) {
            ServerLevel back = overworld.getServer().getLevel(TideTravel.TIDE_BACK);
            return back != null && jp.revati.skyabyss.titan.group.Activity.anyoneAboard(back);
        }

        // 巨神での移動の最中は、動きと読み込みは旅の操縦 (titan.travel.SeaVoyage) が受け持つ
        @Override
        protected boolean inVoyage(TideShellEntity titan) {
            return titan.inVoyage();
        }

        @Override
        protected void announce(ServerLevel level, TideShellEntity titan) {
            // 知らせない。最初の出会いは「沖の島が動いた」と自分で気づく形にする (呼び直しも黙って行う)
        }
    };

    private TideKeeper() {
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
        // 一人を選んで試す。見つからなければ、1 分後に (次に選ばれた人で) やり直す (08 の 2.1節)
        ServerPlayer player = TitanKeeper.randomEligible(level);
        Vec3 at = player == null ? null
                : TideOcean.findOceanPoint(level, level.random, player.getX(), player.getZ(), SPAWN_MIN, SPAWN_MAX, SPAWN_DEPTH, 32);
        if (at != null) {
            TideShellEntity titan = KEEPER.spawn(level, at.x, at.z, null);
            murmur(level, titan);
            return;
        }
        nextTry = now + RETRY;
    }

    // 出現の条件 (08 の 2.1節、15 章の判断 4 は推奨 A): 4 日分たち、空の通過が一度起きたあと。加えて岩鯨の心臓が止まったあと (10 の 3 章)
    static boolean canAppear(ServerLevel level) {
        if (level.getGameTime() < FIRST_APPEARANCE_TICK) {
            return false;
        }
        // 前の巨神 (岩鯨) の心臓を、ワールドの誰かが止めたあと (10 の 3 章。ゲームルール skyabyssStoryGate で切れる)
        if (!jp.revati.skyabyss.progress.Progression.worldAllows(level.getServer(), SLOT)) {
            return false;
        }
        return Config.HORROR_EVENT_FREQUENCY.get() <= 0 || SkyEventData.get(level).passCount() > 0;
    }

    // 出現のとき、320 ブロック以内の人に遠い潮騒を一度だけ鳴らす (本人にだけ聞こえる)
    static void murmur(ServerLevel level, TideShellEntity titan) {
        for (ServerPlayer player : level.players()) {
            if (TitanKeeper.eligible(player) && player.distanceToSqr(titan.getX(), player.getY(), titan.getZ()) < 320 * 320) {
                player.playNotifySound(TideSetup.SURF.get(), SoundSource.AMBIENT, 0.6f, 0.85f);
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
    }
}
