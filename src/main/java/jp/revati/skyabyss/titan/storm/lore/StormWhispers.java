package jp.revati.skyabyss.titan.storm.lore;

import java.util.List;
import java.util.function.Predicate;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.dungeon.BackWhispers;
import jp.revati.skyabyss.titan.storm.back.StormBackLayout;
import jp.revati.skyabyss.titan.storm.back.StormBirdBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 雷翼の背 (嵐の上) の「最初の一度だけの囁き」。岩鯨の BackWhispers・潮殻の TideWhispers と同じ仕組み・同じ聞いた印 (12 の 9.2 節)。
// 看板を 9 枚に絞った代わりに、場所へ初めて来たときだけチャットで伝える。lore は場所の気配、hint は使い方。
// 場所の箱は背中の担当の StormBackLayout の部屋の範囲から取る
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class StormWhispers {
    record Box(int x0, int y0, int z0, int x1, int y1, int z1) {
        boolean contains(BlockPos p) {
            return p.getX() >= x0 && p.getX() <= x1 && p.getY() >= y0 && p.getY() <= y1 && p.getZ() >= z0 && p.getZ() <= z1;
        }
    }

    record Whisper(String id, boolean lore, boolean rest, Predicate<BlockPos> where) {
        String key() {
            return "message.skyabyss.whisper." + id;
        }
    }

    // 部屋の範囲は背中の担当の StormBackLayout から取る (床の 1 上から天井まで)
    static Box of(StormBackLayout.Area a) {
        return new Box(a.x1(), a.floor() + 1, a.z1(), a.x2(), a.floor() + a.height(), a.z2());
    }

    static final Box LANDING = new Box(StormBackLayout.ARRIVAL.getX() - 5, StormBackLayout.ARRIVAL.getY() - 2, StormBackLayout.ARRIVAL.getZ() - 5,
            StormBackLayout.ARRIVAL.getX() + 5, StormBackLayout.ARRIVAL.getY() + 3, StormBackLayout.ARRIVAL.getZ() + 3);  // 尾羽の上
    static final Box TAIL = of(StormBackLayout.TAIL_HALL);       // 尾の間 (炉・焚き火・碑文)
    static final Box HUB = of(StormBackLayout.HUB);              // 風の広間 (上の回廊まで)
    // 背の上: 尾の間の西の壁の段を上りきった所のまわり (鳥の背の表面より上だけ)
    static final Box TOP = new Box(StormBackLayout.TOP_STAIR.getX() - 6, StormBirdBuilder.top(StormBackLayout.TOP_STAIR.getX(), StormBackLayout.TOP_STAIR.getZ()) + 1,
            StormBackLayout.TOP_STAIR.getZ() - 6, StormBackLayout.TOP_STAIR.getX() + 6,
            StormBirdBuilder.top(StormBackLayout.TOP_STAIR.getX(), StormBackLayout.TOP_STAIR.getZ()) + 8, StormBackLayout.TOP_STAIR.getZ() + 6);
    static final Box WEST = of(StormBackLayout.W0);              // 左の肩の門と休み場 w0
    static final Box EAST = of(StormBackLayout.E0);              // 右の肩の門と休み場 e0
    static final Box FRONT = of(StormBackLayout.F0);             // 首の前室 (休み場 f0)
    static final Box HEART = of(StormBackLayout.KEEL_ANTE);      // 竜骨の前室 (休み場 heart)

    // 上から順に見る (一度に一つ)
    static final List<Whisper> WHISPERS = List.of(
            new Whisper("storm_landing", true, false, LANDING::contains),
            new Whisper("storm_tail", false, false, TAIL::contains),
            new Whisper("storm_hub", true, false, HUB::contains),
            new Whisper("storm_top", true, false, TOP::contains),
            new Whisper("storm_west", false, true, WEST::contains),
            new Whisper("storm_east", false, true, EAST::contains),
            new Whisper("storm_front", false, true, FRONT::contains),
            new Whisper("storm_heart", false, true, HEART::contains));

    public static final String CAMPFIRE_FLAG = "storm_campfire";
    public static final String CAMPFIRE_KEY = "message.skyabyss.storm.campfire_hint";

    private StormWhispers() {
    }

    public static List<String> keys() {
        return WHISPERS.stream().map(Whisper::key).toList();
    }

    // 立っている所の囁き (なければ null)。自己テストと、背中の担当が自分の配置で確かめるため
    static Whisper at(BlockPos pos) {
        for (Whisper w : WHISPERS) {
            if (w.where().test(pos)) {
                return w;
            }
        }
        return null;
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 10 != 5
                || player.level().dimension() != StormLore.STORM_BACK || player.isSpectator()) {
            return;
        }
        hearAt(player, player.blockPosition());
    }

    static void hearAt(ServerPlayer player, BlockPos pos) {
        Whisper w = at(pos);
        if (w != null) {
            BackWhispers.hear(player, w.id(), w.key(), w.lore(), w.rest() ? CAMPFIRE_FLAG : null, CAMPFIRE_KEY);
        }
    }
}
