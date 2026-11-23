
package chiikawatalk.server;

import chiikawatalk.common.AppConfig;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;

public class ChatServer {

    private ServerSocket socket;
    private final Vector<ClientService> UserVec = new Vector<>();

    // 서버 접속자와 채팅방 목록은 별도로 관리
    private final Vector<ChatRoom> RoomVec = new Vector<>();
    private int nextRoomId = 1;
    private ServerStore store;

    // 서버 실행
    public static void main(String[] args) {
        ChatServer server = new ChatServer();
        server.startServer();
    }

    // 콘솔에서 서버 자동 시작
    public void startServer() {
        int port = AppConfig.PORT;

        try (ServerSocket serverSocket = new ServerSocket(port)) {

            socket = serverSocket;

            // 저장된 계정과 채팅방 불러오기
            store = new ServerStore(
                Path.of("data", "chiikawatalk-" + port + ".bin")
            );

            RoomVec.clear();
            RoomVec.addAll(store.rooms);

            nextRoomId = 1;
            for (ChatRoom room : RoomVec) {
                nextRoomId = Math.max(
                    nextRoomId,
                    room.getRoomId() + 1
                );
            }

            AppendText("=================================");
            AppendText("      치이카와 톡 서버 실행");
            AppendText("=================================");
            AppendText("포트 번호: " + port);
            AppendText("서버 시작 완료");
            AppendText("클라이언트 접속 대기 중...");
            AppendText("=================================");

            // 클라이언트 접속을 계속 대기
            while (true) {
                Socket clientSocket = socket.accept();

                AppendText("새로운 참가자: " + clientSocket);

                // 사용자마다 별도의 스레드 생성
                ClientService newUser =
                    new ClientService(this, clientSocket);

                newUser.start();
            }

        } catch (IOException e) {
            AppendText("[오류] 서버 실행 또는 통신 실패: "
                    + e.getMessage());
        }
    }

    // 기존 JTextArea 대신 콘솔에 로그 출력
    public void AppendText(String str) {
        System.out.println(str);
    }

    // 현재 접속 중인 사용자 검색
    private String onlineName(String name) {
        for (ClientService user : UserVec) {
            if (user.getUserName().equalsIgnoreCase(name)) {
                return user.getUserName();
            }
        }
        return null;
    }

    // 친구 목록 메시지 생성
    private String friendListMessage(ClientService user) {
        StringBuilder result = new StringBuilder("FRIENDS");

        for (ClientService other : UserVec) {
            if (other != user) {
                result.append("\n")
                      .append(other.getUserName())
                      .append("|1");
            }
        }

        return result.toString();
    }

    synchronized void sendFriends(ClientService user) {
        user.WriteOne(friendListMessage(user));
    }

    private void broadcastFriends() {
        for (ClientService user : getUsers()) {
            sendFriends(user);
        }
    }

    // 회원가입, 로그인 및 중복 접속 검사
    synchronized boolean authenticate(
            ClientService user,
            String name,
            String password,
            boolean signup) throws IOException {

        if (onlineName(name) != null) {
            user.WriteOne("AUTH_ERROR|이미 접속 중인 별명입니다.");
            return false;
        }

        if (signup) {
            if (store.canonicalName(name) != null) {
                user.WriteOne("AUTH_ERROR|이미 등록된 별명입니다.");
                return false;
            }

            store.addAccount(name, password);

        } else if (!store.matches(name, password)) {
            user.WriteOne(
                "AUTH_ERROR|별명 또는 비밀번호가 올바르지 않습니다."
            );
            return false;
        }

        user.setUserName(store.canonicalName(name));

        // 인증 결과를 먼저 전송
        if (!user.WriteOne("AUTH_OK|" + user.getUserName())) {
            return false;
        }

        UserVec.add(user);

        broadcastFriends();

        AppendText("사용자 입장: " + user.getUserName());
        AppendText("현재 참가자 수: " + UserVec.size());

        return true;
    }

    // 사용자 접속 해제
    synchronized boolean unregister(ClientService user) {
        boolean removed = UserVec.remove(user);

        if (removed) {
            AppendText("사용자 퇴장: " + user.getUserName());
            AppendText("남은 참가자 수: " + UserVec.size());

            broadcastFriends();
        }

        return removed;
    }

