package jp.revati.skyabyss.rpg.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.Growth;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayer;

// 職 (docs/design/43) の検査。SKYABYSS_SELFTEST_ONLY="jobs"。
//   武器 → 職 (この作品の武器とバニラの品。鐘銭・お守りは職にならない)・職ごとの点 (ほかの職の木の点は数えない)・
//   持ち替えで枠が入れ替わる・職の武器を持たないと技は使えない・旅人と橋の木は閉じた・天職と刻み目は削った
public final class JobSelfTest {
    private JobSelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        return List.of(SelfTestRunner.Step.once("jobs", JobSelfTest::run));
    }

    static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    }

    // ---- 新しい技を実際に使う (docs/design/43 の 4 段目): 魔法の傷が当たる・玉が飛んで弾ける・鈍足・転移・誓い・心得・踏み・霧・呪い・灯
    private static void spells(MinecraftServer server, List<String> f) {
        net.minecraft.server.level.ServerLevel level = server.overworld();
        net.minecraft.core.BlockPos spawn = level.getSharedSpawnPos();
        // 出現点の近く (いつも読み込まれている。遠いとゾンビが世界に入らない)
        double px = spawn.getX() + 6.5;
        double py = 300;
        double pz = spawn.getZ() + 0.5;
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "jobs_mage_t"));
        p.moveTo(px, py, pz, 0f, 0f); // 南 (+Z) を向く
        SkillManager.forgetAll(p);
        Growth.get(p).level = 30;
        SkillData d = SkillManager.get(p);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(JobChoice.STAFF.get()));
        check(f, Job.held(p) == Job.MAGE && JobChoice.firstSkill(Job.MAGE) == Skill.THUNDER_BOLT, "the staff is not a mage weapon, or the mage's first skill is wrong");
        for (Job j : Job.values()) {
            long n = Skill.inTree(j.tree).stream().filter(sk -> sk.active).count();
            check(f, n >= 4, j + " has fewer than 4 active skills: " + n);
        }
        long actives = Skill.inTree(SkillTree.STAR).stream().filter(sk -> sk.active).count();
        check(f, actives == 4 && d.canLearn(Skill.THUNDER_BOLT, 30) == SkillData.LearnResult.OK, "the mage tree should have 4 open active skills: " + actives);
        net.minecraft.world.entity.monster.Zombie z = new net.minecraft.world.entity.monster.Zombie(net.minecraft.world.entity.EntityType.ZOMBIE, level);
        z.moveTo(px, py, pz + 6, 180f, 0f);
        z.setNoAi(true);
        z.setNoGravity(true);
        level.addFreshEntity(z);
        try {
            // 魔法の傷: 杖の攻撃力 5 × 倍率。精神で伸びる
            float plain = JobActions.spell(p, 1.0);
            check(f, Math.abs(plain - 5.0f) < 0.01f, "spell base with the shrine staff should be 5: " + plain);
            Growth.get(p).stats[jp.revati.skyabyss.rpg.GrowthRules.Stat.SPIRIT.ordinal()] = 50;
            check(f, JobActions.spell(p, 1.0) > plain * 1.29f, "spirit does not raise spell damage");
            Growth.get(p).stats[jp.revati.skyabyss.rpg.GrowthRules.Stat.SPIRIT.ordinal()] = 0;
            // 雷の矢
            z.setHealth(z.getMaxHealth());
            JobActions.perform(p, Skill.THUNDER_BOLT, 1);
            check(f, z.getHealth() < z.getMaxHealth(), "thunder bolt did not hit the zombie ahead");
            // 熾火の玉: 飛んで、当たって弾ける (燃える)
            z.setHealth(z.getMaxHealth());
            z.clearFire();
            JobActions.perform(p, Skill.EMBER_ORB, 1);
            for (int i = 0; i < 40 && JobActions.orbsInFlight() > 0; i++) {
                JobActions.tick(server);
            }
            check(f, JobActions.orbsInFlight() == 0 && z.getHealth() < z.getMaxHealth() && z.getRemainingFireTicks() > 0,
                    "the ember orb did not fly, burst and burn: hp " + z.getHealth() + " fire " + z.getRemainingFireTicks());
            // 凍て潮: 近くの敵に傷と鈍足
            z.moveTo(px, py, pz + 3, 180f, 0f);
            z.setHealth(z.getMaxHealth());
            double speed = z.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            JobActions.perform(p, Skill.FROST_TIDE, 1);
            check(f, z.getHealth() < z.getMaxHealth() && z.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) < speed * 0.6,
                    "frost tide did not hurt and slow the zombie");
            // 呪いの印: 見ている敵に印
            JobActions.perform(p, Skill.CURSE_MARK, 1);
            check(f, SkillEvents.marked(z, level.getGameTime()), "curse mark did not mark the zombie");
            // 炉の踏み: まわりの敵に傷と炎
            z.setHealth(z.getMaxHealth());
            z.clearFire();
            z.moveTo(px + 1.5, py, pz, 180f, 0f);
            JobActions.perform(p, Skill.FORGE_STOMP, 1);
            check(f, z.getRemainingFireTicks() > 0, "forge stomp did not burn the zombie beside");
            // 星の誓い: 星読みの技のマナ +25%
            int cost = SkillManager.manaCostFor(p, Skill.THUNDER_BOLT);
            SkillManager.forceRank(p, Skill.STAR_OATH, 1);
            check(f, SkillManager.manaCostFor(p, Skill.THUNDER_BOLT) == Math.round(cost * 1.25f) && JobActions.spell(p, 1.0) > plain * 1.2f,
                    "star oath did not raise the star cost and power");
            // 星読みの心得: 杖を持つとマナの戻りが速い
            SkillManager.forceRank(p, Skill.STAR_READING, 3);
            d.mana = 0;
            d.lastCombat = level.getGameTime();
            for (int i = 0; i < 20; i++) {
                SkillManager.regenTick(p, d, level.getGameTime());
            }
            check(f, d.mana > 0.8f * 1.4f, "star reading did not speed mana up with a staff: " + d.mana);
            // 霧隠れ: 消える
            JobActions.perform(p, Skill.MIST_VEIL, 1);
            check(f, JobActions.veiled(p) && p.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY), "mist veil did not hide the player");
            // 灯の守り札: 自分に護りの体力
            p.setAbsorptionAmount(0);
            JobActions.perform(p, Skill.LAMP_CHARM, 1);
            check(f, p.getAbsorptionAmount() >= SkillRules.lampAbsorb(1) - 0.01f, "lamp charm gave no ward HP: " + p.getAbsorptionAmount());
            // 転移: 前へ跳ぶ
            z.discard();
            double before = p.getZ();
            String r = JobActions.perform(p, Skill.BLINK, 1);
            check(f, r == null && p.getZ() - before > SkillRules.blinkRange(1) - 1.5, "blink did not move the player forward: " + r + " moved " + (p.getZ() - before));
        } finally {
            z.discard();
            JobActions.clear();
            SkillEffects.clear();
            SkillManager.forgetAll(p);
            p.discard();
        }
    }

    private static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        // ---- 武器 → 職
        Object[][] table = {
                {"skyabyss:whalebone_greatsword", Job.WARRIOR}, {"skyabyss:captain_pike", Job.WARRIOR}, {"skyabyss:coral_harpoon", Job.WARRIOR},
                {"skyabyss:warden_glaive", Job.WARRIOR}, {"skyabyss:coral_shield", Job.WARRIOR}, {"skyabyss:abyss_lamp_sword", Job.WARRIOR},
                {"skyabyss:smith_hammer", Job.SMITH}, {"skyabyss:ember_war_axe", Job.SMITH}, {"skyabyss:thrall_poker", Job.SMITH},
                {"skyabyss:waterway_fang", Job.ASSASSIN}, {"skyabyss:tidecaller_claw", Job.ASSASSIN}, {"skyabyss:raiju_claw", Job.ASSASSIN},
                {"skyabyss:abyss_pulse_dagger", Job.ASSASSIN}, {"skyabyss:thunder_wing_bow", Job.ARCHER}, {"skyabyss:abyss_storm_bow", Job.ARCHER},
                {"skyabyss:abyss_bell_staff", Job.SHAMAN},
                {"skyabyss:shrine_staff", Job.MAGE}, {"skyabyss:shrine_bell_staff", Job.SHAMAN}, {"skyabyss:shrine_dagger", Job.ASSASSIN},
                {"skyabyss:whale_staff", Job.MAGE}, {"skyabyss:whale_bell_staff", Job.SHAMAN}, {"skyabyss:tide_staff", Job.MAGE},
                {"skyabyss:tide_bell_staff", Job.SHAMAN}, {"skyabyss:storm_staff", Job.MAGE}, {"skyabyss:storm_bell_staff", Job.SHAMAN},
                {"skyabyss:ember_staff", Job.MAGE}, {"skyabyss:ember_censer", Job.SHAMAN},
                {"skyabyss:whale_hammer", Job.SMITH}, {"skyabyss:whale_bow", Job.ARCHER}, {"skyabyss:tide_hammer", Job.SMITH}, {"skyabyss:tide_bow", Job.ARCHER},
                {"skyabyss:storm_hammer", Job.SMITH}, {"skyabyss:ember_greatsword", Job.WARRIOR}, {"skyabyss:ember_bow", Job.ARCHER},
                {"skyabyss:ember_dagger", Job.ASSASSIN}, {"skyabyss:whale_dagger", Job.ASSASSIN}, {"skyabyss:tide_dagger", Job.ASSASSIN}, {"skyabyss:storm_sword", Job.WARRIOR}, {"skyabyss:storm_dagger", Job.ASSASSIN}, {"skyabyss:shrine_sword", Job.WARRIOR}, {"skyabyss:shrine_hammer", Job.SMITH},
                {"skyabyss:shrine_bow", Job.ARCHER},
                {"skyabyss:abyss_sinking_anchor", Job.SMITH}, {"skyabyss:abyss_ash_scythe", Job.SMITH},
                {"minecraft:iron_sword", Job.WARRIOR}, {"minecraft:trident", Job.WARRIOR}, {"minecraft:iron_axe", Job.SMITH},
                {"minecraft:bow", Job.ARCHER}, {"minecraft:crossbow", Job.ARCHER},
                {"skyabyss:bell_coin", null}, {"skyabyss:storm_charm", null}, {"minecraft:lightning_rod", null}, {"minecraft:blaze_rod", null},
                {"minecraft:bread", null}};
        for (Object[] row : table) {
            Item it = item((String) row[0]);
            if (it == Items.AIR) {
                f.add("missing item " + row[0]);
                continue;
            }
            check(f, Job.of(it) == row[1], row[0] + " is " + Job.of(it) + ", expected " + row[1]);
        }
        for (Job j : Job.values()) {
            check(f, Job.ofTree(j.tree) == j, "tree of " + j + " does not map back");
        }
        // 木の印は、その武器の職の木の印だけ (魔法使いの杖には出ない)
        net.minecraft.util.RandomSource rnd = net.minecraft.util.RandomSource.create(5);
        java.util.Set<jp.revati.skyabyss.item.affix.AffixType> marks = new java.util.HashSet<>();
        java.util.Set<jp.revati.skyabyss.item.affix.AffixType> staffMarks = new java.util.HashSet<>();
        for (int i = 0; i < 400; i++) {
            for (jp.revati.skyabyss.item.affix.Affix a : jp.revati.skyabyss.item.affix.AffixRules.roll(new ItemStack(item("skyabyss:whalebone_greatsword")), rnd)) {
                if (a.kind().isMark()) {
                    marks.add(a.kind());
                }
            }
            for (jp.revati.skyabyss.item.affix.Affix a : jp.revati.skyabyss.item.affix.AffixRules.roll(new ItemStack(item("skyabyss:whale_staff")), rnd)) {
                if (a.kind().isMark()) {
                    staffMarks.add(a.kind());
                }
            }
        }
        check(f, marks.equals(java.util.Set.of(jp.revati.skyabyss.item.affix.AffixType.MARK_STONE)) && staffMarks.isEmpty(),
                "tree marks should follow the weapon's job: greatsword " + marks + ", staff " + staffMarks);
        // ---- 全部の品: 武器はどれも一つの職 (持つとその職)。防具・装飾品・素材は職と関係ない (誰でも着られる)
        java.util.Map<String, java.util.EnumSet<Job>> byOrigin = new java.util.TreeMap<>();
        for (Item it : BuiltInRegistries.ITEM) {
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(it);
            if (!key.getNamespace().equals(SkyAbyss.MODID)) {
                continue;
            }
            boolean weapon = it instanceof net.minecraft.world.item.SwordItem || it instanceof net.minecraft.world.item.BowItem
                    || it instanceof net.minecraft.world.item.CrossbowItem || it instanceof net.minecraft.world.item.TridentItem
                    || it instanceof net.minecraft.world.item.ShieldItem;
            boolean wearable = it instanceof net.minecraft.world.item.ArmorItem || it instanceof jp.revati.skyabyss.item.accessory.Accessory;
            Job job = Job.of(it);
            if (weapon) {
                check(f, job != null, "weapon " + key + " belongs to no job");
                if (job != null && it instanceof jp.revati.skyabyss.item.ModGear g) {
                    String origin = key.getPath().startsWith("shrine_") ? "island_shrine" : g.dungeon();
                    byOrigin.computeIfAbsent(origin, o -> java.util.EnumSet.noneOf(Job.class)).add(job);
                }
            } else if (wearable) {
                check(f, job == null, "wearable " + key + " was mapped to a job (" + job + ")");
            }
        }
        // 祠と一〜四章は 6 職すべてに専用の武器 (43 の 8 節)
        for (String origin : List.of("island_shrine", "stone_whale", "tide_shell", "thunder_wing", "ember", "abyss")) {
            java.util.EnumSet<Job> got = byOrigin.getOrDefault(origin, java.util.EnumSet.noneOf(Job.class));
            check(f, got.size() == Job.values().length, origin + " lacks weapons for " + java.util.EnumSet.complementOf(got));
        }
        byOrigin.forEach((o, js) -> SkyAbyss.LOGGER.info("[selftest] jobs: {} weapons cover {}", o, js));
        // ---- 職ごとの点・枠・使う
        FakePlayer p = new FakePlayer(server.overworld(), new GameProfile(UUID.randomUUID(), "jobs_t"));
        SkillData d = SkillManager.get(p);
        SkillManager.forgetAll(p);
        Growth.get(p).level = 10;
        int total = d.totalPoints(10);
        SkillManager.forceRank(p, Skill.TITAN_CLEAVE, 3);
        SkillManager.forceRank(p, Skill.RIPOSTE, 2);
        check(f, d.unspentFor(SkillTree.ROCK, 10) == total - 5, "warrior points are wrong: " + d.unspentFor(SkillTree.ROCK, 10));
        check(f, d.unspentFor(SkillTree.BONE, 10) == total, "the warrior's points were taken from the archer: " + d.unspentFor(SkillTree.BONE, 10));
        SkillManager.forceRank(p, Skill.CLOUD_PIERCE, 1);
        check(f, d.unspentFor(SkillTree.BONE, 10) == total - 1 && d.unspentFor(SkillTree.ROCK, 10) == total - 5, "job points mixed: total " + total
                + " bone " + d.unspentFor(SkillTree.BONE, 10) + " rock " + d.unspentFor(SkillTree.ROCK, 10) + " ranks " + d.ranks + " runes " + d.runes);
        check(f, d.canLearn(Skill.SIGNAL, 50) == SkillData.LearnResult.SEALED && d.canLearn(Skill.DUST_CHARGE, 50) == SkillData.LearnResult.SEALED,
                "the traveler / bridge trees are still open");
        check(f, !SkillRules.CALLINGS && !Breakpoints.reached(30, 15), "callings or breakpoints are still on");
        // 持ち替え: 剣 → 戦士の枠、弓 → 弓使いの枠
        d.useJob(null);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        check(f, Job.held(p) == Job.WARRIOR, "holding a sword is not a warrior");
        d.useJob(Job.held(p));
        check(f, Skill.TITAN_CLEAVE.id.equals(d.slots[0]), "the warrior bar did not fill: " + String.join(",", d.slots));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
        d.useJob(Job.held(p));
        check(f, Skill.CLOUD_PIERCE.id.equals(d.slots[0]) && !java.util.Arrays.asList(d.slots).contains(Skill.TITAN_CLEAVE.id),
                "the archer bar is wrong: " + String.join(",", d.slots));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        d.useJob(Job.held(p));
        check(f, Skill.TITAN_CLEAVE.id.equals(d.slots[0]), "the warrior bar was not restored");
        // 職の武器を持たないと使えない
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BREAD));
        d.mana = 100;
        d.cooldowns.clear();
        d.globalCooldown = 0;
        check(f, SkillManager.cast(p, 0) == SkillManager.CastResult.EMPTY, "a skill was cast without a job weapon");
        // 保存と読み直しで職の枠が残る
        net.minecraft.nbt.CompoundTag nbt = d.serializeNBT(server.registryAccess());
        SkillData back = new SkillData();
        back.deserializeNBT(server.registryAccess(), nbt);
        check(f, back.jobBars.containsKey("warrior") && back.jobBars.containsKey("archer"), "job bars were not saved: " + back.jobBars.keySet());
        // 画面 (K) はいまの職を送られた中身で見る。型の箱は職ごとに 2 つ
        net.minecraft.network.RegistryFriendlyByteBuf buf = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), server.registryAccess());
        SkillData.STREAM_CODEC.encode(buf, d);
        check(f, "warrior".equals(SkillData.STREAM_CODEC.decode(buf).curJob), "the current job was not sent to the client");
        check(f, SkillData.loadoutIndex(Job.ARCHER, 1) == 7 && d.loadouts.length == 12, "job loadouts are not two per job");
        SkillManager.forgetAll(p);
        p.discard();
        spells(server, f);
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST jobs PASSED (weapon to job for {} items, per-job points, bar swap on weapon change, no skills without a job weapon, closed trees, save, mage tree and the four new job skills cast)", table.length);
        } else {
            f.forEach(s -> SkyAbyss.LOGGER.error("SELFTEST jobs FAILED: {}", s));
        }
    }
}
