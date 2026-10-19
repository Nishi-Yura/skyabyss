package jp.revati.skyabyss.pouch;

import com.mojang.serialization.Codec;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.record.Keepsakes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// 留め袋の登録 (記録・取り出し済みの印・ゲームルール・控え帳の在りか)。SkyAbyss のコンストラクタから register を一度だけ呼ぶ。
// 共有の登録ファイルには触らず、ここにまとめる (docs/design/22_mod_pouch.md)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class PouchSetup {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, SkyAbyss.MODID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, SkyAbyss.MODID);
    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(SkyAbyss.MODID);

    // 袋の中身。死んでも引き継ぐ (copyOnDeath)。持ち主のクライアントにだけ同期する (他人の袋は見えない。22 の 7 章)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PouchData>> DATA = ATTACHMENTS.register("pouch",
            () -> AttachmentType.builder(PouchData::new)
                    .serialize(PouchData.CODEC)
                    .copyOnDeath()
                    .sync((holder, to) -> holder == to, PouchData.STREAM_CODEC)
                    .build());

    // 取り出し済みの印 (skyabyss:pouch_loan)。付いた品は掃除が袋へ戻さない。拾い直す・袋へ置くと外れる (22 の 6 章)
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Unit>> LOAN = COMPONENTS.registerComponentType("pouch_loan",
            builder -> builder.persistent(Codec.unit(Unit.INSTANCE)).networkSynchronized(StreamCodec.unit(Unit.INSTANCE)));

    // 旅嚢の画面 (持ち物 / 装飾 / 旅嚢 のつまみの三つ目)
    public static final DeferredHolder<MenuType<?>, MenuType<PouchMenu>> MENU = MENUS.register("pouch",
            () -> IMenuTypeExtension.create((id, inventory, buf) -> new PouchMenu(id, inventory)));

    private PouchSetup() {
    }

    public static void openMenu(ServerPlayer player) {
        if (!PouchConfig.enabled(player.server)) {
            return;
        }
        jp.revati.skyabyss.tutorial.TutorialHints.done(player, jp.revati.skyabyss.tutorial.TutorialHints.POUCH);
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new PouchMenu(id, inventory), Component.translatable("skyabyss_pouch.name")));
    }

    // 親スイッチの既定を「入」にした版 (22 の 15.0)。前の版のワールドは、ゲームルールが「切」で保存されているので、
    // 最初の起動で一度だけ既定 (入) に書き換える。そのあと管理者が切にしたものは、また入に戻さない
    static final class WorldState extends SavedData {
        private static final String NAME = "skyabyss_pouch_world";
        boolean ruleApplied;

        static WorldState get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(WorldState::new, WorldState::load), NAME);
        }

        static WorldState load(CompoundTag tag, HolderLookup.Provider registries) {
            WorldState s = new WorldState();
            s.ruleApplied = tag.getBoolean("ruleApplied");
            return s;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putBoolean("ruleApplied", ruleApplied);
            return tag;
        }
    }

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        WorldState state = WorldState.get(server);
        if (!state.ruleApplied) {
            state.ruleApplied = true;
            state.setDirty();
            server.getGameRules().getRule(PouchConfig.MASTER_RULE).set(PouchConfig.ENABLED_DEFAULT, server);
            server.getGameRules().getRule(PouchConfig.WITHDRAW_RULE).set(PouchConfig.WITHDRAW_DEFAULT, server);
        }
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
        COMPONENTS.register(modEventBus);
        MENUS.register(modEventBus);
    }

    @SubscribeEvent
    static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            PouchConfig.MASTER_RULE = GameRules.register("skyabyssPouch", GameRules.Category.PLAYER,
                    GameRules.BooleanValue.create(PouchConfig.ENABLED_DEFAULT));
            // ゲームルール skyabyssPouchWithdraw: 1 = 素材・共鳴を取り出せる (鍵・心核・笛・書は常に不可)、0 = 何も取り出せない
            PouchConfig.WITHDRAW_RULE = GameRules.register("skyabyssPouchWithdraw", GameRules.Category.PLAYER,
                    GameRules.IntegerValue.create(PouchConfig.WITHDRAW_DEFAULT));
            // 控え帳の在りか: 袋の品は「手もと」に数える (22 の 5 章)。天の鍵がそろうとき袋から欠片を取り除く
            Keepsakes.registerHolder("pouch", new Keepsakes.Holder() {
                @Override
                public int count(ServerPlayer player, Item item) {
                    return Pouch.count(player, item);
                }

                @Override
                public int take(ServerPlayer player, Item item, int n) {
                    return Pouch.take(player, item, n);
                }
            });
        });
    }
}
