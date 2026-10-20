package jp.revati.skyabyss.trial;

import java.util.List;

import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.dungeon.WhaleDeck;
import jp.revati.skyabyss.titan.ember.back.EmberDeck;
import jp.revati.skyabyss.titan.storm.back.StormDeck;
import jp.revati.skyabyss.titan.tide.back.TideDeck;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

// 試練の板まわりの、初めての説明 (版 22。ユーザーの試遊の声「鼓動の間の脇の部屋の石を押したら今週の… と出たが、先に説明がない」)。
// 人ごとに一度ずつ (倒れても残る PlayerPersisted の印):
//  - BOARD: 試練の板のそば (半径 6) に初めて来た: 「この石の板が試練の板。引いた三つと今週の試練が読める」
//  - DECK: 試練の階の広間に初めて入った (岩鯨のみ): 「引いた三つと今週の試練の戸だけが開く」
//  - WEEKLY: 今週の試練 (引いていない試練) のセルに初めて入った: 「ここは今週の試練。いつでも挑める。済ませると素材が倍」
// 説明は一行ずつの短い知らせで、チャットを流さない (Notice.hint)
public final class TrialIntro {
    private TrialIntro() {
    }

    private static final String TAG = "skyabyss_trial_intro";
    public static final int BOARD = 1;
    public static final int DECK = 2;
    public static final int WEEKLY = 4;
    public static final int BOARD_READ = 8;
    private static final double REACH = 6.0;

    private static CompoundTag persisted(Player p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    public static boolean seen(Player p, int bit) {
        return (persisted(p).getInt(TAG) & bit) != 0;
    }

    public static void mark(Player p, int bit) {
        CompoundTag t = persisted(p);
        t.putInt(TAG, t.getInt(TAG) | bit);
    }

    // 巨神ごとの、試練の板の置き場
    public static List<BlockPos> boards(String titan) {
        return switch (titan) {
            case Titans.STONE_WHALE -> List.of(WhaleDeck.BOARD_HUB, WhaleDeck.BOARD_D0);
            case Titans.THUNDER_WING -> List.of(StormDeck.BOARD_HUB, StormDeck.BOARD_YARD);
            case Titans.EMBER -> List.of(EmberDeck.BOARD_HUB, EmberDeck.BOARD_D0);
            case Titans.TIDE_SHELL -> List.of(TideDeck.BOARD_H);
            default -> List.of();
        };
    }

    // 10 tick ごとに TrialHub から。まだ見ていない人が板のそばに来たら、一度だけ言う
    public static void tick(String titan, List<ServerPlayer> players) {
        List<BlockPos> boards = boards(titan);
        if (boards.isEmpty()) {
            return;
        }
        for (ServerPlayer p : players) {
            if (!p.isAlive() || p.isSpectator() || seen(p, BOARD)) {
                continue;
            }
            for (BlockPos b : boards) {
                if (Math.abs(p.getY() - b.getY()) < 4 && p.distanceToSqr(b.getX() + 0.5, p.getY(), b.getZ() + 0.5) < REACH * REACH) {
                    mark(p, BOARD);
                    Notice.hint(p, "message.skyabyss.trial_intro.board");
                    break;
                }
            }
        }
        if (Titans.STONE_WHALE.equals(titan)) {
            for (ServerPlayer p : players) {
                if (p.isAlive() && !p.isSpectator() && !seen(p, DECK) && WhaleDeck.D0.aabb().inflate(0, 2, 0).contains(p.position())) {
                    mark(p, DECK);
                    Notice.hint(p, "message.skyabyss.trial_intro.deck");
                }
            }
        }
    }

    // 試練の板を開いた: 初めてなら「今週の試練」の説明を一行 (板を読んだ印)
    public static void onBoardOpened(ServerPlayer sp) {
        if (!seen(sp, BOARD_READ)) {
            mark(sp, BOARD_READ);
            Notice.hint(sp, "message.skyabyss.trial_intro.weekly");
        }
    }

    // 今週の試練 (引いていない試練) のセルに入った: 初めてなら一度だけ
    public static void onWeeklyEntered(ServerPlayer sp) {
        if (!seen(sp, WEEKLY)) {
            mark(sp, WEEKLY);
            Notice.hint(sp, "message.skyabyss.trial_intro.weekly_cell");
        }
    }
}
