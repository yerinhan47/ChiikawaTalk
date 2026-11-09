
package chiikawatalk.server;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import chiikawatalk.common.AppConfig;

public class ChatServer {

    public static void main(String[] args) {

        try (ServerSocket server =
                new ServerSocket(AppConfig.PORT)) {

            System.out.println("채팅 서버 실행!");
            System.out.println("포트: " + AppConfig.PORT);

            while (true) {
                Socket client = server.accept();

                System.out.println("클라이언트 접속!");

                new Thread(() -> handle(client)).start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handle(Socket client) {

        try (Socket socket = client;

             BufferedReader reader = new BufferedReader(
                 new InputStreamReader(
                     socket.getInputStream(),
                     StandardCharsets.UTF_8));

             PrintWriter writer = new PrintWriter(
                 new OutputStreamWriter(
                     socket.getOutputStream(),
                     StandardCharsets.UTF_8), true)) {

            String message = reader.readLine();
            System.out.println("수신: " + message);

            if ("PING".equals(message)) {
                writer.println("PONG");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
