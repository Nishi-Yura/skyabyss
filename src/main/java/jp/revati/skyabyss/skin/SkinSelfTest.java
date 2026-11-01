package jp.revati.skyabyss.skin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import io.netty.buffer.Unpooled;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.Coins;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.connection.ConnectionType;

// スキン (Skins) の自己テスト。SKYABYSS_SELFTEST_ONLY="skins"
//   仕立て屋を開かずには買えない / 鐘銭が足りないと買えない / 買うと鐘銭が減って持つ / 持っていれば二度は買わない /
//   持っていないものは着られない / 着る・外す / 保存と同期の往復 / 絵と形の素材がそろっている
public final class SkinSelfTest {
    private SkinSelfTest() {
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "skin_test"));
        try {
            // 仕立て屋の週替わり: 4 着、同じ週は同じ並び、週が替わると替わることがある
            java.util.List<String> stock = Skins.weeklyStock();
            check(f, stock.size() == Skins.WEEKLY && stock.equals(Skins.weeklyStock()), "the weekly stock is not four stable skins: " + stock);
            java.util.Set<java.util.List<String>> weeks = new java.util.HashSet<>();
            for (int w = 1; w <= 12; w++) {
                weeks.add(Skins.weeklyStock("2026-W" + w));
            }
            check(f, weeks.size() > 1, "the weekly stock never changes");
            Skins.Skin s = Skins.byId(stock.get(0));
            check(f, Skins.ALL.size() >= 4 && Skins.ALL.stream().map(Skins.Skin::id).distinct().count() == Skins.ALL.size(), "skins are missing or repeat");
            check(f, Skins.buy(p, s.id()) == Skins.Buy.FAR, "a skin was bought without the tailor");
            Skins.openTailor(p);
            Coins.take(p, Coins.count(p));
            check(f, Skins.buy(p, s.id()) == Skins.Buy.COINS && !Skins.data(p).has(s.id()), "a skin was bought without enough coins");
            Coins.give(p, s.price() + 7);
            check(f, Skins.buy(p, s.id()) == Skins.Buy.OK && Skins.data(p).has(s.id()) && Coins.count(p) == 7, "buying a skin did not take the price: " + Coins.count(p));
            check(f, Skins.buy(p, s.id()) == Skins.Buy.OWNED, "an owned skin was sold again");
            Skins.Skin other = Skins.ALL.stream().filter(k -> !k.id().equals(s.id())).findFirst().orElseThrow();
            Skins.Skin offWeek = Skins.ALL.stream().filter(k -> k.shop() == Skins.Shop.TAILOR && !stock.contains(k.id())).findFirst().orElse(null);
            if (offWeek != null) {
                Coins.give(p, offWeek.price());
                check(f, Skins.buy(p, offWeek.id()) == Skins.Buy.NOT_THIS_WEEK, "a skin not in this week's stock was sold");
                Coins.take(p, offWeek.price());
            }
            check(f, !Skins.wear(p, other.id()) && Skins.worn(p) == null, "a skin not owned was worn");
            check(f, Skins.wear(p, s.id()) && s.id().equals(Skins.worn(p)), "wearing an owned skin failed");
            check(f, Skins.wear(p, "") && Skins.worn(p) == null, "taking the skin off failed");
            // 店ごと: 塔のスキンは仕立て屋では買えず、塔守で塔の欠片。ほうびのスキンは買えない
            Skins.Skin tower = Skins.ALL.stream().filter(k -> k.shop() == Skins.Shop.TOWER).findFirst().orElseThrow();
            check(f, Skins.buy(p, tower.id()) == Skins.Buy.FAR, "a tower skin was sold at the tailor");
            Skins.openShop(p, Skins.Shop.TOWER);
            net.minecraft.world.item.Item shard = jp.revati.skyabyss.tower.gear.TowerGearSetup.SHARD.get();
            p.getInventory().add(new net.minecraft.world.item.ItemStack(shard, tower.price()));
            check(f, Skins.buy(p, tower.id()) == Skins.Buy.OK && Skins.data(p).has(tower.id())
                    && jp.revati.skyabyss.pouch.Pouch.countAll(p, shard) == 0, "a tower skin was not bought with shards");
            for (Skins.Skin k : Skins.ALL) {
                if (k.shop() == Skins.Shop.REWARD) {
                    check(f, Skins.buy(p, k.id()) == Skins.Buy.NOT_SOLD, "a reward skin was sold: " + k.id());
                }
            }
            // ほうび: 名声 5 段の印と図鑑の印
            Skins.checkRewards(p, java.util.Set.of("hero:tide"));
            check(f, Skins.data(p).has("astronaut") && !Skins.data(p).has("vampire"), "the fame reward skin was not given (or too many)");
            Skins.checkRewards(p, java.util.Set.of("foes:all"));
            check(f, Skins.data(p).has("vampire"), "the codex reward skin was not given");
            // まれな箱: 何度か引くと出る、出たあとは二度と出ない
            boolean dropped = false;
            for (int i = 0; i < 2000 && !dropped; i++) {
                dropped = Skins.rareDrop(p);
            }
            check(f, dropped && Skins.data(p).has("ninja"), "the rare skin never dropped");
            boolean again = false;
            for (int i = 0; i < 500 && !again; i++) {
                again = Skins.rareDrop(p);
            }
            check(f, !again, "an owned rare skin dropped again");
            // 転生 (レベル・技を戻す) でスキンは消えない
            Skins.wear(p, s.id());
            jp.revati.skyabyss.rebirth.Rebirth.reset(p);
            check(f, s.id().equals(Skins.worn(p)) && Skins.data(p).has("ninja"), "a rebirth took the skins away");
            Skins.wear(p, s.id());
            Skins.Data d = Skins.data(p);
            RegistryFriendlyByteBuf b = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess(), ConnectionType.NEOFORGE);
            Skins.Data.STREAM_CODEC.encode(b, d);
            check(f, Skins.Data.STREAM_CODEC.decode(b).equals(d), "skin data stream codec round trip");
            check(f, Skins.Data.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, d).result()
                    .flatMap(t -> Skins.Data.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, t).result()).map(d::equals).orElse(false), "skin data save round trip");
            // 素材: 部位ごとの形と絵、衣装棚の絵、名前
            for (Skins.Skin k : Skins.ALL) {
                for (String piece : new String[] {"helmet", "chestplate", "leggings", "boots"}) {
                    String mesh = "lowpoly/armor/skin_" + k.id() + "/" + piece + ".json";
                    check(f, SkinSelfTest.class.getResource("/assets/skyabyss/" + mesh) != null, "missing skin mesh " + mesh);
                    check(f, SkinSelfTest.class.getResource("/assets/skyabyss/textures/lowpoly/armor/skin_" + k.id() + "/" + piece + ".png") != null,
                            "missing skin texture " + k.id() + "/" + piece);
                }
                check(f, SkinSelfTest.class.getResource("/assets/skyabyss/textures/gui/skins/" + k.id() + ".png") != null, "missing wardrobe picture " + k.id());
            }
        } catch (RuntimeException e) {
            f.add("skins test crashed: " + e);
            SkyAbyss.LOGGER.error("skins test", e);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST skins PASSED (tailor only, weekly stock, coins, buy once, tower shards, rewards not sold, fame and codex rewards, rare drop once, kept through rebirth, wear owned only, take off, save and sync round trips, meshes and pictures)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST skins FAILED: {}", m));
        }
    }
}
