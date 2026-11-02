package jp.revati.skyabyss.item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.skill.Job;
import jp.revati.skyabyss.rpg.skill.SkillActions;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 武器ごとに決まった数値の行 (Wynncraft の ID。docs/design/03 の 1.6。2026-11-02 の 00 の 341 A)。
// 1 秒あたりの傷は階級でそろえたまま、品ごとに「呪文寄り・近接寄り・速さ寄り」の個性を数値の行で付ける。ランダムな特性 (affix) はこの上に乗る。
//   行の数: コモン・アンコモン・レア 2 本 / エピック 2 本 + 欠点 1 本 / ヒロイック・アーティファクト 3 本 + 欠点 1 本 / 神器 3 本 (欠点は大きな能力の側にある)
//   選び方: 職ごとの行の候補から、品の id で決まる並びで選ぶ (同じ品はいつも同じ行)。一本目が品の「顔」の行で、ほかは 0.6 倍
//   大きさ: 候補の基の値 × 階級の倍率 (コモン 0.35 … ヒロイック 1.0 … 神器 1.25)。欠点は基の値の 0.6 倍の逆向き
// 手に持って必要 Lv に届いているときだけ効く (矢の傷は、その弓から放った矢)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class WeaponLines {
    private WeaponLines() {
    }

    public enum Stat {
        MELEE("melee", true),
        SKILL("skill", true),
        ARROW("arrow", true),
        COOLDOWN("cooldown", true),
        MANA_REGEN("mana_regen", true),
        MANA_STEAL("mana_steal", false),
        LIFESTEAL("lifesteal", true),
        MAX_HP("max_hp", true),
        MOVE("move", true),
        ATTACK_SPEED("attack_speed", true),
        ARMOR("armor", true);

        public final String id;
        // 割合 (+12%) で見せるか、そのままの数 (+3) か
        public final boolean percent;

        Stat(String id, boolean percent) {
            this.id = id;
            this.percent = percent;
        }

        public String key() {
            return "weapon_line.skyabyss." + id;
        }

        // 値が正のとき良いか (待ちは短いほど良い)
        boolean goodWhenPositive() {
            return this != COOLDOWN;
        }
    }

    // value は効き目そのもの (待ち −8% なら −0.08)。bad = 欠点の行
    public record Line(Stat stat, double value, boolean bad) {
    }

    // 職ごとの行の候補と基の値 (ヒロイックでの「顔」の行の大きさ)
    private record Cand(Stat stat, double base) {
    }

    private static final Map<Job, List<Cand>> GOOD = new HashMap<>();
    private static final Map<Job, List<Stat>> BAD = new HashMap<>();
    static {
        GOOD.put(Job.WARRIOR, List.of(new Cand(Stat.MELEE, 0.15), new Cand(Stat.SKILL, 0.12), new Cand(Stat.MAX_HP, 0.10), new Cand(Stat.ARMOR, 0.12),
                new Cand(Stat.LIFESTEAL, 0.05), new Cand(Stat.ATTACK_SPEED, 0.08)));
        GOOD.put(Job.SMITH, List.of(new Cand(Stat.MELEE, 0.14), new Cand(Stat.SKILL, 0.12), new Cand(Stat.ARMOR, 0.12), new Cand(Stat.MAX_HP, 0.08),
                new Cand(Stat.MANA_STEAL, 3), new Cand(Stat.COOLDOWN, -0.08)));
        GOOD.put(Job.MAGE, List.of(new Cand(Stat.SKILL, 0.18), new Cand(Stat.MANA_REGEN, 0.25), new Cand(Stat.COOLDOWN, -0.10), new Cand(Stat.MAX_HP, 0.08),
                new Cand(Stat.MANA_STEAL, 3), new Cand(Stat.MOVE, 0.06)));
        GOOD.put(Job.ARCHER, List.of(new Cand(Stat.ARROW, 0.16), new Cand(Stat.SKILL, 0.12), new Cand(Stat.MOVE, 0.10), new Cand(Stat.MANA_REGEN, 0.15),
                new Cand(Stat.COOLDOWN, -0.08), new Cand(Stat.MAX_HP, 0.06)));
        GOOD.put(Job.ASSASSIN, List.of(new Cand(Stat.MELEE, 0.14), new Cand(Stat.MOVE, 0.12), new Cand(Stat.ATTACK_SPEED, 0.10), new Cand(Stat.SKILL, 0.12),
                new Cand(Stat.LIFESTEAL, 0.05), new Cand(Stat.MANA_STEAL, 3)));
        GOOD.put(Job.SHAMAN, List.of(new Cand(Stat.SKILL, 0.14), new Cand(Stat.MANA_REGEN, 0.25), new Cand(Stat.COOLDOWN, -0.10), new Cand(Stat.MAX_HP, 0.10),
                new Cand(Stat.ARMOR, 0.10), new Cand(Stat.MOVE, 0.06)));
        BAD.put(Job.WARRIOR, List.of(Stat.SKILL, Stat.MOVE, Stat.MANA_REGEN, Stat.ATTACK_SPEED));
        BAD.put(Job.SMITH, List.of(Stat.MOVE, Stat.ATTACK_SPEED, Stat.MANA_REGEN, Stat.SKILL));
        BAD.put(Job.MAGE, List.of(Stat.MAX_HP, Stat.ARMOR, Stat.MOVE));
        BAD.put(Job.ARCHER, List.of(Stat.MAX_HP, Stat.ARMOR, Stat.SKILL));
        BAD.put(Job.ASSASSIN, List.of(Stat.MAX_HP, Stat.ARMOR, Stat.SKILL));
        BAD.put(Job.SHAMAN, List.of(Stat.MOVE, Stat.ARMOR, Stat.ATTACK_SPEED));
    }

    // 欠点の大きさを決めるための、職を問わない基の値
    private static double baseOf(Stat s) {
        return switch (s) {
            case MELEE, ARROW -> 0.15;
            case SKILL -> 0.14;
            case COOLDOWN -> -0.10;
            case MANA_REGEN -> 0.25;
            case MANA_STEAL -> 3;
            case LIFESTEAL -> 0.05;
            case MAX_HP, ARMOR -> 0.10;
            case MOVE, ATTACK_SPEED -> 0.10;
        };
    }

    public static double tierScale(GearTier t) {
        return switch (t) {
            case COMMON -> 0.35;
            case UNCOMMON -> 0.5;
            case RARE -> 0.65;
            case EPIC -> 0.8;
            case HEROIC -> 1.0;
            case ARTIFACT -> 1.1;
            case MYTHIC -> 1.25;
        };
    }

    private static final Map<Item, List<Line>> CACHE = new HashMap<>();

    // 品の行 (職の武器でなければ空)
    public static List<Line> of(Item item) {
        return CACHE.computeIfAbsent(item, WeaponLines::make);
    }

    // 自己テスト用: 覚えずに作り直す (同じ品はいつも同じ行か)
    public static List<Line> make0(Item item) {
        return make(item);
    }

    static List<Line> make(Item item) {
        Job job = Job.of(item);
        if (job == null || !(item instanceof ModGear gear)) {
            return List.of();
        }
        GearTier tier = gear.tier();
        int goods = switch (tier) {
            case HEROIC, ARTIFACT, MYTHIC -> 3;
            default -> 2;
        };
        boolean bad = tier == GearTier.EPIC || tier == GearTier.HEROIC || tier == GearTier.ARTIFACT;
        String path = BuiltInRegistries.ITEM.getKey(item).getPath();
        Random rng = new Random(path.hashCode() * 31L + 7);
        List<Cand> pool = new ArrayList<>(GOOD.get(job));
        java.util.Collections.shuffle(pool, rng);
        double scale = tierScale(tier);
        List<Line> out = new ArrayList<>();
        for (int i = 0; i < goods && i < pool.size(); i++) {
            Cand c = pool.get(i);
            out.add(new Line(c.stat, round(c.stat, c.base * scale * (i == 0 ? 1.0 : 0.6)), false));
        }
        if (bad) {
            List<Stat> badPool = new ArrayList<>(BAD.get(job));
            badPool.removeIf(s -> out.stream().anyMatch(l -> l.stat == s));
            if (!badPool.isEmpty()) {
                Stat s = badPool.get(rng.nextInt(badPool.size()));
                out.add(new Line(s, round(s, -baseOf(s) * scale * 0.6), true));
            }
        }
        return List.copyOf(out);
    }

    private static double round(Stat s, double v) {
        if (!s.percent) {
            return Math.signum(v) * Math.max(1, Math.round(Math.abs(v)));
        }
        double r = Math.round(v * 100) / 100.0;
        return r == 0 ? Math.signum(v) * 0.01 : r;
    }

    // 手の武器のその行の合計 (必要 Lv に届いていなければ 0)
    public static double sum(Player p, Stat stat) {
        ItemStack hand = p.getMainHandItem();
        if (hand.isEmpty() || !GearLevel.meets(p, hand)) {
            return 0;
        }
        double v = 0;
        for (Line l : of(hand.getItem())) {
            if (l.stat == stat) {
                v += l.value;
            }
        }
        return v;
    }

    static double sumOf(ItemStack stack, Stat stat) {
        double v = 0;
        for (Line l : of(stack.getItem())) {
            if (l.stat == stat) {
                v += l.value;
            }
        }
        return v;
    }

    // ---------------------------------------------------------------- 効き目
    // 近接の傷・矢の傷 (技の傷は SkillEffects.damagePower、待ちは SkillManager.cooldownFor、マナの戻りは SkillManager.regenTick)
    @SubscribeEvent(priority = EventPriority.LOW)
    static void onIncoming(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer p) || event.getEntity() == p || event.getAmount() <= 0) {
            return;
        }
        var direct = event.getSource().getDirectEntity();
        double v = 0;
        if (direct == p && SkillActions.dealing() == null) {
            v = sum(p, Stat.MELEE);
        } else if (direct instanceof AbstractArrow arrow && arrow.getWeaponItem() != null && !arrow.getWeaponItem().isEmpty()
                && GearLevel.meets(p, arrow.getWeaponItem())) {
            v = sumOf(arrow.getWeaponItem(), Stat.ARROW);
        }
        if (v != 0) {
            event.setAmount((float) (event.getAmount() * Math.max(0.1, 1 + v)));
        }
    }

    // マナ吸収 (AffixEvents の 0.6 秒の決まりに足す) と体力吸収 (近接。1 回 2 点まで)
    public static double manaSteal(Player p) {
        return sum(p, Stat.MANA_STEAL);
    }

    @SubscribeEvent
    static void onDamagePost(LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer p) || event.getSource().getDirectEntity() != p || event.getEntity() == p
                || event.getNewDamage() <= 0 || !p.isAlive()) {
            return;
        }
        double steal = sum(p, Stat.LIFESTEAL);
        if (steal > 0 && p.getHealth() < p.getMaxHealth()) {
            p.heal((float) Math.min(2.0, event.getNewDamage() * steal));
        }
    }

    // 属性の行 (最大体力・足・攻撃の速さ・防御)。手の武器が替わったら付け替える (0.5 秒ごと)
    private static final Map<Stat, ResourceLocation> IDS = new HashMap<>();
    private static final Map<UUID, Item> APPLIED = new HashMap<>();

    private static ResourceLocation id(Stat s) {
        return IDS.computeIfAbsent(s, k -> ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "weapon_line/" + k.id));
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.tickCount % 10 != 4) {
            return;
        }
        ItemStack hand = p.getMainHandItem();
        Item key = hand.isEmpty() || !GearLevel.meets(p, hand) ? null : hand.getItem();
        if (APPLIED.containsKey(p.getUUID()) && APPLIED.get(p.getUUID()) == key) {
            return;
        }
        APPLIED.put(p.getUUID(), key);
        apply(p, Attributes.MAX_HEALTH, Stat.MAX_HP);
        apply(p, Attributes.MOVEMENT_SPEED, Stat.MOVE);
        apply(p, Attributes.ATTACK_SPEED, Stat.ATTACK_SPEED);
        apply(p, Attributes.ARMOR, Stat.ARMOR);
        if (p.getHealth() > p.getMaxHealth()) {
            p.setHealth(p.getMaxHealth());
        }
    }

    private static void apply(Player p, Holder<Attribute> attr, Stat stat) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst == null) {
            return;
        }
        ResourceLocation rid = id(stat);
        inst.removeModifier(rid);
        double v = sum(p, stat);
        if (v != 0) {
            inst.addTransientModifier(new AttributeModifier(rid, v, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    public static void forget(UUID id) {
        APPLIED.remove(id);
    }

    // ---------------------------------------------------------------- 説明 (手元)
    @SubscribeEvent(priority = EventPriority.LOW)
    static void onTooltip(ItemTooltipEvent event) {
        List<Line> lines = of(event.getItemStack().getItem());
        if (lines.isEmpty()) {
            return;
        }
        for (Line l : lines) {
            boolean good = l.stat.goodWhenPositive() == (l.value > 0);
            String num = (l.value > 0 ? "+" : "−") + (l.stat.percent ? Math.round(Math.abs(l.value) * 100) + "%" : String.valueOf((long) Math.abs(l.value)));
            event.getToolTip().add(Component.translatable(l.stat.key(), num).withColor(good ? 0x8FDB8F : 0xE07A7A));
        }
    }

    // 自己テスト用: 階級ごとの行の数の確かめ
    public static boolean wellFormed(Item item) {
        List<Line> lines = of(item);
        if (!(item instanceof ModGear g) || Job.of(item) == null) {
            return lines.isEmpty();
        }
        int bads = (int) lines.stream().filter(Line::bad).count();
        int goods = lines.size() - bads;
        return switch (g.tier()) {
            case COMMON, UNCOMMON, RARE -> goods == 2 && bads == 0;
            case EPIC -> goods == 2 && bads == 1;
            case HEROIC, ARTIFACT -> goods == 3 && bads == 1;
            case MYTHIC -> goods == 3 && bads == 0;
        };
    }
}
