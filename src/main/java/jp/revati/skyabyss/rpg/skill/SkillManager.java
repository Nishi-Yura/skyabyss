package jp.revati.skyabyss.rpg.skill;

import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.Rpg;
import jp.revati.skyabyss.rpg.skill.net.ManaPayload;
import jp.revati.skyabyss.rpg.skill.net.SkillCastResultPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;

// 技の記録を読み書きする入口 (サーバーが決める)。クライアントからの要求 (習得・置き場・発動・振り直し) もここで検める。
// 書き換えたら sync で持ち主へ送る
public final class SkillManager {
    public static final ResourceLocation MOD_STANCE_ARMOR = id("skill/stone_stance_armor");
    public static final ResourceLocation MOD_STANCE_KNOCKBACK = id("skill/stone_stance_knockback");
    public static final ResourceLocation MOD_NIMBLE_MOVE = id("skill/nimble_move");
    public static final ResourceLocation MOD_NIMBLE_FALL = id("skill/nimble_fall");
    public static final ResourceLocation MOD_LONE_ATTACK = id("skill/lone_wind_attack");
    public static final ResourceLocation MOD_LONE_MOVE = id("skill/lone_wind_move");
    public static final ResourceLocation MOD_READY_MANA = id("skill/readiness_mana");
    public static final ResourceLocation MOD_READY_ATTACK = id("skill/readiness_attack");
    public static final ResourceLocation MOD_BP_KNOCKBACK = id("skill/bp_vitality_knockback");
    public static final ResourceLocation MOD_TRAIT_TOUGH = id("skill/trait_rock_toughness");
    public static final ResourceLocation MOD_TRAIT_KNOCKBACK = id("skill/trait_rock_knockback");
    public static final ResourceLocation MOD_TRAIT_MOVE = id("skill/trait_wind_move");
    public static final ResourceLocation MOD_TRAIT_MANA = id("skill/trait_tide_mana");
    public static final ResourceLocation MOD_BP_SWING = id("skill/bp_strength_swing");

