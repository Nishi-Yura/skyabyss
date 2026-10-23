package jp.revati.skyabyss.titan.ember.mob;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.GrowthRewards;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.back.EmberBackLayout;
import jp.revati.skyabyss.titan.ember.back.EmberHeat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

// 余燼の背の敵の自己テスト (SKYABYSS_SELFTEST。ログに SELFTEST ember mobs PASSED / FAILED)。
// 余燼の背 (ember_back) の灰の原の遠い所 (x 2008, z 2008) で、偽のプレイヤーと敵を置いて確かめる:
//   登録と強さ (13 の 5.1節)、スポーンエッグ、試練の個体は保存されない、経験値の表 (6.6節)、落とす物の表 (6.1節、2 万回)、
//   03 の 9.8節の目安で倒すのにかかる手数と、敵の一撃に耐える回数、
//   灰の殻 (半分・殻へ同じだけ・火照りと熱の噴きで 2 倍)、燃え上がり (速さ・攻撃・受ける傷・15 秒で燃え尽きる)、
//   蛾 (いちばん熱い人へ寄る・火の鍵・触れて熱 +8・散って熱 +4・部屋の上限)、
//   燠虫 (丸まると防御 20・壁で目を回して防御 0・2 秒で戻る・火の粉の帯)
public final class EmberMobSelfTest {
    private static final int TX = 2008;
    private static final int TZ = 2008;
    // まわり 2 チャンクまで読み込む (燠虫は 2 秒で 22 ブロック転がる)
    private static final int CHUNKS = 2;
    private static final int FLOOR = EmberBackLayout.PLAIN_TOP;

