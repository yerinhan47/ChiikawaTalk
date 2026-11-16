package chiikawatalk.client;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import javax.swing.*;
import javax.swing.text.*;

// 교수님 View 구조 유지: 소켓, ListenNetwork, Myaction, SendMessage, AppendText
public class ChatClientView extends JFrame {
    private final JPanel contentPane;
    private final CardLayout cards = new CardLayout();
    private final RoomListPanel roomList;
    private final JTextField txtInput;
    private final String UserName;
    private final JButton btnSend;
    private final JButton btnEmoji;
    private final JButton btnLeave;
    private final JTextPane textArea;
    private final JLabel lblUserName;
    private final JLabel roomTitle;
    private final DefaultListModel<String> members = new DefaultListModel<>();
    private Socket socket;
    private InputStream is;
    private OutputStream os;
    private DataInputStream dis;
    private DataOutputStream dos;
    private volatile boolean closed;
    // 화면 상태는 EDT에서만 수정
    private int currentRoomId = -1;
    private boolean serverError;

    public ChatClientView(String username, String ip_addr, String port_no, ChatClientMain main) throws IOException {
        UserName = username;
        setTitle("치이카와 톡 - " + username);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(620, 470);
        setLocationRelativeTo(main);
        setResizable(false);
        contentPane = new JPanel(cards);
        setContentPane(contentPane);
        roomList = new RoomListPanel(this);
        contentPane.add(roomList, "ROOMS");

        // 기존 채팅 GUI는 좌표 배치 유지, 참여자와 이모티콘 영역 추가
        JPanel chatPanel = new JPanel(null);
        roomTitle = new JLabel("채팅방");
        roomTitle.setBounds(12, 8, 430, 28);
        chatPanel.add(roomTitle);
        btnLeave = new JButton("방 나가기");
        btnLeave.setBounds(460, 8, 120, 28);
        chatPanel.add(btnLeave);

        textArea = new JTextPane();
        textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setBounds(12, 42, 412, 320);
        chatPanel.add(scrollPane);
        JLabel memberTitle = new JLabel("참여자 / 캐릭터");
        memberTitle.setBounds(435, 42, 155, 24);
        chatPanel.add(memberTitle);
        JScrollPane memberPane = new JScrollPane(new JList<>(members));
        memberPane.setBounds(435, 70, 150, 292);
        chatPanel.add(memberPane);

        lblUserName = new JLabel(username + ">");
        lblUserName.setBounds(12, 375, 83, 35);
        chatPanel.add(lblUserName);
        txtInput = new JTextField();
        txtInput.setBounds(95, 375, 330, 35);
        chatPanel.add(txtInput);
        btnEmoji = new JButton("이모티콘");
        btnEmoji.setBounds(432, 375, 85, 35);
        chatPanel.add(btnEmoji);
        btnSend = new JButton("Send");
        btnSend.setBounds(522, 375, 63, 35);
        chatPanel.add(btnSend);
        contentPane.add(chatPanel, "CHAT");
        cards.show(contentPane, "ROOMS");

        // 기존 요청대로 제한 시간 및 별도 로그인 성공 응답 확인 없음
        try {
            socket = new Socket(ip_addr, Integer.parseInt(port_no));
            is = socket.getInputStream();
            dis = new DataInputStream(is);
            os = socket.getOutputStream();
            dos = new DataOutputStream(os);
            dos.writeUTF("/login " + UserName);
            dos.flush();
            Myaction action = new Myaction();
            btnSend.addActionListener(action);
            txtInput.addActionListener(action);
            btnEmoji.addActionListener(e -> chooseEmoji());
            btnLeave.addActionListener(e -> SendMessage("/leave"));
            addWindowListener(new WindowAdapter() {
                @Override public void windowClosed(WindowEvent e) {
                    closeSocket();
                    main.setVisible(true);
                }
            });
            ListenNetwork net = new ListenNetwork();
            net.start();
        } catch (IOException | RuntimeException e) {
            closeSocket(); dispose(); throw e;
        }
    }

    class ListenNetwork extends Thread {
        public void run() {
            try {
                while (true) {
                    String msg = dis.readUTF();
                    SwingUtilities.invokeLater(() -> handleMessage(msg));
                }
            } catch (IOException e) {
                if (!closed) SwingUtilities.invokeLater(() -> {
                    if (!serverError) showStatus("서버 연결 종료");
                });
            } finally {
                closeSocket();
                SwingUtilities.invokeLater(() -> {
                    btnSend.setEnabled(false); txtInput.setEnabled(false);
                    btnEmoji.setEnabled(false); btnLeave.setEnabled(false);
                    roomList.setConnected(false);
                });
            }
        }
    }

