package jp.revati.skyabyss.pouch;

import com.mojang.serialization.Codec;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.record.Keepsakes;
import net.minecraft.core.component.DataComponentType;
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
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// 留め袋の登録 (記録・取り出し済みの印・ゲームルール・控え帳の在りか)。SkyAbyss のコンストラクタから register を一度だけ呼ぶ。
// 共有の登録ファイルには触らず、ここにまとめる (docs/design/22_mod_pouch.md)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class PouchSetup {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, SkyAbyss.MODID);
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

    private PouchSetup() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
        COMPONENTS.register(modEventBus);
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
