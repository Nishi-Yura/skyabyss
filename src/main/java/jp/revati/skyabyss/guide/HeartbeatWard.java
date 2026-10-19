package jp.revati.skyabyss.guide;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.accessory.Accessories;
import jp.revati.skyabyss.item.accessory.AccessoryData;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.readiness.Readiness;
import jp.revati.skyabyss.titan.group.GroupBossRoom;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

// 鼓動守の働き (21 の 1.4)。心臓の戦い (GroupBossRoom.heartFightOf) の参加者が、鼓動守を装飾の枠に付けているとき、
// 致命の一撃を体力 1 で耐える。人ごと・戦い 1 回につき一度。使うと光って消える (枠の品が消える)。補充は職人 (P4)。
// 心臓以外の戦い・虚空や /kill のような無視できない傷では働かない。備えの糧の灯に 0.2 足す (Readiness.registerCharm)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class HeartbeatWard {
    private HeartbeatWard() {
    }

    static {
        // 持っている (枠に付けている) 人は糧の灯に +0.2。持ち物にあるだけでは足さない (効くのは枠に付けているとき)
        Readiness.registerCharm(player -> wornSlot(player) >= 0);
        Readiness.registerRemedy(Readiness.Lamp.PROVISIONS, () -> net.minecraft.network.chat.Component.translatable("message.skyabyss.readiness.remedy.name.ward"));
    }

    // 呼ぶだけで static の登録が走る (SkyAbyss の初期化から)
    public static void init() {
    }

    // 枠に付けている鼓動守の枠の番号 (なければ -1)。どの巨神のものでもよい
    public static int wornSlot(net.minecraft.world.entity.player.Player player) {
        AccessoryData data = Accessories.get(player);
        for (int i = 0; i < AccessoryData.SIZE; i++) {
            ItemStack s = data.items.get(i);
            if (s.getItem() instanceof HeartbeatWardItem && jp.revati.skyabyss.item.GearLevel.meets(player, s)) {
                return i;
            }
        }
        return -1;
    }

    // ---------------------------------------------------------------- 補充 (旅の職人。21 の 1.4・P4)
    // 使い切った人 (その巨神の鼓動守を持っていない) に、職人が編み直す。1 日に 1 つまで (素材の岩殻片などは会話の側で受け取る)。
    // 最初の一つは備えの頼み「鼓動を聞く」の報酬 (ここを通らない)
    public static boolean remakeable(ServerPlayer player, String titan) {
        net.minecraft.world.item.Item ward = GuideSetup.wardOf(titan);
        if (ward == null || player.getInventory().countItem(ward) > 0) {
            return false;
        }
        AccessoryData data = Accessories.get(player);
        for (int i = 0; i < AccessoryData.SIZE; i++) {
            if (data.items.get(i).is(ward)) {
                return false;
            }
        }
        return !jp.revati.skyabyss.rpg.Growth.discovered(player, remakeKey(player, titan));
    }

    // 編み直して渡す。渡したら true
    public static boolean remake(ServerPlayer player, String titan) {
        if (!remakeable(player, titan)) {
            return false;
        }
        jp.revati.skyabyss.rpg.Growth.discover(player, remakeKey(player, titan));
        jp.revati.skyabyss.quest.Quests.give(player, new ItemStack(GuideSetup.wardOf(titan)));
        return true;
    }

    private static String remakeKey(ServerPlayer player, String titan) {
        return "ward_remake:" + titan + ":" + player.server.overworld().getGameTime() / 24000L;
    }

    // 致命の傷を耐えられる量に直す。働いたら true (damage は耐えたあとの傷を返す)
    // health + absorption 以上の傷が致命。体力が 1 のままになるよう、傷を (health - 1 + absorption) に抑える
    public static float survivable(float damage, float health, float absorption) {
        if (damage < health + absorption) {
            return damage;
        }
        return Math.max(0f, health - 1f + absorption);
    }

    // 致命の傷か (耐えるべきか)。無敵を貫く傷 (虚空・/kill) は耐えない
    public static boolean lethal(float damage, float health, float absorption, @Nullable DamageSource source) {
        return damage >= health + absorption && (source == null || !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY));
    }

    // 働かせる。働いたら true (枠の品を消し、光らせる)
    public static boolean trigger(ServerPlayer player, float damage, @Nullable DamageSource source, java.util.function.Consumer<Float> setDamage) {
        if (!lethal(damage, player.getHealth(), player.getAbsorptionAmount(), source) || !player.isAlive()) {
            return false;
        }
        int slot = wornSlot(player);
        if (slot < 0 || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        GroupBossRoom room = GroupBossRoom.heartFightOf(level, player.getUUID());
        if (room == null || !room.claimWard(player.getUUID())) {
            return false;
        }
        setDamage.accept(survivable(damage, player.getHealth(), player.getAbsorptionAmount()));
        // 使うと光って消える
        Accessories.get(player).items.set(slot, ItemStack.EMPTY);
        Accessories.sync(player);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.4f, 0.7f);
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 24, 0.4, 0.7, 0.4, 0.04);
        Notice.hint(player, "message.skyabyss.heartbeat_ward.used");
        return true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onDamagePre(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player && !player.level().isClientSide && event.getNewDamage() > 0) {
            trigger(player, event.getNewDamage(), event.getSource(), event::setNewDamage);
        }
    }
}
