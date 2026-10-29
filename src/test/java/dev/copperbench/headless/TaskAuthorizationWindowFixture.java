package dev.copperbench.headless;

import java.nio.file.Path;
import java.util.List;

/** Visual acceptance fixture only: no authorization store or workspace is opened. */
public final class TaskAuthorizationWindowFixture {
    public static void main(String[] args) {
        String locale = args.length > 0 ? args[0] : "en";
        String title = "Copperbench UIUX fixture " + locale + " 2026-10-16";
        String body = TaskAuthorizationCopy.body(locale, "UIUX acceptance fixture",
                Path.of("output", "uiux-optimization-2026-10-16", "native-window-fixture").toAbsolutePath(),
                List.of("create", "edit", "build", "test", "run_client", "run_server", "restore"), 7200);
        boolean result = LocalApprovalWindow.confirm(title, body, locale);
        System.out.println("fixture=" + locale + "; confirmed=" + result + "; authorizationIssued=false");
    }
}
