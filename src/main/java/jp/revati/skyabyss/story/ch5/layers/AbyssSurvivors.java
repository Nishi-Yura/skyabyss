package jp.revati.skyabyss.story.ch5.layers;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.AttendantEntity;
import jp.revati.skyabyss.facility.FacilitySetup;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.story.ch5.AbyssBuild;
import jp.revati.skyabyss.story.ch5.AbyssWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 前の探掘隊の生き残りと手記 (docs/design/36 の 8 章「物語」)。
//   生き残り: 層ごとに一人、野営に立つ (係の人の形 AttendantEntity。役 survivor_<層>、会話は data/skyabyss/dialogue/ch5/attendant_survivor_<層>.json)。
//     骨 = 灯り番のミオ、塩 = 測量士のハヤセ (と荷運びのロク abyss_porter。品の取り替え)、雷 = 薬師のシズ、灰 = 隊長のトウヤ。
//     いなくなったら (コマンドで消した等)、人が近くに来たときに立て直す。塩のロクと灰のトウヤは、護りの下り道のあいだ野営にいない (AbyssEscort)
//   手記: 野営の帳面 (journal_<層>_0) と、道ごとの書見台 (journal_<層>_1..3)。右クリックで読む (人ごとに記録 "read:journal_<層>_<i>"、合図 abyss_journal_<層>_<i>)
//     文は message.skyabyss.abyss_v2.journal.<層>_<i>_1 / _2 (tools/gen/ch5/gen_ch5_layers.py)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class AbyssSurvivors {
    public static final String TAG = "skyabyss_abyss_survivor";
    // 役 → 立つ所の印
    static final String[][] PEOPLE = {{"survivor_bone", "survivor_bone"}, {"survivor_salt", "survivor_salt"}, {"abyss_porter", "trader_salt"},
            {"survivor_storm", "survivor_storm"}, {"survivor_ash", "survivor_ash"}};

    private AbyssSurvivors() {
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 100 != 37) {
            return;
        }
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        if (level == null || level.players().isEmpty() || !AbyssBuild.built(server)) {
            return;
        }
        for (String[] who : PEOPLE) {
            BlockPos at = AbyssBuild.mark(who[1]);
            if (at == null || !level.isLoaded(at) || AbyssEscort.busy(who[0])) {
                // 護りの下り道に出ている人 (AbyssEscort) は、野営に立てない
                continue;
            }
            boolean near = false;
            for (ServerPlayer p : List.copyOf(level.players())) {
                if (p.blockPosition().closerThan(at, 48)) {
                    near = true;
                }
            }
            if (near) {
                keep(level, who[0], at);
            }
        }
    }

    // その役の人がいなければ立てる。いれば (遠くへ行っていれば) 戻す
    public static AttendantEntity keep(ServerLevel level, String role, BlockPos at) {
        List<AttendantEntity> found = level.getEntitiesOfClass(AttendantEntity.class, new AABB(at).inflate(40, 12, 40),
                e -> e.getTags().contains(TAG) && role.equals(e.roleId()));
        if (!found.isEmpty()) {
            AttendantEntity a = found.get(0);
            for (int i = 1; i < found.size(); i++) {
                found.get(i).discard();
            }
            if (!a.blockPosition().closerThan(at, 4)) {
                a.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            }
            return a;
        }
        AttendantEntity a = FacilitySetup.ATTENDANT.get().create(level);
        if (a == null) {
            return null;
        }
        a.setRoleId(role);
        a.addTag(TAG);
        a.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 180f, 0f);
        a.setHome(at);
        a.setPersistenceRequired();
        level.addFreshEntity(a);
        return a;
    }

    // ---------------------------------------------------------------- 手記
    @SubscribeEvent
    static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getLevel().dimension() != AbyssWorld.LEVEL || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) {
            return;
        }
        for (String layer : Layers.ORDER) {
            for (int i = 0; i <= 3; i++) {
                BlockPos m = AbyssBuild.mark("journal_" + layer + "_" + i);
                if (m != null && m.equals(event.getPos())) {
                    event.setCanceled(true);
                    read(p, layer, i);
                    return;
                }
            }
        }
    }

    public static boolean read(ServerPlayer p, String layer, int i) {
        String key = layer + "_" + i;
        boolean first = LayerData.get(p.server).set(p.getUUID(), "read:journal_" + key);
        Notice.narrate(p, "message.skyabyss.abyss_v2.journal." + key + "_1");
        LayerFights.later(p.server, 70, () -> Notice.narrate(p, "message.skyabyss.abyss_v2.journal." + key + "_2"));
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 0.9f);
        Quests.signalWithParty(p.serverLevel(), List.of(p), "abyss_journal_" + key);
        return first;
    }
}
