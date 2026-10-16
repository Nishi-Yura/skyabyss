package jp.revati.skyabyss.titan.ember.back;

import static jp.revati.skyabyss.titan.ember.back.EmberBackLayout.*;

import java.util.List;
import java.util.function.Predicate;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.dungeon.BackWhispers;
import jp.revati.skyabyss.titan.ember.EmberIds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 余燼の背の「最初の一度だけの囁き」(岩鯨の BackWhispers・潮殻の TideWhispers と同じ仕組み・同じ聞いた印。13 の 3.3節)。
// 看板は碑文 6 枚と入口の一言 3 枚だけにし、使い方 (熱・灰溜まり・炉・色・火・抜け道) は、その場所へ初めて来たときだけチャットで伝える。
// lore は場所の気配、hint は使い方。鍵は message.skyabyss.ember.whisper.<id>
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class EmberWhispers {
    record Whisper(String id, boolean lore, boolean rest, Predicate<BlockPos> where) {
        String key() {
            return EmberIds.LANG_WHISPER + id;
        }
    }

    // 上から順に見る (一度に一つ)
    static final List<Whisper> WHISPERS = List.of(
            new Whisper("mouth", true, false, p -> near(p, ARRIVAL, 6, 3)),
            new Whisper("throat", false, true, p -> rest("throat").area().grow(2).contains(p)),
            new Whisper("hub", true, false, p -> HUB.contains(p) && EmberBodyBuilder.hubDist(p.getX(), p.getZ()) < 11),
            new Whisper("ash", false, false, p -> HUB.contains(p) && EmberBodyBuilder.hubDist(p.getX(), p.getZ()) >= 11),
            new Whisper("west", false, true, p -> rest("w0").area().grow(1).contains(p)),
            new Whisper("east", false, true, p -> rest("e0").area().grow(1).contains(p)),
            new Whisper("south", false, true, p -> rest("s0").area().grow(1).contains(p)),
            new Whisper("well", true, false, p -> EmberBodyBuilder.wellDist(p.getX(), p.getZ()) <= WELL_RADIUS && p.getY() > WELL_BOTTOM
                    && p.getY() < WELL_BOTTOM + 14),
            new Whisper("crater", true, false, p -> EmberBodyBuilder.wellDist(p.getX(), p.getZ()) <= 12 && p.getY() >= EmberBodyBuilder.crater()),
            new Whisper("back", true, false, EmberWhispers::onBackTop),
            new Whisper("heart", true, true, p -> HEART_ANTE.grow(1).contains(p)));

    private EmberWhispers() {
    }

    public static List<String> keys() {
        return WHISPERS.stream().map(Whisper::key).toList();
    }

    private static boolean near(BlockPos p, BlockPos c, int r, int dy) {
        return Math.abs(p.getX() - c.getX()) <= r && Math.abs(p.getZ() - c.getZ()) <= r && Math.abs(p.getY() - c.getY()) <= dy + 1;
    }

    // 背の上 (外) に立っている (火山より南、背骨のまわり)
    static boolean onBackTop(BlockPos p) {
        if (p.getZ() < -5 || p.getZ() > 120) {
            return false;
        }
        int top = EmberBodyBuilder.BODY.shellTop(p.getX(), p.getZ());
        return top != Integer.MIN_VALUE && p.getY() > top && p.getY() <= top + 4 && top > 100;
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 10 != 3
                || player.level().dimension() != EmberIds.BACK || player.isSpectator()) {
            return;
        }
        BlockPos pos = player.blockPosition();
        for (Whisper w : WHISPERS) {
            if (w.where().test(pos)) {
                BackWhispers.hear(player, "ember_" + w.id(), w.key(), w.lore(), w.rest() ? "ember_campfire" : null,
                        EmberIds.LANG_BACK + "campfire_hint");
                return;
            }
        }
    }
}
