/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.ui.multiplayer;

import com.google.gson.JsonParseException;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.event.Event;
import org.jackhuang.hmcl.event.EventManager;
import org.jackhuang.hmcl.setting.ConfigHolder;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.*;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.HttpRequest;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.CommandBuilder;
import org.jackhuang.hmcl.util.platform.ManagedProcess;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.logging.Level;

import static org.jackhuang.hmcl.setting.ConfigHolder.globalConfig;
import static org.jackhuang.hmcl.util.Lang.*;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.io.ChecksumMismatchException.verifyChecksum;

/**
 * Easytier Management.
 */
public final class MultiplayerManager {
    private static final EasyTierConfig EASYTIER_CONFIG;
    private static final String EASYTIER_VERSION = "v2.2.4";
    private static final Path EASYTIER_DOWNLOADS = Metadata.HMCL_CURRENT_DIRECTORY.resolve("libraries").resolve("easytier").resolve(EASYTIER_VERSION);

    private static final String EASYTIER_UPDATE_URL = "";
    private static final String EASYTIER_UPDATE_MIRROR_URL = "";
    private static final Path EASYTIER_TEMP_CONFIG_PATH = Metadata.HMCL_CURRENT_DIRECTORY.resolve("hiper.yml");
    private static final Path EASYTIER_CONFIG_DIR = Metadata.HMCL_CURRENT_DIRECTORY.resolve("hiper-config");
    public static final Path EASYTIER_PATH = getEasytierLocalDirectory().resolve(getEasytierFileName());
    private static final String REMOTE_ADDRESS = "127.0.0.1";
    private static final String LOCAL_ADDRESS = "0.0.0.0";

    private static final Map<Architecture, String> archMap = mapOf(
            pair(Architecture.ARM32, "armhf"),
            pair(Architecture.ARM64, "aarch64"), 
            pair(Architecture.X86_64, "x86_64"),
            pair(Architecture.MIPS, "mips"),
            pair(Architecture.MIPSEL, "mipsel")
    );

    private static final Map<OperatingSystem, String> osMap = mapOf(
            pair(OperatingSystem.LINUX, "linux"),
            pair(OperatingSystem.WINDOWS, "windows"), 
            pair(OperatingSystem.OSX, "macos"),
            pair(OperatingSystem.FREEBSD, "freebsd")
    );

    private static final String EASYTIER_TARGET_NAME = String.format("%s-%s",
            osMap.getOrDefault(OperatingSystem.CURRENT_OS, "windows"),
            archMap.getOrDefault(Architecture.SYSTEM_ARCH, "amd64"));

    private static final String GSUDO_VERSION = "1.7.1";
    private static final String GSUDO_TARGET_ARCH = Architecture.SYSTEM_ARCH == Architecture.X86_64 ? "amd64" : "x86";
    private static final String GSUDO_FILE_NAME = "gsudo.exe";
    private static final String GSUDO_DOWNLOAD_URL = "https://gitcode.net/glavo/gsudo-release/-/raw/75c952ea3afe8792b0db4fe9bab87d41b21e5895/" + GSUDO_TARGET_ARCH + "/" + GSUDO_FILE_NAME;
    private static final Path GSUDO_LOCAL_FILE = Metadata.HMCL_CURRENT_DIRECTORY.resolve("libraries").resolve("gsudo").resolve("gsudo").resolve(GSUDO_VERSION).resolve(GSUDO_TARGET_ARCH).resolve(GSUDO_FILE_NAME);
    private static final boolean USE_GSUDO;

    static final boolean IS_ADMINISTRATOR;

    static final BooleanBinding tokenInvalid = Bindings.createBooleanBinding(
            () -> {
                String token = globalConfig().multiplayerTokenProperty().getValue();
                return token == null || token.isEmpty() || !StringUtils.isAlphabeticOrNumber(token);
            },
            globalConfig().multiplayerTokenProperty());

    private static final DateFormat EASYTIER_VALID_TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    static {
        boolean isAdministrator = false;
        if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
            try {
                Process process = Runtime.getRuntime().exec(new String[]{"net.exe", "session"});
                if (!process.waitFor(1, TimeUnit.SECONDS)) {
                    process.destroy();
                } else {
                    isAdministrator = process.exitValue() == 0;
                }
            } catch (Throwable ignored) {
            }
            USE_GSUDO = !isAdministrator && OperatingSystem.SYSTEM_BUILD_NUMBER >= 10000;
        } else {
            isAdministrator = "root".equals(System.getProperty("user.name"));
            USE_GSUDO = false;
        }
        IS_ADMINISTRATOR = isAdministrator;

