package jp.revati.skyabyss.upgrade;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.LoreSetup;
import jp.revati.skyabyss.rpg.rules.NoPlaceables;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 正式版の前の直し (2026-11-02) の自己テスト。段の名前は "upgrade"。
//  - 世界のデータの版がサーバーの始まりで CURRENT になり、もう一度流しても段は流れない。人の版も同じ
//  - 置く品の見分け: 松明・額縁・舟は置く品、頼みの品 (ランタン)・売れる採集の品 (丸太)・種・食べ物・Mod の品は置く品でない。外すと持ち物から消える
//  - 地面の破れた頁は、拾える人のほうへ動き、遠ければ足もとへ移る (lore/PageHoming)
public final class UpgradeSelfTest {
    private UpgradeSelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        return List.of(SelfTestRunner.Step.once("upgrade", UpgradeSelfTest::run));
    }

    static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        // 版
        if (WorldUpgrade.version(server) != WorldUpgrade.CURRENT) {
            f.add("world data version " + WorldUpgrade.version(server) + " != " + WorldUpgrade.CURRENT);
        }
        if (WorldUpgrade.run(server) != 0) {
            f.add("world steps ran twice");
        }
        // 大陸の作り直し (ContinentRegen): この世界は今の版を書いてある (新しい世界・作り直したあと)。版のファイルの読み書きと、チャンクの有無の見分け
        if (ContinentRegen.continent(server) && ContinentRegen.savedVersion(ContinentRegen.root(server)) != ContinentRegen.VERSION) {
            f.add("continent version file " + ContinentRegen.savedVersion(ContinentRegen.root(server)) + " != " + ContinentRegen.VERSION);
        }
        try {
            java.nio.file.Path tmp = java.nio.file.Files.createTempDirectory("skyabyss_regen");
            if (ContinentRegen.savedVersion(tmp) != 0 || ContinentRegen.hasChunks(tmp)) {
                f.add("an empty world folder should be version 0 without chunks");
            }
            ContinentRegen.writeVersion(tmp, 7);
            java.nio.file.Files.createDirectories(tmp.resolve("region"));
            java.nio.file.Files.writeString(tmp.resolve("region/r.0.0.mca"), "x");
            if (ContinentRegen.savedVersion(tmp) != 7 || !ContinentRegen.hasChunks(tmp)) {
                f.add("the continent version file or the chunk check does not round-trip");
            }
            ContinentRegen.deleteTree(tmp);
        } catch (java.io.IOException e) {
            f.add("temp folder: " + e);
        }
        FakePlayer p = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-00000000a9e1"), "upgrade_a"));
        p.getPersistentData().remove(PlayerUpgrade.KEY);
        PlayerUpgrade.run(p);
        if (PlayerUpgrade.version(p) != PlayerUpgrade.CURRENT || PlayerUpgrade.run(p) != 0) {
            f.add("player data version " + PlayerUpgrade.version(p) + " != " + PlayerUpgrade.CURRENT + " or ran twice");
        }
        // 古い品を今の決まりへ (item/ItemRefresh): 振っていない野の装飾は振る、今の表にない特性はそろえ直す、二度目は何もしない
        net.minecraft.util.RandomSource rr = net.minecraft.util.RandomSource.create(9);
        ItemStack acc = null;
        ItemStack gear = null;
        for (net.minecraft.world.item.Item it : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            if (acc == null && it instanceof jp.revati.skyabyss.item.wild.WildAccessoryItem && !jp.revati.skyabyss.dungeon.DungeonGear.isTrinket(it)) {
                acc = new ItemStack(it);
            }
            if (gear == null && jp.revati.skyabyss.item.affix.AffixRules.eligible(new ItemStack(it))) {
                gear = new ItemStack(it);
            }
        }
        if (acc == null || !jp.revati.skyabyss.item.ItemRefresh.refresh(acc, rr) || !jp.revati.skyabyss.item.wild.AccessoryRolls.rolled(acc)
                || jp.revati.skyabyss.item.ItemRefresh.refresh(acc, rr)) {
            f.add("item refresh: an unrolled accessory was not rolled once");
        }
        if (gear != null) {
            gear.set(jp.revati.skyabyss.registry.ModDataComponents.AFFIXES.get(), List.of(new jp.revati.skyabyss.item.affix.Affix("no_such_affix", 1.0)));
            if (!jp.revati.skyabyss.item.ItemRefresh.refresh(gear, rr)
                    || !jp.revati.skyabyss.item.affix.AffixRules.valid(gear, jp.revati.skyabyss.item.affix.AffixRules.affixes(gear))
                    || jp.revati.skyabyss.item.ItemRefresh.refresh(gear, rr)) {
                f.add("item refresh: old affixes were not brought to the current rules: " + jp.revati.skyabyss.item.affix.AffixRules.affixes(gear));
            }
        } else {
            f.add("item refresh: no affix gear found");
        }
        // 置く品
        for (ItemStack s : List.of(new ItemStack(Items.TORCH), new ItemStack(Items.ITEM_FRAME), new ItemStack(Items.OAK_BOAT), new ItemStack(Items.WHITE_WOOL))) {
            if (!NoPlaceables.placeable(s)) {
                f.add("should be placeable: " + s.getItem());
            }
        }
        for (ItemStack s : List.of(new ItemStack(Items.LANTERN), new ItemStack(Items.OAK_LOG), new ItemStack(Items.WHEAT_SEEDS), new ItemStack(Items.BREAD),
                new ItemStack(Items.BUCKET), new ItemStack(Items.WATER_BUCKET), new ItemStack(LoreSetup.LORE_PAGE.get()),
                new ItemStack(jp.revati.skyabyss.item.outfit.ConsumableGear.TOWN_BREAD.get()))) {
            if (NoPlaceables.placeable(s)) {
                f.add("should not be placeable: " + s.getItem());
            }
        }
        p.getInventory().clearContent();
        p.getInventory().add(new ItemStack(Items.TORCH, 8));
        p.getInventory().add(new ItemStack(Items.LANTERN, 2));
        int removed = NoPlaceables.sweep(p);
        if (removed != 8 || p.getInventory().countItem(Items.TORCH) != 0 || p.getInventory().countItem(Items.LANTERN) != 2) {
            f.add("sweep removed " + removed + " (torches left " + p.getInventory().countItem(Items.TORCH) + ", lanterns " + p.getInventory().countItem(Items.LANTERN) + ")");
        }
        p.getInventory().clearContent();
        // 頁が寄る
        p.setPos(0.5, 200, 0.5);
        ItemEntity near = new ItemEntity(level, 4.5, 200, 0.5, new ItemStack(LoreSetup.LORE_PAGE.get()));
        jp.revati.skyabyss.lore.PageHoming.home(near, p);
        if (near.getDeltaMovement().x >= 0) {
            f.add("a near page does not move toward the player (" + near.getDeltaMovement() + ")");
        }
        ItemEntity far = new ItemEntity(level, 60.5, 200, 0.5, new ItemStack(LoreSetup.LORE_PAGE.get()));
        jp.revati.skyabyss.lore.PageHoming.home(far, p);
        if (far.distanceToSqr(p) > 4) {
            f.add("a far page is not moved to the player's feet (" + far.position() + ")");
        }
        // 早駆けの標の残り物 (OldRacePosts): 空の高い所に草の地面と標を建て、片付くか
        BlockPos base = level.getSharedSpawnPos().atY(260);
        for (int a = -3; a <= 3; a++) {
            for (int b = -3; b <= 3; b++) {
                level.setBlock(base.offset(a, -1, b), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            }
        }
        level.setBlock(base.offset(0, -1, 0), Blocks.POLISHED_ANDESITE.defaultBlockState(), 2);
        level.setBlock(base.offset(1, -1, 0), Blocks.COARSE_DIRT.defaultBlockState(), 2);
        level.setBlock(base.offset(1, -1, 1), Blocks.ANDESITE.defaultBlockState(), 2);
        level.setBlock(base, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState(), 2);
        level.setBlock(base.above(), Blocks.SPRUCE_FENCE.defaultBlockState(), 2);
        level.setBlock(base.above(4), Blocks.LANTERN.defaultBlockState(), 2);
        level.setBlock(base.above(3).east(), Blocks.RED_WALL_BANNER.defaultBlockState(), 2);
        jp.revati.skyabyss.opening.site.SiteData sites = jp.revati.skyabyss.opening.site.SiteData.get(level);
        sites.add(OldRacePosts.KIND, 0, base, 0).built = true;
        int swept = OldRacePosts.sweep(level);
        if (swept != 1 || !sites.entries(OldRacePosts.KIND).isEmpty()) {
            f.add("the old race post entry was not swept (" + swept + ")");
        }
        if (!level.getBlockState(base).isAir() || !level.getBlockState(base.above(4)).isAir() || !level.getBlockState(base.above(3).east()).isAir()) {
            f.add("the old race post blocks are still standing");
        }
        if (!level.getBlockState(base.below()).is(Blocks.GRASS_BLOCK) || !level.getBlockState(base.offset(1, -1, 1)).is(Blocks.GRASS_BLOCK)) {
            f.add("the old race post's stone pad was not turned back into the ground around it");
        }
        for (int a = -3; a <= 3; a++) {
            for (int b = -3; b <= 3; b++) {
                level.setBlock(base.offset(a, -1, b), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        // 作り直したあとの立ち位置 (ContinentRegen.settleTarget): 空の高い所に池 (石の床・ガラスの縁・3x3 の水) を作って
        BlockPos o = level.getSharedSpawnPos().offset(40, 0, 0).atY(230);
        for (int a = -3; a <= 3; a++) {
            for (int b = -3; b <= 3; b++) {
                level.setBlock(o.offset(a, 0, b), Blocks.STONE.defaultBlockState(), 2);
                int m = Math.max(Math.abs(a), Math.abs(b));
                level.setBlock(o.offset(a, 1, b), m <= 1 ? Blocks.WATER.defaultBlockState() : m == 2 ? Blocks.GLASS.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
            }
        }
        BlockPos overWater = ContinentRegen.settleTarget(level, o.offset(0, 20, 0));
        BlockPos inWater = ContinentRegen.settleTarget(level, o.offset(0, 1, 0));
        for (BlockPos t : java.util.Arrays.asList(overWater, inWater)) {
            if (t == null || t.equals(ContinentRegen.NO_LAND) || t.getY() != o.getY() + 2 || Math.max(Math.abs(t.getX() - o.getX()), Math.abs(t.getZ() - o.getZ())) != 2) {
                f.add("a player over or in the pool should move to its glass rim, got " + t);
            }
        }
        if (ContinentRegen.settleTarget(level, o.offset(2, 2, 0)) != null) {
            f.add("a player standing on the rim should stay");
        }
        BlockPos drop = ContinentRegen.settleTarget(level, o.offset(3, 15, 3));
        if (drop == null || !drop.equals(o.offset(3, 1, 3))) {
            f.add("a player high over dry ground should be put on it, got " + drop);
        }
        for (int a = -3; a <= 3; a++) {
            for (int b = -3; b <= 3; b++) {
                level.setBlock(o.offset(a, 1, b), Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(o.offset(a, 0, b), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        // NPC を立たせた時刻は作り直しで忘れる (二度目は 0)・大陸の版の印は倒れても残す
        jp.revati.skyabyss.npc.NpcWorldData.get(level).forgetAll();
        if (jp.revati.skyabyss.npc.NpcWorldData.get(level).forgetAll() != 0) {
            f.add("the npc spawn times were not forgotten");
        }
        if (!jp.revati.skyabyss.util.LifeKeep.KEYS.contains(ContinentRegen.PLAYER_KEY)) {
            f.add("the player's continent version is lost on death (not in LifeKeep)");
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST upgrade PASSED (world and player data versions, placeable items told apart and swept, pages home in, old race posts cleared, settle after regen, npc spawn times forgotten)");
        } else {
            f.forEach(s -> SkyAbyss.LOGGER.error("SELFTEST upgrade FAILED: {}", s));
        }
    }
}
