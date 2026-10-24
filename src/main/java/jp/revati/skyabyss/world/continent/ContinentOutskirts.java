package jp.revati.skyabyss.world.continent;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.opening.site.Blueprint;
import jp.revati.skyabyss.opening.site.SiteData;
import jp.revati.skyabyss.opening.site.SiteJob;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 外れの一本道の道ぞいの休み (docs/design/40 の 4 章「はじめの一時間」の 1)。野営地から都への道 (layout.json の最初の道) の、
// 4 割の所に焚き火の休み場 (食べ物の樽)、7 割の所に宿り木の祠 (そばにいると傷が癒える)。道の脇 (横へ OFFSET) に置く。
// 札は一行ずつ (場所の名前と、することを一つ)。大陸の世界でだけ、場所を決めるときに足して建てる
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ContinentOutskirts {
    public static final String KIND = "outskirts";
    static final String[] BLUEPRINTS = {"outskirts_fire", "outskirts_shrine"};
    static final double[] AT = {0.4, 0.7};
    static final int OFFSET = 8;
    static final double HEAL_RADIUS = 4.5;
    static final int CHECK = 40;

    private ContinentOutskirts() {
    }

    // 道の上の割合 t の点と、そこでの道の向き (dx, dz の単位)
    static double[] along(List<BlockPos> line, double t) {
        double total = 0;
        for (int i = 0; i + 1 < line.size(); i++) {
            total += Math.sqrt(line.get(i).distSqr(line.get(i + 1)));
        }
        double want = total * t;
        for (int i = 0; i + 1 < line.size(); i++) {
            BlockPos a = line.get(i);
            BlockPos b = line.get(i + 1);
            double len = Math.sqrt(a.distSqr(b));
            if (want <= len || i + 2 == line.size()) {
                double u = len == 0 ? 0 : Math.min(1, want / len);
                double dx = (b.getX() - a.getX()) / Math.max(1e-6, len);
                double dz = (b.getZ() - a.getZ()) / Math.max(1e-6, len);
                return new double[] {a.getX() + (b.getX() - a.getX()) * u, a.getZ() + (b.getZ() - a.getZ()) * u, dx, dz};
            }
            want -= len;
        }
        BlockPos e = line.get(line.size() - 1);
        return new double[] {e.getX(), e.getZ(), 1, 0};
    }

    // 置き場 (x, z)。道の右手 (進む向きから見て) の脇。avoid (足跡など) から AVOID より近ければ、道の上を少しずつずらし、反対の脇も試す
    static final int AVOID = 40;
    static final double[] SHIFTS = {0, 0.04, -0.04, 0.08, -0.08, 0.12, -0.12};

    public static List<int[]> spots(List<BlockPos> avoid) {
        List<int[]> out = new ArrayList<>();
        List<List<BlockPos>> roads = ContinentLayout.get().roads();
        if (roads.isEmpty()) {
            return out;
        }
        for (double t : AT) {
            int[] pick = null;
            search:
            for (double sh : SHIFTS) {
                double[] p = along(roads.get(0), t + sh);
                for (int side : new int[] {1, -1}) {
                    int x = (int) Math.round(p[0] - p[3] * OFFSET * side);
                    int z = (int) Math.round(p[1] + p[2] * OFFSET * side);
                    if (avoid.stream().noneMatch(a -> Math.hypot(a.getX() - x, a.getZ() - z) < AVOID)) {
                        pick = new int[] {x, z};
                        break search;
                    }
                }
            }
            if (pick == null) {
                double[] p = along(roads.get(0), t);
                pick = new int[] {(int) Math.round(p[0] - p[3] * OFFSET), (int) Math.round(p[1] + p[2] * OFFSET)};
            }
            out.add(pick);
        }
        return out;
    }

    public static SiteJob create(ServerLevel level, SiteData data, SiteData.Entry entry) {
        int i = Math.max(0, Math.min(BLUEPRINTS.length - 1, entry.index));
        return new SiteJob(level, data, entry, Blueprint.get(BLUEPRINTS[i]), BLUEPRINTS[i], job -> {
            BlockPos heal = job.mark("heal");
            if (heal != null) {
                job.entry.extra("heal", heal);
            }
            BlockPos barrel = job.mark("barrel");
            if (barrel != null && level.getBlockEntity(barrel) instanceof BarrelBlockEntity be) {
                be.setItem(0, new ItemStack(Items.BREAD, 16));
                be.setItem(1, new ItemStack(Items.COOKED_COD, 8));
            }
            BlockPos sign = job.mark("sign");
            if (sign != null && level.getBlockEntity(sign) instanceof SignBlockEntity be) {
                String key = "sign.skyabyss.outskirts." + (i == 0 ? "fire" : "shrine");
                be.setText(new SignText().setMessage(1, Component.translatable(key + ".1")).setMessage(2, Component.translatable(key + ".2")), true);
                be.setWaxed(true);
                be.setChanged();
                level.sendBlockUpdated(sign, be.getBlockState(), be.getBlockState(), 3);
            }
        });
    }

    // 祠のそばにいる人の傷を癒やす (再生 I を 3 秒ずつ)
    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.isSpectator() || p.level().dimension() != Level.OVERWORLD
                || (p.tickCount + p.getId()) % CHECK != 0 || p.getHealth() >= p.getMaxHealth()) {
            return;
        }
        for (SiteData.Entry e : SiteData.get(p.serverLevel()).entries(KIND)) {
            BlockPos heal = e.extra("heal");
            if (e.built && heal != null && heal.distToCenterSqr(p.position()) <= HEAL_RADIUS * HEAL_RADIUS) {
                p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, true, true));
                return;
            }
        }
    }
}
