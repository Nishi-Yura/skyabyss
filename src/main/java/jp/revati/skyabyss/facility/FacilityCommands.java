package jp.revati.skyabyss.facility;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.opening.OpeningSites;
import jp.revati.skyabyss.opening.site.SiteData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 都の施設の開発用コマンド (OP)。
//   /skyabyss facility spawn <役> [見た目 0..3]   足もとに係の人を立たせる (家はその場所。見た目 0 = 既定の案、1〜3 = 案 a〜c)
//   /skyabyss facility mark <印の名前>            足もとを都の設計図の印として記す (npc_<役>・inn_bed。設計図がまだ印を持たないときの試し)
//   /skyabyss facility place                      記した印のすべてに係の人を立たせる (見回りと同じ)
//   /skyabyss facility shop <店>                  店を開く (どこからでも)
//   /skyabyss facility stash                      預かり所を開く (どこからでも)
//   /skyabyss facility coins <枚>                 鐘銭を渡す
//   /skyabyss facility look <0..3>                近くの係の人の見た目の案を替える (撮影用)
//   /skyabyss facility records                    砦の老兵の試練の記録を開く
//   /skyabyss facility dummy                      足もとのまわり 8 の木偶の柱に的をかぶせる (撮影・試し用)
//   /skyabyss facility keyroom offer|return       鍵の間に欠片を納める / 返してもらう (祭司と同じ)
//   /skyabyss facility mapcopy                    書庫番の地図の写し (祭司・書庫番の働きと同じ)
//   /skyabyss facility respec <skills|stats> [coins]   師範の振り直し
//   /skyabyss facility brew                       薬師の醸造の手ほどき (四章の決まりのまま)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class FacilityCommands {
    private static final SuggestionProvider<CommandSourceStack> ROLES = (ctx, b) -> SharedSuggestionProvider.suggest(
            java.util.Arrays.stream(AttendantRole.values()).map(r -> r.id), b);
    private static final SuggestionProvider<CommandSourceStack> SHOPS = (ctx, b) -> SharedSuggestionProvider.suggest(
            ShopManager.all().keySet(), b);

    private FacilityCommands() {
    }

    @SubscribeEvent
    static void onRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("skyabyss").requires(s -> s.hasPermission(2))
                .then(Commands.literal("facility")
                        .then(Commands.literal("spawn").then(Commands.argument("role", StringArgumentType.word()).suggests(ROLES)
                                .executes(c -> spawn(c.getSource(), StringArgumentType.getString(c, "role"), 0))
                                .then(Commands.argument("look", IntegerArgumentType.integer(0, 3))
                                        .executes(c -> spawn(c.getSource(), StringArgumentType.getString(c, "role"), IntegerArgumentType.getInteger(c, "look"))))))
                        .then(Commands.literal("mark").then(Commands.argument("name", StringArgumentType.word())
                                .executes(c -> mark(c.getSource(), StringArgumentType.getString(c, "name")))))
                        .then(Commands.literal("place").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            int n = Attendants.ensureAll(p.serverLevel(), OpeningSites.shelterExtras(p.server, AttendantRole.MARK_PREFIX));
                            c.getSource().sendSuccess(() -> Component.literal("attendants: " + n), false);
                            return n;
                        }))
                        .then(Commands.literal("shop").then(Commands.argument("shop", StringArgumentType.word()).suggests(SHOPS)
                                .executes(c -> ShopService.open(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "shop"), null) ? 1 : 0)))
                        .then(Commands.literal("stash").executes(c -> {
                            StashMenu.open(c.getSource().getPlayerOrException(), null);
                            return 1;
                        }))
                        .then(Commands.literal("coins").then(Commands.argument("n", IntegerArgumentType.integer(1, 9999))
                                .executes(c -> {
                                    Coins.give(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "n"));
                                    return 1;
                                })))
                        .then(Commands.literal("look").then(Commands.argument("look", IntegerArgumentType.integer(0, 3))
                                .executes(c -> {
                                    ServerPlayer p = c.getSource().getPlayerOrException();
                                    int look = IntegerArgumentType.getInteger(c, "look");
                                    var list = p.serverLevel().getEntities(FacilitySetup.ATTENDANT.get(), p.getBoundingBox().inflate(48), e -> true);
                                    list.forEach(a -> a.setVariant(look));
                                    return list.size();
                                })))
                        .then(Commands.literal("records").executes(c -> {
                            KeepRecords.open(c.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.literal("dummy").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            int n = TrainingYard.ensureDummies(p.serverLevel(), TrainingYard.findColumns(p.serverLevel(), p.blockPosition(), 8));
                            c.getSource().sendSuccess(() -> Component.literal("dummies: " + n), false);
                            return n;
                        }))
                        .then(Commands.literal("keyroom")
                                .then(Commands.literal("offer").executes(c -> KeyRoom.offerAll(c.getSource().getPlayerOrException())))
                                .then(Commands.literal("return").executes(c -> KeyRoom.giveBack(c.getSource().getPlayerOrException()))))
                        .then(Commands.literal("mapcopy").executes(c -> FacilityActions.mapCopy(c.getSource().getPlayerOrException()) ? 1 : 0))
                        .then(Commands.literal("respec").then(Commands.argument("what", StringArgumentType.word())
                                .suggests((ctx, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(java.util.List.of("skills", "stats"), b))
                                .executes(c -> TrainingMaster.respec(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "what").equals("stats"), false)
                                        == jp.revati.skyabyss.rpg.skill.SkillManager.RespecResult.OK ? 1 : 0)
                                .then(Commands.literal("coins").executes(c -> TrainingMaster.respec(c.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(c, "what").equals("stats"), true) == jp.revati.skyabyss.rpg.skill.SkillManager.RespecResult.OK ? 1 : 0))))
                        .then(Commands.literal("brew").executes(c -> FacilityActions.brewLesson(c.getSource().getPlayerOrException()) ? 1 : 0))));
    }

    private static int spawn(CommandSourceStack src, String roleId, int look) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        AttendantRole role = AttendantRole.byId(roleId);
        if (role == null) {
            src.sendFailure(Component.literal("unknown role " + roleId));
            return 0;
        }
        AttendantEntity npc = FacilitySetup.ATTENDANT.get().create(p.serverLevel());
        if (npc == null) {
            return 0;
        }
        npc.setRole(role);
        npc.setVariant(look);
        npc.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot() + 180f, 0f);
        npc.setYHeadRot(p.getYRot() + 180f);
        npc.setYBodyRot(p.getYRot() + 180f);
        npc.setHome(p.blockPosition());
        p.serverLevel().addFreshEntity(npc);
        return 1;
    }

    private static int mark(CommandSourceStack src, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        SiteData data = SiteData.get(p.server.overworld());
        SiteData.Entry e = data.first(OpeningSites.Kind.SHELTER.id);
        if (e == null) {
            src.sendFailure(Component.literal("no capital (shelter) site"));
            return 0;
        }
        BlockPos at = p.blockPosition();
        e.extra(name, at);
        data.setDirty();
        src.sendSuccess(() -> Component.literal("mark " + name + " = " + at.toShortString()), false);
        return 1;
    }
}
