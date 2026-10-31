package jp.revati.skyabyss.client.dev;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

// 台本 (tools/shots/*.txt) を読む。1 行 1 手順、# で始まる行と空行は飛ばす。書き方は tools/README.md の「台本で撮るスクショ」
// 頭の設定 (どこに書いてもよい): world <名前> [flat|normal]、timeout <秒>、size <幅> <高さ>
public final class ShotScript {
    public record Line(int number, String op, String[] args, String rest) {
        public String arg(int i) {
            if (i >= args.length) {
                throw new IllegalArgumentException("line " + number + ": '" + op + "' needs more arguments");
            }
            return args[i];
        }

        public int intArg(int i) {
            return Integer.parseInt(arg(i));
        }

        public double doubleArg(int i) {
            return Double.parseDouble(arg(i));
        }
    }

    public final String name;
    public final List<Line> steps = new ArrayList<>();
    public String world = "shots";
    public String worldType = "flat";
    // 専用サーバー (host:port) へ入る台本 (マルチの確かめ用。null なら一人用の世界)
    public String server = null;
    public int timeoutSeconds = 600;
    public int width = 0;
    public int height = 0;

    private ShotScript(String name) {
        this.name = name;
    }

    public static ShotScript load(Path file) throws IOException {
        String base = file.getFileName().toString();
        int dot = base.lastIndexOf('.');
        ShotScript script = new ShotScript(dot > 0 ? base.substring(0, dot) : base);
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i).strip();
            if (raw.isEmpty() || raw.startsWith("#")) {
                continue;
            }
            int sp = raw.indexOf(' ');
            String op = (sp < 0 ? raw : raw.substring(0, sp)).toLowerCase(Locale.ROOT);
            String rest = sp < 0 ? "" : raw.substring(sp + 1).strip();
            String[] args = rest.isEmpty() ? new String[0] : rest.split("\\s+");
            Line line = new Line(i + 1, op, args, rest);
            switch (op) {
                case "world" -> {
                    script.world = line.arg(0);
                    if (args.length > 1) {
                        script.worldType = args[1].toLowerCase(Locale.ROOT);
                    }
                }
                case "server" -> script.server = line.arg(0);
                case "timeout" -> script.timeoutSeconds = line.intArg(0);
                case "size" -> {
                    script.width = line.intArg(0);
                    script.height = line.intArg(1);
                }
                case "stareffect", "marker", "waitfile", "signal", "wait", "cmd", "camera", "waitchunks", "hud", "fov", "guiscale", "open", "key", "close", "respawn", "shot", "record", "recwait",
                        "echo", "prof", "watch", "band", "waitgame", "waitband", "waitnotice", "waitscreen", "quit", "waitdump", "chat", "look", "render", "clouds", "perf", "perspective", "swingpose", "swingstep", "inspectpose", "swingclock", "swing", "use", "stopuse", "mainhand", "bodyyaw", "lowpoly", "titanweight", "overheadshadow", "skypass", "skycircle", "weaponmesh", "weapontier", "acctier", "accmesh", "accworn", "accequip", "hover", "openacc", "trail", "watcher", "heartreward", "armortier", "armorlevel", "sneak", "walk", "huddemo", "mapstyle", "mapscale", "wheel", "drag", "click", "music" -> script.steps.add(line);
                default -> throw new IllegalArgumentException("line " + (i + 1) + ": unknown step '" + op + "' (" + Arrays.toString(args) + ")");
            }
        }
        return script;
    }
}
