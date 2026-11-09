package chiikawatalk.client;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import javax.swing.*;
import chiikawatalk.common.AppConfig;

// 11강 ChatClient의 GUI·Receiver 구조를 유지
public class ChatClient extends JFrame implements ActionListener {

    private BufferedReader in;
    private BufferedWriter out;
    private Socket socket;
    private Receiver receiver;
    private JTextField sender;
    private final String nickname;

    public ChatClient(String nickname) {
        this.nickname = nickname;
        setTitle("치이카와 톡 - " + nickname);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        Container c = getContentPane();
        c.setLayout(new BorderLayout());

        receiver = new Receiver();
        receiver.setEditable(false);
        sender = new JTextField();
        sender.addActionListener(this);
        sender.setEnabled(false);

        c.add(new JScrollPane(receiver), BorderLayout.CENTER);
        c.add(sender, BorderLayout.SOUTH);
        setSize(400, 300);
        setLocationRelativeTo(null);
        setVisible(true);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeSocket();
            }
        });

        // 네트워크 연결은 GUI 스레드와 분리
        new Thread(() -> {
            try {
                setupConnection();
                SwingUtilities.invokeLater(() -> {
                    appendMessage("[안내] 서버 연결 성공");
                    sender.setEnabled(true);
                    sender.requestFocusInWindow();
                });
                new Thread(receiver).start();
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> appendMessage(
                        "[오류] 서버 연결 실패: " + e.getMessage()));
            }
        }).start();
    }

    private void setupConnection() throws IOException {
        socket = new Socket(AppConfig.HOST, AppConfig.PORT);
        in = new BufferedReader(new InputStreamReader(
                socket.getInputStream(), StandardCharsets.UTF_8));
        out = new BufferedWriter(new OutputStreamWriter(
                socket.getOutputStream(), StandardCharsets.UTF_8));
        // 서버가 닉네임을 첫 번째 줄로 받음
        out.write(nickname);
        out.newLine();
        out.flush();
    }

    private class Receiver extends JTextArea implements Runnable {
        @Override
        public void run() {
            try {
                String msg;
                while ((msg = in.readLine()) != null) {
                    final String received = msg;
                    SwingUtilities.invokeLater(() -> appendMessage(received));
                }
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> appendMessage("[안내] 연결 종료"));
            } finally {
                SwingUtilities.invokeLater(() -> sender.setEnabled(false));
                closeSocket();
            }
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == sender && out != null) {
            String msg = sender.getText().trim();
            if (msg.isEmpty()) return;
            try {
                out.write(msg);
                out.newLine();
                out.flush();
                // 서버가 모두에게 전송하므로 여기서는 직접 출력하지 않음
                sender.setText("");
            } catch (IOException ex) {
                appendMessage("[오류] 전송 실패: " + ex.getMessage());
            }
        }
    }

    private void appendMessage(String msg) {
        receiver.append(msg + "\n");
        receiver.setCaretPosition(receiver.getDocument().getLength());
    }

    private void closeSocket() {
        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            // 종료 중 발생한 오류는 무시
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            String name = JOptionPane.showInputDialog(null, "닉네임을 입력하세요:",
                    "치이카와 톡", JOptionPane.QUESTION_MESSAGE);
            if (name != null && !name.trim().isEmpty()) {
                new ChatClient(name.trim());
            }
        });
    }
}
