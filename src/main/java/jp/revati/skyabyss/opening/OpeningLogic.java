package jp.revati.skyabyss.opening;

// 序盤の拍子 B0〜B10 (docs/design/17 の 3 章) を進める判断。世界にも人にも触らない (副作用は Fx に任せる) ので、
// 自己テストが「新しいワールド」「雨の日」「洞窟」「寝た人」「二人目」を作った時刻と状況の列で流せる。
// 呼ぶのは人ごと・1 秒に 1 回ほど (OpeningDirector)。偶然には頼らない: 確率は、起きる時刻の幅にだけ使う。
public final class OpeningLogic {
    public static final long DAY = 24000L;
    // 夜の拍子を起こしてよい一日の時刻 (日没の明るさが残るうちと、夜明け前は避ける)
    public static final long NIGHT_START = 13000L;
    public static final long NIGHT_LAST = 21000L;

    // 帳面を「拾う」距離 (野営地の帳面の位置から)
    static final double PICK_DISTANCE = 2.5;
    // 帳面を拾わないまま野営地を離れたとき・目覚めてから十分たったとき、帳面は荷物へ来る
    static final double LEAVE_CAMP = 80.0;
    static final long LEAVE_AFTER = 1200L;
    static final long CAMP_FALLBACK = 6000L;
    // 野営地のない世界 (まだ建っていない・古いワールド) では、目覚めて 20 秒後に帳面が荷物へ来る
    static final long NO_CAMP_WAIT = 400L;
    // 宿りの鐘: 目的が「煙を追う」の間、30 秒おき。聞こえる範囲 (鐘の音の減衰距離に合わせる)
    static final long BELL_EVERY = 600L;
    static final double BELL_HEARD = 230.0;
    // 宿りに着いたとみなす距離
    static final double SHELTER_ARRIVED = 40.0;
    // 足跡のくぼみ・石板に近づいたとみなす距離 (語りの担当の表: 足跡 24、石板 12)
    // 足跡の形 (見える足の形) の外へ、この距離までは「足跡の中」とみなす
    static final int FOOTPRINT_MARGIN = 3;
    static final double SLAB_NEAR = 12.0;
    // 野営地の頁は、帳面を拾って 3 秒後 (野営地の中心から 12 ブロック以内にいるとき)
    static final long CAMP_LORE_DELAY = 60L;
    static final double CAMP_NEAR = 12.0;
    // 足音 (B6): 夜の最初の 60〜180 秒のうちの一時点。3 度の足音は 3 秒おき。頁は 3 度目の 5 秒後
    public static final int STEPS_MIN = 1200;
    public static final int STEPS_MAX = 3600;
    public static final int STEPS_FADE = 100;
    public static final int STEPS_GAP = 60;
    public static final int STEPS_TOTAL = 480;
    public static final int STEPS_LORE = 340;
    // 歌 (B9): 夜の最初の 30〜120 秒のうちの一時点。歌は 40 秒、星の欠けは 5 秒後から 30 秒。頁は星が戻って 5 秒後
    public static final int SONG_MIN = 600;
    public static final int SONG_MAX = 2400;
    public static final int SONG_TOTAL = 900;
    public static final int SONG_LORE = 900;
    // 歌は星が十分に出てから (一日の時刻。日没 13000 の直後は星が薄い) と、夜明けの前に終わる時刻まで。雨・雷の夜は晴れるまで待つ
    public static final long SONG_EARLIEST = 14000L;
    public static final long SONG_LATEST = 21000L;
    // 戦闘中などでずらすときの間隔
    static final int POSTPONE = 100;
    // 影のあとの破れた頁: 影が去って 3 秒後に落とす。落としても読まれなければ、12 分後に自動で読む
    static final long PAGE_DELAY = 60L;
    static final long PAGE_AUTO_READ = 14400L;
    // 影の直後に「影が去った」と数える幅 (見回りの間隔より長く)
    public static final long PASS_END_WINDOW = 400L;
    // 夜の拍子は、目覚めてからこれだけたつまで起こさない (開幕と帳面の邪魔をしない)
    static final long NIGHT_AFTER_AWAKE = 6000L;
    // 影を見ていなくても、目覚めてこれだけたてば足音の資格を与える (詰み止め)
    static final long FOOTSTEPS_NO_SHADOW_AFTER = 36000L;

