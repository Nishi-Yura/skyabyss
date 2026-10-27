package jp.revati.skyabyss.facility;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.map.MapMarks;
import jp.revati.skyabyss.npc.Dialogue;
import jp.revati.skyabyss.npc.DialogueManager;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.record.Keepsakes;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.GrowthData;
import jp.revati.skyabyss.rpg.skill.Skill;
import jp.revati.skyabyss.rpg.skill.SkillData;
import jp.revati.skyabyss.rpg.skill.SkillManager;
import jp.revati.skyabyss.titan.vow.Vow;
import jp.revati.skyabyss.titan.vow.Vows;
import jp.revati.skyabyss.trial.TrialCatalog;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;

// 章で開く施設と訓練場 (docs/design/35 の 3.2・6.7) の自己テスト。段: "facility extras"。
//   会話: 師範 (振り直しの 4 つ)、祭司 (誓いの一覧が Vows の登録とそろう・欠片を納める / 返す)、老兵 (記録・前の私の頁は話だけ)、
//         書庫番 (読み返し・地図の写し)、薬師 (四章から醸造を教わる)、受付 (ギルドの倉庫)
//   振り直し: 炉から遠くても師範の前なら効く。結晶か鐘銭 (結晶 1 = 30 枚)。足りなければ何も変えない
//   木偶: 柱を見つけて的を一つだけかぶせ、柱が崩れたら消す。人の攻撃だけ受け、倒れず体力が戻り、浮かぶ数字が出る。5 秒の合計と毎秒
//   鍵の間: 納めた欠片は手もと (枠 key_room) に数え、鍵がそろうときの取り出しに使われ、返してもらえる。心臓を止めていない巨神には納めない
//   記録: 四体ぶん、試練の一覧は図鑑と同じ数。送る形の往復。地図の写しの印。醸造の手ほどきは一度だけ。空き区画の座標が設計図の碑の印と合う
public final class FacilityExtrasSelfTest {
    private FacilityExtrasSelfTest() {
    }

