package jp.revati.skyabyss.world.continent;

import java.util.LinkedHashMap;
import java.util.Map;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.AttendantRole;
import jp.revati.skyabyss.facility.Attendants;
import jp.revati.skyabyss.opening.site.Blueprint;
import jp.revati.skyabyss.opening.site.SiteData;
import jp.revati.skyabyss.opening.site.SiteJob;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 天と淵の大陸の、土地ごとの小さな町 (docs/design/40 の 3 章「作る順」の 4)。広場 (井戸・依頼の板・街灯)・宿・店・家二つ。
// 場所は layout.json の towns。大陸の世界でだけ、場所を決めるとき (SiteFinder.planRestAll) に足し、野営地や都と同じく少しずつ建てる。
// 部品は data/skyabyss/opening/town_<土地>.json (tools/gen/world/gen_region_towns.py)。SiteData の種類は "town"、番号は REGIONS の並び。
// 建てたら部品の印 (rest・board・npc_<役>) を場所の付属の点に写し、札に町の名前と目安のレベルを書く。近く (KEEP_RADIUS) に人がいれば、宿の主人と道具屋を立たせ続ける
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ContinentTowns {
    public static final String KIND = "town";
    public static final String[] REGIONS = {"whale", "tide", "storm", "ember"};
    static final String[] MARKS = {"rest", "board", "inn", "shop", AttendantRole.INNKEEPER.mark(), AttendantRole.TOOL_MERCHANT.mark()};
    static final int KEEP_RADIUS = 96;
    static final int CHECK_INTERVAL = 100;

    private ContinentTowns() {
    }

    public static String region(int index) {
        return REGIONS[Math.max(0, Math.min(REGIONS.length - 1, index))];
    }

    public static SiteJob create(ServerLevel level, SiteData data, SiteData.Entry entry) {
        String region = region(entry.index);
        return new SiteJob(level, data, entry, Blueprint.get("town_" + region), "town " + region, job -> {
            for (String m : MARKS) {
                BlockPos at = job.mark(m);
                if (at != null) {
                    job.entry.extra(m, at);
                }
            }
            BlockPos sign = job.mark("sign");
            if (sign != null && level.getBlockEntity(sign) instanceof net.minecraft.world.level.block.entity.SignBlockEntity be) {
                ContinentLayout.Level lv = ContinentLayout.get().levels().get(region);
                net.minecraft.world.level.block.entity.SignText text = new net.minecraft.world.level.block.entity.SignText()
                        .setMessage(1, net.minecraft.network.chat.Component.translatable("map.skyabyss.town." + region))
                        .setMessage(2, net.minecraft.network.chat.Component.literal(lv == null ? "" : "Lv " + lv.text()));
                be.setText(text, true);
                be.setWaxed(true);
                be.setChanged();
                level.sendBlockUpdated(sign, be.getBlockState(), be.getBlockState(), 3);
            }
        });
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (level.dimension() != Level.OVERWORLD || (level.getGameTime() + (player.getId() & 0x3F) + 37) % CHECK_INTERVAL != 0) {
            return;
        }
        for (SiteData.Entry e : SiteData.get(level).entries(KIND)) {
            if (!e.built || e.pos.distSqr(player.blockPosition()) > (double) KEEP_RADIUS * KEEP_RADIUS) {
                continue;
            }
            Map<String, BlockPos> marks = new LinkedHashMap<>();
            for (int i = 0; i < e.extraNames.size(); i++) {
                if (e.extraNames.get(i).startsWith(AttendantRole.MARK_PREFIX)) {
                    marks.put(e.extraNames.get(i), e.extraPos.get(i));
                }
            }
            Attendants.ensureAll(level, marks);
        }
    }
}
