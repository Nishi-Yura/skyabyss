package jp.revati.skyabyss.loot.box;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.abyss.gear.AbyssGear;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import jp.revati.skyabyss.item.accessory.Accessory;
import jp.revati.skyabyss.item.affix.AffixRules;
import jp.revati.skyabyss.item.wild.WildGear;
import jp.revati.skyabyss.item.wild.WildGearTypes;
import jp.revati.skyabyss.loot.TitanLoot;
import jp.revati.skyabyss.loot.TitanMemoryData;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.pouch.Pouch;
import jp.revati.skyabyss.pouch.PouchRouting;
import jp.revati.skyabyss.rpg.skill.Job;
import jp.revati.skyabyss.story.ch6.gear.MoonGear;
import jp.revati.skyabyss.story.finale.gear.DivineGear;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

// 装備は箱で落ちる (docs/design/52)。
//  - 包む: 敵・ボス・巨神の心臓の装備の抽選 (今までの確率・天井・図鑑のまま) で当たった品は、渡す直前に封の箱に替わり、
//    品そのもの (特性つき) はその人の封の列 (LootBoxData) の後ろに並ぶ。渡し口は TitanLoot.handOver・WildLoot.give・
//    敵の落とし物 (LivingDropsEvent。一番後で、残った装備を箱に替える)
//  - 包まない: 交換 (炉・共鳴の欠片)・初回の選択・層と区画の主の初回の部位 (物語の報酬)・野営の返却は direct の中で渡す。
//    神器・コモン・島の祠の始めの武器・物語の防具 (案内の道の装い) は箱の種類が無いので、そのまま
//  - 開ける: 古い方から列の品を取り出す。列が空の箱 (渡された箱・試しの箱) は、その箱の表から新しく引く (pool)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class LootBoxes {
    private LootBoxes() {
    }

    // 一度に開ける数の上限 (まとめて開ける)
    public static final int MAX_BATCH = 64;
    // ルーレットの帯の長さと、当たりの位置
    public static final int REEL = 32, WIN = 27;
    // 巨神の箱の階級の重み (レア・エピック・ヒロイック)。階級の箱の品の種類の重み (武器・防具・装飾)
    static final int[] TITAN_TIER_WEIGHTS = {45, 45, 10};
    static final int[] CATEGORY_WEIGHTS = {60, 25, 15};
    static final float JOB_BIAS = 2f / 3f;

    public enum Category {
        WEAPON, ARMOR, ACCESSORY
    }

    // ---------------------------------------------------------------- 包まない渡し方
    private static final ThreadLocal<int[]> DIRECT = ThreadLocal.withInitial(() -> new int[1]);
    // この品だけは包まない (層・区画の主の初回の部位)。品の同一性で覚える
    private static final Set<ItemStack> KEEP = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    public static <T> T direct(Supplier<T> body) {
        DIRECT.get()[0]++;
        try {
            return body.get();
        } finally {
            DIRECT.get()[0]--;
        }
    }

    public static void direct(Runnable body) {
        direct(() -> {
            body.run();
            return null;
        });
    }

    public static boolean isDirect() {
        return DIRECT.get()[0] > 0;
    }

    public static ItemStack keepDirect(ItemStack stack) {
        KEEP.add(stack);
        return stack;
    }

    // ---------------------------------------------------------------- どの箱か
    @Nullable
    public static BoxKind kindFor(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ModGear g)) {
            return null;
        }
        GearTier t = g.tier();
        if (!t.atLeast(GearTier.UNCOMMON) || BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().startsWith("shrine_")) {
            return null;
        }
        String d = g.dungeon();
        if (t == GearTier.ARTIFACT) {
            return DivineGear.DUNGEON.equals(d) ? null : BoxKind.byId("box_artifact");
        }
        if (jp.revati.skyabyss.tower.gear.TowerGear.DUNGEON.equals(d)) {
            return BoxKind.byId("box_tower");
        }
        if (g instanceof WildGear || AbyssGear.DUNGEON.equals(d) || MoonGear.DUNGEON.equals(d)) {
            return BoxKind.rarity(t, WildGearTypes.band(g.requiredLevel()));
        }
        return BoxKind.TITANS.contains(d) ? BoxKind.titan(d) : null;
    }

    // 今この品を渡すと箱に替わるか (知らせで中身を先に言わないため)
    public static boolean wouldBox(ItemStack stack) {
        return !isDirect() && !KEEP.contains(stack) && kindFor(stack) != null;
    }

    // 一つの品を包む: 箱の種類があれば、品を owner の封の列に入れて箱を返す。無ければそのまま
    public static ItemStack wrap(MinecraftServer server, UUID owner, ItemStack gear) {
        if (!wouldBox(gear)) {
            return gear;
        }
        BoxKind kind = kindFor(gear);
        Item box = LootBoxSetup.item(kind);
        if (box == null) {
            return gear;
        }
        LootBoxData data = LootBoxData.get(server);
        int n = Math.max(1, gear.getCount());
        for (int i = 0; i < n; i++) {
            data.push(owner, kind.id(), gear.copyWithCount(1));
        }
        return new ItemStack(box, n);
    }

    // 並びの中の装備を箱に替える。並びが書き換えられれば同じ並びを (呼び手の知らせも箱を言う)、できなければ写しを返す
    public static List<ItemStack> wrapAll(MinecraftServer server, UUID owner, List<ItemStack> items) {
        if (isDirect()) {
            return items;
        }
        List<ItemStack> out = items;
        for (int i = 0; i < out.size(); i++) {
            ItemStack s = out.get(i);
            ItemStack w = wrap(server, owner, s);
            if (w == s) {
                continue;
            }
            try {
                out.set(i, w);
            } catch (UnsupportedOperationException e) {
                out = new ArrayList<>(out);
                out.set(i, w);
            }
        }
        return out;
    }

    // 敵の落とし物: ほかの仕組みが落とした装備を、倒した人の箱に替える (一番後。人の落とし物は見ない)
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof Player || event.getEntity().level().isClientSide()) {
            return;
        }
        ServerPlayer killer = event.getSource().getEntity() instanceof ServerPlayer p ? p : null;
        var it = event.getDrops().iterator();
        while (it.hasNext()) {
            ItemEntity e = it.next();
            ItemStack s = e.getItem();
            if (!wouldBox(s)) {
                continue;
            }
            if (killer == null) {
                // 誰のものでもない装備は落とさない (敵は装備を直接落とさない)
                it.remove();
                continue;
            }
            e.setItem(wrap(killer.server, killer.getUUID(), s));
        }
    }

    // ---------------------------------------------------------------- 箱の表
    private static Map<String, List<Item>> pools;

    static Category category(Item item) {
        if (item instanceof ArmorItem) {
            return Category.ARMOR;
        }
        return item instanceof Accessory ? Category.ACCESSORY : Category.WEAPON;
    }

    // その箱から出うる品 (登録された品から、kindFor と同じ決まりで集める。巨神の箱はレア〜ヒロイック)
    public static synchronized List<Item> pool(BoxKind kind) {
        if (pools == null) {
            Map<String, List<Item>> m = new HashMap<>();
            for (BoxKind k : BoxKind.ALL) {
                m.put(k.id(), new ArrayList<>());
            }
            for (Item item : BuiltInRegistries.ITEM) {
                ItemStack s = new ItemStack(item);
                BoxKind k = kindFor(s);
                if (k == null) {
                    continue;
                }
                GearTier t = ((ModGear) item).tier();
                if (k.type() == BoxKind.Type.TITAN && !t.atLeast(GearTier.RARE)) {
                    continue;
                }
                // 階級の箱は、品の帯と箱の帯がそろうものだけ (近い帯へ寄せた品は入れない)
                if (k.type() == BoxKind.Type.RARITY && WildGearTypes.band(((ModGear) item).requiredLevel()) != k.band()) {
                    continue;
                }
                m.get(k.id()).add(item);
            }
            m.replaceAll((id, list) -> List.copyOf(list));
            pools = m;
        }
        return pools.getOrDefault(kind.id(), List.of());
    }

    // 列が空の箱を開けたときの一品 (特性はまだ付けない)。job: 今の職 (武器は 2/3 でその職)
    public static ItemStack roll(BoxKind kind, @Nullable Job job, RandomSource random) {
        List<Item> pool = pool(kind);
        if (pool.isEmpty()) {
            return ItemStack.EMPTY;
        }
        List<Item> from = pool;
        if (kind.type() == BoxKind.Type.TITAN) {
            GearTier[] tiers = {GearTier.RARE, GearTier.EPIC, GearTier.HEROIC};
            from = pick(pool, tiers, TITAN_TIER_WEIGHTS, i -> ((ModGear) i).tier(), random);
        }
        List<Item> cat = pick(from, Category.values(), CATEGORY_WEIGHTS, LootBoxes::category, random);
        if (category(cat.get(0)) == Category.WEAPON && job != null && random.nextFloat() < JOB_BIAS) {
            List<Item> mine = cat.stream().filter(i -> Job.of(i) == job).toList();
            if (!mine.isEmpty()) {
                cat = mine;
            }
        }
        return new ItemStack(cat.get(random.nextInt(cat.size())));
    }

    // 重みで一つの組を選ぶ (中身の無い組は外す)
    private static <K> List<Item> pick(List<Item> pool, K[] keys, int[] weights, java.util.function.Function<Item, K> keyOf, RandomSource random) {
        List<List<Item>> groups = new ArrayList<>();
        int total = 0;
        int[] w = new int[keys.length];
        for (int i = 0; i < keys.length; i++) {
            K k = keys[i];
            List<Item> g = pool.stream().filter(it -> keyOf.apply(it).equals(k)).toList();
            groups.add(g);
            if (!g.isEmpty()) {
                w[i] = weights[i];
                total += w[i];
            }
        }
        if (total <= 0) {
            return pool;
        }
        int r = random.nextInt(total);
        for (int i = 0; i < keys.length; i++) {
            if (r < w[i]) {
                return groups.get(i);
            }
            r -= w[i];
        }
        return pool;
    }

    // ---------------------------------------------------------------- 開ける
    public record Opened(ItemStack gear, List<ItemStack> reel) {
    }

    public static int count(Player p, BoxKind kind) {
        Item box = LootBoxSetup.item(kind);
        return box == null ? 0 : Pouch.countAll(p, box);
    }

    // n 個まで開ける: 箱を取り、品を渡し (旅嚢 → 持ち物 → 足もと)、ルーレットの帯を作る。開けた分を返す
    public static List<Opened> open(ServerPlayer p, BoxKind kind, int n) {
        List<Opened> out = new ArrayList<>();
        Item box = LootBoxSetup.item(kind);
        if (box == null) {
            return out;
        }
        int taken = Pouch.takeAny(p, box, Math.min(n, MAX_BATCH));
        LootBoxData data = LootBoxData.get(p.server);
        RandomSource random = p.getRandom();
        Job job = currentJob(p);
        for (int i = 0; i < taken; i++) {
            ItemStack gear = data.pop(p.getUUID(), kind.id());
            if (gear.isEmpty()) {
                gear = roll(kind, job, random);
                if (gear.isEmpty()) {
                    continue;
                }
                gear = AffixRules.withAffixes(gear, random);
                recordCodex(p, gear);
            }
            PouchRouting.giveOrDrop(p, gear.copy());
            out.add(new Opened(gear, reel(kind, gear, random)));
        }
        if (!out.isEmpty()) {
            announce(p, kind, out);
        }
        return out;
    }

    // 列が空の箱から出た巨神の品は図鑑に数える (列の品は落ちたときに数えてある)
    private static void recordCodex(ServerPlayer p, ItemStack gear) {
        if (gear.getItem() instanceof ModGear g && BoxKind.TITANS.contains(g.dungeon())) {
            TitanMemoryData m = TitanMemoryData.get(p.server);
            m.record(p.getUUID(), g.dungeon()).owned.add(TitanLoot.id(gear.getItem()));
            m.setDirty();
        }
    }

    // ルーレットの帯: 箱の表から並べ、WIN に当たり。当たりの前後には、表のいちばん上の階級の品 (惜しい品) を置く
    static List<ItemStack> reel(BoxKind kind, ItemStack win, RandomSource random) {
        List<Item> pool = pool(kind);
        List<ItemStack> reel = new ArrayList<>(REEL);
        GearTier top = GearTier.COMMON;
        for (Item i : pool) {
            GearTier t = ((ModGear) i).tier();
            if (t.atLeast(top)) {
                top = t;
            }
        }
        GearTier topTier = top;
        List<Item> best = pool.stream().filter(i -> ((ModGear) i).tier() == topTier).toList();
        for (int i = 0; i < REEL; i++) {
            if (i == WIN) {
                reel.add(win.copyWithCount(1));
            } else if (pool.isEmpty()) {
                reel.add(new ItemStack(win.getItem()));
            } else if ((i == WIN - 1 || i == WIN + 1) && !best.isEmpty()) {
                reel.add(new ItemStack(best.get(random.nextInt(best.size()))));
            } else {
                reel.add(new ItemStack(pool.get(random.nextInt(pool.size()))));
            }
        }
        return reel;
    }

    private static void announce(ServerPlayer p, BoxKind kind, List<Opened> out) {
        List<Component> names = new ArrayList<>();
        GearTier best = GearTier.COMMON;
        for (Opened o : out) {
            names.add(TitanLoot.itemLink(o.gear()));
            if (o.gear().getItem() instanceof ModGear g && g.tier().atLeast(best)) {
                best = g.tier();
            }
        }
        // 品の名は画面のルーレットが見せる (知らせの欄に先に出すと当たりが分かってしまう)。画面のない開け方 (自己テストなど) だけ一行
        if (!(p instanceof net.neoforged.neoforge.common.util.FakePlayer) && !BoxService.viewing(p)) {
            Notice.gain(p, Component.translatable("message.skyabyss.box.opened", new ItemStack(LootBoxSetup.item(kind)).getHoverName(),
                    ComponentUtils.formatList(names, Component.literal(" "))));
        }
        // ヒロイック以上: 頭の上に小さな光 (近くの人にも見える。チャットには出さない)
        if (best.atLeast(GearTier.HEROIC)) {
            p.serverLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 2.4, p.getZ(), 24, 0.25, 0.2, 0.25, 0.08);
        }
    }

    static Job currentJob(ServerPlayer p) {
        String id = jp.revati.skyabyss.rpg.skill.SkillManager.get(p).curJob;
        if (id != null && !id.isEmpty()) {
            return Job.byId(id);
        }
        return Job.held(p);
    }
}
