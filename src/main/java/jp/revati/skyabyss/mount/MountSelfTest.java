package jp.revati.skyabyss.mount;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.Coins;
import jp.revati.skyabyss.story.ch5.AbyssWorld;
import jp.revati.skyabyss.titan.TitanBacks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.util.FakePlayer;

// 乗り物 (docs/design/38) の自己テスト。SKYABYSS_SELFTEST_ONLY="mount"。
// 見た目と段を鐘銭で買う (章の壁・払えない・二重買い・最大)・呼ぶ・帰す・性能が乗り物に当たる・背中や深淵では呼べない・
// 遠く離れると帰る・持ち主が乗れる・落下で傷つかない・保存して読み直す・厩舎の画面を開いている間だけ買える・笛を一本だけ渡す・絵と翻訳の部品がある
public final class MountSelfTest {
    private MountSelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        return List.of(SelfTestRunner.Step.once("mount (load)", MountSelfTest::load),
                SelfTestRunner.Step.once("mount", 40, MountSelfTest::run));
    }

    private static BlockPos base(ServerLevel level) {
        return level.getSharedSpawnPos().offset(512, 0, 512);
    }

    private static void load(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos b = base(level);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setChunkForced((b.getX() >> 4) + dx, (b.getZ() >> 4) + dz, true);
            }
        }
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    private static boolean resource(String path) {
        try (java.io.InputStream in = MountSelfTest.class.getResourceAsStream(path)) {
            return in != null;
        } catch (java.io.IOException e) {
            return false;
        }
    }

    private static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "mount_p"));
        BlockPos b = base(level);
        p.setPos(b.getX() + 3.5, level.getMaxBuildHeight() - 20, b.getZ() + 3.5);
        try {
            assets(f);
            shop(f, p);
            summon(f, level, p);
            refusal(f, server);
            save(f);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST mount crashed", e);
            f.add("crashed: " + e);
        } finally {
            MountService.dismiss(p, false);
            MountService.setChapterOverride(0);
            p.getInventory().clearContent();
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST mount PASSED (assets, buy look/tier with coins, chapter wall, summon/dismiss, stats applied, refusal in backs/abyss, far dismiss, ride, fall immunity, save, shop session, whistle)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST mount FAILED: {}", m));
        }
    }

    private static void assets(List<String> f) {
        String a = "/assets/skyabyss/";
        for (MountLook look : MountLook.values()) {
            check(f, resource(a + "geo/entity/" + look.model() + ".geo.json"), "geo " + look.id);
            check(f, resource(a + "textures/entity/" + look.model() + ".png"), "texture " + look.id);
        }
        check(f, resource(a + "animations/entity/mount.animation.json"), "animation");
        check(f, resource(a + "textures/item/mount_whistle.png") && resource(a + "models/item/mount_whistle.json"), "whistle item");
        check(f, resource(a + "textures/gui/npc_marker/fac_stable_keeper.png"), "stable keeper icon");
        check(f, resource("/data/skyabyss/dialogue/attendant_stable_keeper.json"), "stable keeper dialogue");
        check(f, MountLook.values().length == 5 && MountLook.HORN_BEAST.price == 0 && MountLook.HORN_BEAST.chapter == 1, "roster: starter is free from the start");
        for (int k = 0; k < 3; k++) {
            for (int t = 2; t <= MountStats.TIERS; t++) {
                check(f, MountStats.price(k, t) > MountStats.price(k, t - 1), "price rises per tier " + k + "/" + t);
            }
        }
        check(f, MountStats.speed(5) > MountStats.speed(0) && MountStats.jump(5) > MountStats.jump(0) && MountStats.health(5) > MountStats.health(0), "stats rise");
    }

    private static void shop(List<String> f, FakePlayer p) {
        MountData d = MountService.data(p);
        check(f, d.owns(MountLook.HORN_BEAST) && d.chosen() == MountLook.HORN_BEAST, "starter owned and chosen");
        // 画面を開く前は押しても買えない
        Coins.give(p, 2000);
        MountService.act(p, MountNetwork.BUY_TIER, MountStats.SPEED);
        check(f, d.tier(MountStats.SPEED) == 0 && Coins.count(p) == 2000, "no buying without the shop open");
        MountService.open(p, null);
        check(f, MountService.isOpen(p), "shop opens");
        MountService.act(p, MountNetwork.BUY_TIER, MountStats.SPEED);
        check(f, d.tier(MountStats.SPEED) == 1 && Coins.count(p) == 2000 - MountStats.price(MountStats.SPEED, 1), "buy speed tier 1 with coins");
        // 章の壁
        MountService.setChapterOverride(1);
        check(f, MountService.buyLook(p, MountLook.WHALE_CALF) == MountService.Result.LOCKED && !d.owns(MountLook.WHALE_CALF), "look locked before its chapter");
        MountService.setChapterOverride(2);
        int before = Coins.count(p);
        check(f, MountService.buyLook(p, MountLook.WHALE_CALF) == MountService.Result.OK, "buy look after its chapter");
        check(f, d.owns(MountLook.WHALE_CALF) && d.chosen() == MountLook.WHALE_CALF && Coins.count(p) == before - MountLook.WHALE_CALF.price, "look paid, owned and chosen");
        check(f, MountService.buyLook(p, MountLook.WHALE_CALF) == MountService.Result.OWNED && Coins.count(p) == before - MountLook.WHALE_CALF.price, "no double buy");
        check(f, MountService.chooseLook(p, MountLook.SHELL_TORTOISE) == MountService.Result.NOT_OWNED, "cannot choose an unowned look");
        check(f, MountService.chooseLook(p, MountLook.HORN_BEAST) == MountService.Result.OK && d.chosen() == MountLook.HORN_BEAST, "choose owned look");
        // 段を最大まで。どの見た目でも同じ段
        for (int k = 0; k < 3; k++) {
            while (d.tier(k) < MountStats.TIERS) {
                Coins.give(p, 400);
                check(f, MountService.buyTier(p, k) == MountService.Result.OK, "buy tier " + k + "/" + (d.tier(k) + 1));
            }
            check(f, MountService.buyTier(p, k) == MountService.Result.MAXED, "tier " + k + " maxed");
        }
        Coins.take(p, Coins.count(p));
        MountData fresh = new MountData();
        MountService.setChapterOverride(2);
        check(f, fresh.tier(0) == 0, "fresh data has tier 0");
        // 払えない
        d.setTier(MountStats.SPEED, 0);
        check(f, MountService.buyTier(p, MountStats.SPEED) == MountService.Result.NO_COINS && d.tier(MountStats.SPEED) == 0, "cannot pay without coins");
        MountService.closed(p);
        check(f, !MountService.isOpen(p), "shop closes");
        // 笛
        check(f, MountService.giveWhistle(p) && MountService.hasWhistle(p) && !MountService.giveWhistle(p), "whistle given once");
        d.setTier(MountStats.SPEED, MountStats.TIERS);
    }

    private static void summon(List<String> f, ServerLevel level, FakePlayer p) {
        MountData d = MountService.data(p);
        d.choose(MountLook.WHALE_CALF);
        MountEntity m = MountService.summon(p);
        if (m == null) {
            f.add("summon returned null");
            return;
        }
        check(f, m.isAlive() && MountService.active(p) == m && p.getUUID().equals(m.owner()), "summoned and tracked");
        check(f, m.look() == MountLook.WHALE_CALF, "look applied");
        check(f, Math.abs(m.getAttributeValue(Attributes.MOVEMENT_SPEED) - MountStats.speed(MountStats.TIERS)) < 1e-6, "speed applied");
        check(f, Math.abs(m.getAttributeValue(Attributes.JUMP_STRENGTH) - MountStats.jump(MountStats.TIERS)) < 1e-6, "jump applied");
        check(f, Math.abs(m.getMaxHealth() - MountStats.health(MountStats.TIERS)) < 1e-6 && m.getHealth() >= m.getMaxHealth() - 0.01, "health applied and full");
        check(f, m.distanceToSqr(p) < 36, "called in front of the owner");
        check(f, !m.shouldBeSaved(), "mount is not saved in the world");
        check(f, m.isSaddled() && m.isTamed() && !m.causeFallDamage(30f, 1f, level.damageSources().fall()), "saddled, and no fall damage");
        check(f, m.isInvulnerableTo(level.damageSources().fall()), "immune to fall");
        // 買い足すと出ている乗り物にすぐ当たる (段は最大のまま、見た目を変える)
        d.setTier(MountStats.JUMP, 0);
        MountService.chooseLook(p, MountLook.HORN_BEAST);
        check(f, m.look() == MountLook.HORN_BEAST && Math.abs(m.getAttributeValue(Attributes.JUMP_STRENGTH) - MountStats.jump(0)) < 1e-6, "look and stats follow a change at once");
        d.setTier(MountStats.JUMP, MountStats.TIERS);
        // 乗る (持ち主だけ)
        m.mobInteract(p, InteractionHand.MAIN_HAND);
        check(f, p.getVehicle() == m && m.getControllingPassenger() == p, "owner rides and steers");
        p.stopRiding();
        // 一人一頭
        MountEntity again = MountService.summon(p);
        check(f, again != null && again != m && m.isRemoved() && MountService.active(p) == again, "one mount per player: calling again replaces it");
        // 遠く離れると帰る
        again.teleportTo(p.getX() + 200, p.getY(), p.getZ());
        MountService.watch(p);
        check(f, MountService.active(p) == null && again.isRemoved(), "far mount is dismissed");
        // 笛で帰す
        MountEntity third = MountService.summon(p);
        MountService.toggle(p);
        check(f, third != null && third.isRemoved() && MountService.active(p) == null, "dismissed by the whistle");
        MountService.toggle(p);
        check(f, MountService.active(p) != null, "called again by the whistle");
        MountService.dismiss(p, false);
        // 傷つけられても持ち主の人からではない
        MountEntity hurt = MountService.summon(p);
        if (hurt != null) {
            float hp = hurt.getHealth();
            check(f, !hurt.hurt(level.damageSources().playerAttack(p), 5f) && hurt.getHealth() == hp, "other players cannot hurt it");
            MountService.dismiss(p, false);
        }
    }

    private static void refusal(List<String> f, MinecraftServer server) {
        for (var key : TitanBacks.all()) {
            ServerLevel back = server.getLevel(key);
            if (back == null) {
                f.add("back level missing " + key.location());
                continue;
            }
            FakePlayer q = new FakePlayer(back, new GameProfile(UUID.randomUUID(), "mount_q"));
            check(f, MountService.refusal(q) != null, "refused in " + key.location());
            check(f, MountService.summon(q) == null && MountService.active(q) == null, "no summon in " + key.location());
        }
        ServerLevel abyss = server.getLevel(AbyssWorld.LEVEL);
        if (abyss != null) {
            FakePlayer q = new FakePlayer(abyss, new GameProfile(UUID.randomUUID(), "mount_a"));
            check(f, MountService.refusal(q) != null && MountService.summon(q) == null, "refused in the abyss");
        } else {
            f.add("abyss level missing");
        }
        FakePlayer ow = new FakePlayer(server.overworld(), new GameProfile(UUID.randomUUID(), "mount_o"));
        check(f, MountService.refusal(ow) == null, "allowed in the overworld");
    }

    private static void save(List<String> f) {
        MountData d = new MountData();
        d.own(MountLook.STORM_CHICK);
        d.choose(MountLook.STORM_CHICK);
        d.setTier(MountStats.SPEED, 3);
        d.setTier(MountStats.JUMP, 2);
        d.setTier(MountStats.HEALTH, 5);
        CompoundTag tag = d.serializeNBT(net.minecraft.core.RegistryAccess.EMPTY);
        MountData back = new MountData();
        back.deserializeNBT(net.minecraft.core.RegistryAccess.EMPTY, tag);
        check(f, back.owns(MountLook.STORM_CHICK) && back.owns(MountLook.HORN_BEAST) && !back.owns(MountLook.EMBER_FOX), "owned looks survive a save");
        check(f, back.chosen() == MountLook.STORM_CHICK && back.tier(0) == 3 && back.tier(1) == 2 && back.tier(2) == 5, "choice and tiers survive a save");
        CompoundTag bad = new CompoundTag();
        bad.putInt("owned", 0);
        bad.putInt("chosen", 4);
        MountData fixed = new MountData();
        fixed.deserializeNBT(net.minecraft.core.RegistryAccess.EMPTY, bad);
        check(f, fixed.chosen() == MountLook.HORN_BEAST && fixed.owns(MountLook.HORN_BEAST), "an unowned choice falls back to the starter");
    }
}
