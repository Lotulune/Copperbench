package dev.copperbench.headless;

import dev.copperbench.automation.security.TaskAuthorizationStore;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class TaskAuthorizationCopyTest {
    @Test void englishCoversEveryPermissionAndKeepsMachineValues() {
        Path root = Path.of("approval-fixture", "workspace").toAbsolutePath();
        String body = TaskAuthorizationCopy.body("en", "Fixture task", root, TaskAuthorizationStore.DEFAULT_CAPABILITIES, 7200);
        assertTrue(body.contains(root.toString()));
        assertTrue(body.contains("7200 seconds"));
        assertTrue(body.contains("revoke-authorization"));
        assertTrue(body.contains("Cancel grants no permissions"));
        for (String capability : TaskAuthorizationStore.CAPABILITIES) {
            String copy = TaskAuthorizationCopy.capability("en", capability);
            assertTrue(copy.endsWith("(" + capability + ")"));
            assertFalse(copy.matches("(?s).*\\p{IsHan}.*"));
        }
        String all = body + TaskAuthorizationCopy.title("en") + TaskAuthorizationCopy.serverTitle("en")
                + TaskAuthorizationCopy.eula("en") + TaskAuthorizationCopy.allow("en") + TaskAuthorizationCopy.cancel("en")
                + TaskAuthorizationCopy.error("en", "SERVER_EULA_APPROVAL_REQUIRED");
        assertFalse(all.matches("(?s).*\\p{IsHan}.*"));
        assertTrue(all.contains("https://aka.ms/MinecraftEULA"));
    }

    @Test void chineseLocalizesTheExplanationWithoutTranslatingThePathOrIds() {
        Path root = Path.of("fixture-English-path").toAbsolutePath();
        String body = TaskAuthorizationCopy.body("zh", "user-authored-label", root, java.util.List.of("run_server", "restore"), 60);
        assertTrue(body.contains(root.toString()));
        assertTrue(body.contains("user-authored-label"));
        assertTrue(body.contains("运行专用服务器 (run_server)"));
        assertTrue(body.contains("恢复历史版本 (restore)"));
        assertTrue(body.contains("60 秒"));
        assertTrue(body.contains("撤销"));
        assertEquals("取消", TaskAuthorizationCopy.cancel("zh"));
        assertTrue(TaskAuthorizationCopy.eula("zh").contains("https://aka.ms/MinecraftEULA"));
        assertTrue(TaskAuthorizationCopy.error("zh", "TASK_AUTHORIZATION_USER_ONLY").contains("本机用户"));
    }
}
