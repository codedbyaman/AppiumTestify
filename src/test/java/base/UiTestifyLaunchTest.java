package base;

import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;

public class AppiumBasics {

    private AndroidDriver driver;

    @BeforeClass
    public void setUp() throws MalformedURLException, URISyntaxException {
        UiAutomator2Options options = new UiAutomator2Options();

        // Device details (update to match your emulator/device)
        options.setDeviceName("Test-Pixel 9a");
        options.setPlatformName("Android");
        options.setAutomationName("UiAutomator2");

        // App path (use single forward slashes)
        options.setApp("/Users/zop3722/Downloads/AppiumTestify/src/test/resources/uiTestifyAndroid.apk");

        // Initialize driver (make sure Appium server is running)
        driver = new AndroidDriver(new URI("http://127.0.0.1:4723/").toURL(), options);

        // Implicit wait
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
    }

    @Test
    public void AppiumTest() {
        System.out.println("App launched successfully!");
        System.out.println("Package: " + driver.getCurrentPackage());
        System.out.println("Activity: " + driver.currentActivity());
    }

    @AfterClass(alwaysRun = true)
    public void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }
}