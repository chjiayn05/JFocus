package jfocus.idle;

import java.awt.AWTException;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;

public class ScreenChangeDetector {
    private Robot robot;
    private BufferedImage lastCapture;

    public ScreenChangeDetector() {
        try {
            robot = new Robot();
        } catch (AWTException e) {
            System.err.println("無法初始化 Robot 進行螢幕截圖: " + e.getMessage());
        }
    }

    /**
     * 檢查畫面是否有變化 (例如是否在播放影片)
     */
    public boolean hasScreenChanged() {
        if (robot == null) return true; // 如果無法截圖，預設回傳 true 避免錯誤地進入閒置

        // 擷取整個螢幕的畫面
        java.awt.Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        Rectangle captureRect = new Rectangle(0, 0, screenSize.width, screenSize.height);

        BufferedImage currentCapture = robot.createScreenCapture(captureRect);

        boolean changed = false;
        if (lastCapture != null) {
            changed = !compareImages(lastCapture, currentCapture);
        } else {
            changed = true; // 第一次呼叫，視為有變化
        }

        lastCapture = currentCapture;
        return changed;
    }

    private boolean compareImages(BufferedImage img1, BufferedImage img2) {
        if (img1.getWidth() != img2.getWidth() || img1.getHeight() != img2.getHeight()) {
            return false;
        }

        int width = img1.getWidth();
        int height = img1.getHeight();

        int totalPixels = 0;
        int diffPixels = 0;

        // 跳躍式抽樣比對像素
        for (int y = 0; y < height; y += 10) {
            for (int x = 0; x < width; x += 10) {
                totalPixels++;
                if (img1.getRGB(x, y) != img2.getRGB(x, y)) {
                    diffPixels++;
                }
            }
        }

        // 如果不同的像素比例大於 2%，才認為畫面有顯著變化 (影片等)
        // 若比例很低 (例如閃爍的游標)，則視為畫面靜止
        double diffRatio = (double) diffPixels / totalPixels;
        return diffRatio < 0.02; 
    }
}
