package chiikawatalk.server;

import chiikawatalk.common.AppConfig;
import javax.swing.SwingUtilities;

//ChatServer.java (Java Chatting Server)

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.Vector;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

public class ChatServer extends JFrame {

    private static final long serialVersionUID = 1L;
    private JPanel contentPane;
    JTextArea textArea;
    private JTextField txtPortNumber;

    private ServerSocket socket; // 서버소켓
    private Vector<ClientService> UserVec = new Vector<>(); // 연결된 사용자를 저장할 벡터, ArrayList와 같이 동적 배열을 만들어주는 컬렉션 객체

    // 12주차: 서버 접속자와 채팅방 목록은 별도로 관리한다.
    private final Vector<ChatRoom> RoomVec = new Vector<>();
    private int nextRoomId = 1;
    private ServerStore store;

    // 현재 같은 서버에 로그인한 사용자만 자동 친구로 표시한다.
    private String onlineName(String name) {
        for (ClientService user : UserVec) {
            if (user.getUserName().equalsIgnoreCase(name)) return user.getUserName();
        }
        return null;
    }

    private String friendListMessage(ClientService user) {
        StringBuilder result = new StringBuilder("FRIENDS");
        for (ClientService other : UserVec) {
            if (other != user) result.append("\n").append(other.getUserName()).append("|1");
        }
        return result.toString();
    }

    synchronized void sendFriends(ClientService user) {
        user.WriteOne(friendListMessage(user));
    }

    private void broadcastFriends() {
        for (ClientService user : getUsers()) sendFriends(user);
    }

