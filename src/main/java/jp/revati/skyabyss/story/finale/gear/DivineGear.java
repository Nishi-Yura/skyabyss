package jp.revati.skyabyss.story.finale.gear;

import java.util.List;
import java.util.Map;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.TitanGearSet;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.rpg.skill.Job;
import jp.revati.skyabyss.rpg.skill.SkillManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// 終章の神器 (docs/design/50 の 4.5)。6 職に一つずつ、階級はアーティファクト (★ 伝説)、必要 Lv 50。
//   受け取り  雲上の巡礼者を初めて退けた人に、いまの職の神器と雲上の心核 (SeatArena.onDeath → reward)。二度目からは心核 25% と心核の欠片
//   職を替える 職の間で職を替えると、持っている神器は替えた職の神器になる (強化と特性はそのまま。JobChoice.switchTo → swap)
//   強化      燃料は心核の欠片 (共鳴の炉。由来 cloudsea の図鑑の燃料)。七つの心核 (五つの巨神・深淵・月・雲上) は、しゃがんで使うと欠片 5 つに砕ける
//   書き換え  どれもその職の幹の技を、島と座で覚えた決まりの形に書き換える (rpg/skill/Rune の DIVINE_*・LegendRewrites)
public final class DivineGear {
    public static final String DUNGEON = "cloudsea";
    public static final int REQUIRED_LEVEL = 50;
    public static final String SWORD = id("divine_sky_sword");
    public static final String HAMMER = id("divine_bell_hammer");
    public static final String STAFF = id("divine_dawn_staff");
    public static final String BOW = id("divine_plume_bow");
    public static final String DAGGER = id("divine_rewind_dagger");
    public static final String BELL = id("divine_promise_bell_staff");
    public static final String SHARD = id("heart_core_shard");
    public static final String CORE = id("cloud_heart");
    public static final List<String> RELICS = List.of(SWORD, HAMMER, STAFF, BOW, DAGGER, BELL);
    // 砕くと欠片になる七つの心核
    public static final List<String> CORES = List.of(id("stone_whale_core"), id("tide_shell_core"), id("thunder_wing_core"), id("ember_core"),
            id("abyss_core"), id("moon_heart"), CORE);
    public static final int SHARDS_PER_CORE = 5;
    public static final float CORE_CHANCE = 0.25f;
    public static final int SHARDS_MIN = 2;
    public static final int SHARDS_MAX = 4;
    static final String GIVEN = "skyabyss_divine_given";

    // ---------------------------------------------------------------- 数値 (月のヒロイックの約 1.1 倍)
    public static final double SWORD_ATTACK = 15.0;
    public static final double SWORD_SPEED = 1.6;
    public static final int SWORD_EVERY = 4;
    public static final double SWORD_SLAM = 0.6;
    public static final double HAMMER_ATTACK = 15.2;
    public static final double HAMMER_SPEED = 1.6;
    public static final int HAMMER_EVERY = 3;
    public static final double HAMMER_RING = 0.3;
    public static final double HAMMER_RADIUS = 3.5;
    public static final double STAFF_ATTACK = 20.0;
    public static final double STAFF_SPEED = 1.2;
    public static final int STAFF_LIT_TICKS = 80;
    public static final double STAFF_LIT = 0.12;
    public static final double BOW_MULTIPLIER = 2.3;
    public static final double BOW_FEATHERS = 0.3;
    public static final int BOW_FEATHER_COUNT = 3;
    public static final int BOW_FEATHER_TICKS = 16;
    public static final double DAGGER_ATTACK = 10.8;
    public static final double DAGGER_SPEED = 2.0;
    public static final double DAGGER_ECHO = 0.35;
    public static final int DAGGER_ECHO_TICKS = 30;
    public static final double BELL_ATTACK = 18.2;
    public static final double BELL_SPEED = 1.4;
    public static final double BELL_RADIUS = 5;
    public static final int BELL_PROMISE_TICKS = 60;
    public static final double BELL_PROMISE = 0.85;

    // 図鑑の形は燃料のためだけ (燃料 = 心核の欠片、核 = 雲上の心核)。神器は巡礼者の報酬で、抽選には入れない (品は空)
    public static final TitanGearSet SET = new TitanGearSet(DUNGEON, SHARD, CORE, List.of());

    static final Map<Job, String> BY_JOB = Map.of(Job.WARRIOR, SWORD, Job.SMITH, HAMMER, Job.MAGE, STAFF, Job.ARCHER, BOW, Job.ASSASSIN, DAGGER,
            Job.SHAMAN, BELL);

    private DivineGear() {
    }

    static String id(String path) {
        return SkyAbyss.MODID + ":" + path;
    }

    public static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    }

    public static String forJob(Job job) {
        return job == null ? SWORD : BY_JOB.getOrDefault(job, SWORD);
    }

    public static boolean isRelic(ItemStack stack) {
        return !stack.isEmpty() && RELICS.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    public static boolean isCore(ItemStack stack) {
        return !stack.isEmpty() && CORES.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    // いまの職 (職の間で選んだ職。なければ祠で選んだ職)
    public static Job job(ServerPlayer p) {
        String cur = SkillManager.get(p).curJob;
        for (Job j : Job.values()) {
            if (j.id.equals(cur)) {
                return j;
            }
        }
        return jp.revati.skyabyss.rpg.skill.JobChoice.chosen(p);
    }

    // ---------------------------------------------------------------- 受け取り (巡礼者を退けた人)
    public static void reward(ServerPlayer p) {
        if (!p.getPersistentData().getBoolean(GIVEN)) {
            p.getPersistentData().putBoolean(GIVEN, true);
            ItemStack relic = new ItemStack(item(forJob(job(p))));
            Quests.give(p, relic.copy());
            Quests.give(p, new ItemStack(item(CORE)));
            Notice.objective(p, "message.skyabyss.divine.given", relic.getHoverName());
            return;
        }
        int n = SHARDS_MIN + p.getRandom().nextInt(SHARDS_MAX - SHARDS_MIN + 1);
        Quests.give(p, new ItemStack(item(SHARD), n));
        if (p.getRandom().nextFloat() < CORE_CHANCE) {
            Quests.give(p, new ItemStack(item(CORE)));
        }
    }

    public static boolean given(ServerPlayer p) {
        return p.getPersistentData().getBoolean(GIVEN);
    }

    // ---------------------------------------------------------------- 職を替えた: 持っている神器を替えた職のものへ (強化・特性はそのまま)
    public static int swap(ServerPlayer p, Job job) {
        Item to = item(forJob(job));
        int n = 0;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (isRelic(s) && s.getItem() != to) {
                inv.setItem(i, s.transmuteCopy(to, s.getCount()));
                n++;
            }
        }
        if (n > 0) {
            Notice.objective(p, "message.skyabyss.divine.swapped", Component.translatable(to.getDescriptionId()));
        }
        return n;
    }

    // ---------------------------------------------------------------- 心核を砕く (しゃがんで使う。DivineGearEvents)
    public static boolean breakCore(ServerPlayer p, ItemStack stack) {
        if (!isCore(stack)) {
            return false;
        }
        stack.shrink(1);
        Quests.give(p, new ItemStack(item(SHARD), SHARDS_PER_CORE));
        Notice.bar(p, Component.translatable("message.skyabyss.divine.core_broken", SHARDS_PER_CORE));
        return true;
    }
}