    private OpeningLogic() {
    }

    // 判断に使う、その人のその時の状況
    public record Env(long now, long dayTime, boolean overworld, boolean awake, boolean skyVisible, boolean deep, boolean inWater,
            boolean danger, boolean horror, boolean passActive, boolean clearSky) {
        // 天気を問わない (テスト用): 晴れとみなす
        public Env(long now, long dayTime, boolean overworld, boolean awake, boolean skyVisible, boolean deep, boolean inWater,
                boolean danger, boolean horror, boolean passActive) {
            this(now, dayTime, overworld, awake, skyVisible, deep, inWater, danger, horror, passActive, true);
        }

        long tod() {
            return Math.floorMod(dayTime, DAY);
        }

        long day() {
            return Math.floorDiv(dayTime, DAY);
        }

        // 足音・歌を起こしてよい状態か (起きていて、戦っておらず、体力が十分で、水の中でなく、影の最中でもない)
        boolean calm() {
            return awake && !inWater && !danger && !passActive;
        }
    }

    // 世界・伝承・クエストから読んだ事実
    public record Facts(boolean legacyCandidate, boolean hasBook, boolean campBuilt, double notebookDist, double campDist, double shelterDist,
            boolean footprintNear, boolean slabNear, boolean metStoryteller, boolean metIsana, boolean campLore, boolean footprintLore,
            boolean shadowLore, boolean boneLore, boolean marginLore, boolean whaleLore, boolean passEndedNow, int passCount,
            long firstPassTick, String mainStage) {
    }

    // 副作用の口 (本物は OpeningDirector、テストは記録するだけ)
    public interface Fx {
        void awake();

        void giveNotebook();

        void campLore();

        void ringBell();

        void footprintsLore();

        void startFootsteps(long now);

        void footstepsLore();

        void startSong(long now);

        void songLore();

        void dropShadowPage();

        void readShadowPage();

        void startPersonalShadow();

        void bonesLore();

        void marginPage();

        int random(int min, int max);
    }

    // ---------------------------------------------------------------- 夜
    // 夜の番号 (その日の日没から、一日の時刻が NIGHT_LAST までの間だけ。それ以外は -1)
    public static long nightId(long dayTime) {
        long tod = Math.floorMod(dayTime, DAY);
        return tod >= NIGHT_START && tod <= NIGHT_LAST ? Math.floorDiv(dayTime, DAY) : -1;
    }

    // 洞窟・地下深くでなければ夜の拍子を受けられる
    static boolean exposed(Env env) {
        return env.skyVisible || !env.deep;
    }

    // 足音の資格: 影を見たあと (見ていなくても目覚めて 1.5 日たてば)。初めての夜
    static boolean footstepsEligible(OpeningState st, Env env) {
        return !st.done(OpeningBeat.FOOTSTEPS) && env.horror && env.now - st.awakeAt >= NIGHT_AFTER_AWAKE
                && (st.done(OpeningBeat.SHADOW) || env.now - st.awakeAt >= FOOTSTEPS_NO_SHADOW_AFTER);
    }

    // 歌の資格: 足音を済ませた次の夜以降。空の見える所で (星の欠けが見えないと意味がないので)
    static boolean songEligible(OpeningState st, Env env, long night) {
        return !st.done(OpeningBeat.SONG) && env.horror && st.done(OpeningBeat.FOOTSTEPS) && night > st.stepsDoneNight
                && env.now - st.awakeAt >= NIGHT_AFTER_AWAKE;
    }

    // 星の欠けが見える空か: 晴れていて、星が十分に出ている時刻 (SONG_EARLIEST〜SONG_LATEST)
    static boolean songSkyReady(Env env) {
        return env.clearSky && env.tod() >= SONG_EARLIEST && env.tod() <= SONG_LATEST;
    }

