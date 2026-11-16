package chiikawatalk.client;

import java.awt.*;
import java.net.URL;
import javax.swing.*;

// 방 입장 직전 선택하는 캐릭터 팝업
public class CharacterSelectDialog extends JDialog {
    private String selected;
    private CharacterSelectDialog(JFrame owner) {
        super(owner, "캐릭터 선택", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel panel = new JPanel(new GridLayout(1, 3, 8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        addCharacter(panel, "CHIIKAWA", "치이카와", "chiikawa.png");
        addCharacter(panel, "HACHIWARE", "하치와레", "hachiware.png");
        addCharacter(panel, "USAGI", "우사기", "usagi.png");
        add(panel, BorderLayout.CENTER);
        JButton cancel = new JButton("취소");
        cancel.addActionListener(e -> dispose());
        add(cancel, BorderLayout.SOUTH);
        pack(); setLocationRelativeTo(owner);
    }
    private void addCharacter(JPanel panel, String id, String name, String file) {
        JButton button = new JButton(name, loadIcon("characters/" + file, 88));
        button.setHorizontalTextPosition(SwingConstants.CENTER);
        button.setVerticalTextPosition(SwingConstants.BOTTOM);
        button.addActionListener(e -> { selected = id; dispose(); });
        panel.add(button);
    }
    public static String select(JFrame owner) {
        CharacterSelectDialog dialog = new CharacterSelectDialog(owner);
        dialog.setVisible(true);
        return dialog.selected;
    }
    static ImageIcon loadIcon(String path, int size) {
        URL url = CharacterSelectDialog.class.getResource("/chiikawatalk/resources/" + path);
        if (url == null) return null;
        return new ImageIcon(new ImageIcon(url).getImage().getScaledInstance(size, size, Image.SCALE_SMOOTH));
    }
    static String characterName(String id) {
        switch (id) {
            case "CHIIKAWA": return "치이카와";
            case "HACHIWARE": return "하치와레";
            case "USAGI": return "우사기";
            default: return id;
        }
    }
}
