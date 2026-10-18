package jp.revati.skyabyss.grave;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

// 倒れたときの決まり (地上・ネザー・エンドなど、巨神の背中以外)。docs/design/11_record_screen.md の 6 章。
// ゲームルールで切れる。バニラの keepInventory が true なら、これらより優先してバニラのとおり (全部残る)。
//   skyabyssDeathMode        0 grave (既定) / 1 vanilla / 2 keep / 3 ledger  (ゲームルールは整数しか持てないので番号)
//   skyabyssGraveKeepGear    grave のとき、装備中の防具・左手・装飾品・ホットバーを持ったまま復活する
//   skyabyssGraveOwnerOnly   墓を持ち主だけが開けられる (false なら誰でも)
//   skyabyssGraveExpire      墓が消えるまでの分。0 = 消えない。消えるときは中身を足もとへ落とす
//   skyabyssGraveRescue      語り部の救いで戻る割合 (%)。0 で救いなし
//   skyabyssDeathDurability  grave / keep / ledger で、残した品の耐久が減る割合 (%、壊れる手前で止める)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class DeathRules {
    public enum Mode {
        GRAVE, VANILLA, KEEP, LEDGER
    }

    public static final int DEFAULT_RESCUE = 50;
    public static final int DEFAULT_DURABILITY = 10;

    public static GameRules.Key<GameRules.IntegerValue> MODE;
    public static GameRules.Key<GameRules.BooleanValue> KEEP_GEAR;
    public static GameRules.Key<GameRules.BooleanValue> OWNER_ONLY;
    public static GameRules.Key<GameRules.IntegerValue> EXPIRE;
    public static GameRules.Key<GameRules.IntegerValue> RESCUE;
    public static GameRules.Key<GameRules.IntegerValue> DURABILITY;

    private DeathRules() {
    }

    @SubscribeEvent
    static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            MODE = GameRules.register("skyabyssDeathMode", GameRules.Category.PLAYER, GameRules.IntegerValue.create(Mode.GRAVE.ordinal()));
            KEEP_GEAR = GameRules.register("skyabyssGraveKeepGear", GameRules.Category.PLAYER, GameRules.BooleanValue.create(true));
            OWNER_ONLY = GameRules.register("skyabyssGraveOwnerOnly", GameRules.Category.PLAYER, GameRules.BooleanValue.create(true));
            EXPIRE = GameRules.register("skyabyssGraveExpire", GameRules.Category.PLAYER, GameRules.IntegerValue.create(0));
            RESCUE = GameRules.register("skyabyssGraveRescue", GameRules.Category.PLAYER, GameRules.IntegerValue.create(DEFAULT_RESCUE));
            DURABILITY = GameRules.register("skyabyssDeathDurability", GameRules.Category.PLAYER, GameRules.IntegerValue.create(DEFAULT_DURABILITY));
        });
    }

    // 範囲外の番号は既定 (grave)
    public static Mode mode(MinecraftServer server) {
        if (MODE == null) {
            return Mode.GRAVE;
        }
        int v = server.getGameRules().getInt(MODE);
        return v >= 0 && v < Mode.values().length ? Mode.values()[v] : Mode.GRAVE;
    }

    public static boolean vanillaKeep(MinecraftServer server) {
        return server.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
    }

    public static boolean keepGear(MinecraftServer server) {
        return KEEP_GEAR == null || server.getGameRules().getBoolean(KEEP_GEAR);
    }

    public static boolean ownerOnly(MinecraftServer server) {
        return OWNER_ONLY == null || server.getGameRules().getBoolean(OWNER_ONLY);
    }

    public static int expireMinutes(MinecraftServer server) {
        return EXPIRE == null ? 0 : Math.max(0, server.getGameRules().getInt(EXPIRE));
    }

    public static int rescuePercent(MinecraftServer server) {
        return RESCUE == null ? DEFAULT_RESCUE : Math.max(0, Math.min(100, server.getGameRules().getInt(RESCUE)));
    }

    public static int durabilityPercent(MinecraftServer server) {
        return DURABILITY == null ? DEFAULT_DURABILITY : Math.max(0, Math.min(100, server.getGameRules().getInt(DURABILITY)));
    }
}
