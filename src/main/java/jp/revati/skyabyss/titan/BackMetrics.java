package jp.revati.skyabyss.titan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.brigadier.arguments.StringArgumentType;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.dungeon.TitanBackLayout;
import jp.revati.skyabyss.titan.dungeon.TitanBackLayout.Box;
import jp.revati.skyabyss.titan.dungeon.WhaleShape;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 背の大きさの測り (設計書 26 の B0b)。部屋の面積・通路の幅と高さ・体の殻の厚み (部屋の壁が体の中に収まるか) を、配置 (Layout) と体の形 (bin) だけから出す。
// 開発コマンド /skyabyss dev backmetrics <titan> で表を出し、自己テスト "back scale" が同じ決まりを守らせる。
// いまは岩鯨 (whale) だけ。ほかの巨神は同じ形で足す (rows と check の対応を増やす)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class BackMetrics {
    // 部屋の種類: hall 広間 / trial 試練の部屋 / boss ボス戦 / heart 心臓 / corr 通路 / rest 休み場・前室 / wing 任意の翼 / lower 下の層
    public record Room(String name, String kind, Box box, int oldArea) {
        public int area() {
            return box.area();
        }

        public int width() {
            return Math.min(box.x2() - box.x1() + 1, box.z2() - box.z1() + 1);
        }
    }

    private BackMetrics() {
    }

    // 岩鯨の部屋 (旧の面積 = 版 12 の x2-x1+1 × z2-z1+1。0 は新設)
    public static List<Room> whale() {
        List<Room> r = new ArrayList<>();
        r.add(new Room("hub", "hall", TitanBackLayout.HUB, 399));
        r.add(new Room("entry", "corr", TitanBackLayout.ENTRY, 48));
        r.add(new Room("heart corridor", "corr", TitanBackLayout.HEART_CORRIDOR, 0));
        r.add(new Room("heart rest", "rest", TitanBackLayout.HEART_REST, 49));
        r.add(new Room("song shrine", "wing", TitanBackLayout.SONG_SHRINE, 0));
        r.add(new Room("w0", "rest", TitanBackLayout.W0, 63));
        r.add(new Room("wa", "trial", TitanBackLayout.WA, 299));
        r.add(new Room("w1", "rest", TitanBackLayout.W1, 63));
        r.add(new Room("wb", "trial", TitanBackLayout.WB, 130));
        r.add(new Room("w2", "rest", TitanBackLayout.W2, 48));
        r.add(new Room("wc", "trial", TitanBackLayout.WC, 207));
        r.add(new Room("bell tower", "wing", TitanBackLayout.BELL, 0));
        r.add(new Room("e0", "rest", TitanBackLayout.E0, 63));
        r.add(new Room("e1", "boss", TitanBackLayout.E1, 390));
        r.add(new Room("ce1", "rest", TitanBackLayout.CE1, 49));
        r.add(new Room("e3", "boss", TitanBackLayout.E3, 279));
        r.add(new Room("e2", "boss", TitanBackLayout.E2, 0));
        r.add(new Room("guard post", "boss", TitanBackLayout.CK, 0));
        r.add(new Room("library", "wing", TitanBackLayout.LIBRARY, 0));
        r.add(new Room("maze exit room", "lower", TitanBackLayout.MAZE_EXIT_ROOM, 25));
        return r;
    }

    // 心臓の間の床の面積 (円)
    public static double heartArea() {
        return Math.PI * 25 * 25;
    }

    // 配置と体の形から、守る決まりを調べる。問題の一覧を返す
    //  - 広間・試練・ボスの部屋は旧の面積の 2.4 倍以上 (広間は 3.4 倍以上、新設の部屋は 150 m² 以上)
    //  - 本道の通路は幅 5 以上・高さ 6 以上
    //  - 心臓の間は旧の 1.3 倍の大きさまで (面積は旧の 1.69〜1.8 倍)
    //  - どの部屋も、壁 (1 外側) が体の中にある (WhaleShape が壁の位置に体のブロックを持っている)
    public static List<String> checkWhale() {
        List<String> problems = new ArrayList<>();
        for (Room room : whale()) {
            Box b = room.box();
            int area = room.area();
            switch (room.kind()) {
                case "hall" -> {
                    if (room.oldArea() > 0 && area < room.oldArea() * 3.4) {
                        problems.add(room.name() + ": hall area " + area + " is under 3.4x of " + room.oldArea());
                    }
                }
                case "trial", "boss" -> {
                    if (room.oldArea() > 0 && area < room.oldArea() * 2.4) {
                        problems.add(room.name() + ": area " + area + " is under 2.4x of " + room.oldArea());
                    }
                    if (room.oldArea() == 0 && area < 150) {
                        problems.add(room.name() + ": new room area " + area + " is under 150");
                    }
                }
                case "corr" -> {
                    if (room.width() < 5 || b.height() < 6) {
                        problems.add(room.name() + ": corridor " + room.width() + " wide, " + b.height() + " high (want >= 5, >= 6)");
                    }
                }
                default -> {
                }
            }
            if (room.oldArea() > 0 && area < room.oldArea() * 1.0) {
                problems.add(room.name() + ": got smaller (" + area + " < " + room.oldArea() + ")");
            }
            // 壁の輪 (x1-1〜x2+1、z1-1〜z2+1) が、体のブロックの中にあること
            int y = b.floorY();
            for (int x = b.x1() - 1; x <= b.x2() + 1; x++) {
                for (int z : new int[]{b.z1() - 1, b.z2() + 1}) {
                    if (WhaleShape.at(x, y + b.height() / 2, z) == null) {
                        problems.add(room.name() + ": wall outside the body at " + x + "," + (y + b.height() / 2) + "," + z);
                        x = b.x2() + 2;
                        break;
                    }
                }
            }
            for (int z = b.z1() - 1; z <= b.z2() + 1; z++) {
                for (int x : new int[]{b.x1() - 1, b.x2() + 1}) {
                    if (WhaleShape.at(x, y + b.height() / 2, z) == null) {
                        problems.add(room.name() + ": wall outside the body at " + x + "," + (y + b.height() / 2) + "," + z);
                        z = b.z2() + 2;
                        break;
                    }
                }
            }
        }
        double heart = heartArea();
        if (heart < 1134 * 1.69 || heart > 1134 * 1.9) {
            problems.add("heart chamber area " + (int) heart + " is not about 1.3^2 of the old 1134");
        }
        return problems;
    }

    // 表 (開発コマンドの出力)
    public static Map<String, String> table(String titan) {
        Map<String, String> rows = new LinkedHashMap<>();
        if (!"whale".equals(titan)) {
            return rows;
        }
        int sum = 0;
        for (Room room : whale()) {
            Box b = room.box();
            sum += room.area();
            rows.put(room.name(), String.format("%d x %d = %d m2, height %d (%s)%s", b.x2() - b.x1() + 1, b.z2() - b.z1() + 1, room.area(), b.height(), room.kind(),
                    room.oldArea() > 0 ? String.format(", x%.1f of old %d", room.area() / (double) room.oldArea(), room.oldArea()) : ", new"));
        }
        rows.put("heart chamber", String.format("radius 25 = %d m2, height %d", (int) heartArea(), TitanBackLayout.HEART.height()));
        rows.put("floor total (rooms listed)", sum + " m2");
        int fx = WhaleShape.maxX() - WhaleShape.minX() + 1;
        int fz = WhaleShape.maxZ() - WhaleShape.minZ() + 1;
        rows.put("body box", fx + " x " + fz);
        return rows;
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        event.getDispatcher().register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("dev").then(Commands.literal("backmetrics")
                        .then(Commands.argument("titan", StringArgumentType.word()).executes(ctx -> {
                            String titan = StringArgumentType.getString(ctx, "titan");
                            CommandSourceStack src = ctx.getSource();
                            Map<String, String> rows = table(titan);
                            if (rows.isEmpty()) {
                                src.sendFailure(Component.literal("backmetrics: only whale is measured so far"));
                                return 0;
                            }
                            rows.forEach((k, v) -> src.sendSuccess(() -> Component.literal(k + ": " + v), false));
                            List<String> problems = checkWhale();
                            problems.forEach(p -> src.sendFailure(Component.literal("PROBLEM " + p)));
                            return rows.size();
                        })))));
    }
}
