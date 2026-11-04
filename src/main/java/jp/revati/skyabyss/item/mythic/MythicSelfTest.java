package jp.revati.skyabyss.item.mythic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.skill.Skill;
import jp.revati.skyabyss.rpg.skill.SkillActions;
import jp.revati.skyabyss.story.finale.gear.DivineGearSetup;
import jp.revati.skyabyss.tower.gear.TowerGearSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;

// 神器の大きな能力 (MythicAbilities。docs/design/03 の 13.4) の自己テスト。SKYABYSS_SELFTEST_ONLY="mythic"。
// 偽のプレイヤーは傷を受けないので、受ける側 (番人の誓いの溜め・約束の肩代わり) は数で、与える側は倍率と放つものの傷で確かめる
public final class MythicSelfTest {
    private MythicSelfTest() {
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        double px = spawn.getX() + 0.5;
        double py = 300;
        double pz = spawn.getZ() + 0.5;
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "mythic_test"));
        p.moveTo(px, py, pz, 0f, 0f); // 南 (+Z) を向く
        Growth.get(p).level = 50;
        p.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(7);
        List<Zombie> zs = new ArrayList<>();
        Zombie a = zombie(level, px, py, pz + 2.0, zs);
        Zombie b = zombie(level, px + 2.0, py, pz + 3.0, zs);
        long now = level.getGameTime();
        DamageSource melee = level.damageSources().playerAttack(p);
        try {
            // 天穹の剣「天の手」: 近接で溜め (最大 5)、巨神斬りで落とす。欠点の待ち +2 秒
            hold(p, DivineGearSetup.SWORD.get());
            for (int i = 0; i < 7; i++) {
                hitMelee(a, melee);
            }
            check(f, MythicAbilities.peek(p).hands == MythicAbilities.SKY_HANDS, "hands of heaven did not stop at 5: " + MythicAbilities.peek(p).hands);
            SkillActions.hit((net.minecraft.server.level.ServerPlayer) p, a, 1f, Skill.TITAN_CLEAVE);
            check(f, MythicAbilities.peek(p).hands == 0, "titan cleave did not drop the hands");
            check(f, MythicAbilities.cooldownExtra(p, Skill.TITAN_CLEAVE) == MythicAbilities.SKY_COOLDOWN && MythicAbilities.cooldownExtra(p, Skill.LUNGE) == 0,
                    "the sky sword cooldown drawback is wrong");

            // 羽の番人の大剣「番人の誓い」: 溜めた誓いを次の技に足して使い切る
            hold(p, DivineGearSetup.WARDEN.get());
            heal(zs);
            float before = a.getHealth();
            SkillActions.hit((net.minecraft.server.level.ServerPlayer) p, a, 1f, Skill.LUNGE);
            float plain = before - a.getHealth();
            MythicAbilities.peek(p).oath = 6;
            heal(zs);
            before = a.getHealth();
            SkillActions.hit((net.minecraft.server.level.ServerPlayer) p, a, 1f, Skill.LUNGE);
            check(f, MythicAbilities.peek(p).oath == 0 && before - a.getHealth() > plain + 2, "the warden's oath was not added: " + plain + " -> " + (before - a.getHealth()));

            // あいだの大剣「天と淵の天秤」: 体力が半分より上なら +30%
            hold(p, TowerGearSetup.SWORD.get());
            p.setHealth(p.getMaxHealth());
            check(f, Math.abs(MythicAbilities.dealt(level, p, a, melee, 5f, now) - 1.3) < 1e-9, "the balance sword high bonus is wrong");
            p.setHealth(p.getMaxHealth() * 0.4f);
            check(f, MythicAbilities.dealt(level, p, a, melee, 5f, now) == 1, "the balance sword gave the high bonus at low health");
            p.setHealth(p.getMaxHealth());

            // 七つ鐘の大鎚「七つ鐘」: 七つ目で大鐘 (まわりの敵に傷と縛り)
            hold(p, DivineGearSetup.HAMMER.get());
            heal(zs);
            for (int i = 0; i < 6; i++) {
                hitMelee(a, melee);
            }
            float hb = b.getHealth();
            hitMelee(a, melee);
            check(f, b.getHealth() < hb && MythicAbilities.peek(p).bells == 0, "the great bell did not ring");

            // 灰打ちの大鎚「溶かし打ち」: 打つと溶けが重なり、だれの攻撃も強くなる
            hold(p, DivineGearSetup.ASH.get());
            hitMelee(b, melee);
            hitMelee(b, melee);
            hold(p, net.minecraft.world.item.Items.AIR);
            heal(zs);
            before = b.getHealth();
            hitMelee(b, melee, 10f);
            check(f, before - b.getHealth() > 10.5f, "meltdown did not raise damage taken: " + (before - b.getHealth()));

            // 引き合いの鎚「引力」: 4 度目ごとに引き寄せて叩く
            hold(p, TowerGearSetup.HAMMER.get());
            heal(zs);
            for (int i = 0; i < 3; i++) {
                hitMelee(a, melee);
            }
            hb = b.getHealth();
            hitMelee(a, melee);
            check(f, b.getHealth() < hb && MythicAbilities.peek(p).pulls == 0, "gravity did not strike");

            // 暁の灯杖「命の灯」: 足りないマナを体力で払う
            hold(p, DivineGearSetup.STAFF.get());
            p.setHealth(p.getMaxHealth());
            float hp = p.getHealth();
            check(f, MythicAbilities.payWithLife(p, 10) && Math.abs(hp - p.getHealth() - 10 * MythicAbilities.LAMP_LIFE * p.getMaxHealth()) < 1e-3,
                    "lifelight did not pay with health");
            p.setHealth(1.2f);
            check(f, !MythicAbilities.payWithLife(p, 10), "lifelight paid with the last of the health");
            p.setHealth(p.getMaxHealth());

            // 落ちる月の杖の欠点 (技のマナ +20%)・側の目の弓の欠点 (貫かない)
            hold(p, TowerGearSetup.STAFF.get());
            check(f, MythicAbilities.manaScale(p, Skill.STARDUST) == MythicAbilities.MOON_MANA, "the moon staff mana drawback is wrong");
            hold(p, TowerGearSetup.BOW.get());
            check(f, MythicAbilities.noPierce(p), "the eye bow still pierces");

            // 巡礼の羽弓「羽の嵐」: 技を使うと、背の羽根が狙った敵へみな飛ぶ
            hold(p, DivineGearSetup.BOW.get());
            MythicAbilities.peek(p).feathers = 5;
            MythicAbilities.onSkillCast(p, Skill.SNARE_SHOT, 1);
            check(f, MythicAbilities.peek(p).feathers == 0, "feather storm did not launch the feathers");

            // 灯蛾の短剣「灯の粉」: 正面は −20% で粉が重なり、背から刺すと粉の数だけ強い
            hold(p, DivineGearSetup.MOTH.get());
            a.setYRot(180f);
            a.yBodyRot = 180f; // 北 (-Z) を向く = こちらを向いている
            check(f, Math.abs(MythicAbilities.dealt(level, p, a, melee, 5f, now) - MythicAbilities.MOTH_FRONT) < 1e-9, "a frontal moth hit was not weakened");
            MythicAbilities.dealt(level, p, a, melee, 5f, now);
            check(f, MythicAbilities.dust(a) == 2, "lamp dust did not stack: " + MythicAbilities.dust(a));
            a.setYRot(0f);
            a.yBodyRot = 0f; // 南を向く = 背を向けている
            check(f, Math.abs(MythicAbilities.dealt(level, p, a, melee, 5f, now) - (1 + 2 * MythicAbilities.DUST_STEP)) < 1e-9 && MythicAbilities.dust(a) == 0,
                    "a backstab did not burst the dust");

            // 追う印の短剣「追う印」: 最初の敵に印、印の敵 +40%・ほか −20%
            hold(p, TowerGearSetup.DAGGER.get());
            MythicAbilities.peek(p).sigil = -1;
            check(f, MythicAbilities.dealt(level, p, a, melee, 5f, now) == MythicAbilities.SIGIL_ON
                    && MythicAbilities.dealt(level, p, b, melee, 5f, now) == MythicAbilities.SIGIL_OFF, "the hunting sigil multipliers are wrong");

            // 約束の鈴杖の欠点・鐘守の鈴杖「沈黙の鐘」・合掌の鈴杖「合掌」
            hold(p, DivineGearSetup.BELL.get());
            check(f, MythicAbilities.dealt(level, p, a, melee, 5f, now) == MythicAbilities.PROMISE_OWN, "the promise staff damage drawback is wrong");
            hold(p, DivineGearSetup.KEEPER.get());
            for (int i = 0; i < MythicAbilities.KEEPER_EVERY; i++) {
                hitMelee(a, melee);
            }
            // 体力 1000 の試しのゾンビは「大きな相手」なので、縛られずに鈍る
            check(f, a.getAttribute(Attributes.MOVEMENT_SPEED).getValue() < a.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue() * 0.9,
                    "the bell of silence did not slow the zombie");
            hold(p, TowerGearSetup.BELL.get());
            p.setHealth(p.getMaxHealth() * 0.5f);
            for (int i = 0; i < MythicAbilities.PRAYERS; i++) {
                hitMelee(a, melee);
            }
            check(f, MythicAbilities.peek(p).clapWardUntil > now && p.getHealth() > p.getMaxHealth() * 0.7f, "joined palms did not heal and ward");
            check(f, MythicAbilities.dealt(level, p, a, melee, 5f, now) == MythicAbilities.CLAP_OWN, "the clap staff melee drawback is wrong");

            // 武器の数値の行 (WeaponLines。03 の 1.6): 階級ごとの本数、同じ品はいつも同じ行、職の武器でない品には無い
            int weapons = 0;
            for (Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
                if (!jp.revati.skyabyss.item.WeaponLines.wellFormed(item)) {
                    f.add("weapon lines are malformed for " + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item));
                }
                if (!jp.revati.skyabyss.item.WeaponLines.of(item).isEmpty()) {
                    weapons++;
                }
            }
            check(f, weapons >= 50, "too few weapons have stat lines: " + weapons);
            check(f, jp.revati.skyabyss.item.WeaponLines.of(DivineGearSetup.SWORD.get()).equals(jp.revati.skyabyss.item.WeaponLines.make0(DivineGearSetup.SWORD.get())),
                    "weapon lines are not fixed per item");
            check(f, jp.revati.skyabyss.item.WeaponLines.of(net.minecraft.world.item.Items.DIAMOND_SWORD).isEmpty(), "a vanilla sword got stat lines");

            // 目覚め (MythicAwakening): 段の境、能力と欠点の倍率、倒した数を手の品に書く
            awakening(f, level, p, zs, px, py, pz);
        } catch (Exception e) {
            f.add("threw " + e);
            SkyAbyss.LOGGER.error("SELFTEST mythic threw", e);
        } finally {
            for (Zombie z : zs) {
                z.discard();
            }
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST mythic PASSED (18 mythic weapons: abilities, drawbacks, releases; awakening steps, boosts, halved drawbacks, kill counting)");
        } else {
            f.forEach(s -> SkyAbyss.LOGGER.error("SELFTEST mythic FAILED: {}", s));
        }
    }

    private static void awakening(List<String> f, ServerLevel level, FakePlayer p, List<Zombie> zs, double px, double py, double pz) {
        check(f, MythicAwakening.items().size() == 18, "there should be 18 artifacts: " + MythicAwakening.items().size());
        check(f, MythicAwakening.stage(299) == 0 && MythicAwakening.stage(300) == 1 && MythicAwakening.stage(1499) == 1 && MythicAwakening.stage(1500) == 2
                && MythicAwakening.stage(5000) == 3, "awakening steps should be 300 / 1,500 / 5,000");
        ItemStack sword = new ItemStack(DivineGearSetup.SWORD.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, sword);
        check(f, MythicAwakening.boost(p) == 1 && MythicAwakening.drawback(p) == 1 && MythicAwakening.toNext(sword) == 300, "a fresh artifact should not be awakened");
        MythicAwakening.add(null, sword, 300);
        check(f, Math.abs(MythicAwakening.boost(p) - 1.15) < 1e-9 && MythicAwakening.drawback(p) == 1, "stage 1 should give +15% and keep the drawback");
        MythicAwakening.add(null, sword, 1200);
        check(f, MythicAwakening.drawback(p) == 0.5 && MythicAbilities.cooldownExtra(p, Skill.TITAN_CLEAVE) == MythicAbilities.SKY_COOLDOWN / 2,
                "stage 2 should halve the drawback (titan cleave +1 s)");
        MythicAwakening.add(null, sword, 3500);
        check(f, MythicAwakening.stage(sword) == 3 && Math.abs(MythicAwakening.boost(p) - 1.30) < 1e-9 && MythicAwakening.toNext(sword) == 0, "stage 3 should give +30%");
        // 側の目の弓: 段 2 から雲穿ちが半分まで貫く
        ItemStack bow = new ItemStack(TowerGearSetup.BOW.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, bow);
        check(f, MythicAbilities.pierceLimit(p, 4) == 0, "the eye bow should not pierce before awakening");
        MythicAwakening.add(null, bow, 1500);
        check(f, MythicAbilities.pierceLimit(p, 4) == 2 && !MythicAbilities.noPierce(p), "an awakened eye bow should pierce half");
        // 倒した数: 神器で傷を入れた敵が倒れると、手の品に 1 (大きな相手は 25)
        ItemStack fresh = new ItemStack(DivineGearSetup.HAMMER.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, fresh);
        Zombie z = zombie(level, px, py, pz + 2.0, zs);
        z.invulnerableTime = 0;
        z.hurt(level.damageSources().playerAttack(p), 2f);
        z.invulnerableTime = 0;
        z.hurt(level.damageSources().generic(), 1000f);
        int worth = z.getMaxHealth() >= MythicAwakening.BOSS_HEALTH ? MythicAwakening.BOSS_WORTH : 1;
        check(f, MythicAwakening.kills(p.getMainHandItem()) == worth, "a foe hurt with the artifact should count when it dies (" + worth + "): " + MythicAwakening.kills(p.getMainHandItem()));
    }

    private static void hold(FakePlayer p, Item item) {
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
    }

    private static void hitMelee(Zombie z, DamageSource source) {
        hitMelee(z, source, 1f);
    }

    private static void hitMelee(Zombie z, DamageSource source, float amount) {
        z.invulnerableTime = 0;
        z.hurt(source, amount);
        z.invulnerableTime = 0;
    }

    private static Zombie zombie(ServerLevel level, double x, double y, double z, List<Zombie> out) {
        Zombie zb = new Zombie(EntityType.ZOMBIE, level);
        zb.moveTo(x, y, z, 180f, 0f);
        zb.setNoAi(true);
        zb.setNoGravity(true);
        zb.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        zb.setHealth(1000);
        level.addFreshEntity(zb);
        out.add(zb);
        return zb;
    }

    private static void heal(List<Zombie> zs) {
        for (Zombie z : zs) {
            z.setHealth(z.getMaxHealth());
            z.invulnerableTime = 0;
        }
    }
}
