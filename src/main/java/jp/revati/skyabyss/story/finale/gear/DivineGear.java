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

// 終章の神器 (docs/design/50 の 4.5)。6 職に一つずつ、階級は神器 (いちばん上。あいだの塔の装備と同じ)、必要 Lv 50。
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
    public static final List<String> FIRSTS = List.of(SWORD, HAMMER, STAFF, BOW, DAGGER, BELL);
    // 二本目 (docs/design/50 の 4.5.1。雲の島の敵と主の形。効き方は DivineSecondEvents)
    public static final String WARDEN = id("divine_warden_blade");
    public static final String ASH = id("divine_ash_hammer");
    public static final String EYELID = id("divine_eyelid_staff");
    public static final String MIST = id("divine_mist_bow");
    public static final String MOTH = id("divine_moth_dagger");
    public static final String KEEPER = id("divine_keeper_bell_staff");
    public static final List<String> SECONDS = List.of(WARDEN, ASH, EYELID, MIST, MOTH, KEEPER);
    public static final List<String> RELICS = List.of(SWORD, HAMMER, STAFF, BOW, DAGGER, BELL, WARDEN, ASH, EYELID, MIST, MOTH, KEEPER);
    // 防具 3 式 (品は <一式>_<helmet|chestplate|leggings|boots>) と装飾 2
    public static final String BELLKEEPER = "divine_bellkeeper";
    public static final String MISTWALKER = "divine_mistwalker";
    public static final String LAMPKEEPER = "divine_lampkeeper";
    public static final List<String> ARMOR_SETS = List.of(BELLKEEPER, MISTWALKER, LAMPKEEPER);
    public static final String CHARM = id("divine_plume_charm");
    public static final String CHIME = id("divine_promise_chime");
    // 雲上の箱 (中は神器の武器・防具・装飾のどれか一つ): 二度目からの巡礼者で BOX_CHANCE、巡礼の試しは段ごとに + BOX_TRIAL_STEP
    public static final String BOX = "box_cloud";
    public static final float BOX_CHANCE = 0.20f;
    public static final float BOX_TRIAL_STEP = 0.05f;
    // 砕くと欠片になる七つの心核
    public static final List<String> CORES = List.of(id("stone_whale_core"), id("tide_shell_core"), id("thunder_wing_core"), id("ember_core"),
            id("abyss_core"), id("moon_heart"), CORE);
    public static final int SHARDS_PER_CORE = 5;
    public static final float CORE_CHANCE = 0.25f;
    public static final int SHARDS_MIN = 2;
    public static final int SHARDS_MAX = 4;
    static final String GIVEN = "skyabyss_divine_given";

    // 防具 (頭・胴・脚・靴の防御と強靭。塔の防具と同じ段) と 4 部位の効果、装飾、二本目の効き方
    public static final int[][] ARMOR_DEFENSE = {{7, 11, 9, 7}, {5, 9, 7, 5}, {5, 8, 6, 5}};
    public static final float[] ARMOR_TOUGHNESS = {6f, 4f, 3f};
    public static final double BELLKEEPER_TAKEN = 0.92;
    public static final double BELLKEEPER_PUSH_RADIUS = 4;
    public static final int BELLKEEPER_COOLDOWN = 160;
    public static final double MISTWALKER_SPEED = 0.10;
    public static final int MISTWALKER_HIDE = 20;
    public static final int MISTWALKER_COOLDOWN = 200;
    public static final double LAMPKEEPER_REGEN = 0.35;
    public static final int LAMPKEEPER_WARD_TICKS = 80;
    public static final double LAMPKEEPER_WARD = 0.92;
    public static final double LAMPKEEPER_RADIUS = 6;
    public static final double CHARM_AIR = 0.08;
    public static final double CHIME_REVIVE = 1.5;
    public static final int CHIME_WARD_TICKS = 60;
    public static final double CHIME_WARD = 0.7;
    public static final int WARDEN_GUARD_TICKS = 60;
    public static final double WARDEN_GUARD = 0.40;
    public static final int ASH_MELT_TICKS = 60;
    public static final double ASH_MELT = 0.10;
    public static final int EYELID_TICKS = 60;
    public static final double EYELID_RANGE = 6;
    public static final double EYELID_PILLAR = 0.8;
    public static final int MIST_TICKS = 60;
    public static final double MIST_RADIUS = 2.5;
    public static final double MIST_TAKEN = 0.10;
    public static final int MOTH_TICKS = 100;
    public static final double MOTH_BACK = 1.5;
    public static final int KEEPER_EVERY = 4;
    public static final double KEEPER_RADIUS = 5;
    public static final float KEEPER_HEAL = 2f;

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
    static final Map<Job, String> SECOND_BY_JOB = Map.of(Job.WARRIOR, WARDEN, Job.SMITH, ASH, Job.MAGE, EYELID, Job.ARCHER, MIST, Job.ASSASSIN, MOTH,
            Job.SHAMAN, KEEPER);

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

    public static String secondForJob(Job job) {
        return job == null ? WARDEN : SECOND_BY_JOB.getOrDefault(job, WARDEN);
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
        rollBox(p, BOX_CHANCE);
    }

    // 雲上の箱を chance で一つ (中身は開けるときに神器の武器・防具・装飾から引く)
    public static boolean rollBox(ServerPlayer p, float chance) {
        if (p.getRandom().nextFloat() >= chance) {
            return false;
        }
        Item box = jp.revati.skyabyss.loot.box.LootBoxSetup.item(jp.revati.skyabyss.loot.box.BoxKind.byId(BOX));
        if (box == null) {
            return false;
        }
        Quests.give(p, new ItemStack(box));
        Notice.gain(p, "message.skyabyss.divine.box");
        return true;
    }

    public static boolean given(ServerPlayer p) {
        return p.getPersistentData().getBoolean(GIVEN);
    }

    // ---------------------------------------------------------------- 職を替えた: 持っている神器を替えた職のものへ (強化・特性はそのまま。一本目は一本目へ、二本目は二本目へ)
    public static int swap(ServerPlayer p, Job job) {
        Item first = item(forJob(job));
        Item second = item(secondForJob(job));
        Item to = first;
        int n = 0;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            to = isRelic(s) && SECONDS.contains(BuiltInRegistries.ITEM.getKey(s.getItem()).toString()) ? second : first;
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
