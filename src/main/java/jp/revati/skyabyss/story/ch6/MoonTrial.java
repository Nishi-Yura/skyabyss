package jp.revati.skyabyss.story.ch6;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.mojang.brigadier.arguments.StringArgumentType;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.story.ch6.core.CoreArena;
import jp.revati.skyabyss.story.ch6.core.CorePuzzles;
import jp.revati.skyabyss.story.ch6.core.MoonCore;
import jp.revati.skyabyss.story.ch6.gear.MoonGearLoot;
import jp.revati.skyabyss.story.ch6.mob.FullKeeper;
import jp.revati.skyabyss.story.ch6.mob.MoonCoreBoss;
import jp.revati.skyabyss.story.ch6.mob.MoonMob;
import jp.revati.skyabyss.story.ch6.mob.MoonScale;
import jp.revati.skyabyss.story.ch6.mob.ShadowEmbracer;
import jp.revati.skyabyss.story.ch6.mob.WaningBlade;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

// 六章のクリア後「位相の挑戦」(docs/design/46 の 4.8)。月の核を止めた人が、着く所の南の月の暦 (MoonCalendarBlock) から五つの主に強くして挑む。
//   段は主ごと・人ごとに 1〜5 (越えた段の次。記録は人の持ちもの skyabyss_moon_trial.<主>)。体力 ×(1 + 0.15 × 段)・傷 ×(1 + 0.08 × 段)、
//   どの段でも合った位相の姿。報酬は MoonGearLoot (区画の主 = rollLord に段、核 = rollCore に段)。
//   ふだんの主 (挑戦でない) の報酬もここで配る (段 0)。倒した主のまわり 32 の人に
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class MoonTrial {
    public static final String TAG = "skyabyss_moon_trial";
    public static final String TIER = "skyabyss_moon_trial_tier";
    public static final String RECORD = "skyabyss_moon_trial.";
    public static final int MAX_TIER = 5;
    public static final List<String> LORDS = List.of("valley", "cliff", "field", "rift", "core");
    static final double NEAR_CALENDAR = 8;

    private MoonTrial() {
    }

    // 月の暦 (着く所の南 7)
    public static BlockPos calendar() {
        return MoonWorld.ARRIVAL.offset(0, 0, 7);
    }

    public static boolean open(ServerPlayer p) {
        QuestLog.Entry e = QuestLog.of(p).entry(CorePuzzles.QUEST);
        return e != null && e.done;
    }

    public static int best(ServerPlayer p, String lord) {
        return p.getPersistentData().getInt(RECORD + lord);
    }

    // 次に挑む段 (越えた段の次。五段のあとは五段)
    public static int next(ServerPlayer p, String lord) {
        return Math.min(MAX_TIER, best(p, lord) + 1);
    }

    public static double hpScale(int tier) {
        return 1 + 0.15 * tier;
    }

    public static double dmgScale(int tier) {
        return 1 + 0.08 * tier;
    }

    // ---------------------------------------------------------------- 月の暦に触れた: 五つの挑戦の名前と段をチャットに
    public static void menu(ServerPlayer p) {
        if (!open(p)) {
            Notice.narrate(p, "message.skyabyss.moon_trial.sealed");
            return;
        }
        p.sendSystemMessage(Component.translatable("message.skyabyss.moon_trial.title").withStyle(ChatFormatting.AQUA));
        for (String lord : LORDS) {
            int t = next(p, lord);
            MutableComponent line = Component.literal("  [").append(Component.translatable("moon_trial.skyabyss." + lord))
                    .append(Component.translatable("message.skyabyss.moon_trial.tier", t)).append("]");
            line.setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE).withUnderlined(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/skyabyss_moon_trial " + lord))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.skyabyss.moon_trial.hover", best(p, lord)))));
            p.sendSystemMessage(line);
        }
    }

    // ---------------------------------------------------------------- 挑む (その主の間の入口へ、強い主を呼ぶ)
    @Nullable
    public static MoonMob start(ServerPlayer p, String lord, int tier) {
        ServerLevel level = p.server.getLevel(MoonWorld.LEVEL);
        if (level == null || !LORDS.contains(lord)) {
            return null;
        }
        MoonWorld.build(level);
        MoonMob boss = active(level, lord);
        if (boss != null && !boss.getTags().contains(TAG)) {
            Notice.narrate(p, "message.skyabyss.moon_trial.busy");
            return null;
        }
        BlockPos entry = entry(lord);
        p.teleportTo(level, entry.getX() + 0.5, entry.getY(), entry.getZ() + 0.5, p.getYRot(), 0f);
        switch (lord) {
            case "field" -> p.addTag(jp.revati.skyabyss.story.ch6.field.FieldArena.TAG);
            case "rift" -> p.addTag(jp.revati.skyabyss.story.ch6.rift.RiftArena.TAG);
            case "core" -> p.addTag(CoreArena.TAG);
            default -> {
            }
        }
        if (boss != null) {
            return boss;
        }
        MoonMob m = summon(level, lord);
        if (m == null) {
            return null;
        }
        boost(m, lord, tier);
        Notice.place(p, Component.translatable("moon_trial.skyabyss." + lord), Component.translatable("message.skyabyss.moon_trial.tier", tier));
        return m;
    }

    static BlockPos entry(String lord) {
        return switch (lord) {
            case "valley" -> jp.revati.skyabyss.story.ch6.valley.MoonValley.mark("arena_entry");
            case "cliff" -> jp.revati.skyabyss.story.ch6.cliff.MoonCliff.mark("arena_entry");
            case "field" -> jp.revati.skyabyss.story.ch6.field.MoonField.mark("arena_entry");
            case "rift" -> jp.revati.skyabyss.story.ch6.rift.MoonRift.mark("arena_entry");
            default -> MoonCore.mark("arena_entry");
        };
    }

    @Nullable
    static MoonMob active(ServerLevel level, String lord) {
        List<? extends MoonMob> l = switch (lord) {
            case "valley" -> jp.revati.skyabyss.story.ch6.valley.ValleyArena.lords(level);
            case "cliff" -> jp.revati.skyabyss.story.ch6.cliff.CliffArena.lords(level);
            case "field" -> jp.revati.skyabyss.story.ch6.field.FieldArena.lords(level);
            case "rift" -> jp.revati.skyabyss.story.ch6.rift.RiftArena.lords(level);
            default -> CoreArena.lords(level);
        };
        return l.isEmpty() ? null : l.get(0);
    }

    @Nullable
    static MoonMob summon(ServerLevel level, String lord) {
        return switch (lord) {
            case "valley" -> jp.revati.skyabyss.story.ch6.valley.ValleyArena.summon(level);
            case "cliff" -> jp.revati.skyabyss.story.ch6.cliff.CliffArena.summon(level);
            case "field" -> jp.revati.skyabyss.story.ch6.field.FieldArena.summon(level);
            case "rift" -> jp.revati.skyabyss.story.ch6.rift.RiftArena.summon(level);
            default -> CoreArena.summon(level);
        };
    }

    // 段の強さ: 合った位相の姿で数字を入れ直し、体力と傷に段の倍率 (体力は 1024 で止める)
    public static void boost(MoonMob m, String lord, int tier) {
        int section = switch (lord) {
            case "valley" -> 0;
            case "cliff" -> 1;
            case "field" -> 2;
            default -> 3;
        };
        double hp = switch (lord) {
            case "valley" -> ShadowEmbracer.HP;
            case "cliff" -> MoonScale.HP;
            case "field" -> FullKeeper.HP;
            case "rift" -> WaningBlade.HP;
            default -> MoonCoreBoss.HP;
        };
        double dmg = switch (lord) {
            case "valley" -> ShadowEmbracer.DMG;
            case "cliff" -> MoonScale.DMG;
            case "field" -> FullKeeper.DMG;
            case "rift" -> WaningBlade.DMG;
            default -> MoonCoreBoss.DMG;
        };
        m.tuneMoon(section, true, false, hp / MoonMob.SECTION_HP[section], dmg / MoonMob.SECTION_DMG[section] * dmgScale(tier));
        double max = Math.min(1024, m.getMaxHealth() * hpScale(tier));
        m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(max);
        m.setHealth((float) max);
        m.addTag(TAG);
        m.getPersistentData().putInt(TIER, tier);
        if (m.overlaid() && m.level() instanceof ServerLevel sl) {
            if (m instanceof WaningBlade b) {
                for (WaningBlade e : sl.getEntitiesOfClass(WaningBlade.class, b.getBoundingBox().inflate(48), WaningBlade::echo)) {
                    e.quicken();
                }
            }
            for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, m.getBoundingBox().inflate(48), q -> q.isAlive())) {
                Notice.alert(p, "message.skyabyss.moon_trial.overlay." + lordOf(m));
            }
        }
    }

    // ---------------------------------------------------------------- 倒れた: 報酬と段の記録 (ふだんの主も報酬だけ)
    @Nullable
    public static String lordOf(LivingEntity e) {
        if (e instanceof ShadowEmbracer) {
            return "valley";
        }
        if (e instanceof MoonScale) {
            return "cliff";
        }
        if (e instanceof FullKeeper) {
            return "field";
        }
        if (e instanceof WaningBlade b && !b.echo()) {
            return "rift";
        }
        if (e instanceof MoonCoreBoss) {
            return "core";
        }
        return null;
    }

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        LivingEntity e = event.getEntity();
        if (!(e.level() instanceof ServerLevel level) || level.dimension() != MoonWorld.LEVEL) {
            return;
        }
        String lord = lordOf(e);
        if (lord == null) {
            return;
        }
        finish(level, e, lord, new ArrayList<>(level.getEntitiesOfClass(ServerPlayer.class, e.getBoundingBox().inflate(32), q -> q.isAlive() && !q.isSpectator())));
    }

    // 倒れた主のまわりの人へ報酬と段の記録 (自己テストは人を直に渡す)
    public static void finish(ServerLevel level, LivingEntity e, String lord, List<ServerPlayer> near) {
        boolean trial = e.getTags().contains(TAG);
        int tier = trial ? e.getPersistentData().getInt(TIER) : 0;
        reward(level, lord, tier, near);
        MoonMemories.onLord(near, lord);
        if (trial) {
            for (ServerPlayer p : near) {
                if (tier > best(p, lord)) {
                    p.getPersistentData().putInt(RECORD + lord, tier);
                }
                if (tier >= MAX_TIER) {
                    MoonMemories.onKeepsake(p, lord);
                }
                Notice.narrate(p, "message.skyabyss.moon_trial.cleared", Component.translatable("moon_trial.skyabyss." + lord), tier);
            }
        }
    }

    public static void reward(ServerLevel level, String lord, int tier, List<ServerPlayer> players) {
        if (players.isEmpty()) {
            return;
        }
        if (lord.equals("core")) {
            MoonGearLoot.core(level, players, tier);
        } else {
            MoonGearLoot.lord(level, lord, tier, players);
        }
    }

    // ---------------------------------------------------------------- 命令 (月の暦のチャットの押しどころ)
    @SubscribeEvent
    static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("skyabyss_moon_trial")
                .then(Commands.argument("lord", StringArgumentType.word()).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    String lord = StringArgumentType.getString(c, "lord");
                    if (!open(p) || p.level().dimension() != MoonWorld.LEVEL || !p.blockPosition().closerThan(calendar(), NEAR_CALENDAR)) {
                        Notice.narrate(p, "message.skyabyss.moon_trial.far");
                        return 0;
                    }
                    return start(p, lord, next(p, lord)) != null ? 1 : 0;
                })));
    }
}
