package jp.revati.skyabyss.tablet;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.SignFit;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 碑の置き方と読み方。文のキーは看板のときと同じ ("sign.skyabyss.xxx" の lang の ".1"〜".4" の 4 行。空の行は飛ばす)。
// 読むと、画面の下中央の字幕 (NARRATE) に、行を分けたまま出る。読んだ碑は伝承の「碑文」の条件 ("sign") も満たす
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Tablets {
    private static final double LOOK_DISTANCE = 4.5;
    private static final long REREAD_TICKS = 600;
    private static final Map<UUID, BlockPos> LAST_POS = new HashMap<>();
    private static final Map<UUID, Long> LAST_TIME = new HashMap<>();

    private Tablets() {
    }

    // ---------------------------------------------------------------- 置く
    // 壁掛けの碑 (facing = 文が向く向き)
    public static BlockState wallState(Direction facing) {
        return ModBlocks.TABLET.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing).setValue(TabletBlock.STANDING, false);
    }

    // 立てた碑 (文は北向き。看板の ROTATION 8 と同じ)
    public static BlockState standingState() {
        return ModBlocks.TABLET.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH).setValue(TabletBlock.STANDING, true);
    }

    // 置いた碑に、文のキーを刻む (ブロックを置いたあとに呼ぶ)
    public static void write(LevelAccessor level, BlockPos pos, String key) {
        if (level.getBlockEntity(pos) instanceof TabletBlockEntity tablet) {
            tablet.setKey(key);
        }
    }

    // ---------------------------------------------------------------- 読む
    // 4 行を、行を保ったまま 1 つの文にする
    public static Component text(String key) {
        MutableComponent body = Component.empty();
        var ja = SignFit.lang("ja_jp");
        boolean first = true;
        for (int i = 1; i <= 4; i++) {
            if (ja.getOrDefault(key + "." + i, "").isEmpty()) {
                continue;
            }
            if (!first) {
                body.append("\n");
            }
            body.append(Component.translatable(key + "." + i));
            first = false;
        }
        return body;
    }

    public static void read(ServerPlayer player, String key) {
        Notice.send(player, Notice.Kind.NARRATE, Notice.tag(Notice.Kind.NARRATE,
                Component.literal("❖ ").withStyle(s -> s.withColor(0x7FD6D0)),
                text(key).copy().withStyle(s -> s.withColor(0x7FD6D0).withItalic(true))));
        Lore.fire(player, "sign", t -> t.target().equals(key));
        jp.revati.skyabyss.advancement.Beats.fire(player, "tablet:read");
    }

    // 目を向けるだけでも読める (右クリックを知らない人のため)。同じ碑は、見続けても一度。同じ碑は 30 秒たつまで読み直さない
    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive() || player.isSpectator()) {
            return;
        }
        long now = player.level().getGameTime();
        if ((now + (player.getId() & 0xFF)) % 5 != 0) {
            return;
        }
        HitResult hit = player.pick(LOOK_DISTANCE, 0f, false);
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK
                || !(player.level().getBlockEntity(block.getBlockPos()) instanceof TabletBlockEntity tablet) || tablet.key().isEmpty()) {
            return;
        }
        UUID id = player.getUUID();
        BlockPos pos = block.getBlockPos();
        boolean same = pos.equals(LAST_POS.get(id));
        if (same && now - LAST_TIME.getOrDefault(id, 0L) < REREAD_TICKS) {
            return;
        }
        LAST_POS.put(id, pos.immutable());
        LAST_TIME.put(id, now);
        read(player, tablet.key());
    }
}
