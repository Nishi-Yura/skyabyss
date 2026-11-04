package jp.revati.skyabyss.item.extra;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.item.GearLevel;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import jp.revati.skyabyss.item.affix.Affix;
import jp.revati.skyabyss.item.affix.AffixRules;
import jp.revati.skyabyss.item.affix.AffixType;
import jp.revati.skyabyss.registry.ModDataComponents;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.rpg.skill.Rune;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

// 装備の自由度 (docs/design/31 の A02 アスペクト・A06 技の修飾ルーン・A07 ソケット・B15 世代刻み) の決まり。
// 画面 (forge) もコマンドも自己テストも、ここだけを呼ぶ。
//   穴と粉: アンコモン以上の武器 (弓も)・防具・盾 (socketable)。穴の数は階級で決まる (GearTier.sockets)
//   アスペクト・世代: 特性の付く装備 (レア以上の剣の類・防具・盾。eligible)
public final class GearFreedom {

    // 炉の費用 (共鳴の結晶)
    public static final int EXTRACT_DESTROY_CRYSTALS = 2;
    public static final int EXTRACT_KEEP_CRYSTALS = 4;
    public static final int IMPRINT_CRYSTALS = 1;
    // 外すのは無料 (粉と炉の頁 1-4。粉は段のまま戻るので、差し替えて試せる)
    public static final int UNSOCKET_CRYSTALS = 0;

    public static final int TAG_COLOR = 0x8FD1C9;
    public static final int ASPECT_COLOR = 0xF2D78A;
    public static final int GEN_COLOR = 0xC9A8FF;

    private GearFreedom() {
    }

    // ---------------------------------------------------------------- 読む
    public static boolean eligible(ItemStack stack) {
        return AffixRules.eligible(stack);
    }

    // 穴の側から見た品の種類 (武器か防具か)。特性の付く品はその種類、アンコモンと弓もここで武器・防具になる
    @Nullable
    public static AffixType.Slot socketSlot(net.minecraft.world.item.Item item) {
        AffixType.Slot s = AffixRules.slotOf(item);
        if (s != null) {
            return s;
        }
        if (!(item instanceof ModGear g) || g.tier().sockets() <= 0) {
            return null;
        }
        if (item instanceof net.minecraft.world.item.SwordItem || item instanceof net.minecraft.world.item.BowItem) {
            return AffixType.Slot.WEAPON;
        }
        if (item instanceof net.minecraft.world.item.ArmorItem || item instanceof net.minecraft.world.item.ShieldItem) {
            return AffixType.Slot.ARMOR;
        }
        return null;
    }

    public static boolean socketable(ItemStack stack) {
        return !stack.isEmpty() && socketSlot(stack.getItem()) != null;
    }

    public static boolean isBow(ItemStack stack) {
        return stack.getItem() instanceof net.minecraft.world.item.BowItem;
    }

    public static boolean has(ItemStack stack) {
        return stack.has(ModDataComponents.GEAR_EXTRAS.get());
    }

