package com.spacewaltz.decrees.council;

import com.spacewaltz.decrees.decree.DecreeStatus;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

/**
 * Centralised chat helper so all messages share one style.
 */
public final class Messenger {

    private Messenger() {
    }

    public static String prefix() {
        String name = CouncilConfig.get().councilName;
        if (name == null || name.isBlank()) {
            return "§6[Decrees]";
        }
        return "§6[" + name + "]";
    }

    public static void info(ServerCommandSource src, String msg) {
        src.sendMessage(Text.literal(prefix() + " " + msg));
    }

    public static void error(ServerCommandSource src, String msg) {
        src.sendError(Text.literal(prefix() + " §c" + msg));
    }

    /**
     * For secondary lines in a block (no prefix).
     */
    public static void line(ServerCommandSource src, String msg) {
        src.sendMessage(Text.literal(msg));
    }

    public static String colorStatus(DecreeStatus status) {
        if (status == null) {
            return "§7[UNKNOWN]";
        }
        return switch (status) {
            case DRAFT    -> "§8[DRAFT]";
            case VOTING   -> "§e[VOTING]";
            case ENACTED  -> "§a[ENACTED]";
            case REJECTED -> "§c[REJECTED]";
            case CANCELLED-> "§6[CANCELLED]";
        };
    }
}
