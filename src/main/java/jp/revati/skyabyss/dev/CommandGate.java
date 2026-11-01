package jp.revati.skyabyss.dev;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.function.Predicate;

import com.mojang.brigadier.tree.CommandNode;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// コマンドの権限の振り分け (2026-11-02 の声「正式版なので、コマンド整理して管理者用も設定して」。前は 2026-10-28 の「いらないやつ整理して消して」)。
//   /skyabyss help      誰でも (遊ぶ人のコマンドの一覧。dev/play/HelpCommands)
//   /skyabyss admin …   管理者 (権限の段は設定 adminPermissionLevel。既定 2 = OP。dev/play/PlayCommands)
//   /skyabyss のほか    開発用。製品版では隠す (開発環境と自己テストでは権限 2 で見える)
//   隠す入口 (HIDDEN_ROOTS) 旧い開発の入口 (titanmemory・背のレイドの直入り・hud・目の型・story)。製品版では隠す
// /skyabyss の根は誰でも通し、根に付いていた権限 (どの登録も 2) を子の節へ移す。遊ぶ人のコマンド (/party・/guild・/trade・/skyrecord・
// /growth・/skill・/skyabyss_<名前>) はもともと誰でも使える別の根なので触らない。
// 消さずに隠すだけ: 中から流す呼び出し (Playtest.run の titan board など、inside() の間) は今までどおり通る
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class CommandGate {
    private CommandGate() {
    }

    static final Set<String> HIDDEN_ROOTS = Set.of("titanmemory", "skywhaleraid", "skytideraid", "skystormraid", "skyemberraid", "skyabyss_hud", "eyevariant",
            "story");
    // /skyabyss の下で誰でも使える節
    static final Set<String> PLAYER_CHILDREN = Set.of(jp.revati.skyabyss.dev.play.HelpCommands.HELP);

    private static final ThreadLocal<Integer> INSIDE = ThreadLocal.withInitial(() -> 0);

    // 中から流すコマンドの間だけ、隠した入口も通す
    public static <T> T inside(java.util.function.Supplier<T> run) {
        INSIDE.set(INSIDE.get() + 1);
        try {
            return run.get();
        } finally {
            INSIDE.set(INSIDE.get() - 1);
        }
    }

    public static boolean active() {
        return FMLEnvironment.production && !jp.revati.skyabyss.SelfTestRunner.enabled();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onRegisterCommands(RegisterCommandsEvent event) {
        int hidden = apply(event.getDispatcher(), active());
        if (active()) {
            SkyAbyss.LOGGER.info("command gate: {} developer commands hidden", hidden);
        }
    }

    // 振り分ける (自己テストは別の dispatcher で確かめる)。production = 開発用を隠す。隠した節の数
    public static int apply(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher, boolean production) {
        int hidden = 0;
        for (CommandNode<CommandSourceStack> root : dispatcher.getRoot().getChildren()) {
            if (production && HIDDEN_ROOTS.contains(root.getName())) {
                hidden += hide(root) ? 1 : 0;
            } else if (root.getName().equals(SkyAbyss.MODID)) {
                // 根の権限はどの登録も 2 (help の登録が先に来ると根が「誰でも」になるので、読まずに 2 と決める)
                Predicate<CommandSourceStack> was = s -> s.hasPermission(2);
                if (!set(root, s -> true)) {
                    continue;
                }
                for (CommandNode<CommandSourceStack> child : root.getChildren()) {
                    Predicate<CommandSourceStack> own = requirement(child);
                    if (own == null || PLAYER_CHILDREN.contains(child.getName())) {
                        continue;
                    }
                    if (child.getName().equals(jp.revati.skyabyss.dev.play.PlayCommands.ADMIN)) {
                        set(child, s -> s.hasPermission(jp.revati.skyabyss.Config.adminLevel()) && own.test(s));
                    } else if (production) {
                        hidden += set(child, s -> INSIDE.get() > 0 && was.test(s) && own.test(s)) ? 1 : 0;
                    } else {
                        set(child, s -> was.test(s) && own.test(s));
                    }
                }
            }
        }
        return hidden;
    }

    private static Field requirement;

    @SuppressWarnings("unchecked")
    @org.jetbrains.annotations.Nullable
    private static Predicate<CommandSourceStack> requirement(CommandNode<CommandSourceStack> node) {
        try {
            if (requirement == null) {
                requirement = CommandNode.class.getDeclaredField("requirement");
                requirement.setAccessible(true);
            }
            return (Predicate<CommandSourceStack>) requirement.get(node);
        } catch (ReflectiveOperationException | RuntimeException e) {
            SkyAbyss.LOGGER.warn("command gate: could not read /{}", node.getName(), e);
            return null;
        }
    }

    private static boolean set(CommandNode<CommandSourceStack> node, Predicate<CommandSourceStack> to) {
        try {
            requirement(node);
            requirement.set(node, to);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            SkyAbyss.LOGGER.warn("command gate: could not change /{}", node.getName(), e);
            return false;
        }
    }

    // 中から流すときだけ通す
    private static boolean hide(CommandNode<CommandSourceStack> node) {
        Predicate<CommandSourceStack> was = requirement(node);
        return was != null && set(node, s -> INSIDE.get() > 0 && was.test(s));
    }
}
