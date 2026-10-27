package jp.revati.skyabyss.story.ch1;

import java.util.Optional;

import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.opening.OpeningSetup;
import jp.revati.skyabyss.opening.OpeningSites;
import jp.revati.skyabyss.opening.site.ChapterSites;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

// 歌石 (E4): 地上に 5 つ。石の傍らの積み石の高さが番号 (1〜5)。
// 1 → 2 → 3 → 4 → 5 の順に叩くと、5 つ目で鐘の宿りの鐘が遠くで応える (数え歌の先取り。歌の続きの「いつつ」は途中で切れる)。
// 順番を違えると、石は鳴りかけて黙り、1 番を叩いていれば 1 からやり直し、それ以外は最初に戻る。進みは人ごと (Ch1Data)。
// 石の番号は音の数でも分かる (n 番の石は n 回鳴る)。
public final class SongStones {
    public static final int COUNT = 5;

    public enum Result {
        ADVANCE, RESTART, WRONG, SILENT
    }

    public record Step(Result result, int progress) {
    }

    private SongStones() {
    }

    // 進み progress の人が、番号 n の石を叩いたときの結果 (純粋)
    public static Step step(int progress, int n, boolean done) {
        if (done) {
            return new Step(Result.SILENT, COUNT);
        }
        if (n == progress + 1) {
            return new Step(Result.ADVANCE, n);
        }
        if (n == 1) {
            return new Step(Result.RESTART, 1);
        }
        return new Step(Result.WRONG, 0);
    }

    // pos の歌石の番号 (1〜5。登録された要素の場所から引く。見つからなければ 0)
    public static int numberAt(MinecraftServer server, BlockPos pos) {
        for (int k = 1; k <= COUNT; k++) {
            for (ChapterSites.Site s : ChapterSites.sites(server, Sites.songStoneId(k))) {
                if (Math.hypot(s.pos().getX() - pos.getX(), s.pos().getZ() - pos.getZ()) <= 6 && Math.abs(s.pos().getY() - pos.getY()) <= 6) {
                    return k;
                }
            }
        }
        return 0;
    }

    public static void tap(ServerPlayer player, ServerLevel level, BlockPos pos) {
        int n = numberAt(level.getServer(), pos);
        tap(player, level, pos, n);
    }

    // 番号を渡して叩く (自己テストから)
    public static Step tap(ServerPlayer player, ServerLevel level, BlockPos pos, int n) {
        Ch1Data data = Ch1Data.get(level.getServer().overworld());
        Ch1Data.Player st = data.player(player.getUUID());
        if (n <= 0) {
            // どの要素の石か分からない (古い置き方・コマンドで置いた石): ただ鳴らす
            chimes(level, pos, 1);
            return new Step(Result.SILENT, st.song);
        }
        Step step = step(st.song, n, st.songDone);
        chimes(level, pos, n);
        switch (step.result()) {
            case SILENT -> Notice.narrate(player, "message.skyabyss.ch1.stone.done");
            case WRONG, RESTART -> {
                st.song = step.progress();
                data.setDirty();
                if (step.result() == Result.WRONG) {
                    Notice.narrate(player, "message.skyabyss.ch1.stone.wrong");
                } else {
                    Notice.narrate(player, "message.skyabyss.ch1.stone.n1");
                }
            }
            case ADVANCE -> {
                st.song = step.progress();
                data.setDirty();
                Notice.narrate(player, "message.skyabyss.ch1.stone.n" + n);
                if (n == COUNT) {
                    st.songDone = true;
                    Tasks.later(40, () -> answer(player));
                }
            }
        }
        return step;
    }

    // 5 つ目: 鐘の宿りの鐘が応える。頁も記す
    static void answer(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null || player.isRemoved()) {
            return;
        }
        Optional<BlockPos> bell = OpeningSites.bell(server).or(() -> OpeningSites.shelter(server));
        if (player.connection != null) {
            // 近くにいても遠くにいても、宿りの方角から聞こえる (減衰距離は音の定義のとおり。遠すぎるときは本人の側へ寄せる)
            double x = player.getX() + 40;
            double y = player.getY() + 6;
            double z = player.getZ();
            if (bell.isPresent()) {
                BlockPos at = bell.get();
                double dx = at.getX() + 0.5 - player.getX();
                double dz = at.getZ() + 0.5 - player.getZ();
                double d = Math.hypot(dx, dz);
                double reach = Math.min(d, 60);
                x = player.getX() + (d < 1 ? 0 : dx / d * reach);
                z = player.getZ() + (d < 1 ? 40 : dz / d * reach);
                y = at.getY() + 1.0;
            }
            player.connection.send(new ClientboundSoundPacket(OpeningSetup.BELL, SoundSource.AMBIENT, x, y, z, 1.0f, 0.9f, player.getRandom().nextLong()));
        }
        Notice.narrate(player, "message.skyabyss.ch1.stone.answer");
        Lore.unlock(player, Ch1.SONG, true);
        // 鐘の下の戸 (歌の間) が開く
        if (SongChamber.open(player.serverLevel())) {
            Tasks.later(60, () -> Notice.narrate(player, "message.skyabyss.ch1.stone.chamber"));
        }
    }

    // n 回、澄んだ音を鳴らす (数えるように)
    static void chimes(ServerLevel level, BlockPos pos, int n) {
        float pitch = 0.62f + 0.1f * Math.max(1, n);
        for (int i = 0; i < Math.max(1, n); i++) {
            Tasks.later(i * 5, () -> {
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.4f, pitch);
                level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.5f, pitch * 0.5f);
            });
        }
    }
}
