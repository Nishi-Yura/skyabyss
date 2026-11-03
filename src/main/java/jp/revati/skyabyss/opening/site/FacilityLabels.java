package jp.revati.skyabyss.opening.site;

import java.util.List;
import java.util.Map;

import jp.revati.skyabyss.SignFit;
import jp.revati.skyabyss.facility.TownGuide;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

// 都と土地の町の、施設の戸の前に浮く名札 (docs/design/35 の 2 章)。バニラの text_display を二つ重ねる:
//   題 (label.skyabyss.facility.<名>。2 倍・金・太字) と、あれば中身の行 (<名>.sub。1 倍・淡い色) を題のすぐ下に。
//   どちらも縦の軸だけで回り (billboard vertical。近くで見上げても字が寝ない)、明るさは固定 (夜も読める)。
// 置き場 (2026-11-02 の声「今の位置だとブロックにめり込んで全部上手く見えない」): 設計図の印 label_<名> (戸の前の一つ外・床から 4 上) から、
//   通りの側へ 0〜6・上下 -1〜6・横へ -6〜6 の中で、名札がどの向きを向いてもブロックに触れない
//   (半径 = 広い方の言語の行の幅の半分 + MARGIN の円柱が空気) うえ、下に 2 マスの空き (人の頭に当たらない) がある所の、
//   いちばん戸に近くて低い所。空いた所がなければ、触れるブロックがいちばん少ない所。
// 前の版の名札 (印の上に 1 枚・回る・題だけ) は、見回りが見つけしだい外して置き直す
public final class FacilityLabels {
    public static final String TAG = "skyabyss_facility_label";
    // 今の版の名札の印と、名前ごとの印 (見回りが自分の名札を探す)
    static final String V2 = "skyabyss_facility_label_v2";
    static final String NAME_TAG = "skyabyss_label.";
    // 名札を出さない施設: 闇市 (自分で見つける)・地図屋の屋根裏と厩舎 (係の人を消して人がいない。乗り物は止まり木の旅の番)
    static final java.util.Set<String> HIDDEN = java.util.Set.of("black_market", "mapmaker", "stable");
    // 土地の町で文を替える名札 (宿は都の「鐘の宿」でなく「旅の宿」。碑の sign.skyabyss.town_fac.inn と同じ)
    static final Map<String, String> TOWN_KEYS = Map.of("inn", "town_inn");

    static final float TITLE_SCALE = 2.0f;
    static final float SUB_SCALE = 1.0f;
    // 1 行の高さ (字 9 + 行間 1 + 背景の縁 1、px) と、px からブロック
    static final double LINE_PX = 11;
    static final double PX = 0.025;
    static final double TITLE_H = LINE_PX * PX * TITLE_SCALE;
    static final double SUB_H = LINE_PX * PX * SUB_SCALE;
    static final double GAP = 0.04;
    // 名札の下の端の、マスの床からの高さ
    static final double LIFT = 0.15;
    // 名札の端とブロックのあいだに残す空き
    static final double MARGIN = 0.3;
    static final int MAX_OUT = 6;
    static final int MIN_UP = -1;
    static final int MAX_UP = 6;
    // 横へずらす幅 (土地の町の市は、戸の前の広場に屋台の小屋が建つので、小屋の脇の空いた所まで)
    static final int MAX_SIDE = 6;

    private FacilityLabels() {
    }

    // ---------------------------------------------------------------- 文と大きさ
    public static String key(String name, boolean town) {
        return town ? TOWN_KEYS.getOrDefault(name, name) : name;
    }

    static String langKey(String key) {
        return "label.skyabyss.facility." + key;
    }

    static boolean hasSub(String key) {
        return !SignFit.lang("ja_jp").getOrDefault(langKey(key) + ".sub", "").isEmpty();
    }

    // 題は太字 (字ごとに 1 px 広い)。背景の縁の 2 px を足す
    static int titlePx(String text) {
        return SignFit.approxWidth(text) + text.codePointCount(0, text.length()) + 2;
    }

    // 名札のいちばん広い行の幅の半分 + 空き (ブロック)。日本語と英語の広い方
    public static double radius(String key) {
        double w = 0;
        for (String language : SignFit.LANGUAGES) {
            Map<String, String> lang = SignFit.lang(language);
            w = Math.max(w, titlePx(lang.getOrDefault(langKey(key), key)) * PX * TITLE_SCALE);
            String sub = lang.getOrDefault(langKey(key) + ".sub", "");
            if (!sub.isEmpty()) {
                w = Math.max(w, (SignFit.approxWidth(sub) + 2) * PX * SUB_SCALE);
            }
        }
        return w / 2 + MARGIN;
    }

