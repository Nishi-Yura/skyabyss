package jp.revati.skyabyss.titan.storm.lore;

import java.util.List;
import java.util.function.Predicate;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.dungeon.BackWhispers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 雷翼の背 (嵐の上) の「最初の一度だけの囁き」。岩鯨の BackWhispers・潮殻の TideWhispers と同じ仕組み・同じ聞いた印 (12 の 9.2 節)。
// 看板を 9 枚に絞った代わりに、場所へ初めて来たときだけチャットで伝える。lore は場所の気配、hint は使い方。
// 場所の箱は 12 の 3.3 節の配置の表から取った。背中の担当の StormBackLayout ができたら、その部屋の範囲に差し替える (合わせるときに)
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

    // 12 の 3.3 節 (床の高さから天井まで。少し広めに取る)
    static final Box LANDING = new Box(-8, 140, 86, 8, 150, 102);   // 尾羽の上 (到着 (0, 143, 94))
    static final Box TAIL = new Box(-10, 145, 20, 10, 153, 34);      // 尾の間 (炉・焚き火・碑文)
    static final Box HUB = new Box(-14, 136, -18, 14, 156, 10);      // 風の広間 (天井 156)
    static final Box TOP = new Box(-40, 157, -50, 40, 172, 60);      // 背の上 (天窓と二本の塔のまわり)
    static final Box WEST = new Box(-28, 135, -14, -15, 152, 2);     // 左の肩の門と休み場 w0
    static final Box EAST = new Box(15, 135, -14, 28, 152, 2);       // 右の肩の門と休み場 e0
    static final Box FRONT = new Box(-5, 148, -62, 5, 157, -44);     // 首の回廊・前室 (休み場 f0)
    static final Box HEART = new Box(-4, 119, -37, 4, 127, -29);     // 竜骨の前室 (休み場 heart)

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
