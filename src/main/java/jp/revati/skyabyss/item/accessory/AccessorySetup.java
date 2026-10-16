package jp.revati.skyabyss.item.accessory;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// 装飾の枠の登録 (記録・画面・パケット)。SkyAbyss のコンストラクタから register を一度だけ呼ぶ。
// 共有の登録ファイル (ModMenus・ModNetwork) は触らず、ここにまとめる
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class AccessorySetup {
    private static final DeferredRegister<net.neoforged.neoforge.attachment.AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, SkyAbyss.MODID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, SkyAbyss.MODID);

    // 枠の中身。死んでも落とさない場面 (keepInventory・岩鯨の背) では copyOnDeath で残る。持ち主にだけ同期する
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<AccessoryData>> DATA = ATTACHMENTS.register("accessories",
            () -> AttachmentType.serializable(AccessoryData::new)
                    .copyOnDeath()
                    .sync((holder, to) -> holder == to, AccessoryData.STREAM_CODEC)
                    .build());

    public static final DeferredHolder<MenuType<?>, MenuType<AccessoryMenu>> MENU = MENUS.register("accessories",
            () -> IMenuTypeExtension.create((id, inventory, buf) -> new AccessoryMenu(id, inventory)));

    private AccessorySetup() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
        MENUS.register(modEventBus);
    }

    // ---------------------------------------------------------------- パケット (持ち主 → サーバー)
    // 持ち物の画面のつまみから、装飾の画面を開く
    public record OpenPayload() implements CustomPacketPayload {
        public static final Type<OpenPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "accessory_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenPayload> STREAM_CODEC = StreamCodec.unit(new OpenPayload());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // 護符の枠の品を使う (キー)
    public record UseCharmPayload() implements CustomPacketPayload {
        public static final Type<UseCharmPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "accessory_use_charm"));
        public static final StreamCodec<RegistryFriendlyByteBuf, UseCharmPayload> STREAM_CODEC = StreamCodec.unit(new UseCharmPayload());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    static void onPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToServer(OpenPayload.TYPE, OpenPayload.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        Accessories.openMenu(player);
                    }
                })
                .playToServer(UseCharmPayload.TYPE, UseCharmPayload.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        Accessories.useCharm(player);
                    }
                });
    }
}