    // 名札の高さ (下の端から題の上の端まで)
    public static double height(String key) {
        return hasSub(key) ? SUB_H + GAP + TITLE_H : TITLE_H;
    }

    // ---------------------------------------------------------------- 置き場
    // (x, y, z) のマスに見えるものがあるか
    public interface Cells {
        boolean filled(int x, int y, int z);
    }

    public record Spot(BlockPos cell, int touching) {
    }

    static boolean filled(BlockState s) {
        return !(s.isAir() || s.is(Blocks.LIGHT) || s.is(Blocks.STRUCTURE_VOID));
    }

    // 印から通りの側の向き: 前 (印から 1〜4 先、高さ -2〜+1) の空気が多く、後ろ (1〜2 手前) の空気が少ない向き
    static Direction outward(BlockPos at, Cells cells) {
        Direction best = Direction.NORTH;
        int bestScore = Integer.MIN_VALUE;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int score = 0;
            for (int k = 1; k <= 4; k++) {
                for (int yy = -2; yy <= 1; yy++) {
                    if (!cells.filled(at.getX() + d.getStepX() * k, at.getY() + yy, at.getZ() + d.getStepZ() * k)) {
                        score++;
                    }
                }
            }
            for (int k = 1; k <= 2; k++) {
                for (int yy = -1; yy <= 1; yy++) {
                    if (!cells.filled(at.getX() - d.getStepX() * k, at.getY() + yy, at.getZ() - d.getStepZ() * k)) {
                        score -= 2;
                    }
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = d;
            }
        }
        return best;
    }

    // マス c の真ん中に名札を立てたとき、触れるブロックの数 (円柱の中のマス) と、真下 2 マスの埋まり
    static int touching(BlockPos c, double r, double h, Cells cells) {
        double cx = c.getX() + 0.5;
        double cz = c.getZ() + 0.5;
        int y0 = c.getY();
        int y1 = (int) Math.floor(c.getY() + LIFT + h + 0.08);
        int n = 0;
        for (int x = (int) Math.floor(cx - r); x <= (int) Math.floor(cx + r); x++) {
            for (int z = (int) Math.floor(cz - r); z <= (int) Math.floor(cz + r); z++) {
                double nx = Math.max(x, Math.min(cx, x + 1)) - cx;
                double nz = Math.max(z, Math.min(cz, z + 1)) - cz;
                if (nx * nx + nz * nz > r * r) {
                    continue;
                }
                for (int y = y0; y <= y1; y++) {
                    if (cells.filled(x, y, z)) {
                        n++;
                    }
                }
            }
        }
        for (int y = y0 - 2; y < y0; y++) {
            if (cells.filled(c.getX(), y, c.getZ())) {
                n++;
            }
        }
        return n;
    }

    public static Spot find(BlockPos at, String key, Cells cells) {
        double r = radius(key);
        double h = height(key);
        Direction out = outward(at, cells);
        Direction side = out.getClockWise();
        Spot best = null;
        double bestCost = Double.MAX_VALUE;
        for (int k = 0; k <= MAX_OUT; k++) {
            for (int up = MIN_UP; up <= MAX_UP; up++) {
                for (int s = -MAX_SIDE; s <= MAX_SIDE; s++) {
                    BlockPos c = at.relative(out, k).relative(side, s).above(up);
                    int t = touching(c, r, h, cells);
                    double cost = k + 0.75 * Math.abs(up) + (up < 0 ? 0.5 : 0) + 1.5 * Math.abs(s) + 100.0 * t;
                    if (cost < bestCost) {
                        bestCost = cost;
                        best = new Spot(c, t);
                    }
                }
            }
        }
        return best;
    }

    static Cells world(ServerLevel level) {
        return (x, y, z) -> {
            BlockPos p = new BlockPos(x, y, z);
            return !level.isLoaded(p) || filled(level.getBlockState(p));
        };
    }

    // ---------------------------------------------------------------- 置く・見回る
    public static ListTag floats(float... v) {
        ListTag l = new ListTag();
        for (float f : v) {
            l.add(FloatTag.valueOf(f));
        }
        return l;
    }

