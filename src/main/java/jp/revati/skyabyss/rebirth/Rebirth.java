package jp.revati.skyabyss.rebirth;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.LongSupplier;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.GrowthData;
import jp.revati.skyabyss.rpg.GrowthRules;
import jp.revati.skyabyss.rpg.Rpg;
import jp.revati.skyabyss.rpg.skill.SkillCalling;
import jp.revati.skyabyss.rpg.skill.SkillManager;
import jp.revati.skyabyss.titan.end.ReturnBeacons;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// 転生 (docs/design/31): Lv 50 に着いた人が、鼓動の間の台座で、レベル・技・ステータスの点・天職を戻して「Lv 50+n」になる、物語のあとの遊び。
// 物語・章・移動・試練・装備・巨神の進み・魂盤は戻らない (できることは減らない)。
//
// ほかの担当 (技・見た目) が呼ぶ入口:
//   Rebirth.count(player)            転生した回数 (0〜20。クライアントでも読める)
//   Rebirth.tierColor(n[, millis])   名札などの色 (白 → 青緑 → 金 → プリズム)。tier(n) は段 0〜3
//   Rebirth.symbol(n)                記号 (✦ → ✧ → ✺)。levelLabel(level, n) は「Lv 50+n」
//   Rebirth.reset(player)            レベル・技・点・天職だけ戻す (回数は足さない。perform が回数を足す)
//   Rebirth.gearLevel(player)        装備・クエストの要求レベルに使うレベル (転生した人は 50)
public final class Rebirth {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, SkyAbyss.MODID);

    // 転生で残るもの。持ち主のクライアントにだけ同期する
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<RebirthData>> DATA = ATTACHMENTS.register("rebirth",
            () -> AttachmentType.serializable(RebirthData::new)
                    .copyOnDeath()
                    .sync((holder, to) -> holder == to, RebirthData.STREAM_CODEC)
                    .build());

    public static final ResourceLocation MOD_MOVE = id("rebirth/move");
    public static final ResourceLocation MOD_ATTACK = id("rebirth/attack_speed");
    public static final ResourceLocation MOD_MANA = id("rebirth/mana");
    public static final ResourceLocation MOD_HEALTH = id("rebirth/health");

    // 台座から離れてよい距離 (水平)・高さ
    public static final double ALTAR_RANGE = 24;
    public static final double ALTAR_HEIGHT = 12;

    // 「今日」の通し番号 (導きの欠片の 1 日の数え)。実際の日で数える。自己テストが差し替える
    public static LongSupplier today = () -> System.currentTimeMillis() / 86_400_000L;

    public enum Result {
        OK, NOT_MAX_LEVEL, AT_CAP, NO_CRYSTALS, IN_COMBAT, NOT_AT_ALTAR, BAD_ARM, BAD_BOON
    }

    private Rebirth() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, path);
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }

    // ---------------------------------------------------------------- 読む
    public static RebirthData data(Player player) {
        return player.getData(DATA);
    }

    public static void sync(Player player) {
        if (player instanceof ServerPlayer) {
            player.syncData(DATA);
        }
    }

    public static int count(Player player) {
        return Growth.get(player).rebirths;
    }

    public static boolean isRebirthed(Player player) {
        return count(player) > 0;
    }

    // 装備・クエストの要求レベルに使うレベル。転生した人は、レベルを 1 から上げ直している間も 50 として扱う (できることが減らない)
    public static int gearLevel(Player player) {
        GrowthData g = Growth.get(player);
        return g.rebirths > 0 ? GrowthRules.MAX_LEVEL : g.level;
    }

    public static int tier(int rebirths) {
        return RebirthRules.tier(rebirths);
    }

    public static int tierColor(int rebirths) {
        return RebirthRules.tierColor(rebirths);
    }

    public static int tierColor(int rebirths, long millis) {
        return RebirthRules.tierColor(rebirths, millis);
    }

    public static String symbol(int rebirths) {
        return RebirthRules.symbol(rebirths);
    }

    public static String levelLabel(int level, int rebirths) {
        return RebirthRules.levelLabel(level, rebirths);
    }

    public static String levelLabel(Player player) {
        GrowthData g = Growth.get(player);
        return RebirthRules.levelLabel(g.level, g.rebirths);
    }

    public static RebirthRules.Effects effects(Player player) {
        RebirthData d = data(player);
        int n = count(player);
        if (n <= 0 && d.nodes.isEmpty()) {
            return RebirthRules.Effects.NONE;
        }
        return RebirthRules.effects(d.nodes, d.boons, n);
    }

    public static int spareSlots(Player player) {
        return RebirthRules.spareSlots(count(player));
    }

    // ---------------------------------------------------------------- 効き目
    public static int scaleXp(Player player, int amount) {
        double xp = effects(player).xp();
        return xp <= 0 ? amount : (int) Math.round(amount * (1 + xp));
    }

    public static int scaleCooldown(Player player, int ticks) {
        double cut = effects(player).cooldown();
        return cut <= 0 ? ticks : Math.max(1, (int) Math.round(ticks * (1 - cut)));
    }

    // 修飾子を今の値で付け直す (Growth.applyModifiers から。ログイン・復活・ディメンション移動・転生のたび)
    public static void applyModifiers(Player player) {
        RebirthRules.Effects e = effects(player);
        set(player, Attributes.MOVEMENT_SPEED, MOD_MOVE, e.moveSpeed(), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        set(player, Attributes.ATTACK_SPEED, MOD_ATTACK, e.attackSpeed(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        set(player, Rpg.MAX_MANA, MOD_MANA, e.maxMana(), AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.MAX_HEALTH, MOD_HEALTH, e.maxHealth(), AttributeModifier.Operation.ADD_VALUE);
    }

    private static void set(Player player, Holder<Attribute> attribute, ResourceLocation id, double value, AttributeModifier.Operation op) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        if (value == 0) {
            instance.removeModifier(id);
            return;
        }
        AttributeModifier current = instance.getModifier(id);
        if (current != null && Math.abs(current.amount() - value) < 1e-9 && current.operation() == op) {
            return;
        }
        instance.addOrReplacePermanentModifier(new AttributeModifier(id, value, op));
    }

    // ---------------------------------------------------------------- 台座
    // 転生できる所 (背中の鼓動の間の台座のそば)。背中の外・台座から遠いところでは false
    public static boolean nearAltar(Player player) {
        String titan = Titans.titanOfBack(player.level().dimension());
        if (titan == null) {
            return false;
        }
        BlockPos hub = ReturnBeacons.hubOf(titan);
        if (hub == null) {
            return false;
        }
        double dx = player.getX() - (hub.getX() + 0.5);
        double dz = player.getZ() - (hub.getZ() + 0.5);
        return dx * dx + dz * dz <= ALTAR_RANGE * ALTAR_RANGE && Math.abs(player.getY() - hub.getY()) <= ALTAR_HEIGHT;
    }

    // ---------------------------------------------------------------- 恩恵の候補 (B07)
    public static List<Boon> offer(Player player) {
        UUID id = player.getUUID();
        return RebirthRules.offer(id.getMostSignificantBits() ^ id.getLeastSignificantBits(), count(player));
    }

    // ---------------------------------------------------------------- 転生する
    // 条件を調べるだけ (画面のボタンの見せ方と、perform の検めが同じ)。altar は台座を問うか
    public static Result check(ServerPlayer player, boolean needAltar) {
        GrowthData g = Growth.get(player);
        if (g.rebirths >= RebirthRules.MAX_REBIRTHS) {
            return Result.AT_CAP;
        }
        if (g.level < RebirthRules.MIN_LEVEL) {
            return Result.NOT_MAX_LEVEL;
        }
        if (needAltar && !player.isCreative() && !nearAltar(player)) {
            return Result.NOT_AT_ALTAR;
        }
        if (!SkillManager.quiet(player)) {
            return Result.IN_COMBAT;
        }
        if (!player.isCreative() && SkillManager.countCrystals(player) < RebirthRules.COST_CRYSTALS) {
            return Result.NO_CRYSTALS;
        }
        return Result.OK;
    }

    // 転生する。armId = 魂盤で次の節を足す腕、boonId = 3 択の恩恵。条件が足りなければ何も変えず理由を返す
    public static Result perform(ServerPlayer player, String armId, String boonId, boolean needAltar) {
        Result r = check(player, needAltar);
        if (r != Result.OK) {
            return r;
        }
        SoulArm arm = SoulArm.byId(armId);
        if (arm == null) {
            return Result.BAD_ARM;
        }
        Boon boon = Boon.byId(boonId);
        if (boon == null || !offer(player).contains(boon)) {
            return Result.BAD_BOON;
        }
        if (!player.isCreative()) {
            SkillManager.removeCrystals(player, RebirthRules.COST_CRYSTALS);
        }
        RebirthData d = data(player);
        GrowthData g = Growth.get(player);
        d.nodes.add(arm.id);
        d.boons.add(boon.id);
        d.lastRebirthMillis = System.currentTimeMillis();
        g.rebirths = RebirthRules.clampCount(g.rebirths + 1);
        reset(player);
        d.sanitize(g.rebirths);
        sync(player);
        Growth.sync(player);
        Growth.applyModifiers(player);
        celebrate(player, g.rebirths, arm, boon);
        return Result.OK;
    }

    // レベル・経験値・ステータスの点・技・刻印・天職だけ戻す (回数・魂盤・恩恵・装備・物語・型の保存は残る)。回数は足さない
    public static void reset(ServerPlayer player) {
        Growth.resetForRebirth(player);
        SkillManager.resetForRebirth(player);
        if (!(player instanceof FakePlayer)) {
            // 天職をもう一度選べるように (選べる段なら画面を開く)
            SkillCalling.offerSoon(player, 100, true);
        }
    }

    private static void celebrate(ServerPlayer player, int n, SoulArm arm, Boon boon) {
        if (player instanceof FakePlayer) {
            return;
        }
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 0.8f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), Rpg.LEVEL_UP.get(), SoundSource.PLAYERS, 0.9f, 0.6f);
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 60, 0.5, 1.0, 0.5, 0.06);
        level.sendParticles(ParticleTypes.GLOW, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.6, 1.0, 0.6, 0.02);
        Notice.alert(player, "message.skyabyss.rebirth.done", n);
        Notice.narrate(player, "message.skyabyss.rebirth.done_detail", Component.translatable(arm.nameKey()), Component.translatable(boon.nameKey()));
        jp.revati.skyabyss.advancement.Beats.fire(player, "rebirth:" + n);
    }

    // ---------------------------------------------------------------- 魂盤の欠片の節 (B05)
    // 導きの欠片 5 個で、好きな腕に節を 1 つ足す (一生で 6 つまで)
    public static boolean claimShardNode(ServerPlayer player, String armId) {
        RebirthData d = data(player);
        SoulArm arm = SoulArm.byId(armId);
        if (arm == null || !d.canClaimShardNode()) {
            return false;
        }
        d.shards -= RebirthRules.SHARDS_PER_NODE;
        d.shardNodes++;
        d.nodes.add(arm.id);
        sync(player);
        Growth.applyModifiers(player);
        Notice.gain(player, ModText.progress("message.skyabyss.rebirth.shard_node", Component.translatable(arm.nameKey())));
        return true;
    }

    // ---------------------------------------------------------------- 試練を遊んだ (B05・結晶の出)
    // その試練を越えた人たち (一緒に遊んだグループ) から呼ぶ。
    //   転生者に: 結晶の出 (魂盤・恩恵) で結晶 1 つが出ることがある。未転生の仲間と遊ぶと導きの欠片 (1 日 3 回まで・同じ人は 1 日 1 回まで)
    //   未転生の仲間に: 次のレベルに要る経験値の 1 割 (最低 10)
    public static void onTrialCleared(java.util.Collection<ServerPlayer> players) {
        List<ServerPlayer> guides = new ArrayList<>();
        List<ServerPlayer> newcomers = new ArrayList<>();
        for (ServerPlayer p : players) {
            if (p.isSpectator()) {
                continue;
            }
            (count(p) > 0 ? guides : newcomers).add(p);
        }
        for (ServerPlayer g : guides) {
            double chance = effects(g).crystal();
            if (chance > 0 && g.getRandom().nextDouble() < chance) {
                jp.revati.skyabyss.pouch.PouchRouting.placeBack(g, new ItemStack(ModItems.RESONANCE_CRYSTAL.get()));
                Notice.gain(g, ModText.progress("message.skyabyss.rebirth.crystal_bonus"));
            }
            if (!newcomers.isEmpty()) {
                giveGuideShard(g, newcomers);
            }
        }
        if (!guides.isEmpty()) {
            for (ServerPlayer n : newcomers) {
                int need = Growth.get(n).xpNeed();
                if (need > 0) {
                    Growth.addXp(n, Math.max(10, need / 10), "growth.skyabyss.reason.guide");
                }
            }
        }
    }

    // 導きの欠片を 1 つ渡す (限りがあれば false)
    static boolean giveGuideShard(ServerPlayer guide, List<ServerPlayer> newcomers) {
        RebirthData d = data(guide);
        long day = today.getAsLong();
        if (d.guideDay != day) {
            d.guideDay = day;
            d.guideToday = 0;
            d.guideMet.clear();
        }
        if (d.guideToday >= RebirthRules.GUIDE_PER_DAY) {
            return false;
        }
        for (ServerPlayer n : newcomers) {
            String key = n.getUUID().toString();
            if (d.guideMet.contains(key)) {
                continue;
            }
            d.guideMet.add(key);
            d.guideToday++;
            d.shards++;
            sync(guide);
            Notice.gain(guide, ModText.progress("message.skyabyss.rebirth.shard", d.shards, RebirthRules.SHARDS_PER_NODE));
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- 若い運び手との絆 (B14)
    // 持ち主 (id) の転生の回数から、子の運ぶ速さの倍率と、呼べる・乗れる距離の足し
    public static double youngSpeed(net.minecraft.server.MinecraftServer server, UUID owner) {
        return RebirthRules.bondSpeed(countOf(server, owner));
    }

    public static double youngRange(net.minecraft.server.MinecraftServer server, UUID owner) {
        return RebirthRules.bondRange(countOf(server, owner));
    }

    private static int countOf(net.minecraft.server.MinecraftServer server, UUID id) {
        if (server == null) {
            return 0;
        }
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        return p == null ? 0 : count(p);
    }
}
