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
import java.util.Vector;

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
                    socket = new ServerSocket(port);
                } catch (NumberFormatException | IOException e1) {
                    AppendText("[오류] 서버 시작 실패: 포트 번호를 확인하세요.");
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

    // 로그인 완료 사용자만 Vector에 추가 (중복 닉네임 검사)
    synchronized boolean register(ClientService user) {
        for (ClientService other : UserVec) {
            if (other.getUserName().equalsIgnoreCase(user.getUserName())) return false;
        }
        UserVec.add(user);
        AppendText("사용자 입장. 현재 참가자 수 " + UserVec.size());
        return true;
    }

    synchronized boolean unregister(ClientService user) {
        boolean removed = UserVec.remove(user);
        if (removed) AppendText("사용자 퇴장. 남은 참가자 수 " + UserVec.size());
        return removed;
    }

    // 전체 전송 중 접속 목록이 변경되어도 안전하게 순회
    ClientService[] getUsers() {
        return UserVec.toArray(new ClientService[0]);
    }
}