    /**
     * Launch the application.
     */
    public static void main(String[] args) {   // 스윙 비주얼 디자이너를 이용해 GUI를 만들면 자동으로 생성되는 main 함수
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                try {
                    ChatServer frame = new ChatServer();      // ChatServer 클래스의 객체 생성
                    frame.setVisible(true);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public ChatServer() {
        setTitle("치이카와 톡 - 서버");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setBounds(100, 100, 338, 386);
        contentPane = new JPanel();
        contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
        setContentPane(contentPane);
        contentPane.setLayout(null);

        JScrollPane scrollPane = new JScrollPane();
        scrollPane.setBounds(12, 10, 300, 244);
        contentPane.add(scrollPane);

        textArea = new JTextArea();
        textArea.setEditable(false);
        scrollPane.setViewportView(textArea);

        JLabel lblNewLabel = new JLabel("Port Number");
        lblNewLabel.setBounds(12, 264, 87, 26);
        contentPane.add(lblNewLabel);

        txtPortNumber = new JTextField();
        txtPortNumber.setHorizontalAlignment(SwingConstants.CENTER);
        txtPortNumber.setText(String.valueOf(AppConfig.PORT));
        txtPortNumber.setBounds(111, 264, 199, 26);
        contentPane.add(txtPortNumber);
        txtPortNumber.setColumns(10);

        JButton btnServerStart = new JButton("Server Start");
        btnServerStart.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    int port = Integer.parseInt(txtPortNumber.getText().trim());
                    if (port < 1 || port > 65535) throw new NumberFormatException();
                    // 동일한 포트의 저장 파일을 불러온다. 포트가 열리지 않으면 시작하지 않는다.
                    ServerSocket candidate = new ServerSocket(port);
                    try {
                        store = new ServerStore(Path.of("data", "chiikawatalk-" + port + ".bin"));
                        RoomVec.clear(); RoomVec.addAll(store.rooms);
                        nextRoomId = 1;
                        for (ChatRoom room : RoomVec) nextRoomId = Math.max(nextRoomId, room.getRoomId() + 1);
                        socket = candidate;
                    } catch (IOException ex) { candidate.close(); throw ex; }
                } catch (NumberFormatException | IOException e1) {
                    AppendText("[오류] 서버 시작 실패: " + e1.getMessage());
                    return;
                }
                AppendText("Chat Server Running..");
                btnServerStart.setText("Chat Server Running..");
                btnServerStart.setEnabled(false); // 서버를 더이상 실행시키지 못 하게 막는다
                txtPortNumber.setEnabled(false); // 더이상 포트번호 수정못 하게 막는다
                AcceptServer accept_server = new AcceptServer();   // 멀티 스레드 객체 생성
                accept_server.start();
            }
        });
        btnServerStart.setBounds(12, 300, 300, 35);
        contentPane.add(btnServerStart);
    }

    
    // 새로운 참가자 accept() 하고 user thread를 새로 생성한다. 한번 만들어서 계속 사용하는 스레드
    class AcceptServer extends Thread {
        public void run() {
            while (true) { // 사용자 접속을 계속해서 받기 위해 while문
                try {
                    AppendText("Waiting clients ...");
                    Socket client_socket = socket.accept(); // accept가 일어나기 전까지는 무한 대기중
                    AppendText("새로운 참가자 from " + client_socket);
                    // User 당 하나씩 Thread 생성
                    ClientService new_user = new ClientService(ChatServer.this, client_socket);
                    new_user.start(); // 만든 객체의 스레드 실행
                } catch (IOException e) {
                    AppendText("!!!! accept 에러 발생... !!!!");
                    break;
                }
            }
        }
    }
    // 지금은 보다 심플한 코드를 위해 서버는 계속 켜져 있는 것으로 가정하였으나(AcceptServer 스레스 종료 X)
    // 서버를 정상적으로 종료하고 싶은 경우, 서버 GUI에 종료 버튼을 만들어 서버소켓을 닫는 스레드를 추가로 만들거나,
    // 또는 GUI 창이 닫히는 순간(addWindowListener의 windowClosing 메서드 등에서)
    // ServerSocket.close()를 호출하여 accept()를 깨워서 AcceptServer를 종료하는 방법이나 플래그 신호 사용 방법 등이 있을 수 있음
    
    
    // JtextArea에 문자열을 출력
    public void AppendText(String str) {
        SwingUtilities.invokeLater(() -> {
            textArea.append(str + "\n");
            textArea.setCaretPosition(textArea.getDocument().getLength());
        });
    }

    // 계정 확인과 중복 접속 검사를 서버에서 처리한다.
    synchronized boolean authenticate(ClientService user, String name, String password, boolean signup) throws IOException {
        if (onlineName(name) != null) { user.WriteOne("AUTH_ERROR|이미 접속 중인 별명입니다."); return false; }
        if (signup) {
            if (store.canonicalName(name) != null) { user.WriteOne("AUTH_ERROR|이미 등록된 별명입니다."); return false; }
            store.addAccount(name, password);
        } else if (!store.matches(name, password)) {
            user.WriteOne("AUTH_ERROR|별명 또는 비밀번호가 올바르지 않습니다."); return false;
        }
        user.setUserName(store.canonicalName(name));
        // 인증 결과는 친구/방 목록보다 먼저 전송한다.
        if (!user.WriteOne("AUTH_OK|" + user.getUserName())) return false;
        UserVec.add(user);
        broadcastFriends();
        AppendText("사용자 입장. 현재 참가자 수 " + UserVec.size());
        return true;
    }

    synchronized boolean unregister(ClientService user) {
        boolean removed = UserVec.remove(user);
        if (removed) {
            AppendText("사용자 퇴장. 남은 참가자 수 " + UserVec.size());
            broadcastFriends();
        }
        return removed;
    }

    // 전체 전송 중 접속 목록이 변경되어도 안전하게 순회
    ClientService[] getUsers() {
        return UserVec.toArray(new ClientService[0]);
    }

    // 방 목록과 입퇴장 처리를 같은 서버 락에서 순서대로 처리한다.
    private ChatRoom findRoom(int roomId) {
        for (ChatRoom room : RoomVec) {
            if (room.getRoomId() == roomId) return room;
        }
        return null;
    }

    private String roomListMessage(ClientService user) {
        StringBuilder result = new StringBuilder("ROOMS");
        for (ChatRoom room : RoomVec) {
            if (!room.canJoin(user.getUserName())) continue;
            result.append("\n").append(room.getRoomId()).append("|")
                  .append(room.getRoomName()).append("|").append(room.getUserCount());
        }
        return result.toString();
    }

    synchronized void sendRooms(ClientService user) {
        user.WriteOne(roomListMessage(user));
    }

    private void broadcastRooms() {
        // 초대 권한에 따라 사용자마다 목록을 생성
        for (ClientService user : getUsers()) user.WriteOne(roomListMessage(user));
    }

    synchronized void createRoom(ClientService user, String payload) {
        String[] parts = payload.split("\\|", -1);
        String roomName = parts[0].trim();
        if (roomName.isEmpty() || roomName.length() > 30 || roomName.contains("|")
                || roomName.contains("\n") || roomName.contains("\r")) {
            user.WriteOne("ERROR|방 이름은 1~30글자이며 | 또는 줄바꿈을 사용할 수 없습니다.");
            return;
        }
        if (RoomVec.size() >= 100) {
            user.WriteOne("ERROR|채팅방은 최대 100개까지 만들 수 있습니다.");
            return;
        }
        // 초대 시점에 같은 서버에 접속 중인 다른 사용자만 초대 가능.
        Set<String> invited = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        if (parts.length < 2 || parts.length > 101) {
            user.WriteOne("ERROR|초대할 친구를 한 명 이상 선택하세요."); return;
        }
        for (int i = 1; i < parts.length; i++) {
            String name = parts[i].trim();
            String online = onlineName(name);
            if (online == null || online.equalsIgnoreCase(user.getUserName())) {
                user.WriteOne("ERROR|현재 접속 중인 다른 사용자만 초대할 수 있습니다."); return;
            }
            invited.add(online);
        }
        ChatRoom room = new ChatRoom(nextRoomId++, roomName, user.getUserName(), invited);
        RoomVec.add(room);
        store.rooms.add(room);
        try { store.save(); }
        catch (IOException e) {
            RoomVec.remove(room); store.rooms.remove(room);
            user.WriteOne("ERROR|채팅방 저장 실패"); return;
        }
        AppendText("[방 생성] " + roomName);
        user.WriteOne("INFO|채팅방을 생성했습니다. 목록에서 입장하세요.");
        broadcastRooms();
    }

    synchronized void joinRoom(ClientService user, int roomId, String character) {
        ChatRoom room = findRoom(roomId);
        if (room == null || !room.canJoin(user.getUserName())) {
            user.WriteOne("ERROR|초대받은 사용자만 입장할 수 있습니다."); return;
        }
        if (!ClientService.validCharacter(character)) {
            user.WriteOne("ERROR|캐릭터를 다시 선택하세요."); return;
        }
        if (user.getCurrentRoom() != null) {
            user.WriteOne("ERROR|현재 방에서 먼저 퇴장하세요."); return;
        }
        user.setCurrentRoom(room, character);
        room.addUser(user);
        user.WriteOne("JOINED|" + roomId + "|" + room.getRoomName() + "|" + character);
        // JOINED 뒤에 과거 대화를 같은 형식으로 재전송한다.
        for (String saved : room.getHistory()) user.WriteOne(saved);
        sendRoom(room, "NOTICE|" + roomId + "|" + user.getUserName() + "님이 입장했습니다.");
        sendMembers(room);
        broadcastRooms();
        AppendText("[방 입장] " + room.getRoomName() + " / " + user.getUserName());
    }

    synchronized void leaveRoom(ClientService user, boolean notifyUser) {
        ChatRoom room = user.getCurrentRoom();
        if (room == null) return;
        room.removeUser(user);
        user.setCurrentRoom(null, "");
        if (notifyUser) user.WriteOne("LEFT|" + room.getRoomId());
        sendRoom(room, "NOTICE|" + room.getRoomId() + "|" + user.getUserName() + "님이 퇴장했습니다.");
        sendMembers(room);
        broadcastRooms();
        AppendText("[방 퇴장] " + room.getRoomName() + " / " + user.getUserName());
    }

    synchronized void disconnect(ClientService user) {
        unregister(user);
        leaveRoom(user, false);
    }

    private void sendRoom(ChatRoom room, String message) {
        for (ClientService user : room.getUsers()) user.WriteOne(message);
    }

    // 각 방의 참여자 목록을 새로 전송한다. 13주차 캐릭터 표시에도 활용 가능.
    private void sendMembers(ChatRoom room) {
        for (ClientService recipient : room.getUsers()) {
            recipient.WriteOne("MEMBERS|" + room.getRoomId());
            for (ClientService member : room.getUsers()) {
                recipient.WriteOne("MEMBER|" + room.getRoomId() + "|" + member.getUserName()
                        + "|" + member.getCharacterId());
            }
        }
    }

    private boolean saveMessage(ChatRoom room, String message, ClientService sender) {
        Vector<String> history = room.getHistory();
        String removed = history.size() >= ChatRoom.HISTORY_LIMIT ? history.remove(0) : null;
        history.add(message);
        try { store.save(); return true; }
        catch (IOException e) {
            history.remove(history.size() - 1);
            if (removed != null) history.add(0, removed);
            sender.WriteOne("ERROR|대화 저장 실패"); return false;
        }
    }

    synchronized void sendChat(ClientService sender, String message) {
        ChatRoom room = sender.getCurrentRoom();
        if (room == null) { sender.WriteOne("ERROR|채팅방에 먼저 입장하세요."); return; }
        if (message.length() > 1000) { sender.WriteOne("ERROR|메시지는 최대 1000글자입니다."); return; }
        String packet = "CHAT|" + room.getRoomId() + "|" + sender.getUserName() + "|" + message;
        if (!saveMessage(room, packet, sender)) return;
        sendRoom(room, packet);
        AppendText("[" + room.getRoomName() + "] " + sender.getUserName() + " : " + message);
    }

    synchronized void sendEmoji(ClientService sender, String emojiId) {
        ChatRoom room = sender.getCurrentRoom();
        if (room == null) { sender.WriteOne("ERROR|채팅방에 먼저 입장하세요."); return; }
        if (!ClientService.validEmoji(emojiId)) { sender.WriteOne("ERROR|지원하지 않는 이모티콘입니다."); return; }
        String packet = "EMOJI|" + room.getRoomId() + "|" + sender.getUserName() + "|" + emojiId;
        if (!saveMessage(room, packet, sender)) return;
        sendRoom(room, packet);
        AppendText("[" + room.getRoomName() + "] " + sender.getUserName() + " : " + emojiId);
    }

}