    // ---------------------------------------------------------------- 目的の帯
    // 画面左上の帯に出す目的の ID (objective.skyabyss.<ID>。17 の 11.2節)。空なら帯はクエストの目的を出す
    public static String objective(OpeningState st, Facts f, Env env) {
        if (st.legacy() || st.awakeAt < 0) {
            return "";
        }
        if (!st.done(OpeningBeat.CAMP)) {
            return "examine_camp";
        }
        String stage = f.mainStage();
        String id = switch (stage) {
            case "", "follow_smoke" -> "follow_smoke";
            case "watch_sky" -> "watch_sky";
            case "ask_storyteller" -> "ask_storyteller";
            case "find_chaser" -> "find_isana";
            case "see_whale" -> "see_whale";
            default -> "";
        };
        if (!id.isEmpty() && boneOffer(st, env) && (stage.equals("find_chaser") || stage.equals("see_whale"))) {
            return "bone_hill";
        }
        return id;
    }

    // 日 2 の昼の任意の寄り道 (イサナと話したあと。足りなければ出さなくてよい)
    static boolean boneOffer(OpeningState st, Env env) {
        long tod = env.tod();
        return st.awakeDay() >= 0 && env.day() > st.awakeDay() && tod >= 3000 && tod <= 11000 && !st.done(OpeningBeat.BONES)
                && st.done(OpeningBeat.ISANA);
    }

