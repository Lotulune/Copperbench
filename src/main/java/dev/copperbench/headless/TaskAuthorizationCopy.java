package dev.copperbench.headless;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/** Locale-specific presentation; capability IDs, paths and protocol codes remain unchanged. */
final class TaskAuthorizationCopy {
    private TaskAuthorizationCopy() {}

    static String text(String locale, String chinese, String english) { return "zh".equals(locale) ? chinese : english; }
    static String title(String locale) { return text(locale, "Copperbench 任务授权", "Copperbench task authorization"); }
    static String serverTitle(String locale) { return text(locale, "Copperbench 服务器授权", "Copperbench server authorization"); }
    static String allow(String locale) { return text(locale, "允许", "Allow"); }
    static String cancel(String locale) { return text(locale, "取消", "Cancel"); }

    static String capability(String locale, String capability) {
        String label = switch (capability) {
            case "create" -> text(locale, "创建工作区", "Create workspaces");
            case "edit" -> text(locale, "修改内容", "Edit content");
            case "build" -> text(locale, "生成与构建", "Generate and build");
            case "test" -> text(locale, "校验与测试", "Validate and test");
            case "run_client" -> text(locale, "运行客户端", "Run clients");
            case "run_server" -> text(locale, "运行专用服务器", "Run dedicated servers");
            case "restore" -> text(locale, "恢复历史版本", "Restore history");
            default -> capability;
        };
        return label.equals(capability) ? capability : label + " (" + capability + ")";
    }

    static String body(String locale, String label, Path root, List<String> capabilities, long ttl) {
        return text(locale, "任务：", "Task: ") + label
                + text(locale, "\n授权目录（包含子目录）：", "\nRoot (including subdirectories): ") + root
                + text(locale, "\n允许的操作：", "\nAllowed operations: ")
                + capabilities.stream().map(value -> capability(locale, value)).collect(Collectors.joining(text(locale, "、", ", ")))
                + text(locale, "\n有效期：", "\nLifetime: ") + ttl + text(locale, " 秒", " seconds")
                + text(locale,
                    "\n\n允许此任务通过 CLI 和 MCP 执行上述操作，直至授权到期或被撤销？"
                    + "\n仅授权所列目录和操作，不扩大 MCP 连接本身的权限。"
                    + "\n可在“AI 与 MCP”的任务授权中撤销，或使用 revoke-authorization 命令。"
                    + "\n选择“取消”不会授予权限。",
                    "\n\nAllow this task to perform the listed operations through CLI and MCP until the authorization expires or is revoked?"
                    + "\nOnly the listed root and operations are authorized. This does not expand the MCP connection permissions."
                    + "\nRevoke access in AI & MCP > Task authorizations, or with the revoke-authorization command."
                    + "\nCancel grants no permissions.");
    }

    static String eula(String locale) {
        return text(locale,
                "运行专用服务器需要接受 Minecraft 最终用户许可协议（EULA）：https://aka.ms/MinecraftEULA\n是否接受该协议，并将其用于此任务创建的服务器？取消不会授予服务器权限。",
                "Running a dedicated server requires accepting the Minecraft End User License Agreement (EULA): https://aka.ms/MinecraftEULA\nAccept this agreement for servers created by this task? Cancel grants no server permissions.");
    }

    static String error(String locale, String code) {
        return switch (code) {
            case "TASK_AUTHORIZATION_USER_ONLY" -> text(locale, "任务尚未获得本机用户授权。请在桌面应用中确认后重试。", "The task has not been approved by the local user. Approve it in the desktop app before retrying.");
            case "SERVER_EULA_APPROVAL_REQUIRED" -> text(locale, "尚未接受 Minecraft EULA，未授予服务器权限。", "The Minecraft EULA was not accepted. Server permissions were not granted.");
            case "TASK_AUTHORIZATION_SCOPE_MISMATCH" -> text(locale, "此授权属于其他任务目录。", "This authorization belongs to another task root.");
            default -> text(locale, "授权操作未完成。请查看详细原因后重试。", "The authorization operation did not complete. Review the details before retrying.");
        };
    }
}
