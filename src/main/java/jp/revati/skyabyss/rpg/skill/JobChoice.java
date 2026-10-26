package jp.revati.skyabyss.rpg.skill;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.rpg.skill.net.JobChoiceOpenPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// 島の祠で職を選ぶ (docs/design/43 の 4 節)。祠のそばに来ると選ぶ画面が開き、選ぶとその職の始めの武器を受け取って、
// 職の木の 1 行目の技を一つ覚える (Lv 1 の 1 点)。選んだ職は人の永続データの KEY (倒れても残る。util/LifeKeep)。
// 始めの武器はどれも祠の品 (祠の剣・祠の鎚・宿り木の杖・祠の弓・宿り木の短剣・祠の鈴杖)
public final class JobChoice {
    public static final String KEY = "skyabyss_first_job";
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);

    // 杖: 攻撃力 5、速さ 1.2 / 鈴杖: 攻撃力 4、速さ 1.4 / 短剣: 攻撃力 4、速さ 2.0 (攻撃力 = 1 + 2 + 品ごとの値)
    public static final DeferredItem<Item> STAFF = ITEMS.register("shrine_staff",
            () -> new StarterWeaponItem(new Item.Properties(), 2, 1.2f, "item.skyabyss.shrine_staff.desc"));
    public static final DeferredItem<Item> BELL_STAFF = ITEMS.register("shrine_bell_staff",
            () -> new StarterWeaponItem(new Item.Properties(), 1, 1.4f, "item.skyabyss.shrine_bell_staff.desc"));
    public static final DeferredItem<Item> DAGGER = ITEMS.register("shrine_dagger",
            () -> new StarterWeaponItem(new Item.Properties(), 1, 2.0f, "item.skyabyss.shrine_dagger.desc"));
    // 剣: 攻撃力 5、速さ 1.6 / 鎚: 攻撃力 8、速さ 1.0 (どちらも 1 秒あたり 8。一章のエピックの武器を上回らない) / 弓: 矢の傷はふつうの弓と同じ
    public static final DeferredItem<Item> SWORD = ITEMS.register("shrine_sword",
            () -> new StarterWeaponItem(new Item.Properties(), 2, 1.6f, "item.skyabyss.shrine_sword.desc"));
    public static final DeferredItem<Item> HAMMER = ITEMS.register("shrine_hammer",
            () -> new StarterWeaponItem(new Item.Properties(), 5, 1.0f, "item.skyabyss.shrine_hammer.desc"));
    public static final DeferredItem<Item> BOW = ITEMS.register("shrine_bow",
            () -> new jp.revati.skyabyss.item.ChapterBowItem(new Item.Properties().durability(384), "stone_whale", 1.0, jp.revati.skyabyss.item.GearTier.COMMON,
                    "item.skyabyss.shrine_bow.desc"));

    // 祠のそばで画面を一度開いた人 (離れると消える。また近づくともう一度開く)
    private static final Set<UUID> PROMPTED = new HashSet<>();

    private JobChoice() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        modEventBus.addListener(JobChoice::onCreativeTab);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(JobChoice::onCommands);
    }

    // 開発用: /skyabyss job open (選ぶ画面) | choose <職> | reset (選んだ職を消す)
    private static void onCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        var job = net.minecraft.commands.Commands.literal("job")
                .then(net.minecraft.commands.Commands.literal("open").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    PROMPTED.remove(p.getUUID());
                    prompt(p);
                    return 1;
                }))
                .then(net.minecraft.commands.Commands.literal("reset").executes(c -> {
                    reset(c.getSource().getPlayerOrException());
                    return 1;
                }));
        var choose = net.minecraft.commands.Commands.literal("choose");
        for (Job j : Job.values()) {
            choose.then(net.minecraft.commands.Commands.literal(j.id).executes(c -> choose(c.getSource().getPlayerOrException(), j) ? 1 : 0));
        }
        event.getDispatcher().register(net.minecraft.commands.Commands.literal(SkyAbyss.MODID).requires(src -> src.hasPermission(2)).then(job.then(choose)));
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModItems.TAB.getKey())) {
            for (DeferredItem<Item> item : List.of(SWORD, HAMMER, STAFF, BOW, DAGGER, BELL_STAFF)) {
                event.accept(item.get());
            }
        }
    }

    public static ItemStack starter(Job job) {
        return switch (job) {
            case WARRIOR -> new ItemStack(SWORD.get());
            case SMITH -> new ItemStack(HAMMER.get());
            case MAGE -> new ItemStack(STAFF.get());
            case ARCHER -> new ItemStack(BOW.get());
            case ASSASSIN -> new ItemStack(DAGGER.get());
            case SHAMAN -> new ItemStack(BELL_STAFF.get());
        };
    }

    // 職の木の 1 行目の使う技 (列の順で最初)。まだ技のない木は null
    public static Skill firstSkill(Job job) {
        Skill best = null;
        for (Skill s : Skill.inTree(job.tree)) {
            if (s.active && !s.keystone() && (best == null || s.tier < best.tier || s.tier == best.tier && s.column < best.column)) {
                best = s;
            }
        }
        return best;
    }

    public static Job chosen(ServerPlayer p) {
        return Job.byId(p.getPersistentData().getString(KEY));
    }

    // 選ぶ画面を開く (祠のそばに来たとき。離れるまでは一度だけ)
    public static void prompt(ServerPlayer p) {
        if (!(p instanceof net.neoforged.neoforge.common.util.FakePlayer) && PROMPTED.add(p.getUUID())) {
            PacketDistributor.sendToPlayer(p, new JobChoiceOpenPayload());
        }
    }

    public static void away(ServerPlayer p) {
        PROMPTED.remove(p.getUUID());
    }

    // 職を選ぶ。すでに選んでいれば false
    public static boolean choose(ServerPlayer p, Job job) {
        if (job == null || chosen(p) != null) {
            return false;
        }
        p.getPersistentData().putString(KEY, job.id);
        ItemStack weapon = starter(job);
        if (p.getMainHandItem().isEmpty()) {
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, weapon);
        } else {
            jp.revati.skyabyss.quest.Quests.give(p, weapon);
        }
        if (job == Job.ARCHER) {
            jp.revati.skyabyss.quest.Quests.give(p, new ItemStack(Items.ARROW, 32));
        }
        Skill first = firstSkill(job);
        if (first != null && !SkillManager.get(p).knows(first)) {
            SkillManager.forceRank(p, first, 1);
        }
        SkillManager.sync(p);
        Notice.objective(p, "message.skyabyss.job.chosen", Component.translatable(job.nameKey()), weapon.getHoverName());
        return true;
    }

    // 訓練場の師範が、ほかの職の始めの武器を貸す (一つの職につき一度、ただ。43 の 4 節)。祠で選んだ職の分は、もう受け取ったものとして数える
    public static final String LENT = "skyabyss_starter_lent";

    public static boolean lend(ServerPlayer p, Job job) {
        if (job == null) {
            return false;
        }
        net.minecraft.nbt.CompoundTag tag = p.getPersistentData().getCompound(LENT);
        if (tag.getBoolean(job.id) || job == chosen(p)) {
            Notice.hint(p, "message.skyabyss.job.lent_already", Component.translatable(job.nameKey()));
            return false;
        }
        tag.putBoolean(job.id, true);
        p.getPersistentData().put(LENT, tag);
        ItemStack weapon = starter(job);
        jp.revati.skyabyss.quest.Quests.give(p, weapon.copy());
        if (job == Job.ARCHER) {
            jp.revati.skyabyss.quest.Quests.give(p, new ItemStack(Items.ARROW, 32));
        }
        Notice.objective(p, "message.skyabyss.job.lent", Component.translatable(job.nameKey()), weapon.getHoverName());
        return true;
    }

    // 開発・自己テスト用: 選んだ職を消す (武器と技はそのまま)
    public static void reset(ServerPlayer p) {
        p.getPersistentData().remove(KEY);
        p.getPersistentData().remove(LENT);
        PROMPTED.remove(p.getUUID());
    }
}