    // ---------------------------------------------------------------- 一歩
    // 人ごと・1 秒に 1 回ほど。状態 st を進め、起こすことを fx に頼む
    public static void step(OpeningState st, Env env, Facts f, Fx fx) {
        if (!env.overworld) {
            return;
        }
        long now = env.now;
        // 初参加の判定。すでに遊んでいた人は何も起こさない
        if (st.awakeAt < 0) {
            st.awakeAt = now;
            st.awakeDay = env.day();
            if (f.legacyCandidate()) {
                st.legacy = true;
                for (OpeningBeat beat : OpeningBeat.values()) {
                    st.markDone(beat);
                }
                return;
            }
            st.markDone(OpeningBeat.AWAKE);
            fx.awake();
            return;
        }
        if (st.legacy) {
            return;
        }
        long awakeFor = now - st.awakeAt;

        // 済んだ事実の取り込み (別の道で先に進んだ人)
        if (f.metStoryteller()) {
            st.markDone(OpeningBeat.STORYTELLER);
        }
        if (f.metIsana()) {
            st.markDone(OpeningBeat.ISANA);
        }
        if (f.boneLore()) {
            st.markDone(OpeningBeat.BONES);
        }
        if (f.whaleLore()) {
            st.markDone(OpeningBeat.WHALE);
        }
        if (f.footprintLore()) {
            st.markDone(OpeningBeat.FOOTPRINTS);
        }
        if (f.shadowLore()) {
            st.markDone(OpeningBeat.SHADOW);
        }

        // B1 旅の野営地: 帳面を拾う。野営地がない世界や、野営地へ行かない人にも、帳面は必ず届く
        if (!st.done(OpeningBeat.CAMP)) {
            boolean picked = f.notebookDist() >= 0 && f.notebookDist() <= PICK_DISTANCE && awakeFor > 100;
            boolean noCamp = !f.campBuilt() && awakeFor >= NO_CAMP_WAIT;
            boolean leftCamp = f.campBuilt() && f.campDist() > LEAVE_CAMP && awakeFor >= LEAVE_AFTER;
            boolean late = awakeFor >= CAMP_FALLBACK;
            if (f.hasBook()) {
                st.markDone(OpeningBeat.CAMP);
            } else if (picked || noCamp || leftCamp || late) {
                fx.giveNotebook();
                st.markDone(OpeningBeat.CAMP);
                if (picked) {
                    st.campLoreAt = now + CAMP_LORE_DELAY;
                }
            }
        }
        // 野営地の頁 (prologue/camp): 帳面を持って野営地の中にいる間に 3 秒たてば記す
        if (st.done(OpeningBeat.CAMP) && !f.campLore() && f.campBuilt() && f.campDist() >= 0 && f.campDist() <= CAMP_NEAR && env.awake) {
            if (st.campLoreAt < 0) {
                st.campLoreAt = now + CAMP_LORE_DELAY;
            }
            if (now >= st.campLoreAt) {
                fx.campLore();
                st.campLoreAt = -1;
            }
        } else if (f.campLore()) {
            st.campLoreAt = -1;
        } else if (st.campLoreAt >= 0 && now >= st.campLoreAt && f.campDist() > CAMP_NEAR) {
            // 離れてしまった: 近くへ戻ったときにもう一度数える
            st.campLoreAt = -1;
        }

        // B2 道しるべ: 目的が「煙を追う」の間、宿りの鐘を 30 秒おきに鳴らす。着いた (または語り部と会った) ら済み
        if (st.done(OpeningBeat.CAMP) && !st.done(OpeningBeat.TRAIL)) {
            boolean arrived = f.metStoryteller() || (f.shelterDist() >= 0 && f.shelterDist() <= SHELTER_ARRIVED);
            String stage = f.mainStage();
            boolean following = stage.isEmpty() || stage.equals("follow_smoke");
            if (arrived) {
                st.markDone(OpeningBeat.TRAIL);
            } else if (following && f.shelterDist() >= 0 && f.shelterDist() <= BELL_HEARD && env.awake && (st.bellAt < 0 || now - st.bellAt >= BELL_EVERY)) {
                fx.ringBell();
                st.bellAt = now;
            }
        }

        // B3 巨大な足跡: くぼみの 24 ブロック以内に初めて入ったとき (起きている人だけ)
        if (!st.done(OpeningBeat.FOOTPRINTS) && f.footprintNear() && env.awake) {
            fx.footprintsLore();
            st.markDone(OpeningBeat.FOOTPRINTS);
        }

        // B8 巨神の骨: 石板に近づいたとき。寄り道なので誘導は目的の帯だけ
        if (!st.done(OpeningBeat.BONES) && f.slabNear() && env.awake) {
            fx.bonesLore();
            st.markDone(OpeningBeat.BONES);
        }

        shadow(st, env, f, fx, now);
        nightBeats(st, env, f, fx, now);

        // 判断 17-5: 日 3 の朝にも宿りに着いていない人の帳面に、余白の一行
        if (!f.marginLore() && !st.done(OpeningBeat.TRAIL) && st.done(OpeningBeat.CAMP) && env.day() >= st.awakeDay() + 2 && env.tod() <= 6000
                && (f.mainStage().isEmpty() || f.mainStage().equals("follow_smoke"))) {
            fx.marginPage();
        }
    }

    // B4 最初の影 (空の影そのものは SkyEvents が必ず起こす。ここは人ごとの拍子)
    private static void shadow(OpeningState st, Env env, Facts f, Fx fx, long now) {
        // 落とす予定の破れた頁
        if (st.pageAt >= 0 && now >= st.pageAt) {
            st.pageAt = -1;
            fx.dropShadowPage();
            st.pageReadAt = now + PAGE_AUTO_READ;
        }
        if (st.pageReadAt >= 0) {
            if (f.shadowLore()) {
                st.pageReadAt = -1;
            } else if (now >= st.pageReadAt) {
                st.pageReadAt = -1;
                fx.readShadowPage();
            }
        }
        if (st.done(OpeningBeat.SHADOW)) {
            return;
        }
        if (!env.horror) {
            // 影を起こさない設定: 拍子は済んだものとして先へ (誘導は残る)
            st.markDone(OpeningBeat.SHADOW);
            return;
        }
        if (f.passEndedNow() && st.pageAt < 0 && st.pageReadAt < 0) {
            st.markDone(OpeningBeat.SHADOW);
            // 影を見て記された人 (書のない人) 以外 (書を持っている人・屋内で見なかった人) には、空から破れた頁が落ちる (17 の 判断 2 A)。
            // 書のない人には従来どおり書が落ちる (LoreEvents)
            st.pageAt = now + PAGE_DELAY;
            return;
        }
        // あとから入った人 (最初の影のあとに目覚めた): 翌日の窓 (一日の時刻 1000〜9000) に、その人のために影を起こす (17 の 判断 6 A)
        if (f.passCount() > 0 && f.firstPassTick() >= 0 && f.firstPassTick() < st.awakeAt && env.day() > st.awakeDay() && env.tod() >= 1000
                && env.tod() <= 9000 && env.skyVisible && env.calm() && st.cueUntil < now) {
            fx.startPersonalShadow();
        }
    }

