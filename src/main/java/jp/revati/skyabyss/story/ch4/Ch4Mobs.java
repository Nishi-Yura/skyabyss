package jp.revati.skyabyss.story.ch4;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.guide.ChapterMobSpawner;
import jp.revati.skyabyss.story.Chapters;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

// 四章の地上の敵の差し込み口と湧かせ方 (docs/design/21 の 6.1、P20)。
//   熾り蟲 (cinder_mite)     ひとり歩き (群れ 3〜5 匹。CinderMite が仲間を呼ぶ)。昼夜。消えた炉の坑の入口の間 (湧き台)
//   灰被りの屑 (ash_husk)     ひとり歩き (夜)。坑の鍛えの跡 (湧き台)
//   灰ひきずり (ash_dragger)  ひとり歩き (昼。敵意なし)。人に気づくと丸まって、煤の窯の跡へ転がって逃げる
//   古い灰被り (elder_thrall) 湧き台だけ。坑の奥の間 (名づき)
// 落とし物: 熾り蟲 煤鉄 20%、屑 灰麻 40%・煤鉄 15%、灰ひきずり 灰麻 50%・煤鉄 30%、古い灰被り 灰の護り糸 1・煤鉄 2〜3 (倒した人へ)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Ch4Mobs {
    public static final String CINDER_MITE = "skyabyss:cinder_mite";
    public static final String ASH_HUSK = "skyabyss:ash_husk";
    public static final String ASH_DRAGGER = "skyabyss:ash_dragger";
    public static final String ELDER_THRALL = "skyabyss:elder_thrall";
    // 煤の砥石で研いだ刃が効いている時刻 (人の永続データ。灰の殻への傷 1.5 倍)
    public static final String SOOT_WHETTED_KEY = "skyabyss_soot_whetted_until";
    public static final float SOOT_SHELL_MULT = 1.5f;

    public static final double MITE_THIN = 0.25;
    public static final double HUSK_THIN = 0.2;
    public static final double DRAGGER_THIN = 0.12;

    private Ch4Mobs() {
    }

    @SubscribeEvent
    static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(Ch4Mobs::register);
    }

    public static void register() {
        ChapterMobSpawner.register(new ChapterMobSpawner.Wanderer("ch4_cinder_mite", Chapters.EMBER, CINDER_MITE, 5, ChapterMobSpawner.Time.ANY,
                thin(Ch4Mobs::land, MITE_THIN)));
        ChapterMobSpawner.register(new ChapterMobSpawner.Wanderer("ch4_ash_husk", Chapters.EMBER, ASH_HUSK, 3, ChapterMobSpawner.Time.NIGHT,
                thin(Ch4Mobs::land, HUSK_THIN)));
        ChapterMobSpawner.register(new ChapterMobSpawner.Wanderer("ch4_ash_dragger", Chapters.EMBER, ASH_DRAGGER, 2, ChapterMobSpawner.Time.DAY,
                thin(Ch4Mobs::land, DRAGGER_THIN)));
    }

    // 陸 (海・川でない)
    public static boolean land(Holder<Biome> b) {
        return !b.is(BiomeTags.IS_OCEAN) && !b.is(BiomeTags.IS_RIVER);
    }

    private static Predicate<Holder<Biome>> thin(Predicate<Holder<Biome>> biome, double chance) {
        return b -> biome.test(b) && ThreadLocalRandom.current().nextDouble() < chance;
    }

    // 煤の砥石の研ぎが効いているか
    public static boolean sootWhetted(ServerPlayer p) {
        return p.getPersistentData().getLong(SOOT_WHETTED_KEY) > p.level().getGameTime();
    }

    // 殻への傷の倍率 (煤の砥石で研いだ人の攻撃は 1.5 倍)
    public static float shellBonus(DamageSource source) {
        return source.getEntity() instanceof ServerPlayer p && sootWhetted(p) ? SOOT_SHELL_MULT : 1f;
    }

    // 落とし物: 倒した人 (いなければ誰でも拾える) へ。chance の確率で count 個
    public static void drop(ServerLevel level, LivingEntity mob, DamageSource source, Item item, double chance, int min, int max) {
        if (level.random.nextDouble() >= chance) {
            return;
        }
        int n = min + (max > min ? level.random.nextInt(max - min + 1) : 0);
        ItemEntity e = new ItemEntity(level, mob.getX(), mob.getY() + 0.4, mob.getZ(), new ItemStack(item, n));
        e.setDefaultPickUpDelay();
        if (source.getEntity() instanceof ServerPlayer p) {
            e.setTarget(p.getUUID());
        }
        level.addFreshEntity(e);
    }
}
