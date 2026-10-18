package jp.revati.skyabyss.readiness;

import java.util.function.Predicate;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.dungeon.TitanBackLayout;
import jp.revati.skyabyss.titan.ember.back.EmberBackLayout;
import jp.revati.skyabyss.titan.storm.back.StormBackLayout;
import jp.revati.skyabyss.titan.tide.back.TideBackLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 休み場 heart (心臓の前室) の碑の囁き (docs/design/21 の 1.2 の 3)。前室に初めて入ったとき、最初の一度だけ、足りない灯を一行。
// 碑文の本文は変えない。全部が緑なら何も言わず、それでも「聞いた」ことにする (二度目に入ったときに言い出さない)。
// 聞いた印は人の永続 NBT (倒れても残る)。画面の出し方は Notice.narrate (世界の声・囁き、字幕)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ReadinessWhisper {
    private static final String TAG_HEARD = "skyabyss_readiness_heard";

    private ReadinessWhisper() {
    }

    // 巨神ごとの心臓の前室 (背中のディメンション内の位置)
    @Nullable
    static Predicate<BlockPos> antechamber(String titan) {
        return switch (titan) {
            case Titans.STONE_WHALE -> TitanBackLayout.HEART_REST::contains;
            case Titans.TIDE_SHELL -> TideBackLayout.HEART_REST::contains;
            case Titans.THUNDER_WING -> StormBackLayout.KEEL_ANTE::contains;
            case Titans.EMBER -> EmberBackLayout.HEART_ANTE.grow(1)::contains;
            default -> null;
        };
    }

    public static String key(Readiness.Lamp lamp, Readiness.Colour colour) {
        return "message.skyabyss.readiness.whisper." + lamp.id() + "." + (colour == Readiness.Colour.RED ? "red" : "yellow");
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 10 != 7 || player.isSpectator()) {
            return;
        }
        String titan = Titans.titanOfBack(player.level().dimension());
        Predicate<BlockPos> room = titan == null ? null : antechamber(titan);
        if (room != null && room.test(player.blockPosition())) {
            hear(player, titan);
        }
    }

    // 最初の一度だけ囁く。囁いたら true (全部緑・もう聞いたあとは false)
    public static boolean hear(ServerPlayer player, String titan) {
        CompoundTag heard = heard(player);
        if (heard.getBoolean(titan)) {
            return false;
        }
        heard.putBoolean(titan, true);
        Readiness.Result result = Readiness.of(player, titan);
        Readiness.Lamp worst = result.weakest();
        if (worst == null) {
            return false;
        }
        Notice.narrate(player, key(worst, result.colour(worst)));
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.AMBIENT, 0.6f, 0.6f);
        return true;
    }

    // 開発用: 聞いた印を消す
    public static void forget(Player player, String titan) {
        heard(player).remove(titan);
    }

    public static boolean heardAlready(Player player, String titan) {
        return heard(player).getBoolean(titan);
    }

    private static CompoundTag heard(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.contains(TAG_HEARD)) {
            persisted.put(TAG_HEARD, new CompoundTag());
        }
        return persisted.getCompound(TAG_HEARD);
    }
}
