package jp.revati.skyabyss.dev;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.entity.StoneWhaleHeart;
import jp.revati.skyabyss.entity.WhaleboneCaptain;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 開発用: /skyabyss dev force <対象> <技> [人数]
// 番兵長 (SWEEP / LEAP / ERUPT / ROAR) と岩鯨の心臓 (THRUST / SWEEP / LEAP / CRACK / RECLOSE / VOLLEY / BEAM / CHAIN / SHELL_DROP) の技を一つ出し、
// 次の技は始めない (台本のスクショで予告の途中を撮るため)。人数 (心臓だけ) は、心臓の間にいる人数を実際より多いことにする (一人で複数人の技を見る)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class BossForceDev {
    private BossForceDev() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        event.getDispatcher().register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("dev").then(Commands.literal("force")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .then(Commands.argument("action", StringArgumentType.word())
                                        .executes(ctx -> run(EntityArgument.getEntities(ctx, "targets"), StringArgumentType.getString(ctx, "action"), 0))
                                        .then(Commands.argument("party", IntegerArgumentType.integer(1, 6))
                                                .executes(ctx -> run(EntityArgument.getEntities(ctx, "targets"), StringArgumentType.getString(ctx, "action"),
                                                        IntegerArgumentType.getInteger(ctx, "party")))))))));
    }

    private static int run(java.util.Collection<? extends Entity> targets, String action, int party) {
        int n = 0;
        for (Entity e : targets) {
            if (forceHeartExtra(e, action)) {
                n++;
            } else if (e instanceof WhaleboneCaptain captain) {
                captain.holdAttacks();
                var nearest = captain.level().getNearestPlayer(captain, 60);
                if (nearest != null) {
                    captain.setDevAim(nearest.position());
                }
                captain.forceAction(action);
                n++;
            } else if (e instanceof StoneWhaleHeart heart) {
                heart.holdAttacks();
                heart.setPartyOverride(party);
                heart.forceAttack(action);
                n++;
            } else if (e instanceof jp.revati.skyabyss.titan.storm.heart.StormHeart storm) {
                storm.holdAttacks();
                storm.setPartyOverride(party);
                storm.forceAction(jp.revati.skyabyss.titan.storm.heart.StormHeart.Action.valueOf(action.toUpperCase(java.util.Locale.ROOT)));
                n++;
            } else if (e instanceof jp.revati.skyabyss.titan.tide.heart.TideHeart tide) {
                tide.holdAttacks();
                tide.setPartyOverride(party);
                tide.forceAction(jp.revati.skyabyss.titan.tide.heart.TideHeart.Action.valueOf(action.toUpperCase(java.util.Locale.ROOT)));
                n++;
            } else if (e instanceof jp.revati.skyabyss.titan.ember.heart.EmberHeart ember) {
                ember.holdAttacks(true);
                ember.forceAction(jp.revati.skyabyss.titan.ember.heart.EmberHeart.Action.valueOf(action.toUpperCase(java.util.Locale.ROOT)));
                n++;
            } else if (e instanceof jp.revati.skyabyss.titan.storm.raiju.Raiju raiju) {
                raiju.holdAttacks();
                raiju.setPartyOverride(party);
                raiju.forceAction(jp.revati.skyabyss.titan.storm.raiju.Raiju.Action.valueOf(action.toUpperCase(java.util.Locale.ROOT)));
                n++;
            }
        }
        return n;
    }

    // 回り続けへの追い技 (LEAD / CUTOFF / CHASE。いちばん近い人へ) と領域 (DOMAIN) を、四つの心臓で出す。出したら true
    private static boolean forceHeartExtra(Entity e, String action) {
        String a = action.toUpperCase(java.util.Locale.ROOT);
        int kind = switch (a) {
            case "LEAD" -> 0;
            case "CUTOFF" -> 1;
            case "CHASE" -> 2;
            default -> -1;
        };
        if (a.equals("DOMAIN_OK") || a.equals("DOMAIN_BAD")) {
            // 撮影用: 岩鯨の領域の骨を、次に叩く骨 (OK) / 違う骨 (BAD) で叩く
            if (e instanceof StoneWhaleHeart h && h.domain() != null && e.level() instanceof net.minecraft.server.level.ServerLevel lv
                    && lv.getNearestPlayer(e, 60) instanceof net.minecraft.server.level.ServerPlayer sp) {
                int want = h.domain().expected();
                h.domain().strikeNode(lv, a.equals("DOMAIN_OK") ? want : (want + 1) % 3, sp);
                return true;
            }
            return false;
        }
        if (kind < 0 && !a.equals("DOMAIN")) {
            return false;
        }
        if (!(e.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return false;
        }
        jp.revati.skyabyss.combat.HeartPursuit pursuit;
        java.util.function.Consumer<net.minecraft.server.level.ServerLevel> domain;
        if (e instanceof StoneWhaleHeart h) {
            h.holdAttacks();
            pursuit = h.pursuit();
            domain = h::startDomain;
        } else if (e instanceof jp.revati.skyabyss.titan.tide.heart.TideHeart h) {
            h.holdAttacks();
            pursuit = h.pursuit();
            domain = h::startDomain;
        } else if (e instanceof jp.revati.skyabyss.titan.storm.heart.StormHeart h) {
            h.holdAttacks();
            pursuit = h.pursuit();
            domain = h::startDomain;
        } else if (e instanceof jp.revati.skyabyss.titan.ember.heart.EmberHeart h) {
            h.holdAttacks(true);
            pursuit = h.pursuit();
            domain = h::startDomain;
        } else {
            return false;
        }
        if (kind < 0) {
            domain.accept(level);
            return true;
        }
        net.minecraft.world.entity.player.Player p = level.getNearestPlayer(e, 60);
        if (p instanceof net.minecraft.server.level.ServerPlayer sp) {
            pursuit.launch(level, sp, kind);
        }
        return true;
    }
}
