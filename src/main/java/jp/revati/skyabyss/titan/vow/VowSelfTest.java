package jp.revati.skyabyss.titan.vow;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.party.PartyData;
import jp.revati.skyabyss.pouch.Pouch;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.TitanStay;
import jp.revati.skyabyss.titan.dungeon.BackWhispers;
import jp.revati.skyabyss.titan.dungeon.CampBlocks;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.FirstRun;
import jp.revati.skyabyss.titan.group.FirstRunData;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.titan.TitanTravel;
import jp.revati.skyabyss.titan.tide.mob.TideMobSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

// 誓い (Vow) と、居座りの早じまい (TitanStay.Vacancy) の自己テスト。段の名前は "vow" と "stay leave"。
//  vow        : 登録・段・倍率・称号・予告の短縮 (最短 0.8 秒)・選べる条件 (周回だけ・試練の前まで・3 つまで)・パーティで揃う・
//               体力と傷と野営禁止の効き・時間制限・報酬 (素材・共鳴の欠片・最高の段・降ろす)・周回の囁きの切り替え
//  stay leave : 無人の連続 3 分で去る・戻れば数え直し・背中にグループがいれば数えない・範囲 (160)・観戦者
public final class VowSelfTest {
    private static final String WHALE = Titans.STONE_WHALE;

