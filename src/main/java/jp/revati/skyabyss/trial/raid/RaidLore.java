package jp.revati.skyabyss.trial.raid;

import java.util.Map;

import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.tablet.Tablets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;

// レイドの部屋の物語 (fun_review 2026-10-23 の P1-3・P2-2)。部屋ごとに一つ:
//   - 入口の間の西の壁に碑 (背中の民の言葉)。文のキーは古い試練の翼の碑文と同じ sign.skyabyss.lore_* で、
//     読むと伝承の「碑文」の項目 (chapter1/inscription_*) が記される (Tablets.read → Lore.fire "sign")
//   - 部屋の戸は、入口の碑をだれかが読むまで開かない (2026-11-04 の声「プレイヤーが何もしなくても進む所を直す」。前は越えると読まずに記された)。
//     組のだれかが読む (前の周で読んでいた人も含む) と、その場の全員に記される (waiting)。全員がもう知っていれば待たない
//   - 入口へ運ばれたとき、心の声を二行 (message.skyabyss.raid_lore.<部屋>.1 / .2)
public final class RaidLore {
    // 部屋 → 碑の文のキー
    public static final Map<String, String> SIGNS = Map.ofEntries(
            Map.entry("WR1", "sign.skyabyss.lore_west"),
            Map.entry("WR2", "sign.skyabyss.lore_below"),
            Map.entry("WR3", "sign.skyabyss.lore_east"),
            Map.entry("WR4", "sign.skyabyss.lore_stomach"),
            Map.entry("WR5", "sign.skyabyss.lore_floor"),
            Map.entry("WR6", "sign.skyabyss.lore_ring"),
            Map.entry("TR1", "sign.skyabyss.lore_tide_west"),
            Map.entry("TR3", "sign.skyabyss.lore_tide_front"),
            Map.entry("TR4", "sign.skyabyss.lore_tide_coral"),
            Map.entry("TR5", "sign.skyabyss.lore_tide_light"),
            Map.entry("TR6", "sign.skyabyss.lore_tide_calendar"),
            Map.entry("TR7", "sign.skyabyss.lore_tide_brood"),
            Map.entry("SR1", "sign.skyabyss.lore_storm_west"),
            Map.entry("SR2", "sign.skyabyss.lore_storm_east"),
            Map.entry("SR3", "sign.skyabyss.lore_storm_tail"),
            Map.entry("SR4", "sign.skyabyss.lore_storm_column"),
            Map.entry("SR5", "sign.skyabyss.lore_storm_beat"),
            Map.entry("SR6", "sign.skyabyss.lore_storm_gale"),
            Map.entry("ER1", "sign.skyabyss.lore_ember_west"),
            Map.entry("ER2", "sign.skyabyss.lore_ember_east"),
            Map.entry("ER3", "sign.skyabyss.lore_ember_south"),
            Map.entry("ER4", "sign.skyabyss.lore_raid_ember_chain"),
            Map.entry("ER5", "sign.skyabyss.lore_raid_ember_valve"),
            Map.entry("ER6", "sign.skyabyss.lore_raid_ember_pupil"));

    private RaidLore() {
    }

    // 入口の間の碑の場所 (休み場の西、壁に掛ける。文は東 = 休み場の側を向く)
    public static BlockPos tabletPos(RaidKit.Cell cell) {
        return cell.rest().offset(-3, 1, 0);
    }

    // buildCell の終わりに呼ぶ。登録のない部屋 (手本・自己テストの部屋) には置かない
    static void placeTablet(RaidCtx ctx, RaidKit.Cell cell) {
        String key = SIGNS.get(ctx.roomId());
        if (key == null) {
            return;
        }
        BlockPos at = tabletPos(cell);
        RaidKit.set(ctx.level(), at, Tablets.wallState(Direction.EAST));
        Tablets.write(ctx.level(), at, key);
    }

    // 入口へ運ばれたとき (段の名前の語りのあと)
    static void voice(ServerPlayer player, String roomId) {
        if (SIGNS.containsKey(roomId)) {
            Notice.narrate(player, "message.skyabyss.raid_lore." + roomId + ".1");
            Notice.narrate(player, "message.skyabyss.raid_lore." + roomId + ".2");
        }
    }

    // 部屋の碑の伝承の項目 (碑の文のキー → unlock の sign の先)。碑のない部屋は null
    @javax.annotation.Nullable
    public static String entryOf(String roomId) {
        String key = SIGNS.get(roomId);
        if (key == null) {
            return null;
        }
        for (jp.revati.skyabyss.lore.LoreEntry e : jp.revati.skyabyss.lore.LoreManager.catalog().entries()) {
            for (jp.revati.skyabyss.lore.LoreTrigger t : e.unlock()) {
                if (t.type().equals("sign") && t.target().equals(key)) {
                    return e.id();
                }
            }
        }
        return null;
    }

    // 戸を開ける前に、入口の碑を読むのを待つか。だれかが知っていれば (読んだ・前の周で読んだ)、その場の全員に記して待たない
    public static boolean waiting(java.util.List<ServerPlayer> members, String roomId) {
        String entry = entryOf(roomId);
        if (entry == null || members.isEmpty()) {
            return false;
        }
        boolean any = false;
        boolean all = true;
        for (ServerPlayer p : members) {
            boolean has = Lore.state(p).has(entry);
            any |= has;
            all &= has;
        }
        if (all) {
            return false;
        }
        if (any) {
            String key = SIGNS.get(roomId);
            for (ServerPlayer p : members) {
                Lore.fire(p, "sign", t -> t.target().equals(key));
            }
            return false;
        }
        return true;
    }
}
