package jp.revati.skyabyss.item.extra;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

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
// 画面 (forge) もコマンドも自己テストも、ここだけを呼ぶ。対象は特性の付く装備 (レア以上の武器・防具・盾)
public final class GearFreedom {
    // 穴の抽選 (0 個・1 個・2 個の重み) は階級の表 GearTier.socketOdds

    // 炉の費用 (共鳴の結晶)
    public static final int EXTRACT_DESTROY_CRYSTALS = 2;
    public static final int EXTRACT_KEEP_CRYSTALS = 4;
    public static final int IMPRINT_CRYSTALS = 1;
    public static final int UNSOCKET_CRYSTALS = 1;
    // 穴を足す (打ち直し): 結晶 + 由来の巨神の素材。2 つ目のほうが高い
    public static final int ADD_SOCKET_CRYSTALS = 2;
    public static final int[] ADD_SOCKET_MATERIAL = {8, 16};

    public static final int TAG_COLOR = 0x8FD1C9;
    public static final int ASPECT_COLOR = 0xF2D78A;
    public static final int GEN_COLOR = 0xC9A8FF;

    private GearFreedom() {
    }

    // ---------------------------------------------------------------- 読む
    public static boolean eligible(ItemStack stack) {
        return AffixRules.eligible(stack);
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

    // ---------------------------------------------------------------- 抽選 (手にしたとき・古い装備)
    public static int rollSockets(GearTier tier, RandomSource random) {
        int[] odds = tier.socketOdds();
        int total = 0;
        for (int o : odds) {
            total += o;
        }
        int r = random.nextInt(total);
        for (int i = 0; i < odds.length; i++) {
            r -= odds[i];
            if (r < 0) {
                return i;
            }
        }
        return 0;
    }

    // 付いていなければ穴を抽選して付ける (新しい装備も古いセーブの装備も、手にしたときに一度)。付けたら true
    public static boolean ensure(ItemStack stack, RandomSource random) {
        if (!eligible(stack) || has(stack)) {
            return false;
        }
        GearTier tier = ((ModGear) stack.getItem()).tier();
        stack.set(ModDataComponents.GEAR_EXTRAS.get(), GearExtras.EMPTY.withSockets(rollSockets(tier, random)));
        return true;
    }

    private static void put(ItemStack stack, GearExtras x) {
        stack.set(ModDataComponents.GEAR_EXTRAS.get(), x);
    }

    // ---------------------------------------------------------------- 穴 (A07) と修飾ルーン (A06)
    public static boolean canInsert(ItemStack stack, Gem gem) {
        if (!eligible(stack) || freeSockets(stack) <= 0) {
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

    public static boolean canAddSocket(ItemStack stack) {
        return eligible(stack) && sockets(stack) < GearExtras.MAX_SOCKETS;
    }

    // 打ち直しで穴を足す (抽選の運に頼らず、必ず付けられる道)。費用は ForgeRules が払う
    public static boolean addSocket(ItemStack stack) {
        if (!canAddSocket(stack)) {
            return false;
        }
        put(stack, extras(stack).withSockets(sockets(stack) + 1));
        return true;
    }

    // ---------------------------------------------------------------- 数字: 欠片と形見の特性は特性と同じ道で効く
    // その装備の欠片・形見の特性が足す数字 (種類は武器か防具の側に合わせる)
    public static List<Affix> bonus(ItemStack stack) {
        AffixType.Slot slot = AffixRules.slotOf(stack.getItem());
        GearExtras x = extras(stack);
        if (slot == null || (x.gems().isEmpty() && x.traits().isEmpty())) {
            return List.of();
        }
        List<Affix> out = new ArrayList<>();
        for (Gem g : x.gems()) {
            Element e = g.isShard() ? Element.byId(g.arg()) : null;
            if (e != null) {
                int t = Powder.tier(g);
                out.add(slot == AffixType.Slot.WEAPON ? new Affix(e.weapon, e.weaponValue * t) : new Affix(e.armor, e.armorValue * t));
            }
        }
        out.addAll(x.traits());
        return out;
    }

    // 差した欠片の属性タグ (重なれば重なった数だけ)
    public static List<Element> tags(ItemStack stack) {
        List<Element> out = new ArrayList<>();
        for (Gem g : extras(stack).gems()) {
            Element e = g.isShard() ? Element.byId(g.arg()) : null;
            if (e != null) {
                out.add(e);
            }
        }
        return out;
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
        if (AffixRules.slotOf(hand.getItem()) == AffixType.Slot.WEAPON && GearLevel.meets(player, hand)) {
            out.add(hand);
        }
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack s = player.getItemBySlot(slot);
            if (AffixRules.slotOf(s.getItem()) == AffixType.Slot.ARMOR && GearLevel.meets(player, s)) {
                out.add(s);
            }
        }
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof net.minecraft.world.item.ShieldItem && AffixRules.slotOf(off.getItem()) == AffixType.Slot.ARMOR
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

    // いま効いている属性タグ
    public static Set<Element> activeTags(Player player) {
        Set<Element> out = EnumSet.noneOf(Element.class);
        for (ItemStack s : worn(player)) {
            out.addAll(tags(s));
        }
        return out;
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
        if (!eligible(stack) || !has(stack)) {
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

    // 穴に差したものの一行 (名前と効き目)
    public static Component gemLine(Gem g, ItemStack stack) {
        if (g.isShard()) {
            Element e = Element.byId(g.arg());
            if (e == null) {
                return Component.translatable("affix.skyabyss.unknown").withStyle(ChatFormatting.GRAY);
            }
            AffixType.Slot slot = AffixRules.slotOf(stack.getItem());
            int t = Powder.tier(g);
            Affix a = slot == AffixType.Slot.ARMOR ? new Affix(e.armor, e.armorValue * t) : new Affix(e.weapon, e.weaponValue * t);
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
