package chiikawatalk.desktop;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import javax.imageio.ImageIO;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.JTextArea;
import javax.swing.BorderFactory;
import java.awt.BorderLayout;
import java.awt.Dimension;

/**
 * 이미지 한 장을 화면 위에 띄우고 좌우로 움직이는 독립된 투명 창.
 * 모든 메서드는 Swing EDT에서 호출한다.
 */
public class DesktopPetWindow extends JWindow {
    private static final long serialVersionUID = 1L;
    private static final int PET_WIDTH = 130;
    private static final int PET_HEIGHT = 154;
    private static final int IMAGE_SIZE = 124;

    private final String nickname;
    private final BufferedImage petImage;
    private final Timer walkTimer;
    private final PetCanvas canvas = new PetCanvas();
    private final Runnable onRemove;
    private final JWindow bubble = new JWindow();
    private final JTextArea bubbleText = new JTextArea();
    private final Timer bubbleHideTimer;
    private static final int BUBBLE_WIDTH = 260;
    private static final int BUBBLE_HEIGHT = 84;

    private int direction = 2;
    private boolean movementEnabled = true;
    private boolean dragging = false;
    private Point dragOffset;

    public DesktopPetWindow(String nickname, String characterId,
                            int slot, Runnable onRemove) throws IOException {
        this.nickname = nickname;
        this.onRemove = onRemove;
        URL imageUrl = DesktopPetWindow.class.getResource(
            "/chiikawatalk/resources/characters/" + imageFilename(characterId));
        if (imageUrl == null) {
            throw new IOException("캐릭터 PNG를 찾을 수 없습니다: " + characterId);
        }
        petImage = ImageIO.read(imageUrl);
        if (petImage == null) {
            throw new IOException("PNG 파일을 읽을 수 없습니다: " + imageUrl);
        }

        JPanel bubblePanel = new JPanel(new BorderLayout());
        bubblePanel.setBackground(new Color(255, 250, 225));
        bubblePanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(235, 174, 91), 2),
            BorderFactory.createEmptyBorder(9, 12, 9, 12)));
        bubbleText.setEditable(false);
        bubbleText.setFocusable(false);
        bubbleText.setOpaque(false);
        bubbleText.setLineWrap(true);
        bubbleText.setWrapStyleWord(true);
        bubbleText.setFont(new Font("Malgun Gothic", Font.BOLD, 13));
        bubbleText.setForeground(new Color(65, 55, 45));
        bubblePanel.add(bubbleText, BorderLayout.CENTER);
        bubble.setContentPane(bubblePanel);
        bubble.setSize(BUBBLE_WIDTH, BUBBLE_HEIGHT);
        bubble.setAlwaysOnTop(true);
        bubble.setFocusableWindowState(false);
        bubbleHideTimer = new Timer(4000, e -> bubble.setVisible(false));
        bubbleHideTimer.setRepeats(false);

        setBackground(new Color(0, 0, 0, 0));
        setAlwaysOnTop(true);
        setFocusableWindowState(false);
        setContentPane(canvas);
        setSize(PET_WIDTH, PET_HEIGHT);

        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
            .getMaximumWindowBounds();
        int startX = screen.x + 40 + slot * 145;
        int maxX = screen.x + Math.max(0, screen.width - PET_WIDTH);
        int startY = screen.y + Math.max(0, screen.height - PET_HEIGHT - 12);
        setLocation(Math.min(startX, maxX), startY);

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (maybeShowPopup(e)) return;
                if (SwingUtilities.isLeftMouseButton(e)) {
                    dragging = true;
                    dragOffset = e.getPoint();
                }
            }
            @Override
            public void mouseDragged(MouseEvent e) {
                if (!dragging || dragOffset == null) return;
                Point cursor = e.getLocationOnScreen();
                setLocation(cursor.x - dragOffset.x, cursor.y - dragOffset.y);
                updateBubblePosition();
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                if (maybeShowPopup(e)) return;
                if (dragging) {
                    dragging = false;
                    dragOffset = null;
                    keepInsideScreen();
                }
            }
        };
        canvas.addMouseListener(mouse);
        canvas.addMouseMotionListener(mouse);
        canvas.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        walkTimer = new Timer(35, e -> { walkOneStep(); updateBubblePosition(); });
        walkTimer.start();
    }

    private static String imageFilename(String characterId) {
        switch (characterId.toUpperCase(java.util.Locale.ROOT)) {
            case "CHIIKAWA": return "chiikawa.png";
            case "HACHIWARE": return "hachiware.png";
            case "USAGI": return "usagi.png";
            default: throw new IllegalArgumentException("지원하지 않는 캐릭터 ID: " + characterId);
        }
    }

    private boolean maybeShowPopup(MouseEvent e) {
        if (!e.isPopupTrigger()) return false;
        JPopupMenu menu = new JPopupMenu();
        JMenuItem walk = new JMenuItem(movementEnabled ? "자동 걷기 중지" : "자동 걷기 시작");
        walk.addActionListener(ev -> setMovementEnabled(!movementEnabled));
        JMenuItem remove = new JMenuItem("이 캐릭터 제거");
        remove.addActionListener(ev -> onRemove.run());
        menu.add(walk);
        menu.add(remove);
        menu.show(canvas, e.getX(), e.getY());
        return true;
    }

    private void walkOneStep() {
        if (dragging || !movementEnabled || !isVisible()) return;
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
            .getMaximumWindowBounds();
        int minX = screen.x;
        int maxX = screen.x + Math.max(0, screen.width - getWidth());
        int nextX = getX() + direction;
        if (nextX > maxX) {
            nextX = maxX;
            direction = -Math.abs(direction);
            canvas.repaint();
        } else if (nextX < minX) {
            nextX = minX;
            direction = Math.abs(direction);
            canvas.repaint();
        }
        setLocation(nextX, getY());
    }

    private void keepInsideScreen() {
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
            .getMaximumWindowBounds();
        int maxX = screen.x + Math.max(0, screen.width - getWidth());
        int maxY = screen.y + Math.max(0, screen.height - getHeight());
        setLocation(Math.max(screen.x, Math.min(getX(), maxX)),
                    Math.max(screen.y, Math.min(getY(), maxY)));
    }

    public void showChatBubble(String sender, String message) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> showChatBubble(sender, message));
            return;
        }
        if (sender == null || message == null) return;
        String content = sender + " : " + message;
        if (content.length() > 150) content = content.substring(0, 150) + "...";
        bubbleText.setText(content);
        updateBubblePosition();
        if (isVisible()) {
            bubble.setVisible(true);
            bubbleHideTimer.restart();
        }
    }

    private void updateBubblePosition() {
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
            .getMaximumWindowBounds();
        int desiredX = getX() + getWidth() / 2 - BUBBLE_WIDTH / 2;
        int desiredY = getY() - BUBBLE_HEIGHT - 12;
        int x = Math.max(screen.x, Math.min(desiredX,
            screen.x + screen.width - BUBBLE_WIDTH));
        int y = Math.max(screen.y, Math.min(desiredY,
            screen.y + screen.height - BUBBLE_HEIGHT));
        bubble.setLocation(x, y);
    }

    public void hideBubble() {
        bubbleHideTimer.stop();
        bubble.setVisible(false);
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (!visible && bubble != null) bubble.setVisible(false);
    }

    public void setMovementEnabled(boolean enabled) {
        movementEnabled = enabled;
    }

    public void closePet() {
        walkTimer.stop();
        bubbleHideTimer.stop();
        bubble.dispose();
        dispose();
    }

    private class PetCanvas extends JPanel {
        private static final long serialVersionUID = 1L;
        PetCanvas() { setOpaque(false); }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                   RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                   RenderingHints.VALUE_ANTIALIAS_ON);
                if (direction >= 0) {
                    g.drawImage(petImage, 3, 0, IMAGE_SIZE, IMAGE_SIZE, null);
                } else {
                    // 왼쪽으로 이동할 때 좌우 반전 (원본 이미지는 변경하지 않음).
                    g.drawImage(petImage, 3 + IMAGE_SIZE, 0, -IMAGE_SIZE, IMAGE_SIZE, null);
                }
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
                int textWidth = g.getFontMetrics().stringWidth(nickname);
                int x = Math.max(1, (getWidth() - textWidth) / 2);
                g.setColor(new Color(255, 255, 255, 235));
                g.drawString(nickname, x + 1, 141);
                g.setColor(new Color(40, 37, 38));
                g.drawString(nickname, x, 140);
            } finally {
                g.dispose();
            }
        }
    }
}