    // 접속 중인 사용자 목록 반환
    synchronized ClientService[] getUsers() {
        return UserVec.toArray(new ClientService[0]);
    }

    // 채팅방 번호로 방 검색
    private ChatRoom findRoom(int roomId) {
        for (ChatRoom room : RoomVec) {
            if (room.getRoomId() == roomId) {
                return room;
            }
        }

        return null;
    }

    // 사용자별 입장 가능한 채팅방 목록
    private String roomListMessage(ClientService user) {
        StringBuilder result = new StringBuilder("ROOMS");

        for (ChatRoom room : RoomVec) {
            if (!room.canJoin(user.getUserName())) {
                continue;
            }

            result.append("\n")
                  .append(room.getRoomId())
                  .append("|")
                  .append(room.getRoomName())
                  .append("|")
                  .append(room.getUserCount());
        }

        return result.toString();
    }

    synchronized void sendRooms(ClientService user) {
        user.WriteOne(roomListMessage(user));
    }

    private void broadcastRooms() {
        for (ClientService user : getUsers()) {
            user.WriteOne(roomListMessage(user));
        }
    }

    // 채팅방 생성
    synchronized void createRoom(
            ClientService user,
            String payload) {

        String[] parts = payload.split("\\|", -1);
        String roomName = parts[0].trim();

        if (roomName.isEmpty()
                || roomName.length() > 30
                || roomName.contains("|")
                || roomName.contains("\n")
                || roomName.contains("\r")) {

            user.WriteOne(
                "ERROR|방 이름은 1~30글자이며 | 또는 줄바꿈을 사용할 수 없습니다."
            );
            return;
        }

        if (RoomVec.size() >= 100) {
            user.WriteOne(
                "ERROR|채팅방은 최대 100개까지 만들 수 있습니다."
            );
            return;
        }

        Set<String> invited =
            new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        if (parts.length < 2 || parts.length > 101) {
            user.WriteOne(
                "ERROR|초대할 친구를 한 명 이상 선택하세요."
            );
            return;
        }

        for (int i = 1; i < parts.length; i++) {
            String name = parts[i].trim();
            String online = onlineName(name);

            if (online == null ||
                    online.equalsIgnoreCase(user.getUserName())) {

                user.WriteOne(
                    "ERROR|현재 접속 중인 다른 사용자만 초대할 수 있습니다."
                );
                return;
            }

            invited.add(online);
        }

        ChatRoom room = new ChatRoom(
            nextRoomId++,
            roomName,
            user.getUserName(),
            invited
        );

        RoomVec.add(room);
        store.rooms.add(room);

        try {
            store.save();

        } catch (IOException e) {
            RoomVec.remove(room);
            store.rooms.remove(room);

            user.WriteOne("ERROR|채팅방 저장 실패");
            return;
        }

        AppendText("[방 생성] " + roomName);

        user.WriteOne(
            "INFO|채팅방을 생성했습니다. 목록에서 입장하세요."
        );

        broadcastRooms();
    }

    // 채팅방 입장
    synchronized void joinRoom(
            ClientService user,
            int roomId,
            String character) {

        ChatRoom room = findRoom(roomId);

        if (room == null ||
                !room.canJoin(user.getUserName())) {

            user.WriteOne(
                "ERROR|초대받은 사용자만 입장할 수 있습니다."
            );
            return;
        }

        if (!ClientService.validCharacter(character)) {
            user.WriteOne(
                "ERROR|캐릭터를 다시 선택하세요."
            );
            return;
        }

        if (user.getCurrentRoom() != null) {
            user.WriteOne(
                "ERROR|현재 방에서 먼저 퇴장하세요."
            );
            return;
        }

        user.setCurrentRoom(room, character);
        room.addUser(user);

        user.WriteOne(
            "JOINED|" + roomId + "|"
            + room.getRoomName() + "|"
            + character
        );

        // 저장된 과거 대화 전송
        for (String saved : room.getHistory()) {
            user.WriteOne(saved);
        }

        sendRoom(
            room,
            "NOTICE|" + roomId + "|"
            + user.getUserName()
            + "님이 입장했습니다."
        );

        sendMembers(room);
        broadcastRooms();

        AppendText(
            "[방 입장] " + room.getRoomName()
            + " / " + user.getUserName()
        );
    }

