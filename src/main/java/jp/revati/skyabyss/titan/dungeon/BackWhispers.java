package jp.revati.skyabyss.titan.dungeon;

import static jp.revati.skyabyss.titan.dungeon.TitanBackLayout.*;

import java.util.List;
import java.util.function.Predicate;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.TitanTravel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 岩鯨の背の「最初の一度だけの囁き」。案内の看板を減らした代わりに、その場所へ初めて来たときだけチャットで伝える。
//  - lore (青緑の斜体・小さな共鳴の音): 場所の気配 (祠の下の鼓動、封印の扉、宝物庫の奥)
//  - hint (淡い灰色): 使い方と先の様子 (錨石と炉、休み場の先)。最初の休み場では、焚き火であきらめられることも添える
// 聞いた印はプレイヤーの永続 NBT に置く (倒れても、試練が元に戻っても、二度は言わない)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class BackWhispers {
    private static final String TAG_HEARD = "skyabyss_back_heard";

    private record Whisper(String id, boolean lore, Predicate<BlockPos> where) {
        String key() {
            return "message.skyabyss.whisper." + id;
        }
    }

    // 上から順に見る。休み場は TitanBackTrials の休憩地点と同じ部屋
    private static final List<Whisper> WHISPERS = List.of(
            new Whisper("plaza", false, p -> near(p, arrival(), 9, 4)),
            new Whisper("shrine", true, p -> near(p, new BlockPos(0, shrineY(), SHRINE_Z), 6, 4)),
            new Whisper("hub", true, HUB::contains),
            new Whisper("treasury", true, TREASURY::contains),
            new Whisper("w0", false, W0::contains),
            new Whisper("w1", false, W1::contains),
            new Whisper("w2", false, W2::contains),
            new Whisper("e0", false, E0::contains),
            new Whisper("ce1", false, CE1::contains),
            new Whisper("heart", false, HEART_REST::contains));
    private static final List<String> REST_POINTS = List.of("w0", "w1", "w2", "e0", "ce1", "heart");

    private BackWhispers() {
    }

    // 外から使う: 囁きの文のキーすべて (自己テストで lang にあるかを見る)
    public static List<String> keys() {
        return WHISPERS.stream().map(Whisper::key).toList();
    }

    private static boolean near(BlockPos p, BlockPos c, int r, int dy) {
        return Math.abs(p.getX() - c.getX()) <= r && Math.abs(p.getZ() - c.getZ()) <= r && Math.abs(p.getY() - c.getY()) <= dy;
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 10 != 3
                || player.level().dimension() != TitanTravel.TITAN_BACK || player.isSpectator()) {
            return;
        }
        BlockPos pos = player.blockPosition();
        for (Whisper w : WHISPERS) {
            if (w.where().test(pos)) {
                hear(player, w);
                return;
            }
        }
    }

    private static void hear(ServerPlayer player, Whisper w) {
        CompoundTag heard = heard(player);
        if (heard.getBoolean(w.id())) {
            return;
        }
        heard.putBoolean(w.id(), true);
        if (w.lore()) {
            player.sendSystemMessage(ModText.lore(w.key()));
            player.playNotifySound(SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.AMBIENT, 0.6f, 0.7f);
        } else {
            player.sendSystemMessage(ModText.hint(w.key()));
        }
        // 最初の休み場で、焚き火のことも一度だけ添える
        if (REST_POINTS.contains(w.id()) && !heard.getBoolean("campfire")) {
            heard.putBoolean("campfire", true);
            player.sendSystemMessage(ModText.hint("message.skyabyss.campfire_hint"));
        }
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
