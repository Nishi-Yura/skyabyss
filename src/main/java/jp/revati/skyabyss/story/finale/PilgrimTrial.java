package jp.revati.skyabyss.story.finale;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.story.finale.gear.DivineGearSetup;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

// クリア後「巡礼の試し」(docs/design/50 の 4.6。六章の位相の挑戦と同じ形)。
//   入り方  巡礼者を退けた人 (座の頼みを終えた人) が、座の台の「雲の暦」(日時計の台。印 calendar) を右クリック → チャットに今の段への挑戦
//           (押すと /skyabyss_pilgrim_trial。暦のそば 8 の内で、戦いのない座でだけ)。三形態をもう一度、強くして通しで
//   段      人ごとに 1〜5 (越えた段の次。五段のあとは五段を繰り返す)。体力 ×(1 + 0.15 × 段)、傷 ×(1 + 0.08 × 段)。段 3 から技の間が三分の二。
//           段 5 は形態ごとの重ね: 手 = 叩きつけが三つの円・目 = 閉じる隙が半分で光の雨が倍・月 = 巻き戻しが三度
//   報酬    月を倒した座の内の人に、心核の欠片 3 + 段・雲上の心核 (段 × 10%)。段 5 を初めて越えた人に伝承の頁「巡礼の試し」(記念)
//   閉じ方  誰もいなくなると記録せずに片付ける (SeatArena と同じ)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class PilgrimTrial {
    public static final String KEY = "skyabyss_pilgrim_trial";
    public static final int MAX_TIER = 5;
    static final String COMMAND = "skyabyss_pilgrim_trial";
    static final double NEAR = 8;
    static final String KEEPSAKE = "finale/keepsake_pilgrim";

    private PilgrimTrial() {
    }

    // 挑める人: 座の頼みを終えた
    public static boolean open(ServerPlayer p) {
        QuestLog.Entry e = QuestLog.of(p).entry(PilgrimSeat.QUEST);
        return e != null && e.done;
    }

    // 越えた段 (0 = まだ)
    public static int cleared(ServerPlayer p) {
        return p.getPersistentData().getInt(KEY);
    }

    // 次に挑む段 (越えた段の次。五段のあとは五段)
    public static int next(ServerPlayer p) {
        return Math.min(MAX_TIER, cleared(p) + 1);
    }

    public static double hpScale(int tier) {
        return 1 + 0.15 * tier;
    }

    public static double dmgScale(int tier) {
        return 1 + 0.08 * tier;
    }

    static boolean nearCalendar(ServerPlayer p) {
        BlockPos c = PilgrimSeat.mark("calendar");
        return FinaleWorld.in(p) && p.distanceToSqr(c.getX() + 0.5, c.getY(), c.getZ() + 0.5) <= NEAR * NEAR;
    }

    static void offer(ServerPlayer p) {
        int t = next(p);
        Notice.narrate(p, "message.skyabyss.trial.calendar", cleared(p));
        p.sendSystemMessage(Component.translatable("message.skyabyss.trial.option", t).withStyle(Style.EMPTY.withColor(0xF2D78A).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + COMMAND))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.skyabyss.trial.option.hint." + Math.min(t, 5))))));
    }

    // 挑む (チャットから)
    public static boolean begin(ServerPlayer p) {
        if (!open(p) || !nearCalendar(p)) {
            return false;
        }
        if (SeatArena.form() != 0) {
            Notice.bar(p, Component.translatable("message.skyabyss.seat.throne_busy"));
            return false;
        }
        SeatArena.startTrial(p.serverLevel(), next(p));
        return true;
    }

    // 月が倒れた (試しのとき。SeatArena.onDeath から)
    static void won(List<ServerPlayer> here, int tier) {
        for (ServerPlayer p : here) {
            Quests.give(p, new ItemStack(DivineGearSetup.SHARD.get(), 3 + tier));
            if (p.getRandom().nextFloat() < 0.1f * tier) {
                Quests.give(p, new ItemStack(DivineGearSetup.CORE.get()));
            }
            if (tier > cleared(p)) {
                p.getPersistentData().putInt(KEY, tier);
            }
            Notice.objective(p, "message.skyabyss.trial.cleared", tier);
            if (tier >= MAX_TIER && Lore.unlock(p, KEEPSAKE, true)) {
                Notice.narrate(p, "message.skyabyss.trial.keepsake");
            }
        }
    }

    @SubscribeEvent
    static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getHand() != InteractionHand.MAIN_HAND || !FinaleWorld.in(p)
                || FinaleWorld.version(p.server) < 7 || !event.getPos().equals(PilgrimSeat.mark("calendar"))) {
            return;
        }
        event.setCanceled(true);
        if (!open(p)) {
            Notice.narrate(p, "message.skyabyss.trial.shut");
            return;
        }
        offer(p);
    }

    @SubscribeEvent
    static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(COMMAND).executes(c -> begin(c.getSource().getPlayerOrException()) ? 1 : 0));
    }
}
