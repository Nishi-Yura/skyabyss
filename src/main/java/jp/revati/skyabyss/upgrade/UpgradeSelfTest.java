package jp.revati.skyabyss.upgrade;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.LoreSetup;
import jp.revati.skyabyss.rpg.rules.NoPlaceables;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST upgrade PASSED (world and player data versions, placeable items told apart and swept, pages home in)");
        } else {
            f.forEach(s -> SkyAbyss.LOGGER.error("SELFTEST upgrade FAILED: {}", s));
        }
    }
}
