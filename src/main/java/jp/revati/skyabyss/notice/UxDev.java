package jp.revati.skyabyss.notice;

import java.util.List;

import com.mojang.brigadier.arguments.StringArgumentType;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.grave.GraveData;
import jp.revati.skyabyss.grave.Graves;
import jp.revati.skyabyss.party.PartyLocator;
import jp.revati.skyabyss.titan.group.TrialSteles;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 使い心地の磨き (rpg_brushup A4 / A8 / A11) の見た目の確かめ用 (開発時だけ。撮影の台本から cmd で使う):
//   /skyabyss dev ux stele <self|other|none>   石の「誰が鳴らしたか」の知らせを自分に一つ出す
//   /skyabyss dev ux grave <dx> <dz>           自分の墓を、いまいる所から (dx, dz) ずらした場所に作る (中身はパンと経験値)
//   /skyabyss dev ux party                     仲間の札の見本 (体力の違う 3 人) を自分にだけ送る
//   /skyabyss dev ux clear                     見本の墓と札を消す
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class UxDev {
    private UxDev() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        event.getDispatcher().register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("dev").then(Commands.literal("ux")
                        .then(Commands.literal("stele").then(Commands.argument("who", StringArgumentType.word()).executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            TrialSteles.WhoRang w = switch (StringArgumentType.getString(ctx, "who")) {
                                case "self" -> new TrialSteles.WhoRang(TrialSteles.Who.SELF, p.getGameProfile().getName());
                                case "other" -> new TrialSteles.WhoRang(TrialSteles.Who.OTHER, "Alice");
                                default -> new TrialSteles.WhoRang(TrialSteles.Who.NONE, "");
                            };
                            Notice.bar(p, ModText.hint("message.skyabyss.steles.who", TrialSteles.whoText(w)));
                            return 1;
                        })))
                        .then(Commands.literal("grave").then(Commands.argument("dx", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                .then(Commands.argument("dz", com.mojang.brigadier.arguments.IntegerArgumentType.integer()).executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    ServerLevel level = p.serverLevel();
                                    BlockPos at = p.blockPosition().offset(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "dx"), 0,
                                            com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "dz"));
                                    Graves.create(p, level, at, null, List.of(new ItemStack(Items.BREAD, 5)), 30);
                                    return 1;
                                }))))
                        .then(Commands.literal("party").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            String dim = p.level().dimension().location().toString();
                            int x = p.getBlockX();
                            int y = p.getBlockY();
                            int z = p.getBlockZ();
                            PacketDistributor.sendToPlayer(p, new PartyLocator.Pos(List.of(
                                    new PartyLocator.Entry("sample-1", "Alice", dim, x + 9, y, z + 14, 100),
                                    new PartyLocator.Entry("sample-2", "Bob", dim, x - 7, y, z + 22, 40),
                                    new PartyLocator.Entry("sample-3", "Carol", dim, x + 14, y, z + 30, 10))));
                            return 1;
                        }))
                        .then(Commands.literal("clear").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            GraveData data = GraveData.get(p.server);
                            for (GraveData.Grave g : data.owned(p.getUUID())) {
                                ServerLevel l = p.server.getLevel(g.dimension);
                                data.remove(g.id);
                                if (l != null) {
                                    l.removeBlock(g.pos, false);
                                }
                            }
                            PacketDistributor.sendToPlayer(p, new PartyLocator.Pos(List.of()));
                            return 1;
                        })))));
    }
}
