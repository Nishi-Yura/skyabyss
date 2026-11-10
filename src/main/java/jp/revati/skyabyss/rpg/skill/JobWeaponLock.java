package jp.revati.skyabyss.rpg.skill;

import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.ModText;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

// ほかの職の武器は使えない (docs/design/48 の 3 章。2026-11-05 の声「職業がちがうのに、違う職の武器使えてしまう。弓とか」)。
//   近接: 手の武器が今の職のものでなければ、敵を打てない (前は攻撃力が半分だった)
//   弓・弩: 今の職が弓使いでなければ引けない (前は何も止めていなかった)
// 職の品でない物 (素手・採集の道具 gather_*・素材) は止めない。サーバーと手元の両方で止める (手元で止めると振りの空打ちも出ない)。
// 帯の知らせはサーバーから、2 秒に一度まで
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class JobWeaponLock {
    private static final long TELL_EVERY = 40;
    private static final Map<UUID, Long> TOLD = jp.revati.skyabyss.util.PlayerMaps.track(new java.util.concurrent.ConcurrentHashMap<>());

    private JobWeaponLock() {
    }

    // 持った物 stack が、player の今の職では使えない職の武器なら、その武器の職。使えれば null
    @Nullable
    public static Job wrongJob(Player player, ItemStack stack) {
        if (SkillManager.IGNORE_JOBS || player instanceof FakePlayer || player.isCreative() || player.isSpectator()) {
            return null;
        }
        return wrongJob(Job.byId(SkillManager.get(player).curJob), stack);
    }

    // 職 cur の人が stack を使えないなら、その武器の職 (職を選ぶ前の人・職の品でない物は null)
    @Nullable
    public static Job wrongJob(@Nullable Job cur, ItemStack stack) {
        Job held = Job.of(stack);
        if (cur == null || held == null || held == cur || BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().startsWith("gather_")) {
            return null;
        }
        return held;
    }

    // 自己テスト (SKYABYSS_SELFTEST_ONLY="job weapons")
    public static void selfTest(net.minecraft.server.MinecraftServer server) {
        java.util.List<String> f = new java.util.ArrayList<>();
        ItemStack bow = new ItemStack(net.minecraft.world.item.Items.BOW);
        ItemStack axe = new ItemStack(BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "gather_axe")));
        ItemStack hammer = new ItemStack(BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "storm_hammer")));
        if (wrongJob(Job.WARRIOR, bow) != Job.ARCHER || wrongJob(Job.ARCHER, bow) != null) {
            f.add("a bow should be locked for a warrior and free for an archer");
        }
        if (Job.of(hammer) != Job.SMITH || wrongJob(Job.MAGE, hammer) != Job.SMITH || wrongJob(Job.SMITH, hammer) != null) {
            f.add("a smith hammer should be locked for a mage and free for a smith (" + Job.of(hammer) + ")");
        }
        if (axe.isEmpty() || wrongJob(Job.MAGE, axe) != null) {
            f.add("the gathering axe should never be locked");
        }
        // 名前に「rod」が入る素材は杖ではない (熾火の棒は灰の荒野の素材。店は職の品を今の職の人にだけ並べるので、杖と見ると魔法使いにしか売られない)
        for (String id : java.util.List.of("ember_rod", "fishing_rod")) {
            var item = BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, id));
            if (item != net.minecraft.world.item.Items.AIR && Job.of(item) != null) {
                f.add(id + " is read as a " + Job.of(item) + " weapon");
            }
        }
        if (wrongJob(Job.MAGE, ItemStack.EMPTY) != null || wrongJob((Job) null, bow) != null) {
            f.add("bare hands and players without a job should never be locked");
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST job weapons PASSED (other jobs' weapons locked, own weapon, bare hands, gathering tools and rod materials free)");
        } else {
            SkyAbyss.LOGGER.error("SELFTEST job weapons FAILED: {}", f);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onAttack(AttackEntityEvent event) {
        Player p = event.getEntity();
        if (wrongJob(p, p.getMainHandItem()) != null) {
            event.setCanceled(true);
            tell(p);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onUseStart(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof Player p && event.getItem().getItem() instanceof ProjectileWeaponItem && wrongJob(p, event.getItem()) != null) {
            event.setCanceled(true);
            tell(p);
        }
    }

    public static void tell(Player p) {
        if (!(p instanceof ServerPlayer sp)) {
            return;
        }
        long now = sp.serverLevel().getGameTime();
        Long last = TOLD.get(sp.getUUID());
        if (last != null && now - last < TELL_EVERY && now >= last) {
            return;
        }
        TOLD.put(sp.getUUID(), now);
        Job cur = Job.byId(SkillManager.get(sp).curJob);
        Notice.bar(sp, cur == null ? ModText.deny("message.skyabyss.skill.no_job")
                : ModText.deny("message.skyabyss.skill.wrong_job_attack", Component.translatable(cur.nameKey())));
    }
}
