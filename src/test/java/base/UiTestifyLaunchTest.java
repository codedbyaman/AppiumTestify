package base;

import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import io.appium.java_client.service.local.AppiumDriverLocalService;
import io.appium.java_client.service.local.AppiumServiceBuilder;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.net.URI;
import java.time.Duration;

public class UiTestifyLaunchTest {

    private final EmulatorManager emulatorManager = new EmulatorManager();
    private AndroidDriver driver;
    private AppiumDriverLocalService service;

    @BeforeClass
    public void setUp() throws Exception {

        emulatorManager.startEmulator("Test-Pixel_9a", 120);

        // Start Appium server programmatically
        service = new AppiumServiceBuilder()
                .withAppiumJS(new File("/usr/local/lib/node_modules/appium/build/lib/main.js"))
                .withIPAddress("127.0.0.1")
                .usingPort(4723)
                .build();
        service.start();
        System.out.println("✅ Appium service started");

        // Define device & app options
        UiAutomator2Options options = new UiAutomator2Options();
        options.setDeviceName("Test-Pixel 9a");  // match your emulator/device
        options.setPlatformName("Android");
        options.setAutomationName("UiAutomator2");
        options.setApp("/Users/zop3722/Downloads/AppiumTestify/src/test/resources/uiTestifyAndroid.apk");

        // Initialize Android driver
        driver = new AndroidDriver(new URI("http://127.0.0.1:4723/").toURL(), options);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        System.out.println("✅ Driver initialized");
    }

    @Test
    public void testAppLaunch() {
        System.out.println("🚀 App launched successfully!");
        System.out.println("📦 Package: " + driver.getCurrentPackage());
        System.out.println("🎯 Activity: " + driver.currentActivity());
    }

    @AfterClass(alwaysRun = true)
    public void tearDown() {
        // Quit driver
        if (driver != null) {
            driver.quit();
            System.out.println("✅ App terminated successfully");
        }

        // Stop Appium server
        if (service != null) {
            service.stop();
            System.out.println("🛑 Appium service stopped");
        }

        // Stop emulator
//        emulatorManager.stopEmulator();
//        System.out.println("emulator stopped");
    }
}