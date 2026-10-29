package jp.revati.skyabyss.story.ch6;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 開発・撮影: /skyabyss moonworld enter | leave | gate (門へ行く) | phase (今の位相) | phase <0〜3> (位相の頭へ進める)
//   新月の谷: valley <印> (谷の印へ行く。camp・arena_center・shard_bridge…) | lamp (足もとに星の印) | lord (主の間に影抱き) | clear (主の間を片付ける)
//   上弦の崖: cliff <印> (崖の印へ行く。camp・shard_tower・ledge_a・plate_w・cliff_gate・arena_entry…) | cliff_lord (主の間に月秤) |
//             cliff_clear (主の間を片付ける) | lightbody [off] (星術「軽身」を覚える / 忘れる)
//   満月の野: field <印> (野の印へ行く。camp・pond_entry・gully_entry・tower_entry・field_gate・arena_entry…) | field_lord (主の間に望の灯守) |
//             field_clear (主の間を片付ける) | stop [off] (星術「一瞬止め」を覚えて砂時計をもらう / 忘れる) | tide <bright|to_dim|dim|to_bright> (満ち引きの頭へ)
//   下弦の淵: rift <印> (淵の印へ行く。camp・door_entry・leap_entry・hall_entry・gate_front・arena_entry…) | rift_lord (主の間に残月の剣守) |
//             rift_clear (主の間を片付ける) | echo (鏡の中で待つ自分の残像を放つ)
//   月の核: core <印> (core_gate・gate_front・arena_entry・arena_center…) | core_lord (核を呼ぶ) | core_allies (四人を立たせる) | core_clear
//   位相の挑戦: trial <valley|cliff|field|rift|core> <段> (その主の間へ跳び、段の強さの主を呼ぶ)
//   撮影: pose <動き> (まわり 24 の月の敵を止めて、その動きを流す。向きは今の向きのまま)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class MoonCommands {
    private MoonCommands() {
    }

    @SubscribeEvent
    static void onRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("skyabyss").requires(s -> s.hasPermission(2))
                .then(Commands.literal("moonworld")
                        .then(Commands.literal("enter").executes(c -> MoonWorld.enter(c.getSource().getPlayerOrException()) ? 1 : 0))
                        .then(Commands.literal("leave").executes(c -> {
                            MoonWorld.leave(c.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.literal("gate").executes(c -> {
                            BlockPos g = MoonGate.gatePos(c.getSource().getServer());
                            if (g == null) {
                                c.getSource().sendFailure(Component.literal("the capital has no moon gate (built before the gate existed?)"));
                                return 0;
                            }
                            c.getSource().getPlayerOrException().teleportTo(c.getSource().getServer().overworld(), g.getX() + 0.5, g.getY(), g.getZ() + 2.5, 180f, 0f);
                            return 1;
                        }))
                        .then(Commands.literal("valley").then(Commands.argument("mark", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c -> {
                            net.minecraft.server.level.ServerPlayer p = c.getSource().getPlayerOrException();
                            ServerLevel moon = c.getSource().getServer().getLevel(MoonWorld.LEVEL);
                            if (moon == null) {
                                return 0;
                            }
                            MoonWorld.build(moon);
                            BlockPos m = jp.revati.skyabyss.story.ch6.valley.MoonValley.mark(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "mark"));
                            p.teleportTo(moon, m.getX() + 0.5, m.getY(), m.getZ() + 0.5, p.getYRot(), p.getXRot());
                            return 1;
                        })))
                        .then(Commands.literal("cliff").then(Commands.argument("mark", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c -> {
                            net.minecraft.server.level.ServerPlayer p = c.getSource().getPlayerOrException();
                            ServerLevel moon = c.getSource().getServer().getLevel(MoonWorld.LEVEL);
                            if (moon == null) {
                                return 0;
                            }
                            MoonWorld.build(moon);
                            BlockPos m = jp.revati.skyabyss.story.ch6.cliff.MoonCliff.mark(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "mark"));
                            p.teleportTo(moon, m.getX() + 0.5, m.getY(), m.getZ() + 0.5, p.getYRot(), p.getXRot());
                            return 1;
                        })))
                        .then(Commands.literal("cliff_lord").executes(c -> {
                            ServerLevel moon = c.getSource().getServer().getLevel(MoonWorld.LEVEL);
                            return moon != null && jp.revati.skyabyss.story.ch6.cliff.CliffArena.summon(moon) != null ? 1 : 0;
                        }))
                        .then(Commands.literal("cliff_clear").executes(c -> {
                            ServerLevel moon = c.getSource().getServer().getLevel(MoonWorld.LEVEL);
                            if (moon != null) {
                                jp.revati.skyabyss.story.ch6.cliff.CliffArena.clear(moon);
                            }
                            return 1;
                        }))
                        .then(Commands.literal("field").then(Commands.argument("mark", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c -> {
                            net.minecraft.server.level.ServerPlayer p = c.getSource().getPlayerOrException();
                            ServerLevel moon = c.getSource().getServer().getLevel(MoonWorld.LEVEL);
                            if (moon == null) {
                                return 0;
                            }
                            MoonWorld.build(moon);
                            BlockPos m = jp.revati.skyabyss.story.ch6.field.MoonField.mark(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "mark"));
                            p.teleportTo(moon, m.getX() + 0.5, m.getY(), m.getZ() + 0.5, p.getYRot(), p.getXRot());
                            return 1;
                        })))
                        .then(Commands.literal("rift").then(Commands.argument("mark", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c -> {
                            net.minecraft.server.level.ServerPlayer p = c.getSource().getPlayerOrException();
                            ServerLevel moon = c.getSource().getServer().getLevel(MoonWorld.LEVEL);
                            if (moon == null) {
                                return 0;
                            }
                            MoonWorld.build(moon);
                            BlockPos m = jp.revati.skyabyss.story.ch6.rift.MoonRift.mark(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "mark"));
                            p.teleportTo(moon, m.getX() + 0.5, m.getY(), m.getZ() + 0.5, p.getYRot(), p.getXRot());
                            return 1;
                        })))
                        .then(Commands.literal("core").then(Commands.argument("mark", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c -> {
                            net.minecraft.server.level.ServerPlayer p = c.getSource().getPlayerOrException();
                            ServerLevel moon = c.getSource().getServer().getLevel(MoonWorld.LEVEL);
                            if (moon == null) {
                                return 0;
                            }
                            MoonWorld.build(moon);
                            BlockPos m = jp.revati.skyabyss.story.ch6.core.MoonCore.mark(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "mark"));
                            p.teleportTo(moon, m.getX() + 0.5, m.getY(), m.getZ() + 0.5, p.getYRot(), p.getXRot());
                            return 1;
                        })))
                        .then(Commands.literal("trial").then(Commands.argument("lord", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .then(Commands.argument("tier", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, MoonTrial.MAX_TIER)).executes(c -> {
                                    net.minecraft.server.level.ServerPlayer p = c.getSource().getPlayerOrException();
                                    return MoonTrial.start(p, com.mojang.brigadier.arguments.StringArgumentType.getString(c, "lord"),
                                            com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "tier")) != null ? 1 : 0;
                                }))))
                        .then(Commands.literal("core_lord").executes(c -> {
                            ServerLevel moon = c.getSource().getServer().getLevel(MoonWorld.LEVEL);
                            return moon != null && jp.revati.skyabyss.story.ch6.core.CoreArena.summon(moon) != null ? 1 : 0;
                        }))
                        .then(Commands.literal("core_allies").executes(c -> {
                            ServerLevel moon = c.getSource().getServer().getLevel(MoonWorld.LEVEL);
                            if (moon != null) {
                                jp.revati.skyabyss.story.ch6.core.CoreArena.allies(moon);
                            }
                            return 1;
                        }))
                        .then(Commands.literal("core_clear").executes(c -> {
                            ServerLevel moon = c.getSource().getServer().getLevel(MoonWorld.LEVEL);
                            if (moon != null) {
                                jp.revati.skyabyss.story.ch6.core.CoreArena.clear(moon);
                            }
                            return 1;
                        }))
                        .then(Commands.literal("rift_lord").executes(c -> {
                            ServerLevel moon = c.getSource().getServer().getLevel(MoonWorld.LEVEL);
                            return moon != null && jp.revati.skyabyss.story.ch6.rift.RiftArena.summon(moon) != null ? 1 : 0;
                        }))
                        .then(Commands.literal("rift_clear").executes(c -> {
                            ServerLevel moon = c.getSource().getServer().getLevel(MoonWorld.LEVEL);
                            if (moon != null) {
                                jp.revati.skyabyss.story.ch6.rift.RiftArena.clear(moon);
                            }
                            return 1;
                        }))
                        .then(Commands.literal("echo").executes(c -> {
                            net.minecraft.server.level.ServerPlayer p = c.getSource().getPlayerOrException();
                            return jp.revati.skyabyss.story.ch6.rift.RiftEcho.releaseFor(p) != null ? 1 : 0;
                        }))
                        .then(Commands.literal("field_lord").executes(c -> {
                            ServerLevel moon = c.getSource().getServer().getLevel(MoonWorld.LEVEL);
                            return moon != null && jp.revati.skyabyss.story.ch6.field.FieldArena.summon(moon) != null ? 1 : 0;
                        }))
                        .then(Commands.literal("field_clear").executes(c -> {
                            ServerLevel moon = c.getSource().getServer().getLevel(MoonWorld.LEVEL);
                            if (moon != null) {
                                jp.revati.skyabyss.story.ch6.field.FieldArena.clear(moon);
                            }
                            return 1;
                        }))
                        .then(Commands.literal("stop").executes(c -> {
                            net.minecraft.server.level.ServerPlayer p = c.getSource().getPlayerOrException();
                            jp.revati.skyabyss.story.ch6.field.MomentStop.learn(p);
                            if (!jp.revati.skyabyss.story.ch6.field.MoonHourglassItem.carries(p)) {
                                p.getInventory().add(new net.minecraft.world.item.ItemStack(jp.revati.skyabyss.story.ch6.field.FieldBlocks.MOON_HOURGLASS.get()));
                            }
                            return 1;
                        }).then(Commands.literal("off").executes(c -> {
                            jp.revati.skyabyss.story.ch6.field.MomentStop.forget(c.getSource().getPlayerOrException());
                            return 1;
                        })))
                        .then(Commands.literal("tide").then(Commands.argument("at", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c -> {
                            ServerLevel moon = c.getSource().getServer().getLevel(MoonWorld.LEVEL);
                            if (moon == null) {
                                return 0;
                            }
                            jp.revati.skyabyss.story.ch6.field.FieldTide.setTo(moon, jp.revati.skyabyss.story.ch6.field.FieldTide.Tide.valueOf(
                                    com.mojang.brigadier.arguments.StringArgumentType.getString(c, "at").toUpperCase(java.util.Locale.ROOT)), 0);
                            return 1;
                        })))
                        .then(Commands.literal("lightbody").executes(c -> {
                            jp.revati.skyabyss.story.ch6.cliff.LightBody.learn(c.getSource().getPlayerOrException());
                            return 1;
                        }).then(Commands.literal("off").executes(c -> {
                            jp.revati.skyabyss.story.ch6.cliff.LightBody.forget(c.getSource().getPlayerOrException());
                            return 1;
                        })))
                        .then(Commands.literal("pose").then(Commands.argument("action", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c -> {
                            net.minecraft.server.level.ServerPlayer p = c.getSource().getPlayerOrException();
                            String act = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "action");
                            int n = 0;
                            for (jp.revati.skyabyss.story.ch6.mob.MoonMob m : p.serverLevel().getEntitiesOfClass(jp.revati.skyabyss.story.ch6.mob.MoonMob.class,
                                    p.getBoundingBox().inflate(24))) {
                                m.setNoAi(true);
                                if (m.hasAction(act)) {
                                    m.playAction(act);
                                    n++;
                                }
                            }
                            int done = n;
                            c.getSource().sendSuccess(() -> Component.literal("posed " + done), false);
                            return n;
                        })))
                        .then(Commands.literal("lamp").executes(c -> {
                            net.minecraft.server.level.ServerPlayer p = c.getSource().getPlayerOrException();
                            jp.revati.skyabyss.story.ch6.valley.StarLamps.place(p.serverLevel(), p.blockPosition(), p.getUUID());
                            return 1;
                        }))
                        .then(Commands.literal("lord").executes(c -> {
                            ServerLevel moon = c.getSource().getServer().getLevel(MoonWorld.LEVEL);
                            return moon != null && jp.revati.skyabyss.story.ch6.valley.ValleyArena.summon(moon) != null ? 1 : 0;
                        }))
                        .then(Commands.literal("clear").executes(c -> {
                            ServerLevel moon = c.getSource().getServer().getLevel(MoonWorld.LEVEL);
                            if (moon != null) {
                                jp.revati.skyabyss.story.ch6.valley.ValleyArena.clear(moon);
                            }
                            return 1;
                        }))
                        .then(Commands.literal("phase").executes(c -> {
                            ServerLevel moon = c.getSource().getServer().getLevel(MoonWorld.LEVEL);
                            int ph = moon == null ? -1 : MoonClock.phase(moon);
                            c.getSource().sendSuccess(() -> Component.literal("moon phase " + (ph < 0 ? "-" : MoonClock.PHASES[ph] + " (" + MoonClock.untilNext(MoonClock.time(moon)) / 20 + " s to the next)")), false);
                            return 1;
                        }).then(Commands.argument("n", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 3)).executes(c -> {
                            ServerLevel moon = c.getSource().getServer().getLevel(MoonWorld.LEVEL);
                            if (moon == null) {
                                return 0;
                            }
                            MoonClock.skipTo(moon, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "n"));
                            return 1;
                        })))));
    }
}
