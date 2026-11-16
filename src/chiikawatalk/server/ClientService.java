package chiikawatalk.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

// 교수님 UserService 구조 유지: 사용자마다 하나의 Thread
public class ClientService extends Thread {
    private InputStream is;
    private OutputStream os;
    private DataInputStream dis;
    private DataOutputStream dos;
    private final Socket client_socket;
    private final ChatServer server;
    private String UserName = "";
    private ChatRoom currentRoom;
    private String characterId = "";

    public ClientService(ChatServer server, Socket client_socket) {
        this.server = server;
        this.client_socket = client_socket;
    }

    public String getUserName() { return UserName; }
    public ChatRoom getCurrentRoom() { return currentRoom; }
    public String getCharacterId() { return characterId; }
    void setCurrentRoom(ChatRoom room, String character) {
        currentRoom = room;
        characterId = character;
    }
    static boolean validCharacter(String id) {
        return "CHIIKAWA".equals(id) || "HACHIWARE".equals(id) || "USAGI".equals(id);
    }
    static boolean validEmoji(String id) {
        return "chiikawa_happy".equals(id) || "hachiware_love".equals(id) || "usagi_surprise".equals(id);
    }

    public synchronized boolean WriteOne(String msg) {
        try {
            dos.writeUTF(msg);
            dos.flush();
            return true;
        } catch (IOException e) {
            closeSocket();
            return false;
        }
    }

    // 기존 전체 전송 메서드: 서버 전체 안내 등에 사용할 수 있다.
    public void WriteAll(String str) {
        for (ClientService user : server.getUsers()) user.WriteOne(str);
        server.AppendText(str);
    }

    // 12주차: 일반 채팅은 소속된 방에만 전달
    public void WriteRoom(String str) { server.sendChat(this, str); }

    @Override
    public void run() {
        try {
            is = client_socket.getInputStream();
            dis = new DataInputStream(is);
            os = client_socket.getOutputStream();
            dos = new DataOutputStream(os);

            String line1 = dis.readUTF();
            if (!line1.startsWith("/login ")) {
                WriteOne("ERROR|로그인 형식을 확인하세요."); return;
            }
            UserName = line1.substring(7).trim();
            if (UserName.isEmpty() || UserName.length() > 20 || UserName.contains("|")
                    || UserName.contains("\n") || UserName.contains("\r")) {
                WriteOne("ERROR|닉네임은 1~20글자이며 | 또는 줄바꿈을 사용할 수 없습니다."); return;
            }
            if (!server.register(this)) {
                WriteOne("ERROR|이미 사용 중인 닉네임입니다."); return;
            }
            // 별도 로그인 성공 응답 없이 채팅방 목록 제공
            server.sendRooms(this);
            while (true) {
                String msg = dis.readUTF().trim();
                if (msg.isEmpty()) continue;
                if ("/exit".equalsIgnoreCase(msg)) break;
                if (msg.equals("/rooms")) {
                    server.sendRooms(this);
                } else if (msg.startsWith("/create ")) {
                    server.createRoom(this, msg.substring(8).trim());
                } else if (msg.startsWith("/join ")) {
                    String[] parts = msg.split("\\s+", 3);
                    try {
                        if (parts.length != 3) throw new NumberFormatException();
                        server.joinRoom(this, Integer.parseInt(parts[1]), parts[2]);
                    } catch (NumberFormatException e) {
                        WriteOne("ERROR|입장 형식: /join 방번호 캐릭터ID");
                    }
                } else if (msg.equals("/leave")) {
                    server.leaveRoom(this, true);
                } else if (msg.startsWith("/emoji ")) {
                    server.sendEmoji(this, msg.substring(7).trim());
                } else if (msg.startsWith("/chat ")) {
                    WriteRoom(msg.substring(6));
                } else if (msg.startsWith("/")) {
                    WriteOne("ERROR|지원하지 않는 명령어입니다.");
                } else {
                    WriteRoom(msg);
                }
            }
        } catch (IOException e) {
            // 소켓 종료 시 아래 finally에서 방과 접속 목록을 정리
        } finally {
            closeSocket();
            server.disconnect(this);
        }
    }

    private void closeSocket() {
        try { client_socket.close(); } catch (IOException ignored) { }
    }
}
