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

            while (true) {
                Socket socket = listener.accept();
                // 클라이언트마다 담당 스레드 생성
                new Thread(new ClientHandler(socket)).start();
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
}
