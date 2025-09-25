package base;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class EmulatorManager {

    private final String emulatorBin;
    private final String adbBin;
    private Process emulatorProcess;

    public EmulatorManager() {
        String sdkRoot = System.getenv("ANDROID_SDK_ROOT");
        if (sdkRoot == null || sdkRoot.isBlank()) sdkRoot = System.getenv("ANDROID_HOME");
        if (sdkRoot == null || sdkRoot.isBlank()) {
            throw new IllegalStateException("Set ANDROID_SDK_ROOT or ANDROID_HOME to your Android SDK path.");
        }

        emulatorBin = Paths.get(sdkRoot, "emulator", "emulator").toString();
        adbBin = Paths.get(sdkRoot, "platform-tools", "adb").toString();
    }

    /** List available AVDs */
    public List<String> listAvds() throws Exception {
        ProcessBuilder pb = new ProcessBuilder(emulatorBin, "-list-avds");
        pb.redirectErrorStream(true);
        Process p = pb.start();
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

    /** Start emulator by name and wait until boot is complete */
    public void startEmulator(String avdName, int waitSeconds) throws Exception {
        if (avdName == null || avdName.isBlank()) {
            List<String> avds = listAvds();
            if (avds.isEmpty()) throw new IllegalStateException("No AVDs available. Create one in Android Studio.");
            avdName = avds.get(0);
        }

        System.out.println("👉 Starting emulator: " + avdName);
        emulatorProcess = new ProcessBuilder(emulatorBin, "-avd", avdName)
                .redirectErrorStream(true)
                .start();

        // Wait until sys.boot_completed == 1
        Instant start = Instant.now();
        while (Duration.between(start, Instant.now()).getSeconds() < waitSeconds) {
            String devices = runCommand(adbBin, "devices");
            boolean connected = devices.lines().anyMatch(l -> l.trim().endsWith("device"));
            if (connected) {
                String boot = runCommand(adbBin, "shell", "getprop", "sys.boot_completed").trim();
                if ("1".equals(boot)) {
                    System.out.println("✅ Emulator boot completed");
                    return;
                }
            }
            Thread.sleep(2000);
        }
        throw new RuntimeException("Emulator did not boot within " + waitSeconds + " seconds");
    }

    /** Stop emulator */
    public void stopEmulator() {
        try {
            runCommand(adbBin, "emu", "kill");
            System.out.println("🛑 Emulator stopped");
        } catch (Exception e) {
            if (emulatorProcess != null && emulatorProcess.isAlive()) {
                emulatorProcess.destroy();
            }
        }
    }

    // Helper to run adb/emulator commands
    private String runCommand(String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).start();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
        }
        p.waitFor();
        return sb.toString();
    }
}