    static boolean spawn(ServerLevel level, String name, String text, float scale, int background, double x, double y, double z) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:text_display");
        tag.putString("text", text);
        tag.putString("billboard", "vertical");
        // 揃えを書かないと、バニラが読むたびに「Display entityNot a string」をログに出す
        tag.putString("alignment", "center");
        tag.putInt("background", background);
        tag.putInt("line_width", 400);
        tag.putFloat("view_range", 1.0f);
        CompoundTag light = new CompoundTag();
        light.putInt("sky", 15);
        light.putInt("block", 15);
        tag.put("brightness", light);
        CompoundTag tf = new CompoundTag();
        tf.put("left_rotation", floats(0f, 0f, 0f, 1f));
        tf.put("right_rotation", floats(0f, 0f, 0f, 1f));
        tf.put("translation", floats(0f, 0f, 0f));
        tf.put("scale", floats(scale, scale, scale));
        tag.put("transformation", tf);
        ListTag tags = new ListTag();
        tags.add(StringTag.valueOf(TAG));
        tags.add(StringTag.valueOf(V2));
        tags.add(StringTag.valueOf(NAME_TAG + name));
        tag.put("Tags", tags);
        Entity e = EntityType.loadEntityRecursive(tag, level, en -> {
            en.moveTo(x, y, z, 0f, 0f);
            return en;
        });
        return e != null && level.addFreshEntity(e);
    }

    static List<Display.TextDisplay> mine(ServerLevel level, BlockPos at, String name) {
        AABB box = new AABB(at).inflate(Math.max(MAX_OUT, MAX_SIDE) + 3, MAX_UP + 2, Math.max(MAX_OUT, MAX_SIDE) + 3);
        return level.getEntitiesOfClass(Display.TextDisplay.class, box, d -> d.getTags().contains(NAME_TAG + name) && d.getTags().contains(V2));
    }

    static void clear(ServerLevel level, BlockPos at, String name) {
        // 前の版の名札 (印の上に 1 枚) と、この名札の今の版
        for (Entity old : level.getEntitiesOfClass(Display.TextDisplay.class, new AABB(at).inflate(1.5), d -> d.getTags().contains(TAG) && !d.getTags().contains(V2))) {
            old.discard();
        }
        for (Entity old : mine(level, at, name)) {
            old.discard();
        }
    }

    // 印 at に、施設 name の名札を置く (前に置いた分は外してから)
    public static boolean place(ServerLevel level, BlockPos at, String name, boolean town) {
        clear(level, at, name);
        String key = key(name, town);
        Spot spot = find(at, key, world(level));
        double x = spot.cell().getX() + 0.5;
        double z = spot.cell().getZ() + 0.5;
        double y = spot.cell().getY() + LIFT;
        String lk = langKey(key);
        boolean ok;
        if (hasSub(key)) {
            ok = spawn(level, name, "{\"translate\":\"" + lk + ".sub\",\"color\":\"#E9E0C8\"}", SUB_SCALE, 0x700A0C10, x, y, z);
            y += SUB_H + GAP;
        } else {
            ok = true;
        }
        return spawn(level, name, "{\"translate\":\"" + lk + "\",\"color\":\"#F2C14E\",\"bold\":true}", TITLE_SCALE, 0x6A0A0C10, x, y, z) && ok;
    }

    // 5 秒ごと (都は SiteGuard、土地の町は ContinentTowns の見回りから): 名札が無ければ置き直す (生き物の動いているチャンクだけ)。
    // 都は建てたときに写した印に、今の設計図の印を重ねる (あとから足した名札 = 訓練場が前の版の都にも出るように)
    public static void keep(ServerLevel level, SiteData.Entry e, boolean town) {
        Map<String, BlockPos> marks = new java.util.LinkedHashMap<>();
        for (int i = 0; i < e.extraNames.size(); i++) {
            if (e.extraNames.get(i).startsWith("label_")) {
                marks.put(e.extraNames.get(i), e.extraPos.get(i));
            }
        }
        if (!town) {
            marks.putAll(TownGuide.worldMarks(level.getServer(), "label_"));
        }
        for (Map.Entry<String, BlockPos> m : marks.entrySet()) {
            String name = m.getKey().substring(6);
            BlockPos at = m.getValue();
            if (!level.isPositionEntityTicking(at)) {
                continue;
            }
            if (HIDDEN.contains(name)) {
                clear(level, at, name);
                continue;
            }
            if (mine(level, at, name).isEmpty()) {
                place(level, at, name, town);
            }
        }
    }
}
