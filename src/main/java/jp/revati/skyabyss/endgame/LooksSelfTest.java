package jp.revati.skyabyss.endgame;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.Coins;
import jp.revati.skyabyss.loot.TitanMemoryData;
import jp.revati.skyabyss.registry.ModDataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 見た目替え (Looks) の自己テスト。SKYABYSS_SELFTEST_ONLY="looks"
//   同じ種類だけ (杖は杖、大剣と長柄は混ぜない) / 図鑑にない形は選べない / 鐘銭を取って印を付ける / 戻す / 強さの品は替わらない
public final class LooksSelfTest {
    private LooksSelfTest() {
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    static Item item(String path) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, path));
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        FakePlayer p = FakePlayerFactory.get(server.overworld(), new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-0000000100c1"), "looks_test"));
        TitanMemoryData mem = TitanMemoryData.get(server);
        ItemStack before = p.getMainHandItem().copy();
        try {
            Item whaleStaff = item("whale_staff");
            Item tideStaff = item("tide_staff");
            Item tideBell = item("tide_bell_staff");
            Item greatsword = item("whalebone_greatsword");
            Item pike = item("captain_pike");
            check(f, Looks.sameKind(whaleStaff, tideStaff) && !Looks.sameKind(whaleStaff, tideBell) && !Looks.sameKind(greatsword, pike)
                    && !Looks.sameKind(whaleStaff, whaleStaff), "the same-kind rule is wrong");
            mem.record(p.getUUID(), "tide_shell").owned.remove(BuiltInRegistries.ITEM.getKey(tideStaff).toString());
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(whaleStaff));
            check(f, !Looks.choices(p, p.getMainHandItem()).contains(tideStaff), "a look not in the codex was offered");
            check(f, !Looks.set(p, BuiltInRegistries.ITEM.getKey(tideStaff).toString()), "a look not in the codex was set");
            mem.record(p.getUUID(), "tide_shell").owned.add(BuiltInRegistries.ITEM.getKey(tideStaff).toString());
            check(f, Looks.choices(p, p.getMainHandItem()).contains(tideStaff), "a codex look was not offered");
            Coins.give(p, Looks.PRICE);
            int coins = Coins.count(p);
            check(f, Looks.set(p, BuiltInRegistries.ITEM.getKey(tideStaff).toString()), "the look was not set");
            ItemStack held = p.getMainHandItem();
            check(f, held.getItem() == whaleStaff && Looks.look(held) == tideStaff && coins - Coins.count(p) == Looks.PRICE, "the look did not take coins or stick");
            Looks.clear(p);
            check(f, Looks.look(p.getMainHandItem()) == null && !p.getMainHandItem().has(ModDataComponents.LOOK.get()), "the look did not clear");
        } catch (RuntimeException ex) {
            f.add("looks test crashed: " + ex);
            SkyAbyss.LOGGER.error("looks test", ex);
        } finally {
            p.setItemInHand(InteractionHand.MAIN_HAND, before);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST looks PASSED (same kind only, codex only, price, mark, clear)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST looks FAILED: {}", m));
        }
    }
}
