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

// 巨神との「出会い」 (docs/design/09 の 4.4節、28): 乗れる姿を見た・背へ入った・心臓を倒した巨神に出会ったと記し、その呼び笛を一度だけ渡す。
// 笛は巨神を呼ばない (成体の巨神は一つのパーティの用事では動かない)。笛が呼ぶのは巨神の「子」(titan.young)。
// 最初の出会いの場面・時刻は変えない。笛は出会った時に一度だけ渡る (なくしたら作業台で作り直せる)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TitanCalls {
    // 出会いとみなす距離 (巨神が乗れる姿で止まっているとき)
    public static final double MEET_RANGE = 200;

    private static final String LANG = "message.skyabyss.titan_call.";

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
}
