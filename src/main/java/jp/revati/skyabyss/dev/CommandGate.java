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

// 試遊の jar (製品版) で見せるコマンドを絞る (2026-10-28 の試遊「コマンドごちゃごちゃなってるから、いらないやつ整理して消して。play はいるかも、playtest はいらない」)。
// /skyabyss の下は play と inspect (控え帳の「見る」の行き先) だけ。開発用の入口 (旧 playtest・試遊・titanmemory・背のレイドの直入り・hud・目の型) は隠す。
// 消さずに隠すだけ: 中から流す呼び出し (Playtest.run の titan board など、inside() の間) は今までどおり通る。開発環境と自己テストでは全部見える
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class CommandGate {
    private CommandGate() {
    }

    static final Set<String> HIDDEN_ROOTS = Set.of("titanmemory", "skywhaleraid", "skytideraid", "skystormraid", "skyemberraid", "skyabyss_hud", "eyevariant",
            "story");
    static final Set<String> SKYABYSS_SHOWN = Set.of("play", "inspect");

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
        if (!active()) {
            return;
        }
        SkyAbyss.LOGGER.info("command gate: {} developer commands hidden", apply(event.getDispatcher()));
    }

    // 隠す (自己テストは別の dispatcher で確かめる)。隠した節の数
    public static int apply(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher) {
        int hidden = 0;
        for (CommandNode<CommandSourceStack> root : dispatcher.getRoot().getChildren()) {
            if (HIDDEN_ROOTS.contains(root.getName())) {
                hidden += hide(root) ? 1 : 0;
            } else if (root.getName().equals(SkyAbyss.MODID)) {
                for (CommandNode<CommandSourceStack> child : root.getChildren()) {
                    if (!SKYABYSS_SHOWN.contains(child.getName())) {
                        hidden += hide(child) ? 1 : 0;
                    }
                }
            }
        }
        return hidden;
    }

    private static Field requirement;

    @SuppressWarnings("unchecked")
    private static boolean hide(CommandNode<CommandSourceStack> node) {
        try {
            if (requirement == null) {
                requirement = CommandNode.class.getDeclaredField("requirement");
                requirement.setAccessible(true);
            }
            Predicate<CommandSourceStack> was = (Predicate<CommandSourceStack>) requirement.get(node);
            requirement.set(node, (Predicate<CommandSourceStack>) s -> INSIDE.get() > 0 && was.test(s));
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            SkyAbyss.LOGGER.warn("command gate: could not hide /{}", node.getName(), e);
            return false;
        }
    }
}
