package chiikawatalk.client;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

// 첫 화면: 내 프로필, 친구 목록, 온라인 상태
public class FriendListPanel extends JPanel {
    private final DefaultListModel<FriendItem> model = new DefaultListModel<>();
    private final JList<FriendItem> list = new JList<>(model);
    private final JLabel profileStatus = new JLabel("내 프로필 · 접속 중");
    private final JLabel count = new JLabel("친구 0명");
    private final JLabel status = new JLabel("친구 목록을 불러오는 중...");
    private final JButton refresh = new JButton("새로고침");
    private boolean connected = true;

    public FriendListPanel(ChatClientView view, String nickname) {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        JLabel title = new JLabel("친구");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        JPanel top = new JPanel(new BorderLayout(6, 8));
        top.add(title, BorderLayout.NORTH);
        JPanel profile = new JPanel(new BorderLayout(12, 4));
        profile.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 4));
        JLabel avatar = new JLabel(CharacterSelectDialog.loadIcon("characters/chiikawa.png", 64));
        profile.add(avatar, BorderLayout.WEST);
        JPanel info = new JPanel(new GridLayout(2, 1));
        JLabel name = new JLabel(nickname);
        name.setFont(name.getFont().deriveFont(Font.BOLD, 18f));
        info.add(name); info.add(profileStatus);
        profile.add(info, BorderLayout.CENTER);
        top.add(profile, BorderLayout.CENTER); top.add(count, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);
        list.setFixedCellHeight(38);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object value,
                    int index, boolean selected, boolean focus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(l, value, index, selected, focus);
                label.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
                return label;
            }
        });
        add(new JScrollPane(list), BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(5, 5));
        JPanel buttons = new JPanel(); buttons.add(refresh);
        bottom.add(status, BorderLayout.NORTH); bottom.add(buttons, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);
        refresh.addActionListener(e -> view.SendMessage("/friends"));
    }

    public void updateFriends(String message) {
        model.clear();
        String[] lines = message.split("\n");
        for (int i = 1; i < lines.length; i++) {
            String[] fields = lines[i].split("\\|", -1);
            if (fields.length == 2) model.addElement(new FriendItem(fields[0], "1".equals(fields[1])));
        }
        count.setText("친구 " + model.size() + "명");
        if (connected) setStatus(model.isEmpty() ? "현재 접속 중인 다른 사용자가 없습니다." : "같은 서버의 접속자가 자동으로 표시됩니다.");
    }
    public List<String> getFriendNames() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < model.size(); i++) names.add(model.get(i).name);
        return names;
    }
    public void setStatus(String text) { status.setText(text); }
    public void setConnected(boolean value) { connected = value; refresh.setEnabled(value); if (!value) { model.clear(); count.setText("친구 0명"); } profileStatus.setText(value ? "내 프로필 · 접속 중" : "내 프로필 · 연결 종료"); }
    private static class FriendItem {
        final String name; final boolean online;
        FriendItem(String name, boolean online) { this.name = name; this.online = online; }
        @Override public String toString() { return (online ? "● " : "○ ") + name + (online ? "  (온라인)" : "  (오프라인)"); }
    }
}
