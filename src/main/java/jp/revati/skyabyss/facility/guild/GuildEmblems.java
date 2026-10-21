package jp.revati.skyabyss.facility.guild;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;

// ギルドの旗の模様と、名札の特別な色 (docs/design/35 の 6.5)。サーバーとクライアントの両方で使う (画面の見本も同じ旗の品を描く)。
//   模様 = バニラの旗の模様を 1〜3 枚重ねたもの。各層は模様の色 (accent) か地の色 (base) で塗る
//   unlock が null の模様ははじめから使える。そうでなければギルドの功績 (GuildAchievements) で開く
//   名札の色 (Plate) は名札の「[印]」の字の色。旗の品は染料の 16 色のままなので、名札だけに出る
public final class GuildEmblems {
    private GuildEmblems() {
    }

    public record Layer(ResourceKey<BannerPattern> pattern, boolean accent) {
    }

    public record Emblem(String id, List<Layer> layers, @Nullable String unlock) {
        public String nameKey() {
            return "gui.skyabyss.guild.pattern." + id;
        }
    }

    public record Plate(String id, int rgb, String unlock) {
        public String nameKey() {
            return "gui.skyabyss.guild.plate." + id;
        }
    }

    private static Layer a(ResourceKey<BannerPattern> p) {
        return new Layer(p, true);
    }

    private static Layer b(ResourceKey<BannerPattern> p) {
        return new Layer(p, false);
    }

    private static Emblem e(String id, String unlock, Layer... layers) {
        return new Emblem(id, List.of(layers), unlock);
    }

    // 既定 (前の版の旗と同じ形: 中の帯と縁)
    public static final String DEFAULT = "band";

    // 並びは画面の順 (はじめからの模様、そのあとに功績で開く模様)
    public static final List<Emblem> ALL = List.of(
            e("band", null, a(BannerPatterns.STRIPE_MIDDLE), a(BannerPatterns.BORDER)),
            e("fess", null, a(BannerPatterns.STRIPE_MIDDLE)),
            e("pale", null, a(BannerPatterns.STRIPE_CENTER)),
            e("cross", null, a(BannerPatterns.STRAIGHT_CROSS)),
            e("saltire", null, a(BannerPatterns.CROSS)),
            e("chevron", null, a(BannerPatterns.TRIANGLE_BOTTOM)),
            e("chief", null, a(BannerPatterns.STRIPE_TOP), a(BannerPatterns.STRIPE_BOTTOM)),
            e("per_pale", null, a(BannerPatterns.HALF_VERTICAL)),
            e("per_fess", null, a(BannerPatterns.HALF_HORIZONTAL)),
            e("per_bend", null, a(BannerPatterns.DIAGONAL_LEFT)),
            e("bend", null, a(BannerPatterns.STRIPE_DOWNRIGHT)),
            e("corners", null, a(BannerPatterns.SQUARE_TOP_LEFT), a(BannerPatterns.SQUARE_TOP_RIGHT),
                    a(BannerPatterns.SQUARE_BOTTOM_LEFT)),
            e("lozenge", null, a(BannerPatterns.RHOMBUS_MIDDLE)),
            e("roundel", null, a(BannerPatterns.CIRCLE_MIDDLE)),
            e("eye", null, a(BannerPatterns.RHOMBUS_MIDDLE), b(BannerPatterns.CIRCLE_MIDDLE)),
            e("bordure", null, a(BannerPatterns.BORDER)),
            e("indented", null, a(BannerPatterns.CURLY_BORDER)),
            e("dancetty", null, a(BannerPatterns.TRIANGLES_BOTTOM), a(BannerPatterns.TRIANGLES_TOP)),
            e("paly", null, a(BannerPatterns.STRIPE_SMALL)),
            e("bricks", null, a(BannerPatterns.BRICKS)),
            e("gradient", null, a(BannerPatterns.GRADIENT)),
            // 功績で開く
            e("flower", "requests_1", a(BannerPatterns.FLOWER)),
            e("flow", "requests_5", a(BannerPatterns.FLOW)),
            e("skull", "contrib_500", a(BannerPatterns.SKULL), a(BannerPatterns.BORDER)),
            e("globe", "contrib_1500", a(BannerPatterns.GLOBE)),
            e("guster", "members_10", a(BannerPatterns.GUSTER)),
            e("tide", "requests_15", a(BannerPatterns.GRADIENT_UP), a(BannerPatterns.FLOW), a(BannerPatterns.BORDER)),
            e("abyss_eye", "requests_30", a(BannerPatterns.GRADIENT_UP), a(BannerPatterns.RHOMBUS_MIDDLE), b(BannerPatterns.CIRCLE_MIDDLE)));

    // 名札の特別な色 (どれも功績で開く。"" = 旗の地の色のまま)
    public static final List<Plate> PLATES = List.of(
            new Plate("gold", 0xF2C14E, "requests_10"),
            new Plate("teal", 0x7FD6D0, "contrib_4000"),
            new Plate("silver", 0xDCE4EE, "members_25"),
            new Plate("abyss", 0xB48CFF, "requests_30"));

    public static Emblem emblem(String id) {
        for (Emblem m : ALL) {
            if (m.id().equals(id)) {
                return m;
            }
        }
        return ALL.get(0);
    }

    @Nullable
    public static Emblem byId(String id) {
        for (Emblem m : ALL) {
            if (m.id().equals(id)) {
                return m;
            }
        }
        return null;
    }

    @Nullable
    public static Plate plate(String id) {
        for (Plate p : PLATES) {
            if (p.id().equals(id)) {
                return p;
            }
        }
        return null;
    }

    // その模様の層 (地の色・模様の色)
    public static BannerPatternLayers layers(HolderLookup.Provider registries, String emblemId, int base, int accent) {
        var patterns = registries.lookupOrThrow(Registries.BANNER_PATTERN);
        BannerPatternLayers.Builder b = new BannerPatternLayers.Builder();
        for (Layer l : emblem(emblemId).layers()) {
            var holder = patterns.get(l.pattern());
            if (holder.isPresent()) {
                b.add(holder.get(), DyeColor.byId(l.accent() ? accent : base));
            }
        }
        return b.build();
    }

    // 旗の品 (地の色の旗に模様)
    public static ItemStack banner(HolderLookup.Provider registries, String emblemId, int base, int accent) {
        ItemStack stack = new ItemStack(BannerBlock.byColor(DyeColor.byId(base)).asItem());
        stack.set(DataComponents.BANNER_PATTERNS, layers(registries, emblemId, base, accent));
        return stack;
    }

    // ギルドが使える模様の ID (はじめからのものと、開いたもの)
    public static List<String> available(java.util.Set<String> unlocked) {
        List<String> out = new ArrayList<>();
        for (Emblem m : ALL) {
            if (m.unlock() == null || unlocked.contains(m.unlock())) {
                out.add(m.id());
            }
        }
        return out;
    }

    public static boolean usable(String emblemId, java.util.Set<String> unlocked) {
        Emblem m = byId(emblemId);
        return m != null && (m.unlock() == null || unlocked.contains(m.unlock()));
    }

    public static boolean plateUsable(String plateId, java.util.Set<String> unlocked) {
        if (plateId.isEmpty()) {
            return true;
        }
        Plate p = plate(plateId);
        return p != null && unlocked.contains(p.unlock());
    }
}