    // 채팅방 퇴장
    synchronized void leaveRoom(
            ClientService user,
            boolean notifyUser) {

        ChatRoom room = user.getCurrentRoom();

        if (room == null) {
            return;
        }

        room.removeUser(user);
        user.setCurrentRoom(null, "");

        if (notifyUser) {
            user.WriteOne("LEFT|" + room.getRoomId());
        }

        sendRoom(
            room,
            "NOTICE|" + room.getRoomId() + "|"
            + user.getUserName()
            + "님이 퇴장했습니다."
        );

        sendMembers(room);
        broadcastRooms();

        AppendText(
            "[방 퇴장] " + room.getRoomName()
            + " / " + user.getUserName()
        );
    }

    // 클라이언트 연결 종료
    synchronized void disconnect(ClientService user) {
        unregister(user);
        leaveRoom(user, false);
    }

    // 특정 채팅방에만 메시지 전송
    private void sendRoom(
            ChatRoom room,
            String message) {

        for (ClientService user : room.getUsers()) {
            user.WriteOne(message);
        }
    }

    // 채팅방 참여자와 캐릭터 정보 전송
    private void sendMembers(ChatRoom room) {

        for (ClientService recipient : room.getUsers()) {

            recipient.WriteOne(
                "MEMBERS|" + room.getRoomId()
            );

            for (ClientService member : room.getUsers()) {

                recipient.WriteOne(
                    "MEMBER|" + room.getRoomId()
                    + "|" + member.getUserName()
                    + "|" + member.getCharacterId()
                );
            }
        }
    }

    // 채팅 기록 저장
    private boolean saveMessage(
            ChatRoom room,
            String message,
            ClientService sender) {

        Vector<String> history = room.getHistory();

        String removed = history.size() >= ChatRoom.HISTORY_LIMIT
                ? history.remove(0)
                : null;

        history.add(message);

        try {
            store.save();
            return true;

        } catch (IOException e) {

            history.remove(history.size() - 1);

            if (removed != null) {
                history.add(0, removed);
            }

            sender.WriteOne("ERROR|대화 저장 실패");
            return false;
        }
    }

    // 일반 채팅 메시지 전송
    synchronized void sendChat(
            ClientService sender,
            String message) {

        ChatRoom room = sender.getCurrentRoom();

        if (room == null) {
            sender.WriteOne(
                "ERROR|채팅방에 먼저 입장하세요."
            );
            return;
        }

        if (message.length() > 1000) {
            sender.WriteOne(
                "ERROR|메시지는 최대 1000글자입니다."
            );
            return;
        }

        String packet =
            "CHAT|" + room.getRoomId()
            + "|" + sender.getUserName()
            + "|" + message;

        if (!saveMessage(room, packet, sender)) {
            return;
        }

        sendRoom(room, packet);

        AppendText(
            "[" + room.getRoomName() + "] "
            + sender.getUserName()
            + " : " + message
        );
    }

    // 이모티콘 전송
    synchronized void sendEmoji(
            ClientService sender,
            String emojiId) {

        ChatRoom room = sender.getCurrentRoom();

        if (room == null) {
            sender.WriteOne(
                "ERROR|채팅방에 먼저 입장하세요."
            );
            return;
        }

        if (!ClientService.validEmoji(emojiId)) {
            sender.WriteOne(
                "ERROR|지원하지 않는 이모티콘입니다."
            );
            return;
        }

        String packet =
            "EMOJI|" + room.getRoomId()
            + "|" + sender.getUserName()
            + "|" + emojiId;

        if (!saveMessage(room, packet, sender)) {
            return;
        }

        sendRoom(room, packet);

        AppendText(
            "[" + room.getRoomName() + "] "
            + sender.getUserName()
            + " : " + emojiId
        );
    }
}
