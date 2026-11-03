package jp.revati.skyabyss.world.continent;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.Coins;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 逃げる獣 (RareBeast・RareBeasts): 型と絵と動き、土地と獣、出る所の決まり、硬さ、潜った土竜、倒した報い、その日の締め出し
public final class RareBeastSelfTest {
    private RareBeastSelfTest() {
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        for (RareBeast.Kind k : RareBeast.Kind.values()) {
            check(f, RareBeasts.type(k) != null, k.id + ": no entity type");
            String a = "/assets/skyabyss/";
            check(f, exists(a + "lowpoly/" + k.id + ".json") && exists(a + "geo/entity/" + k.id + "_lowpoly.geo.json") && exists(a + "textures/entity/" + k.id + ".png"),
                    k.id + ": model files missing");
            String anims = read(a + "animations/entity/" + k.id + ".animation.json");
            List<String> need = new ArrayList<>(List.of("idle", "walk", "run", "hurt", "death"));
            switch (k) {
                case ROLLER -> need.add("roll");
                case OTTER -> need.add("swim");
                case MOLE -> {
                    need.add("dig");
                    need.add("emerge");
                }
                default -> {
                }
            }
            for (String n : need) {
                check(f, anims.contains("\"animation." + k.id + "." + n + "\""), k.id + ": animation " + n + " missing");
            }
            check(f, RareBeast.Kind.ofRegion(k.region) == k, k.id + ": region " + k.region + " does not map back");
        }
        check(f, RareBeast.Kind.ofRegion(1) == null, "the start island has a beast");
        // 出る所: 都と町の近くには出ない、道ぞいだけ
        ContinentLayout lay = ContinentLayout.get();
        BlockPos cap = lay.capital();
        check(f, RareBeasts.nearTown(cap.getX(), cap.getZ()) && RareBeasts.kindAt(cap.getX(), cap.getZ()) == RareBeast.Kind.HARE, "the capital is not meadow / not kept clear");
        BlockPos mid = lay.roads().get(1).get(lay.roads().get(1).size() / 2);
        check(f, RareBeasts.alongRoad(mid.getX(), mid.getZ()) && !RareBeasts.alongRoad(mid.getX() + 300, mid.getZ() + 300), "road nearness is wrong");
        run(f, server.overworld());
        f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST rare beasts FAILED: {}", m));
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST rare beasts PASSED (5 beasts with models and their escape animations, one per land, not near towns, only along roads, hits capped, a dug-in mole cannot be hit, a kill pays coins and a box, today's lock)");
        }
    }

    private static void run(List<String> f, ServerLevel level) {
        FakePlayer p = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-00000000bea5"), "beast_test"));
        RareBeasts.forgetForTest(p);
        BlockPos at = level.getSharedSpawnPos().above(2);
        p.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 4.5);
        List<RareBeast> made = new ArrayList<>();
        try {
            RareBeast hare = spawn(level, RareBeast.Kind.HARE, at, p, made);
            float hp = hare.getHealth();
            hare.hurt(level.damageSources().playerAttack(p), 50f);
            check(f, hare.getHealth() >= hp - RareBeast.HIT_CAP - 0.01f && hare.getHealth() < hp, "a big hit was not capped (" + hp + " -> " + hare.getHealth() + ")");
            check(f, hare.state() == RareBeast.FLEE, "a hit did not make it flee");
            RareBeast mole = spawn(level, RareBeast.Kind.MOLE, at, p, made);
            mole.setState(RareBeast.SPECIAL);
            mole.digTicks = 10;
            float mh = mole.getHealth();
            mole.hurt(level.damageSources().playerAttack(p), 2f);
            check(f, mole.getHealth() == mh, "a dug-in mole took damage");
            // 倒すと報い (鐘銭と数)
            int coins = Coins.count(p);
            RareBeast otter = spawn(level, RareBeast.Kind.OTTER, at, p, made);
            for (int i = 0; i < 20 && otter.isAlive(); i++) {
                otter.invulnerableTime = 0;
                otter.hurt(level.damageSources().playerAttack(p), 50f);
            }
            check(f, !otter.isAlive(), "the otter did not fall");
            check(f, Coins.count(p) == coins + RareBeast.Kind.OTTER.coins && RareBeasts.caught(p, RareBeast.Kind.OTTER) == 1,
                    "the kill paid " + (Coins.count(p) - coins) + " coins (expected " + RareBeast.Kind.OTTER.coins + ")");
            // 逃げ切られた日は締め出す
            check(f, !RareBeasts.locked(p, RareBeast.Kind.WEASEL), "locked before any escape");
            RareBeasts.lock(p, RareBeast.Kind.WEASEL);
            check(f, RareBeasts.locked(p, RareBeast.Kind.WEASEL) && !RareBeasts.locked(p, RareBeast.Kind.HARE), "today's lock is wrong");
        } finally {
            made.forEach(b -> {
                if (!b.isRemoved()) {
                    b.discard();
                }
            });
            RareBeasts.forgetForTest(p);
        }
    }

    private static RareBeast spawn(ServerLevel level, RareBeast.Kind k, BlockPos at, FakePlayer owner, List<RareBeast> made) {
        RareBeast b = RareBeasts.type(k).create(level);
        b.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
        b.owner = owner.getUUID();
        level.addFreshEntity(b);
        made.add(b);
        return b;
    }

    private static boolean exists(String path) {
        try (InputStream in = RareBeastSelfTest.class.getResourceAsStream(path)) {
            return in != null;
        } catch (java.io.IOException e) {
            return false;
        }
    }

    private static String read(String path) {
        try (InputStream in = RareBeastSelfTest.class.getResourceAsStream(path)) {
            return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            return "";
        }
    }

    private static void check(List<String> f, boolean ok, String msg) {
        if (!ok) {
            f.add(msg);
        }
    }
}
