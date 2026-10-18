/* Copyright (C) 2026 Copperbench contributors; SPDX-License-Identifier: GPL-3.0-only */
package dev.copperbench.installer;

import javax.net.ssl.SSLException;
import javax.swing.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.Locale;

/** Shared user-facing update messages for the desktop host and the independent installer JVM. */
public final class UpdateUiMessages {
    private UpdateUiMessages() {}
    public static String text(String language, String chinese, String english) {
        return "zh".equals(language) ? chinese : english;
    }
    public static void configureLocale(String language) {
        Locale locale = "zh".equals(language) ? Locale.SIMPLIFIED_CHINESE : Locale.US;
        Locale.setDefault(locale); JComponent.setDefaultLocale(locale); UIManager.getDefaults().setDefaultLocale(locale);
        UIManager.getLookAndFeelDefaults().setDefaultLocale(locale);
        UIManager.put("OptionPane.okButtonText", text(language, "确定", "OK"));
        UIManager.put("OptionPane.cancelButtonText", text(language, "取消", "Cancel"));
        UIManager.put("OptionPane.yesButtonText", text(language, "是", "Yes"));
        UIManager.put("OptionPane.noButtonText", text(language, "否", "No"));
    }
    public static String phase(String phase, String language) {
        return switch (phase) {
            case "copy" -> text(language, "正在复制并校验更新…", "Copying and verifying the update…");
            case "activate" -> text(language, "正在完成安装…", "Finishing installation…");
            case "cleanup" -> text(language, "正在清理旧版程序…", "Removing previous application files…");
            case "authorize" -> text(language, "正在等待系统授权…", "Waiting for system authorization…");
            case "package" -> text(language, "正在准备系统升级包…", "Preparing the system upgrade package…");
            case "complete" -> text(language, "更新已安装，正在重新启动…", "Update installed. Restarting…");
            default -> text(language, "正在准备更新…", "Preparing the update…");
        };
    }
    public static String errorSummary(Throwable error, String language) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            String message = String.valueOf(cause.getMessage()).toLowerCase(Locale.ROOT);
            if (cause instanceof SocketTimeoutException || cause instanceof java.net.http.HttpTimeoutException)
                return text(language, "连接更新服务器超时，请检查网络后重试。", "The update connection timed out. Check your network and retry.");
            if (cause instanceof UnknownHostException || cause instanceof ConnectException)
                return text(language, "无法连接更新服务器，请检查网络或代理设置后重试。", "Could not connect to the update server. Check your network or proxy and retry.");
            if (cause instanceof SSLException)
                return text(language, "无法验证更新服务器的安全连接，请检查系统时间和网络设置。", "Could not verify the secure update connection. Check your system clock and network settings.");
            if (cause instanceof AccessDeniedException)
                return text(language, "无法写入更新文件，请检查目录权限并关闭占用文件的程序。", "Could not write update files. Check folder permissions and close programs using these files.");
            if (cause instanceof NoSuchFileException)
                return text(language, "更新所需的文件缺失，请重新下载更新。", "A required update file is missing. Download the update again.");
            if (message.contains("does not contain an in-app update package"))
                return text(language, "此版本尚未提供应用内更新包，请稍后重新检查。", "This version does not have an in-app update package yet. Check again later.");
            if (message.contains("installed copperbench build") || message.contains("graphical installer before"))
                return text(language, "当前程序不是可升级的安装，请先使用图形安装器安装 Copperbench。", "Use the graphical installer to install Copperbench before applying in-app updates.");
            if (message.contains("space") && (message.contains("not enough") || message.contains("no space")))
                return text(language, "磁盘空间不足，请释放空间后重试。", "There is not enough disk space. Free up space and retry.");
            if (message.contains("http 403") || message.contains("http 429"))
                return text(language, "更新服务器暂时限制请求，请稍后重试。", "The update server is temporarily limiting requests. Try again later.");
            if (message.contains("http ") || message.contains("redirect"))
                return text(language, "更新资源暂时不可用，请稍后重新检查。", "Update resources are temporarily unavailable. Check again later.");
            if (message.contains("files changed while preparing"))
                return text(language, "程序文件在准备更新时发生变化，请重新检查更新。", "Installed files changed while preparing the update. Check for updates again.");
            if (message.contains("checksum") || message.contains("verification failed") || message.contains("incomplete")
                    || message.contains("declared size") || message.contains("invalid update") || message.contains("update inventory")
                    || message.contains("missing an update component") || message.contains("missing package")
                    || message.contains("unsafe") || message.contains("unexpected download") || message.contains("unexpected update")
                    || message.contains("different version or platform") || message.contains("installation file list"))
                return text(language, "更新包不完整或校验失败，请重新下载更新。", "The update package is incomplete or failed verification. Download it again.");
            if (message.contains("workspaces outside"))
                return text(language, "请先将工作区移出程序安装目录，再重试升级。", "Move workspaces outside the application folder before retrying the upgrade.");
            if (message.contains("close copperbench") || message.contains("still running"))
                return text(language, "请保存工作并关闭 Copperbench 及其 Minecraft 客户端后重试。", "Save your work and close Copperbench and its Minecraft clients before retrying.");
            if (message.contains("authorization") || message.contains("system installation failed"))
                return text(language, "系统授权或安装未完成，请重试并确认系统提示。", "System authorization or installation did not complete. Retry and confirm the system prompt.");
        }
        return text(language, "更新未完成，请查看错误详情后重试。", "The update did not complete. Review the error details and retry.");
    }
    public static String details(Throwable error) {
        StringWriter writer = new StringWriter(); error.printStackTrace(new PrintWriter(writer)); return writer.toString();
    }
}
