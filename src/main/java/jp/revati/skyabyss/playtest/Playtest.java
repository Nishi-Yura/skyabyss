package jp.revati.skyabyss.playtest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.cinematic.Cinematics;
import jp.revati.skyabyss.cinematic.Scenes;
import jp.revati.skyabyss.item.TitanGearSet;
import jp.revati.skyabyss.item.TitanGearSets;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.npc.NpcBonds;
import jp.revati.skyabyss.npc.NpcSetup;
import jp.revati.skyabyss.opening.OpeningBeat;
import jp.revati.skyabyss.opening.OpeningDirector;
import jp.revati.skyabyss.progress.ProgressRules;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.readiness.Readiness;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.titan.TitanTravel;
import jp.revati.skyabyss.titan.WhaleKeeper;
import jp.revati.skyabyss.titan.dungeon.TitanBackLayout;
import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.EmberKeeper;
import jp.revati.skyabyss.titan.ember.back.EmberBackLayout;
import jp.revati.skyabyss.titan.ember.back.EmberBackState;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.group.GroupOverlays;
import jp.revati.skyabyss.titan.group.GroupProgress;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.titan.storm.StormKeeper;
import jp.revati.skyabyss.titan.storm.StormTravel;
import jp.revati.skyabyss.titan.storm.back.StormBackLayout;
import jp.revati.skyabyss.titan.storm.back.StormBackState;
import jp.revati.skyabyss.titan.storm.back.StormProgress;
import jp.revati.skyabyss.titan.tide.TideKeeper;
import jp.revati.skyabyss.titan.tide.TideTravel;
import jp.revati.skyabyss.titan.tide.back.TideBackLayout;
import jp.revati.skyabyss.titan.tide.back.TideBackState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

// 試遊を速く進めるための処理 (OP のコマンド /skyabyss playtest ... の中身。製品の jar にも入る)。
// 既存の API・コマンドを呼ぶだけで、新しい仕組みは足さない。docs/playtest_commands.md
public final class Playtest {
    private Playtest() {
    }

