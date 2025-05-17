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

import javafx.application.Platform;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.event.Event;
import org.jackhuang.hmcl.event.EventManager;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.*;
import org.jackhuang.hmcl.util.io.HttpRequest;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.CommandBuilder;
import org.jackhuang.hmcl.util.platform.ManagedProcess;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import static org.jackhuang.hmcl.util.Lang.*;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.io.ChecksumMismatchException.verifyChecksum;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * Easytier Management.
 */
public final class MultiplayerManager {
    static final String EASYTIER_VERSION = "v2.2.4";
    private static final String DOWNLOAD_URL = "https://raw.gitcode.com/zkitefly/easytier-release/raw/2f70217b9418e05779caf187e42c5a6786355195/";
    public static final Path EASYTIER_PATH = getEasytierLocalDirectory().resolve(getEasutierFileName());
    public static final int EASYTIER_AGREEMENT_VERSION = 1;
    private static final String REMOTE_ADDRESS = "127.0.0.1";
    private static final String LOCAL_ADDRESS = "0.0.0.0";

    private static final Map<Architecture, String> archMap = mapOf(
            pair(Architecture.ARM32, "armv7"),
            pair(Architecture.ARM64, "arm64"),
            pair(Architecture.X86_64, "x86_64"),
            pair(Architecture.MIPS, "mips"),
            pair(Architecture.MIPSEL, "mipsel"),
            pair(Architecture.ARM64, "aarch64")
    );

    private static final Map<OperatingSystem, String> osMap = mapOf(
            pair(OperatingSystem.FREEBSD, "freebsd-13.2"),
            pair(OperatingSystem.LINUX, "linux"),
            pair(OperatingSystem.WINDOWS, "windows"),
            pair(OperatingSystem.OSX, "macos")
    );

    private static final String EASYTIER_TARGET_NAME = String.format("easytier-%s-%s",
            osMap.getOrDefault(OperatingSystem.CURRENT_OS, "windows"),
            archMap.getOrDefault(Architecture.SYSTEM_ARCH, "x86_64"));

    private static final String EASYTIER_DOWNLOAD_URL = DOWNLOAD_URL + EASYTIER_VERSION + "/" + EASYTIER_TARGET_NAME + "-" + EASYTIER_VERSION + "/" + EASYTIER_TARGET_NAME + "/";

    private static final String GSUDO_VERSION = "v2.6.0";
    private static final String GSUDO_TARGET_ARCH = Architecture.SYSTEM_ARCH == Architecture.X86_64 ? "x64" : (Architecture.SYSTEM_ARCH == Architecture.ARM64 ? "arm64" : "x86");
    private static final String GSUDO_FILE_NAME = "gsudo.exe";
    private static final String GSUDO_DOWNLOAD_URL = "https://raw.gitcode.com/zkitefly/gsudo-release/raw/cd81dfd753bdbcb945ce392aaf620fa5112dce51/" + GSUDO_VERSION + "/" + GSUDO_TARGET_ARCH + "/" + GSUDO_FILE_NAME;
    private static final Path GSUDO_LOCAL_FILE = Metadata.HMCL_CURRENT_DIRECTORY.resolve("libraries").resolve("gsudo").resolve("gsudo").resolve(GSUDO_VERSION).resolve(GSUDO_TARGET_ARCH).resolve(GSUDO_FILE_NAME);
    private static final boolean USE_GSUDO;

