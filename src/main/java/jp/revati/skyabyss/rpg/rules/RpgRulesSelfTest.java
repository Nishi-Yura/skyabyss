package jp.revati.skyabyss.rpg.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner.Step;
import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

// 完全な RPG の決まりの自己テスト (docs/design/40 の 2 章)。段の名前は "rpg rules"。
//  - ゲームルールが入っていて、倒れたときの型の既定が keep
//  - 採集: 丸太は素手では採れず斧で採れて壊れない・すぐ二度目は採れない・鉄鉱石は素手・斧で採れず石のつるはしで採れる・作った物 (板) は採れない
//  - 空のバケツで水源を見て使うと水入りになり、水源は残る
//  - 偽の人はアドベンチャーへ移さない
public final class RpgRulesSelfTest {
    private RpgRulesSelfTest() {
    }

    // 世界の箱は人ごと (loot/SharedChests): 一人目が受け取ると箱は空、二度目は待ち、別の人は受け取れる、待ちが明けるとまた。空の箱はふつうの箱
    static void chests(net.minecraft.server.level.ServerLevel level, FakePlayer p, BlockPos at, List<String> f) {
        level.setBlockAndUpdate(at, Blocks.CHEST.defaultBlockState());
        if (!(level.getBlockEntity(at) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity box)) {
            f.add("chest: no block entity");
            return;
        }
        box.setItem(3, new ItemStack(Items.TORCH, 5));
        var data = jp.revati.skyabyss.loot.SharedChests.get(level.getServer());
        if (!jp.revati.skyabyss.loot.SharedChests.open(level, p, level.getBlockState(at), at, box) || p.getInventory().countItem(Items.TORCH) != 5 || !box.isEmpty()) {
            f.add("chest first take: torches " + p.getInventory().countItem(Items.TORCH) + ", box empty " + box.isEmpty());
        }
        jp.revati.skyabyss.loot.SharedChests.open(level, p, level.getBlockState(at), at, box);
        if (p.getInventory().countItem(Items.TORCH) != 5) {
            f.add("chest second take inside the wait gave " + p.getInventory().countItem(Items.TORCH));
        }
        FakePlayer q = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-0000000c0b02"), "chest_b"));
        q.getInventory().clearContent();
        jp.revati.skyabyss.loot.SharedChests.open(level, q, level.getBlockState(at), at, box);
        if (q.getInventory().countItem(Items.TORCH) != 5) {
            f.add("chest: a second person got " + q.getInventory().countItem(Items.TORCH));
        }
        q.getInventory().clearContent();
        data.forget(level, at, p.getUUID());
        jp.revati.skyabyss.loot.SharedChests.open(level, p, level.getBlockState(at), at, box);
        if (p.getInventory().countItem(Items.TORCH) != 10) {
            f.add("chest after the wait: " + p.getInventory().countItem(Items.TORCH));
        }
        BlockPos plain = at.east(2);
        level.setBlockAndUpdate(plain, Blocks.BARREL.defaultBlockState());
        if (level.getBlockEntity(plain) instanceof net.minecraft.world.Container c2
                && jp.revati.skyabyss.loot.SharedChests.open(level, p, level.getBlockState(plain), plain, c2)) {
            f.add("an empty barrel was taken over");
        }
    }

    public static List<Step> steps() {
        return List.of(Step.once("rpg rules", RpgRulesSelfTest::run));
    }

    private static BlockPos site(MinecraftServer server) {
        BlockPos spawn = server.overworld().getSharedSpawnPos();
        // 都の外へ (都の中では採れない)
        for (int i = 1; i < 40; i++) {
            BlockPos p = new BlockPos(spawn.getX() - 300 * i, 200, spawn.getZ() - 300 * i);
            if (Gathering.allowedHere(server.overworld(), p)) {
                return p;
            }
        }
        return null;
    }