    private EmberMobSelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        return List.of(
                SelfTestRunner.Step.once("ember mobs (load)", EmberMobSelfTest::load),
                SelfTestRunner.Step.once("ember mobs", 30, EmberMobSelfTest::run));
    }

    static void load(MinecraftServer server) {
        ServerLevel back = server.getLevel(EmberIds.BACK);
        if (back != null) {
            force(back, true);
        }
    }

    private static void force(ServerLevel back, boolean on) {
        for (int dx = -CHUNKS; dx <= CHUNKS; dx++) {
            for (int dz = -CHUNKS; dz <= CHUNKS; dz++) {
                back.setChunkForced((TX >> 4) + dx, (TZ >> 4) + dz, on);
            }
        }
    }

    static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel back = server.getLevel(EmberIds.BACK);
        List<Mob> made = new ArrayList<>();
        try {
            if (back == null) {
                f.add("no ember_back level");
            } else {
                check(back, made, f);
            }
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST ember mobs crashed", e);
            f.add("crashed: " + e);
        } finally {
            made.forEach(Mob::discard);
            if (back != null) {
                clearWall(back);
                force(back, false);
            }
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST ember mobs PASSED (registry and stats, eggs, not saved, xp, drop table, 03 strength, ash shell and flare, "
                    + "burn out, moth lure and heat, room cap, roller curl armor, bonk and dizzy, spark trail)");
        } else {
            f.forEach(x -> SkyAbyss.LOGGER.error("SELFTEST ember mobs FAILED: {}", x));
        }
    }

    private static void expect(boolean ok, String failure, List<String> f) {
        if (!ok) {
            f.add(failure);
        }
    }

    private static <T extends Mob> T make(ServerLevel level, EntityType<T> type, double x, double z, List<Mob> made) {
        T mob = type.create(level);
        mob.moveTo(x, FLOOR + 1, z, 0, 0);
        level.addFreshEntity(mob);
        made.add(mob);
        return mob;
    }

    private static void check(ServerLevel back, List<Mob> made, List<String> f) {
        checkStats(back, f);
        checkXp(f);
        checkLoot(f);
        checkStrength(back, made, f);
        checkSaved(back, made, f);
        checkShell(back, made, f);
        checkMoth(back, made, f);
        checkRoller(back, made, f);
    }

    // ---------------------------------------------------------------- 登録と強さ (5.1節の表)
    private static void checkStats(ServerLevel level, List<String> f) {
        Object[][] rows = {
                {EmberMobSetup.ASH_THRALL.get(), EmberIds.ASH_THRALL, 60.0, 13.0, 10.0},
                {EmberMobSetup.EMBER_MOTH.get(), EmberIds.EMBER_MOTH, 8.0, 3.0, 0.0},
                {EmberMobSetup.CINDER_ROLLER.get(), EmberIds.CINDER_ROLLER, 36.0, 12.0, 6.0},
        };
        for (Object[] r : rows) {
            EntityType<?> type = (EntityType<?>) r[0];
            expect(BuiltInRegistries.ENTITY_TYPE.getKey(type).equals(EmberIds.id((String) r[1])), "id of " + r[1], f);
            Mob mob = (Mob) type.create(level);
            expect(mob instanceof EmberMob, r[1] + " is not an EmberMob", f);
            if (mob == null) {
                continue;
            }
            expect(mob.getAttributeBaseValue(Attributes.MAX_HEALTH) == (double) r[2], r[1] + " health " + mob.getAttributeBaseValue(Attributes.MAX_HEALTH), f);
            expect(mob.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) == (double) r[3], r[1] + " damage", f);
            expect(mob.getAttributeBaseValue(Attributes.ARMOR) == (double) r[4], r[1] + " armor", f);
            expect(type.fireImmune(), r[1] + " should not burn in its own fire", f);
            mob.discard();
        }
        expect(AshThrall.SHELL_MAX == 30 && AshThrall.REACH == 3.0 && AshThrall.FLARED_DAMAGE == 16 && AshThrall.BURNOUT_TICKS == 300,
                "ash thrall numbers (shell 30, reach 3, flared 16, 15 s)", f);
        expect(CinderRoller.CURLED_ARMOR == 20 && CinderRoller.DIZZY_TICKS == 40 && CinderRoller.CURL_TICKS == 16 && CinderRoller.TRAIL_TICKS == 60,
                "cinder roller numbers (curled 20, dizzy 2 s, warn 0.8 s, trail 3 s)", f);
        expect(EmberMoth.TOUCH_HEAT == 8 && EmberMoth.SPARK_HEAT == 4 && EmberMoth.roomCap(1) == 18 && EmberMoth.roomCap(3) == 26,
                "ember moth numbers (touch +8, sparks +4, room 18 +4 per player)", f);
        for (var egg : List.of(EmberMobSetup.ASH_THRALL_EGG, EmberMobSetup.EMBER_MOTH_EGG, EmberMobSetup.CINDER_ROLLER_EGG)) {
            expect(egg.get() != null && !new ItemStack(egg.get()).isEmpty(), "spawn egg " + egg.getId(), f);
        }
        expect(EmberMobSetup.ASH_THRALL_EGG.getId().getPath().equals(EmberIds.ASH_THRALL_EGG)
                && EmberMobSetup.EMBER_MOTH_EGG.getId().getPath().equals(EmberIds.EMBER_MOTH_EGG)
                && EmberMobSetup.CINDER_ROLLER_EGG.getId().getPath().equals(EmberIds.CINDER_ROLLER_EGG), "egg ids are not the EmberIds ones", f);
    }

    // ---------------------------------------------------------------- 経験値 (6.6節): 26 / 4 / 16、推奨 30
    private static void checkXp(List<String> f) {
        Object[][] rows = {{EmberIds.ASH_THRALL, 26}, {EmberIds.EMBER_MOTH, 4}, {EmberIds.CINDER_ROLLER, 16}};
        for (Object[] r : rows) {
            GrowthRewards.Source s = GrowthRewards.MOD_ENEMIES.get(EmberIds.id((String) r[0]).toString());
            expect(s != null && s.xp() == (int) r[1] && s.contentLevel() == 30, "xp of " + r[0] + " = " + s, f);
        }
    }

    // ---------------------------------------------------------------- 落とす物 (6.1節)
    private static void checkLoot(List<String> f) {
        RandomSource random = RandomSource.create(20261016L);
        Object[][] rows = {{"thrall", EmberMobLoot.THRALL}, {"moth", EmberMobLoot.MOTH}, {"roller", EmberMobLoot.ROLLER}};
        int n = 20000;
        for (Object[] row : rows) {
            EmberMobLoot.Row r = (EmberMobLoot.Row) row[1];
            Map<ResourceLocation, Integer> got = new HashMap<>();
            for (int i = 0; i < n; i++) {
                for (EmberMobLoot.Drop d : EmberMobLoot.roll(r, random)) {
                    ResourceLocation key = EmberMobLoot.ARMOR.contains(d.id()) ? EmberIds.id("ember_armor_any") : d.id();
                    got.merge(key, 1, Integer::sum);
                    if (d.id().equals(EmberMobLoot.SHARD)) {
                        expect(d.count() >= r.shardMin() && d.count() <= r.shardMax(), row[0] + " shard count " + d.count(), f);
                    } else if (!List.of(EmberMobLoot.RESONANCE, EmberMobLoot.HORN).contains(d.id()) && !EmberMobLoot.ARMOR.contains(d.id())
                            && !d.id().equals(r.extra())) {
                        f.add(row[0] + " unexpected drop " + d.id());
                    }
                }
            }
            near(f, row[0] + " shard", got.getOrDefault(EmberMobLoot.SHARD, 0), n, r.shard());
            near(f, row[0] + " resonance", got.getOrDefault(EmberMobLoot.RESONANCE, 0), n, r.resonance());
            near(f, row[0] + " horn", got.getOrDefault(EmberMobLoot.HORN, 0), n, r.horn());
            near(f, row[0] + " armor", got.getOrDefault(EmberIds.id("ember_armor_any"), 0), n, r.armor());
            if (r.extra() != null) {
                near(f, row[0] + " " + r.extra().getPath(), got.getOrDefault(r.extra(), 0), n, r.extraChance());
            }
        }
        // 表の値 (13 の 6.1節)
        expect(EmberMobLoot.THRALL.equals(new EmberMobLoot.Row(0.35f, 1, 2, 0.06f, 0.04f, 0.01f, null, 0f)), "thrall table (6.1)", f);
        expect(EmberMobLoot.MOTH.equals(new EmberMobLoot.Row(0.05f, 1, 1, 0.01f, 0f, 0f, EmberMobLoot.SPARK_DUST, 0.15f)), "moth table (6.1)", f);
        expect(EmberMobLoot.ROLLER.equals(new EmberMobLoot.Row(0.25f, 1, 2, 0.05f, 0.03f, 0f, EmberMobLoot.CINDER_HUSK, 0.20f)), "roller table (6.1)", f);
        // 共鳴の欠片は今ある品。ほかは担当 G が登録するまで落ちない (登録されていない品は物にしない)
        expect(EmberMobLoot.stacks(List.of(new EmberMobLoot.Drop(EmberMobLoot.RESONANCE, 1))).size() == 1, "resonance shard is not found", f);
        expect(EmberMobLoot.stacks(List.of(new EmberMobLoot.Drop(EmberIds.id("no_such_item_for_test"), 1))).isEmpty(), "unknown item became a stack", f);
        expect(EmberBackLayout.SOUTH.contains(EmberBackLayout.SOUTH.center()), "the hearth valley box", f);
    }

    private static void near(List<String> f, String what, int got, int n, float p) {
        double rate = got / (double) n;
        double tol = 4 * Math.sqrt(p * (1 - p) / n) + 1e-9;
        expect(Math.abs(rate - p) <= tol, what + " rate " + rate + " expected " + p, f);
    }

    // ---------------------------------------------------------------- 03 の 9.8節の目安
    // 余燼の段の武器 +0 は 1 秒あたり 17.5 (一撃 17.5 を 1 秒に一度とする)、防具 +0 は防御 22 / 強靭 16、体力 20
    static final float WEAPON_HIT = 17.5f;
    static final float PLAYER_ARMOR = 22;
    static final float PLAYER_TOUGHNESS = 16;

    // バニラの防具の式 (1.21: CombatRules.getDamageAfterAbsorb の強靭の効き)
    static float afterArmor(float dmg, float armor, float toughness) {
        float f = 2.0f + toughness / 4.0f;
        float g = Math.min(20f, Math.max(armor - dmg / f, armor * 0.2f));
        return dmg * (1 - g / 25f);
    }

    private static void checkStrength(ServerLevel level, List<Mob> made, List<String> f) {
        // 敵の一撃に何回耐えるか (体力 20)
        double thrall = 20 / afterArmor(AshThrall.DAMAGE, PLAYER_ARMOR, PLAYER_TOUGHNESS);
        double flared = 20 / afterArmor(AshThrall.FLARED_DAMAGE, PLAYER_ARMOR, PLAYER_TOUGHNESS);
        double roller = 20 / afterArmor(CinderRoller.DAMAGE, PLAYER_ARMOR, PLAYER_TOUGHNESS);
        expect(thrall >= 6 && thrall <= 9, "thrall hits to down a player " + thrall, f);
        expect(flared >= 4.5 && flared < thrall, "flared thrall hits to down a player " + flared, f);
        expect(roller >= 6.5 && roller <= 10, "roller hits to down a player " + roller, f);
        SkyAbyss.LOGGER.info("SELFTEST ember mobs: hits to down a +0 player: thrall {} flared {} roller {}", String.format("%.1f", thrall),
                String.format("%.1f", flared), String.format("%.1f", roller));
        // 倒すのにかかる手数 (+0 の武器。殻のある灰被りは火照っていないとき・火照ったとき)
        int cold = hitsToKill(level, made, null);
        FakePlayer hot = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "ember_mob_test_hot"));
        EmberHeat.set(hot, 60);
        int warm = hitsToKill(level, made, hot);
        EmberHeat.reset(hot);
        expect(cold >= 5 && cold <= 8, "thrall takes " + cold + " hits (cold)", f);
        expect(warm < cold && warm >= 4, "thrall takes " + warm + " hits when flushed (cold " + cold + ")", f);
        CinderRoller r = make(level, EmberMobSetup.CINDER_ROLLER.get(), TX + 0.5, TZ + 6.5, made);
        int rh = 0;
        while (r.isAlive() && rh < 20) {
            r.invulnerableTime = 0;
            r.hurt(level.damageSources().generic(), WEAPON_HIT);
            rh++;
        }
        expect(rh >= 2 && rh <= 4, "roller takes " + rh + " hits", f);
        SkyAbyss.LOGGER.info("SELFTEST ember mobs: +0 weapon hits to kill: thrall {} (flushed {}), roller {}, moth 1", cold, warm, rh);
    }

    // 殻のある灰被りを 17.5 の一撃で倒すのに要る回数 (by が火照った人なら殻へ 2 倍)
    private static int hitsToKill(ServerLevel level, List<Mob> made, FakePlayer by) {
        AshThrall t = make(level, EmberMobSetup.ASH_THRALL.get(), TX + 0.5, TZ - 6.5, made);
        int hits = 0;
        while (t.isAlive() && hits < 30) {
            t.invulnerableTime = 0;
            t.hurt(by == null ? level.damageSources().generic() : level.damageSources().playerAttack(by), WEAPON_HIT);
            hits++;
        }
        return hits;
    }

    // ---------------------------------------------------------------- 保存されない
    private static void checkSaved(ServerLevel level, List<Mob> made, List<String> f) {
        for (EntityType<? extends Mob> type : List.<EntityType<? extends Mob>>of(EmberMobSetup.ASH_THRALL.get(), EmberMobSetup.EMBER_MOTH.get(),
                EmberMobSetup.CINDER_ROLLER.get())) {
            Mob free = make(level, type, TX + 4.5, TZ + 4.5, made);
            Mob trial = make(level, type, TX + 4.5, TZ + 4.5, made);
            trial.addTag(EmberMob.TRIAL_TAG);
            expect(free.shouldBeSaved(), type + " from an egg should be saved", f);
            expect(!trial.shouldBeSaved(), type + " from the trial must not be saved", f);
            expect(!free.removeWhenFarAway(100), type + " should not despawn", f);
        }
    }

    // ---------------------------------------------------------------- 灰の殻と燃え上がり
    private static void checkShell(ServerLevel level, List<Mob> made, List<String> f) {
        AshThrall t = make(level, EmberMobSetup.ASH_THRALL.get(), TX - 4.5, TZ + 0.5, made);
        jp.revati.skyabyss.combat.MobTuning.untune(t);
        // 防具を通る傷 (焦げ) で、殻の分け方だけを見る
        t.hurt(t.damage(EmberIds.HEAT, null), 10);
        expect(Math.abs(t.getHealth() - 55) < 1e-3 && Math.abs(t.shell() - 25) < 1e-3, "shell halves (hp " + t.getHealth() + ", shell " + t.shell() + ")", f);
        t.invulnerableTime = 0;
        t.hurt(t.damage(EmberIds.HEAT_BURST, null), 10);
        expect(Math.abs(t.shell() - 15) < 1e-3, "heat burst is double on the shell (shell " + t.shell() + ")", f);
        FakePlayer hot = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "ember_mob_test_flushed"));
        EmberHeat.set(hot, 55);
        expect(AshThrall.shellMultiplier(level.damageSources().playerAttack(hot)) == 2f, "a flushed player is double on the shell", f);
        EmberHeat.set(hot, 30);
        expect(AshThrall.shellMultiplier(level.damageSources().playerAttack(hot)) == 1f, "a player at heat 30 is not double", f);
        EmberHeat.reset(hot);
        expect(!t.isFlared(), "flared too early", f);
        // 殻を割る (残り 15 に、熱の噴き 20 は殻へ 20)
        t.invulnerableTime = 0;
        t.hurt(t.damage(EmberIds.HEAT_BURST, null), 20);
        expect(t.isFlared() && t.shell() == 0f, "the shell did not break into a flare (shell " + t.shell() + ")", f);
        expect(Math.abs(t.getAttributeValue(Attributes.MOVEMENT_SPEED) - AshThrall.SPEED * 1.3) < 1e-6, "flared speed is not x1.3", f);
        expect(Math.abs(t.getAttributeValue(Attributes.ATTACK_DAMAGE) - 16) < 1e-6, "flared attack is not 16", f);
        float hp = t.getHealth();
        t.invulnerableTime = 0;
        t.hurt(t.damage(EmberIds.HEAT, null), 4);
        expect(Math.abs(hp - t.getHealth() - 5) < 1e-3, "flared thrall does not take x1.25 (" + (hp - t.getHealth()) + ")", f);
        // 15 秒で燃え尽きる
        expect(t.burnLeft() == AshThrall.BURNOUT_TICKS, "burn time is not 15 s", f);
        for (int i = 0; i < AshThrall.BURNOUT_TICKS - 1 && t.isAlive(); i++) {
            t.aiStep();
        }
        expect(t.isAlive(), "burned out too early", f);
        t.aiStep();
        expect(!t.isAlive(), "did not burn out after 15 s", f);
        // 振り下ろしは前だけ、3 ブロックまで
        AshThrall s = make(level, EmberMobSetup.ASH_THRALL.get(), TX - 8.5, TZ + 0.5, made);
        s.setYRot(0);
        FakePlayer front = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "ember_mob_test_front"));
        front.moveTo(s.getX(), s.getY(), s.getZ() + 3.2, 0, 0);
        expect(s.inSwingReach(front), "swing does not reach 3 blocks in front", f);
        front.moveTo(s.getX(), s.getY(), s.getZ() + 4.6, 0, 0);
        expect(!s.inSwingReach(front), "swing reaches too far", f);
        front.moveTo(s.getX(), s.getY(), s.getZ() - 2.0, 0, 0);
        expect(!s.inSwingReach(front), "swing reaches behind", f);
    }

    // ---------------------------------------------------------------- 火の粉蛾
    private static void checkMoth(ServerLevel level, List<Mob> made, List<String> f) {
        EmberMoth m = make(level, EmberMobSetup.EMBER_MOTH.get(), TX + 0.5, TZ + 10.5, made);
        Vec3 here = m.position();
        FakePlayer near = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "ember_mob_test_near"));
        FakePlayer hot = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "ember_mob_test_hotter"));
        near.moveTo(here.x + 2, FLOOR + 1, here.z, 0, 0);
        hot.moveTo(here.x - 8, FLOOR + 1, here.z, 0, 0);
        EmberHeat.set(hot, 70);
        expect(EmberMoth.hottestPlayer(List.of(near, hot), here) == hot, "the moth does not go to the hottest player", f);
        EmberHeat.set(hot, 0);
        expect(EmberMoth.hottestPlayer(List.of(near, hot), here) == near, "with no heat the moth does not go to the nearest", f);
        hot.moveTo(here.x + 30, FLOOR + 1, here.z, 0, 0);
        EmberHeat.set(hot, 100);
        expect(EmberMoth.hottestPlayer(List.of(hot), here) == null, "the moth goes to a player out of range", f);
        // 触れて熱 +8 (たまり方の倍率は B が持つ。ここでは偽のプレイヤーに倍率の素がないので 1 倍のはず)
        EmberHeat.reset(near);
        m.touch(near);
        float h = EmberHeat.get(near);
        expect(h > 0 && h <= EmberMoth.TOUCH_HEAT + 1e-3, "a touch did not add heat 8 (" + h + ")", f);
        expect(m.touchCooldown() > 0, "touch has no cooldown", f);
        m.touch(near);
        expect(EmberHeat.get(near) == h, "touched twice without waiting", f);
        // 散って熱 +4 (3 ブロック以内だけ)
        hot.moveTo(here.x + 5, FLOOR + 1, here.z, 0, 0);
        EmberHeat.reset(hot);
        int sparked = EmberMoth.sparkPlayers(List.of(near, hot), here);
        expect(sparked == 1 && EmberHeat.get(hot) == 0f && EmberHeat.get(near) > h, "sparks reach only 3 blocks (" + sparked + ")", f);
        EmberHeat.reset(near);
        EmberHeat.reset(hot);
        // 叩けば一撃
        m.hurt(level.damageSources().generic(), WEAPON_HIT);
        expect(!m.isAlive(), "a moth did not die in one hit", f);
    }

    // ---------------------------------------------------------------- 燠虫
    private static void checkRoller(ServerLevel level, List<Mob> made, List<String> f) {
        // 東 6 ブロック先に壁 (高さ 3)
        buildWall(level);
        CinderRoller r = make(level, EmberMobSetup.CINDER_ROLLER.get(), TX + 20.5, TZ - 20.5, made);
        for (int i = 0; i < 5; i++) {
            r.tick();
        }
        expect(r.mode() == CinderRoller.Mode.WALK && Math.abs(r.getArmorValue() - 6) < 1e-3, "roller starts walking with armor 6", f);
        FakePlayer aim = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "ember_mob_test_roll"));
        aim.moveTo(TX + 30.5, FLOOR + 1, TZ - 20.5, 0, 0);
        r.curlToward(aim);
        expect(r.mode() == CinderRoller.Mode.CURL && r.getArmorValue() == 20, "curled armor is not 20 (" + r.getArmorValue() + ")", f);
        int ticks = 0;
        while (r.mode() == CinderRoller.Mode.CURL && ticks < 40) {
            r.tick();
            ticks++;
        }
        expect(ticks == CinderRoller.CURL_TICKS && r.mode() == CinderRoller.Mode.ROLL, "the warning is not 0.8 s (" + ticks + ")", f);
        double x0 = r.getX();
        ticks = 0;
        while (r.mode() == CinderRoller.Mode.ROLL && ticks < 60) {
            r.tick();
            ticks++;
        }
        expect(r.mode() == CinderRoller.Mode.DIZZY, "the roller did not get dizzy at the wall (mode " + r.mode() + ", x " + r.getX() + ")", f);
        expect(r.getX() > x0 + 3, "the roller did not roll toward the target (" + x0 + " -> " + r.getX() + ")", f);
        expect(r.getArmorValue() == 0, "dizzy armor is not 0 (" + r.getArmorValue() + ")", f);
        expect(r.trailLength() > 0, "no spark trail behind the roll", f);
        expect(r.onTrail(new Vec3(x0 + 2, FLOOR + 1, TZ - 20.5)) && !r.onTrail(new Vec3(x0 + 2, FLOOR + 1, TZ - 17.5)),
                "the spark trail is not under the path", f);
        ticks = 0;
        while (r.mode() == CinderRoller.Mode.DIZZY && ticks < 80) {
            r.tick();
            ticks++;
        }
        expect(Math.abs(ticks - CinderRoller.DIZZY_TICKS) <= 1 && r.mode() == CinderRoller.Mode.WALK && Math.abs(r.getArmorValue() - 6) < 1e-3,
                "dizzy does not last 2 s (" + ticks + ")", f);
        // 何もない所へ転がると、目を回さずに止まる
        CinderRoller free = make(level, EmberMobSetup.CINDER_ROLLER.get(), TX + 20.5, TZ - 30.5, made);
        free.startRoll(new Vec3(-1, 0, 0));
        ticks = 0;
        while (free.mode() == CinderRoller.Mode.ROLL && ticks < 80) {
            free.tick();
            ticks++;
        }
        expect(free.mode() == CinderRoller.Mode.WALK && ticks == CinderRoller.ROLL_TICKS, "a free roll does not stop after 2 s (" + ticks + ", " + free.mode() + ")", f);
    }

    private static void buildWall(ServerLevel level) {
        for (int z = -24; z <= -17; z++) {
            for (int y = 1; y <= 3; y++) {
                level.setBlock(new BlockPos(TX + 27, FLOOR + y, TZ + z), Blocks.BASALT.defaultBlockState(), 3);
            }
        }
    }

    private static void clearWall(ServerLevel level) {
        for (int z = -24; z <= -17; z++) {
            for (int y = 1; y <= 3; y++) {
                level.setBlock(new BlockPos(TX + 27, FLOOR + y, TZ + z), Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }
}
