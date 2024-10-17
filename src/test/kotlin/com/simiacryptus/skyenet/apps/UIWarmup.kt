package com.simiacryptus.skyenet.apps

import com.simiacryptus.skyenet.util.Selenium2S3
import org.openqa.selenium.By
import org.openqa.selenium.Dimension
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.WebDriver
import org.openqa.selenium.interactions.Actions

object UIWarmup {
    @JvmStatic
    fun main(args: Array<String>) {
        Selenium2S3.chromeDriver(false).apply {
            try {
                singlewalkthrough(this)
            } finally {
                quit()
            }
        }
    }

    private val vars: MutableMap<String, Any> = HashMap()

    private fun waitForWindow(driver: WebDriver, timeout: Int): String {
        Thread.sleep(timeout.toLong())
        val whNow = driver.windowHandles
        val whThen = vars["window_handles"] as Set<String>
        if (whNow.size > whThen.size) {
            whNow.removeAll(whThen)
        }
        return whNow.iterator().next()
    }

    private fun singlewalkthrough(driver: WebDriver) {
        val js: JavascriptExecutor = driver as JavascriptExecutor
        driver.get("http://localhost:1900/")
        driver.manage().window().size = Dimension(972, 894)

//    driver.findElement(By.id("login")).click()
//    awaitUserLogin()
//    driver.findElement(By.name("confirm")).click()


        driver.findElement(By.linkText("Themes")).click()
        driver.findElement(By.id("theme_night")).click()


        driver.findElement(By.id("username")).click()
        driver.findElement(By.id("user-settings")).click()
        Thread.sleep(5000)
        driver.findElement(By.name("settings")).click()
        val key = "abc123"
        driver.findElement(By.name("settings")).sendKeys("""{\n  "apiKey" : "$key"\n}""")
        driver.findElement(By.cssSelector("input:nth-child(3)")).click()
        driver.findElement(By.cssSelector("p:nth-child(6)")).click()



        driver.findElement(By.linkText("List Sessions")).click()
        Thread.sleep(5000)
        driver.findElement(By.cssSelector("div > p:nth-child(1)")).click()
        driver.findElement(By.cssSelector("div > p:nth-child(1)")).click()
        val descriptionElement = driver.findElement(By.id("app-description"))
        val description = descriptionElement.text
        println(description)
        return

        driver.findElement(By.cssSelector("div > p:nth-child(1)")).click()
        driver.findElement(By.cssSelector("div > p:nth-child(1)")).click()
        driver.findElement(By.cssSelector("div > p:nth-child(1)")).click()
        Actions(driver).doubleClick(driver.findElement(By.cssSelector("div > p:nth-child(1)"))).perform()
        driver.findElement(By.cssSelector("div > p:nth-child(1)")).click()
        driver.findElement(By.cssSelector("div > p:nth-child(1)")).click()
        driver.findElement(By.cssSelector("div > p:nth-child(1)")).click()
        driver.findElement(By.cssSelector("div > p:nth-child(1)")).click()
        driver.findElement(By.cssSelector("#modal-content > div > div")).click()
        driver.findElement(By.id("modal")).click()
        driver.findElement(By.linkText("New Public Session")).click()
        driver.findElement(By.id("threads")).click()
        driver.findElement(By.id("modal")).click()
        driver.findElement(By.id("settings")).click()
        js.executeScript("window.scrollTo(0,0)")
        driver.findElement(By.id("modal")).click()
        js.executeScript("window.scrollTo(0,1611.5)")
        js.executeScript("window.scrollTo(0,364)")
        driver.findElement(By.id("threads")).click()
        driver.findElement(By.cssSelector(".pool-stats")).click()
        driver.findElement(By.id("modal")).click()
        vars["window_handles"] = driver.windowHandles
        driver.findElement(By.id("share")).click()
        vars["win2686"] = waitForWindow(driver, 2000)
        driver.findElement(By.id("modal")).click()
        driver.findElement(By.id("threads")).click()
        driver.findElement(By.id("modal")).click()
        driver.findElement(By.id("threads")).click()
        js.executeScript("window.scrollTo(0,474)")
        vars["root"] = driver.windowHandle
        vars["window_handles"] = driver.windowHandles
        driver.switchTo().window(vars["win2686"].toString())
        vars["win8544"] = waitForWindow(driver, 2000)
        driver.switchTo().window(vars["root"].toString())
        driver.findElement(By.id("modal")).click()
        driver.findElement(By.id("delete")).click()
        driver.findElement(By.name("confirm")).click()
        driver.findElement(By.name("confirm")).sendKeys("confirm")
        driver.findElement(By.cssSelector("input:nth-child(3)")).click()
        driver.findElement(By.cssSelector("tr:nth-child(2) > td:nth-child(4) > .new-session-link")).click()
        driver.findElement(By.cssSelector("html")).click()


        /*
        driver.get("https://apps.simiacrypt.us/");
        driver.manage().window().setSize(new Dimension(973, 895));
        driver.findElement(By.linkText("List Sessions")).click();
        driver.findElement(By.id("modal")).click();
        driver.findElement(By.cssSelector("tr:nth-child(4) > td:nth-child(2) > a")).click(); // List Sessions
        driver.findElement(By.id("modal")).click(); // Close modal
        driver.findElement(By.linkText("List Sessions")).click();
        driver.findElement(By.id("modal")).click(); // Close modal
        driver.findElement(By.cssSelector("tr:nth-child(4) > td:nth-child(2) > a")).click(); // List Sessions
        driver.findElement(By.cssSelector(".modal-content")).click();
        driver.findElement(By.cssSelector("ul:nth-child(3) > li:nth-child(2)")).click();
        driver.findElement(By.linkText("Are cats our friends?")).click();
        driver.findElement(By.id("usage")).click(); // Usage
        driver.findElement(By.cssSelector(".table-row:nth-child(4) > .cost-cell")).click();
        driver.findElement(By.cssSelector(".table-row:nth-child(4) > .cost-cell")).click();
        {
          WebElement element = driver.findElement(By.cssSelector(".table-row:nth-child(4) > .cost-cell"));
          Actions builder = new Actions(driver);
          builder.doubleClick(element).perform();
        }
        driver.findElement(By.cssSelector(".table-row:nth-child(4) > .cost-cell")).click();
        driver.findElement(By.cssSelector(".table-row:nth-child(4) > .cost-cell")).click();
        driver.findElement(By.id("modal")).click();
        driver.close();
        */
    }

}