    static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "facility_x"));
        p.setGameMode(GameType.SURVIVAL);
        try {
            dialogues(f);
            respec(f, level, p);
            dummies(f, level, p);
            keyRoom(f, server, p);
            records(f, server, p);
            marks(f, p);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST facility extras crashed", e);
            f.add("crashed: " + e);
        } finally {
            p.getInventory().clearContent();
            jp.revati.skyabyss.facility.Coins.take(p, jp.revati.skyabyss.facility.Coins.count(p));
            KeyRoom.Data.get(server).offered.remove(p.getUUID());
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST facility extras PASSED (dialogue options, respec at the master, dummies, key room, records, map copy, brewing lesson)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST facility extras FAILED: {}", m));
        }
    }

    // ---------------------------------------------------------------- 会話
    static boolean opens(Dialogue d, String open) {
        for (Dialogue.Node n : d.nodes().values()) {
            for (Dialogue.Option o : n.options()) {
                if (o.actions().stream().anyMatch(a -> a.type().equals("facility") && a.str("open").equals(open))) {
                    return true;
                }
            }
            if (n.actions().stream().anyMatch(a -> a.type().equals("facility") && a.str("open").equals(open))) {
                return true;
            }
        }
        return false;
    }

    private static void dialogues(List<String> f) {
        Map<AttendantRole, List<String>> need = Map.of(
                AttendantRole.TRAINING_MASTER, List.of("respec_quote", "respec_skills", "respec_skills_coins", "respec_stats", "respec_stats_coins"),
                AttendantRole.SHRINE_PRIEST, List.of("key_offer", "key_return"),
                AttendantRole.KEEP_VETERAN, List.of("records"),
                AttendantRole.ARCHIVIST, List.of("lore_book", "map_copy"),
                AttendantRole.APOTHECARY, List.of("shop", "brew_lesson"),
                AttendantRole.GUILD_CLERK, List.of("guild", "guild_storage"));
        need.forEach((role, list) -> {
            Dialogue d = DialogueManager.dialogue(role.dialogueRole());
            if (d == null) {
                f.add("no dialogue " + role.id);
                return;
            }
            for (String o : list) {
                check(f, opens(d, o), role.id + " opens " + o);
            }
        });
        // 誓い: 登録された誓いがすべて祭司の一覧にあり、説明の節が誓いの説明文を読む
        Dialogue priest = DialogueManager.dialogue(AttendantRole.SHRINE_PRIEST.dialogueRole());
        if (priest != null) {
            Dialogue.Node vow = priest.nodes().get("vow");
            check(f, vow != null, "priest has the vow node");
            for (Vow v : Vows.all()) {
                boolean listed = vow != null && vow.options().stream().anyMatch(o -> o.textKey().equals(v.nameKey()));
                Dialogue.Node desc = priest.nodes().get("vow_" + v.id());
                check(f, listed && desc != null && desc.textKey().equals(v.descKey()), "priest explains vow " + v.id());
            }
        }
        // 前の私の頁は話だけ (まだ中身がない)
        Dialogue keep = DialogueManager.dialogue(AttendantRole.KEEP_VETERAN.dialogueRole());
        check(f, keep != null && keep.nodes().containsKey("page"), "keep veteran has the page placeholder");
    }

    // ---------------------------------------------------------------- 振り直し
    private static void respec(List<String> f, ServerLevel level, FakePlayer p) {
        BlockPos at = level.getSharedSpawnPos().atY(level.getMaxBuildHeight() - 6);
        p.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        p.getInventory().clearContent();
        jp.revati.skyabyss.facility.Coins.take(p, jp.revati.skyabyss.facility.Coins.count(p));
        GrowthData gd = Growth.get(p);
        SkillData sd = SkillManager.get(p);
        gd.level = 16;
        sd.bonusPoints = 40;
        sd.respecs = 0;
        SkillManager.forceRank(p, Skill.TITAN_CLEAVE, 1);
        int crystals = TrainingMaster.skillCrystals(p);
        check(f, crystals >= 1, "late skill respec costs crystals (" + crystals + ")");
        check(f, SkillManager.respec(p) == SkillManager.RespecResult.NO_FORGE, "plain respec still needs a forge");
        check(f, TrainingMaster.respec(p, false, false) == SkillManager.RespecResult.NO_CRYSTALS && !sd.ranks.isEmpty(),
                "master respec without crystals refused and kept skills");
        p.getInventory().add(new ItemStack(ModItems.RESONANCE_CRYSTAL.get(), crystals));
        check(f, TrainingMaster.respec(p, false, false) == SkillManager.RespecResult.OK && sd.ranks.isEmpty()
                && SkillManager.countCrystals(p.getInventory()) == 0, "master respec with crystals away from a forge");
        SkillManager.forceRank(p, Skill.TITAN_CLEAVE, 1);
        int price = TrainingMaster.skillCrystals(p) * TrainingMaster.COINS_PER_CRYSTAL;
        check(f, TrainingMaster.respec(p, false, true) != SkillManager.RespecResult.OK && !sd.ranks.isEmpty(), "coin respec without coins refused");
        Coins.give(p, price);
        check(f, TrainingMaster.respec(p, false, true) == SkillManager.RespecResult.OK && sd.ranks.isEmpty() && Coins.count(p) == 0,
                "coin respec took " + price + " coins");
        // 力: 2 回目からは結晶 (鐘銭)
        java.util.Arrays.fill(gd.stats, 0);
        gd.statRespecs = 1;
        gd.stats[0] = 3;
        int sp = TrainingMaster.statCrystals(p) * TrainingMaster.COINS_PER_CRYSTAL;
        Coins.give(p, sp);
        check(f, sp > 0 && TrainingMaster.respec(p, true, true) == SkillManager.RespecResult.OK && gd.spentPoints() == 0 && Coins.count(p) == 0,
                "stat respec for coins");
        check(f, TrainingMaster.respec(p, true, true) == SkillManager.RespecResult.NOTHING, "stat respec with nothing spent");
        gd.level = 1;
        sd.bonusPoints = 0;
    }

    // ---------------------------------------------------------------- 木偶
    private static void dummies(List<String> f, ServerLevel level, FakePlayer p) {
        BlockPos foot = FacilitySelfTest.testBase(level).atY(level.getMaxBuildHeight() - 14).offset(6, 0, 6);
        BlockState[] old = {level.getBlockState(foot), level.getBlockState(foot.above()), level.getBlockState(foot.above(2))};
        level.setBlock(foot, Blocks.OAK_FENCE.defaultBlockState(), 3);
        level.setBlock(foot.above(), Blocks.HAY_BLOCK.defaultBlockState(), 3);
        level.setBlock(foot.above(2), Blocks.CARVED_PUMPKIN.defaultBlockState(), 3);
        try {
            List<BlockPos> cols = TrainingYard.findColumns(level, foot.above(), 3);
            check(f, cols.size() == 1 && cols.get(0).equals(foot), "finds the dummy column " + cols);
            check(f, TrainingYard.ensureDummies(level, cols) == 1 && TrainingYard.ensureDummies(level, cols) == 1, "one dummy per column");
            List<TrainingDummy> ds = level.getEntities(FacilitySetup.TRAINING_DUMMY.get(), new AABB(foot).inflate(1), e -> true);
            check(f, ds.size() == 1, "dummy count " + ds.size());
            if (ds.size() == 1) {
                TrainingDummy d = ds.get(0);
                check(f, d.isInvulnerableTo(level.damageSources().generic()) && !d.isInvulnerableTo(level.damageSources().playerAttack(p)),
                        "dummy takes only player hits");
                p.moveTo(foot.getX() + 0.5, foot.getY(), foot.getZ() + 2.5);
                d.invulnerableTime = 0;
                DamageSource src = level.damageSources().playerAttack(p);
                d.hurt(src, 6f);
                check(f, d.isAlive() && d.getHealth() >= d.getMaxHealth() - 0.01f, "dummy stays whole after a hit (" + d.getHealth() + ")");
                d.invulnerableTime = 0;
                d.hurt(src, 5000f);
                check(f, d.isAlive(), "dummy never falls");
                List<Display.TextDisplay> nums = level.getEntitiesOfClass(Display.TextDisplay.class, new AABB(foot).inflate(2, 6, 2),
                        t -> t.getTags().contains(TrainingYard.NUMBER_TAG));
                check(f, !nums.isEmpty(), "a damage number floats");
                nums.forEach(Display.TextDisplay::discard);
            }
            level.setBlock(foot.above(2), Blocks.AIR.defaultBlockState(), 3);
            TrainingYard.ensureDummies(level, cols);
            check(f, level.getEntities(FacilitySetup.TRAINING_DUMMY.get(), new AABB(foot).inflate(1), e -> e.isAlive()).isEmpty(),
                    "dummy leaves when the column breaks");
            UUID who = UUID.randomUUID();
            TrainingYard.record(who, 0, 4);
            double[] s = TrainingYard.record(who, 20, 6);
            check(f, Math.abs(s[0] - 10) < 1e-6 && s[1] > 0, "5 s total " + s[0]);
            s = TrainingYard.record(who, 400, 2);
            check(f, Math.abs(s[0] - 2) < 1e-6, "old hits drop out of the window");
        } finally {
            level.setBlock(foot.above(2), old[2], 3);
            level.setBlock(foot.above(), old[1], 3);
            level.setBlock(foot, old[0], 3);
        }
    }

    // ---------------------------------------------------------------- 鍵の間
    private static void keyRoom(List<String> f, MinecraftServer server, FakePlayer p) {
        p.getInventory().clearContent();
        jp.revati.skyabyss.facility.Coins.take(p, jp.revati.skyabyss.facility.Coins.count(p));
        KeyRoom.Data data = KeyRoom.Data.get(server);
        String titan = Titans.all().get(0);
        p.getInventory().add(new ItemStack(ModItems.SKY_KEY_FRAGMENT.get()));
        check(f, !KeyRoom.offer(p, titan, false), "no offering for a titan whose heart is still beating");
        p.getInventory().clearContent();
        jp.revati.skyabyss.facility.Coins.take(p, jp.revati.skyabyss.facility.Coins.count(p));
        data.offered.put(p.getUUID(), new java.util.LinkedHashSet<>(Set.of(titan)));
        Keepsakes.Where w = Keepsakes.locate(p, ModItems.SKY_KEY_FRAGMENT.get());
        check(f, w.holders().getOrDefault(KeyRoom.HOLDER, 0) == 1 && w.safe() >= 1, "laid fragment counts as at hand " + w.holders());
        check(f, Keepsakes.take(p, ModItems.SKY_KEY_FRAGMENT.get(), s -> true, 1) == 1 && KeyRoom.offered(p).isEmpty(),
                "forming the key takes from the key room");
        data.offered.put(p.getUUID(), new java.util.LinkedHashSet<>(Set.of(titan)));
        check(f, KeyRoom.giveBack(p) == 1 && KeyRoom.offered(p).isEmpty() && Keepsakes.locate(p, ModItems.SKY_KEY_FRAGMENT.get()).safe() == 1,
                "laid fragments come back");
        check(f, KeyRoom.giveBack(p) == 0, "nothing to give back twice");
        // 保存と読み直し
        data.offered.put(p.getUUID(), new java.util.LinkedHashSet<>(Set.of(titan)));
        KeyRoom.Data copy = KeyRoom.Data.load(data.save(new net.minecraft.nbt.CompoundTag(), server.registryAccess()), server.registryAccess());
        check(f, copy.offered.getOrDefault(p.getUUID(), Set.of()).contains(titan), "key room survives save");
        data.offered.remove(p.getUUID());
        p.getInventory().clearContent();
        jp.revati.skyabyss.facility.Coins.take(p, jp.revati.skyabyss.facility.Coins.count(p));
    }

    // ---------------------------------------------------------------- 記録・印
    private static void records(List<String> f, MinecraftServer server, FakePlayer p) {
        KeepRecords.View v = KeepRecords.build(p);
        check(f, v.pages().size() == Titans.all().size(), "records cover every titan");
        for (KeepRecords.TitanPage page : v.pages()) {
            check(f, page.rows().size() == TrialCatalog.of(page.titan()).size(), "records rows for " + page.titan());
        }
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), server.registryAccess());
        KeepRecords.View.STREAM_CODEC.encode(buf, v);
        KeepRecords.View back = KeepRecords.View.STREAM_CODEC.decode(buf);
        buf.release();
        check(f, back.equals(v), "records view survives the codec");
    }

    private static void marks(List<String> f, FakePlayer p) {
        FacilityMarks.remove(p, FacilityMarks.MAP_COPY);
        check(f, FacilityActions.mapCopy(p) && !FacilityActions.mapCopy(p) && FacilityMarks.has(p, FacilityMarks.MAP_COPY), "map copy is marked once");
        List<MapMarks.Mark> out = new ArrayList<>();
        MapMarks.archiveMarks(p.server, List.of(), jp.revati.skyabyss.story.Chapters.of(p), out);
        for (String t : Titans.all()) {
            boolean built = jp.revati.skyabyss.titan.shrine.TitanShrine.posOf(p.server, t) != null;
            check(f, built == out.stream().anyMatch(m -> m.kind() == MapMarks.Kind.SHRINE && m.id().equals("shrine:" + t)), "map copy shrine " + t);
        }
        MapMarks.collect(p);
        // 醸造: 四章より前は教えない、四章 (ここではクリエイティブで代える) で一度だけ
        FacilityMarks.remove(p, FacilityMarks.BREW_LESSON);
        p.getInventory().clearContent();
        jp.revati.skyabyss.facility.Coins.take(p, jp.revati.skyabyss.facility.Coins.count(p));
        check(f, !FacilityActions.brewLesson(p) && !FacilityMarks.has(p, FacilityMarks.BREW_LESSON), "no brewing lesson before chapter 4");
        p.setGameMode(GameType.CREATIVE);
        check(f, FacilityActions.brewLesson(p) && p.getInventory().countItem(jp.revati.skyabyss.item.outfit.ConsumableGear.FIRE_WARD.get()) == 1 && p.getInventory().countItem(jp.revati.skyabyss.item.outfit.ConsumableGear.HEALING.get()) == 1, "the fire-chapter gift gives a fire ward and a healing potion");
        check(f, !FacilityActions.brewLesson(p) && p.getInventory().countItem(jp.revati.skyabyss.item.outfit.ConsumableGear.FIRE_WARD.get()) == 1, "the fire-chapter gift only once");
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        jp.revati.skyabyss.facility.Coins.take(p, jp.revati.skyabyss.facility.Coins.count(p));
        FacilityActions.loreBook(p);
    }
}
