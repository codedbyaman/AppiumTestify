package base;

import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import io.appium.java_client.service.local.AppiumDriverLocalService;
import io.appium.java_client.service.local.AppiumServiceBuilder;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UiTestifyLaunchTest {

    private EmulatorManager emulatorManager;
    private AndroidDriver driver;
    private AppiumDriverLocalService service;
    private Process externalAppiumProcess;

    // change if your apk location differs
    private final String apkPath = "/Users/aman/Downloads/appium/src/test/resources/uiTestifyAndroid.apk";
    private final String appiumHost = "127.0.0.1";
    private final int appiumPort = 4723;

    @BeforeClass
    public void setUp() throws Exception {
        emulatorManager = new EmulatorManager();

        // 1) start emulator (first available AVD if Test_Pixel_9a not present)
        System.out.println("Starting emulator...");
        emulatorManager.startEmulator("Test_Pixel_9a", 120);

        // 2) determine connected device udid using EmulatorManager's adb
        String adb = emulatorManager.getAdbPath();
        String udid = findFirstDeviceUdId(adb);
        if (udid == null) throw new IllegalStateException("No device found (adb returned no device).");
        System.out.println("Found device udid: " + udid);

        // 3) start Appium (prefer embedded via APPIUM_JS; otherwise start external with env)
        String appiumJs = System.getenv("APPIUM_JS");
        if (appiumJs != null && !appiumJs.isBlank() && new File(appiumJs).exists()) {
            service = new AppiumServiceBuilder()
                    .withAppiumJS(new File(appiumJs))
                    .withIPAddress(appiumHost)
                    .usingPort(appiumPort)
                    .build();
            service.start();
            System.out.println("Appium embedded started");
        } else {
            externalAppiumProcess = startExternalAppium();
            waitForPort(appiumHost, appiumPort, 60);
            System.out.println("External Appium started");
        }

        // 4) sanity: apk exists
        File apk = new File(apkPath);
        if (!apk.exists()) throw new IllegalStateException("APK not found: " + apkPath);

        // 5) build capabilities (include udid)
        UiAutomator2Options options = new UiAutomator2Options()
                .setDeviceName("Test_Pixel_9a")
                .setPlatformName("Android")
                .setAutomationName("UiAutomator2")
                .setApp(apk.getAbsolutePath());
        options.setCapability("udid", udid);
        options.setCapability("autoGrantPermissions", true);

        // 6) create driver
        driver = new AndroidDriver(new URI("http://" + appiumHost + ":" + appiumPort + "/").toURL(), options);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        System.out.println("Driver initialized");
    }

    @Test
    public void testAppLaunch() {
        System.out.println("Package: " + driver.getCurrentPackage());
        System.out.println("Activity: " + driver.currentActivity());
    }

    @AfterClass(alwaysRun = true)
    public void tearDown() {
        try { if (driver != null) driver.quit(); } catch (Exception ignored) {}
        try { if (service != null) service.stop(); } catch (Exception ignored) {}
        try { if (externalAppiumProcess != null) externalAppiumProcess.destroy(); } catch (Exception ignored) {}
        //try { if (emulatorManager != null) emulatorManager.stopEmulator(); } catch (Exception ignored) {}
        System.out.println("Teardown complete");
    }

    // --- Helpers ---

    // Start external Appium but ensure environment contains SDK & PATH so Appium can find adb/emulator
    private Process startExternalAppium() throws IOException {
        ProcessBuilder pb = new ProcessBuilder("appium", "--port", String.valueOf(appiumPort));
        pb.redirectErrorStream(true);

        Map<String, String> env = pb.environment();

        // ensure SDK paths available to the child process (use current env or default macOS SDK location)
        String sdk = System.getenv("ANDROID_SDK_ROOT");
        if (sdk == null || sdk.isBlank()) sdk = System.getProperty("user.home") + "/Library/Android/sdk";
        env.put("ANDROID_SDK_ROOT", sdk);
        env.put("ANDROID_HOME", sdk);

        // build a safe PATH: npm global, homebrew, android tools, plus existing
        StringBuilder path = new StringBuilder();
        path.append(System.getProperty("user.home")).append("/.npm-global/bin:"); // npm global default
        path.append("/opt/homebrew/bin:").append("/usr/local/bin:");             // brew locations
        path.append(sdk).append("/platform-tools:").append(sdk).append("/emulator:");
        String old = env.get("PATH");
        if (old != null && !old.isBlank()) path.append(old);
        env.put("PATH", path.toString());

        // predictable appium home
        env.putIfAbsent("APPIUM_HOME", System.getProperty("user.home") + "/.appium");

        Process p = pb.start();

        // drain and print stdout
        Thread t = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) System.out.println("[Appium] " + line);
            } catch (IOException ignored) {}
        });
        t.setDaemon(true);
        t.start();

        return p;
    }

    // Wait until a TCP port is open
    private void waitForPort(String host, int port, int timeoutSeconds) throws InterruptedException {
        long start = System.currentTimeMillis();
        while ((System.currentTimeMillis() - start) / 1000 < timeoutSeconds) {
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(host, port), 1000);
                return;
            } catch (Exception ignored) {
                Thread.sleep(500);
            }
        }
        throw new RuntimeException("Timed out waiting for " + host + ":" + port);
    }

    // Use adb (from emulatorManager) to find the first connected device udid
    private String findFirstDeviceUdId(String adbPath) {
        try {
            Process p = new ProcessBuilder(adbPath, "devices", "-l").redirectErrorStream(true).start();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                Pattern ptn = Pattern.compile("^([^\\s]+)\\s+device");
                while ((line = br.readLine()) != null) {
                    Matcher m = ptn.matcher(line.trim());
                    if (m.find()) return m.group(1);
                }
            }
            p.waitFor();
        } catch (Exception ignored) {}
        return null;
    }
}
