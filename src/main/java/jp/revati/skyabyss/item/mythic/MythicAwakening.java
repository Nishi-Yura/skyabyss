package jp.revati.skyabyss.item.mythic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.story.finale.gear.DivineGearSetup;
import jp.revati.skyabyss.tower.gear.TowerGearSetup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

// 神器の目覚め (2026-11-05 の決めごと。docs/design/03 の 13.5)。神器の武器 18 本は、使ううちに三段まで目覚める。
//   数える   その神器を手に持って (弓は放った矢で) 傷を入れた敵が倒れた数。とどめでなくてよい。主・中ボス (最大体力 BOSS_HEALTH 以上) は BOSS_WORTH 体ぶん。
//            数は品に書く (CustomData の KEY)。同じ品を持ち替えても、職を替えても消えない
//   段       STEPS = 300・1,500・5,000。段 1 で大きな能力の芯の数値 +15%、段 2 で欠点が半分、段 3 で真名と、能力がさらに +15% (合わせて +30%)、
//            当てたときの光の粒 (神器ごとの色。モデルは変えない)。数値 (攻撃力・速さ) は変えない
//   知らせ   段が上がると、「〈名〉が目覚めた (n/3)」と、その神器の言い伝えの一行 (item.skyabyss.<id>.awaken_line)。段 3 で真名 (item.skyabyss.<id>.true_name)
//   見え方   ツールチップに段・次までの数・真名 (client/item/MythicAwakeningTooltip)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class MythicAwakening {
    public static final String KEY = "skyabyss_awaken_kills";
    public static final int[] STEPS = {300, 1500, 5000};
    public static final int BOSS_WORTH = 25;
    public static final double BOSS_HEALTH = 200;
    public static final double BOOST_1 = 0.15;
    public static final double BOOST_3 = 0.30;
    public static final double DRAWBACK_2 = 0.5;

    private MythicAwakening() {
    }

    // 神器の武器 18 本と、段 3 の光の粒の色 (今の大きな能力の粒の色から)
    static final Vector3f WHITE = new Vector3f(0.95f, 0.95f, 1.0f);
    static final Vector3f GOLD = new Vector3f(1.0f, 0.84f, 0.45f);
    static final Vector3f VIOLET = new Vector3f(0.62f, 0.36f, 1.0f);
    static final Vector3f RED = new Vector3f(1.0f, 0.32f, 0.22f);
    static final Vector3f MIST = new Vector3f(0.78f, 0.86f, 1.0f);
    static final Vector3f EMBER = new Vector3f(1.0f, 0.55f, 0.2f);

    private static final Map<Supplier<? extends Item>, Vector3f> ARTIFACTS = new java.util.LinkedHashMap<>();

    static {
        ARTIFACTS.put(DivineGearSetup.SWORD, WHITE);
        ARTIFACTS.put(DivineGearSetup.WARDEN, GOLD);
        ARTIFACTS.put(TowerGearSetup.SWORD, VIOLET);
        ARTIFACTS.put(DivineGearSetup.HAMMER, GOLD);
        ARTIFACTS.put(DivineGearSetup.ASH, EMBER);
        ARTIFACTS.put(TowerGearSetup.HAMMER, VIOLET);
        ARTIFACTS.put(DivineGearSetup.STAFF, RED);
        ARTIFACTS.put(DivineGearSetup.EYELID, GOLD);
        ARTIFACTS.put(TowerGearSetup.STAFF, WHITE);
        ARTIFACTS.put(DivineGearSetup.BOW, GOLD);
        ARTIFACTS.put(DivineGearSetup.MIST, MIST);
        ARTIFACTS.put(TowerGearSetup.BOW, GOLD);
        ARTIFACTS.put(DivineGearSetup.DAGGER, VIOLET);
        ARTIFACTS.put(DivineGearSetup.MOTH, GOLD);
        ARTIFACTS.put(TowerGearSetup.DAGGER, RED);
        ARTIFACTS.put(DivineGearSetup.BELL, WHITE);
        ARTIFACTS.put(DivineGearSetup.KEEPER, GOLD);
        ARTIFACTS.put(TowerGearSetup.BELL, GOLD);
    }

    public static List<Item> items() {
        return ARTIFACTS.keySet().stream().map(s -> (Item) s.get()).toList();
    }

    public static boolean isArtifact(Item item) {
        for (Supplier<? extends Item> s : ARTIFACTS.keySet()) {
            if (s.get() == item) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    static Vector3f color(Item item) {
        for (Map.Entry<Supplier<? extends Item>, Vector3f> e : ARTIFACTS.entrySet()) {
            if (e.getKey().get() == item) {
                return e.getValue();
            }
        }
        return null;
    }

    public static String id(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).getPath();
    }

    // ---------------------------------------------------------------- 数と段
    public static int kills(ItemStack s) {
        if (s.isEmpty()) {
            return 0;
        }
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        return d == null ? 0 : d.copyTag().getInt(KEY);
    }

    public static int stage(int kills) {
        int st = 0;
        for (int step : STEPS) {
            if (kills >= step) {
                st++;
            }
        }
        return st;
    }

    public static int stage(ItemStack s) {
        return s.isEmpty() || !isArtifact(s.getItem()) ? 0 : stage(kills(s));
    }

    // 次の段まであと何体 (段 3 なら 0)
    public static int toNext(ItemStack s) {
        int k = kills(s);
        for (int step : STEPS) {
            if (k < step) {
                return step - k;
            }
        }
        return 0;
    }

    // 大きな能力の芯の数値に掛ける倍率 (段 1・2 は 1.15、段 3 は 1.30)
    public static double boost(ItemStack s) {
        int st = stage(s);
        return st >= 3 ? 1 + BOOST_3 : st >= 1 ? 1 + BOOST_1 : 1;
    }

    // 欠点に掛ける倍率 (段 2 から 0.5)
    public static double drawback(ItemStack s) {
        return stage(s) >= 2 ? DRAWBACK_2 : 1;
    }

    public static double boost(Player p) {
        return boost(p.getMainHandItem());
    }

    public static double drawback(Player p) {
        return drawback(p.getMainHandItem());
    }

    // 品に数を足す。段が上がったら知らせる。新しい段 (変わらなければ -1)
    public static int add(@Nullable ServerPlayer p, ItemStack s, int n) {
        if (s.isEmpty() || n <= 0 || !isArtifact(s.getItem())) {
            return -1;
        }
        int before = kills(s);
        int after = before + n;
        CustomData.update(DataComponents.CUSTOM_DATA, s, t -> t.putInt(KEY, after));
        int sb = stage(before);
        int sa = stage(after);
        if (sa > sb && p != null) {
            announce(p, s, sa);
            return sa;
        }
        return -1;
    }

    // 数を決める (管理のコマンド)。段が上がったら知らせる。神器でなければ false
    public static boolean set(@Nullable ServerPlayer p, ItemStack s, int n) {
        if (s.isEmpty() || !isArtifact(s.getItem())) {
            return false;
        }
        int before = kills(s);
        int after = Math.max(0, n);
        CustomData.update(DataComponents.CUSTOM_DATA, s, t -> t.putInt(KEY, after));
        if (stage(after) > stage(before) && p != null) {
            announce(p, s, stage(after));
        }
        return true;
    }

    static void announce(ServerPlayer p, ItemStack s, int stage) {
        String id = id(s.getItem());
        Notice.gain(p, Component.translatable("message.skyabyss.awaken.stage", s.getHoverName(), stage));
        Notice.hint(p, "item.skyabyss." + id + ".awaken_line");
        if (stage >= 3) {
            Notice.narrate(p, "message.skyabyss.awaken.true_name", s.getHoverName(), Component.translatable("item.skyabyss." + id + ".true_name"));
        }
        ServerLevel level = p.serverLevel();
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2f, 0.6f + 0.15f * stage);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6f, 1.2f);
        Vector3f c = color(s.getItem());
        if (c != null) {
            level.sendParticles(new DustParticleOptions(c, 1.4f), p.getX(), p.getY() + 1.2, p.getZ(), 40, 0.5, 0.8, 0.5, 0);
        }
        SkyAbyss.LOGGER.info("[awaken] {}'s {} reached stage {}", p.getGameProfile().getName(), id, stage);
    }

    // ---------------------------------------------------------------- 数える
    // 傷を入れた神器の持ち主 (敵ごと: 人 → 神器の品の種類。倒れたら消す)
    private static final Map<LivingEntity, Map<ServerPlayer, Item>> HITS = new WeakHashMap<>();

    // この傷を入れた神器 (矢なら放った弓。手の品なら手の品)。神器でなければ null
    @Nullable
    static ItemStack weaponOf(ServerPlayer p, @Nullable Entity direct) {
        if (direct instanceof AbstractArrow arrow) {
            ItemStack bow = arrow.getWeaponItem();
            return bow != null && isArtifact(bow.getItem()) ? bow : null;
        }
        ItemStack hand = p.getMainHandItem();
        return isArtifact(hand.getItem()) ? hand : null;
    }

    @SubscribeEvent
    static void onDamaged(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0 || !(event.getSource().getEntity() instanceof ServerPlayer p) || event.getEntity() instanceof Player) {
            return;
        }
        ItemStack w = weaponOf(p, event.getSource().getDirectEntity());
        if (w == null) {
            return;
        }
        HITS.computeIfAbsent(event.getEntity(), k -> new HashMap<>()).put(p, w.getItem());
        if (stage(w) >= 3) {
            trail(p.serverLevel(), p, event.getEntity(), w.getItem());
        }
    }

    // 段 3: 当てたときの光の粒 (振った向きの弧。矢は当たった所)
    static void trail(ServerLevel level, ServerPlayer p, LivingEntity target, Item item) {
        Vector3f c = color(item);
        if (c == null) {
            return;
        }
        DustParticleOptions dust = new DustParticleOptions(c, 1.0f);
        Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
        Vec3 side = new Vec3(-look.z, 0, look.x);
        Vec3 center = p.position().add(0, 1.1, 0).add(look.scale(1.2));
        if (p.distanceToSqr(target) > 36) {
            center = target.position().add(0, target.getBbHeight() * 0.6, 0);
        }
        for (int i = -4; i <= 4; i++) {
            double t = i / 4.0;
            Vec3 q = center.add(side.scale(t * 1.1)).add(look.scale(-Math.abs(t) * 0.4)).add(0, t * 0.3, 0);
            level.sendParticles(dust, q.x, q.y, q.z, 1, 0.02, 0.02, 0.02, 0);
        }
    }

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide() || dead instanceof Player) {
            return;
        }
        Map<ServerPlayer, Item> hits = HITS.remove(dead);
        if (hits == null) {
            return;
        }
        int worth = dead.getMaxHealth() >= BOSS_HEALTH ? BOSS_WORTH : 1;
        for (Map.Entry<ServerPlayer, Item> e : hits.entrySet()) {
            ServerPlayer p = e.getKey();
            if (p.hasDisconnected() || p.isRemoved() && !(p instanceof net.neoforged.neoforge.common.util.FakePlayer)) {
                continue;
            }
            ItemStack s = find(p, e.getValue());
            if (s != null) {
                add(p, s, worth);
            }
        }
    }

    // 人の持ち物から、その神器の品 (手の品を先に)
    @Nullable
    static ItemStack find(Player p, Item item) {
        if (p.getMainHandItem().getItem() == item) {
            return p.getMainHandItem();
        }
        for (ItemStack s : p.getInventory().items) {
            if (s.getItem() == item) {
                return s;
            }
        }
        return null;
    }

    // 自己テスト用
    static CompoundTag tagOf(ItemStack s) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        return d == null ? new CompoundTag() : d.copyTag();
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        HITS.clear();
    }
}