    private VowSelfTest() {
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    private static FakePlayer player(ServerLevel level, String name) {
        return new FakePlayer(level, new GameProfile(UUID.randomUUID(), name));
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 0.001f;
    }

    // 敵を入れる場所のチャンクを読み込んでおく (エンティティが入るのは、チャンクが全部読めてから)
    public static void load(MinecraftServer server) {
        ServerLevel back = server.getLevel(TitanTravel.TITAN_BACK);
        if (back != null) {
            back.setChunkForced(6000 >> 4, 6000 >> 4, true);
        }
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        PartyData parties = PartyData.get(server);
        var snapshot = parties.snapshot(server.registryAccess());
        ServerLevel back = server.getLevel(TitanTravel.TITAN_BACK);
        List<UUID> groups = new ArrayList<>();
        FakePlayer a = null;
        FakePlayer b = null;
        FakePlayer c = null;
        List<net.minecraft.world.entity.Entity> made = new ArrayList<>();
        try {
            if (back == null) {
                f.add("titan_back dimension is missing");
            } else {
                parties.clearAll();
                a = player(back, "vow_a");
                b = player(back, "vow_b");
                c = player(back, "vow_c");
                UUID ga = a.getUUID();
                UUID gb = b.getUUID();
                UUID gc = c.getUUID();
                groups.addAll(List.of(ga, gb, gc));
                BlockPos at = new BlockPos(6000, 100, 6000);
                back.getChunk(at);
                for (FakePlayer p : List.of(a, b, c)) {
                    p.moveTo(at.getX(), at.getY(), at.getZ(), 0, 0);
                    check(f, back.addFreshEntity(p), "could not add the fake player " + p.getGameProfile().getName());
                }

                // ---- 登録・段・倍率・称号
                check(f, Vows.all().size() >= 5 && Vows.get("hardy") != null && Vows.get("sharp") != null && Vows.get("thin") != null
                        && Vows.get("bare") != null && Vows.get("hasty") != null, "the stock vows are not registered");
                List<Vow> heavy = List.of(Vows.get("hardy"), Vows.get("sharp"), Vows.get("thin"));
                check(f, Vows.level(heavy) == 6 && Vows.level(List.of()) == 0, "vow tier is not the sum of the points");
                check(f, near(Vows.multiplier(0), 1f) && near(Vows.multiplier(6), 2.2f), "the material multiplier is not 1 + 0.2 per tier");
                check(f, Vows.bonusMaterials(0) == 0 && Vows.bonusMaterials(1) == 2 && Vows.bonusMaterials(6) == 12, "the bonus material count is off");
                check(f, near(Vows.shardChance(6), 0.48f) && near(Vows.shardChance(0), 0f), "the shard chance is off");
                int[] tiers = {Vows.titleTier(0), Vows.titleTier(1), Vows.titleTier(2), Vows.titleTier(3), Vows.titleTier(4), Vows.titleTier(5), Vows.titleTier(6)};
                check(f, java.util.Arrays.equals(tiers, new int[] {0, 1, 1, 2, 2, 3, 4}), "title tiers are off: " + java.util.Arrays.toString(tiers));
                Vows.register(new Vow("selftest_vow", 3, 0f, 0.1f, 0f, false, 0));
                check(f, Vows.get("selftest_vow") != null && Vows.all().stream().anyMatch(v -> v.id().equals("selftest_vow")), "a custom vow was not registered");
                // 予告の短縮: 最短 0.8 秒 (16 tick) は割れない
                check(f, Vows.scaleWarn(List.of(Vows.get("sharp")), 40) == 30 && Vows.scaleWarn(List.of(), 40) == 40, "the sharp vow does not scale the warning");
                check(f, Vows.scaleWarn(List.of(Vows.get("selftest_vow")), 40) == Telegraph.MIN_WARN_TICKS
                        && Vows.scaleWarn(List.of(Vows.get("sharp")), 20) == Telegraph.MIN_WARN_TICKS, "the shortened warning went under 0.8 seconds");
                Vows.unregister("selftest_vow");
                check(f, Vows.get("selftest_vow") == null, "a custom vow was not unregistered");

                // ---- 選べる条件: 周回だけ・試練の前まで・3 つまで
                check(f, Vows.add(server, ga, WHALE, "hardy") == Vows.Result.NOT_REPEAT, "a first-run group could swear a vow");
                FirstRun.onFirstCleared(back, List.of(ga));
                check(f, Vows.canChange(server, ga, WHALE) == Vows.Result.OK && Vows.canChange(server, gb, WHALE) == Vows.Result.NOT_REPEAT,
                        "vows are not open to exactly the cleared group");
                check(f, Vows.add(server, ga, WHALE, "nope") == Vows.Result.UNKNOWN, "an unknown vow was accepted");
                check(f, Vows.add(server, ga, WHALE, "hardy") == Vows.Result.OK && Vows.add(server, ga, WHALE, "hardy") == Vows.Result.ALREADY, "add / already");
                check(f, Vows.add(server, ga, WHALE, "sharp") == Vows.Result.OK && Vows.add(server, ga, WHALE, "thin") == Vows.Result.OK, "adding the 2nd and 3rd vow");
                check(f, Vows.add(server, ga, WHALE, "bare") == Vows.Result.FULL && Vows.active(back, ga).size() == 3, "a fourth vow was accepted");
                check(f, Vows.remove(server, ga, WHALE, "sharp") == Vows.Result.OK && Vows.remove(server, ga, WHALE, "sharp") == Vows.Result.NOT_SET
                        && Vows.level(Vows.active(back, ga)) == 4, "remove / not set");
                check(f, Vows.active(back, gb).isEmpty() && Vows.active(back, gc).isEmpty(), "vows leaked to another group");
                check(f, BackGroupData.get(back).of(ga).has("keep.vows"), "the vows are not stored under keep.");
                // 試練のリセットでは残る。試練が進むと変えられない
                BackGroupData.get(back).of(ga).resetProgress();
                check(f, Vows.active(back, ga).size() == 2, "the trial reset erased the vows");
                BackGroupData.get(back).of(ga).markHallCleared("selftest_hall");
                check(f, Vows.canChange(server, ga, WHALE) == Vows.Result.STARTED && Vows.add(server, ga, WHALE, "bare") == Vows.Result.STARTED
                        && Vows.clear(server, ga, WHALE) == Vows.Result.STARTED && Vows.active(back, ga).size() == 2, "vows changed after the trials started");
                BackGroupData.get(back).of(ga).resetProgress();
                check(f, Vows.clear(server, ga, WHALE) == Vows.Result.OK && Vows.active(back, ga).isEmpty(), "clear did not drop the vows");

                // ---- パーティで揃う: 周回のパーティは記録ごと写る。全員の帯 (snapshot) が同じ
                check(f, Vows.add(server, ga, WHALE, "hardy") == Vows.Result.OK && Vows.add(server, ga, WHALE, "thin") == Vows.Result.OK, "party setup");
                PartyData.Party party = parties.create(ga, "vow_a");
                groups.add(party.id);
                parties.add(party, gb, "vow_b");
                check(f, Vows.active(back, party.id).size() == 2, "the party did not inherit the leader's vows");
                VowNetwork.Sync sa = VowNetwork.snapshot(a);
                VowNetwork.Sync sb = VowNetwork.snapshot(b);
                check(f, sa.equals(sb) && sa.titan().equals(WHALE) && sa.ids().equals(List.of("hardy", "thin")) && sa.level() == 4 && sa.timerSec() == -1,
                        "party members do not see the same vows in the band: " + sa + " / " + sb);
                check(f, VowNetwork.snapshot(c).ids().isEmpty(), "a non-member sees the party's vows");
                parties.remove(gb);
                parties.remove(ga);
                // 抜けた人の記録はパーティの写し (誓いも写る)。ここから個人の検査に戻るので、みな外す
                check(f, Vows.active(back, ga).size() == 2 && Vows.active(back, gb).size() == 2, "leaving the party did not keep a copy of its vows");
                Vows.clear(server, party.id, WHALE);
                Vows.clear(server, ga, WHALE);
                Vows.clear(server, gb, WHALE);

                // ---- 効き: 体力
                Mob thrall = TideMobSetup.CORAL_THRALL.get().create(back);
                Mob other = TideMobSetup.CORAL_THRALL.get().create(back);
                check(f, thrall instanceof jp.revati.skyabyss.titan.group.GroupOwned && other != null, "could not make group-owned test mobs");
                if (thrall instanceof jp.revati.skyabyss.titan.group.GroupOwned go1 && other instanceof jp.revati.skyabyss.titan.group.GroupOwned go2) {
                    jp.revati.skyabyss.util.SpawnSafety.testFloor(back, at, 3, 1); // 床のない所には敵が湧かない決まり (SpawnSafety)
                    made.add(thrall);
                    made.add(other);
                    thrall.moveTo(at.getX() + 2, at.getY(), at.getZ(), 0, 0);
                    other.moveTo(at.getX() - 2, at.getY(), at.getZ(), 0, 0);
                    go1.setGroupId(ga);
                    go2.setGroupId(gb);
                    check(f, back.addFreshEntity(thrall) && back.addFreshEntity(other), "could not add the test mobs");
                    boolean seen = false;
                    for (net.minecraft.world.entity.Entity e : back.getAllEntities()) {
                        seen |= e == thrall;
                    }
                    check(f, seen, "the test mob is not among the level's entities (removed " + thrall.getRemovalReason() + ", byUuid " + (back.getEntity(thrall.getUUID()) != null)
                            + ", all " + java.util.stream.StreamSupport.stream(back.getAllEntities().spliterator(), false).map(e -> e.getType().toShortString()).toList() + ")");
                    float base = thrall.getMaxHealth();
                    VowEvents.applyFoeHealth(back);
                    check(f, near(thrall.getMaxHealth(), base) && near(other.getMaxHealth(), base), "foes grew without a vow");
                    Vows.add(server, ga, WHALE, "hardy");
                    thrall.setHealth(base / 2f);
                    int grown = VowEvents.applyFoeHealth(back);
                    check(f, near(thrall.getMaxHealth(), base * 1.3f), "the hardy vow did not add 30% health (" + thrall.getMaxHealth() + " vs " + base + ", grown "
                            + grown + ", vows " + Vows.active(back, ga) + ", id " + ((jp.revati.skyabyss.titan.group.GroupOwned) thrall).groupId() + " vs " + ga + ")");
                    check(f, near(thrall.getHealth(), base * 1.3f / 2f), "health was not kept in proportion");
                    check(f, near(other.getMaxHealth(), base), "another group's foe grew");
                    VowEvents.applyFoeHealth(back);
                    check(f, near(thrall.getMaxHealth(), base * 1.3f), "the health bonus stacked");
                }
                // 体力の部品そのもの (誰の敵でなくても)
                net.minecraft.world.entity.monster.Zombie z = EntityType.ZOMBIE.create(back);
                made.add(z);
                float zb = z.getMaxHealth();
                check(f, VowEvents.applyFoeHealth(z, 0.3f) && near(z.getMaxHealth(), zb * 1.3f) && !VowEvents.applyFoeHealth(z, 0.3f), "applyFoeHealth is not once-only");

                // ---- 効き: 予告 (Telegraph は持ち主のグループの誓いを読む。最短は割れない)
                if (thrall instanceof jp.revati.skyabyss.titan.group.GroupOwned) {
                    Vows.clear(server, ga, WHALE);
                    Vows.add(server, ga, WHALE, "sharp");
                    Telegraph t1 = Telegraph.in(back, TelegraphShape.circle(at.getX(), at.getZ(), 3)).floor(at.getY()).warn(40).owner(thrall).start();
                    check(f, t1.warnTicks() == 30, "the telegraph was not shortened by the sharp vow (" + t1.warnTicks() + ")");
                    Telegraph t2 = Telegraph.in(back, TelegraphShape.circle(at.getX(), at.getZ(), 3)).floor(at.getY()).warn(18).owner(thrall).start();
                    check(f, t2.warnTicks() == Telegraph.MIN_WARN_TICKS, "the telegraph went under 0.8 seconds (" + t2.warnTicks() + ")");
                    Telegraph t3 = Telegraph.in(back, TelegraphShape.circle(at.getX(), at.getZ(), 3)).floor(at.getY()).warn(40).owner(other).start();
                    check(f, t3.warnTicks() == 40, "another group's telegraph was shortened");
                    Telegraph.cancelAll(thrall);
                    Telegraph.cancelAll(other);
                    Vows.clear(server, ga, WHALE);
                }

                // ---- 効き: 受ける傷 (薄き守り) と野営禁止 (素手の旅)
                Vows.add(server, ga, WHALE, "thin");
                Vows.add(server, ga, WHALE, "bare");
                check(f, near(VowEvents.damageTaken(Vows.activeFor(a)), 0.25f) && VowEvents.damageTaken(Vows.activeFor(b)) == 0f, "damageTaken is not per group");
                a.setHealth(20f);
                LivingIncomingDamageEvent ev = new LivingIncomingDamageEvent(a, new DamageContainer(back.damageSources().generic(), 4f));
                NeoForge.EVENT_BUS.post(ev);
                check(f, near(ev.getAmount(), 5f), "the thin vow did not add 25% damage (" + ev.getAmount() + ")");
                LivingIncomingDamageEvent ev2 = new LivingIncomingDamageEvent(b, new DamageContainer(back.damageSources().generic(), 4f));
                NeoForge.EVENT_BUS.post(ev2);
                check(f, near(ev2.getAmount(), 4f), "another group's damage was changed");
                check(f, VowEvents.noCamp(Vows.activeFor(a)) && !VowEvents.noCamp(Vows.activeFor(b)) && CampBlocks.isCamp(Blocks.CRAFTING_TABLE.defaultBlockState())
                        && !CampBlocks.isCamp(Blocks.STONE.defaultBlockState()), "the bare vow does not forbid camp tools for exactly that group");
                Vows.clear(server, ga, WHALE);

                // ---- 時間制限 (急ぐ足)
                Vows.add(server, ga, WHALE, "hasty");
                Vows.add(server, ga, WHALE, "hardy");
                check(f, Vows.timerRemaining(back, ga, 1000) == -1, "the timer ran before anyone entered");
                Vows.startTimerIfNeeded(back, ga, 1000);
                Vows.startTimerIfNeeded(back, ga, 5000);
                int limit = Vows.get("hasty").timerTicks();
                check(f, Vows.timerRemaining(back, ga, 1600) == limit - 600, "the timer does not count from the first entry");
                check(f, Vows.effective(back, ga, 1600).size() == 2 && Vows.level(Vows.effective(back, ga, 1600)) == 3, "an unexpired timer dropped a vow");
                check(f, Vows.timerRemaining(back, ga, 1000 + limit + 5) == 0 && Vows.effective(back, ga, 1000 + limit + 5).size() == 1
                        && Vows.level(Vows.effective(back, ga, 1000 + limit + 5)) == 2, "a broken timer still counts its vow");
                check(f, VowNetwork.snapshot(a).timerSec() >= 0, "the band does not carry the timer");
                Vows.clear(server, ga, WHALE);
                check(f, !BackGroupData.get(back).of(ga).has("keep.vow_start"), "the timer start survived clear");

                // ---- 報酬: 素材の上乗せ・共鳴の欠片・最高の段・降ろす
                var store = FirstRunData.get(server);
                store.forgetVowBest(a.getUUID());
                var shard = Vows.materialOf(WHALE);
                int shardBefore = Pouch.countAll(a, shard);
                Vows.reward(a, WHALE, 6, false, 0.99f);
                check(f, Pouch.countAll(a, shard) - shardBefore == 12, "tier 6 did not add 12 materials");
                check(f, Pouch.countAll(a, Vows.resonanceShardOf(WHALE)) == 0, "a resonance shard dropped on a bad roll");
                check(f, store.vowBest(a.getUUID(), WHALE) == 6, "the best tier was not recorded");
                Vows.reward(a, WHALE, 6, false, 0.0f);
                check(f, Pouch.countAll(a, Vows.resonanceShardOf(WHALE)) == Vows.SHARD_COUNT, "no resonance shard on a lucky roll");
                Vows.reward(a, WHALE, 2, false, 0.99f);
                check(f, store.vowBest(a.getUUID(), WHALE) == 6, "a lower tier lowered the best record");
                int before = Pouch.countAll(a, shard);
                Vows.reward(a, WHALE, 0, false, 0.0f);
                check(f, Pouch.countAll(a, shard) == before, "tier 0 paid out");
                store.forgetVowBest(a.getUUID());
                // 心臓を止めたときの流れ
                Vows.add(server, ga, WHALE, "hardy");
                Vows.add(server, ga, WHALE, "sharp");
                int s0 = Pouch.countAll(a, shard);
                Vows.onHeartStopped(back, WHALE, List.of(ga));
                check(f, Pouch.countAll(a, shard) - s0 == Vows.bonusMaterials(4) && store.vowBest(a.getUUID(), WHALE) == 4, "the heart stop did not pay tier 4");
                check(f, Vows.active(back, ga).isEmpty() && VowNetwork.snapshot(a).ids().isEmpty(), "the vows were not lifted after the heart stopped");
                int s1 = Pouch.countAll(a, shard);
                Vows.onHeartStopped(back, WHALE, List.of(ga));
                check(f, Pouch.countAll(a, shard) == s1, "the heart stop paid twice");
                check(f, Pouch.countAll(a, shard) == s1 && store.vowBest(b.getUUID(), WHALE) == 0, "another player was paid");
                // 時間切れは数えない
                Vows.add(server, ga, WHALE, "hasty");
                Vows.add(server, ga, WHALE, "thin");
                Vows.startTimerIfNeeded(back, ga, server.overworld().getGameTime() - limit - 100);
                int s2 = Pouch.countAll(a, shard);
                Vows.onHeartStopped(back, WHALE, List.of(ga));
                check(f, Pouch.countAll(a, shard) - s2 == Vows.bonusMaterials(2), "a broken timer was paid in full");
                store.forgetVowBest(a.getUUID());

                // ---- 周回の囁き: 最初の囁きを聞いた人にだけ、周回の言い回しを一度。初めての人は最初の囁き (c は初回のグループ、b は周回に入ったあとの初めての人)
                check(f, FirstRun.isFirstRun(back, gc), "test setup: group C should be on a first run");
                BackWhispers.hear(c, "w0", "message.skyabyss.whisper.w0", false, null, "x");
                check(f, BackWhispers.heardAlready(c, "w0") && !BackWhispers.heardAlready(c, BackWhispers.REPEAT_PREFIX + "w0"), "first run: the original whisper was not used");
                BackWhispers.hear(c, "w0", "message.skyabyss.whisper.w0", false, null, "x");
                check(f, !BackWhispers.heardAlready(c, BackWhispers.REPEAT_PREFIX + "w0"), "first run: a whisper repeated");
                FirstRun.onFirstCleared(back, List.of(gc));
                BackWhispers.hear(c, "w0", "message.skyabyss.whisper.w0", false, null, "x");
                check(f, BackWhispers.heardAlready(c, BackWhispers.REPEAT_PREFIX + "w0"), "repeat run: the repeat whisper did not switch on");
                BackWhispers.hear(c, "hub", "message.skyabyss.whisper.hub", true, null, "x");
                BackWhispers.hear(c, "hub", "message.skyabyss.whisper.hub", true, null, "x");
                check(f, !BackWhispers.heardAlready(c, BackWhispers.REPEAT_PREFIX + "hub"), "a whisper without a repeat variant repeated");
                check(f, !FirstRun.isFirstRun(back, Groups.keyOf(b)), "test setup: B left a cleared party and should be on the repeat path");
                BackWhispers.hear(b, "w1", "message.skyabyss.whisper.w1", false, null, "x");
                check(f, BackWhispers.heardAlready(b, "w1") && !BackWhispers.heardAlready(b, BackWhispers.REPEAT_PREFIX + "w1"),
                        "a first-timer in a cleared group did not hear the original whisper");
                for (String id : BackWhispers.REPEAT_IDS) {
                    check(f, hasLang(BackWhispers.repeatKey(id)), "no ja/en text for repeat whisper " + id);
                }
                for (String t : List.of("whale", "tide", "storm", "ember")) {
                    check(f, hasLang("message.skyabyss.steles.rung_repeat." + t) && hasLang("message.skyabyss.steles.touch_repeat." + t),
                            "no ja/en stele repeat text for " + t);
                }
                // 語り部の周回の一言 (repeat_run 条件)
                check(f, FirstRun.hasCleared(server, ga, WHALE) && !FirstRun.hasCleared(server, UUID.randomUUID(), null)
                        && FirstRun.hasCleared(server, gb, null) && !FirstRun.hasCleared(server, gb, Titans.EMBER), "FirstRun.hasCleared (repeat_run) is off");
                check(f, jp.revati.skyabyss.npc.DialogueManager.dialogue("skyabyss:storyteller") != null
                        && jp.revati.skyabyss.npc.DialogueManager.dialogue("skyabyss:storyteller").nodes().containsKey("repeat_note"), "the storyteller's repeat note is missing");
            }
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST vow crashed", e);
            f.add("crashed: " + e);
        } finally {
            parties.restore(snapshot, server.registryAccess());
            Vows.unregister("selftest_vow");
            if (back != null) {
                back.setChunkForced(6000 >> 4, 6000 >> 4, false);
            }
            for (net.minecraft.world.entity.Entity e : made) {
                e.discard();
            }
            for (FakePlayer p : java.util.Arrays.asList(a, b, c)) {
                if (p != null) {
                    FirstRunData.get(server).forgetVowBest(p.getUUID());
                    p.discard();
                }
            }
            if (back != null) {
                for (UUID g : groups) {
                    BackGroupData.get(back).remove(g);
                }
            }
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST vow PASSED");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST vow FAILED: {}", m));
        }
    }

    // skyabyss_vow の ja・en の lang に、そのキーがあるか
    private static boolean hasLang(String key) {
        try {
            var assets = net.neoforged.fml.ModList.get().getModFileById(SkyAbyss.MODID).getFile().findResource("assets", "skyabyss_vow", "lang");
            for (String name : List.of("ja_jp.json", "en_us.json")) {
                try (var reader = java.nio.file.Files.newBufferedReader(assets.resolve(name), java.nio.charset.StandardCharsets.UTF_8)) {
                    var json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                    if (!json.has(key) || json.get(key).getAsString().isEmpty()) {
                        return false;
                    }
                }
            }
            return true;
        } catch (java.io.IOException | RuntimeException e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- 居座りの早じまい

    public static void runStaySelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel overworld = server.overworld();
        ServerLevel back = server.getLevel(TitanTravel.TITAN_BACK);
        FakePlayer near = null;
        FakePlayer far = null;
        FakePlayer inside = null;
        FakePlayer watcher = null;
        try {
            // 数え方そのもの: 3 分の連続・戻れば数え直し・去ったら数え直し
            TitanStay.Vacancy v = new TitanStay.Vacancy();
            boolean early = false;
            for (int i = 0; i < TitanStay.LEAVE_AFTER - 1; i++) {
                early |= v.tick(false);
            }
            check(f, !early && v.vacantTicks() == TitanStay.LEAVE_AFTER - 1, "the titan left before 3 minutes of nobody");
            check(f, v.tick(false) && v.vacantTicks() == 0, "the titan did not leave at exactly 3 minutes of nobody");
            for (int i = 0; i < TitanStay.LEAVE_AFTER - 1; i++) {
                v.tick(false);
            }
            check(f, !v.tick(true) && v.vacantTicks() == 0, "coming back at 2:59 did not cancel");
            for (int i = 0; i < TitanStay.LEAVE_AFTER - 1; i++) {
                v.tick(false);
            }
            check(f, !v.tick(true) && !v.tick(false) && v.vacantTicks() == 1, "the count did not restart after someone came back");
            check(f, TitanStay.LEAVE_AFTER == 3600 && TitanStay.LEAVE_RANGE == 160 && TitanStay.TICKS == 24000, "the stay constants changed");
            // 警告の係を通しても同じ (Warner は巨神ごと)
            TitanStay.Warner w = new TitanStay.Warner();
            Vec3 titanAt = new Vec3(9000, 120, 9000);
            boolean left = false;
            for (int i = 0; i < TitanStay.LEAVE_AFTER; i++) {
                left = w.vacantTooLong(overworld, titanAt, Titans.STONE_WHALE);
            }
            check(f, left, "the warner did not report the early leave after 3 minutes");
            w.reset();

            // 誰かいるか: 160 ブロック以内 (水平) ・遠い・観戦者・背中の中
            near = new FakePlayer(overworld, new GameProfile(UUID.randomUUID(), "stay_near"));
            near.moveTo(titanAt.x + 100, 200, titanAt.z, 0, 0);
            overworld.addFreshEntity(near);
            check(f, TitanStay.attended(overworld, titanAt, Titans.STONE_WHALE), "a player 100 blocks away did not count");
            near.moveTo(titanAt.x + 159, 10, titanAt.z, 0, 0);
            check(f, TitanStay.attended(overworld, titanAt, Titans.STONE_WHALE), "the range is not horizontal 160");
            near.moveTo(titanAt.x + 170, 200, titanAt.z, 0, 0);
            boolean othersNearby = TitanStay.attended(overworld, titanAt, Titans.STONE_WHALE);
            check(f, !othersNearby, "a player 170 blocks away counted (or the test world has someone near)");
            near.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
            near.moveTo(titanAt.x + 10, 200, titanAt.z, 0, 0);
            check(f, !TitanStay.attended(overworld, titanAt, Titans.STONE_WHALE), "a spectator counted");
            near.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            check(f, TitanStay.attended(overworld, titanAt, Titans.STONE_WHALE), "a survival player right next to it did not count");
            near.moveTo(titanAt.x + 1000, 200, titanAt.z, 0, 0);
            check(f, !TitanStay.attended(overworld, titanAt, Titans.STONE_WHALE), "a far player counted");
            // グループが背中の中にいれば、遠くても数えない (早じまいしない)
            if (back != null) {
                inside = new FakePlayer(back, new GameProfile(UUID.randomUUID(), "stay_inside"));
                inside.moveTo(0, 100, 0, 0, 0);
                back.addFreshEntity(inside);
                check(f, TitanStay.attended(overworld, titanAt, Titans.STONE_WHALE), "someone inside the back did not count as attended");
                check(f, !TitanStay.attended(overworld, titanAt, Titans.TIDE_SHELL), "someone in the whale's back counted for the tide shell");
                inside.discard();
                inside = null;
                check(f, !TitanStay.attended(overworld, titanAt, Titans.STONE_WHALE), "nobody inside the back, yet still attended");
                // 戻ってきた (3 分の途中) → 取り消し
                TitanStay.Vacancy v2 = new TitanStay.Vacancy();
                for (int i = 0; i < 3000; i++) {
                    v2.tick(TitanStay.attended(overworld, titanAt, Titans.STONE_WHALE));
                }
                near.moveTo(titanAt.x + 20, 200, titanAt.z, 0, 0);
                boolean fired = false;
                for (int i = 0; i < 1000; i++) {
                    fired |= v2.tick(TitanStay.attended(overworld, titanAt, Titans.STONE_WHALE));
                }
                check(f, !fired && v2.vacantTicks() == 0, "a player coming back within the 3 minutes did not cancel the early leave");
            }
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST stay leave crashed", e);
            f.add("crashed: " + e);
        } finally {
            for (FakePlayer p : java.util.Arrays.asList(near, far, inside, watcher)) {
                if (p != null) {
                    p.discard();
                }
            }
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST stay leave PASSED");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST stay leave FAILED: {}", m));
        }
    }
}