    // B6 足音 と B9 歌
    private static void nightBeats(OpeningState st, Env env, Facts f, Fx fx, long now) {
        // 頁 (予定の時刻になれば、夜でなくても)
        if (st.stepsLoreAt >= 0 && now >= st.stepsLoreAt) {
            st.stepsLoreAt = -1;
            fx.footstepsLore();
        }
        if (st.songLoreAt >= 0 && now >= st.songLoreAt) {
            st.songLoreAt = -1;
            fx.songLore();
        }
        if (!env.horror) {
            // ホラーの強さ 0: 起こさずに済んだことにして先へ進める (頁は記さない)
            st.markDone(OpeningBeat.FOOTSTEPS);
            st.markDone(OpeningBeat.SONG);
            return;
        }
        long night = nightId(env.dayTime);
        if (night < 0 || st.cueUntil > now) {
            return;
        }
        // 足音
        if (footstepsEligible(st, env)) {
            if (st.stepsNight != night) {
                st.stepsNight = night;
                st.stepsAt = now + fx.random(STEPS_MIN, STEPS_MAX);
            } else if (st.stepsAt >= 0 && now >= st.stepsAt) {
                if (env.calm() && exposed(env)) {
                    st.markDone(OpeningBeat.FOOTSTEPS);
                    st.stepsDoneNight = night;
                    st.stepsAt = -1;
                    st.cueUntil = now + STEPS_TOTAL;
                    st.stepsLoreAt = now + STEPS_LORE;
                    fx.startFootsteps(now);
                } else {
                    // 戦闘中・寝ている・水の中: 後ろへずらす (寝て過ごせば夜が終わり、次の夜にもう一度予定を立てる)
                    st.stepsAt = now + POSTPONE;
                }
            }
            return;
        }
        // 歌
        if (songEligible(st, env, night)) {
            if (st.songNight != night) {
                st.songNight = night;
                st.songAt = now + fx.random(SONG_MIN, SONG_MAX);
            } else if (st.songAt >= 0 && now >= st.songAt) {
                if (env.calm() && env.skyVisible && songSkyReady(env)) {
                    st.markDone(OpeningBeat.SONG);
                    st.songDoneNight = night;
                    st.songAt = -1;
                    st.cueUntil = now + SONG_TOTAL;
                    st.songLoreAt = now + SONG_LORE;
                    fx.startSong(now);
                } else {
                    st.songAt = now + POSTPONE;
                }
            }
        }
    }

    // ---------------------------------------------------------------- 岩鯨の門 (17 の 3.9節)
    // 詰み止め: 総 tick が 4 日分を過ぎたら (5 日目)、拍子を問わず岩鯨は出てよい
    public static final long WHALE_FALLBACK = 4 * DAY;

    // 岩鯨を自然に出してよいか (ほかの条件 TitanEvents.whaleMayAppear に重ねる): 拍子を済ませた人がいるか、5 日目に入ったか
    public static boolean whaleGateOpen(long now, boolean anyoneReady) {
        return anyoneReady || now >= WHALE_FALLBACK;
    }

    // その人が「岩鯨を迎えられる」か: 最初の影 (B4)、歌 (B9)、イサナ (B7) を済ませている
    public static boolean whaleReady(OpeningState st) {
        return st.legacy() || (st.done(OpeningBeat.SHADOW) && st.done(OpeningBeat.SONG) && st.done(OpeningBeat.ISANA));
    }
}
