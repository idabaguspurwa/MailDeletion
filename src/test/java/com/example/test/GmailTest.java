package com.example.test;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Properties;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import io.github.bonigarcia.wdm.WebDriverManager;

public class GmailTest {
    private WebDriver driver;
    private WebDriverWait wait;
    private static final Logger logger = LoggerFactory.getLogger(GmailTest.class);
    
    // Credentials will be loaded from properties file
    private String testEmail;
    private String testPassword;
    
    @BeforeMethod
    public void setUp() {
        logger.info("Setting up WebDriver and loading credentials...");
        
        // Load credentials
        loadCredentials();
        
        WebDriverManager.chromedriver().setup();
        
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--start-maximized");
        options.addArguments("--disable-notifications");
        options.addArguments("--disable-popup-blocking");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--disable-extensions");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-infobars");
        options.addArguments("--lang=en-US");
        
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
        options.setExperimentalOption("useAutomationExtension", false);
        
        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        logger.info("WebDriver initialized successfully");
    }
    
    private void loadCredentials() {
        try {
            Properties props = new Properties();
            String propertiesPath = "src/test/resources/test.properties";
            props.load(new FileInputStream(propertiesPath));
            
            testEmail = props.getProperty("gmail.test.email");
            testPassword = props.getProperty("gmail.test.password");
            
            if (testEmail == null || testPassword == null || testEmail.isEmpty() || testPassword.isEmpty()) {
                throw new RuntimeException("Credentials not found in properties file. Please check test.properties file.");
            }
            
            logger.info("Credentials loaded successfully");
        } catch (IOException e) {
            logger.error("Failed to load credentials from properties file", e);
            throw new RuntimeException("Failed to load test.properties file. Please ensure it exists in src/test/resources/", e);
        }
    }
    
    @Test
    public void testGmailUnreadEmailDeletion() throws InterruptedException {
        try {
            login();
            
            handleSecurityPrompts();
            
            boolean success = processUnreadEmail();
            
            if (success) {
                logger.info("Test completed successfully - All unread emails have been deleted");
                Thread.sleep(2000);
            } else {
                throw new RuntimeException("Failed to delete all unread emails");
            }
            
        } catch (Exception e) {
            logger.error("Test failed: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    private void login() throws InterruptedException {
        logger.info("Starting login process...");
        
        // Use direct Google Accounts URL instead of Gmail
        driver.get("https://accounts.google.com/signin/v2/identifier?service=mail");
        Thread.sleep(2000);
        
        boolean loginCompleted = false;
        int loginAttempts = 0;
        int maxLoginAttempts = 3;
        
        while (!loginCompleted && loginAttempts < maxLoginAttempts) {
            loginAttempts++;
            logger.info("Login attempt {}", loginAttempts);
            
            try {
                // Check if we're on the email input page
                WebElement emailInput = wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("#identifierId, input[type='email']")));
                typeSlowly(emailInput, testEmail);
                Thread.sleep(1000);
                
                WebElement nextButton = wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("#identifierNext button, button[type='submit']")));
                ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("arguments[0].click();", nextButton);
                Thread.sleep(3000);
                
                // Wait for and enter password
                WebElement passwordInput = wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("input[type='password']")));
                typeSlowly(passwordInput, testPassword);
                Thread.sleep(1000);
                
                WebElement passwordNextButton = wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("#passwordNext button, button[type='submit']")));
                ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("arguments[0].click();", passwordNextButton);
                Thread.sleep(5000);
                
                // Try to access inbox directly
                driver.get("https://mail.google.com/mail/u/0/#inbox");
                Thread.sleep(5000);
                
                // Check if we landed on workspace page
                if (driver.getCurrentUrl().contains("workspace.google.com")) {
                    logger.info("Detected workspace landing page, attempting direct Gmail access...");
                    // Try accessing Gmail directly with a different URL
                    driver.get("https://mail.google.com/mail/u/0");
                    Thread.sleep(3000);
                    
                    // If still on workspace, try finding and clicking Gmail-related buttons
                    if (driver.getCurrentUrl().contains("workspace.google.com")) {
                        List<WebElement> possibleButtons = driver.findElements(
                            By.cssSelector("a[href*='mail.google.com'], a[data-action='sign in'], .button-primary"));
                        
                        for (WebElement button : possibleButtons) {
                            try {
                                String text = button.getText().toLowerCase();
                                String href = button.getAttribute("href");
                                
                                if ((text.contains("gmail") || text.contains("sign in") || text.contains("email")) ||
                                    (href != null && href.contains("mail.google.com"))) {
                                    ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                                        "arguments[0].click();", button);
                                    Thread.sleep(3000);
                                    break;
                                }
                            } catch (Exception e) {
                                continue;
                            }
                        }
                    }
                }
                
                // Verify we're actually in Gmail
                wait.until(ExpectedConditions.or(
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector("div[role='main']")),
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector(".aeH")),
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector(".ain"))
                ));
                
                loginCompleted = true;
                logger.info("Successfully logged in and accessed inbox");
                
            } catch (Exception e) {
                logger.warn("Login attempt {} failed: {}", loginAttempts, e.getMessage());
                if (loginAttempts >= maxLoginAttempts) {
                    throw new RuntimeException("Failed to complete login after " + maxLoginAttempts + " attempts", e);
                }
                // Start over from the beginning
                driver.get("https://accounts.google.com/signin/v2/identifier?service=mail");
                Thread.sleep(3000);
            }
        }
        
        if (!loginCompleted) {
            throw new RuntimeException("Failed to complete login process");
        }
        
        logger.info("Login process completed");
    }
    
    private void handleSecurityPrompts() throws InterruptedException {
        logger.info("Checking for security prompts...");
        
        try {
            WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(5));
            try {
                WebElement notNowButton = shortWait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("button[jsname='b6kHab']")));
                logger.info("Found 'Sign in faster' prompt, clicking 'Not now'");
                ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("arguments[0].click();", notNowButton);
                Thread.sleep(2000);
            } catch (Exception e) {
                logger.info("No 'Sign in faster' prompt found");
            }
            
            try {
                WebElement skipButton = shortWait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("[data-dismiss='acct-dismiss']")));
                logger.info("Found 'Protect your account' prompt, clicking skip");
                ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("arguments[0].click();", skipButton);
                Thread.sleep(2000);
            } catch (Exception e) {
                logger.info("No 'Protect your account' prompt found");
            }
            
            logger.info("Navigating to inbox after handling prompts...");
            driver.get("https://mail.google.com/mail/u/0/#inbox");
            Thread.sleep(3000);
            
            int maxAttempts = 3;
            int attempt = 0;
            boolean inboxLoaded = false;
            
            while (!inboxLoaded && attempt < maxAttempts) {
                attempt++;
                try {
                    wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("div[role='main']")));
                    
                    wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".AO")));
                    
                    inboxLoaded = true;
                    logger.info("Successfully loaded to inbox after handling prompts");
                } catch (Exception e) {
                    logger.warn("Attempt {} to load inbox failed, retrying...", attempt);
                    if (attempt < maxAttempts) {
                        Thread.sleep(2000);
                        driver.get("https://mail.google.com/mail/u/0/#inbox");
                        Thread.sleep(3000);
                    }
                }
            }
            
            if (!inboxLoaded) {
                throw new RuntimeException("Failed to load inbox after handling security prompts");
            }
            
        } catch (Exception e) {
            logger.error("Error handling security prompts: {}", e.getMessage());
            throw e;
        }
    }
    
    private boolean processUnreadEmail() throws InterruptedException {
        logger.info("Looking for unread emails...");
        
        try {
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("div[role='main']")));
            Thread.sleep(3000);
            
            int processedCount = 0;
            int noEmailsFoundCount = 0;
            int maxRetries = 3;
            
            while (noEmailsFoundCount < maxRetries) {
                try {
                    List<WebElement> unreadCheckboxes = driver.findElements(
                        By.cssSelector("tr.zE div[role='checkbox']"));
                    
                    if (unreadCheckboxes.isEmpty()) {
                        noEmailsFoundCount++;
                        if (noEmailsFoundCount >= maxRetries) {
                            if (processedCount > 0) {
                                logger.info("All unread emails have been deleted. Total deleted: {}", processedCount);
                                return true;
                            } else {
                                logger.info("No unread emails found in inbox");
                                return true;
                            }
                        }
                        logger.info("No unread emails found, checking again ({}/{})", noEmailsFoundCount, maxRetries);
                        Thread.sleep(2000);
                        continue;
                    }
                    
                    try {
                        WebElement lastEmailRow = driver.findElement(By.cssSelector("tr.zE"));
                        WebElement subjectElement = lastEmailRow.findElement(By.cssSelector("td[role='gridcell'] span.bog"));
                        String emailTitle = subjectElement.getAttribute("innerText");
                        logger.info("Last unread email title: {}", emailTitle);
                    } catch (Exception e) {
                        logger.warn("Could not retrieve last email title: {}", e.getMessage());
                    }
                    
                    noEmailsFoundCount = 0;
                    logger.info("Found {} unread emails", unreadCheckboxes.size());
                    
                    for (WebElement checkbox : unreadCheckboxes) {
                        try {
                            ((org.openqa.selenium.JavascriptExecutor) driver)
                                .executeScript("arguments[0].scrollIntoView(true);", checkbox);
                            Thread.sleep(500);
                            
                            ((org.openqa.selenium.JavascriptExecutor) driver)
                                .executeScript("arguments[0].click();", checkbox);
                            Thread.sleep(500);
                            
                            if (!checkbox.getAttribute("aria-checked").equals("true")) {
                                ((org.openqa.selenium.JavascriptExecutor) driver)
                                    .executeScript("arguments[0].click();", checkbox);
                                Thread.sleep(500);
                            }
                        } catch (Exception e) {
                            logger.warn("Failed to click checkbox, trying next one");
                            continue;
                        }
                    }
                    
                    List<WebElement> selectedCheckboxes = driver.findElements(
                        By.cssSelector("tr.zE div[role='checkbox'][aria-checked='true']"));
                    
                    if (selectedCheckboxes.isEmpty()) {
                        logger.warn("No emails were selected, retrying...");
                        continue;
                    }
                    
                    logger.info("Successfully selected {} emails", selectedCheckboxes.size());
                    
                    boolean deleteClicked = false;
                    
                    try {
                        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("div[gh='mtb']")));
                        Thread.sleep(2000);
                        
                        try {
                            WebElement deleteButton = driver.findElement(By.cssSelector("[aria-label='Delete']"));
                            ((org.openqa.selenium.JavascriptExecutor) driver)
                                .executeScript("arguments[0].scrollIntoView(true);", deleteButton);
                            Thread.sleep(1000);
                            
                            deleteButton.click();
                            Thread.sleep(2000);
                            deleteClicked = true;
                            logger.info("Successfully clicked delete button directly");
                        } catch (Exception e) {
                            logger.warn("Direct click failed, trying JavaScript click");
                            
                            List<WebElement> deleteButtons = driver.findElements(
                                By.cssSelector("[aria-label='Delete'], div[data-tooltip='Delete']"));
                            
                            if (!deleteButtons.isEmpty()) {
                                WebElement deleteButton = deleteButtons.get(0);
                                ((org.openqa.selenium.JavascriptExecutor) driver)
                                    .executeScript("arguments[0].click();", deleteButton);
                                Thread.sleep(2000);
                                deleteClicked = true;
                                logger.info("Successfully clicked delete button using JavaScript");
                            }
                        }
                        
                        if (!deleteClicked) {
                            WebElement inbox = driver.findElement(By.cssSelector("div[role='main']"));
                            ((org.openqa.selenium.JavascriptExecutor) driver)
                                .executeScript("arguments[0].focus();", inbox);
                            Thread.sleep(1000);
                            
                            inbox.sendKeys("#");
                            Thread.sleep(2000);
                            deleteClicked = true;
                            logger.info("Successfully used keyboard shortcut to delete");
                        }
                        
                        if (deleteClicked) {
                            Thread.sleep(2000);
                            
                            boolean deletionVerified = false;
                            
                            try {
                                WebElement confirmationMsg = wait.until(ExpectedConditions.presenceOfElementLocated(
                                    By.xpath("//span[contains(text(),'moved to') and contains(text(),'Trash')]")));
                                if (confirmationMsg != null) {
                                    deletionVerified = true;
                                    logger.info("Deletion confirmed via message");
                                }
                            } catch (Exception e) {
                                logger.warn("No confirmation message found");
                            }
                            
                            if (!deletionVerified) {
                                Thread.sleep(2000);
                                List<WebElement> remainingSelected = driver.findElements(
                                    By.cssSelector("tr.zE div[role='checkbox'][aria-checked='true']"));
                                
                                if (remainingSelected.isEmpty()) {
                                    deletionVerified = true;
                                    logger.info("Deletion confirmed - selected emails no longer present");
                                }
                            }
                            
                            if (!deletionVerified) {
                                driver.navigate().refresh();
                                Thread.sleep(3000);
                                
                                List<WebElement> newUnreadEmails = driver.findElements(
                                    By.cssSelector("tr.zE div[role='checkbox']"));
                                
                                if (newUnreadEmails.size() < unreadCheckboxes.size()) {
                                    deletionVerified = true;
                                    logger.info("Deletion confirmed - unread count decreased");
                                }
                            }
                            
                            if (deletionVerified) {
                                processedCount += selectedCheckboxes.size();
                                logger.info("Successfully moved {} emails to Trash", selectedCheckboxes.size());
                                
                                driver.navigate().refresh();
                                Thread.sleep(3000);
                                continue;
                            }
                        }
                        
                        logger.error("Failed to verify deletion after clicking delete button");
                        driver.navigate().refresh();
                        Thread.sleep(3000);
                        
                    } catch (Exception e) {
                        logger.error("Error during deletion process: {}", e.getMessage());
                        driver.navigate().refresh();
                        Thread.sleep(3000);
                    }
                    
                } catch (Exception e) {
                    logger.error("Error during email processing loop: {}", e.getMessage());
                    noEmailsFoundCount++;
                    Thread.sleep(2000);
                }
            }
            
            return processedCount > 0;
            
        } catch (Exception e) {
            logger.error("Failed to process unread emails: {}", e.getMessage(), e);
            return false;
        }
    }
    
    private void typeSlowly(WebElement element, String text) throws InterruptedException {
        ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", element);
        Thread.sleep(500);
        
        element.clear();
        Thread.sleep(500);
        
        element.click();
        Thread.sleep(500);
        
        for (char c : text.toCharArray()) {
            element.sendKeys(String.valueOf(c));
            Thread.sleep((long) (Math.random() * 150 + 150));
        }
        Thread.sleep(1000);
    }
    
    @AfterMethod
    public void tearDown() {
        if (driver != null) {
            driver.quit();
            logger.info("WebDriver shut down successfully");
        }
    }
} 