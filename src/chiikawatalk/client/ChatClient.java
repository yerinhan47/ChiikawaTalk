
package chiikawatalk.client;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import chiikawatalk.common.AppConfig;

public class ChatClient {

    public static void main(String[] args) {

        try (Socket socket = new Socket(
                AppConfig.HOST, AppConfig.PORT);

             BufferedReader reader = new BufferedReader(
                 new InputStreamReader(
                     socket.getInputStream(),
                     StandardCharsets.UTF_8));

             PrintWriter writer = new PrintWriter(
                 new OutputStreamWriter(
                     socket.getOutputStream(),
                     StandardCharsets.UTF_8), true)) {

            System.out.println("서버 연결 성공!");

            writer.println("PING");

            String response = reader.readLine();
            System.out.println("서버 응답: " + response);

        } catch (IOException e) {
            System.out.println("서버 연결 실패!");
            e.printStackTrace();
        }
    }
}
