package jp.revati.skyabyss.titan.travel;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.loot.TitanMemoryData;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.pouch.PouchRouting;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.StoneWhaleEntity;
import jp.revati.skyabyss.titan.TitanStay;
import jp.revati.skyabyss.titan.TitanWorldData;
import jp.revati.skyabyss.titan.WhaleKeeper;
import jp.revati.skyabyss.titan.end.TitanDormancy;
import jp.revati.skyabyss.titan.ember.EmberKeeper;
import jp.revati.skyabyss.titan.ember.EmberTitanEntity;
import jp.revati.skyabyss.titan.ember.EmberTitanSetup;
import jp.revati.skyabyss.titan.group.Activity;
import jp.revati.skyabyss.titan.storm.StormKeeper;
import jp.revati.skyabyss.titan.storm.StormPerch;
import jp.revati.skyabyss.titan.storm.StormSetup;
import jp.revati.skyabyss.titan.storm.StormWingEntity;
import jp.revati.skyabyss.titan.tide.TideKeeper;
import jp.revati.skyabyss.titan.tide.TideShellEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 巨神の「呼び」 (docs/design/09 の 4.4節): 出会った巨神は、その呼び笛でいつでも呼べる。昼夜・潮・嵐の刻・一章の降下の条件に関わらず、
// 巨神が吹いた人の近くへ来て、背へ乗れる姿で 20 分 (TitanStay。無人 3 分で去る決まりも同じ) 待つ。
//   岩鯨: 鎖を垂らして降りてくる。潮殻: いちばん近い浅瀬へ寄って潮門を開ける。雷翼: 近くの高い峰へ降りて止まり、昇り風を立てる。余燼: 近くの陸に浮かんで、口を開け閉めする
// 出会い (met) = 乗れる姿を見た (降りて止まった巨神の近く)・背へ入った・心臓を倒した。最初の出会いの場面・時刻は変えない (これは呼びだけ)。
// 眠っている巨神は応えない (目覚めまでの目安を伝える)。笛は出会った時に一度だけ渡る (なくしたら作業台で作り直せる)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TitanCalls {
    public enum Result { CALLED, ALREADY_HERE, REFUSED }

    // 出会いとみなす距離 (巨神が乗れる姿で止まっているとき)
    public static final double MEET_RANGE = 200;
    // すぐそこにいるとみなす距離 (呼ばずに「ここにいる」と答える)
    public static final double HERE_RANGE = 128;
    // 別の巨神がこの距離 (水平) 以内にいるあいだは、呼びを断る (巨神の近くで別の巨神を呼ばない。TitanStay.LEAVE_RANGE と同じ)
    public static final double NEAR_TITAN_RANGE = 160;
    // 遠くから呼び寄せるときの現れる距離
    private static final double ENTRY = 260;
    // 巨神ごとの、呼び寄せに頼る距離 (これより遠ければ、現れ直す)
    private static final double FAR_SKY = 480;
    private static final double FAR_STORM = 320;

    private static final String LANG = "message.skyabyss.titan_call.";

    // 自己テスト用: 余燼の眠る場所を決める (本物の地形に長い平らな陸がないときの代わり)
    static java.util.function.Function<ServerPlayer, jp.revati.skyabyss.titan.ember.EmberGround.Spot> emberSpotOverride;

    private TitanCalls() {
    }

    // ---------------------------------------------------------------- 巨神の一覧

    public static boolean known(String id) {
        return Titans.STONE_WHALE.equals(id) || Titans.TIDE_SHELL.equals(id) || Titans.THUNDER_WING.equals(id) || Titans.EMBER.equals(id);
    }

    public static Item callItem(String id) {
        return switch (id) {
            case Titans.STONE_WHALE -> TravelSetup.STONE_WHALE_CALL.get();
            case Titans.TIDE_SHELL -> TravelSetup.TIDE_SHELL_CALL.get();
            case Titans.THUNDER_WING -> TravelSetup.THUNDER_WING_CALL.get();
            case Titans.EMBER -> TravelSetup.EMBER_CALL.get();
            default -> throw new IllegalArgumentException("unknown titan " + id);
        };
    }

    // ---------------------------------------------------------------- 出会い

    // 出会っているか: 記録にある、または心臓を倒している (巨神の記憶・コマンドの認め)
    public static boolean met(ServerPlayer player, String id) {
        if (Voyages.state(player).met.contains(id)) {
            return true;
        }
        TitanMemoryData.Record r = TitanMemoryData.get(player.server).peek(player.getUUID(), id);
        if (r != null && r.clears > 0) {
            return true;
        }
        return Voyages.state(player).unlocked.contains(id);
    }

    // 出会ったと記す (記録がなければ true)
    public static boolean markMet(ServerPlayer player, String id) {
        return Voyages.state(player).met.add(id);
    }

    // 乗れる姿を、プレイヤーが見ているか (巨神が乗れる姿で止まっていて、MEET_RANGE 以内)。眠っている巨神は数えない
    static boolean sees(ServerPlayer player, String id) {
        if (player.level().dimension() != Level.OVERWORLD || TitanDormancy.isResting(player.server, id)) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        Vec3 at = boardable(level, id);
        return at != null && Math.hypot(at.x - player.getX(), at.z - player.getZ()) <= MEET_RANGE;
    }

    // 巨神が今、乗れる姿で止まっている位置 (止まっていなければ null)
    @Nullable
    static Vec3 boardable(ServerLevel level, String id) {
        switch (id) {
            case Titans.STONE_WHALE -> {
                StoneWhaleEntity whale = WhaleKeeper.current(level);
                return whale != null && whale.isResting() ? whale.position() : null;
            }
            case Titans.TIDE_SHELL -> {
                TideShellEntity shell = TideKeeper.KEEPER.current(level);
                return shell != null && shell.isResting() ? shell.position() : null;
            }
            case Titans.THUNDER_WING -> {
                StormWingEntity wing = StormKeeper.KEEPER.current(level);
                return wing != null && wing.isPerched() ? wing.position() : null;
            }
            case Titans.EMBER -> {
                EmberTitanEntity ember = EmberKeeper.KEEPER.current(level);
                // 乗れるのは、浮かびきって眠っている (口が開け閉めされる) あいだ。浮かんでいる途中はまだ口がない
                return ember != null && ember.mode() == EmberTitanEntity.Mode.SLEEP ? ember.position() : null;
            }
            default -> {
                return null;
            }
        }
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 40 != 11 || !player.isAlive() || player.isSpectator()) {
            return;
        }
        meetAndGive(player);
    }

    // 出会いを調べ、出会った巨神の笛を一度だけ渡す。渡した数を返す (旅のできる巨神の、心臓のあとの笛は Voyages.giveCalls が渡す)
    public static int meetAndGive(ServerPlayer player) {
        TravelState s = Voyages.state(player);
        int given = 0;
        for (String id : Titans.all()) {
            if (!known(id)) {
                continue;
            }
            if (!s.met.contains(id)) {
                boolean in = id.equals(Titans.titanOfBack(player.level().dimension()));
                if (in || sees(player, id)) {
                    s.met.add(id);
                }
            }
            if (!met(player, id) || s.callGiven.contains(id)) {
                continue;
            }
            TitanKind kind = TitanKind.byId(id);
            if (kind != null && Voyages.isUnlocked(player, kind)) {
                // 心臓を倒した巨神の笛は、Voyages.giveCalls が認めの言葉と一緒に渡す
                continue;
            }
            s.callGiven.add(id);
            given++;
            ItemStack call = new ItemStack(callItem(id));
            PouchRouting.give(player, call);
            if (!call.isEmpty()) {
                player.drop(call, false);
            }
            Notice.narrate(player, LANG + "met." + id);
            Notice.hint(player, LANG + "met_hint");
            player.playNotifySound(callSound(id), SoundSource.PLAYERS, 0.6f, 1.2f);
        }
        return given;
    }

    private static net.minecraft.sounds.SoundEvent callSound(String id) {
        return switch (id) {
            case Titans.TIDE_SHELL -> TravelSetup.TIDE_CALL_SOUND.get();
            case Titans.THUNDER_WING -> StormSetup.WING_CALL.get();
            case Titans.EMBER -> EmberTitanSetup.RUMBLE.get();
            default -> TravelSetup.CALL_SOUND.get();
        };
    }

    // ---------------------------------------------------------------- 呼ぶ

    // 笛を吹いた。bypass はテスト用 (出会い・空・乗り物を見ない)。眠りは bypass でも見る
    public static Result call(ServerPlayer player, String id, boolean bypass) {
        if (!known(id)) {
            return Result.REFUSED;
        }
        Component name = Progression.titanName(id);
        if (player.level().dimension() != Level.OVERWORLD) {
            Notice.bar(player, ModText.deny(LANG + "wrong_dimension", name));
            return Result.REFUSED;
        }
        if (TitanDormancy.isResting(player.server, id)) {
            sleeping(player, id, name);
            return Result.REFUSED;
        }
        if (!bypass) {
            if (!met(player, id)) {
                Notice.bar(player, ModText.deny(LANG + "not_met", name));
                return Result.REFUSED;
            }
            if (player.isPassenger()) {
                Notice.bar(player, ModText.deny("message.skyabyss.travel.refuse.riding"));
                return Result.REFUSED;
            }
            if (skyTitan(id) && !TravelRules.openSky(player)) {
                Notice.bar(player, ModText.deny(LANG + "no_sky", name));
                return Result.REFUSED;
            }
        }
        ServerLevel level = player.server.overworld();
        String near = nearTitan(level, player, id);
        if (near != null) {
            Notice.bar(player, ModText.hint(LANG + "near_titan", Progression.titanName(near)));
            player.playNotifySound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.5f, 0.7f);
            return Result.REFUSED;
        }
        TitanWorldData data = TitanWorldData.get(level);
        TitanWorldData.Slot slot = data.slot(id);
        if (!slot.spawned) {
            Notice.bar(player, ModText.hint(LANG + "absent", name));
            return Result.REFUSED;
        }
        Vec3 here = boardable(level, id);
        if (here == null && Titans.EMBER.equals(id)) {
            // 余燼が浮かんでいる途中なら、「ここにいる」と答える (呼び直して浮かび直させない)
            EmberTitanEntity rising = EmberKeeper.KEEPER.current(level);
            here = rising != null && rising.mode() == EmberTitanEntity.Mode.RISE ? rising.position() : null;
        }
        if (here != null && Math.hypot(here.x - player.getX(), here.z - player.getZ()) <= HERE_RANGE) {
            Notice.bar(player, ModText.hint(LANG + "here", name));
            return Result.ALREADY_HERE;
        }
        if (busy(level, id, here != null)) {
            Notice.bar(player, ModText.deny(LANG + "busy", name));
            return Result.REFUSED;
        }
        boolean ok = switch (id) {
            case Titans.STONE_WHALE -> callWhale(level, player);
            case Titans.TIDE_SHELL -> callTide(level, player);
            case Titans.THUNDER_WING -> callWing(level, player);
            default -> callEmber(level, player);
        };
        if (!ok) {
            Notice.bar(player, ModText.deny(LANG + "no_place." + id, name));
            return Result.REFUSED;
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), callSound(id), SoundSource.PLAYERS, 4f, 1f);
        Notice.narrate(player, LANG + "called." + id);
        SkyAbyss.LOGGER.info("{} called the {} near {}", player.getGameProfile().getName(), id, player.blockPosition());
        return Result.CALLED;
    }

    // 呼ぶ巨神 except 以外で、player から NEAR_TITAN_RANGE (水平) 以内にいる巨神の id (いなければ null)。止まっていても飛んでいても数える
    @Nullable
    public static String nearTitan(ServerLevel level, ServerPlayer player, String except) {
        for (String other : Titans.all()) {
            if (!known(other) || other.equals(except)) {
                continue;
            }
            net.minecraft.world.entity.Entity e = switch (other) {
                case Titans.STONE_WHALE -> WhaleKeeper.current(level);
                case Titans.TIDE_SHELL -> TideKeeper.KEEPER.current(level);
                case Titans.THUNDER_WING -> StormKeeper.KEEPER.current(level);
                default -> EmberKeeper.KEEPER.current(level);
            };
            if (e != null && e.isAlive() && Math.hypot(e.getX() - player.getX(), e.getZ() - player.getZ()) <= NEAR_TITAN_RANGE) {
                return other;
            }
        }
        return null;
    }

    private static boolean skyTitan(String id) {
        return Titans.STONE_WHALE.equals(id) || Titans.THUNDER_WING.equals(id);
    }

    // 眠っている巨神へ: 目覚めまでの目安 (決まっていれば) を添える
    private static void sleeping(ServerPlayer player, String id, Component name) {
        TitanDormancy.Rest rest = TitanDormancy.rest(player.server, id);
        long now = player.server.overworld().getGameTime();
        if (rest != null && rest.wakeAt != Long.MAX_VALUE && rest.wakeAt > now) {
            long minutes = Math.max(1, (rest.wakeAt - now + 1199) / 1200);
            Notice.bar(player, ModText.hint(LANG + "sleeping", name, minutes));
        } else {
            Notice.bar(player, ModText.deny("message.skyabyss.titan_end.sleeping_call", name));
        }
    }

    // 巨神が、ほかの人の用で手が離せないか: 旅の最中・背の鎖を上っている人がいる・止まっているのが遠く、その背に人がいる
    private static boolean busy(ServerLevel level, String id, boolean restingElsewhere) {
        TitanKind kind = TitanKind.byId(id);
        if (kind != null && (Voyages.isBusy(kind) || kind.busyAboard().get())) {
            return true;
        }
        if (restingElsewhere) {
            ResourceKey<Level> backKey = Titans.backOf(id);
            ServerLevel back = backKey == null ? null : level.getServer().getLevel(backKey);
            return back != null && Activity.anyoneAboard(back);
        }
        return false;
    }

    // 巨神が遠いとき (または読み込まれていないとき) に現れる点: 前にいた方角の ENTRY ブロック先
    private static Vec3 entryPoint(ServerLevel level, ServerPlayer player, @Nullable Vec3 from) {
        double angle = from != null ? Math.atan2(from.z - player.getZ(), from.x - player.getX()) : level.random.nextDouble() * Math.PI * 2;
        return new Vec3(player.getX() + Math.cos(angle) * ENTRY, 0, player.getZ() + Math.sin(angle) * ENTRY);
    }

    private static double flatDistance(Vec3 a, double x, double z) {
        return Math.hypot(a.x - x, a.z - z);
    }

    private static boolean callWhale(ServerLevel level, ServerPlayer player) {
        TitanKind kind = TitanKind.STONE_WHALE;
        StoneWhaleEntity whale = WhaleKeeper.current(level);
        Vec3 last = whale != null ? whale.position() : TitanWorldData.get(level).lastPosition();
        if (whale == null || flatDistance(whale.position(), player.getX(), player.getZ()) > FAR_SKY) {
            Vec3 e = entryPoint(level, player, last);
            double y = Math.min(level.getMaxBuildHeight() - 50, Math.max(TravelRules.TRAVEL_Y, Voyage.surfaceMax(level, player.position()) + TravelRules.TERRAIN_CLEARANCE));
            if (whale == null) {
                whale = (StoneWhaleEntity) kind.respawn().spawn(level, e.x, y, e.z);
            } else {
                whale.setPos(e.x, y, e.z);
            }
        }
        whale.callNear(player);
        return true;
    }

    private static boolean callTide(ServerLevel level, ServerPlayer player) {
        Vec3 spot = jp.revati.skyabyss.titan.tide.TideOcean.findCallSpot(level, player.getX(), player.getZ());
        if (spot == null) {
            return false;
        }
        TideShellEntity shell = TideKeeper.KEEPER.current(level);
        if (shell == null) {
            shell = (TideShellEntity) TitanKind.TIDE_SHELL.respawn().spawn(level, spot.x + 100, level.getSeaLevel(), spot.z);
        }
        boolean ok = shell.callNear(player);
        if (ok) {
            // 体の下が開けた水が人から遠いとき (岸が荒れて近くに寄れない) は、待っている場所の向きと距離を添える
            double far = Math.hypot(spot.x - player.getX(), spot.z - player.getZ());
            if (far > 60) {
                Notice.hint(player, LANG + "far.tide_shell", Component.translatable("message.skyabyss.travel.compass."
                        + SeaVoyage.compass(player.blockPosition(), BlockPos.containing(spot.x, 0, spot.z))), (int) Math.round(far));
            }
        }
        return ok;
    }

    private static boolean callWing(ServerLevel level, ServerPlayer player) {
        if (StormPerch.find(level, player.getX(), player.getZ(), 24, 96) == null) {
            return false;
        }
        StormWingEntity wing = StormKeeper.KEEPER.current(level);
        if (wing == null || flatDistance(wing.position(), player.getX(), player.getZ()) > FAR_STORM) {
            Vec3 from = wing != null ? wing.position() : null;
            Vec3 e = entryPoint(level, player, from);
            double surface = StormPerch.surfaceAt(level, e.x, e.z);
            double y = Math.max((Double.isNaN(surface) ? level.getSeaLevel() : surface) + 60, 150);
            if (wing == null) {
                wing = StormKeeper.KEEPER.spawn(level, e.x, e.z, StormKeeper.KEEPER.slot(level).state);
            }
            wing.setPos(e.x, y, e.z);
        }
        return wing.comeRest(player, TitanStay.TICKS);
    }

    private static boolean callEmber(ServerLevel level, ServerPlayer player) {
        EmberTitanEntity ember = EmberKeeper.KEEPER.current(level);
        if (ember == null) {
            if (emberSpotOverride == null && jp.revati.skyabyss.titan.ember.EmberGround.findCallSpot(level, player.getX(), player.getZ()) == null) {
                return false;
            }
            ember = EmberKeeper.KEEPER.spawn(level, player.getX(), player.getZ(), EmberKeeper.KEEPER.slot(level).state);
        }
        return ember.callNear(player, emberSpotOverride == null ? null : emberSpotOverride.apply(player));
    }
}
