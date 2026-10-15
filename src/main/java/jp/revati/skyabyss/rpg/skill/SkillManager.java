package jp.revati.skyabyss.rpg.skill;

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
            d.mana = (float) Math.min(max, d.mana + SkillRules.regenPerSecond(max, calm) / SkillRules.SECOND);
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
        if (skill.active && rank == 1) {
            for (int i = 0; i < d.slots.length; i++) {
                if (d.slots[i].isEmpty()) {
                    d.slots[i] = skill.id;
                    break;
                }
            }
        }
        sync(player);
        applyModifiers(player);
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.getX(), player.getY(), player.getZ(), Skills.SOUND_LEARN.get(), SoundSource.PLAYERS, 0.8f, 1f);
        level.sendParticles(Skills.RUNE_SPARK.get(), player.getX(), player.getY() + 1.0, player.getZ(), 18, 0.4, 0.6, 0.4, 0.02);
        player.displayClientMessage(ModText.progress("message.skyabyss.skill.learned", Component.translatable(skill.nameKey()), rank, skill.maxRank), true);
        return r;
    }

    // ---------------------------------------------------------------- 置き場
    // 枠に技を置く (skill が null なら空ける)。同じ技が別の枠にあれば、そちらは空ける
    public static boolean setSlot(ServerPlayer player, int slot, Skill skill) {
        SkillData d = get(player);
        if (slot < 0 || slot >= d.slots.length) {
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

    // 共鳴の炉のそばで、共鳴の結晶を払って振り直す (初回は無料)。クリエイティブは炉も結晶も要らない
    public static RespecResult respec(ServerPlayer player) {
        SkillData d = get(player);
        if (d.ranks.isEmpty()) {
            return RespecResult.NOTHING;
        }
        boolean free = player.isCreative();
        if (!free && !nearForge(player)) {
            return RespecResult.NO_FORGE;
        }
        int cost = SkillRules.respecCost(d.respecs);
        if (!free && countCrystals(player.getInventory()) < cost) {
            return RespecResult.NO_CRYSTALS;
        }
        if (!free) {
            removeCrystals(player.getInventory(), cost);
        }
        d.clearSkills();
        d.respecs++;
        sync(player);
        applyModifiers(player);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), Rpg.LEVEL_UP.get(), SoundSource.PLAYERS, 0.6f, 0.7f);
        player.displayClientMessage(ModText.lore("message.skyabyss.skill.respec_done"), false);
        return RespecResult.OK;
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

    private static void removeCrystals(Inventory inv, int count) {
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
            player.displayClientMessage(ModText.deny("message.skyabyss.skill.no_mana"), true);
            player.playNotifySound(Skills.SOUND_FAIL.get(), SoundSource.PLAYERS, 0.6f, 1f);
            sendResult(player, slot, false);
            return CastResult.NO_MANA;
        }
        String failure = SkillActions.perform(player, skill, rank);
        if (failure != null) {
            player.displayClientMessage(ModText.deny(failure), true);
            player.playNotifySound(Skills.SOUND_FAIL.get(), SoundSource.PLAYERS, 0.6f, 1f);
            sendResult(player, slot, false);
            return CastResult.FAILED;
        }
        d.mana = Math.max(0, d.mana - cost);
        d.cooldowns.put(skill.id, SkillRules.cooldownTicks(skill, rank));
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

    // ---------------------------------------------------------------- 常時の技の修飾子 (岩の構え)
    // ログイン・復活・ディメンション移動・習得・振り直しのたびに今の値で付け直す (保存される修飾子)
    public static void applyModifiers(Player player) {
        int stance = get(player).rank(Skill.STONE_STANCE);
        set(player, Attributes.ARMOR, MOD_STANCE_ARMOR, SkillRules.stanceArmor(stance));
        set(player, Attributes.KNOCKBACK_RESISTANCE, MOD_STANCE_KNOCKBACK, SkillRules.stanceKnockback(stance));
    }

    private static void set(Player player, Holder<Attribute> attribute, ResourceLocation id, double value) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        if (value == 0) {
            instance.removeModifier(id);
            return;
        }
        AttributeModifier current = instance.getModifier(id);
        if (current != null && current.amount() == value) {
            return;
        }
        instance.addOrReplacePermanentModifier(new AttributeModifier(id, value, AttributeModifier.Operation.ADD_VALUE));
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

    // 管理者・テスト用: 段を直接決める (前提とレベルは検めない。0 で忘れる)。
    // 点が足りなければ追加の点を足して数を合わせる (ログインのときの検めで消されないように)
    public static void forceRank(ServerPlayer player, Skill skill, int rank) {
        SkillData d = get(player);
        if (rank <= 0) {
            d.ranks.remove(skill.id);
        } else {
            d.ranks.put(skill.id, Math.min(rank, skill.maxRank));
            fillRequirements(d, skill);
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
