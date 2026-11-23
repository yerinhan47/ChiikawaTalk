package chiikawatalk.client;

import javax.swing.*;
import java.awt.*;
import java.util.List;

// 채팅방 목록 화면: 네트워크는 ChatClientView의 SendMessage만 사용
public class RoomListPanel extends JPanel {
    private final DefaultListModel<RoomItem> model = new DefaultListModel<>();
    private final JList<RoomItem> list = new JList<>(model);
    private boolean connected = true;
    private final JLabel status = new JLabel("채팅방 목록을 불러오는 중...");
    private final JButton create = new JButton("방 생성");
    private final JButton join = new JButton("입장");
    private final JButton refresh = new JButton("새로고침");

    public RoomListPanel(ChatClientView view) {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        JLabel title = new JLabel("치이카와 톡 - 채팅방 목록");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 19f));
        add(title, BorderLayout.NORTH);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        add(new JScrollPane(list), BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(5, 5));
        JPanel buttons = new JPanel();
        buttons.add(create); buttons.add(join); buttons.add(refresh);
        bottom.add(status, BorderLayout.NORTH);
        bottom.add(buttons, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);

        create.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(this, "채팅방 이름을 입력하세요.");
            if (name == null) return;
            name = name.trim();
            if (name.isEmpty() || name.length() > 30 || name.contains("|")) {
                setStatus("방 이름은 1~30글자, | 제외"); return;
            }
            List<String> friends = view.getFriendNames();
            if (friends.isEmpty()) { setStatus("현재 접속 중인 다른 사용자가 없습니다."); return; }
            JList<String> inviteList = new JList<>(friends.toArray(new String[0]));
            inviteList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            inviteList.setVisibleRowCount(5);
            JPanel invitePanel = new JPanel(new BorderLayout(5, 5));
            invitePanel.add(new JLabel("초대할 친구 선택 (Ctrl/Shift로 여러 명 선택)"), BorderLayout.NORTH);
            invitePanel.add(new JScrollPane(inviteList), BorderLayout.CENTER);
            int result = JOptionPane.showConfirmDialog(this, invitePanel, "친구 초대", JOptionPane.OK_CANCEL_OPTION);
            if (result != JOptionPane.OK_OPTION) return;
            List<String> invited = inviteList.getSelectedValuesList();
            if (invited.isEmpty()) { setStatus("초대할 친구를 한 명 이상 선택하세요."); return; }
            view.SendMessage("/create " + name + "|" + String.join("|", invited));
        });
        join.addActionListener(e -> {
            RoomItem room = list.getSelectedValue();
            if (room == null) { setStatus("입장할 채팅방을 선택하세요."); return; }
            String character = CharacterSelectDialog.select(view);
            if (character != null) view.SendMessage("/join " + room.id + " " + character);
        });
        refresh.addActionListener(e -> view.SendMessage("/rooms"));
    }

    // ROOMS 응답: 방번호|이름|인원
    public void updateRooms(String message) {
        RoomItem selected = list.getSelectedValue();
        int selectedId = selected == null ? -1 : selected.id;
        model.clear();
        String[] lines = message.split("\n");
        for (int i = 1; i < lines.length; i++) {
            String[] data = lines[i].split("\\|", -1);
            if (data.length != 3) continue;
            try {
                RoomItem item = new RoomItem(Integer.parseInt(data[0]), data[1], Integer.parseInt(data[2]));
                model.addElement(item);
                if (item.id == selectedId) list.setSelectedIndex(model.size() - 1);
            } catch (NumberFormatException ignored) { }
        }
        if (connected) setStatus(model.isEmpty() ? "초대받거나 만든 방이 없습니다." : "채팅방 " + model.size() + "개");
    }

    public void setStatus(String message) { status.setText(message); }
    public void setConnected(boolean connected) {
        this.connected = connected;
        create.setEnabled(connected); join.setEnabled(connected); refresh.setEnabled(connected);
    }
    private static class RoomItem {
        final int id; final String name; final int count;
        RoomItem(int id, String name, int count) {
            this.id = id; this.name = name; this.count = count;
        }
        @Override public String toString() {
            return "[" + id + "] " + name + "  (" + count + "명)";
        }
    }
}
