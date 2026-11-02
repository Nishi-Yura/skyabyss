package jp.revati.skyabyss.endgame;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.Coins;
import jp.revati.skyabyss.item.TitanGearSet;
import jp.revati.skyabyss.item.TitanGearSets;
import jp.revati.skyabyss.loot.TitanMemoryData;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.registry.ModDataComponents;
import jp.revati.skyabyss.rpg.skill.Job;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 見た目替え (幻装。docs/design/54 の 5.4)。都と土地の町の鍛冶屋 (武具屋に足す) に「見た目を替える」と言うと、利き手の武器の見た目を、
// 図鑑に載った (手にしたことのある) 同じ種類の武器の見た目に替えられる (強さ・技・動きの速さは替えない)。一回 PRICE 鐘銭、元に戻すのはただ。
//   同じ種類 = 同じ職の武器 (Job.of) で、同じ型 (client/weapon/WeaponKind。大剣と長柄などを混ぜない)
//   品に見た目の印 (ModDataComponents.LOOK = 見た目の武器の ID) を付け、描く側 (WeaponItemRenderer) がその形を使う。説明に「見た目: 〜」
//   防具の見た目替えと飾り装備 (6.1) は、飾り装備の形を見せてから足す
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Looks {
    public static final int PRICE = 200;

    private Looks() {
    }

    @Nullable
    public static Item look(ItemStack stack) {
        String id = stack.get(ModDataComponents.LOOK.get());
        if (id == null) {
            return null;
        }
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? null : BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
    }

    // 二つの武器が同じ種類か (同じ職の武器で、同じ型)
    public static boolean sameKind(Item a, Item b) {
        Job ja = Job.of(a);
        if (ja == null || ja != Job.of(b) || a == b) {
            return false;
        }
        // 形を描けるもの (型のある武器か、弓) だけ
        var ka = jp.revati.skyabyss.client.weapon.WeaponKind.of(new ItemStack(a));
        var kb = jp.revati.skyabyss.client.weapon.WeaponKind.of(new ItemStack(b));
        boolean bows = a instanceof net.minecraft.world.item.BowItem && b instanceof net.minecraft.world.item.BowItem;
        return bows || (ka != null && ka == kb);
    }

    // 着られる見た目: 図鑑 (巨神の記憶の手にした品) のうち、利き手の武器と同じ種類のもの
    public static List<Item> choices(ServerPlayer p, ItemStack held) {
        Set<String> owned = new LinkedHashSet<>();
        TitanMemoryData mem = TitanMemoryData.get(p.server);
        for (TitanGearSet set : TitanGearSets.all()) {
            TitanMemoryData.Record r = mem.peek(p.getUUID(), set.titan);
            if (r != null) {
                owned.addAll(r.owned);
            }
        }
        List<Item> out = new ArrayList<>();
        for (String id : owned) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            Item item = rl == null ? null : BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
            if (item != null && sameKind(held.getItem(), item)) {
                out.add(item);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- 鍛冶屋の会話から
    public static void menu(ServerPlayer p) {
        ItemStack held = p.getMainHandItem();
        if (held.isEmpty() || Job.of(held.getItem()) == null) {
            Notice.hint(p, "message.skyabyss.look.no_weapon");
            return;
        }
        List<Item> choices = choices(p, held);
        p.sendSystemMessage(Component.translatable("message.skyabyss.look.head", held.getHoverName()).withStyle(ChatFormatting.GOLD));
        if (choices.isEmpty()) {
            p.sendSystemMessage(Component.translatable("message.skyabyss.look.none").withStyle(ChatFormatting.GRAY));
        } else {
            MutableComponent line = Component.empty();
            for (Item item : choices) {
                String id = BuiltInRegistries.ITEM.getKey(item).toString();
                line.append(Component.literal("[").append(new ItemStack(item).getHoverName()).append("] ")
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/skyabyss_look set " + id))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.skyabyss.look.hover", PRICE)))));
            }
            p.sendSystemMessage(line);
        }
        if (look(held) != null) {
            p.sendSystemMessage(Component.translatable("message.skyabyss.look.clear").withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/skyabyss_look clear"))));
        }
        p.sendSystemMessage(Component.translatable("message.skyabyss.look.foot", PRICE).withStyle(ChatFormatting.GRAY));
    }

    public static boolean set(ServerPlayer p, String id) {
        ItemStack held = p.getMainHandItem();
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Item item = rl == null ? null : BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
        if (held.isEmpty() || item == null || !choices(p, held).contains(item)) {
            Notice.hint(p, "message.skyabyss.look.cannot");
            return false;
        }
        if (!Coins.take(p, PRICE)) {
            Notice.hint(p, "message.skyabyss.shop.no_coins");
            return false;
        }
        held.set(ModDataComponents.LOOK.get(), id);
        Notice.gain(p, "message.skyabyss.look.done", held.getHoverName(), new ItemStack(item).getHoverName());
        return true;
    }

    public static void clear(ServerPlayer p) {
        ItemStack held = p.getMainHandItem();
        if (look(held) != null) {
            held.remove(ModDataComponents.LOOK.get());
            Notice.gain(p, "message.skyabyss.look.cleared", held.getHoverName());
        }
    }

    @SubscribeEvent
    static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("skyabyss_look")
                .then(Commands.literal("set").then(Commands.argument("item", com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(c ->
                        set(c.getSource().getPlayerOrException(), com.mojang.brigadier.arguments.StringArgumentType.getString(c, "item")) ? 1 : 0)))
                .then(Commands.literal("clear").executes(c -> {
                    clear(c.getSource().getPlayerOrException());
                    return 1;
                })));
    }
}
