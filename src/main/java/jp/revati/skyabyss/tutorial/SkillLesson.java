package jp.revati.skyabyss.tutorial;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.FacilitySetup;
import jp.revati.skyabyss.facility.TrainingDummy;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.npc.NpcBonds;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.rpg.skill.Skill;
import jp.revati.skyabyss.rpg.skill.SkillActions;
import jp.revati.skyabyss.rpg.skill.SkillData;
import jp.revati.skyabyss.rpg.skill.SkillManager;
import jp.revati.skyabyss.rpg.skill.SkillTree;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 技の稽古 (都の訓練場の師範。クエストと会話は データを直接直す（生成スクリプトはない）)。
//   一章: lesson/skills (会う → 覚えて枠に置く → 木偶に技を二度 → 背後から → 構え → 振り直しを聞く)。段ごとに師範の一言と少しの経験。
//         最後に一度だけ鐘銭。飛ばせる (師範の印 lesson_skip。自動では始まらなくなる)。済んだら師範に頼めば何度でも (報酬は初回だけ)
//   後の章: 新しい木・要が開いたら一段だけの稽古 (lesson/tree_*) を始め、一言を出す。その木の技を木偶か敵に当てれば済む
//   合図は Quests.signal に送る (目的の帯・ログはクエストの仕組みが出す)。画面の案内はクライアント (client.tutorial.SkillLessonClient)
//   木偶の反応: 段に合わせて木偶のまわりに光 (背後の段は背中側に)、技・背後の一打で火花と音
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class SkillLesson {
    private SkillLesson() {
    }

    public static final String QUEST = "lesson/skills";
    public static final String MASTER = "skyabyss:attendant_training_master";
    public static final String SKIP_FLAG = "lesson_skip";
    public static final String REWARD_FLAG = "lesson_rewarded";
    public static final Set<String> ACTS = Set.of("say", "finish", "skip", "replay", "track");
    public static final String SIG_SLOTTED = "skill_lesson:slotted";
    public static final String SIG_DUMMY = "skill_lesson:dummy";
    public static final String SIG_BEHIND = "skill_lesson:behind";
    public static final String SIG_STANCE = "skill_lesson:stance";
    public static final String SIG_KEYSTONE = "skill_lesson:keystone";
    public static final String SIG_TREE = "skill_lesson:tree:";
    public static final int REWARD_COINS = 15;
    static final double DUMMY_RANGE = 14;

    // 後の章の一段だけの稽古 (名前・木。KEYSTONE は木でなく要)
    public enum Unlock {
        HYBRID("tree_hybrid", SkillTree.HYBRID),
        KEYSTONE("tree_keystone", null),
        STAR("tree_star", SkillTree.STAR),
        ABYSS("tree_abyss", SkillTree.ABYSS),
        DEEP("tree_deep", SkillTree.DEEP),
        PAST("tree_past", SkillTree.PAST);

        public final String name;
        @Nullable
        public final SkillTree tree;

        Unlock(String name, @Nullable SkillTree tree) {
            this.name = name;
            this.tree = tree;
        }

        public String quest() {
            return "lesson/" + name;
        }

        // 開いたか (今の技の記録で)
        public boolean open(SkillData d, int level) {
            return switch (this) {
                case HYBRID -> Skill.inTree(SkillTree.HYBRID).stream().anyMatch(d::bridgeOpen);
                case KEYSTONE -> level >= Skill.STONE_OATH.minLevel;
                case STAR, ABYSS -> d.unlockedTrees.contains(tree.id);
                case DEEP, PAST -> d.rebirth >= 1;
            };
        }
    }

    // 構えの覚え (変わったら合図) と、合図を送った最後の使用 (一回の使用で二度数えない)
    private static final Map<UUID, Integer> STANCE = new HashMap<>();
    private static final Map<UUID, Long> LAST_CAST = new HashMap<>();

    // ---------------------------------------------------------------- 今の段
    // 一章の稽古の今の段の ID (受けていない・済んだなら null)
    @Nullable
    public static String stage(ServerPlayer player) {
        return stageOf(player, QUEST);
    }

    @Nullable
    static String stageOf(ServerPlayer player, String quest) {
        QuestLog.Entry e = QuestLog.of(player).entry(quest);
        QuestDef def = QuestManager.quest(quest);
        if (e == null || e.done || def == null || e.stage >= def.stages().size()) {
            return null;
        }
        return def.stages().get(e.stage).id();
    }

    // ---------------------------------------------------------------- 会話・段の結果 (Action "lesson")
    public static void act(ServerPlayer player, String act, @Nullable String line) {
        switch (act) {
            case "say" -> {
                if (line != null) {
                    Notice.narrate(player, "message.skyabyss.lesson.say." + line);
                }
            }
            case "finish" -> {
                Notice.narrate(player, "message.skyabyss.lesson.say.finish");
                NpcBonds bonds = NpcBonds.of(player);
                if (!bonds.hasFlag(MASTER, REWARD_FLAG)) {
                    bonds.bond(MASTER).flags().add(REWARD_FLAG);
                    NpcBonds.sync(player);
                    Item coin = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "bell_coin"));
                    if (coin != Items.AIR) {
                        Quests.give(player, new ItemStack(coin, REWARD_COINS));
                        Notice.gain(player, "message.skyabyss.lesson.reward");
                    }
                }
            }
            case "skip" -> {
                Quests.forget(player, QUEST);
                Notice.hint(player, "message.skyabyss.lesson.skipped");
            }
            // 師範に頼んだ: 稽古を追う (目的の帯・印がこちらを指す)
            case "track" -> {
                if (stage(player) != null) {
                    Quests.track(player, QUEST);
                }
            }
            case "replay" -> {
                Quests.forget(player, QUEST);
                Quests.start(player, QUEST);
                Quests.track(player, QUEST);
            }
            default -> {
            }
        }
    }

    // ---------------------------------------------------------------- 見回り (1 秒ごと。光は 0.5 秒ごと)
    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        long t = player.level().getGameTime() + (player.getId() & 0x1F);
        if (t % 10 == 0) {
            String st = stage(player);
            if (st != null) {
                glow(player, st);
            }
        }
        if (t % 20 == 0) {
            poll(player);
        }
    }

    public static void poll(ServerPlayer player) {
        SkillData d = SkillManager.get(player);
        String st = stage(player);
        UUID id = player.getUUID();
        if ("learn".equals(st) && anySlotted(d)) {
            Quests.signal(player, SIG_SLOTTED);
        }
        Integer before = STANCE.put(id, d.stance);
        if ("stance".equals(st) && before != null && before != d.stance) {
            Quests.signal(player, SIG_STANCE);
        }
        if (stageOf(player, Unlock.KEYSTONE.quest()) != null && knowsKeystone(d)) {
            Quests.signal(player, SIG_KEYSTONE);
        }
        // 新しい木・要が開いた: 一段だけの稽古を始めて、一言
        int level = SkillManager.level(player);
        QuestLog log = QuestLog.of(player);
        for (Unlock u : Unlock.values()) {
            if (log.entry(u.quest()) == null && QuestManager.quest(u.quest()) != null && u.open(d, level)) {
                if (Quests.start(player, u.quest())) {
                    Notice.narrate(player, "message.skyabyss.lesson.say." + u.name);
                }
            }
        }
    }

    static boolean anySlotted(SkillData d) {
        for (int i = 0; i < d.slotCount(); i++) {
            Skill s = d.slot(i);
            if (s != null && s.active && d.knows(s)) {
                return true;
            }
        }
        // 職ごとの枠 (docs/design/43): ほかの職の枠に置いてあってもよい
        for (String[] bar : d.jobBars.values()) {
            for (String id : bar) {
                Skill s = Skill.byId(id);
                if (s != null && s.active && d.knows(s)) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean knowsKeystone(SkillData d) {
        for (Skill s : Skill.values()) {
            if (s.keystone() && d.knows(s)) {
                return true;
            }
        }
        return false;
    }

    // 段に合わせた木偶の光 (その人にだけ見せる)。当てる段は頭の上、背後の段は背中側
    static void glow(ServerPlayer player, String st) {
        if (!st.equals("cast") && !st.equals("behind")) {
            return;
        }
        ServerLevel level = player.serverLevel();
        List<TrainingDummy> near = level.getEntities(FacilitySetup.TRAINING_DUMMY.get(), new AABB(player.blockPosition()).inflate(DUMMY_RANGE), LivingEntity::isAlive);
        for (TrainingDummy dummy : near) {
            if (st.equals("cast")) {
                level.sendParticles(player, ParticleTypes.HAPPY_VILLAGER, false, dummy.getX(), dummy.getY() + 3.1, dummy.getZ(), 3, 0.2, 0.1, 0.2, 0);
            } else {
                Vec3 back = dummy.position().subtract(facing(dummy).scale(1.3));
                level.sendParticles(player, ParticleTypes.END_ROD, false, back.x, dummy.getY() + 0.2, back.z, 4, 0.25, 0.05, 0.25, 0.01);
            }
        }
    }

    // ---------------------------------------------------------------- 木偶を打つ・技が当たる
    @SubscribeEvent
    static void onDamage(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0 || !(event.getSource().getEntity() instanceof ServerPlayer player) || player.level().isClientSide) {
            return;
        }
        LivingEntity victim = event.getEntity();
        onHit(player, victim, SkillActions.dealing());
    }

    public static void onHit(ServerPlayer player, LivingEntity victim, @Nullable Skill skill) {
        // 後の章の稽古: その木の技が木偶か敵に当たった
        if (skill != null) {
            for (Unlock u : Unlock.values()) {
                if (u.tree == skill.tree && stageOf(player, u.quest()) != null) {
                    Quests.signal(player, SIG_TREE + skill.tree.id);
                }
            }
        }
        if (!(victim instanceof TrainingDummy dummy)) {
            return;
        }
        String st = stage(player);
        if (st == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (st.equals("cast") && skill != null) {
            long cast = SkillManager.get(player).lastCastTick;
            Long last = LAST_CAST.put(player.getUUID(), cast);
            if (last == null || last != cast) {
                Quests.signal(player, SIG_DUMMY);
                level.sendParticles(ParticleTypes.ENCHANTED_HIT, dummy.getX(), dummy.getY() + 2.2, dummy.getZ(), 14, 0.3, 0.4, 0.3, 0.2);
            }
        } else if (st.equals("behind") && behind(dummy, player)) {
            Quests.signal(player, SIG_BEHIND);
            level.sendParticles(ParticleTypes.CRIT, dummy.getX(), dummy.getY() + 2.0, dummy.getZ(), 20, 0.3, 0.5, 0.3, 0.3);
            level.playSound(null, dummy.getX(), dummy.getY() + 1, dummy.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.9f, 1.1f);
            Notice.send(player, Notice.Kind.BAR, net.minecraft.network.chat.Component.translatable("message.skyabyss.lesson.behind"));
        }
    }

    // 木偶の向き: 柱の頭 (彫った南瓜) の顔の向き。柱がなければ体の向き
    public static Vec3 facing(TrainingDummy dummy) {
        BlockPos col = dummy.column();
        if (!col.equals(BlockPos.ZERO)) {
            BlockState head = dummy.level().getBlockState(col.above(2));
            if (head.hasProperty(HorizontalDirectionalBlock.FACING)) {
                var n = head.getValue(HorizontalDirectionalBlock.FACING).getNormal();
                return new Vec3(n.getX(), 0, n.getZ());
            }
        }
        return Vec3.directionFromRotation(0, dummy.yBodyRot);
    }

    // 打った人が木偶の背中側にいるか (顔の向きと反対の半分。少し余裕を見る)
    public static boolean behind(TrainingDummy dummy, ServerPlayer player) {
        Vec3 to = new Vec3(player.getX() - dummy.getX(), 0, player.getZ() - dummy.getZ());
        if (to.lengthSqr() < 1e-6) {
            return false;
        }
        return facing(dummy).dot(to.normalize()) < -0.3;
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STANCE.remove(event.getEntity().getUUID());
        LAST_CAST.remove(event.getEntity().getUUID());
    }
}
