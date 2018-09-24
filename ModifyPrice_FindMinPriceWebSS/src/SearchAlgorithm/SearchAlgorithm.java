/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package SearchAlgorithm;

import Selenium.CommonConstant;
import Selenium.XPathConstant;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JOptionPane;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 *
 * @author Minato
 */
public class SearchAlgorithm {

    // Xét giá nhỏ nhất để bỏ qua nó (chỉ lấy giá từ 500k trở lên)
    private static final int DEFAULT_PRICE_MIN = 500000;

    private static String listProductFail = "";

    public void searchProduct(String linkPathFile, String linkSaveFileProductFail, int priceReduction) {

        long startTime = System.currentTimeMillis();

        FileInputStream fileInputStream;

        try {
            fileInputStream = new FileInputStream(linkPathFile);
            Reader reader = null;

            try {
                reader = new InputStreamReader(fileInputStream, "utf8");
            } catch (UnsupportedEncodingException ex) {
                Logger.getLogger(SearchAlgorithm.class.getName()).log(Level.SEVERE, null, ex);
            }

            BufferedReader inputText = new BufferedReader(reader);

            String tempProduct;
            int count = 1;

            try {
                while ((tempProduct = inputText.readLine()) != null) {

                    String nameProduct;

                    // remove 1234, bếp từ :v
                    if (tempProduct.contains(",")) {
                        nameProduct = tempProduct.substring(tempProduct.indexOf(",") + 1, tempProduct.length());
                    } else {
                        nameProduct = tempProduct;
                    }

                    // Nếu line = null => continue
                    if (nameProduct.length() == 0) {
                        continue;
                    }

                    System.out.println(count++ + ": " + nameProduct);

                    String product = nameProduct.replaceAll("[-–]", " ").toLowerCase();
                    product = product.replaceAll("\\s\\s+", " ");

                    // Check tên sản phẩm chỉ với 2 mã code ở cuối
                    String codeOfProduct = "";

                    Pattern patternCode = Pattern.compile(XPathConstant.REGEX.REGEX_GET_CODE_OF_PRODUCT);
                    Matcher matcherCode = patternCode.matcher(product);

                    while (matcherCode.find()) {
                        codeOfProduct = matcherCode.group(0);
                    }

                    // Code tìm sản phẩm trên website để sửa giá
                    String codeSearchProductInWeb = "";

                    Pattern patternCodeSearchProductInWeb = Pattern.compile(XPathConstant.REGEX.REGEX_SEARCH_PRODUCT_IN_WEB);
                    Matcher matcherCodeSearchProductInWeb = patternCodeSearchProductInWeb.matcher(nameProduct);

                    while (matcherCodeSearchProductInWeb.find()) {
                        codeSearchProductInWeb = matcherCodeSearchProductInWeb.group(0);
                    }

                    if (codeSearchProductInWeb.equals("")) {
                        listProductFail += nameProduct + "\n";
                        System.out.println("Not found name product...");
                        continue;
                    }

                    long timeStart = System.currentTimeMillis();

                    // Get source code of websosanh
                    int defaultPrice = 100000000, price, sumPages = 1, numberPage = 1;
                    do {

                        String encodeSingleText = URLEncoder.encode(nameProduct.trim(), "UTF-8");

                        encodeSingleText = "https://websosanh.vn/s/" + encodeSingleText;

                        String urlText = encodeSingleText + "?pi=" + (String.valueOf(numberPage)) + ".htm";

                        URL url = new URL(urlText);
                        URLConnection connectURL = url.openConnection();

                        // Get inputStreamReader
                        try (BufferedReader inputURL = new BufferedReader(new InputStreamReader(
                                connectURL.getInputStream(), StandardCharsets.UTF_8))) {

                            String tempText;
                            String textLineStream = "";
                            String containsTextOfPage = "";

                            while ((tempText = inputURL.readLine()) != null) {
                                textLineStream += tempText;

                                // Get text chứa số trang của một sản phẩm
                                if (textLineStream.contains("data-page-index")) {
                                    containsTextOfPage += tempText;
                                }
                            }

                            // Get text chứa giá
                            Pattern pattern = Pattern.compile(XPathConstant.REGEX.REGEX_GET_BLOCK_CONTAINS_PRICE);
                            Matcher matcher = pattern.matcher(textLineStream.toLowerCase());

                            while (matcher.find()) {

                                String textContainsPrice = matcher.group(0);

                                // Hết Hàng
                                if (textContainsPrice.compareTo("out-of-stock") == 0) {
                                    continue;
                                }

                                // Get title product
                                Pattern pattern1 = Pattern.compile(XPathConstant.REGEX.REGEX_GET_TITLE_OF_PRODUCT);
                                Matcher matcher1 = pattern1.matcher(textContainsPrice);

                                // So sánh tên sản phẩm từ file với tên sản phẩm tên websosanh
                                String titleOfProduct = "";

                                while (matcher1.find()) {
                                    titleOfProduct = matcher1.group(2).replaceAll("[-–]", " ").toLowerCase();
                                    titleOfProduct = titleOfProduct.replaceAll("\\s\\s+", " ");
                                }

                                // Nếu khác với tên sản phẩm trong file thì sẽ bỏ qua
                                if (!titleOfProduct.contains(codeOfProduct)) {
                                    continue;
                                }

                                textContainsPrice = textContainsPrice.replaceAll("giá từ", "");

                                // Tìm giá
                                Pattern pattern2 = Pattern.compile(XPathConstant.REGEX.REGEX_GET_PRICE_IN_BLOCK);
                                Matcher matcher2 = pattern2.matcher(textContainsPrice);

                                while (matcher2.find()) {
                                    // Continute if price set = 1
                                    if (matcher2.group(1).compareTo("1") == 0) {
                                        continue;
                                    }
                                    // Get price
                                    price = Integer.parseInt(matcher2.group(1).replace(".", ""));

                                    if (price <= DEFAULT_PRICE_MIN) {
                                        continue;
                                    }

                                    if (price < defaultPrice) {
                                        defaultPrice = price;
                                    }
                                }
                            }

                            // Page đầu tiên
                            if (sumPages == 1) {

                                // Chứa data-page-index => page > 1
                                if (!containsTextOfPage.equals("")) {

                                    // Get số trang của sản phẩm
                                    Pattern pattern3 = Pattern.compile(XPathConstant.REGEX.REGEX_GET_NUMBER_PAGES);
                                    Matcher matcher3 = pattern3.matcher(containsTextOfPage);

                                    while (matcher3.find()) {

                                        int tempNumberPages = Integer.parseInt(matcher3.group(2));

                                        if (sumPages < tempNumberPages) {
                                            sumPages = tempNumberPages;
                                        }
                                    }
                                }
                            }
                        }

                        numberPage++;

                    } while (numberPage <= sumPages);

                    long timeEnd = System.currentTimeMillis();
                    System.out.println("Lay gia mat: " + (timeEnd - timeStart) + " milis!");

                    String priceMin = NumberFormat.getIntegerInstance(Locale.GERMAN).format(defaultPrice) + " VND";

                    System.out.println("-----------------------------------------> Gia thap nhat la: " + priceMin);

                    WebDriver driver = null;

                    try {
                        driver = this.modifyPrice(nameProduct, codeSearchProductInWeb, defaultPrice, priceReduction);

                    } finally {
                        if (driver != null) {
                            // Đóng trình duyệt
                            driver.quit();
                        }

                    }
                }
            } catch (IOException ex) {
                Logger.getLogger(SearchAlgorithm.class.getName()).log(Level.SEVERE, null, ex);
            }
        } catch (FileNotFoundException ex) {
            Logger.getLogger(SearchAlgorithm.class.getName()).log(Level.SEVERE, null, ex);
            JOptionPane.showMessageDialog(null, "Lỗi đường dẫn khi chọn/lưu tệp. Vui lòng chọn lại!", "Error", JOptionPane.ERROR_MESSAGE);
        }

        if (!listProductFail.equals("")) {
            try {
                this.output(linkSaveFileProductFail, listProductFail);
            } catch (IOException ex) {
                Logger.getLogger(SearchAlgorithm.class.getName()).log(Level.SEVERE, null, ex);
                JOptionPane.showMessageDialog(null, "Lỗi ghi tệp!", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        long endTime = System.currentTimeMillis();
        long durationInMillis = endTime - startTime;

        long millis = durationInMillis % 1000;
        long second = (durationInMillis / 1000) % 60;
        long minute = (durationInMillis / (1000 * 60)) % 60;
        long hour = (durationInMillis / (1000 * 60 * 60)) % 24;

        String timeDuration = String.format("%02d:%02d:%02d.%d", hour, minute, second, millis);

        JOptionPane.showMessageDialog(null, "Đã chỉnh giá xong!\nThời gian hoàn thành: " + timeDuration, "Thông báo", JOptionPane.INFORMATION_MESSAGE);
    }

    private void output(String linkFile, String contain) throws IOException {
        try (Writer out = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(linkFile), "UTF-8"))) {
            out.write(contain);
        }
    }

    private WebDriver modifyPrice(String nameProduct, String codeSearchProductInWeb, int defaultPrice, int priceReduction) {

        defaultPrice = defaultPrice - priceReduction;

        String price = NumberFormat.getIntegerInstance(Locale.GERMAN).format(defaultPrice) + " đ";

        System.setProperty(CommonConstant.Driver.CHROME_DRIVER, CommonConstant.PathFile.PROPERTY);
        // Khởi tạo trình duyệt Chrome
        WebDriver driver = null;

        try {
            driver = new ChromeDriver();
        } catch (IllegalStateException e) {
            JOptionPane.showMessageDialog(null, "Lỗi đường dẫn tệp CHROME DRIVER!", "Error", JOptionPane.ERROR_MESSAGE);
        }

        WebDriverWait driverWait = new WebDriverWait(driver, 10);

        // Mở rộng cửa sổ trình duyệt lớn nhất
        driver.manage().window().maximize();

        // Wait 10s cho page được load thành công
        driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);

        try {
            // Open website Bepanphat
            driver.get(CommonConstant.LogIn.URL);

            // Wait 10s cho page được load thành công
            driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);

            // Check open url success
            this.waitForVisible(driverWait, "//form[@id='loginform']");
            this.waitForVisible(driverWait, "//a[contains(text(),'Quay lại Bếp An Phát')]");

            // Login
            driver.findElement(By.xpath("//input[@id='user_login']")).sendKeys(CommonConstant.Users.ADMIN_ACCOUNT);
            driver.findElement(By.xpath("//input[@id='user_pass']")).sendKeys(CommonConstant.Users.ADMIN_PASSWORD);
            driver.findElement(By.xpath("//input[@id='wp-submit']")).click();

            // Check login success
            this.waitForVisible(driverWait, "//div[@id='wpadminbar']");
            this.waitForVisible(driverWait, "//a[contains(text(),'Bếp An Phát')]");

            // Click vao san pham
            driver.findElement(By.xpath("//div[contains(text(),'Sản phẩm')]")).click();
            this.waitForVisible(driverWait, "//input[@id='post-search-input']");

            // Click search item
            driver.findElement(By.xpath("//input[@id='post-search-input']")).sendKeys(codeSearchProductInWeb);
            driver.findElement(By.xpath("//input[@id='search-submit']")).click();

            String xPath = "//input[@id='post-search-input' and @value='" + codeSearchProductInWeb + "']";

            this.waitForVisible(driverWait, xPath);

            String value = driver.findElement(By.xpath("//input[@id='post-search-input']")).getAttribute("value");

            if (value.equals("")) {
                listProductFail += nameProduct + "\n";
                return driver;
            }

            // Select san pham
            driver.findElement(By.xpath(XPathConstant.XPATH.XPATH_FIND_FIRST_PRODUCT)).click();

            String valueTitle = driver.findElement(By.xpath("//input[@id='title']")).getAttribute("value");

            if (valueTitle.contains(codeSearchProductInWeb)) {
                driver.findElement(By.xpath("//input[@id='Gia_km']")).clear();
                driver.findElement(By.xpath("//input[@id='Gia_km']")).sendKeys(price);

                // Cap nhat
                WebElement element = driver.findElement(By.xpath("//input[@id='publish']"));

                JavascriptExecutor executor = (JavascriptExecutor) driver;
                executor.executeScript("arguments[0].click();", element);

                System.out.println("Done update price: " + nameProduct + " => " + price);
            } else {
                System.out.println("Not found title: " + nameProduct);
                System.out.println("With code: " + codeSearchProductInWeb);
                listProductFail += nameProduct + "\n";
            }

        } catch (Exception e) {
            System.out.println("Xảy ra lỗi khi chỉnh giá sản phẩm: " + nameProduct);
            listProductFail += nameProduct + "\n";
            return driver;
        }
        return driver;
    }

    private void waitForVisible(WebDriverWait myWait, String xPath) {
        myWait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(xPath)));
    }
}
