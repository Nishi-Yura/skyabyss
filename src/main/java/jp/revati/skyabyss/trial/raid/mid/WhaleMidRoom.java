package jp.revati.skyabyss.trial.raid.mid;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.entity.StoneWhaleHeart;
import jp.revati.skyabyss.entity.WhaleboneCaptain;
import jp.revati.skyabyss.loot.TitanLoot;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidKit;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

// 岩鯨の中ボス「番兵長」の間 (元は東の翼の番兵長の広間 E3。13 × 31、高さ 11、柱の列)。
// 前触れの波 (骨の番兵 2・肋の射手・苔這い 2。人が一人増えるごとに 2 体) を倒すと、番兵長が間の奥に現れる
// (人が一人増えるごとに番兵と射手が 1 体ずつ。番兵長の体力は 280 × (1 + 0.6 × (人数 − 1)))。倒すと越える
public final class WhaleMidRoom extends MidRoom {
    public static final String ID = "WRM";
    static final int HX = 6;
    static final int HZ = 15;
    static final int HEIGHT = 11;

    private int wave;

    @Override
    public void build(RaidCtx ctx) {
        RaidKit.buildCell(ctx, new RaidKit.Spec(HX, HZ, HEIGHT, RaidKit.BONE));
        // 広間の柱 (元の広間の柱の列と同じく、両側の壁から 2 内側に 8 おき)。身を隠せる
        for (int z = -HZ + 5; z <= HZ - 5; z += 8) {
            for (int x : new int[] {-HX + 2, HX - 2}) {
                for (int y = 1; y <= HEIGHT; y++) {
                    RaidKit.set(ctx.level(), ctx.local(x, y, z), y % 4 == 0 ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState() : Blocks.BONE_BLOCK.defaultBlockState());
                }
            }
        }
        // 黒い旗の代わりの暗い帯 (奥の壁の前の床)
        for (int x = -HX + 1; x <= HX - 1; x++) {
            RaidKit.set(ctx.level(), ctx.local(x, 0, -HZ + 1), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        }
        // 飾り (docs/design/39 の 4.1): 肋骨のアーチの喉の回廊と、奥の番兵長の額
        jp.revati.skyabyss.trial.raid.whale.WhaleDecor.wrm(ctx);
        RaidKit.restInArena(ctx, 0, HZ - 2);
    }

    @Override
    protected void startFight(RaidCtx ctx) {
        wave = 1;
        int extra = Math.min(ctx.scale(), 6) - 1;
        List<String> kinds = new ArrayList<>(List.of("sentinel", "sentinel", "archer", "crawler", "crawler"));
        for (int i = 0; i < extra; i++) {
            kinds.add("sentinel");
            kinds.add("archer");
        }
        for (int i = 0; i < kinds.size(); i++) {
            int x = (i % 3 - 1) * 3;
            int z = -HZ + 3 + (i / 3) * 2;
            ctx.spawnGuard(kinds.get(i), ctx.cell().floor(x, Math.min(z, 0)));
        }
        say(ctx, "raid.skyabyss.WRM.wave");
    }

    @Override
    protected boolean bossReady(RaidCtx ctx) {
        return wave >= 2 || ctx.aliveMobs().isEmpty();
    }

    @Override
    @Nullable
    protected Mob spawnBoss(RaidCtx ctx) {
        wave = 2;
        int n = Math.min(ctx.scale(), 6);
        for (int i = 1; i < n; i++) {
            ctx.spawnGuard(i % 2 == 0 ? "archer" : "sentinel", ctx.cell().floor((i % 3 - 1) * 3, -HZ + 4));
        }
        Mob captain = ctx.spawnGuard("captain", ctx.cell().floor(0, -HZ + 5));
        if (captain instanceof WhaleboneCaptain) {
            var health = captain.getAttribute(Attributes.MAX_HEALTH);
            if (health != null) {
                health.setBaseValue(Math.min(1024, health.getBaseValue() * StoneWhaleHeart.scaleFor(n)));
                captain.setHealth(captain.getMaxHealth());
            }
        }
        say(ctx, "raid.skyabyss.WRM.captain");
        return captain;
    }

    @Override
    protected Vec3 home(RaidCtx ctx) {
        return Vec3.atBottomCenterOf(ctx.cell().floor(0, -HZ + 5));
    }

    @Override
    protected void reward(RaidCtx ctx, Set<UUID> ids) {
        List<ServerPlayer> ps = new ArrayList<>();
        for (UUID id : ids) {
            ServerPlayer p = ctx.level().getServer().getPlayerList().getPlayer(id);
            if (p != null) {
                ps.add(p);
            }
        }
        jp.revati.skyabyss.trial.raid.whale.WhaleDecor.exitLamps(ctx, true);
        TitanLoot.onCaptainDefeat(ctx.level(), ps, ids);
        jp.revati.skyabyss.titan.group.FightBadges.onVictory(ctx.level(), boss, ids, 0);
    }

    @Override
    protected void resetFixtures(RaidCtx ctx) {
        wave = 0;
        jp.revati.skyabyss.trial.raid.whale.WhaleDecor.exitLamps(ctx, false);
    }

    @Override
    public String objectiveKey() {
        return wave >= 2 ? "raid.skyabyss.WRM.defeat" : "raid.skyabyss.WRM.clear";
    }

    private int left;

    @Override
    protected void tickFight(RaidCtx ctx) {
        left = wave >= 2 ? 0 : ctx.aliveMobs().size();
    }

    @Override
    public Object[] objectiveArgs() {
        return new Object[] {left};
    }

    static void say(RaidCtx ctx, String key) {
        for (ServerPlayer p : ctx.players()) {
            Notice.bar(p, Component.translatable(key));
        }
    }
}
