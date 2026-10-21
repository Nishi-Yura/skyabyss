package jp.revati.skyabyss.story.arc;

import java.util.function.Supplier;

import com.mojang.brigadier.arguments.StringArgumentType;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.story.Chapters;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// 章の演出の共通部品の登録: 人ごとの記録 (ArcData)、題字・余韻の札 (ChapterCards)、予兆の枠 (Foreshadow) の見回り。
// SkyAbyss のコンストラクタから register を呼ぶ。文は assets/skyabyss_story/lang
public final class ArcSetup {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, SkyAbyss.MODID);

    // 人ごとの記録。死んでも引き継ぐ
    public static final Supplier<AttachmentType<ArcData>> STATE = ATTACHMENTS.register("chapter_arc",
            () -> AttachmentType.builder(ArcData::new).serialize(ArcData.CODEC).copyOnDeath().build());

    private ArcSetup() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
        modEventBus.addListener(ArcSetup::onCommonSetup);
    }

    // 登録の口を開く (各巨神の ForeshadowRegisterEvent の購読がここで呼ばれる)
    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> ModLoader.postEvent(new ForeshadowRegisterEvent()));
    }

    @EventBusSubscriber(modid = SkyAbyss.MODID)
    static final class Events {
        private Events() {
        }

        @SubscribeEvent
        static void onPlayerTick(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof net.neoforged.neoforge.common.util.FakePlayer) {
                return;
            }
            // 人ごとに時刻をずらして (UUID から) 同じ tick に集めない
            int phase = (player.getUUID().hashCode() & 0x7fffffff) % Foreshadow.CHECK_TICKS;
            if (player.tickCount % 20 == phase % 20) {
                ChapterCards.tick(player);
            }
            if (player.tickCount % Foreshadow.CHECK_TICKS == phase) {
                Foreshadow.tick(player);
            }
        }

        @SubscribeEvent
        static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            ChapterCards.forget(event.getEntity().getUUID());
        }

        // 開発用 (OP): /skyabyss dev arc <status|reset|card|aftermath> [章の ID]
        @SubscribeEvent
        static void onCommands(RegisterCommandsEvent event) {
            if (FMLEnvironment.production) {
                return;
            }
            event.getDispatcher().register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2))
                    .then(Commands.literal("dev").then(Commands.literal("arc")
                            .then(Commands.literal("status").executes(ctx -> {
                                ServerPlayer p = ctx.getSource().getPlayerOrException();
                                ArcData d = ArcData.of(p);
                                ctx.getSource().sendSuccess(() -> Component.literal("chapter=" + Chapters.of(p) + " beats=" + d.beats()
                                        + " lastBeatAt=" + d.lastBeatAt()), false);
                                return 1;
                            }))
                            .then(Commands.literal("reset").executes(ctx -> {
                                ArcData.of(ctx.getSource().getPlayerOrException()).reset();
                                return 1;
                            }))
                            .then(Commands.literal("card").then(Commands.argument("chapter", StringArgumentType.word()).executes(ctx -> {
                                Chapters c = Chapters.byId(StringArgumentType.getString(ctx, "chapter"));
                                if (c == null) {
                                    return 0;
                                }
                                ServerPlayer p = ctx.getSource().getPlayerOrException();
                                // 出した札は「出した」と記す (左上の章の札もこの章に替わる)
                                ArcData.of(p).markTitle(c);
                                jp.revati.skyabyss.notice.Notice.chapter(p, c.label(), ChapterCards.titleLine(c));
                                return 1;
                            })))
                            .then(Commands.literal("aftermath").then(Commands.argument("chapter", StringArgumentType.word()).executes(ctx -> {
                                Chapters c = Chapters.byId(StringArgumentType.getString(ctx, "chapter"));
                                if (c == null) {
                                    return 0;
                                }
                                jp.revati.skyabyss.notice.Notice.aftermath(ctx.getSource().getPlayerOrException(), ChapterCards.aftermathHead(c),
                                        Component.translatable("chapter.skyabyss.aftermath.preview"));
                                return 1;
                            }))))));
        }
    }
}
