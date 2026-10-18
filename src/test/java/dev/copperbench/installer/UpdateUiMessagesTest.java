package dev.copperbench.installer;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;

class UpdateUiMessagesTest {
    @Test void chineseFailuresDescribeTheActionWithoutExposingRawEnglishOnTheMainSurface() {
        assertTrue(UpdateUiMessages.errorSummary(new UnknownHostException("api.github.com"), "zh").contains("检查网络"));
        assertTrue(UpdateUiMessages.errorSummary(new IOException("This release does not contain an in-app update package yet."), "zh").contains("尚未提供"));
        assertTrue(UpdateUiMessages.errorSummary(new IOException("Update server returned HTTP 429"), "zh").contains("限制请求"));
        assertTrue(UpdateUiMessages.errorSummary(new IOException("Update file checksum mismatch: payload.jar"), "zh").contains("重新下载"));
        assertTrue(UpdateUiMessages.errorSummary(new IOException("Not enough space to prepare the update"), "zh").contains("空间不足"));
        IOException unknown = new IOException("Raw English backend failure");
        assertEquals("更新未完成，请查看错误详情后重试。", UpdateUiMessages.errorSummary(unknown, "zh"));
        assertTrue(UpdateUiMessages.details(unknown).contains("Raw English backend failure"));
    }
    @Test void nestedNetworkErrorsAndEnglishRemainActionable() {
        var wrapped = new java.util.concurrent.ExecutionException(new java.net.SocketTimeoutException("Read timed out"));
        assertTrue(UpdateUiMessages.errorSummary(wrapped, "zh").contains("超时"));
        assertTrue(UpdateUiMessages.errorSummary(wrapped, "en").contains("timed out"));
        assertEquals("正在清理旧版程序…", UpdateUiMessages.phase("cleanup", "zh"));
        assertEquals("Update installed. Restarting…", UpdateUiMessages.phase("complete", "en"));
    }
    @Test void independentChineseWorkerOverridesEnglishJvmDialogDefaults() throws Exception {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        Process child = new ProcessBuilder(java.nio.file.Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-Djava.awt.headless=true", "-Duser.language=en", "-Duser.country=US", "-cp",
                System.getProperty("copperbench.test.runtimeClasspath"), LocaleProbe.class.getName()).redirectErrorStream(true).start();
        try {
            assertTrue(child.waitFor(20, java.util.concurrent.TimeUnit.SECONDS), "Locale probe timed out");
            String output = new String(child.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(0, child.exitValue(), output); assertTrue(output.contains("locale-probe=passed"), output);
        } finally { if (child.isAlive()) child.destroyForcibly(); }
    }
    public static final class LocaleProbe {
        public static void main(String[] args) {
            if (!Locale.getDefault().getLanguage().equals("en")) throw new AssertionError("Expected an English JVM");
            UpdateUiMessages.configureLocale("zh");
            if (!"确定".equals(UIManager.getString("OptionPane.okButtonText"))
                    || !"取消".equals(UIManager.getString("OptionPane.cancelButtonText"))
                    || !Locale.SIMPLIFIED_CHINESE.equals(JComponent.getDefaultLocale())
                    || !Locale.SIMPLIFIED_CHINESE.equals(UIManager.getLookAndFeelDefaults().getDefaultLocale())) throw new AssertionError("Chinese defaults were not applied");
            UpdateUiMessages.configureLocale("en");
            if (!"OK".equals(UIManager.getString("OptionPane.okButtonText"))) throw new AssertionError("English defaults were not restored");
            System.out.println("locale-probe=passed");
        }
    }
}
