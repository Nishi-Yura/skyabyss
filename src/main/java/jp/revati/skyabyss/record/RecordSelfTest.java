package jp.revati.skyabyss.record;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import io.netty.buffer.Unpooled;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.loot.TitanMemoryData;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.rpg.Growth;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

// 控え帳と大事な品の守りの自己テスト (SKYABYSS_SELFTEST=1 の専用サーバー、起動直後に一度。止めるのは TitanBackSelfTest)。
// 中身の符号化、在りかの数え方 (持ち物・袋・エンダーチェスト・預かり・背中の預かり)、変わった値の見つけ方、
// 落ちた品の守り (消えない・燃えない・奈落から戻る・控え)、投げ捨ての拒み、見てよい人の決まりを確かめる
public final class RecordSelfTest {
    private RecordSelfTest() {
    }

    // 呼ぶのは SelfTestRunner (SKYABYSS_SELFTEST=1 のとき、1 tick に 1 系統ずつ)
    public static void runSelfTest(MinecraftServer server) {
        List<String> failures = new ArrayList<>();
        try {
            run(server, failures);
        } catch (Exception e) {
            SkyAbyss.LOGGER.error("record selftest crashed", e);
            failures.add("crashed: " + e);
        }
        if (failures.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST record PASSED (snapshot codec, locations, diff, guarded drops, void rescue, toss, give without duplicate, holders, permissions)");
        } else {
            failures.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST record FAILED: {}", f));
        }
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    static void run(MinecraftServer server, List<String> f) {
        ServerLevel overworld = server.overworld();
        FakePlayer a = FakePlayerFactory.get(overworld, new GameProfile(UUID.fromString("5c1e0a2b-0000-4000-8000-00000000b001"), "[record_test_a]"));
        FakePlayer b = FakePlayerFactory.get(overworld, new GameProfile(UUID.fromString("5c1e0a2b-0000-4000-8000-00000000b002"), "[record_test_b]"));
        a.getInventory().clearContent();
        a.getEnderChestInventory().clearContent();
        TitanMemoryData memory = TitanMemoryData.get(server);
        memory.takePending(a.getUUID());

        // ---------------------------------------------------------------- 中身と符号化
        RecordSnapshot snap = RecordBuilder.build(a, true);
        check(f, snap.tabs().size() == RecordSnapshot.TAB_IDS.size(), "snapshot should have " + RecordSnapshot.TAB_IDS.size() + " tabs");
        for (String id : RecordSnapshot.TAB_IDS) {
            RecordSnapshot.Tab t = snap.tab(id);
            check(f, t != null && !t.rows().isEmpty(), "tab " + id + " is empty");
            if (t != null) {
                java.util.Set<String> keys = new java.util.HashSet<>();
                for (RecordSnapshot.Row r : t.rows()) {
                    check(f, keys.add(r.key()), "duplicate row key " + id + "/" + r.key());
                }
            }
        }
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        RecordSnapshot.STREAM_CODEC.encode(buf, snap);
        int bytes = buf.readableBytes();
        RecordSnapshot back = RecordSnapshot.STREAM_CODEC.decode(buf);
        buf.release();
        check(f, back.target().equals(snap.target()) && back.self() == snap.self(), "snapshot header did not survive the codec");
        check(f, RecordSnapshot.diff(snap, back).isEmpty(), "snapshot rows changed through the codec: " + RecordSnapshot.diff(snap, back).size());
        check(f, bytes < 64 * 1024, "snapshot is too large: " + bytes + " bytes");
        SkyAbyss.LOGGER.info("[selftest] record snapshot {} rows, {} bytes", snap.flat().size(), bytes);

        // 見てよい人: 本人は見られる。権限のない人はほかの人を見られない
        check(f, RecordService.mayView(a, a), "a player should see their own ledger");
        check(f, !RecordService.mayView(b, a), "a non-operator could view someone else's ledger");

        // ---------------------------------------------------------------- 在りか
        var fragment = ModItems.SKY_KEY_FRAGMENT.get();
        check(f, Keepsakes.locate(a, fragment).total() == 0 && Keepsakes.fragmentMissing(a), "an empty player should have no fragment");
        a.getInventory().add(new ItemStack(fragment));
        a.getEnderChestInventory().setItem(3, new ItemStack(fragment, 2));
        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(fragment, 3))));
        a.getInventory().add(box);
        memory.pending(a.getUUID()).items.add(new ItemStack(fragment, 4));
        CompoundTag persisted = a.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag kept = new CompoundTag();
        ListTag inv = new ListTag();
        inv.add(new ItemStack(fragment, 5).save(server.registryAccess()));
        kept.put("Inventory", inv);
        persisted.put(Keepsakes.KEPT_TAG, kept);
        a.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        Keepsakes.Where w = Keepsakes.locate(a, fragment);
        check(f, w.inventory() == 1 && w.ender() == 2 && w.bags() == 3 && w.pending() == 4 && w.kept() == 5,
                "locate counted " + w);
        check(f, !Keepsakes.fragmentMissing(a), "fragment should not be missing");
        check(f, Keepsakes.keptStacks(a) == 1, "kept stacks should be 1");
        RecordSnapshot.Row row = RecordBuilder.build(a, true).tab(RecordSnapshot.KEEPSAKES).row("fragment");
        check(f, row != null && row.mark() == RecordSnapshot.MARK_SAFE, "fragment row should be safe when it is at hand");
        RecordSnapshot.Row pendingRow = RecordBuilder.build(a, true).tab(RecordSnapshot.KEEPSAKES).row("pending");
        check(f, pendingRow != null && pendingRow.action() == RecordSnapshot.ACTION_CLAIM, "own pending row should offer the claim button");
        RecordSnapshot.Row inspected = RecordBuilder.build(a, false).tab(RecordSnapshot.KEEPSAKES).row("pending");
        check(f, inspected != null && inspected.action() == RecordSnapshot.ACTION_NONE, "an inspected ledger must not offer the claim button");
        persisted.remove(Keepsakes.KEPT_TAG);
        memory.takePending(a.getUUID());
        a.getInventory().clearContent();
        a.getEnderChestInventory().clearContent();

        // ---------------------------------------------------------------- 変わった値
        RecordSnapshot before = RecordBuilder.build(a, true);
        int level = Growth.get(a).level;
        Growth.setLevel(a, level >= 12 ? 3 : 12);
        a.getInventory().add(new ItemStack(fragment));
        List<RecordSnapshot.Change> changes = RecordSnapshot.diff(before, RecordBuilder.build(a, true));
        check(f, changes.stream().anyMatch(c -> c.key().equals("growth/level")), "level change was not found");
        check(f, changes.stream().anyMatch(c -> c.key().equals("keepsakes/fragment")), "fragment change was not found");
        check(f, RecordSnapshot.isVolatile("growth/mana") && !RecordSnapshot.isVolatile("growth/level"), "volatile rows are wrong");
        Growth.setLevel(a, level);
        a.getInventory().clearContent();

        // ---------------------------------------------------------------- 落ちた品の守り
        BlockPos spawn = overworld.getSharedSpawnPos();
        ItemEntity dropped = new ItemEntity(overworld, spawn.getX() + 0.5, overworld.getMaxBuildHeight() - 2, spawn.getZ() + 0.5, new ItemStack(fragment));
        dropped.getPersistentData().putUUID(Keepsakes.OWNER_TAG, a.getUUID());
        overworld.addFreshEntity(dropped);
        check(f, dropped.isInvulnerable() && dropped.getAge() == -32768, "a guarded drop should be invulnerable and never despawn");
        check(f, !dropped.hurt(overworld.damageSources().lava(), 100) && !dropped.hurt(overworld.damageSources().explosion(null, null), 100)
                && !dropped.hurt(overworld.damageSources().cactus(), 100), "a guarded drop took damage");
        check(f, dropped.isAlive(), "a guarded drop died");
        Map<UUID, DropLedger.Drop> ledger = DropLedger.get(server).all();
        check(f, ledger.containsKey(dropped.getUUID()), "the drop was not written in the ledger");
        Keepsakes.Where ground = Keepsakes.locate(a, fragment);
        check(f, ground.groundCount() == 1 && ground.ground().get(0).loaded(), "the ledger should show the drop on the ground: " + ground);
        // 奈落へ
        dropped.setPos(dropped.getX(), overworld.getMinBuildHeight() - 20, dropped.getZ());
        NeoForge.EVENT_BUS.post(new EntityTickEvent.Pre(dropped));
        check(f, dropped.isRemoved(), "a guarded drop in the void was not rescued");
        check(f, !DropLedger.get(server).all().containsKey(dropped.getUUID()), "a rescued drop stayed in the ledger");
        check(f, memory.hasPending(a.getUUID()) && memory.pending(a.getUUID()).items.stream().anyMatch(s -> s.is(fragment)),
                "an absent owner's rescued fragment should be held");
        memory.takePending(a.getUUID());
        // ふつうの品は守らない
        ItemEntity plain = new ItemEntity(overworld, spawn.getX() + 0.5, overworld.getMaxBuildHeight() - 2, spawn.getZ() + 0.5, new ItemStack(Items.DIRT));
        overworld.addFreshEntity(plain);
        check(f, !plain.isInvulnerable() && !DropLedger.get(server).all().containsKey(plain.getUUID()), "a plain item was guarded");
        plain.discard();

        // ---------------------------------------------------------------- 投げ捨て
        a.getInventory().clearContent();
        ItemEntity toss = new ItemEntity(overworld, a.getX(), a.getY(), a.getZ(), new ItemStack(fragment));
        toss.setThrower(a);
        ItemTossEvent tossEvent = new ItemTossEvent(toss, a);
        NeoForge.EVENT_BUS.post(tossEvent);
        check(f, tossEvent.isCanceled() && Keepsakes.locate(a, fragment).inventory() == 1, "tossing a fragment should be refused and returned");
        // /give は持ち物に入れたあと、見せかけの品を投げた人なしで落とす。拒んで戻すと 1 つ増えてしまう
        a.getInventory().clearContent();
        a.getInventory().add(new ItemStack(fragment));
        ItemEntity giveShow = new ItemEntity(overworld, a.getX(), a.getY(), a.getZ(), new ItemStack(fragment));
        ItemTossEvent giveEvent = new ItemTossEvent(giveShow, a);
        NeoForge.EVENT_BUS.post(giveEvent);
        check(f, !giveEvent.isCanceled() && Keepsakes.locate(a, fragment).inventory() == 1,
                "the /give display drop should pass untouched (no duplicate), got " + Keepsakes.locate(a, fragment).inventory());
        ItemEntity tossCore = new ItemEntity(overworld, a.getX(), a.getY(), a.getZ(), new ItemStack(ModItems.STONE_WHALE_CORE.get()));
        ItemTossEvent coreEvent = new ItemTossEvent(tossCore, a);
        NeoForge.EVENT_BUS.post(coreEvent);
        check(f, !coreEvent.isCanceled(), "tossing a core should be allowed");
        a.getInventory().clearContent();

        // ---------------------------------------------------------------- ほかの仕組みの枠 (装飾品のスロット用)
        Keepsakes.registerHolder("accessory", (player, item) -> player == a && item == fragment ? 1 : 0);
        try {
            Keepsakes.Where held = Keepsakes.locate(a, fragment);
            check(f, held.holders().getOrDefault("accessory", 0) == 1 && held.safe() == 1 && held.total() == 1,
                    "a registered holder should count as at hand, got " + held);
            check(f, Keepsakes.locate(b, fragment).holders().isEmpty(), "a holder with nothing should not be listed");
        } finally {
            Keepsakes.unregisterHolder("accessory");
        }
        memory.takePending(a.getUUID());
    }
}
