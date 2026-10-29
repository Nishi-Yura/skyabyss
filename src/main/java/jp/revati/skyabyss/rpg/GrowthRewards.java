package jp.revati.skyabyss.rpg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.TitanTravel;
import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 経験値の入り口: 敵の撃破、ボス (参加者ごと)、試練の踏破、新しい場所。
// あわせて、成長の修飾子を付け直す時機 (ログイン・復活・ディメンション移動) と、敏捷の飛び道具の補正を受け持つ
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class GrowthRewards {
    // ---------------------------------------------------------------- 数値 (表は 04 の 3 章)
    // 岩鯨の推奨レベル。これ +3 を超えると、岩鯨で得る経験値が減っていく
    public static final int STONE_WHALE_LEVEL = 12;
    // 潮殻の巨神の推奨レベル (04 の 11 章の判断 6 の案 A)
    public static final int TIDE_SHELL_LEVEL = 18;
    // 雷翼の巨神の推奨レベル (12 の 6.6節)
    public static final int THUNDER_WING_LEVEL = 24;

    public record Source(int xp, int contentLevel) {
    }

    // Mod の敵 (エンティティ ID → 経験値と推奨レベル)。倒したとき近くにいた人で分ける
    public static final Map<String, Source> MOD_ENEMIES = new LinkedHashMap<>();
    // この札のある敵は倒しても経験値を出さない (残像など)
    public static final String NO_REWARD = "skyabyss_no_reward";
    static {
        MOD_ENEMIES.put(SkyAbyss.MODID + ":bone_sentinel", new Source(12, STONE_WHALE_LEVEL));
        MOD_ENEMIES.put(SkyAbyss.MODID + ":rib_archer", new Source(10, STONE_WHALE_LEVEL));
        MOD_ENEMIES.put(SkyAbyss.MODID + ":moss_crawler", new Source(9, STONE_WHALE_LEVEL));
        // 背の新しい敵: 骨の砲手 14、骨鳥 4、老番兵 (名づき) 60
        MOD_ENEMIES.put(SkyAbyss.MODID + ":bone_mortar", new Source(14, STONE_WHALE_LEVEL));
        MOD_ENEMIES.put(SkyAbyss.MODID + ":bone_gull", new Source(4, STONE_WHALE_LEVEL));
        MOD_ENEMIES.put(SkyAbyss.MODID + ":elder_sentinel", new Source(60, STONE_WHALE_LEVEL));
        // 一章の地上の敵 (章の経験値の枠には数えない小さな分): 岩殻虫 7、棘うさぎ 5、骨負い 2、老いた歩哨 40
        MOD_ENEMIES.put(SkyAbyss.MODID + ":rock_shell_beetle", new Source(7, STONE_WHALE_LEVEL));
        MOD_ENEMIES.put(SkyAbyss.MODID + ":spine_hare", new Source(5, STONE_WHALE_LEVEL));
        MOD_ENEMIES.put(SkyAbyss.MODID + ":bone_hauler", new Source(2, STONE_WHALE_LEVEL));
        MOD_ENEMIES.put(SkyAbyss.MODID + ":elder_sentry", new Source(40, STONE_WHALE_LEVEL));
        // 大陸の新しい敵 (docs/design/44。推奨は島・草原のレベル)
        MOD_ENEMIES.put(SkyAbyss.MODID + ":bandit", new Source(8, 3));
        MOD_ENEMIES.put(SkyAbyss.MODID + ":bandit_archer", new Source(7, 3));
        MOD_ENEMIES.put(SkyAbyss.MODID + ":bandit_chief", new Source(30, 4));
        MOD_ENEMIES.put(SkyAbyss.MODID + ":bone_footman", new Source(9, 5));
        MOD_ENEMIES.put(SkyAbyss.MODID + ":bone_bowman", new Source(8, 5));
        MOD_ENEMIES.put(SkyAbyss.MODID + ":bone_standard", new Source(40, 5));
        MOD_ENEMIES.put(SkyAbyss.MODID + ":grain_rat", new Source(3, 3));
        MOD_ENEMIES.put(SkyAbyss.MODID + ":stray_wisp", new Source(4, 4));
        MOD_ENEMIES.put(SkyAbyss.MODID + ":feral_dog", new Source(6, 4));
        MOD_ENEMIES.put(SkyAbyss.MODID + ":moss_dead", new Source(12, 12));
        MOD_ENEMIES.put(SkyAbyss.MODID + ":mist_angler", new Source(14, 18));
        MOD_ENEMIES.put(SkyAbyss.MODID + ":thunder_wolf", new Source(12, 24));
        MOD_ENEMIES.put(SkyAbyss.MODID + ":rock_boar", new Source(120, 12));
        MOD_ENEMIES.put(SkyAbyss.MODID + ":tide_crab", new Source(160, 18));
        MOD_ENEMIES.put(SkyAbyss.MODID + ":storm_ram", new Source(200, 24));
        MOD_ENEMIES.put(SkyAbyss.MODID + ":ash_lizard", new Source(260, 30));
        // 試練の外で呼ばれた番兵長・心臓 (コマンドなど) は、ふつうの敵として分ける
        MOD_ENEMIES.put(SkyAbyss.MODID + ":whalebone_captain", new Source(100, STONE_WHALE_LEVEL));
        MOD_ENEMIES.put(SkyAbyss.MODID + ":stone_whale_heart", new Source(300, STONE_WHALE_LEVEL));
        // 潮殻の背の敵 (08 の 6.6節。推奨レベル 18)
        MOD_ENEMIES.put(SkyAbyss.MODID + ":coral_thrall", new Source(14, TIDE_SHELL_LEVEL));
        MOD_ENEMIES.put(SkyAbyss.MODID + ":reef_scuttler", new Source(3, TIDE_SHELL_LEVEL));
        MOD_ENEMIES.put(SkyAbyss.MODID + ":bubble_puffer", new Source(10, TIDE_SHELL_LEVEL));
        MOD_ENEMIES.put(SkyAbyss.MODID + ":reef_nest", new Source(20, TIDE_SHELL_LEVEL));
        // 雷翼の背の敵 (12 の 6.6節。推奨レベル 24): 羽守 18、雷蛾 5、雷乞い 14
        MOD_ENEMIES.put(SkyAbyss.MODID + ":feather_warden", new Source(18, THUNDER_WING_LEVEL));
        MOD_ENEMIES.put(SkyAbyss.MODID + ":static_moth", new Source(5, THUNDER_WING_LEVEL));
        MOD_ENEMIES.put(SkyAbyss.MODID + ":thunder_supplicant", new Source(14, THUNDER_WING_LEVEL));
        // 雷翼の中ボス 雷獣 (12 の 6.6節。推奨レベル 24)。試練の雷獣は参加者の一覧で配る (RaijuLoot)。ここは試練の外で呼ばれたもの
        MOD_ENEMIES.put(SkyAbyss.MODID + ":raiju", new Source(170, THUNDER_WING_LEVEL));
        // 余燼の背の敵 (13 の 6.6節。推奨レベル 30): 灰被り 26、火の粉蛾 4、燠虫 16
        jp.revati.skyabyss.titan.ember.mob.EmberMobSetup.Xp.put(MOD_ENEMIES);
        jp.revati.skyabyss.titan.ember.heart.EmberHeartSetup.Xp.put(MOD_ENEMIES);
        // 余燼の中ボス 灰の鍛え手 (13 の 6.6節。推奨レベル 30)。試練の鍛え手は参加者の一覧で配る (AshSmithLoot)。ここは試練の外で呼ばれたもの
        MOD_ENEMIES.put(SkyAbyss.MODID + ":ash_smith", new Source(220, 30));
        MOD_ENEMIES.put(SkyAbyss.MODID + ":thunder_wing_heart", new Source(480, THUNDER_WING_LEVEL)); // 雷翼の心臓 (12 の 6.6節。試練の外で倒したとき)
        // 二章の地上の敵 (章の経験値の枠には数えない小さな分): 潮だまり貝 6、砂潜り 5、宿借り 2、潮の番人 50
        MOD_ENEMIES.put(SkyAbyss.MODID + ":tidepool_clam", new Source(6, TIDE_SHELL_LEVEL));
        MOD_ENEMIES.put(SkyAbyss.MODID + ":sand_burrower", new Source(5, TIDE_SHELL_LEVEL));
        MOD_ENEMIES.put(SkyAbyss.MODID + ":drift_hermit", new Source(2, TIDE_SHELL_LEVEL));
        MOD_ENEMIES.put(SkyAbyss.MODID + ":shore_warden", new Source(50, TIDE_SHELL_LEVEL));
        // 三章の地上の敵 (P16。章の経験値の枠には数えない小さな分。推奨レベル 21): 帯電毛玉 5、峰の雛 6、雷拾い 2、焼けた羽守 60
        MOD_ENEMIES.put(SkyAbyss.MODID + ":static_puff", new Source(5, 21));
        MOD_ENEMIES.put(SkyAbyss.MODID + ":peak_fledgling", new Source(6, 21));
        MOD_ENEMIES.put(SkyAbyss.MODID + ":lightning_gleaner", new Source(2, 21));
        MOD_ENEMIES.put(SkyAbyss.MODID + ":scorched_warden", new Source(60, 21));
    }


    // 試練のボス: 参加者それぞれが満額を受け取る (人数で強くなるぶん、取り分は減らさない)
    public static final int CAPTAIN_XP = 100;
    public static final int CAPTAIN_FIRST_BONUS = 50;
    public static final int HEART_XP = 300;
    public static final int HEART_FIRST_BONUS = 200;

    // バニラのボス (日ごとの上限に数えない)
    public static final Map<String, Integer> VANILLA_BOSSES = Map.of(
            "minecraft:ender_dragon", 300,
            "minecraft:wither", 200,
            "minecraft:warden", 60,
            "minecraft:elder_guardian", 40);

    // 試練の踏破 (共鳴石が歌った)。そのとき石の近くにいた人へ
    public static final int TRIAL_XP = 40;
    public static final double TRIAL_RADIUS = 16;
    // 新しい場所 (一人一回)
    public static final int REST_POINT_XP = 30;
    public static final int DIMENSION_XP = 50;
    public static final int BIOME_XP = 10;
    // 序盤の「はじめて見た物」(伝承の項目が記されたとき。一人一回)04 の 3.2。
    // 最初の 1 時間で Lv2 (鐘の宿り) → Lv3 (最初の夜まで) に届くための配分。足音・骨・歌は、見ただけで一歩ぶんの手ごたえ
    public static final Map<String, Integer> LORE_XP = new LinkedHashMap<>();
    static {
        LORE_XP.put("prologue/the_book", 5);
        LORE_XP.put("prologue/camp", 5);
        LORE_XP.put("prologue/footprints", 10);
        LORE_XP.put("prologue/shadow", 15);
        LORE_XP.put("prologue/footsteps", 10);
        LORE_XP.put("prologue/bone_hill", 10);
        LORE_XP.put("prologue/sky_song", 20);
        LORE_XP.put("chapter1/stone_whale", 30);
    }
    // 撃破の経験値を分ける範囲
    public static final double SHARE_RADIUS = 32;
    public static final double SPAWNER_FACTOR = 0.25;

    private GrowthRewards() {
    }

    // バニラの敵の経験値: 最大体力の 1/5 (1〜10)
    public static int vanillaXp(LivingEntity mob) {
        return Mth.clamp(Math.round(mob.getMaxHealth() / 5f), 1, 10);
    }

    // ---------------------------------------------------------------- 試練のボス (TitanLoot から呼ぶ)
    // boss: "captain" か "heart"。participants は倒れて復活を待つ人も含む
    public static void onBossDefeat(ServerLevel level, List<ServerPlayer> participants, String boss) {
        boolean heart = "heart".equals(boss);
        int base = heart ? HEART_XP : CAPTAIN_XP;
        int first = heart ? HEART_FIRST_BONUS : CAPTAIN_FIRST_BONUS;
        String reason = heart ? "entity.skyabyss.stone_whale_heart" : "entity.skyabyss.whalebone_captain";
        List<ServerPlayer> seen = new ArrayList<>();
        for (ServerPlayer player : participants) {
            if (player == null || seen.contains(player)) {
                continue;
            }
            seen.add(player);
            GrowthData data = Growth.get(player);
            int amount = (int) Math.round(base * GrowthRules.levelFalloff(data.level, STONE_WHALE_LEVEL));
            if (Growth.discover(player, "first:" + SkyAbyss.MODID + ":" + boss)) {
                amount += first;
            }
            Growth.addXp(player, amount, reason);
        }
        // パーティの仲間で戦いにいなかった人 (同じ背中の別の所にいた人など) には、満額の分け前の割合 (初回の上乗せなし)
        double rate = jp.revati.skyabyss.party.Parties.shareRate(level.getServer());
        for (ServerPlayer mate : jp.revati.skyabyss.party.Parties.absentMates(level, seen)) {
            int amount = (int) Math.round(base * rate * GrowthRules.levelFalloff(Growth.get(mate).level, STONE_WHALE_LEVEL));
            Growth.addXp(mate, amount, "growth.skyabyss.reason.party");
        }
    }

    // 伝承が記されたとき (Lore.unlock から)。表にある項目だけ、一人一回
    public static void onLore(ServerPlayer player, String entry) {
        Integer xp = LORE_XP.get(entry);
        if (xp != null && !(player instanceof FakePlayer) && Growth.discover(player, "lore:" + entry)) {
            Growth.addXp(player, xp, "growth.skyabyss.reason.new_sight");
        }
    }

    // ---------------------------------------------------------------- 敵の撃破
    @SubscribeEvent(priority = EventPriority.LOW)
    static void onDeath(LivingDeathEvent event) {
        LivingEntity mob = event.getEntity();
        if (event.isCanceled() || mob instanceof Player || !(mob.level() instanceof ServerLevel level)) {
            return;
        }
        // 試練の番兵長と心臓は、参加者の一覧で配る (onBossDefeat)
        if (mob instanceof jp.revati.skyabyss.titan.tide.back.TideBeckoner beckoner && beckoner.isTrialMob()) {
            // 試練の潮招きは、参加者の一覧で配る (TideFlats)
            return;
        }
        if (mob.getTags().contains(TitanBackTrials.HEART_TAG)
                || (mob.getTags().contains(TitanBackTrials.MOB_TAG) && mob.getType() == jp.revati.skyabyss.registry.ModEntities.WHALEBONE_CAPTAIN.get())) {
            return;
        }
        // 残像 (下弦の淵の敵が倒れて立ち上がったもの) は経験値を出さない
        if (mob.getTags().contains(NO_REWARD)) {
            return;
        }
        ServerPlayer killer = killer(event.getSource(), mob);
        // 機械 (ほかの Mod の偽のプレイヤー) が倒した分は数えない
        if (killer == null || killer instanceof FakePlayer) {
            return;
        }
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
        Source mod = MOD_ENEMIES.get(id);
        Integer boss = VANILLA_BOSSES.get(id);
        boolean spawner = mob instanceof Mob m && m.getSpawnType() == MobSpawnType.SPAWNER;
        if (mod == null && boss == null) {
            // バニラはスポナーの敵を数えない (経験値の畑を作らせない)
            if (!(mob instanceof Enemy) || spawner) {
                return;
            }
        }
        List<ServerPlayer> share = sharers(level, mob, killer);
        double part = GrowthRules.partyShare(share.size());
        for (ServerPlayer player : share) {
            giveKill(level, player, mob, mod, boss, spawner, part, "");
        }
        // パーティの仲間で居合わせなかった人: 居合わせた人の取り分の割合 (10 の 6 章。距離・レベル差は Parties が見る)
        double rate = jp.revati.skyabyss.party.Parties.shareRate(level.getServer());
        for (ServerPlayer mate : jp.revati.skyabyss.party.Parties.absentMates(level, share)) {
            giveKill(level, mate, mob, mod, boss, spawner, part * rate, "growth.skyabyss.reason.party");
        }
    }

    private static void giveKill(ServerLevel level, ServerPlayer player, LivingEntity mob, @Nullable Source mod, @Nullable Integer boss,
            boolean spawner, double part, String reason) {
        double amount;
        if (mod != null) {
            // Mod の敵もスポナー (水路の顎の巣) から湧いたものは 1/4
            amount = mod.xp() * part * GrowthRules.levelFalloff(Growth.get(player).level, mod.contentLevel())
                    * (spawner ? SPAWNER_FACTOR : 1.0);
        } else if (boss != null) {
            amount = boss * part;
        } else {
            amount = vanillaBudget(level, Growth.get(player), vanillaXp(mob) * part);
        }
        int xp = GrowthRules.roundRandom(amount, level.random.nextDouble());
        if (xp > 0) {
            Growth.addXp(player, xp, reason);
        }
    }

    // 倒した人 (飼い慣らした動物の主、飛び道具の持ち主を含む)
    @Nullable
    private static ServerPlayer killer(DamageSource source, LivingEntity mob) {
        if (source.getEntity() instanceof ServerPlayer p) {
            return p;
        }
        if (source.getEntity() instanceof OwnableEntity pet && pet.getOwner() instanceof ServerPlayer p) {
            return p;
        }
        return mob.getKillCredit() instanceof ServerPlayer p ? p : null;
    }

    // 撃破を分ける人: 倒した人と、近く (32 ブロック) にいた生きているプレイヤー
    public static List<ServerPlayer> sharers(ServerLevel level, LivingEntity mob, ServerPlayer killer) {
        List<ServerPlayer> list = new ArrayList<>(level.getEntitiesOfClass(ServerPlayer.class, mob.getBoundingBox().inflate(SHARE_RADIUS),
                p -> p.isAlive() && !p.isSpectator() && p.distanceToSqr(mob) <= SHARE_RADIUS * SHARE_RADIUS));
        if (!list.contains(killer) && killer.level() == level) {
            list.add(killer);
        }
        return list;
    }

    // バニラの敵は日ごとに VANILLA_DAILY (100) までは満額、そのあとは 20%
    static double vanillaBudget(ServerLevel level, GrowthData data, double amount) {
        long day = level.getGameTime() / 24000L;
        if (data.vanillaDay != day) {
            data.vanillaDay = day;
            data.vanillaToday = 0;
        }
        double full = Math.max(0, Math.min(amount, GrowthRules.VANILLA_DAILY - data.vanillaToday));
        double tired = (amount - full) * GrowthRules.VANILLA_TIRED;
        data.vanillaToday += (int) Math.ceil(full);
        return full + tired;
    }

    // ---------------------------------------------------------------- 新しい場所
    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator() || !player.isAlive()) {
            return;
        }
        if (player.tickCount % 20 != 7) {
            return;
        }
        ServerLevel level = player.serverLevel();
        // 新しい土地 (バイオーム)。一人一回
        player.level().getBiome(player.blockPosition()).unwrapKey().ifPresent(key -> {
            if (Growth.discover(player, "biome:" + key.location())) {
                Growth.addXp(player, BIOME_XP, net.minecraft.Util.makeDescriptionId("biome", key.location()));
            }
        });
        // 岩鯨の背の休み場 (試練の担当が記録した休み場を読む)。一人一回
        if (level.dimension() == TitanTravel.TITAN_BACK) {
            CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
            String cp = persisted.getString("skyabyss_checkpoint");
            if (!cp.isEmpty() && Growth.discover(player, "rest:stone_whale/" + cp)) {
                Growth.addXp(player, REST_POINT_XP, "checkpoint.skyabyss." + cp);
            }
        }
    }

    // 新しいディメンション (地上を除く)。一人一回
    @SubscribeEvent
    static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Growth.applyModifiers(player);
        ResourceKey<Level> to = event.getTo();
        if (to != Level.OVERWORLD && Growth.discover(player, "dim:" + to.location())) {
            Growth.addXp(player, DIMENSION_XP, "growth.skyabyss.reason.new_land");
        }
    }

    // ---------------------------------------------------------------- 試練の踏破 (順路の段を越えたとき TrialRoute.onCleared から)
    // 越えた試練の参加者に渡し、同じ背中にいない仲間にも分け前。数字は各背の TRIAL_XP と推奨レベル (P2-1)。
    // 岩鯨は石でもレイドの部屋でもここ。潮殻・雷翼・余燼の原型の石は自分で渡すので、ここへ来るのはレイドの部屋を越えたときだけ
    public static void onWhaleTrialCleared(ServerLevel level, List<ServerPlayer> near) {
        onRouteTrialCleared(level, jp.revati.skyabyss.progress.Titans.STONE_WHALE, near, null);
    }

    // titan は進行の名前 (stone_whale / tide_shell / thunder_wing / ember)。signal はその巨神の試練の合図 (storm_trial_west など。無ければ null)
    public static void onRouteTrialCleared(ServerLevel level, String titan, List<ServerPlayer> near, @Nullable String signal) {
        int base = trialXp(titan);
        int rec = trialLevel(titan);
        String reason = trialReason(titan);
        for (ServerPlayer player : near) {
            int xp = (int) Math.round(base * GrowthRules.levelFalloff(Growth.get(player).level, rec));
            Growth.addXp(player, xp, reason);
            if (signal != null) {
                jp.revati.skyabyss.quest.Quests.signal(player, signal);
            }
        }
        double rate = jp.revati.skyabyss.party.Parties.shareRate(level.getServer());
        for (ServerPlayer mate : jp.revati.skyabyss.party.Parties.absentMates(level, near)) {
            int xp = (int) Math.round(base * rate * GrowthRules.levelFalloff(Growth.get(mate).level, rec));
            Growth.addXp(mate, xp, "growth.skyabyss.reason.party");
        }
        // サブクエストの「試練を踏破する」と進捗 trial:<巨神> (居合わせた人と、同じ背中の仲間。quest/free)
        jp.revati.skyabyss.quest.Quests.signalWithParty(level, near, jp.revati.skyabyss.quest.free.FreeQuestEvents.TRIAL_CLEARED);
    }

    public static int trialXp(String titan) {
        return switch (titan) {
            case jp.revati.skyabyss.progress.Titans.TIDE_SHELL -> jp.revati.skyabyss.titan.tide.back.TideBackTrials.TRIAL_XP;
            case jp.revati.skyabyss.progress.Titans.THUNDER_WING -> jp.revati.skyabyss.titan.storm.back.StormBackTrials.TRIAL_XP;
            case jp.revati.skyabyss.progress.Titans.EMBER -> jp.revati.skyabyss.titan.ember.back.EmberBackTrials.TRIAL_XP;
            default -> TRIAL_XP;
        };
    }

    private static int trialLevel(String titan) {
        return switch (titan) {
            case jp.revati.skyabyss.progress.Titans.TIDE_SHELL -> jp.revati.skyabyss.titan.tide.heart.TideLoot.TIDE_LEVEL;
            case jp.revati.skyabyss.progress.Titans.THUNDER_WING -> jp.revati.skyabyss.titan.storm.back.StormBackTrials.STORM_LEVEL;
            case jp.revati.skyabyss.progress.Titans.EMBER -> jp.revati.skyabyss.titan.ember.back.EmberBackTrials.EMBER_LEVEL;
            default -> STONE_WHALE_LEVEL;
        };
    }

    private static String trialReason(String titan) {
        return switch (titan) {
            case jp.revati.skyabyss.progress.Titans.TIDE_SHELL -> "growth.skyabyss.reason.tide_trial";
            case jp.revati.skyabyss.progress.Titans.THUNDER_WING -> "growth.skyabyss.reason.storm_trial";
            case jp.revati.skyabyss.progress.Titans.EMBER -> "growth.skyabyss.reason.ember_trial";
            default -> "growth.skyabyss.reason.trial";
        };
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        HEALTH_AFTER_EQUIP.clear();
    }

    // ---------------------------------------------------------------- 修飾子を付け直す時機
    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Growth.get(player).sanitize();
            Growth.applyModifiers(player);
            Growth.sync(player);
        }
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Growth.applyModifiers(player);
            // 倒れて目を覚ましたら、耐久で増えた分まで満たす (バニラの復活と同じく満タン)
            if (!event.isEndConquered()) {
                player.setHealth(player.getMaxHealth());
                // 装備の特性 (活力) の最大体力は、次の tick で装備を見直したときに付く。付いてからもう一度満たす
                HEALTH_AFTER_EQUIP.put(player.getUUID(), Float.MAX_VALUE);
            }
        }
    }

    // ---------------------------------------------------------------- 装備の最大体力と体力の持ち越し
    // 装備の修飾子 (特性の活力など) は保存されず、ログイン・復活のあとの最初の tick で付く。
    // それより先に体力が今の上限 (装備なし) に切り詰められるので、最後に見た体力を覚えておき、
    // 修飾子が付いたあとで戻す (復活のときは満たす)
    static final String HEALTH_TAG = "skyabyss_health";
    private static final Map<java.util.UUID, Float> HEALTH_AFTER_EQUIP = new HashMap<>();

    @SubscribeEvent
    static void onLoginHealth(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            CompoundTag data = player.getPersistentData();
            if (data.contains(HEALTH_TAG) && player.isAlive()) {
                float saved = data.getFloat(HEALTH_TAG);
                // 切り詰められていたときだけ (上限ちょうどになっている)
                if (saved > player.getHealth() && player.getHealth() >= player.getMaxHealth() - 1e-3f) {
                    HEALTH_AFTER_EQUIP.put(player.getUUID(), saved);
                }
            }
        }
    }

    @SubscribeEvent
    static void onLogoutHealth(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            HEALTH_AFTER_EQUIP.remove(player.getUUID());
            if (player.isAlive()) {
                player.getPersistentData().putFloat(HEALTH_TAG, player.getHealth());
            }
        }
    }

    @SubscribeEvent
    static void onPlayerTickHealth(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) {
            return;
        }
        if (!HEALTH_AFTER_EQUIP.isEmpty()) {
            Float target = HEALTH_AFTER_EQUIP.remove(player.getUUID());
            if (target != null && player.isAlive() && target > player.getHealth()) {
                player.setHealth(Math.min(target, player.getMaxHealth()));
            }
        }
        // 自動保存に備えて 1 秒ごとに覚えておく (ログアウトのときは onLogoutHealth で最新にする)
        if (player.tickCount % 20 == 11 && player.isAlive()) {
            player.getPersistentData().putFloat(HEALTH_TAG, player.getHealth());
        }
    }

    // ---------------------------------------------------------------- 敏捷: 飛び道具の威力
    @SubscribeEvent
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        if (source.getDirectEntity() instanceof Projectile && source.getEntity() instanceof ServerPlayer shooter) {
            int agility = Growth.get(shooter).stat(GrowthRules.Stat.AGILITY);
            if (agility > 0) {
                event.setAmount((float) (event.getAmount() * (1 + GrowthRules.projectileMultiplier(agility))));
            }
        }
    }
}
