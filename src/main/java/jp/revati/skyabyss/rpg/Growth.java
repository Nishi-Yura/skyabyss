package jp.revati.skyabyss.rpg;

import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.GrowthRules.Stat;
import jp.revati.skyabyss.rpg.net.GrowthGainPayload;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

// 成長の記録を読み書きする入口。ほかの担当 (報酬、クエスト、スキル) もここを通す。
// 書き換えたら sync で持ち主へ送り、ステータスが変わったら修飾子を付け直す
public final class Growth {
    // 修飾子の ID。バニラの値は置き換えず、この ID の分だけ上乗せする
    public static final ResourceLocation MOD_STRENGTH = id("growth/strength_melee");
    public static final ResourceLocation MOD_AGILITY_MOVE = id("growth/agility_move");
    public static final ResourceLocation MOD_AGILITY_ATTACK = id("growth/agility_attack_speed");
    public static final ResourceLocation MOD_SPIRIT = id("growth/spirit_mana");
    public static final ResourceLocation MOD_VITALITY = id("growth/vitality_health");

    private Growth() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, path);
    }

    public static GrowthData get(Player player) {
        return player.getData(Rpg.GROWTH);
    }

    public static void sync(Player player) {
        if (player instanceof ServerPlayer) {
            player.syncData(Rpg.GROWTH);
        }
    }

    // ---------------------------------------------------------------- 経験値
    // 経験値を足す。reason は画面に出す理由の翻訳キー (空なら数だけ)。上がったレベルの数を返す
    public static int addXp(ServerPlayer player, int amount, String reason) {
        if (amount <= 0) {
            return 0;
        }
        GrowthData data = get(player);
        // 転生の恩恵「学びの手」で経験値が少し増える (docs/design/31 の B07)
        amount = jp.revati.skyabyss.rebirth.Rebirth.scaleXp(player, amount);
        // 物語の進みで決まるレベルの上限 (10 の 5 章)
        int cap = jp.revati.skyabyss.progress.Progression.levelCap(player);
        int gained = addXpRaw(data, amount, cap);
        sync(player);
        if (gained > 0) {
            applyModifiers(player);
            celebrate(player, data, gained);
            if (data.level >= cap && cap < GrowthRules.MAX_LEVEL) {
                jp.revati.skyabyss.progress.Progression.tellCapped(player, cap);
            }
        }
        if (player.connection != null) {
            PacketDistributor.sendToPlayer(player, new GrowthGainPayload(amount, reason, gained));
        }
        return gained;
    }

    // 記録だけを進める (同期・演出なし)。自己テストからも使う
    public static int addXpRaw(GrowthData data, long amount) {
        return addXpRaw(data, amount, GrowthRules.MAX_LEVEL);
    }

    // cap: 物語の進みで決まるレベルの上限。上限のレベルでは、バーが満ちる 1 手前まで貯まり、あふれた分は捨てる
    // (物語が進んで上限が上がれば、次の経験値ですぐ上がる)。50 では 0 のまま (バーは満ちたまま)
    public static int addXpRaw(GrowthData data, long amount, int cap) {
        int before = data.level;
        int limit = Math.min(cap, GrowthRules.MAX_LEVEL);
        long xp = data.xp + amount;
        while (data.level < limit && xp >= data.xpNeed()) {
            xp -= data.xpNeed();
            data.level++;
        }
        if (data.level >= GrowthRules.MAX_LEVEL) {
            data.xp = 0;
        } else {
            data.xp = (int) Math.min(xp, data.xpNeed() - 1L);
        }
        return data.level - before;
    }

    // レベルを直接決める (管理者・テスト用)。使いすぎた力は戻す
    public static void setLevel(ServerPlayer player, int level) {
        GrowthData data = get(player);
        data.level = level;
        data.xp = 0;
        data.sanitize();
        sync(player);
        applyModifiers(player);
    }

    // 転生 (docs/design/31): レベル・経験値・ステータスの点・振り直しの回数を戻す。追加の点 (bonusPoints)・記憶の断片・発見の記録は残す。
    // 転生の回数を 1 足すのは呼ぶ側 (Rebirth)
    public static void resetForRebirth(ServerPlayer player) {
        GrowthData data = get(player);
        data.level = 1;
        data.xp = 0;
        java.util.Arrays.fill(data.stats, 0);
        data.statRespecs = 0;
        data.sanitize();
        sync(player);
        applyModifiers(player);
    }

    // レベルが上がった知らせ: 鈴のような音 (近くの仲間にも小さく聞こえる)、光の粒、チャットに一行
    private static void celebrate(ServerPlayer player, GrowthData data, int gained) {
        for (int n = data.level - gained + 1; n <= data.level; n++) {
            jp.revati.skyabyss.advancement.Beats.fire(player, "level:" + n);
        }
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.getX(), player.getY(), player.getZ(), Rpg.LEVEL_UP.get(), SoundSource.PLAYERS, 0.9f, 1f);
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 24, 0.45, 0.7, 0.45, 0.02);
        // 基礎の振り分けは無い (技の木の節で上がる。docs/design/48)。知らせは技の木の点が増えたこと
        int skill = jp.revati.skyabyss.rpg.skill.SkillRules.pointsForLevel(data.level) - jp.revati.skyabyss.rpg.skill.SkillRules.pointsForLevel(data.level - gained);
        Notice.levelUp(player, "message.skyabyss.growth.level_up", data.level, skill);
        if (data.level >= GrowthRules.MAX_LEVEL) {
            Notice.narrate(player, "message.skyabyss.growth.max_level");
        }
    }

    // ---------------------------------------------------------------- ステータス
    // 力を振り分ける。add は 4 つのステータスへの追加量。正しくなければ何もせず false
    public static boolean allocate(ServerPlayer player, int[] add) {
        // 基礎は技の木の節で上がる (docs/design/48)。振り分けは受け付けない
        if (true) {
            return false;
        }
        GrowthData data = get(player);
        if (!canAllocate(data, add)) {
            return false;
        }
        for (int i = 0; i < add.length; i++) {
            data.stats[i] += add[i];
        }
        sync(player);
        applyModifiers(player);
        return true;
    }

    public static boolean canAllocate(GrowthData data, int[] add) {
        if (add == null || add.length != data.stats.length) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < add.length; i++) {
            // 引き算で比べる (足し算だと、改造したクライアントの巨大な値で int があふれて通ってしまう)
            if (add[i] < 0 || add[i] > GrowthRules.STAT_CAP - data.stats[i]) {
                return false;
            }
            sum += add[i];
        }
        return sum > 0 && sum <= data.unspentPoints();
    }

    // 振り分けを全部戻す (管理者・テスト用。回数も費用も数えない)
    public static void resetStats(ServerPlayer player) {
        GrowthData data = get(player);
        java.util.Arrays.fill(data.stats, 0);
        sync(player);
        applyModifiers(player);
    }

    // ステータス画面の「振り直す」(00 の 3 = A): 技の振り直しと同じく、共鳴の炉のそばで共鳴の結晶を払う。
    // 費用も技と同じ表 (初回無料、以後 1〜5) だが、回数は技と別に数える。クリエイティブは炉も結晶も要らない
    public static jp.revati.skyabyss.rpg.skill.SkillManager.RespecResult respecStats(ServerPlayer player) {
        return respecStats(player, false, true);
    }

    // atPlace = 炉のそばと同じに扱う (都の訓練場の師範)、crystals = 結晶で払う (false なら代わりの代価を呼ぶ側が取る)
    public static jp.revati.skyabyss.rpg.skill.SkillManager.RespecResult respecStats(ServerPlayer player, boolean atPlace, boolean crystals) {
        GrowthData data = get(player);
        if (data.spentPoints() <= 0) {
            return jp.revati.skyabyss.rpg.skill.SkillManager.RespecResult.NOTHING;
        }
        boolean free = player.isCreative();
        if (!free && !atPlace && !jp.revati.skyabyss.rpg.skill.SkillManager.nearForge(player)) {
            return jp.revati.skyabyss.rpg.skill.SkillManager.RespecResult.NO_FORGE;
        }
        int cost = GrowthRules.statRespecCost(data.statRespecs);
        if (!free && crystals && jp.revati.skyabyss.rpg.skill.SkillManager.countCrystals(player) < cost) {
            return jp.revati.skyabyss.rpg.skill.SkillManager.RespecResult.NO_CRYSTALS;
        }
        if (!free && crystals) {
            jp.revati.skyabyss.rpg.skill.SkillManager.removeCrystals(player, cost);
        }
        java.util.Arrays.fill(data.stats, 0);
        data.statRespecs++;
        sync(player);
        applyModifiers(player);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), Rpg.LEVEL_UP.get(),
                net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 0.7f);
        Notice.send(player, jp.revati.skyabyss.ModText.lore("message.skyabyss.growth.respec_done"));
        return jp.revati.skyabyss.rpg.skill.SkillManager.RespecResult.OK;
    }

    // 成長の修飾子を今の値で付け直す。ログイン・復活・ディメンション移動・振り分けのたびに呼ぶ。
    // 保存される修飾子にしているので、ログインしたとき体力がバニラの上限 20 に切り詰められない
    public static void applyModifiers(Player player) {
        GrowthData data = get(player);
        float healthBefore = player.getHealth();
        float maxBefore = player.getMaxHealth();
        set(player, Attributes.ATTACK_DAMAGE, MOD_STRENGTH, GrowthRules.meleeMultiplier(data.stat(Stat.STRENGTH)), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        set(player, Attributes.MOVEMENT_SPEED, MOD_AGILITY_MOVE, GrowthRules.moveMultiplier(data.stat(Stat.AGILITY)), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        set(player, Attributes.ATTACK_SPEED, MOD_AGILITY_ATTACK, GrowthRules.attackSpeedMultiplier(data.stat(Stat.AGILITY)), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        set(player, Rpg.MAX_MANA, MOD_SPIRIT, GrowthRules.bonusMana(data.stat(Stat.SPIRIT)), AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.MAX_HEALTH, MOD_VITALITY, GrowthRules.bonusHealth(data.stat(Stat.VITALITY)), AttributeModifier.Operation.ADD_VALUE);
        // 転生の魂盤・恩恵の修飾子 (docs/design/31)
        jp.revati.skyabyss.rebirth.Rebirth.applyModifiers(player);
        // 最大体力が増えた分は、そのぶん満たしておく (振った直後にハートが空のままにならないように)
        float maxAfter = player.getMaxHealth();
        if (maxAfter > maxBefore && player.isAlive() && healthBefore >= maxBefore - 0.01f) {
            player.setHealth(maxAfter);
        } else if (player.getHealth() > maxAfter) {
            player.setHealth(maxAfter);
        }
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
        if (current != null && current.amount() == value && current.operation() == op) {
            return;
        }
        instance.addOrReplacePermanentModifier(new AttributeModifier(id, value, op));
    }

    // ---------------------------------------------------------------- 一度だけの経験値
    // key をまだ記録していなければ記録して true (経験値を渡すのは呼ぶ側)
    public static boolean discover(Player player, String key) {
        return get(player).discoveries.add(key);
    }

    // 記録していなければ書かずに false (控え帳など、読むだけの所から)
    public static boolean discovered(Player player, String key) {
        return get(player).discoveries.contains(key);
    }

    // ---------------------------------------------------------------- 記憶の断片
    // 記憶の断片を付ける。器に入りきらない分は付けず、付けた数を返す。
    // 効果 (永続強化の枝) はまだない。入れ物と記録だけ (03 の 5.1 節、E9)
    public static int grantFragment(ServerPlayer player, ResourceLocation id, int count) {
        GrowthData data = get(player);
        int added = data.addFragments(id.toString(), count);
        if (added > 0) {
            sync(player);
            player.playNotifySound(Rpg.LEVEL_UP.get(), SoundSource.PLAYERS, 0.6f, 1.5f);
            Notice.narrate(player, "message.skyabyss.growth.fragment_gained",
                    Component.translatable(MemoryFragments.nameKey(id.toString())), data.fragmentCount(), data.fragmentCapacity);
        }
        return added;
    }

    public static void clearFragments(ServerPlayer player) {
        get(player).fragments.clear();
        sync(player);
    }
}