        try {
            EASYTIER_CONFIG = JsonUtils.GSON.fromJson(FileUtils.readText(MultiplayerManager.class.getResourceAsStream("/assets/easytier-config.json")), EasyTierConfig.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load EasyTier configuration", e);
        }
    }

    private static EasyTierPlatformInfo getCurrentPlatformInfo() {
        String osKey = osMap.getOrDefault(OperatingSystem.CURRENT_OS, "windows");
        String archKey = archMap.getOrDefault(Architecture.SYSTEM_ARCH, "x86_64");
        
        EasyTierPlatform platform = EASYTIER_CONFIG.platforms.get(osKey);
        if (platform == null || !platform.architectures.containsKey(archKey)) {
            throw new EasytierUnsupportedPlatformException();
        }
        
        return platform.architectures.get(archKey);
    }

    private static CompletableFuture<Map<String, String>> HASH;

    private MultiplayerManager() {
    }

    public static Task<Void> downloadEasytier() {
        return Task.runAsync(() -> {
            EasyTierPlatformInfo info = getCurrentPlatformInfo();
            Path zipFile = EASYTIER_DOWNLOADS.resolve(info.filename);
            Path extractDir = EASYTIER_DOWNLOADS.resolve("extracted");

            // Download and verify zip
            if (!Files.exists(zipFile) || !verifyZipChecksum(zipFile, info.sha1)) {
                Files.createDirectories(zipFile.getParent());
                
                for (String baseUrl : EASYTIER_CONFIG.downloadsUrl) {
                    String url = baseUrl.replace("{FILE_NAME}", info.filename);
                    try {
                        new FileDownloadTask(NetworkUtils.toURL(url), zipFile.toFile()).run();
                        if (verifyZipChecksum(zipFile, info.sha1)) {
                            break;
                        }
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "Failed to download EasyTier from " + url, e);
                    }
                }
            }

            // Extract files
            Files.createDirectories(extractDir);
            FileUtils.extractZipTo(zipFile.toFile(), extractDir.toFile());

            // Copy files to final location
            Files.createDirectories(getEasytierLocalDirectory());
            for (String path : info.path) {
                Path source = extractDir.resolve(path);
                Path target = getEasytierLocalDirectory().resolve(path.substring(path.lastIndexOf('/') + 1));
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                
                // Set executable permission on Unix
                if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX || 
                    OperatingSystem.CURRENT_OS == OperatingSystem.OSX) {
                    Set<PosixFilePermission> perms = Files.getPosixFilePermissions(target);
                    perms.add(PosixFilePermission.OWNER_EXECUTE);
                    Files.setPosixFilePermissions(target, perms);
                }
            }
        });
    }

    private static boolean verifyZipChecksum(Path file, String expectedHash) throws IOException {
        if (expectedHash == null || expectedHash.isEmpty()) return true;
        String actualHash = FileUtils.calculateSha1(file);
        return expectedHash.equalsIgnoreCase(actualHash);
    }

    public static void downloadEasytierConfig(String token, Path configPath) throws IOException {
        String certFileContent = HttpRequest.GET(String.format("https://cert.mcer.cn/%s.yml", token)).getString();
        if (!certFileContent.equals("")) {
            FileUtils.writeText(configPath, certFileContent);
        }
    }

    public static CompletableFuture<EasytierSession> startEasytier(String token) {
        return CompletableFuture.runAsync(() -> {
            Path configPath = getConfigPath(token);
            Files.createDirectories(configPath.getParent());

            // 下载 HiPer 配置文件
            Logging.registerForbiddenToken(token, "<hiper token>");
            try {
                downloadEasytierConfig(token, configPath);
            } catch (IOException e) {
                LOG.log(Level.WARNING, "configuration file cloud cache token has been not available, try to use the local configuration file", e);
            }

            if (Files.exists(configPath)) {
                Files.copy(configPath, EASYTIER_TEMP_CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
                try (BufferedWriter output = Files.newBufferedWriter(EASYTIER_TEMP_CONFIG_PATH, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
                    output.write("\n");
                    output.write("logging:\n");
                    output.write("  format: json\n");
                    output.write("  file_path: '" + Metadata.HMCL_CURRENT_DIRECTORY.resolve("logs").resolve("hiper.log").toString().replace("'", "''") + "'\n");
                }
            }

            String[] commands = new String[]{EASYTIER_PATH.toString(), "-config", EASYTIER_TEMP_CONFIG_PATH.toString()};

            if (!IS_ADMINISTRATOR) {
                switch (OperatingSystem.CURRENT_OS) {
                    case WINDOWS:
                        if (USE_GSUDO)
                            commands = new String[]{GSUDO_LOCAL_FILE.toString(), EASYTIER_PATH.toString(), "-config", EASYTIER_TEMP_CONFIG_PATH.toString()};
                        break;
                    case LINUX:
                        String askpass = System.getProperty("hmcl.askpass", System.getenv("HMCL_ASKPASS"));
                        if ("user".equalsIgnoreCase(askpass))
                            commands = new String[]{"sudo", "-A", EASYTIER_PATH.toString(), "-config", EASYTIER_TEMP_CONFIG_PATH.toString()};
                        else if ("false".equalsIgnoreCase(askpass))
                            commands = new String[]{"sudo", "--non-interactive", EASYTIER_PATH.toString(), "-config", EASYTIER_TEMP_CONFIG_PATH.toString()};
                        else {
                            if (Files.exists(Paths.get("/usr/bin/pkexec")))
                                commands = new String[]{"/usr/bin/pkexec", EASYTIER_PATH.toString(), "-config", EASYTIER_TEMP_CONFIG_PATH.toString()};
                            else
                                commands = new String[]{"sudo", "--non-interactive", EASYTIER_PATH.toString(), "-config", EASYTIER_TEMP_CONFIG_PATH.toString()};
                        }
                        break;
                    case OSX:
                        commands = new String[]{"sudo", "--non-interactive", EASYTIER_PATH.toString(), "-config", EASYTIER_TEMP_CONFIG_PATH.toString()};
                        break;
                }
            }

            Process process = new ProcessBuilder()
                    .command(commands)
                    .start();

            new EasytierSession(process, Arrays.asList(commands));
        });
    }

    public static String getEasytierFileName() {
        if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
            return "hiper.exe";
        } else {
            return "hiper";
        }
    }

    public static Path getEasytierLocalDirectory() {
        return Metadata.HMCL_CURRENT_DIRECTORY.resolve("libraries").resolve("hiper").resolve("hiper").resolve("binary");
    }

    public static class EasytierSession extends ManagedProcess {
        private final EventManager<EasytierExitEvent> onExit = new EventManager<>();
        private final EventManager<EasytierIPEvent> onIPAllocated = new EventManager<>();
        private final EventManager<EasytierShowValidUntilEvent> onValidUntil = new EventManager<>();
        private final BufferedWriter writer;
        private int error = 0;

        EasytierSession(Process process, List<String> commands) {
            super(process, commands);

            Runtime.getRuntime().addShutdownHook(new Thread(this::stop));

            LOG.info("Started hiper with command: " + new CommandBuilder().addAll(commands));

            addRelatedThread(Lang.thread(this::waitFor, "EasytierExitWaiter", true));
            pumpInputStream(this::onLog);
            pumpErrorStream(this::onLog);

            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        }

        private void onLog(String log) {
            if (!log.startsWith("{")) {
                LOG.warning("[HiPer] " + log);

                if (log.startsWith("failed to load config"))
                    error = EasytierExitEvent.INVALID_CONFIGURATION;
                else if (log.startsWith("sudo: ") || log.startsWith("Error getting authority") || log.startsWith("Error: An error occurred trying to start process"))
                    error = EasytierExitEvent.NO_SUDO_PRIVILEGES;
                else if (log.startsWith("Failed to write to log, can't rename log file")) {
                    error = EasytierExitEvent.NO_SUDO_PRIVILEGES;
                    stop();
                }

                return;
            }

            try {
                Map<?, ?> logJson = JsonUtils.fromNonNullJson(log, Map.class);
                String msg = "";
                if (logJson.containsKey("msg")) {
                    msg = tryCast(logJson.get("msg"), String.class).orElse("");
                    if (msg.contains("Failed to get a tun/tap device")) {
                        error = EasytierExitEvent.FAILED_GET_DEVICE;
                    }
                    if (msg.contains("Failed to load certificate from config")) {
                        error = EasytierExitEvent.FAILED_LOAD_CONFIG;
                    }
                    if (msg.contains("Validity of client certificate")) {
                        Optional<String> validUntil = tryCast(logJson.get("valid"), String.class);
                        if (validUntil.isPresent()) {
                            try {
                                synchronized (EASYTIER_VALID_TIME_FORMAT) {
                                    Date date = EASYTIER_VALID_TIME_FORMAT.parse(validUntil.get());
                                    onValidUntil.fireEvent(new EasytierShowValidUntilEvent(this, date));
                                }
                            } catch (JsonParseException | ParseException e) {
                                LOG.log(Level.WARNING, "Failed to parse certification expire time string: " + validUntil.get());
                            }
                        }
                    }
                }

                if (logJson.containsKey("network")) {
                    Map<?, ?> network = tryCast(logJson.get("network"), Map.class).orElse(Collections.emptyMap());
                    if (network.containsKey("IP") && msg.contains("Main HostMap created")) {
                        Optional<String> ip = tryCast(network.get("IP"), String.class);
                        ip.ifPresent(s -> onIPAllocated.fireEvent(new EasytierIPEvent(this, s)));
                    }
                }
            } catch (JsonParseException e) {
                LOG.log(Level.WARNING, "Failed to parse hiper log: " + log, e);
            }
        }

        private void waitFor() {
            try {
                int exitCode = getProcess().waitFor();
                LOG.info("Easytier exited with exitcode " + exitCode);
                if (error != 0) {
                    onExit.fireEvent(new EasytierExitEvent(this, error));
                } else {
                    onExit.fireEvent(new EasytierExitEvent(this, exitCode));
                }
            } catch (InterruptedException e) {
                onExit.fireEvent(new EasytierExitEvent(this, EasytierExitEvent.INTERRUPTED));
            } finally {
                try {
                    if (writer != null)
                        writer.close();
                } catch (IOException e) {
                    LOG.log(Level.WARNING, "Failed to close Easytier stdin writer", e);
                }
            }
            destroyRelatedThreads();
        }

        @Override
        public void stop() {
            try {
                writer.write("quit\n");
                writer.flush();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed to quit HiPer", e);
            }
            try {
                getProcess().waitFor(1, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
            super.stop();
        }

        public EventManager<EasytierExitEvent> onExit() {
            return onExit;
        }

        public EventManager<EasytierIPEvent> onIPAllocated() {
            return onIPAllocated;
        }

        public EventManager<EasytierShowValidUntilEvent> onValidUntil() {
            return onValidUntil;
        }

    }

    public static class EasytierExitEvent extends Event {
        private final int exitCode;

        public EasytierExitEvent(Object source, int exitCode) {
            super(source);
            this.exitCode = exitCode;
        }

        public int getExitCode() {
            return exitCode;
        }

        public static final int INTERRUPTED = -1;
        public static final int INVALID_CONFIGURATION = -2;
        public static final int CERTIFICATE_EXPIRED = -3;
        public static final int FAILED_GET_DEVICE = -4;
        public static final int FAILED_LOAD_CONFIG = -5;
        public static final int NO_SUDO_PRIVILEGES = -6;
    }

    public static class EasytierIPEvent extends Event {
        private final String ip;

        public EasytierIPEvent(Object source, String ip) {
            super(source);
            this.ip = ip;
        }

        public String getIP() {
            return ip;
        }
    }

    public static class EasytierShowValidUntilEvent extends Event {
        private final Date validAt;

        public EasytierShowValidUntilEvent(Object source, Date validAt) {
            super(source);
            this.validAt = validAt;
        }

        public Date getValidUntil() {
            return validAt;
        }
    }

    public static class EasytierExitException extends RuntimeException {
        private final int exitCode;
        private final boolean ready;

        public EasytierExitException(int exitCode, boolean ready) {
            this.exitCode = exitCode;
            this.ready = ready;
        }

        public int getExitCode() {
            return exitCode;
        }

        public boolean isReady() {
            return ready;
        }
    }

    public static class EasytierExitTimeoutException extends RuntimeException {
    }

    public static class EasytierSessionExpiredException extends EasytierInvalidConfigurationException {
    }

    public static class EasytierInvalidConfigurationException extends RuntimeException {
    }

    public static class JoinRequestTimeoutException extends RuntimeException {
    }

    public static class PeerConnectionTimeoutException extends RuntimeException {
    }

    public static class ConnectionErrorException extends RuntimeException {
    }

    public static class KickedException extends RuntimeException {
        private final String reason;

        public KickedException(String reason) {
            this.reason = reason;
        }

        public String getReason() {
            return reason;
        }
    }

    public static class EasytierInvalidTokenException extends RuntimeException {
    }

    public static class EasytierUnsupportedPlatformException extends RuntimeException {
    }

}

// EasyTier configuration classes
class EasyTierConfig {
    List<String> downloadsUrl;
    Map<String, EasyTierPlatform> platforms;
}

class EasyTierPlatform {
    Map<String, EasyTierPlatformInfo> architectures;
}

class EasyTierPlatformInfo {
    String filename;
    List<String> path;
    String sha1;
}