    public static GearExtras extras(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.GEAR_EXTRAS.get(), GearExtras.EMPTY);
    }

    public static int sockets(ItemStack stack) {
        return extras(stack).sockets();
    }

    public static int freeSockets(ItemStack stack) {
        GearExtras x = extras(stack);
        return x.sockets() - x.gems().size();
    }

    public static String aspect(ItemStack stack) {
        return extras(stack).aspect();
    }

    public static int generation(ItemStack stack) {
        return extras(stack).generation();
    }

    // ---------------------------------------------------------------- 穴の数 (手にしたとき・古い装備)
    // その装備の穴の数 (階級で決まる)
    public static int maxSockets(ItemStack stack) {
        return socketable(stack) && stack.getItem() instanceof ModGear g ? Math.min(GearExtras.MAX_SOCKETS, g.tier().sockets()) : 0;
    }

    // 穴を階級の数にそろえる (新しい装備も古いセーブの装備も。前の抽選で多く開いていた品は減らさない)。変えたら true
    public static boolean ensure(ItemStack stack, RandomSource random) {
        if (!socketable(stack)) {
            return false;
        }
        int want = maxSockets(stack);
        if (!has(stack)) {
            stack.set(ModDataComponents.GEAR_EXTRAS.get(), GearExtras.EMPTY.withSockets(want));
            return true;
        }
        if (sockets(stack) < want) {
            put(stack, extras(stack).withSockets(want));
            return true;
        }
        return false;
    }

    private static void put(ItemStack stack, GearExtras x) {
        stack.set(ModDataComponents.GEAR_EXTRAS.get(), x);
    }

    // ---------------------------------------------------------------- 穴 (A07) と修飾ルーン (A06)
    public static boolean canInsert(ItemStack stack, Gem gem) {
        if (!socketable(stack) || freeSockets(stack) <= 0) {
            return false;
        }
        if (gem.isShard()) {
            return Element.byId(gem.arg()) != null;
        }
        if (gem.isRune()) {
            if (jp.revati.skyabyss.rpg.skill.Skill.byId(gem.arg()) == null || gem.milli() <= 0) {
                return false;
            }
            // 1 つの技に 1 枚 (同じ装備に同じ技のルーンは二枚差さない)
            for (Gem g : extras(stack).gems()) {
                if (g.isRune() && g.arg().equals(gem.arg())) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    public static boolean insert(ItemStack stack, Gem gem) {
        if (!canInsert(stack, gem)) {
            return false;
        }
        GearExtras x = extras(stack);
        List<Gem> gems = new ArrayList<>(x.gems());
        gems.add(gem);
        put(stack, x.withGems(gems));
        return true;
    }

    // 外す。外したものを返す (なければ null)
    @Nullable
    public static Gem remove(ItemStack stack, int index) {
        GearExtras x = extras(stack);
        if (index < 0 || index >= x.gems().size()) {
            return null;
        }
        List<Gem> gems = new ArrayList<>(x.gems());
        Gem out = gems.remove(index);
        put(stack, x.withGems(gems));
        return out;
    }

    // ---------------------------------------------------------------- 数字: 欠片と形見の特性は特性と同じ道で効く
    // その装備の欠片・形見の特性が足す数字 (種類は武器か防具の側に合わせる)
    public static List<Affix> bonus(ItemStack stack) {
        AffixType.Slot slot = socketSlot(stack.getItem());
        GearExtras x = extras(stack);
        if (slot == null || (x.gems().isEmpty() && x.traits().isEmpty())) {
            return List.of();
        }
        List<Affix> out = new ArrayList<>();
        for (Gem g : x.gems()) {
            Element e = g.isShard() ? Element.byId(g.arg()) : null;
            if (e != null) {
                out.add(line(e, slot, isBow(stack), Powder.tier(g)));
            }
        }
        out.addAll(x.traits());
        return out;
    }

    // 粉一つの数字の行 (段の数だけ掛ける)。弓は風が移動の速さになる (攻撃の速さは矢に効かないため)。岩の攻撃力は矢にも掛ける (ChapterBowItem)
    public static Affix line(Element e, AffixType.Slot slot, boolean bow, int tier) {
        if (slot == AffixType.Slot.ARMOR) {
            return new Affix(e.armor, e.armorValue * tier);
        }
        if (bow && e == Element.WIND) {
            return new Affix(AffixType.STRIDE, Element.WIND.armorValue * tier);
        }
        return new Affix(e.weapon, e.weaponValue * tier);
    }

    // ---------------------------------------------------------------- アスペクト (A02)
    // その品自身の書き換え (ヒロイック以上の品。なければ null)
    @Nullable
    public static Rune ownRune(ItemStack stack) {
        if (stack.isEmpty() || !AffixRules.wide(stack)) {
            return null;
        }
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        for (Rune r : Rune.values()) {
            if (r.legendary() && r.weapon.equals(path)) {
                return r;
            }
        }
        return null;
    }

    public static boolean isHollow(ItemStack stack) {
        return extras(stack).hollow();
    }

    public static boolean canExtract(ItemStack stack) {
        return ownRune(stack) != null && !isHollow(stack);
    }

    // 抽出して、アスペクトの芯を作る。壊すなら装備は消え (stack を空にして返す側が捨てる)、印だけ残すなら抜け殻になる
    public static ItemStack extract(ItemStack stack, boolean destroy) {
        Rune r = ownRune(stack);
        if (r == null || isHollow(stack)) {
            return ItemStack.EMPTY;
        }
        if (!destroy) {
            put(stack, extras(stack).withHollow(true));
        }
        return core(r);
    }

    public static ItemStack core(Rune r) {
        ItemStack core = new ItemStack(ModItems.ASPECT_CORE.get());
        core.set(ModDataComponents.ASPECT.get(), r.id);
        return core;
    }

    @Nullable
    public static Rune coreRune(ItemStack core) {
        if (!core.is(ModItems.ASPECT_CORE.get())) {
            return null;
        }
        String id = core.get(ModDataComponents.ASPECT.get());
        Rune r = id == null ? null : Rune.byId(id);
        return r != null && r.legendary() ? r : null;
    }

    // 刻める装備: レアかエピック (ヒロイック以上は自分の書き換えがある) の武器か防具で、まだアスペクトがないもの
    public static boolean canImprint(ItemStack stack, ItemStack core) {
        return coreRune(core) != null && eligible(stack) && stack.getItem() instanceof ModGear g && g.tier().aspectHost()
                && extras(stack).aspect().isEmpty();
    }

    public static boolean imprint(ItemStack stack, ItemStack core) {
        if (!canImprint(stack, core)) {
            return false;
        }
        put(stack, extras(stack).withAspect(coreRune(core).id));
        return true;
    }

    // ---------------------------------------------------------------- 身につけている装備 (技の側が読む)
    // 効く装備: 主の手の武器・着ている防具・左手の盾 (必要 Lv に届いているもの)
    public static List<ItemStack> worn(Player player) {
        List<ItemStack> out = new ArrayList<>(6);
        ItemStack hand = player.getMainHandItem();
        if (socketSlot(hand.getItem()) == AffixType.Slot.WEAPON && GearLevel.meets(player, hand)) {
            out.add(hand);
        }
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack s = player.getItemBySlot(slot);
            if (socketSlot(s.getItem()) == AffixType.Slot.ARMOR && GearLevel.meets(player, s)) {
                out.add(s);
            }
        }
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof net.minecraft.world.item.ShieldItem && socketSlot(off.getItem()) == AffixType.Slot.ARMOR
                && GearLevel.meets(player, off)) {
            out.add(off);
        }
        return out;
    }

    // 書き換え r を、刻んだアスペクトで持っているか (抜け殻でない自分の書き換えは Rune.held が見る)
    public static boolean carries(Player player, Rune r) {
        for (ItemStack s : worn(player)) {
            if (r.id.equals(extras(s).aspect())) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- 世代 (B15)
    // 転生を終えた人の持ち物のうち、特性の付く装備の世代を一つ上げる (その転生で一度だけ)。上げた数を返す
    public static int onRebirth(ServerPlayer player, int rebirthCount) {
        int n = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (bump(inv.getItem(i), rebirthCount, player.getRandom())) {
                n++;
            }
        }
        if (n > 0) {
            inv.setChanged();
            jp.revati.skyabyss.notice.Notice.gain(player, "message.skyabyss.gear_generation", n);
        }
        return n;
    }

    public static boolean bump(ItemStack stack, int rebirthCount, RandomSource random) {
        if (!eligible(stack)) {
            return false;
        }
        ensure(stack, random);
        GearExtras x = extras(stack);
        if (x.generation() >= GearExtras.MAX_GENERATION || x.genStamp() >= rebirthCount) {
            return false;
        }
        int gen = x.generation() + 1;
        List<Affix> traits = new ArrayList<>(x.traits());
        while (traits.size() < gen / GearExtras.GENERATIONS_PER_TRAIT) {
            Affix t = rollTrait(stack, traits, random);
            if (t == null) {
                break;
            }
            traits.add(t);
        }
        put(stack, x.withGeneration(gen, rebirthCount, traits));
        return true;
    }

    // 形見の特性: 付いていない種類 (木の印は除く) から一つ。値は幅の下端 (小さな特性)
    @Nullable
    public static Affix rollTrait(ItemStack stack, List<Affix> traits, RandomSource random) {
        AffixType.Slot slot = AffixRules.slotOf(stack.getItem());
        if (slot == null) {
            return null;
        }
        List<AffixType> pool = new ArrayList<>();
        for (AffixType t : AffixType.values()) {
            if (t.slot == slot && !t.isMark() && !hasType(AffixRules.affixes(stack), t) && !hasType(traits, t)) {
                pool.add(t);
            }
        }
        if (pool.isEmpty()) {
            return null;
        }
        AffixType pick = pool.get(random.nextInt(pool.size()));
        return new Affix(pick, pick.min(false));
    }

    private static boolean hasType(List<Affix> list, AffixType t) {
        for (Affix a : list) {
            if (a.kind() == t) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- ツールチップ
    public static void tooltip(ItemStack stack, List<Component> out) {
        if (!socketable(stack) || !has(stack)) {
            return;
        }
        GearExtras x = extras(stack);
        for (int i = 0; i < x.sockets(); i++) {
            if (i < x.gems().size()) {
                out.add(Component.literal(" ◆ ").withStyle(s -> s.withColor(TAG_COLOR)).append(gemLine(x.gems().get(i), stack)));
            } else {
                out.add(Component.translatable("gear.skyabyss.socket_empty").withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        artLines(stack, out);
        if (!x.aspect().isEmpty()) {
            Rune r = Rune.byId(x.aspect());
            if (r != null) {
                out.add(Component.translatable("gear.skyabyss.aspect", Component.translatable(r.nameKey())).withStyle(s -> s.withColor(ASPECT_COLOR)));
                out.add(Component.translatable("tooltip.skyabyss.legend_rewrite", Component.translatable(r.skillNameKey()), Component.translatable(r.nameKey()))
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        if (x.hollow()) {
            out.add(Component.translatable("gear.skyabyss.hollow").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
        if (x.generation() > 0) {
            out.add(Component.translatable("gear.skyabyss.generation", x.generation()).withStyle(s -> s.withColor(GEN_COLOR)));
            for (Affix t : x.traits()) {
                out.add(Component.literal(" ❖ ").withStyle(s -> s.withColor(GEN_COLOR)).append(AffixRules.describe(t).withStyle(s -> s.withColor(AffixRules.LINE_COLOR))));
            }
            if (x.generation() < GearExtras.MAX_GENERATION) {
                int next = GearExtras.GENERATIONS_PER_TRAIT - x.generation() % GearExtras.GENERATIONS_PER_TRAIT;
                out.add(Component.translatable("gear.skyabyss.generation_next", next).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            }
        }
    }

    // 粉の技の行: 差した粉ごとに、技の名前と段の和 (4 以上で働く)
    public static void artLines(ItemStack stack, List<Component> out) {
        boolean weapon = socketSlot(stack.getItem()) == AffixType.Slot.WEAPON;
        for (java.util.Map.Entry<Element, Integer> e : Powder.weights(stack).entrySet()) {
            boolean on = e.getValue() >= Powder.WEIGHT_FOR_ART;
            Component name = Component.translatable(PowderArts.artKey(e.getKey(), weapon));
            int color = PowderArts.COLORS[e.getKey().ordinal()];
            if (on) {
                out.add(Component.translatable("gear.skyabyss.powder_art_on", name, e.getValue(), Powder.WEIGHT_FOR_ART).withStyle(s -> s.withColor(color)));
            } else {
                out.add(Component.translatable("gear.skyabyss.powder_art_off", name, e.getValue(), Powder.WEIGHT_FOR_ART).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }

    // 炉の粉のつまみの短い一行 (「岩 IV ─ 攻撃力 +8%」)。ルーンはいつもの一行
    public static Component gemShort(Gem g, ItemStack stack) {
        Element e = g.isShard() ? Element.byId(g.arg()) : null;
        if (e == null) {
            return gemLine(g, stack);
        }
        AffixType.Slot slot = socketSlot(stack.getItem());
        int t = Powder.tier(g);
        Affix a = line(e, slot == null ? AffixType.Slot.WEAPON : slot, isBow(stack), t);
        return Component.translatable(e.nameKey() + ".short").append(" " + Powder.roman(t)).withStyle(s -> s.withColor(TAG_COLOR))
                .append(Component.literal(" ─ ").withStyle(ChatFormatting.DARK_GRAY))
                .append(AffixRules.describe(a).withStyle(s -> s.withColor(AffixRules.LINE_COLOR)));
    }

    // 穴に差したものの一行 (名前と効き目)
    public static Component gemLine(Gem g, ItemStack stack) {
        if (g.isShard()) {
            Element e = Element.byId(g.arg());
            if (e == null) {
                return Component.translatable("affix.skyabyss.unknown").withStyle(ChatFormatting.GRAY);
            }
            AffixType.Slot slot = socketSlot(stack.getItem());
            int t = Powder.tier(g);
            Affix a = line(e, slot == null ? AffixType.Slot.WEAPON : slot, isBow(stack), t);
            return Component.translatable(e.nameKey()).append(" " + Powder.roman(t)).withStyle(s -> s.withColor(TAG_COLOR))
                    .append(Component.literal(" ─ ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(AffixRules.describe(a).withStyle(s -> s.withColor(AffixRules.LINE_COLOR)));
        }
        return runeLine(RuneData.fromGem(g));
    }

    public static Component runeLine(RuneData d) {
        jp.revati.skyabyss.rpg.skill.Skill skill = jp.revati.skyabyss.rpg.skill.Skill.byId(d.skill());
        Component name = skill == null ? Component.literal("?") : Component.translatable(skill.nameKey());
        String pct = String.valueOf(Math.round(Math.abs(d.milli() - 1000) / 10.0));
        return Component.translatable(d.cooldown() ? "gear.skyabyss.rune_cooldown" : "gear.skyabyss.rune_power", name, pct)
                .withStyle(s -> s.withColor(ASPECT_COLOR));
    }
}
