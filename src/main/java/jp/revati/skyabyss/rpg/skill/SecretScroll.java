package jp.revati.skyabyss.rpg.skill;

import java.util.List;

import jp.revati.skyabyss.notice.Notice;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

// 秘伝の巻 (docs/design/54 の 3.2.1)。使うと、いまの職の秘伝から一つを覚える (段が一つ上がる)。III のあとは塔の欠片 20 になる。
// 手に入るのは、あいだの塔を越えたとき (25%) と塔の店
public class SecretScroll extends Item {
    public SecretScroll(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            SecretArts.Art a = SecretArts.roll(jp.revati.skyabyss.story.finale.gear.DivineGear.job(sp), sp.getRandom());
            if (SecretArts.learn(sp, a)) {
                Notice.unlock(sp, "message.skyabyss.secret.learned", SecretArts.name(a, SecretArts.tier(sp, a)),
                        Component.translatable("skill.skyabyss." + a.skill.id));
            } else {
                jp.revati.skyabyss.quest.Quests.give(sp, new ItemStack(jp.revati.skyabyss.tower.gear.TowerGearSetup.SHARD.get(), SecretArts.OVER_SHARDS));
                Notice.gainLog(sp, "message.skyabyss.secret.over", SecretArts.name(a, SecretArts.MAX_TIER), SecretArts.OVER_SHARDS);
            }
            level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0f, 0.8f);
            if (!sp.getAbilities().instabuild) {
                stack.shrink(1);
            }
            SecretArts.menu(sp);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.skyabyss.secret_scroll.desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.skyabyss.secret_scroll.use").withStyle(ChatFormatting.DARK_AQUA));
    }
}
