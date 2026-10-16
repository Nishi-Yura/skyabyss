package jp.revati.skyabyss.titan.storm.back;

import static jp.revati.skyabyss.titan.storm.back.StormBackLayout.*;

import java.util.List;
import java.util.function.Predicate;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.dungeon.BackWhispers;
import jp.revati.skyabyss.titan.storm.StormTravel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 雷翼の背の「最初の一度だけの囁き」(12 の 9.2節の終わり)。看板を減らした代わりに、その場所へ初めて来たときだけチャットで伝える。
// 仕組みと聞いた印は岩鯨の BackWhispers と同じ (印の名前は storm_ で始めて、ほかの背中と分ける)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class StormWhispers {
    private record Whisper(String id, boolean lore, boolean rest, Predicate<BlockPos> where) {
        String key() {
            return "message.skyabyss.whisper." + id;
        }
    }

    private static final List<Whisper> WHISPERS = List.of(
            new Whisper("storm_tail", true, false, p -> near(p, ARRIVAL, 5, 3)),
            new Whisper("storm_tail_hall", false, false, TAIL_HALL::contains),
            new Whisper("storm_hub", false, true, HUB::contains),
            new Whisper("storm_top", true, false, StormWhispers::onTop),
            new Whisper("storm_w0", false, false, W0::contains),
            new Whisper("storm_e0", false, false, E0::contains),
            new Whisper("storm_f0", false, false, F0::contains),
            new Whisper("storm_heart", true, false, KEEL_ANTE::contains));

    private StormWhispers() {
    }

    public static List<String> keys() {
        return WHISPERS.stream().map(Whisper::key).toList();
    }

    private static boolean near(BlockPos p, BlockPos c, int r, int dy) {
        return Math.abs(p.getX() - c.getX()) <= r && Math.abs(p.getZ() - c.getZ()) <= r && Math.abs(p.getY() - c.getY()) <= dy;
    }

    // 背の上 (尾の間の段を上りきった所のまわり)
    static boolean onTop(BlockPos p) {
        return Math.abs(p.getX() - TOP_STAIR.getX()) <= 6 && Math.abs(p.getZ() - TOP_STAIR.getZ()) <= 6 && p.getY() > StormBirdBuilder.top(p.getX(), p.getZ());
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 10 != 7
                || player.level().dimension() != StormTravel.STORM_BACK || player.isSpectator()) {
            return;
        }
        BlockPos pos = player.blockPosition();
        for (Whisper w : WHISPERS) {
            if (w.where().test(pos)) {
                BackWhispers.hear(player, w.id(), w.key(), w.lore(), w.rest() ? "storm_campfire" : null, "message.skyabyss.storm.campfire_first");
                return;
            }
        }
    }
}
