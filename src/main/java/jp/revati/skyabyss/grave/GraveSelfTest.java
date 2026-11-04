package jp.revati.skyabyss.grave;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

// 倒れたときの決まり (docs/design/40 の 2 章「落とさない」) と前の版の墓 (2026-11-05 の ct_grave = cut)。SKYABYSS_SELFTEST_ONLY="grave"
//   keepInventory が入っていれば何もしない / 切られていても持ち物と経験値を預かって新しい体へ戻す・墓は作らない
//   前の版の墓: 持ち主が開けると中身と経験値が戻り、空になると控えもブロックも消える。ほかの人は開けられない
public final class GraveSelfTest {
    private static int loadWait;

    private GraveSelfTest() {
    }

    static BlockPos base(MinecraftServer server) {
        BlockPos spawn = server.overworld().getSharedSpawnPos();
        return new BlockPos(spawn.getX() + 140, 120, spawn.getZ() - 140);
    }

    public static boolean loaded(MinecraftServer server) {
        BlockPos c = base(server);
        if (loadWait++ == 0) {
            server.overworld().setChunkForced(c.getX() >> 4, c.getZ() >> 4, true);
        }
        return server.overworld().isPositionEntityTicking(c) || loadWait > 400;
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        GameRules rules = server.getGameRules();
        boolean keepInv = rules.getBoolean(GameRules.RULE_KEEPINVENTORY);
        BlockPos at = base(server);
        GraveData data = GraveData.get(server);
        GraveData.Grave legacy = null;
        try {
            // ---- keepInventory が入っていれば何もしない
            rules.getRule(GameRules.RULE_KEEPINVENTORY).set(true, server);
            FakePlayer a = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "grave_keepinv"));
            a.getInventory().add(new ItemStack(Items.EMERALD, 4));
            check(f, DeathEvents.handleDeath(a) == null && a.getInventory().countItem(Items.EMERALD) == 4, "keepInventory: death handling should do nothing");

            // ---- 切られていても全部残る (墓は作らない)
            rules.getRule(GameRules.RULE_KEEPINVENTORY).set(false, server);
            FakePlayer dead = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "grave_dead"));
            dead.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            dead.getInventory().add(new ItemStack(Items.EMERALD, 7));
            dead.getInventory().armor.set(0, new ItemStack(Items.IRON_BOOTS));
            dead.experienceLevel = 12;
            DeathEvents.Outcome out = DeathEvents.handleDeath(dead);
            check(f, out != null && out.keptStacks() == 2 && dead.getInventory().isEmpty(), "with keepInventory off the inventory should be taken into keeping: " + out);
            check(f, data.owned(dead.getUUID()).isEmpty(), "a grave was made on death");
            FakePlayer fresh = new FakePlayer(level, new GameProfile(dead.getUUID(), "grave_dead"));
            NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(fresh, dead, true));
            check(f, fresh.getInventory().countItem(Items.EMERALD) == 7 && fresh.getInventory().armor.get(0).is(Items.IRON_BOOTS) && fresh.experienceLevel == 12,
                    "the new body should get everything back (items, armor, level)");
            check(f, !fresh.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).contains(DeathEvents.KEEP_TAG), "the keeping was not cleared after the respawn");

            // ---- 前の版の墓: 持ち主が開けると戻って消える。ほかの人は開けられない
            level.setBlock(at.below(), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(at, ModBlocks.GRAVE.get().defaultBlockState(), 3);
            FakePlayer owner = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "grave_owner"));
            FakePlayer other = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "grave_other"));
            legacy = new GraveData.Grave(UUID.randomUUID(), owner.getUUID(), "grave_owner", level.dimension(), at, 0L, 30,
                    new ArrayList<>(List.of(new ItemStack(Items.DIAMOND, 3), new ItemStack(Items.BREAD, 5))));
            data.add(legacy);
            Graves.interact(other, level, at);
            check(f, data.get(legacy.id) != null && other.getInventory().countItem(Items.DIAMOND) == 0, "someone else opened a legacy grave");
            Graves.interact(owner, level, at);
            check(f, owner.getInventory().countItem(Items.DIAMOND) == 3 && owner.getInventory().countItem(Items.BREAD) == 5 && owner.totalExperience >= 30,
                    "the owner should get the legacy grave's items and experience back");
            check(f, data.get(legacy.id) == null && !level.getBlockState(at).is(ModBlocks.GRAVE.get()), "an emptied legacy grave should vanish");
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST grave crashed", e);
            f.add("crashed: " + e);
        } finally {
            rules.getRule(GameRules.RULE_KEEPINVENTORY).set(keepInv, server);
            if (legacy != null && data.get(legacy.id) != null) {
                data.remove(legacy.id);
            }
            level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
            server.overworld().setChunkForced(at.getX() >> 4, at.getZ() >> 4, false);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST grave PASSED (keepInventory does nothing, keepInventory off keeps everything and makes no grave, legacy graves return to their owner only and vanish)");
        } else {
            SkyAbyss.LOGGER.error("SELFTEST grave FAILED: {}", f);
        }
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }
}
