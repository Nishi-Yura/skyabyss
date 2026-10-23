package jp.revati.skyabyss.trial.raid.whale;

import java.util.UUID;

import com.mojang.brigadier.arguments.StringArgumentType;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidKit;
import jp.revati.skyabyss.trial.raid.RaidRoom;
import jp.revati.skyabyss.trial.raid.RaidRooms;
import jp.revati.skyabyss.trial.raid.mid.MidRooms;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 開発・撮影 (OP): 岩鯨のレイドの部屋 (WR1〜WR6) と中ボスの間 (WRM) を、順路なしで建てるだけ (始めない。敵は出ない)。
//   /skywhaleraid show <WR1..WR6|WRM>   いまいる次元の、部屋ごとに決まった場所 (x = 6000 + 128 × 番号、y 180、z 6000) に建て、入口の戸を開けておく
//   /skywhaleraid decor on|off          飾り (WhaleDecor) の入り切り。off で建てると飾りのない元の部屋 (見比べの「前」)
// 建築の見比べ (docs/review/raid_build_whale/) の撮影に使う。台本は tools/shots/raid_build_whale.txt
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class WhaleRaidDemo {
    private WhaleRaidDemo() {
    }

    public static final String[] IDS = {"WR1", "WR2", "WR3", "WR4", "WR5", "WR6", "WRM"};
    public static final int Y = 180;
    public static final int Z = 6000;

    public static BlockPos originOf(String id) {
        for (int i = 0; i < IDS.length; i++) {
            if (IDS[i].equals(id)) {
                return new BlockPos(6000 + 128 * i, Y, Z);
            }
        }
        return null;
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("skywhaleraid").requires(s -> s.hasPermission(2))
                // 見比べの「前」: 飾りを切って建てる (順路の部屋にも効く。撮り終えたら on に戻す)
                .then(Commands.literal("decor").then(Commands.literal("on").executes(c -> decor(true))).then(Commands.literal("off").executes(c -> decor(false))))
                .then(Commands.literal("show").then(Commands.argument("id", StringArgumentType.word()).executes(c -> {
                    String id = StringArgumentType.getString(c, "id");
                    String done = show(c.getSource().getLevel(), id);
                    c.getSource().sendSuccess(() -> Component.literal(done), false);
                    return 1;
                }))));
    }

    public static String show(ServerLevel lv, String id) {
        BlockPos o = originOf(id);
        RaidRoom room = id.equals(WhaleMidRoom()) ? MidRooms.create(Titans.STONE_WHALE) : RaidRooms.create(id);
        if (o == null || room == null) {
            return "unknown room " + id;
        }
        for (int cx = (o.getX() - 32) >> 4; cx <= (o.getX() + 32) >> 4; cx++) {
            for (int cz = (o.getZ() - 48) >> 4; cz <= (o.getZ() + 48) >> 4; cz++) {
                lv.setChunkForced(cx, cz, true);
            }
        }
        RaidCtx ctx = RaidCtx.forSolo(lv, UUID.nameUUIDFromBytes(("whale demo " + id).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                Titans.STONE_WHALE, id, 1, o, 20261024L);
        long t0 = System.nanoTime();
        room.build(ctx);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        RaidKit.Cell c = ctx.cell();
        RaidKit.setGate(ctx, c.entryGate(), true);
        String msg = "built " + id + " at " + o.toShortString() + " in " + ms + " ms; rest " + c.rest().toShortString() + " " + WhaleDecor.lastStats(id);
        SkyAbyss.LOGGER.info("[whale demo] {}", msg);
        return msg;
    }

    private static int decor(boolean on) {
        WhaleDecor.enabled = on;
        return 1;
    }

    private static String WhaleMidRoom() {
        return jp.revati.skyabyss.trial.raid.mid.WhaleMidRoom.ID;
    }
}
