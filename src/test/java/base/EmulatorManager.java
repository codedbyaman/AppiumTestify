package base;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EmulatorManager {

    private final String emulatorBin;
    private final String adbBin;
    private Process emulatorProcess;
    private Thread emulatorLogThread;

    public EmulatorManager() {
        String sdkRoot = locateAndroidSdk();
        if (sdkRoot == null) {
            throw new IllegalStateException(
                    "Set ANDROID_SDK_ROOT or ANDROID_HOME, or install Android SDK in ~/Library/Android/sdk or ~/Android/Sdk");
        }
        emulatorBin = Paths.get(sdkRoot, "emulator", "emulator").toString();
        adbBin = Paths.get(sdkRoot, "platform-tools", "adb").toString();

        if (!new File(emulatorBin).exists()) {
            throw new IllegalStateException("Emulator binary not found: " + emulatorBin);
        }
        if (!new File(adbBin).exists()) {
            throw new IllegalStateException("adb not found: " + adbBin);
        }
    }

    private String locateAndroidSdk() {
        String sdk = System.getenv("ANDROID_SDK_ROOT");
        if (sdk != null && !sdk.isBlank()) return sdk;
        sdk = System.getenv("ANDROID_HOME");
        if (sdk != null && !sdk.isBlank()) return sdk;

        String home = System.getProperty("user.home");
        String[] common = {
                Paths.get(home, "Library", "Android", "sdk").toString(),
                Paths.get(home, "Android", "Sdk").toString()
        };
        for (String p : common) {
            if (new File(p).exists()) return p;
        }
        return null;
    }

    /** List available AVD names (as returned by `emulator -list-avds`) */
    public List<String> listAvds() throws Exception {
        Process p = new ProcessBuilder(emulatorBin, "-list-avds").redirectErrorStream(true).start();
        List<String> avds = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.isBlank()) avds.add(line.trim());
            }
        }
        p.waitFor();
        return avds;
    }

    /**
     * Start emulator by AVD name and block until device reports boot completed.
     *
     * @param avdName     AVD name (if null or blank, the first AVD is used)
     * @param waitSeconds how many seconds to wait for boot (recommended 120+)
     */
    public void startEmulator(String avdName, int waitSeconds) throws Exception {
        if (avdName == null || avdName.isBlank()) {
            var avds = listAvds();
            if (avds.isEmpty()) throw new IllegalStateException("No AVDs found. Create one in Android Studio.");
            avdName = avds.get(0);
        }

        if (emulatorProcess != null && emulatorProcess.isAlive()) {
            throw new IllegalStateException("An emulator process is already running from this manager.");
        }

        // Use a ProcessBuilder and inject Android SDK env so the emulator process has the correct environment
        ProcessBuilder pb = new ProcessBuilder(emulatorBin, "-avd", avdName, "-no-snapshot-save");
        pb.redirectErrorStream(true);

        Map<String, String> env = pb.environment();
        String sdk = System.getenv("ANDROID_SDK_ROOT");
        if (sdk == null || sdk.isBlank()) sdk = System.getProperty("user.home") + "/Library/Android/sdk";
        env.put("ANDROID_SDK_ROOT", sdk);
        env.put("ANDROID_HOME", sdk);

        // Ensure PATH contains platform-tools & emulator so subprocess can find adb/emulator if needed
        String existingPath = env.get("PATH");
        String extra = sdk + "/platform-tools:" + sdk + "/emulator:";
        env.put("PATH", (extra + (existingPath == null ? "" : existingPath)));

        emulatorProcess = pb.start();

        // Drain emulator stdout/stderr in background and print prefixed lines
        emulatorLogThread = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(emulatorProcess.getInputStream()))) {
                String ln;
                while ((ln = br.readLine()) != null) {
                    System.out.println("[EMULATOR] " + ln);
                }
            } catch (Exception ignored) {
            }
        }, "emulator-log-reader");
        emulatorLogThread.setDaemon(true);
        emulatorLogThread.start();

        // Wait for device to appear and boot to complete
        Instant start = Instant.now();
        // first wait for any device via adb
        while (Duration.between(start, Instant.now()).getSeconds() < waitSeconds) {
            String devicesOut = runCommand(adbBin, "devices");
            boolean deviceConnected = devicesOut.lines().anyMatch(l -> l.trim().endsWith("device") || l.contains("\tdevice"));
            if (deviceConnected) break;
            Thread.sleep(1000);
        }

        if (!isAnyDeviceConnected()) {
            throw new RuntimeException("Emulator did not appear in adb devices within " + waitSeconds + "s");
        }

        // Now wait for boot completed property and bootanim stopped
        Instant bootStart = Instant.now();
        while (Duration.between(bootStart, Instant.now()).getSeconds() < waitSeconds) {
            String boot = runCommand(adbBin, "shell", "getprop", "sys.boot_completed").trim();
            String bootanim = runCommand(adbBin, "shell", "getprop", "init.svc.bootanim").trim();
            if ("1".equals(boot) || "stopped".equalsIgnoreCase(bootanim) || "0".equals(bootanim)) {
                // small extra wait to stabilize
                Thread.sleep(1000);
                System.out.println("Emulator reports boot completed");
                return;
            }
            Thread.sleep(1000);
        }

        throw new RuntimeException("Emulator did not finish boot within " + waitSeconds + " seconds");
    }

    /** Stop emulator started by this manager. */
    public void stopEmulator() {
        try {
            // Prefer graceful stop via adb emu kill
            runCommand(adbBin, "emu", "kill");
        } catch (Exception e) {
            // fallback - destroy process if still alive
            if (emulatorProcess != null && emulatorProcess.isAlive()) {
                emulatorProcess.destroy();
            }
        } finally {
            if (emulatorLogThread != null && emulatorLogThread.isAlive()) {
                emulatorLogThread.interrupt();
            }
            emulatorProcess = null;
        }
    }

    // Expose adb path for callers
    public String getAdbPath() {
        return adbBin;
    }

    // quick check if adb reports any device
    private boolean isAnyDeviceConnected() {
        try {
            String out = runCommand(adbBin, "devices");
            return out.lines().anyMatch(l -> l.trim().endsWith("device") || l.contains("\tdevice"));
        } catch (Exception e) {
            return false;
        }
    }

    // Run a command and return its stdout (blocking)
    private String runCommand(String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
        }
        p.waitFor();
        return sb.toString();
    }
}