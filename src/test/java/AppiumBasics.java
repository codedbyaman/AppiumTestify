import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import io.appium.java_client.service.local.AppiumDriverLocalService;
import io.appium.java_client.service.local.AppiumServiceBuilder;
import io.appium.java_client.service.local.flags.GeneralServerFlag;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;

public class AppiumBasics {

    @Test
    public void AppiumTest() throws URISyntaxException, MalformedURLException, IOException, InterruptedException {

        // 1. Start Emulator
        String emulatorPath = System.getenv("ANDROID_HOME") + "/emulator/emulator";
        ProcessBuilder pb = new ProcessBuilder(
                emulatorPath,
                "-avd", "Test-Pixel_9a",   // <-- use AVD name
                "-netDelay", "none",
                "-nested", "full"
        );
        pb.redirectErrorStream(true);
        pb.start();
        System.out.println("Emulator Test-Pixel_9a is starting...");

        // Wait for device to boot
        Process waitProcess = new ProcessBuilder("adb", "wait-for-device").start();
        waitProcess.waitFor();
        System.out.println("Emulator booted and ready.");

        // 2. Start Appium server programmatically
        AppiumServiceBuilder appiumServiceBuilder = new AppiumServiceBuilder()
                .withAppiumJS(new File("/usr/local/lib/node_modules/appium/build/lib/main.js"))
                .withIPAddress("127.0.0.1")
                .usingPort(4723)
                .withArgument(GeneralServerFlag.SESSION_OVERRIDE);

        AppiumDriverLocalService service = AppiumDriverLocalService.buildService(appiumServiceBuilder);
        service.start();
        System.out.println("Appium Server started at: " + service.getUrl());

        // 3. Define capabilities
        UiAutomator2Options uiAutomator2Options = new UiAutomator2Options();
        uiAutomator2Options.setDeviceName("Test-Pixel_9a"); // match AVD name
        uiAutomator2Options.setApp(System.getProperty("user.dir") + "/src/test/resources/ApiDemos-debug.apk");

        // 4. Create AndroidDriver
        AndroidDriver androidDriver = new AndroidDriver(service.getUrl(), uiAutomator2Options);
        androidDriver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        System.out.println("AndroidDriver session started. SessionId: " + androidDriver.getSessionId());

        //  5. Clean up
        androidDriver.quit();
        service.stop();
        System.out.println("Driver quit & Appium server stopped");
    }
}
