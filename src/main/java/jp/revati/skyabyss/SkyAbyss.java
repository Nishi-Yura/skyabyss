package jp.revati.skyabyss;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import jp.revati.skyabyss.registry.ModArmorMaterials;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.registry.ModDataComponents;
import jp.revati.skyabyss.registry.ModEffects;
import jp.revati.skyabyss.registry.ModEntities;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.registry.ModMenus;
import jp.revati.skyabyss.registry.ModSounds;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

// 天と淵のあいだ (Between Sky and Abyss) のメインクラス。
// 値は META-INF/neoforge.mods.toml の modId と一致させる。
@Mod(SkyAbyss.MODID)
public class SkyAbyss {
    public static final String MODID = "skyabyss";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SkyAbyss(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        ModSounds.register(modEventBus);
        ModEffects.register(modEventBus);
        ModDataComponents.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModArmorMaterials.register(modEventBus);
        ModItems.register(modEventBus);
        ModEntities.register(modEventBus);
        ModMenus.register(modEventBus);
        // 伝承の書 (アイテム・頁の出どころ・プレイヤーの記録)
        jp.revati.skyabyss.lore.LoreSetup.register(modEventBus);

        // サーバー設定はワールドごとに保存され、マルチプレイでは参加者に同期される。
        // クライアント設定は各プレイヤーのPCごとの好み
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SERVER_SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("[{}] 天と淵のあいだ を読み込みました", MODID);
    }
}