    private SkillManager() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, path);
    }

    public static SkillData get(Player player) {
        return player.getData(Skills.DATA);
    }

    public static int level(Player player) {
        return Growth.get(player).level;
    }

    public static void sync(Player player) {
        if (player instanceof ServerPlayer sp && !(player instanceof FakePlayer) && sp.connection != null) {
            player.syncData(Skills.DATA);
            get(player).sentMana = get(player).mana;
        }
    }

    // ---------------------------------------------------------------- マナ
    public static double maxMana(Player player) {
        AttributeInstance a = player.getAttribute(Rpg.MAX_MANA);
        return a == null ? 0 : a.getValue();
    }

    // はじめてのとき (まだ決めていない) は満タンにする。最大が下がっていたら収める
    public static void settleMana(Player player) {
        SkillData d = get(player);
        double max = maxMana(player);
        if (d.mana < 0) {
            d.mana = (float) max;
        } else if (d.mana > max) {
            d.mana = (float) max;
        }
    }

    public static void setMana(ServerPlayer player, double value) {
        SkillData d = get(player);
        d.mana = (float) Math.max(0, Math.min(value, maxMana(player)));
        sendMana(player, true);
    }

    // 変わった分が 1 以上か、満ちたとき、空になったときだけ送る (毎 tick は送らない)
    public static void sendMana(ServerPlayer player, boolean force) {
        SkillData d = get(player);
        double max = maxMana(player);
        boolean edge = (d.mana >= max - 1e-3 || d.mana <= 1e-3) && d.sentMana != d.mana;
        if (force || edge || Math.abs(d.mana - d.sentMana) >= 1f) {
            d.sentMana = d.mana;
            if (!(player instanceof FakePlayer) && player.connection != null) {
                PacketDistributor.sendToPlayer(player, new ManaPayload(d.mana));
            }
        }
    }

    // マナを 1 tick ぶん満たす。calm = 5 秒のあいだ戦っていない
    public static void regenTick(Player player, SkillData d, long gameTime) {
        double max = maxMana(player);
        if (d.mana < 0) {
            d.mana = (float) max;
            return;
        }
        if (d.mana > max) {
            d.mana = (float) max;
            return;
        }
        if (d.mana < max) {
            boolean calm = gameTime - d.lastCombat >= SkillRules.CALM_AFTER_TICKS;
            double scale = d.regenScale + (gameTime < d.regenBoostUntil ? d.regenBoost : 0);
            d.mana = (float) Math.min(max, d.mana + SkillRules.regenPerSecond(max, calm) * scale / SkillRules.SECOND);
        }
    }

    // ---------------------------------------------------------------- 習得
    public static SkillData.LearnResult learn(ServerPlayer player, Skill skill) {
        SkillData d = get(player);
        SkillData.LearnResult r = d.canLearn(skill, level(player));
        if (r != SkillData.LearnResult.OK) {
            return r;
        }
        int rank = d.rank(skill) + 1;
        d.ranks.put(skill.id, rank);
        // 能動の技を初めて覚えたら、空いている枠に置いておく (画面を開かなくても使える)
        if (rank == 1) {
            autoSlot(d, skill);
        }
        jp.revati.skyabyss.advancement.Beats.fire(player, "skill:learned");
        jp.revati.skyabyss.advancement.Beats.fire(player, "skill:tree:" + skill.tree.beat);
        jp.revati.skyabyss.advancement.Beats.fire(player, "skill:tree:" + skill.tree.id);
        sync(player);
        applyModifiers(player);
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.getX(), player.getY(), player.getZ(), Skills.SOUND_LEARN.get(), SoundSource.PLAYERS, 0.8f, 1f);
        level.sendParticles(Skills.RUNE_SPARK.get(), player.getX(), player.getY() + 1.0, player.getZ(), 18, 0.4, 0.6, 0.4, 0.02);
        Notice.send(player, Notice.Kind.LEVEL_UP, ModText.progress("message.skyabyss.skill.learned", Component.translatable(skill.nameKey()), rank, skill.maxRank));
        return r;
    }

    // 能動の技がどの枠にもなければ、最初の空いた枠に置く
    static void autoSlot(SkillData d, Skill skill) {
        if (!skill.active || !d.knows(skill)) {
            return;
        }
        for (String id : d.slots) {
            if (skill.id.equals(id)) {
                return;
            }
        }
        for (int i = 0; i < d.slotCount(); i++) {
            if (d.slots[i].isEmpty()) {
                d.slots[i] = skill.id;
                return;
            }
        }
    }

    // ---------------------------------------------------------------- 置き場
    // 枠に技を置く (skill が null なら空ける)。同じ技が別の枠にあれば、そちらは空ける
    public static boolean setSlot(ServerPlayer player, int slot, Skill skill) {
        SkillData d = get(player);
        if (slot < 0 || slot >= d.slotCount()) {
            return false;
        }
        if (skill == null) {
            d.slots[slot] = "";
            sync(player);
            return true;
        }
        if (!skill.active || !d.knows(skill)) {
            return false;
        }
        for (int i = 0; i < d.slots.length; i++) {
            if (skill.id.equals(d.slots[i])) {
                d.slots[i] = "";
            }
        }
        d.slots[slot] = skill.id;
        sync(player);
        return true;
    }

    // ---------------------------------------------------------------- 振り直し
    public enum RespecResult {
        OK, NOTHING, NO_FORGE, NO_CRYSTALS
    }

    // いまの振り直しの費用 (結晶の数)。Lv 15 になるまでは 0、目利きがあれば 2 以上のとき 1 減る
    public static int respecCostFor(Player player) {
        SkillData d = get(player);
        return SkillRules.respecCost(d.respecs, level(player), d.knows(Skill.APPRAISER));
    }

    // Lv 15 になるまでは、どこでも無料 (炉も結晶も要らない。回数も数えない)
    public static boolean freeRespec(Player player) {
        return level(player) < SkillRules.FREE_RESPEC_LEVEL;
    }

    // 共鳴の炉のそばで、共鳴の結晶を払って振り直す (初回は無料)。クリエイティブは炉も結晶も要らない。
    // Lv 15 になるまでは、どこでも無料
    public static RespecResult respec(ServerPlayer player) {
        SkillData d = get(player);
        if (d.ranks.isEmpty()) {
            return RespecResult.NOTHING;
        }
        boolean early = freeRespec(player);
        boolean free = player.isCreative() || early;
        if (!free && !nearForge(player)) {
            return RespecResult.NO_FORGE;
        }
        int cost = respecCostFor(player);
        if (!free && countCrystals(player) < cost) {
            return RespecResult.NO_CRYSTALS;
        }
        if (!free) {
            removeCrystals(player, cost);
        }
        d.clearSkills();
        if (!early) {
            d.respecs++;
        }
        sync(player);
        applyModifiers(player);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), Rpg.LEVEL_UP.get(), SoundSource.PLAYERS, 0.6f, 0.7f);
        Notice.send(player, ModText.lore("message.skyabyss.skill.respec_done"));
        return RespecResult.OK;
    }

    // ---------------------------------------------------------------- 刻印・型の保存
    // 刻印の効き目の番号 (選んでいない・まだ開かない・極めていない技は 0)
    public static int runeOf(Player player, Skill skill) {
        SkillData d = get(player);
        int r = d.rune(skill);
        if (r <= 0 || !d.runeAvailable(skill)) {
            return 0;
        }
        // 伝説の書き換えは、その武器を持つあいだだけ
        Rune rune = Rune.of(skill, r);
        return rune != null && rune.held(player) ? r : 0;
    }

    public enum RuneResult {
        OK, NOT_AVAILABLE, SAME, NO_POINTS, NOT_QUIET, NO_PLACE, NO_CRYSTALS
    }

    // 戦いの外 (30 秒、傷を受けも与えもしていない) か。クリエイティブは問わない
    public static boolean quiet(Player player) {
        return player.isCreative() || player.level().getGameTime() - get(player).lastCombat >= SkillRules.QUIET_TICKS;
    }

    // 刻印・型を切り替えられる場所か (炉・焚き火の近く。旅の手帖があればどこでも)
    public static boolean switchPlace(Player player) {
        return player.isCreative() || get(player).knows(Skill.JOURNAL) || nearForge(player) || SkillPassives.campNear(player);
    }

    // 刻印を選ぶ。はじめて選ぶのは 1 点、別の刻印へ付け替えるのは炉などで結晶 1 (初回は無料)
    public static RuneResult chooseRune(ServerPlayer player, Skill skill, int index) {
        SkillData d = get(player);
        if (skill == null || Rune.of(skill, index) == null || !d.runeAvailable(skill) || !Rune.of(skill, index).held(player)) {
            return RuneResult.NOT_AVAILABLE;
        }
        int current = d.rune(skill);
        if (current == index) {
            return RuneResult.SAME;
        }
        if (current == 0) {
            if (d.unspentPoints(level(player)) < 1) {
                return RuneResult.NO_POINTS;
            }
            d.runes.put(skill.id, index);
        } else {
            if (!quiet(player)) {
                return RuneResult.NOT_QUIET;
            }
            if (!switchPlace(player)) {
                return RuneResult.NO_PLACE;
            }
            boolean free = player.isCreative() || d.runeSwaps == 0;
            if (!free && countCrystals(player) < SkillRules.RUNE_SWAP_COST) {
                return RuneResult.NO_CRYSTALS;
            }
            if (!free) {
                removeCrystals(player, SkillRules.RUNE_SWAP_COST);
            }
            d.runeSwaps++;
            d.runes.put(skill.id, index);
        }
        sync(player);
        applyModifiers(player);
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.getX(), player.getY(), player.getZ(), Skills.SOUND_LEARN.get(), SoundSource.PLAYERS, 0.7f, 1.4f);
        level.sendParticles(Skills.RUNE_SPARK.get(), player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.4, 0.6, 0.4, 0.02);
        return RuneResult.OK;
    }

    // 型の保存: 今の枠と刻印を保存する (いつでも)
    public static boolean saveLoadout(ServerPlayer player, int index) {
        SkillData d = get(player);
        if (index < 0 || index >= d.loadouts.length) {
            return false;
        }
        SkillData.Loadout l = d.loadouts[index];
        System.arraycopy(d.slots, 0, l.slots, 0, l.slots.length);
        l.runes.clear();
        for (var e : d.runes.entrySet()) {
            l.runes.put(e.getKey(), e.getValue());
        }
        l.saved = true;
        sync(player);
        return true;
    }

    public enum LoadoutResult {
        OK, EMPTY, NOT_QUIET, NO_PLACE
    }

    // 型を切り替える (点は変わらない)。枠は覚えている使う技だけを置き、刻印はすでに 1 つ選んである技だけ付け替える
    public static LoadoutResult loadLoadout(ServerPlayer player, int index) {
        SkillData d = get(player);
        if (index < 0 || index >= d.loadouts.length || !d.loadouts[index].saved) {
            return LoadoutResult.EMPTY;
        }
        if (!quiet(player)) {
            return LoadoutResult.NOT_QUIET;
        }
        if (!switchPlace(player)) {
            return LoadoutResult.NO_PLACE;
        }
        SkillData.Loadout l = d.loadouts[index];
        java.util.Arrays.fill(d.slots, "");
        for (int i = 0; i < Math.min(d.slotCount(), l.slots.length); i++) {
            Skill s = Skill.byId(l.slots[i]);
            if (s != null && s.active && d.knows(s) && !java.util.Arrays.asList(d.slots).contains(s.id)) {
                d.slots[i] = s.id;
            }
        }
        for (var e : l.runes.entrySet()) {
            Skill s = Skill.byId(e.getKey());
            if (s != null && d.rune(s) > 0 && d.runeAvailable(s) && Rune.of(s, e.getValue()) != null) {
                d.runes.put(s.id, e.getValue());
            }
        }
        sync(player);
        applyModifiers(player);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), Skills.SOUND_LEARN.get(), SoundSource.PLAYERS, 0.6f, 1.8f);
        return LoadoutResult.OK;
    }

    // 技の待ち (tick): 刻印・敏捷 30 の刻み目・手の武器の冴えを入れた長さ。クライアントの枠の表示も同じ計算
    public static int cooldownFor(Player player, Skill skill) {
        SkillData d = get(player);
        int rank = d.rank(skill);
        int ticks = SkillRules.cooldownTicks(skill, rank, runeOf(player, skill));
        if ((skill == Skill.LUNGE || skill == Skill.BACKSTEP || skill == Skill.WIND_REND)
                && Breakpoints.reached(player, jp.revati.skyabyss.rpg.GrowthRules.Stat.AGILITY, Breakpoints.SECOND)) {
            ticks = (int) Math.round(ticks * (1 - Breakpoints.AGILITY_30_COOLDOWN));
        }
        return jp.revati.skyabyss.rebirth.Rebirth.scaleCooldown(player, jp.revati.skyabyss.item.affix.AffixEvents.scaleCooldown(player, ticks));
    }

    public static boolean nearForge(Player player) {
        BlockPos center = player.blockPosition();
        int r = SkillRules.RESPEC_FORGE_RANGE;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
            if (player.level().getBlockState(p).is(ModBlocks.RESONANCE_FORGE.get())) {
                return true;
            }
        }
        return false;
    }

    // 持ち物 + 留め袋の共鳴の結晶の数 (振り直しの費用の足りるか。docs/design/22 の 8.3)
    public static int countCrystals(net.minecraft.world.entity.player.Player player) {
        return jp.revati.skyabyss.pouch.Pouch.countAll(player, ModItems.RESONANCE_CRYSTAL.get());
    }

    // 袋から先に、足りない分を持ち物から払う
    public static void removeCrystals(net.minecraft.world.entity.player.Player player, int count) {
        jp.revati.skyabyss.pouch.Pouch.takeAny(player, ModItems.RESONANCE_CRYSTAL.get(), count);
    }

    // 持ち物だけの数 (留め袋は含まない)
    public static int countCrystals(Inventory inv) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(ModItems.RESONANCE_CRYSTAL.get())) {
                n += s.getCount();
            }
        }
        return n;
    }

    public static void removeCrystals(Inventory inv, int count) {
        for (int i = 0; i < inv.getContainerSize() && count > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(ModItems.RESONANCE_CRYSTAL.get())) {
                int take = Math.min(count, s.getCount());
                s.shrink(take);
                count -= take;
            }
        }
        inv.setChanged();
    }

    // ---------------------------------------------------------------- 発動
    public enum CastResult {
        OK, EMPTY, NOT_LEARNED, COOLDOWN, NO_MANA, FAILED
    }

    // 枠の技を使う。正しければマナを払い、クールダウンを始め、同期する
    public static CastResult cast(ServerPlayer player, int slot) {
        if (!player.isAlive() || player.isSpectator()) {
            return CastResult.FAILED;
        }
        SkillData d = get(player);
        Skill skill = d.slot(slot);
        if (skill == null) {
            return CastResult.EMPTY;
        }
        int rank = d.rank(skill);
        if (rank <= 0 || !skill.active) {
            return CastResult.NOT_LEARNED;
        }
        if (d.globalCooldown > 0 || d.cooldown(skill) > 0) {
            return CastResult.COOLDOWN;
        }
        settleMana(player);
        int cost = SkillRules.manaCost(skill, rank);
        if (d.mana + 1e-4 < cost) {
            Notice.bar(player, ModText.deny("message.skyabyss.skill.no_mana"));
            player.playNotifySound(Skills.SOUND_FAIL.get(), SoundSource.PLAYERS, 0.6f, 1f);
            sendResult(player, slot, false);
            return CastResult.NO_MANA;
        }
        SkillActions.cooldownScale = 1;
        String failure = SkillActions.perform(player, skill, rank);
        if (failure != null) {
            Notice.bar(player, ModText.deny(failure));
            player.playNotifySound(Skills.SOUND_FAIL.get(), SoundSource.PLAYERS, 0.6f, 1f);
            sendResult(player, slot, false);
            return CastResult.FAILED;
        }
        d.mana = Math.max(0, d.mana - cost);
        // 刻印・敏捷の刻み目・手の武器の特性「冴え」で待ちが変わる (03 の 8章)。連ねは当たると半分
        d.cooldowns.put(skill.id, Math.max(1, (int) Math.round(cooldownFor(player, skill) * SkillActions.cooldownScale)));
        SkillActions.cooldownScale = 1;
        // 炉の記憶: 技を使うたびに熾が 1 つ溜まる (次の近接で使い切る)
        int memory = d.rank(Skill.FORGE_MEMORY);
        if (memory > 0) {
            d.embers = Math.min(memory, d.embers + 1);
        }
        d.globalCooldown = SkillRules.GLOBAL_COOLDOWN;
        d.lastCombat = player.level().getGameTime();
        sync(player);
        sendResult(player, slot, true);
        return CastResult.OK;
    }

    private static void sendResult(ServerPlayer player, int slot, boolean ok) {
        if (!(player instanceof FakePlayer) && player.connection != null) {
            PacketDistributor.sendToPlayer(player, new SkillCastResultPayload(slot, ok));
        }
    }

    // ---------------------------------------------------------------- 常時の技・刻み目の修飾子
    // ログイン・復活・ディメンション移動・習得・振り直しのたび、そして 0.5 秒ごとに今の値で付け直す (保存される修飾子。値が同じなら何もしない)。
    // 岩の構え・軽業・孤風・備えの報い・耐久 15・筋力 15 の刻み目
    public static void applyModifiers(Player player) {
        SkillData d = get(player);
        int stance = d.rank(Skill.STONE_STANCE);
        double guard = stance > 0 && WeaponType.has(player, WeaponType.GUARD) ? 1 : 0;
        add(player, Attributes.ARMOR, MOD_STANCE_ARMOR, SkillRules.stanceArmor(stance) + guard);
        double vit15 = Breakpoints.reached(player, jp.revati.skyabyss.rpg.GrowthRules.Stat.VITALITY, Breakpoints.FIRST) ? Breakpoints.VITALITY_15_KNOCKBACK : 0;
        add(player, Attributes.KNOCKBACK_RESISTANCE, MOD_STANCE_KNOCKBACK, SkillRules.stanceKnockback(stance));
        add(player, Attributes.KNOCKBACK_RESISTANCE, MOD_BP_KNOCKBACK, vit15);
        int nimble = d.rank(Skill.NIMBLE);
        double nimbleMove = SkillRules.nimbleMove(nimble) + (nimble > 0 && WeaponType.has(player, WeaponType.FAST) ? SkillRules.NIMBLE_FAST_BONUS : 0);
        set(player, Attributes.MOVEMENT_SPEED, MOD_NIMBLE_MOVE, nimbleMove, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        add(player, Attributes.SAFE_FALL_DISTANCE, MOD_NIMBLE_FALL, SkillRules.nimbleFall(nimble));
        boolean lone = d.knows(Skill.LONE_WIND) && player instanceof ServerPlayer sp && SkillEffects.allies(sp, SkillRules.LONE_RANGE).isEmpty();
        set(player, Attributes.ATTACK_DAMAGE, MOD_LONE_ATTACK, lone ? SkillRules.LONE_ATTACK : 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        set(player, Attributes.MOVEMENT_SPEED, MOD_LONE_MOVE, lone ? SkillRules.LONE_MOVE : 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        int ready = d.readyBlue ? d.rank(Skill.READINESS_REWARD) : 0;
        add(player, Rpg.MAX_MANA, MOD_READY_MANA, SkillRules.readinessMana(ready));
        set(player, Attributes.ATTACK_DAMAGE, MOD_READY_ATTACK, SkillRules.readinessAttack(ready), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        // 天性 (一つ目の天職だけ)
        SkillTree calling = d.callingTree();
        add(player, Attributes.ARMOR_TOUGHNESS, MOD_TRAIT_TOUGH, calling == SkillTree.ROCK ? SkillRules.TRAIT_ROCK_TOUGHNESS : 0);
        add(player, Attributes.KNOCKBACK_RESISTANCE, MOD_TRAIT_KNOCKBACK, calling == SkillTree.ROCK ? SkillRules.TRAIT_ROCK_KNOCKBACK : 0);
        set(player, Attributes.MOVEMENT_SPEED, MOD_TRAIT_MOVE, calling == SkillTree.WIND ? SkillRules.TRAIT_WIND_MOVE : 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        add(player, Rpg.MAX_MANA, MOD_TRAIT_MANA, calling == SkillTree.TIDE ? SkillRules.TRAIT_TIDE_MANA : 0);
        boolean heavy = WeaponType.isHeld(player.getMainHandItem(), WeaponType.HEAVY)
                && Breakpoints.reached(player, jp.revati.skyabyss.rpg.GrowthRules.Stat.STRENGTH, Breakpoints.FIRST);
        set(player, Attributes.ATTACK_SPEED, MOD_BP_SWING, heavy ? Breakpoints.STRENGTH_15_SWING : 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    private static void add(Player player, Holder<Attribute> attribute, ResourceLocation id, double value) {
        set(player, attribute, id, value, AttributeModifier.Operation.ADD_VALUE);
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

    // 前提をどれも覚えていなければ、最初の前提を段 1 で覚えさせる (その前提の前提も)
    static void fillRequirements(SkillData d, Skill skill) {
        if (d.requirementsMet(skill)) {
            return;
        }
        Skill first = skill.requires().get(0);
        d.ranks.put(first.id, 1);
        fillRequirements(d, first);
    }

    // 管理者・テスト用: 技をすべて忘れる (振り直しの回数は数えない)
    public static void forgetAll(ServerPlayer player) {
        get(player).clearSkills();
        sync(player);
        applyModifiers(player);
    }

    // 転生 (docs/design/31): 覚えた技・刻印・天職・振り直しの回数を戻す。型の保存・解いた木・追加の点は残す。
    // 呼ぶ前にレベルを 1 に戻しておく (知らせ済みの点の数をそのレベルに合わせるため)
    public static void resetForRebirth(ServerPlayer player) {
        SkillData d = get(player);
        d.clearSkills();
        d.calling = "";
        d.calling2 = "";
        d.respecs = 0;
        d.runeSwaps = 0;
        d.cooldowns.clear();
        d.globalCooldown = 0;
        d.sanitize(level(player));
        d.notifiedPoints = d.totalPoints(level(player));
        sync(player);
        applyModifiers(player);
        setMana(player, maxMana(player));
    }

    // 管理者・テスト用: 段を直接決める (前提とレベルは検めない。0 で忘れる)。
    // 点が足りなければ追加の点を足して数を合わせる (ログインのときの検めで消されないように)
    public static void forceRank(ServerPlayer player, Skill skill, int rank) {
        SkillData d = get(player);
        java.util.Set<String> before = new java.util.HashSet<>(d.ranks.keySet());
        if (rank <= 0) {
            d.ranks.remove(skill.id);
        } else {
            d.ranks.put(skill.id, Math.min(rank, skill.maxRank));
            fillRequirements(d, skill);
            // 覚えたての能動の技 (埋めた前提も) は、画面で覚えたときと同じく空いた枠へ
            for (Skill s : Skill.values()) {
                if (!before.contains(s.id) && d.knows(s)) {
                    autoSlot(d, s);
                }
            }
        }
        int over = d.spentPoints() - d.totalPoints(level(player));
        if (over > 0) {
            d.bonusPoints += over;
        }
        for (int i = 0; i < d.slots.length; i++) {
            Skill s = d.slot(i);
            if (s != null && !d.knows(s)) {
                d.slots[i] = "";
            }
        }
        sync(player);
        applyModifiers(player);
    }
}