    static final boolean IS_ADMINISTRATOR;

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
    }

    private static CompletableFuture<Map<String, String>> HASH;

    private MultiplayerManager() {
    }

    private static CompletableFuture<Map<String, String>> getPackagesHash() {
        FXUtils.checkFxUserThread();
        if (HASH == null) {
            HASH = CompletableFuture.supplyAsync(wrap(() -> {
                Map<String, String> hashes = new HashMap<>();
                hashes.put(EASYTIER_DOWNLOAD_URL + getEasutierFileName(), HttpRequest.GET(EASYTIER_DOWNLOAD_URL + getEasutierFileName() + ".sha1").getString().trim());
                if (USE_GSUDO) {
                    hashes.put(GSUDO_FILE_NAME, HttpRequest.GET(GSUDO_DOWNLOAD_URL + ".sha1").getString().trim());
                }
                return hashes;
            }));
        }
        return HASH;
    }

    public static Task<Void> downloadEasytier() {
        return Task.fromCompletableFuture(getPackagesHash()).thenComposeAsync(packagesHash -> {

            BiFunction<String, String, FileDownloadTask> getFileDownloadTask = (String remotePath, String localFileName) -> {
                String hash = packagesHash.get(remotePath);
                return new FileDownloadTask(
                        NetworkUtils.toURL(remotePath),
                        getEasytierLocalDirectory().resolve(localFileName).toFile(),
                        hash == null ? null : new FileDownloadTask.IntegrityCheck("SHA-1", hash));
            };

            List<Task<?>> tasks;
            if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
                if (!packagesHash.containsKey(EASYTIER_DOWNLOAD_URL + getEasutierFileName())) {
                    throw new EasytierUnsupportedPlatformException();
                }
                tasks = new ArrayList<>(4);

                tasks.add(getFileDownloadTask.apply(EASYTIER_DOWNLOAD_URL + getEasutierFileName(), getEasutierFileName()));
                if (USE_GSUDO)
                    tasks.add(new FileDownloadTask(
                            NetworkUtils.toURL(GSUDO_DOWNLOAD_URL),
                            GSUDO_LOCAL_FILE.toFile(),
                            new FileDownloadTask.IntegrityCheck("SHA-1", packagesHash.get(GSUDO_FILE_NAME))
                    ));
            } else {
                if (!packagesHash.containsKey(EASYTIER_DOWNLOAD_URL + getEasutierFileName())) {
                    throw new EasytierUnsupportedPlatformException();
                }
                tasks = Arrays.asList(
                        getFileDownloadTask.apply(EASYTIER_DOWNLOAD_URL + getEasutierFileName(), getEasutierFileName())
                );
            }
            return Task.allOf(tasks).thenRunAsync(() -> {
                if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX || OperatingSystem.CURRENT_OS == OperatingSystem.OSX) {
                    Set<PosixFilePermission> perm = Files.getPosixFilePermissions(EASYTIER_PATH);
                    perm.add(PosixFilePermission.OWNER_EXECUTE);
                    Files.setPosixFilePermissions(EASYTIER_PATH, perm);

                    perm = Files.getPosixFilePermissions(EASYTIER_PATH);
                    perm.add(PosixFilePermission.OWNER_EXECUTE);
                    Files.setPosixFilePermissions(EASYTIER_PATH, perm);
                }
            });
        });
    }

    public static CompletableFuture<EasytierSession> startEasytier(String network_name, String network_secret, String server_url, Boolean no_p2p) {
        return getPackagesHash().thenComposeAsync(packagesHash -> {
            CompletableFuture<Void> future = new CompletableFuture<>();
            try {
                if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
                    verifyChecksum(getEasytierLocalDirectory().resolve("easytier-core.exe"), "SHA-1", packagesHash.get(String.format("%s/easytier-core.exe", EASYTIER_TARGET_NAME)));
                    verifyChecksum(getEasytierLocalDirectory().resolve("wintun.dll"), "SHA-1", packagesHash.get(String.format("%s/wintun.dll", EASYTIER_TARGET_NAME)));
                    if (USE_GSUDO)
                        verifyChecksum(GSUDO_LOCAL_FILE, "SHA-1", packagesHash.get(GSUDO_FILE_NAME));
                } else {
                    verifyChecksum(getEasytierLocalDirectory().resolve("easytier-core"), "SHA-1", packagesHash.get(String.format("%s/easytier-core", EASYTIER_TARGET_NAME)));
                }

                future.complete(null);
            } catch (IOException e) {
                LOG.warning("Failed to verify EasyTier files", e);
                Platform.runLater(() -> Controllers.taskDialog(MultiplayerManager.downloadEasytier()
                        .whenComplete(exception -> {
                            if (exception == null)
                                future.complete(null);
                            else
                                future.completeExceptionally(exception);
                        }), i18n("multiplayer.download"), TaskCancellationAction.NORMAL));
            }
            return future;
        }).thenApplyAsync(wrap(ignored -> {
            List<String> commandList = new ArrayList<>();

            String baseCommand = String.format("--use-smoltcp --multi-thread --dhcp --dev-name \"HMCL-Easytier\" --network-name \"%s\" --network-secret \"%s\" --peers \"%s\"",
                    network_name, network_secret, server_url);

            if (no_p2p) {
                baseCommand += " --disable-p2p";
            }

            if (!IS_ADMINISTRATOR) {
                switch (OperatingSystem.CURRENT_OS) {
                    case WINDOWS:
                        if (USE_GSUDO) {
                            commandList.add(GSUDO_LOCAL_FILE.toString());
                            commandList.add(EASYTIER_PATH.toString());
                            commandList.addAll(Arrays.asList(baseCommand.split(" ")));
                        }
                        break;
                    case LINUX:
                        String askpass = System.getProperty("hmcl.askpass", System.getenv("HMCL_ASKPASS"));
                        if ("user".equalsIgnoreCase(askpass)) {
                            commandList.addAll(Arrays.asList("sudo", "-A", EASYTIER_PATH.toString()));
                        } else if ("false".equalsIgnoreCase(askpass)) {
                            commandList.addAll(Arrays.asList("sudo", "--non-interactive", EASYTIER_PATH.toString()));
                        } else {
                            if (Files.exists(Paths.get("/usr/bin/pkexec"))) {
                                commandList.addAll(Arrays.asList("/usr/bin/pkexec", EASYTIER_PATH.toString()));
                            } else {
                                commandList.addAll(Arrays.asList("sudo", "--non-interactive", EASYTIER_PATH.toString()));
                            }
                        }
                        commandList.addAll(Arrays.asList(baseCommand.split(" ")));
                        break;
                    case OSX:
                        commandList.addAll(Arrays.asList("sudo", "--non-interactive", EASYTIER_PATH.toString()));
                        commandList.addAll(Arrays.asList(baseCommand.split(" ")));
                        break;
                }
            } else {
                commandList.add(EASYTIER_PATH.toString());
                commandList.addAll(Arrays.asList(baseCommand.split(" ")));
            }

            Process process = new ProcessBuilder()
                    .command(commandList)
                    .start();

            return new EasytierSession(process, commandList);
        }));
    }

    public static String getEasutierFileName() {
        if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
            return "easytier-core.exe";
        } else {
            return "easytier-core";
        }
    }

    public static Path getEasytierLocalDirectory() {
        return Metadata.HMCL_CURRENT_DIRECTORY.resolve("libraries").resolve("easytier").resolve("easytier").resolve(EASYTIER_VERSION);
    }

    public static class EasytierSession extends ManagedProcess {
        private final EventManager<EasytierExitEvent> onExit = new EventManager<>();
        private final EventManager<EasytierIPEvent> onIPAllocated = new EventManager<>();
        private final BufferedWriter writer;
        private int error = 0;

        EasytierSession(Process process, List<String> commands) {
            super(process, commands);

            Runtime.getRuntime().addShutdownHook(new Thread(this::stop));

            LOG.info("Started easytier with command: " + new CommandBuilder().addAll(commands));

            addRelatedThread(Lang.thread(this::waitFor, "EasytierExitWaiter", true));
            pumpInputStream(this::onLog);
            pumpErrorStream(this::onLog);

            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        }

        private void onLog(String log) {
                LOG.info("[Easytier] " + log);
                if (log.startsWith("error: unexpected argument"))
                    error = EasytierExitEvent.INVALID_CONFIGURATION;
                else if (log.startsWith("sudo: ") || log.startsWith("Error getting authority") || log.startsWith("Error: An error occurred trying to start process"))
                    error = EasytierExitEvent.NO_SUDO_PRIVILEGES;
                else if (log.contains("connecting to peer. dst:")) {
                    error = EasytierExitEvent.FAILED_SERVER_URL;
                    stop();
                }
                else if (log.contains("tun device error")) {
                    error = EasytierExitEvent.NO_SUDO_PRIVILEGES;
                    stop();
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
                    LOG.warning("Failed to close Easytier stdin writer", e);
                }
            }
            destroyRelatedThreads();
        }

        @Override
        public void stop() {
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

//        public EventManager<EasytierShowValidUntilEvent> onValidUntil() {
//            return onValidUntil;
//        }
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
        public static final int NO_SUDO_PRIVILEGES = -3;
        public static final int FAILED_SERVER_URL = -4;
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

//    public static class EasytierShowValidUntilEvent extends Event {
//        private final Date validAt;
//
//        public EasytierShowValidUntilEvent(Object source, Date validAt) {
//            super(source);
//            this.validAt = validAt;
//        }
//
//        public Date getValidUntil() {
//            return validAt;
//        }
//    }

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