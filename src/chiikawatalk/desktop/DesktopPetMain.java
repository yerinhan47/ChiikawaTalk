package chiikawatalk.desktop;

import java.awt.AWTException;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.net.URL;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/** Java 21 / Eclipse: Run As > Java Application */
public final class DesktopPetMain {
    private DesktopPetMain() { }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            DesktopPetManager manager = new DesktopPetManager();
            manager.addPet("치이카와", "CHIIKAWA");
            installTray(manager);
        });
    }

    private static void installTray(DesktopPetManager manager) {
        if (!SystemTray.isSupported()) {
            System.out.println("시스템 트레이를 사용할 수 없습니다. 캐릭터를 우클릭해 제거하세요.");
            return;
        }
        URL imageUrl = DesktopPetMain.class.getResource(
            "/chiikawatalk/resources/characters/chiikawa.png");
        if (imageUrl == null) return;
        Image iconImage = new ImageIcon(imageUrl).getImage();
        PopupMenu menu = new PopupMenu();
        MenuItem visibility = new MenuItem("캐릭터 숨기기");
        visibility.addActionListener(e -> {
            boolean next = !manager.isPetsVisible();
            manager.setPetsVisible(next);
            visibility.setLabel(next ? "캐릭터 숨기기" : "캐릭터 보이기");
        });
        MenuItem movement = new MenuItem("자동 걷기 중지");
        movement.addActionListener(e -> {
            boolean next = !manager.isMovementEnabled();
            manager.setMovementEnabled(next);
            movement.setLabel(next ? "자동 걷기 중지" : "자동 걷기 시작");
        });
        MenuItem addHachiware = new MenuItem("하치와레 추가");
        addHachiware.addActionListener(e -> manager.addPet("하치와레", "HACHIWARE"));
        MenuItem addUsagi = new MenuItem("우사기 추가");
        addUsagi.addActionListener(e -> manager.addPet("우사기", "USAGI"));
        MenuItem exit = new MenuItem("프로그램 종료");
        menu.add(visibility);
        menu.add(movement);
        menu.addSeparator();
        menu.add(addHachiware);
        menu.add(addUsagi);
        menu.addSeparator();
        menu.add(exit);
        TrayIcon trayIcon = new TrayIcon(iconImage, "DesktopChiikawa", menu);
        trayIcon.setImageAutoSize(true);
        exit.addActionListener(e -> {
            manager.clear();
            SystemTray.getSystemTray().remove(trayIcon);
            System.exit(0);
        });
        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException ex) {
            JOptionPane.showMessageDialog(null,
                "트레이 등록 실패: " + ex.getMessage());
        }
    }
}
