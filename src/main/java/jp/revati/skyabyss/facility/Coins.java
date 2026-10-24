package jp.revati.skyabyss.facility;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.Quests;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

// 鐘銭の出入り (docs/design/35 の 4 章)。銭は持ち物の品 (旅嚢には入らない)。
//   手に入る: 言伝ての板の頼み (日替わり・週替わり。章が進むほど多い)、町のクエスト (報酬の品に skyabyss:bell_coin を書く)、
//            敵の落とし物 (プレイヤーが倒した敵。章が進むほど落としやすく、多い)、店への売り (ShopService)
//   使う: 店 (ShopService)、鍛冶師の修理 (FacilityActions)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Coins {
    private Coins() {
    }

    // 人の章 (止めた心臓の数 + 1。1〜5)
    public static int chapter(ServerPlayer player) {
        if (player instanceof FakePlayer) {
            return 1;
        }
        return Mth.clamp(Progression.stage(player) + 1, 1, 5);
    }

    // 袋 (旅嚢) と持ち物の合計
    public static int count(ServerPlayer player) {
        return jp.revati.skyabyss.pouch.Pouch.countAll(player, FacilitySetup.BELL_COIN.get());
    }

    // 足りなければ何も取らずに false。袋から先に、足りない分を持ち物から
    public static boolean take(ServerPlayer player, int n) {
        if (n <= 0) {
            return true;
        }
        if (count(player) < n) {
            return false;
        }
        jp.revati.skyabyss.pouch.Pouch.takeAny(player, FacilitySetup.BELL_COIN.get(), n);
        return true;
    }

    // 渡す (入りきらなければ足もとに落とす)
    public static void give(ServerPlayer player, int n) {
        int left = n;
        while (left > 0) {
            int k = Math.min(left, 99);
            Quests.give(player, new ItemStack(FacilitySetup.BELL_COIN.get(), k));
            left -= k;
        }
    }

    // ---------------------------------------------------------------- 頼みの報酬
    // 言伝ての板の頼み (QuestDef.board) を終えて報酬を渡すとき (Quests.finish から)。報酬の品に銭が書いてあれば足さない
    public static int errandReward(QuestDef def, int chapter) {
        if (!def.board()) {
            return 0;
        }
        for (QuestDef.ItemReward r : def.reward().items()) {
            if (r.item().equals(SkyAbyss.MODID + ":bell_coin")) {
                return 0;
            }
        }
        return def.repeat().equals("weekly") ? 10 + 4 * chapter : 3 + chapter;
    }

    public static void onQuestPaid(ServerPlayer player, QuestDef def) {
        int n = errandReward(def, chapter(player));
        if (n > 0) {
            give(player, n);
            Notice.gain(player, "message.skyabyss.facility.coins_gained", n);
        }
    }

    // ---------------------------------------------------------------- 敵の落とし物
    // 落とす割合 (一章 6% から章ごとに +3%、五章 18%)
    public static float dropChance(int chapter) {
        return 0.03f + 0.03f * Mth.clamp(chapter, 1, 5);
    }

    // 落とす枚数 (1〜章)
    public static int dropCount(int chapter, RandomSource random) {
        return 1 + random.nextInt(Mth.clamp(chapter, 1, 5));
    }

    @SubscribeEvent
    static void onDrops(LivingDropsEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead instanceof Enemy) || !(event.getSource().getEntity() instanceof ServerPlayer killer) || killer instanceof FakePlayer
                || dead.level().isClientSide) {
            return;
        }
        int ch = chapter(killer);
        RandomSource random = dead.getRandom();
        if (random.nextFloat() >= dropChance(ch)) {
            return;
        }
        ItemEntity drop = new ItemEntity(dead.level(), dead.getX(), dead.getY() + 0.3, dead.getZ(),
                new ItemStack(FacilitySetup.BELL_COIN.get(), dropCount(ch, random)));
        drop.setDefaultPickUpDelay();
        event.getDrops().add(drop);
    }
}