    public static final List<String> TITANS = List.of("whale", "tide", "storm", "ember");
    static final ResourceLocation HEALTH_MOD = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "playtest_health");
    private static final String[] MAIN = {"main_song", "main_tide", "main_thunder", "main_ember"};

    // "whale"/"stone_whale"/"tide_shell"/... → 0..3、不明は -1
    public static int index(String name) {
        return switch (name) {
            case "whale", "stone_whale", "鯨", "岩鯨" -> 0;
            case "tide", "tide_shell", "亀", "潮殻" -> 1;
            case "storm", "thunder", "thunder_wing", "雷翼" -> 2;
            case "ember", "余燼" -> 3;
            default -> -1;
        };
    }

    static String suffix(int idx) {
        return switch (idx) {
            case 1 -> " tide";
            case 2 -> " storm";
            case 3 -> " ember";
            default -> "";
        };
    }

    public static int run(ServerPlayer p, String cmd) {
        try {
            return p.server.getCommands().getDispatcher().execute(cmd, p.createCommandSourceStack().withPermission(4).withSuppressedOutput());
        } catch (CommandSyntaxException e) {
            SkyAbyss.LOGGER.warn("playtest: '{}' failed: {}", cmd, e.getMessage());
            return 0;
        }
    }

    // ---------------------------------------------------------------- 序盤
    public static void skipOpening(ServerPlayer p) {
        for (OpeningBeat b : OpeningBeat.values()) {
            String lore = OpeningDirector.loreOf(b);
            if (!lore.isEmpty()) {
                Lore.unlock(p, lore, false);
            }
        }
        for (String role : List.of(NpcSetup.STORYTELLER_ID, NpcSetup.WHALE_CHASER_ID)) {
            NpcBonds.of(p).bond(role).flags().add("met");
        }
        NpcBonds.sync(p);
        OpeningDirector.force(p, OpeningBeat.WHALE);
        if (QuestLog.of(p).entry("main_song") == null) {
            Quests.start(p, "main_song");
        }
        QuestLog.Entry e = QuestLog.of(p).entry("main_song");
        if (e != null && !e.done && e.stage < 4) {
            Quests.setStage(p, "main_song", "see_whale");
        }
    }

    // ---------------------------------------------------------------- 巨神
    @Nullable
    public static Entity titanEntity(ServerLevel overworld, int idx) {
        return switch (idx) {
            case 0 -> WhaleKeeper.current(overworld);
            case 1 -> TideKeeper.KEEPER.current(overworld);
            case 2 -> StormKeeper.KEEPER.current(overworld);
            case 3 -> EmberKeeper.KEEPER.current(overworld);
            default -> null;
        };
    }

    public static void toOverworld(ServerPlayer p) {
        if (p.level().dimension() == Level.OVERWORLD) {
            return;
        }
        if (Titans.titanOfBack(p.level().dimension()) != null) {
            run(p, "skyabyss titan leave");
            if (p.level().dimension() == Level.OVERWORLD) {
                return;
            }
        }
        ServerLevel ow = p.server.overworld();
        BlockPos s = ow.getSharedSpawnPos();
        int y = ow.getHeight(Heightmap.Types.MOTION_BLOCKING, s.getX(), s.getZ());
        p.teleportTo(ow, s.getX() + 0.5, y, s.getZ() + 0.5, p.getYRot(), 0f);
    }

    // 地上に出た巨神を待つ仕事 (呼んだ直後はまだ読み込まれていないことがある)
    private record Wait(UUID player, int idx, java.util.function.Consumer<ServerPlayer> task, int[] left) {
    }

    private static final List<Wait> WAITS = new ArrayList<>();

    public static void whenTitan(ServerPlayer p, int idx, java.util.function.Consumer<ServerPlayer> task) {
        if (titanEntity(p.server.overworld(), idx) != null) {
            task.accept(p);
        } else {
            WAITS.add(new Wait(p.getUUID(), idx, task, new int[] {400}));
        }
    }

    static void tickWaits(MinecraftServer server) {
        if (WAITS.isEmpty()) {
            return;
        }
        for (Wait w : List.copyOf(WAITS)) {
            ServerPlayer p = server.getPlayerList().getPlayer(w.player());
            if (p == null || --w.left()[0] < 0) {
                WAITS.remove(w);
            } else if (titanEntity(server.overworld(), w.idx()) != null) {
                WAITS.remove(w);
                w.task().accept(p);
            }
        }
    }

    // その巨神が地上にいなければ呼び、近くへ降ろして道 (鎖など) を下ろさせ、そのあと then
    public static void summonRest(ServerPlayer p, int idx, java.util.function.Consumer<ServerPlayer> then) {
        toOverworld(p);
        if (titanEntity(p.server.overworld(), idx) == null) {
            run(p, "skyabyss titan summon" + suffix(idx));
        }
        whenTitan(p, idx, pp -> {
            if (run(pp, "skyabyss titan rest" + suffix(idx)) == 0 && idx > 0) {
                run(pp, "skyabyss titan rest" + suffix(idx) + " here");
            }
            then.accept(pp);
        });
    }

    public static void whale(ServerPlayer p) {
        skipOpening(p);
        summonRest(p, 0, pp -> {
            Entity whale = titanEntity(pp.server.overworld(), 0);
            if (whale != null) {
                Cinematics.start(pp, Scenes.WHALE_FIRST_SIGHT, whale, whale.position());
            }
        });
    }

    // 前の巨神の心臓は止めたことにする (乗る関門を通すため)
    public static void grantPrior(ServerPlayer p, int idx) {
        MinecraftServer server = p.server;
        for (int i = 0; i < idx; i++) {
            String t = ProgressRules.TITAN_ORDER.get(i);
            if (!Progression.stopped(server, p.getUUID(), t)) {
                Progression.onTitanStopped(server, List.<UUID>of(p.getUUID()), t);
            }
        }
    }

    public static ResourceKey<Level> back(int idx) {
        return switch (idx) {
            case 0 -> TitanTravel.TITAN_BACK;
            case 1 -> TideTravel.TIDE_BACK;
            case 2 -> StormTravel.STORM_BACK;
            default -> EmberIds.BACK;
        };
    }

    public static boolean insideBack(ServerPlayer p, int idx) {
        return p.level().dimension() == back(idx);
    }

    // 乗せる (巨神がまだ読み込まれていなければ、出てから)。乗ったあとに then
    public static void board(ServerPlayer p, int idx, java.util.function.Consumer<ServerPlayer> then) {
        if (insideBack(p, idx)) {
            then.accept(p);
            return;
        }
        grantPrior(p, idx);
        summonRest(p, idx, pp -> {
            run(pp, "skyabyss titan board" + suffix(idx));
            if (insideBack(pp, idx)) {
                then.accept(pp);
            } else {
                pp.sendSystemMessage(net.minecraft.network.chat.Component.translatable("commands.skyabyss.playtest.board_failed")
                        .withStyle(net.minecraft.ChatFormatting.RED));
            }
        });
    }

    // ---------------------------------------------------------------- 試練・移動
    public static boolean trials(MinecraftServer server, UUID group, int idx) {
        ServerLevel level = server.getLevel(back(idx));
        if (level == null) {
            return false;
        }
        switch (idx) {
            case 0 -> {
                var record = GroupProgress.of(level, group);
                for (int i = 0; i < TitanBackLayout.GOAL_SPOTS.size(); i++) {
                    record.setFlag(TitanBackTrials.STONE_FLAG + i, true);
                }
                record.markHallCleared("e1");
                record.markHallCleared("e3");
                record.markGateOpened();
                for (List<BlockPos> gate : List.of(TitanBackLayout.GATE, TitanBackLayout.E1_EXIT)) {
                    GroupGates.open(level, group, gate);
                }
                GroupGates.release(level, group, TitanBackLayout.E1_ENTRANCE);
                GroupOverlays.refresh(level, group);
            }
            case 1 -> {
                TideBackState.Group g = TideBackState.of(level, group);
                for (TideBackState.Trial t : TideBackState.Trial.values()) {
                    g.ring(t);
                }
                g.markBeckonerDefeated();
                g.markBottomOpen();
            }
            case 2 -> {
                StormProgress sp = StormProgress.of(level, group);
                for (StormBackState.Trial t : StormBackState.Trial.values()) {
                    sp.setRung(t, true);
                }
            }
            default -> {
                EmberBackState es = EmberBackState.of(level, group);
                for (EmberBackState.Trial t : EmberBackState.Trial.values()) {
                    es.ring(t);
                }
                es.markGateOpen();
            }
        }
        return true;
    }

    // 背の地点: 休み場 (心臓の前室ほか) と、真ん中 "hub" (背は外見だけなので、到着の台。trial/RouteSeals.safeFeet)
    public static List<String> spots(int idx) {
        List<String> out = new ArrayList<>();
        out.add("hub");
        switch (idx) {
            case 0 -> out.addAll(TitanBackTrials.checkpointSpawns().keySet());
            case 1 -> TideBackLayout.RESTS.forEach(r -> out.add(r.id()));
            case 2 -> out.addAll(StormBackLayout.REST_ORDER);
            default -> EmberBackLayout.RESTS.forEach(r -> out.add(r.name()));
        }
        out.sort(null);
        return out;
    }

    // 地点の立つ位置と向き (知らない名前なら null)
    public record SpotAt(BlockPos pos, float yaw) {
    }

    @Nullable
    public static SpotAt spotAt(ServerLevel level, int idx, String spot, float yaw) {
        if (spot.equals("hub")) {
            return new SpotAt(jp.revati.skyabyss.trial.RouteSeals.safeFeet(ProgressRules.TITAN_ORDER.get(idx)), yaw);
        }
        BlockPos at = null;
        switch (idx) {
            case 0 -> {
                at = TitanBackTrials.checkpointSpawns().get(spot);
                if (at != null) {
                    at = TitanBackTrials.safeSpot(level, at);
                }
            }
            case 1 -> {
                for (var r : TideBackLayout.RESTS) {
                    if (r.id().equals(spot)) {
                        at = r.spawn();
                    }
                }
            }
            case 2 -> at = StormBackLayout.REST.get(spot);
            default -> {
                for (var r : EmberBackLayout.RESTS) {
                    if (r.name().equals(spot)) {
                        at = r.floor().above();
                        yaw = r.yaw();
                    }
                }
            }
        }
        return at == null ? null : new SpotAt(at, yaw);
    }

    public static boolean gotoSpot(ServerPlayer p, int idx, String spot) {
        ServerLevel level = p.server.getLevel(back(idx));
        if (level == null) {
            return false;
        }
        SpotAt at = spotAt(level, idx, spot, p.getYRot());
        if (at == null) {
            return false;
        }
        p.fallDistance = 0;
        p.teleportTo(level, at.pos().getX() + 0.5, at.pos().getY(), at.pos().getZ() + 0.5, at.yaw(), 0f);
        return true;
    }

    // ---------------------------------------------------------------- 装備
    // tier 0 = 岩鯨に挑む装備 (鉄)、1 = 潮殻 (岩鯨の装備)、2 = 雷翼 (潮殻)、3 = 余燼 (雷翼)、4 = 全部終えた (余燼)
    public static void gear(ServerPlayer p, int tier) {
        tier = Math.max(0, Math.min(4, tier));
        Growth.setLevel(p, tier >= 4 ? 40 : ProgressRules.TITAN_LEVEL[tier]);
        List<ItemStack> give = new ArrayList<>();
        if (tier == 0) {
            equip(p, new ItemStack(jp.revati.skyabyss.item.outfit.OutfitGear.IRONCLAD_HELMET.get()), new ItemStack(jp.revati.skyabyss.item.outfit.OutfitGear.IRONCLAD_CHESTPLATE.get()), new ItemStack(jp.revati.skyabyss.item.outfit.OutfitGear.IRONCLAD_LEGGINGS.get()),
                    new ItemStack(jp.revati.skyabyss.item.outfit.OutfitGear.IRONCLAD_BOOTS.get()));
            give.add(new ItemStack(jp.revati.skyabyss.rpg.skill.JobChoice.SWORD.get()));
            give.add(new ItemStack(jp.revati.skyabyss.rpg.skill.JobChoice.BOW.get()));
        } else {
            TitanGearSet set = TitanGearSets.get(ProgressRules.TITAN_ORDER.get(tier - 1));
            if (set != null) {
                List<ItemStack> armor = new ArrayList<>();
                for (TitanGearSet.Piece piece : set.pieces()) {
                    TitanGearSet.item(piece.id()).ifPresent(item -> {
                        ItemStack s = new ItemStack(item);
                        if (item instanceof ArmorItem) {
                            armor.add(s);
                        } else {
                            give.add(s);
                        }
                    });
                }
                equip(p, armor.toArray(new ItemStack[0]));
            }
        }
        give.add(new ItemStack(jp.revati.skyabyss.item.outfit.ConsumableGear.TRAVEL_ARROW.get(), 64));
        give.add(new ItemStack(jp.revati.skyabyss.item.outfit.OutfitGear.TRAVEL_SHIELD.get()));
        give.add(new ItemStack(jp.revati.skyabyss.item.outfit.ConsumableGear.SMOKED_MEAT.get(), 48));
        give.add(new ItemStack(jp.revati.skyabyss.guide.Ch2Gear.PEARL_POWDER.get(), 8));
        give.add(new ItemStack(jp.revati.skyabyss.item.outfit.ConsumableGear.STRONG_HEALING.get(), 4));
        for (ItemStack s : give) {
            Quests.give(p, s);
        }
        // 目安の体力 (備えの灯が緑になる値)
        double goal = Readiness.goalHearts(ProgressRules.TITAN_ORDER.get(Math.min(tier, 3))) * 2;
        AttributeInstance max = p.getAttribute(Attributes.MAX_HEALTH);
        if (max != null) {
            max.removeModifier(HEALTH_MOD);
            double delta = goal - max.getValue();
            if (delta > 0) {
                max.addOrReplacePermanentModifier(new AttributeModifier(HEALTH_MOD, delta, AttributeModifier.Operation.ADD_VALUE));
            }
        }
        heal(p);
    }

    private static void equip(ServerPlayer p, ItemStack... armor) {
        for (ItemStack s : armor) {
            if (s.getItem() instanceof ArmorItem a) {
                EquipmentSlot slot = a.getEquipmentSlot();
                p.setItemSlot(slot, s);
            }
        }
    }

    public static void heal(ServerPlayer p) {
        p.setHealth(p.getMaxHealth());
        p.getFoodData().setFoodLevel(20);
        p.getFoodData().setSaturation(20f);
        p.removeAllEffects();
        p.clearFire();
        p.fallDistance = 0;
    }

    // ---------------------------------------------------------------- 章・リセット・そのほか
    public static void chapter(ServerPlayer p, int n) {
        int idx = n - 1;
        skipOpening(p);
        grantPrior(p, idx);
        gear(p, idx);
        for (int i = 1; i <= idx; i++) {
            Quests.start(p, MAIN[i]);
        }
        summonRest(p, idx, pp -> {
        });
    }

    public static void reset(ServerPlayer p) {
        String id = p.getUUID().toString();
        OpeningDirector.reset(p);
        Quests.forgetAll(p);
        Cinematics.state(p).forgetAll();
        run(p, "skyabyss lore reset " + id);
        run(p, "skyabyss story reset " + id);
        for (String t : ProgressRules.TITAN_ORDER) {
            run(p, "skyabyss progress revoke " + id + " " + t);
        }
        run(p, "skyabyss level reset " + id);
        AttributeInstance max = p.getAttribute(Attributes.MAX_HEALTH);
        if (max != null) {
            max.removeModifier(HEALTH_MOD);
        }
        god(p, false);
        heal(p);
    }

    public static void god(ServerPlayer p, boolean on) {
        p.getAbilities().invulnerable = on || p.getAbilities().instabuild;
        p.onUpdateAbilities();
    }

    public static void day(MinecraftServer server, int n) {
        server.overworld().setDayTime(Math.max(0, n) * 24000L + 1000L);
    }

    public static int fast(MinecraftServer server, boolean on) {
        try {
            return server.getCommands().getDispatcher().execute("tick rate " + (on ? 40 : 20), server.createCommandSourceStack().withSuppressedOutput());
        } catch (CommandSyntaxException e) {
            return 0;
        }
    }

    public static Set<UUID> groupsOf(Collection<ServerPlayer> targets) {
        Set<UUID> out = new HashSet<>();
        for (ServerPlayer p : targets) {
            out.add(Groups.keyOf(p));
        }
        return out;
    }

    // ---------------------------------------------------------------- いま・つぎ
    // 今の目的の「次の出来事」を、本物の仕組みで今すぐ起こす。返すのは言語キー (引数は無し)
    public static String now(ServerPlayer p, boolean next) {
        try {
            return nowInner(p, next);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.warn("playtest now failed", e);
            return "commands.skyabyss.playtest.now.unknown";
        }
    }

    // 一日の中の時刻 (tick) へ。日付は変えない
    private static void timeOfDay(MinecraftServer server, long tod) {
        ServerLevel ow = server.overworld();
        ow.setDayTime(Math.floorDiv(ow.getDayTime(), 24000L) * 24000L + tod);
    }

    private static String nowInner(ServerPlayer p, boolean next) {
        var st = OpeningDirector.state(p);
        OpeningBeat open = null;
        for (OpeningBeat b : OpeningBeat.values()) {
            if (!st.done(b)) {
                open = b;
                break;
            }
        }
        if (open != null) {
            if (next && open != OpeningBeat.WHALE) {
                // 今の拍子を済ませてから、その次を起こす
                OpeningDirector.force(p, open);
                if (open == OpeningBeat.STORYTELLER) {
                    NpcBonds.of(p).bond(NpcSetup.STORYTELLER_ID).flags().add("met");
                } else if (open == OpeningBeat.ISANA) {
                    NpcBonds.of(p).bond(NpcSetup.WHALE_CHASER_ID).flags().add("met");
                }
                NpcBonds.sync(p);
                return now(p, false);
            }
            toOverworld(p);
            switch (open) {
                case FOOTSTEPS, SONG -> timeOfDay(p.server, 14000);
                case WHALE -> timeOfDay(p.server, 12000);
                default -> {
                }
            }
            switch (open) {
                case STORYTELLER -> run(p, "execute positioned ^ ^ ^2 run skyabyss npc spawn storyteller");
                case ISANA -> run(p, "execute positioned ^ ^ ^2 run skyabyss npc spawn whale_chaser");
                case WHALE -> whale(p);
                case SHADOW, FOOTSTEPS, SONG -> {
                    OpeningDirector.force(p, open);
                }
                default -> OpeningDirector.force(p, open);
            }
            return "commands.skyabyss.playtest.now." + open.id;
        }
        int stage = Progression.stage(p);
        if (stage >= ProgressRules.TITAN_ORDER.size()) {
            return "commands.skyabyss.playtest.now.all_done";
        }
        if (next) {
            grantPrior(p, stage + 1);
            stage = Progression.stage(p);
            if (stage >= ProgressRules.TITAN_ORDER.size()) {
                return "commands.skyabyss.playtest.now.all_done";
            }
        }
        int idx = stage;
        if (insideBack(p, idx)) {
            trials(p.server, Groups.keyOf(p), idx);
            gotoSpot(p, idx, "heart");
            return "commands.skyabyss.playtest.now.heart";
        }
        if (Titans.titanOfBack(p.level().dimension()) != null) {
            toOverworld(p);
        }
        // 四章の支度の段 (火除けの薬・炉跡) が残っていれば、先にそちらを一歩進める (story/ch4/Ch4Playtest)
        if (idx == 3 && !next) {
            String ch4 = jp.revati.skyabyss.story.ch4.Ch4Playtest.now(p, false);
            if (ch4 != null) {
                return ch4;
            }
        }
        summonRest(p, idx, pp -> {
        });
        return "commands.skyabyss.playtest.now.titan";
    }
}
