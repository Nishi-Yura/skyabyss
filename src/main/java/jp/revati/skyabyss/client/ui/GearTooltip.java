package jp.revati.skyabyss.client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.mojang.datafixers.util.Either;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.GatherSkippedAttributeTooltipsEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.common.util.AttributeUtil;

// 武器・防具のツールチップを、区切りのある見やすい形に並べ直す (2026-11-10 の声「もっと武器や防具のツールチップの見た目を見やすくして。アイコンとか使って」)。
//   頭: 品の絵・名前・階級と出どころ
//   基の数値: 攻撃力・攻撃速度・防御など (バニラの「利き手に持ったとき」の段は出さず、ここに小さな絵つきで)。品ごとの数値の行 (WeaponLines) も
//   効果: 品の説明 (固有の効果)・追加効果など、そのほかの行 (並びはそのまま)
//   特性・穴: 特性 (◇)・代の特性 (❖)・穴
//   条件: 職・必要 Lv・必要な基礎 (足りないときの赤い一行も)
//   強化と物語: 共鳴の強化、物語の一行、Shift の案内
// 行の見分けは文の鍵 (lang のキー) で行う。見分けられない行は「効果」に残すので、情報は消えない。絵は tools/gen/ui/gen_tooltip_icons.py
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class GearTooltip {
    private GearTooltip() {
    }

    static final ResourceLocation ICONS = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/tooltip_icons.png");
    static final int ICON_COUNT = 32;

    // 絵の番号 (gen_tooltip_icons.py の並び)
    static final int ATTACK = 0, SPEED = 1, SHIELD = 2, HEART = 3, MANA = 4, STAR = 5, ARROW = 6, COOLDOWN = 7, BOOT = 8, LIFESTEAL = 9,
            MANA_STEAL = 10, SPARK = 11, BELL = 12, SOCKET_EMPTY = 13, SOCKET_FULL = 14, ENHANCE = 15, JOB = 16, LEVEL = 17, FIST = 18,
            EFFECT = 19, MARK_STONE = 20, MARK_WIND = 21, MARK_TIDE = 22, MARK_ASH = 23, MARK_BONE = 24, CALM = 25, STEADY = 26, WARD = 27,
            MELEE = 28, TOUGH = 29, DOT = 30, LORE = 31;

    private static final Map<String, Integer> AFFIX_ICONS = Map.ofEntries(
            Map.entry("sharp", ATTACK), Map.entry("swift", SPEED), Map.entry("thirst", LIFESTEAL), Map.entry("resonant", BELL),
            Map.entry("keen", SPARK), Map.entry("mana_steal", MANA_STEAL), Map.entry("spell", STAR), Map.entry("mark_stone", MARK_STONE),
            Map.entry("mark_wind", MARK_WIND), Map.entry("mark_tide", MARK_TIDE), Map.entry("mark_ash", MARK_ASH), Map.entry("mark_bone", MARK_BONE),
            Map.entry("bulwark", SHIELD), Map.entry("vigor", HEART), Map.entry("stride", BOOT), Map.entry("ward", WARD), Map.entry("calm", CALM),
            Map.entry("steady", STEADY), Map.entry("mana_regen", MANA));

    private static final Map<String, Integer> LINE_ICONS = Map.ofEntries(
            Map.entry("melee", MELEE), Map.entry("skill", STAR), Map.entry("arrow", ARROW), Map.entry("cooldown", COOLDOWN),
            Map.entry("mana_regen", MANA), Map.entry("mana_steal", MANA_STEAL), Map.entry("lifesteal", LIFESTEAL), Map.entry("max_hp", HEART),
            Map.entry("move", BOOT), Map.entry("attack_speed", SPEED), Map.entry("armor", SHIELD));

    // 並べ直す品: このmod の、階級のある品・防具・職の武器
    static boolean applies(ItemStack stack) {
        if (stack.isEmpty() || !SkyAbyss.MODID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace())) {
            return false;
        }
        return GearTierFrames.tierOf(stack) != null || stack.getItem() instanceof ArmorItem || jp.revati.skyabyss.rpg.skill.Job.of(stack) != null;
    }

    // バニラの「利き手に持ったとき」の段は出さない (数値は頭の下に絵つきで出す)
    @SubscribeEvent
    static void skipAttributes(GatherSkippedAttributeTooltipsEvent event) {
        if (applies(event.getStack())) {
            event.setSkipAll(true);
        }
    }

    @SubscribeEvent
    static void onRegisterFactories(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(IconRow.class, r -> r);
        event.register(Header.class, h -> h);
        event.register(Rule.class, r -> r);
    }

    // ---------------------------------------------------------------- 並べ直し
    @SubscribeEvent(priority = EventPriority.LOW)
    static void gather(RenderTooltipEvent.GatherComponents event) {
        ItemStack stack = event.getItemStack();
        if (!applies(stack)) {
            return;
        }
        List<Either<FormattedText, TooltipComponent>> in = event.getTooltipElements();
        if (in.isEmpty()) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        int wrap = Math.max(120, event.getMaxWidth() > 0 ? event.getMaxWidth() : TooltipWidth.maxFor(event.getScreenWidth()));
        Component name = in.get(0).left().filter(t -> t instanceof Component).map(t -> (Component) t).orElse(stack.getHoverName());
        Component sub = null;
        List<Either<FormattedText, TooltipComponent>> stats = new ArrayList<>();
        List<Either<FormattedText, TooltipComponent>> effect = new ArrayList<>();
        List<Either<FormattedText, TooltipComponent>> traits = new ArrayList<>();
        List<Either<FormattedText, TooltipComponent>> sockets = new ArrayList<>();
        List<Either<FormattedText, TooltipComponent>> reqs = new ArrayList<>();
        List<Either<FormattedText, TooltipComponent>> foot = new ArrayList<>();
        List<Either<FormattedText, TooltipComponent>> lore = new ArrayList<>();
        baseStats(stack, stats, wrap);
        for (int i = 1; i < in.size(); i++) {
            Either<FormattedText, TooltipComponent> e = in.get(i);
            if (e.right().isPresent() || !(e.left().get() instanceof Component c)) {
                effect.add(e);
                continue;
            }
            if (c.getString().isBlank()) {
                continue;
            }
            String key = keyOf(c);
            String lead = literal(c);
            if (key != null && key.startsWith("gear.skyabyss.tier.") && sub == null) {
                sub = c;
            } else if (key != null && key.startsWith("weapon_line.skyabyss.")) {
                stats.add(row(LINE_ICONS.getOrDefault(key.substring("weapon_line.skyabyss.".length()), DOT), c, wrap));
            } else if (lead != null && (lead.contains("◇") || lead.contains("❖")) && !c.getSiblings().isEmpty()) {
                String k = keyOf(c.getSiblings().get(0));
                int icon = k != null && k.startsWith("affix.skyabyss.") ? AFFIX_ICONS.getOrDefault(k.substring("affix.skyabyss.".length()), DOT) : DOT;
                traits.add(row(icon, rest(c), wrap));
            } else if (lead != null && lead.contains("◆")) {
                sockets.add(row(SOCKET_FULL, rest(c), wrap));
            } else if ("gear.skyabyss.socket_empty".equals(key)) {
                sockets.add(row(SOCKET_EMPTY, Component.translatable("gear.skyabyss.socket_empty_short").withStyle(c.getStyle()), wrap));
            } else if (key != null && key.startsWith("gear.skyabyss.powder_art")) {
                sockets.add(e);
            } else if ("gui.skyabyss.job.tooltip".equals(key)) {
                reqs.add(row(JOB, c, wrap));
            } else if ("skyabyss_gear.tooltip.required_level".equals(key)) {
                reqs.add(row(LEVEL, c, wrap));
            } else if ("gui.skyabyss.job.need_stat".equals(key)) {
                reqs.add(row(FIST, c, wrap));
            } else if (key != null && key.startsWith("skyabyss_gear.tooltip.short")) {
                reqs.add(e);
            } else if (key != null && (key.startsWith("gear.skyabyss.enhance") || key.equals("gear.skyabyss.awakened"))) {
                foot.add(row(ENHANCE, c, wrap));
            } else if (key != null && key.endsWith(".story")) {
                lore.add(e);
            } else if ("gear.skyabyss.affix_shift_hint".equals(key) || "gear.skyabyss.affix_unrolled".equals(key)) {
                lore.add(e);
            } else if (key != null && key.endsWith(".desc") && key.startsWith("item.")) {
                effect.add(row(EFFECT, c, wrap));
            } else {
                effect.add(e);
            }
        }
        List<Either<FormattedText, TooltipComponent>> out = new ArrayList<>();
        out.add(Either.right(new Header(stack, name, sub)));
        int edge = GearTierFrames.tierOf(stack) == null ? 0x707888 : GearTierFrames.tierOf(stack).rgb();
        List<Rule> rules = new ArrayList<>();
        for (List<Either<FormattedText, TooltipComponent>> part : List.of(stats, effect, traits, sockets, reqs, foot, lore)) {
            if (part.isEmpty()) {
                continue;
            }
            // 特性と穴、強化と物語は続けて (区切りを挟まない)
            boolean joined = (part == sockets && !traits.isEmpty()) || (part == lore && !foot.isEmpty());
            if (!joined) {
                Rule r = new Rule(edge);
                rules.add(r);
                out.add(Either.right(r));
            }
            out.addAll(part);
        }
        // 区切りの線の長さ = いちばん広い行
        int w = 0;
        for (Either<FormattedText, TooltipComponent> e : out) {
            if (e.left().isPresent()) {
                w = Math.max(w, Math.min(wrap, font.width(e.left().get())));
            } else if (e.right().get() instanceof ClientTooltipComponent ct) {
                w = Math.max(w, ct.getWidth(font));
            }
        }
        for (Rule r : rules) {
            r.width = w;
        }
        in.clear();
        in.addAll(out);
    }

    // 基の数値 (攻撃力・攻撃速度・防御・体力など)。特性の分 (affix/…) は特性の段に出るので除く
    private static void baseStats(ItemStack stack, List<Either<FormattedText, TooltipComponent>> out, int wrap) {
        EquipmentSlotGroup group = stack.getItem() instanceof ArmorItem armor ? EquipmentSlotGroup.bySlot(armor.getEquipmentSlot()) : EquipmentSlotGroup.MAINHAND;
        com.google.common.collect.Multimap<Holder<Attribute>, AttributeModifier> mods;
        try {
            mods = AttributeUtil.getSortedModifiers(stack, group);
        } catch (RuntimeException ex) {
            return;
        }
        for (Map.Entry<Holder<Attribute>, AttributeModifier> en : mods.entries()) {
            Holder<Attribute> attr = en.getKey();
            AttributeModifier m = en.getValue();
            if (m.id().getPath().startsWith("affix/")) {
                continue;
            }
            double v = m.amount();
            String num;
            boolean base = m.id().equals(AttributeUtil.BASE_ATTACK_DAMAGE_ID) || m.id().equals(AttributeUtil.BASE_ATTACK_SPEED_ID);
            if (base) {
                // バニラと同じく、持ち主の素の値を足した数 (画面の倍率は toValueComponent が掛ける。AttributeShownMixin)
                var player = Minecraft.getInstance().player;
                v += player != null ? player.getAttributeBaseValue(attr) : attr.value().getDefaultValue();
            }
            // 数の形は属性の決まり (攻撃力・体力は画面の倍率つき) に任せ、符号だけ付ける
            String body = attr.value().toValueComponent(m.operation(), Math.abs(v), net.minecraft.world.item.TooltipFlag.NORMAL).getString();
            num = base ? body : (v >= 0 ? "+" : "−") + body;
            int color = base ? 0xE9E3D4 : v >= 0 ? 0x8FDB8F : 0xE07A7A;
            MutableComponent text = Component.translatable(attr.value().getDescriptionId()).withColor(0xA9AEBC)
                    .append(Component.literal("  " + num).withColor(color));
            out.add(row(iconOf(attr), text, wrap));
        }
    }

    private static int iconOf(Holder<Attribute> attr) {
        if (attr.is(Attributes.ATTACK_DAMAGE.unwrapKey().orElseThrow())) {
            return ATTACK;
        }
        if (attr.is(Attributes.ATTACK_SPEED.unwrapKey().orElseThrow())) {
            return SPEED;
        }
        if (attr.is(Attributes.ARMOR.unwrapKey().orElseThrow())) {
            return SHIELD;
        }
        if (attr.is(Attributes.ARMOR_TOUGHNESS.unwrapKey().orElseThrow())) {
            return TOUGH;
        }
        if (attr.is(Attributes.MAX_HEALTH.unwrapKey().orElseThrow())) {
            return HEART;
        }
        if (attr.is(Attributes.MOVEMENT_SPEED.unwrapKey().orElseThrow())) {
            return BOOT;
        }
        if (attr.is(Attributes.KNOCKBACK_RESISTANCE.unwrapKey().orElseThrow())) {
            return STEADY;
        }
        if (attr.is(Attributes.ENTITY_INTERACTION_RANGE.unwrapKey().orElseThrow())) {
            return MELEE;
        }
        if (attr.unwrapKey().map(k -> k.location().getPath().equals("max_mana")).orElse(false)) {
            return MANA;
        }
        return DOT;
    }

    private static Either<FormattedText, TooltipComponent> row(int icon, Component text, int wrap) {
        return Either.right(new IconRow(icon, text, wrap - 11));
    }

    // 行の鍵: その行 (またはその最初の部品) の lang のキー
    static String keyOf(Component c) {
        if (c.getContents() instanceof TranslatableContents t) {
            return t.getKey();
        }
        for (Component s : c.getSiblings()) {
            String k = keyOf(s);
            if (k != null) {
                return k;
            }
            if (!s.getString().isBlank()) {
                break;
            }
        }
        return null;
    }

    private static String literal(Component c) {
        return c.getContents() instanceof PlainTextContents.LiteralContents lit ? lit.text() : null;
    }

    // 頭の印 (「 ◇ 」など) を外した残り
    private static Component rest(Component c) {
        MutableComponent out = Component.empty();
        c.getSiblings().forEach(out::append);
        return out;
    }

    // ---------------------------------------------------------------- 描く部品
    // 小さな絵つきの一行 (長ければ絵の右で折り返す)
    public static final class IconRow implements TooltipComponent, ClientTooltipComponent {
        final int icon;
        final Component text;
        final int wrap;
        private List<FormattedCharSequence> lines;

        IconRow(int icon, Component text, int wrap) {
            this.icon = icon;
            this.text = text;
            this.wrap = Math.max(60, wrap);
        }

        private List<FormattedCharSequence> lines(Font font) {
            if (lines == null) {
                lines = font.split(text, wrap);
                if (lines.isEmpty()) {
                    lines = List.of(FormattedCharSequence.EMPTY);
                }
            }
            return lines;
        }

        @Override
        public int getHeight() {
            return lines(Minecraft.getInstance().font).size() * 10;
        }

        @Override
        public int getWidth(Font font) {
            int w = 0;
            for (FormattedCharSequence l : lines(font)) {
                w = Math.max(w, font.width(l));
            }
            return 11 + w;
        }

        @Override
        public void renderText(Font font, int x, int y, org.joml.Matrix4f matrix, net.minecraft.client.renderer.MultiBufferSource.BufferSource buffer) {
            int ly = y;
            for (FormattedCharSequence l : lines(font)) {
                font.drawInBatch(l, x + 11, ly, -1, true, matrix, buffer, Font.DisplayMode.NORMAL, 0, 0xF000F0);
                ly += 10;
            }
        }

        @Override
        public void renderImage(Font font, int x, int y, GuiGraphics g) {
            g.blit(ICONS, x, y, 8, 8, icon * 8, 0, 8, 8, ICON_COUNT * 8, 8);
        }
    }

    // 頭: 品の絵 (16 画素)、名前、階級と出どころ
    public static final class Header implements TooltipComponent, ClientTooltipComponent {
        final ItemStack stack;
        final Component name;
        final Component sub;

        Header(ItemStack stack, Component name, Component sub) {
            this.stack = stack;
            this.name = name;
            this.sub = sub;
        }

        @Override
        public int getHeight() {
            return sub == null ? 18 : 22;
        }

        @Override
        public int getWidth(Font font) {
            return 21 + Math.max(font.width(name), sub == null ? 0 : font.width(sub));
        }

        @Override
        public void renderText(Font font, int x, int y, org.joml.Matrix4f matrix, net.minecraft.client.renderer.MultiBufferSource.BufferSource buffer) {
            int ny = sub == null ? y + 4 : y;
            font.drawInBatch(name, x + 21, ny, -1, true, matrix, buffer, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            if (sub != null) {
                font.drawInBatch(sub, x + 21, y + 11, -1, true, matrix, buffer, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            }
        }

        @Override
        public void renderImage(Font font, int x, int y, GuiGraphics g) {
            int iy = sub == null ? y : y + 2;
            g.fill(x - 1, iy - 1, x + 17, iy + 17, 0x60000000);
            g.renderItem(stack, x, iy);
        }
    }

    // 区切りの細い線 (階級の色を薄く)
    public static final class Rule implements TooltipComponent, ClientTooltipComponent {
        final int rgb;
        int width;

        Rule(int rgb) {
            this.rgb = rgb;
        }

        @Override
        public int getHeight() {
            return 6;
        }

        @Override
        public int getWidth(Font font) {
            return 0;
        }

        @Override
        public void renderImage(Font font, int x, int y, GuiGraphics g) {
            int w = Math.max(20, width);
            g.fill(x, y + 2, x + w, y + 3, 0x70000000 | rgb);
        }
    }
}
