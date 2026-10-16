package jp.revati.skyabyss.titan.tide.back;

import static jp.revati.skyabyss.titan.tide.back.TideBackLayout.*;

import java.util.List;
import java.util.function.Predicate;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.dungeon.BackWhispers;
import jp.revati.skyabyss.titan.tide.TideTravel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 潮殻の背の「最初の一度だけの囁き」(岩鯨の BackWhispers と同じ仕組み・同じ聞いた印)。
// 看板 (潮見の目盛りの石盤・三つの試練・眠る試練・広間の道・祭壇・潮見の間の栓と重石の床) をやめた代わりに、
// その場所へ初めて来たときだけチャットで伝える (08 の 21.5節)。lore は場所の気配、hint は使い方
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TideWhispers {
    record Whisper(String id, boolean lore, boolean rest, Predicate<BlockPos> where) {
        String key() {
            return "message.skyabyss.whisper." + id;
        }
    }

    // 上から順に見る (一度に一つ)
    static final List<Whisper> WHISPERS = List.of(
            new Whisper("tide_landing", true, false, p -> LANDING.contains(p) || near(p, ARRIVAL, 8, 4)),
            new Whisper("tide_beach", false, false, BEACH::contains),
            new Whisper("tide_hub", true, false, HUB::contains),
            new Whisper("tide_top", true, false, TideWhispers::onShellTop),
            new Whisper("tide_tower", true, false, p -> p.getY() > TOWER_DECK && Math.abs(p.getX()) <= 6 && Math.abs(p.getZ() - TOWER_CZ) <= 6),
            new Whisper("tide_garden", false, true, G0::contains),
            new Whisper("tide_watch", false, true, S0::contains),
            new Whisper("tide_flats", false, true, F0::contains));

    private TideWhispers() {
    }

    public static List<String> keys() {
        return WHISPERS.stream().map(Whisper::key).toList();
    }

    private static boolean near(BlockPos p, BlockPos c, int r, int dy) {
        return Math.abs(p.getX() - c.getX()) <= r && Math.abs(p.getZ() - c.getZ()) <= r && Math.abs(p.getY() - c.getY()) <= dy;
    }

    // 甲羅の上 (外) に立っている
    static boolean onShellTop(BlockPos p) {
        int top = shellTop(p.getX(), p.getZ());
        return top != Integer.MIN_VALUE && p.getY() > top && p.getY() <= top + 6 && top > 100;
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 10 != 7
                || player.level().dimension() != TideTravel.TIDE_BACK || player.isSpectator()) {
            return;
        }
        BlockPos pos = player.blockPosition();
        for (Whisper w : WHISPERS) {
            if (w.where().test(pos)) {
                BackWhispers.hear(player, w.id(), w.key(), w.lore(), w.rest() ? "tide_campfire" : null, "message.skyabyss.tide.campfire_hint");
                return;
            }
        }
    }
}
