package chiikawatalk.server;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Vector;
import chiikawatalk.common.AppConfig;

public class ChatServer {

    // 12강의 Vector 방식: 현재 연결된 클라이언트 저장
    private static final Vector<ClientHandler> clients = new Vector<>();

    public static void main(String[] args) {
        try (ServerSocket listener = new ServerSocket(AppConfig.PORT)) {
            System.out.println("치이카와 톡 서버 실행 - 포트 " + AppConfig.PORT);

<<<<<<< Updated upstream
            while (true) {
                Socket socket = listener.accept();
                // 클라이언트마다 담당 스레드 생성
                new Thread(new ClientHandler(socket)).start();
=======
    private ServerSocket socket; // 서버소켓
    private Vector<ClientService> UserVec = new Vector<>(); // 연결된 사용자를 저장할 벡터, ArrayList와 같이 동적 배열을 만들어주는 컬렉션 객체

    // 12주차: 서버 접속자와 채팅방 목록은 별도로 관리한다.
    private final Vector<ChatRoom> RoomVec = new Vector<>();
    private int nextRoomId = 1;

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
>>>>>>> Stashed changes
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 접속 중인 모든 클라이언트에 동일한 메시지를 전송
    private static void broadcast(String msg) {
        for (ClientHandler client : clients.toArray(new ClientHandler[0])) {
            client.send(msg);
        }
        System.out.println(msg);
    }

    private static class ClientHandler implements Runnable {
        private final Socket socket;
        private BufferedWriter out;
        private String nickname;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            boolean joined = false;
            try (Socket client = socket;
                 BufferedReader in = new BufferedReader(new InputStreamReader(
                         client.getInputStream(), StandardCharsets.UTF_8));
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                         client.getOutputStream(), StandardCharsets.UTF_8))) {

                out = writer;
                // 클라이언트가 처음 보내는 한 줄은 닉네임
                String name = in.readLine();
                if (name == null || name.trim().isEmpty()) return;
                nickname = name.trim();

                clients.add(this);
                joined = true;
                broadcast("[입장] " + nickname + "님이 입장했습니다.");

                String msg;
                while ((msg = in.readLine()) != null) {
                    if (!msg.trim().isEmpty()) {
                        broadcast(nickname + " : " + msg);
                    }
                }

            } catch (IOException e) {
                System.out.println("연결 종료: " + e.getMessage());
            } finally {
                if (joined) {
                    clients.remove(this);
                    broadcast("[퇴장] " + nickname + "님이 퇴장했습니다.");
                }
            }
        }

        // 각 클라이언트의 출력 스트림에 메시지 기록
        synchronized void send(String msg) {
            if (out == null) return;
            try {
                out.write(msg);
                out.newLine();
                out.flush();
            } catch (IOException e) {
                System.out.println("메시지 전송 실패: " + e.getMessage());
            }
        }
    }

    // 방 목록과 입퇴장 처리를 같은 서버 락에서 순서대로 처리한다.
    private ChatRoom findRoom(int roomId) {
        for (ChatRoom room : RoomVec) {
            if (room.getRoomId() == roomId) return room;
        }
        return null;
    }

    private String roomListMessage() {
        StringBuilder result = new StringBuilder("ROOMS");
        for (ChatRoom room : RoomVec) {
            result.append("\n").append(room.getRoomId()).append("|")
                  .append(room.getRoomName()).append("|").append(room.getUserCount());
        }
        return result.toString();
    }

    synchronized void sendRooms(ClientService user) {
        user.WriteOne(roomListMessage());
    }

    private void broadcastRooms() {
        String message = roomListMessage();
        for (ClientService user : getUsers()) user.WriteOne(message);
    }

    synchronized void createRoom(ClientService user, String roomName) {
        if (roomName.isEmpty() || roomName.length() > 30 || roomName.contains("|")
                || roomName.contains("\n") || roomName.contains("\r")) {
            user.WriteOne("ERROR|방 이름은 1~30글자이며 | 또는 줄바꿈을 사용할 수 없습니다.");
            return;
        }
        if (RoomVec.size() >= 100) {
            user.WriteOne("ERROR|채팅방은 최대 100개까지 만들 수 있습니다.");
            return;
        }
        ChatRoom room = new ChatRoom(nextRoomId++, roomName);
        RoomVec.add(room);
        AppendText("[방 생성] " + roomName);
        user.WriteOne("INFO|채팅방을 생성했습니다. 목록에서 입장하세요.");
        broadcastRooms();
    }

    synchronized void joinRoom(ClientService user, int roomId, String character) {
        ChatRoom room = findRoom(roomId);
        if (room == null) { user.WriteOne("ERROR|존재하지 않는 채팅방입니다."); return; }
        if (!ClientService.validCharacter(character)) {
            user.WriteOne("ERROR|캐릭터를 다시 선택하세요."); return;
        }
        if (user.getCurrentRoom() != null) {
            user.WriteOne("ERROR|현재 방에서 먼저 퇴장하세요."); return;
        }
        user.setCurrentRoom(room, character);
        room.addUser(user);
        user.WriteOne("JOINED|" + roomId + "|" + room.getRoomName() + "|" + character);
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

    synchronized void sendChat(ClientService sender, String message) {
        ChatRoom room = sender.getCurrentRoom();
        if (room == null) { sender.WriteOne("ERROR|채팅방에 먼저 입장하세요."); return; }
        if (message.length() > 1000) { sender.WriteOne("ERROR|메시지는 최대 1000글자입니다."); return; }
        sendRoom(room, "CHAT|" + room.getRoomId() + "|" + sender.getUserName() + "|" + message);
        AppendText("[" + room.getRoomName() + "] " + sender.getUserName() + " : " + message);
    }

    synchronized void sendEmoji(ClientService sender, String emojiId) {
        ChatRoom room = sender.getCurrentRoom();
        if (room == null) { sender.WriteOne("ERROR|채팅방에 먼저 입장하세요."); return; }
        if (!ClientService.validEmoji(emojiId)) { sender.WriteOne("ERROR|지원하지 않는 이모티콘입니다."); return; }
        sendRoom(room, "EMOJI|" + room.getRoomId() + "|" + sender.getUserName() + "|" + emojiId);
        AppendText("[" + room.getRoomName() + "] " + sender.getUserName() + " : " + emojiId);
    }

}
