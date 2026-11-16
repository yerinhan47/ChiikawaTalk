package chiikawatalk.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

// 교수님 JavaChatServer의 UserService를 별도 파일로 분리
public class ClientService extends Thread {
    private InputStream is;
    private OutputStream os;
    private DataInputStream dis;
    private DataOutputStream dos;
    private final Socket client_socket;
    private final ChatServer server;
    private String UserName = "";

    public ClientService(ChatServer server, Socket client_socket) {
        this.server = server;
        this.client_socket = client_socket;
    }

    public String getUserName() {
        return UserName;
    }

    // 클라이언트로 메시지 전송 (원본 WriteOne 유지)
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

    // 모든 다중 클라이언트에게 채팅 메시지 전달 (원본 WriteAll 유지)
    public void WriteAll(String str) {
        for (ClientService user : server.getUsers()) {
            user.WriteOne(str);
        }
        server.AppendText(str);
    }

    @Override
    public void run() {
        try {
            // 원본 생성자에서 하던 로그인 수신을 run()으로 이동
            // 다른 사용자의 접속을 기다리는 AcceptServer가 막히지 않게 함
            is = client_socket.getInputStream();
            dis = new DataInputStream(is);
            os = client_socket.getOutputStream();
            dos = new DataOutputStream(os);

            String line1 = dis.readUTF();
            if (!line1.startsWith("/login ")) {
                WriteOne("[오류] 로그인 형식을 확인하세요.");
                return;
            }
            UserName = line1.substring(7).trim();
            if (UserName.isEmpty() || UserName.length() > 20 ||
                    UserName.contains("\n") || UserName.contains("\r")) {
                WriteOne("[오류] 닉네임은 1~20글자여야 합니다.");
                return;
            }
            if (!server.register(this)) {
                WriteOne("[오류] 이미 사용 중인 닉네임입니다.");
                return;
            }
            WriteAll("[입장] " + UserName + "님이 입장했습니다.");

            // 원본과 같이 readUTF()로 기다리고 WriteAll()로 전체 전송
            while (true) {
                String msg = dis.readUTF();
                msg = msg.trim();
                if (msg.isEmpty()) continue;
                if ("/exit".equalsIgnoreCase(msg)) break;
                // 치이카와 톡: 서버에서 닉네임을 붙임
                WriteAll(UserName + " : " + msg);
            }
        } catch (IOException e) {
            // 소켓 종료 및 통신 오류는 finally에서 처리
        } finally {
            closeSocket();
            if (server.unregister(this)) {
                WriteAll("[퇴장] " + UserName + "님이 퇴장했습니다.");
            }
        }
    }

    private void closeSocket() {
        try { client_socket.close(); } catch (IOException ignored) { }
    }
}