    // 서버가 보낸 종류별 메시지를 GUI에 반영 (EDT)
    private void handleMessage(String msg) {
        if (msg.equals("ROOMS") || msg.startsWith("ROOMS\n")) {
            serverError = false;
            roomList.updateRooms(msg); return;
        }
        String[] data = msg.split("\\|", 4);
        if (data.length < 2) return;
        try {
            switch (data[0]) {
                case "ERROR":
                    serverError = true;
                    showStatus(msg.substring(msg.indexOf('|') + 1)); break;
                case "INFO":
                    serverError = false;
                    showStatus(msg.substring(msg.indexOf('|') + 1)); break;
                case "JOINED":
                    if (data.length != 4) return;
                    serverError = false;
                    currentRoomId = Integer.parseInt(data[1]);
                    roomTitle.setText(data[2] + " / " + CharacterSelectDialog.characterName(data[3]));
                    textArea.setText(""); members.clear();
                    cards.show(contentPane, "CHAT");
                    txtInput.requestFocusInWindow(); break;
                case "LEFT":
                    if (Integer.parseInt(data[1]) != currentRoomId) return;
                    currentRoomId = -1; members.clear();
                    cards.show(contentPane, "ROOMS"); break;
                case "NOTICE":
                    if (data.length >= 3 && Integer.parseInt(data[1]) == currentRoomId)
                        AppendText("[안내] " + data[2] + "\n");
                    break;
                case "CHAT":
                    if (data.length == 4 && Integer.parseInt(data[1]) == currentRoomId)
                        AppendText(data[2] + " : " + data[3] + "\n");
                    break;
                case "EMOJI":
                    if (data.length == 4 && Integer.parseInt(data[1]) == currentRoomId)
                        AppendEmoji(data[2], data[3]);
                    break;
                case "MEMBERS":
                    if (Integer.parseInt(data[1]) == currentRoomId) members.clear();
                    break;
                case "MEMBER":
                    if (data.length == 4 && Integer.parseInt(data[1]) == currentRoomId)
                        members.addElement(data[2] + " / " + CharacterSelectDialog.characterName(data[3]));
                    break;
                default: AppendText(msg + "\n");
            }
        } catch (NumberFormatException ignored) { }
    }

    class Myaction implements ActionListener {
        @Override public void actionPerformed(ActionEvent e) {
            if (e.getSource() == btnSend || e.getSource() == txtInput) {
                String msg = txtInput.getText().trim();
                if (msg.isEmpty()) return;
                if (msg.length() > 1000) { showStatus("메시지는 최대 1000글자입니다."); return; }
                if ("/exit".equalsIgnoreCase(msg)) {
                    SendMessage("/exit"); closeSocket(); dispose(); return;
                }
                // /로 시작하는 일반 문장도 명령어로 오인되지 않도록 /chat 사용
                SendMessage("/chat " + msg);
                txtInput.setText(""); txtInput.requestFocusInWindow();
            }
        }
    }

    private void chooseEmoji() {
        String[] ids = {"chiikawa_happy", "hachiware_love", "usagi_surprise"};
        String[] names = {"치이카와 기쁨", "하치와레 하트", "우사기 놀람"};
        JDialog dialog = new JDialog(this, "이모티콘 선택", true);
        JPanel panel = new JPanel(new GridLayout(1, 3, 8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        for (int i = 0; i < ids.length; i++) {
            final String id = ids[i];
            JButton button = new JButton(names[i], CharacterSelectDialog.loadIcon("emoticons/" + id + ".png", 72));
            button.setHorizontalTextPosition(SwingConstants.CENTER);
            button.setVerticalTextPosition(SwingConstants.BOTTOM);
            button.addActionListener(e -> { SendMessage("/emoji " + id); dialog.dispose(); });
            panel.add(button);
        }
        dialog.add(panel); dialog.pack(); dialog.setLocationRelativeTo(this); dialog.setVisible(true);
    }

    // JTextArea 대신 JTextPane: 텍스트와 이미지 출력
    public void AppendText(String msg) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> AppendText(msg)); return;
        }
        StyledDocument doc = textArea.getStyledDocument();
        try { doc.insertString(doc.getLength(), msg, null); }
        catch (BadLocationException e) { throw new IllegalStateException(e); }
        textArea.setCaretPosition(doc.getLength());
    }
    private void AppendEmoji(String nickname, String id) {
        AppendText(nickname + " : ");
        ImageIcon icon = CharacterSelectDialog.loadIcon("emoticons/" + id + ".png", 64);
        if (icon == null) { AppendText("[" + id + "]\n"); return; }
        textArea.setCaretPosition(textArea.getDocument().getLength());
        textArea.insertIcon(icon);
        AppendText("\n");
    }
    private void showStatus(String msg) {
        if (currentRoomId == -1) roomList.setStatus(msg);
        else AppendText("[안내] " + msg + "\n");
    }
    public void SendMessage(String msg) {
        if (closed) return;
        try { dos.writeUTF(msg); dos.flush(); }
        catch (IOException e) {
            showStatus("메시지 전송 실패"); closeSocket();
            btnSend.setEnabled(false); txtInput.setEnabled(false);
            btnEmoji.setEnabled(false); btnLeave.setEnabled(false); roomList.setConnected(false);
        }
    }
    private void closeSocket() {
        closed = true;
        if (socket != null) try { socket.close(); } catch (IOException ignored) { }
    }
}