    private static boolean click(FakePlayer p, BlockPos pos) {
        PlayerInteractEvent.RightClickBlock e = new PlayerInteractEvent.RightClickBlock(p, InteractionHand.MAIN_HAND, pos,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        NeoForge.EVENT_BUS.post(e);
        return e.isCanceled();
    }

    private static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        BlockPos c = site(server);
        try {
            if (RpgRules.ADVENTURE == null || RpgRules.NO_WEAR == null) {
                f.add("rpg game rules were not registered");
            }
            // 巨神の背の試練の部屋 (前の 8 枠 x 全段) が、どの背の次元でも世界の縁の内にある (縁の外だと入った瞬間に倒れる。2026-10-27)
            for (ServerLevel back : server.getAllLevels()) {
                var dim = back.dimension();
                if (!dim.location().getNamespace().equals(SkyAbyss.MODID) || !dim.location().getPath().endsWith("_back")) {
                    continue;
                }
                for (int slot = 1; slot <= 8; slot++) {
                    for (int step = 1; step <= 5; step++) {
                        BlockPos o = jp.revati.skyabyss.trial.raid.RaidRun.originOf(slot, step);
                        if (!back.getWorldBorder().isWithinBounds(o.offset(-60, 0, -60)) || !back.getWorldBorder().isWithinBounds(o.offset(60, 0, 60))) {
                            f.add("trial room " + slot + "/" + step + " at " + o.toShortString() + " lies outside the world border of " + dim.location());
                            break;
                        }
                    }
                }
            }
            if (!server.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY)) {
                f.add("keepInventory should be on (items are kept on death)");
            }
            // 一度きりの印は倒れても残る (はじめの持ち物を二度もらえた・渡し船で渡ったのが消えた不具合)
            if (!jp.revati.skyabyss.util.LifeKeep.KEYS.contains(RpgRules.KIT_TAG)
                    || !jp.revati.skyabyss.util.LifeKeep.KEYS.contains(jp.revati.skyabyss.world.continent.ContinentOutskirts.FERRIED)) {
                f.add("the starter-kit / ferried flags are not kept across death");
            }
            FakePlayer dead = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "life_dead"));
            FakePlayer fresh = new FakePlayer(level, new GameProfile(dead.getUUID(), "life_dead"));
            dead.getPersistentData().putBoolean(RpgRules.KIT_TAG, true);
            dead.getPersistentData().putBoolean(jp.revati.skyabyss.world.continent.ContinentOutskirts.FERRIED, true);
            dead.getPersistentData().putLong("skyabyss_some_cooldown", 5L);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone(fresh, dead, true));
            if (!fresh.getPersistentData().getBoolean(RpgRules.KIT_TAG) || !fresh.getPersistentData().getBoolean(jp.revati.skyabyss.world.continent.ContinentOutskirts.FERRIED)
                    || fresh.getPersistentData().contains("skyabyss_some_cooldown")) {
                f.add("death clone: kit " + fresh.getPersistentData().getBoolean(RpgRules.KIT_TAG) + " ferried "
                        + fresh.getPersistentData().getBoolean(jp.revati.skyabyss.world.continent.ContinentOutskirts.FERRIED) + " (cooldowns must not carry over)");
            }
            // 敵を倒しても経験の玉を出さない (バニラの dropExperience はこの知らせが止められると 0 を出す)。人の分は止めない (墓・預かりの決まりが決める)
            var foeType = net.minecraft.world.entity.EntityType.byString(SkyAbyss.MODID + ":moss_crawler").orElse(null);
            net.minecraft.world.entity.Entity foe = foeType == null ? null : foeType.create(level);
            if (!(foe instanceof net.minecraft.world.entity.LivingEntity living)) {
                f.add("cannot make a moss crawler to test experience drops");
            } else {
                var drop = new net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent(living, dead, 5);
                if (!net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(drop).isCanceled()) {
                    f.add("a slain foe still drops experience orbs (" + drop.getDroppedExperience() + ")");
                }
                foe.discard();
            }
            FakePlayer xpPlayer = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "xp_drop"));
            if (net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent(xpPlayer, null, 5)).isCanceled()) {
                f.add("a player's own experience drop was cancelled by the foe rule");
            }
            if (c == null) {
                f.add("no place outside the capital to test gathering");
                return;
            }
            level.getChunk(c);
            Gathering.clearAll();
            FakePlayer p = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-0000000c0b01"), "gather_a"));
            p.setPos(c.getX() + 0.5, c.getY() + 1, c.getZ() - 1.5);
            p.getInventory().clearContent();
            p.gameMode.changeGameModeForPlayer(GameType.ADVENTURE);
            RpgRules.enforce(p);
            if (p.gameMode.getGameModeForPlayer() != GameType.ADVENTURE) {
                f.add("the fake player's mode changed to " + p.gameMode.getGameModeForPlayer());
            }
            p.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            RpgRules.enforce(p);
            if (p.gameMode.getGameModeForPlayer() != GameType.SURVIVAL) {
                f.add("enforce moved a fake player to " + p.gameMode.getGameModeForPlayer());
            }
            p.gameMode.changeGameModeForPlayer(GameType.ADVENTURE);

            BlockPos log = c;
            BlockPos ore = c.east(2);
            BlockPos plank = c.west(2);
            BlockPos water = c.south(3);
            level.setBlockAndUpdate(log, Blocks.OAK_LOG.defaultBlockState());
            level.setBlockAndUpdate(ore, Blocks.IRON_ORE.defaultBlockState());
            level.setBlockAndUpdate(plank, Blocks.OAK_PLANKS.defaultBlockState());
            level.setBlockAndUpdate(water.below(), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(water, Blocks.WATER.defaultBlockState());

            // 丸太: 素手では採れない。斧なら採れる・壊れない
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            click(p, log);
            if (p.getInventory().countItem(Items.OAK_LOG) != 0) {
                f.add("a log was gathered by hand");
            }
            // 丸太は斧でも採れない (2026-11-08: 使い道のない品は採らせない)
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE_AXE));
            if (click(p, log) || p.getInventory().countItem(Items.OAK_LOG) != 0) {
                f.add("a log was still gatherable with an axe");
            }
            // 鉄鉱石: 道具があっても採れない (2026-11-12 に鉱石の採集をやめた)
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE_PICKAXE));
            click(p, ore);
            if (p.getInventory().countItem(jp.revati.skyabyss.item.outfit.MaterialGear.IRON_LUMP.get()) != 0 || !level.getBlockState(ore).is(Blocks.IRON_ORE)) {
                f.add("iron ore was still gatherable");
            }
            // 実った麦は素手で採れ、すぐ二度目は採れない
            BlockPos crop = c.north(2);
            level.setBlockAndUpdate(crop.below(), Blocks.FARMLAND.defaultBlockState());
            level.setBlockAndUpdate(crop, ((net.minecraft.world.level.block.CropBlock) Blocks.WHEAT).getStateForAge(7));
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            click(p, crop);
            int sheaves = p.getInventory().countItem(jp.revati.skyabyss.item.outfit.MaterialGear.WHEAT_SHEAF.get());
            if (sheaves < 1) {
                f.add("ripe wheat was not gathered by hand");
            }
            click(p, crop);
            if (p.getInventory().countItem(jp.revati.skyabyss.item.outfit.MaterialGear.WHEAT_SHEAF.get()) != sheaves) {
                f.add("the crop regrow wait did not hold");
            }
            // 作った物は採れない
            if (click(p, plank) || p.getInventory().countItem(Items.OAK_PLANKS) != 0) {
                f.add("planks were gatherable");
            }
            // 水を汲む
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
            p.setPos(water.getX() + 0.5, water.getY() + 1, water.getZ() + 0.5);
            p.setXRot(90f);
            p.setYRot(0f);
            PlayerInteractEvent.RightClickItem use = new PlayerInteractEvent.RightClickItem(p, InteractionHand.MAIN_HAND);
            NeoForge.EVENT_BUS.post(use);
            if (!use.isCanceled() || p.getInventory().countItem(Items.WATER_BUCKET) != 1 || p.getInventory().countItem(Items.BUCKET) != 0
                    || !level.getFluidState(water).isSource()) {
                f.add("bucket fill: cancelled " + use.isCanceled() + ", water buckets " + p.getInventory().countItem(Items.WATER_BUCKET)
                        + ", empty " + p.getInventory().countItem(Items.BUCKET) + ", source kept " + level.getFluidState(water).isSource());
            }
            p.getInventory().clearContent();
            chests(level, p, c.north(3), f);
            p.getInventory().clearContent();
            p.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST rpg rules crashed", e);
            f.add("crashed: " + e);
        } finally {
            Gathering.clearAll();
            if (c != null) {
                for (BlockPos q : List.of(c, c.east(2), c.west(2), c.south(3), c.south(3).below(), c.north(3), c.north(3).east(2))) {
                    level.setBlockAndUpdate(q, Blocks.AIR.defaultBlockState());
                }
            }
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST rpg rules PASSED (shared chests: each person takes a copy with a personal 20-min wait, empty chests stay plain; rules registered, keep on death by default, no experience orbs from slain foes, crop gathering by hand with regrow, no logs or ores, no gathering of crafted blocks, bucket fill keeps the source, fake players left alone)");
        } else {
            for (String s : f) {
                SkyAbyss.LOGGER.error("SELFTEST rpg rules FAILED: {}", s);
            }
        }
    }
}
