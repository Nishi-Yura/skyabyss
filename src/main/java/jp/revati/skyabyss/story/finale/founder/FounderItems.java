package jp.revati.skyabyss.story.finale.founder;

import java.util.List;

import com.mojang.serialization.Codec;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.story.ch1.Tasks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// 初代の空見の報酬の品 (docs/design/53 の 6 章)。強さは何も変えない。
//   空見の肩掛け  見た目だけ。右クリックで着る・脱ぐ (人の付け足しのデータ SHAWL。装飾の枠は使わない)。着ていると背中と肩に描く
//                 (client/founder/ShawlLayer。ほかの人にも見えるので、まわりへ同期する。倒れても脱げない)
//   始祖の小鐘    記念品。右クリックで澄んだ一打ち (3 秒に一度)。都の中で鳴らすと、遠くの大鐘楼が一度応える
public final class FounderItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, SkyAbyss.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> SHAWL = ATTACHMENTS.register("founder_shawl",
            () -> AttachmentType.builder(() -> Boolean.FALSE).serialize(Codec.BOOL).copyOnDeath().sync((holder, to) -> true, ByteBufCodecs.BOOL).build());

    public static final DeferredItem<Item> SHAWL_ITEM = ITEMS.register("founder_shawl",
            () -> new ShawlItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final DeferredItem<Item> HANDBELL = ITEMS.register("founder_handbell",
            () -> new HandbellItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    static final int BELL_COOLDOWN = 60;

    private FounderItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        ATTACHMENTS.register(modEventBus);
    }

    public static boolean wearing(Player p) {
        return p.getData(SHAWL);
    }

    public static void setWearing(Player p, boolean on) {
        p.setData(SHAWL, on);
    }

    static void lore(List<Component> lines, String id) {
        lines.add(Component.translatable("item.skyabyss." + id + ".desc").withStyle(net.minecraft.ChatFormatting.GRAY));
        lines.add(Component.translatable("item.skyabyss." + id + ".story").withStyle(net.minecraft.ChatFormatting.DARK_GRAY, net.minecraft.ChatFormatting.ITALIC));
    }

    static final class ShawlItem extends Item {
        ShawlItem(Properties props) {
            super(props);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!level.isClientSide && player instanceof ServerPlayer sp) {
                boolean on = !wearing(sp);
                setWearing(sp, on);
                level.playSound(null, sp.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1f, on ? 1.0f : 0.8f);
                Notice.hint(sp, on ? "message.skyabyss.founder.shawl_on" : "message.skyabyss.founder.shawl_off");
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
            lore(lines, "founder_shawl");
        }
    }

    static final class HandbellItem extends Item {
        HandbellItem(Properties props) {
            super(props);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!level.isClientSide && player instanceof ServerPlayer sp) {
                ring(sp);
                sp.getCooldowns().addCooldown(this, BELL_COOLDOWN);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
            lore(lines, "founder_handbell");
        }
    }

    // 小鐘の一打ち。都の中なら、遠くの大鐘楼 (月への門の上) が遅れて一度応える
    public static boolean ring(ServerPlayer p) {
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0f, 1.7f);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.6f, 1.2f);
        BlockPos tower = jp.revati.skyabyss.story.ch6.MoonGate.gatePos(p.server);
        boolean capital = tower != null && p.level().dimension() == Level.OVERWORLD && tower.distSqr(p.blockPosition()) <= 200.0 * 200.0;
        if (capital) {
            Tasks.later(30, () -> {
                if (!p.hasDisconnected()) {
                    p.serverLevel().playSound(null, tower.above(6), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 8.0f, 0.5f);
                    p.serverLevel().playSound(null, tower.above(6), SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 4.0f, 0.5f);
                }
            });
        }
        return capital;
    }
}
