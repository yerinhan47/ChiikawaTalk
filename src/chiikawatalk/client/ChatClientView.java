package chiikawatalk.client;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.SwingUtilities;

//ChatClientView.java
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

public class ChatClientView extends JFrame {
    private JPanel contentPane;
    private JTextField txtInput;
    private String UserName;
    private JButton btnSend;
    private JTextArea textArea;
    private Socket socket; // 연결소켓
    private InputStream is;
    private OutputStream os;
    private DataInputStream dis;
    private DataOutputStream dos;
    private JLabel lblUserName;
    private volatile boolean closed;

	/**
	 * Create the frame.
	 */
	public ChatClientView(String username, String ip_addr, String port_no, ChatClientMain main) throws IOException {
		setTitle("치이카와 톡 - " + username);
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		setBounds(100, 100, 392, 462);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(contentPane);
		contentPane.setLayout(null);

		JScrollPane scrollPane = new JScrollPane();
		scrollPane.setBounds(12, 10, 352, 340);
		contentPane.add(scrollPane);

		textArea = new JTextArea();
		textArea.setEditable(false);
		textArea.setLineWrap(true);
		textArea.setWrapStyleWord(true);
		scrollPane.setViewportView(textArea);

		txtInput = new JTextField();
		txtInput.setBounds(91, 365, 185, 40);
		contentPane.add(txtInput);
		txtInput.setColumns(10);

		btnSend = new JButton("Send");
		btnSend.setBounds(288, 364, 76, 40);
		contentPane.add(btnSend);
		
		lblUserName = new JLabel("Name");
		lblUserName.setHorizontalAlignment(SwingConstants.CENTER);
		lblUserName.setBounds(12, 364, 67, 40);
		contentPane.add(lblUserName);

	
		AppendText("User " + username + " connecting " + ip_addr + " " + port_no + "\n");
		UserName = username;
		lblUserName.setText(username + ">");

        try {
            socket = new Socket(ip_addr, Integer.parseInt(port_no));
            is = socket.getInputStream();
            dis = new DataInputStream(is);
            os = socket.getOutputStream();
            dos = new DataOutputStream(os);

            // 교수님 원본과 동일한 로그인 형식
            dos.writeUTF("/login " + UserName);
            dos.flush();

            ListenNetwork net = new ListenNetwork();
            net.start();
            Myaction action = new Myaction();
            btnSend.addActionListener(action);
            txtInput.addActionListener(action);
            txtInput.requestFocusInWindow();
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    closeSocket();
                    main.setVisible(true);
                }
            });
        } catch (IOException | RuntimeException e) {
            closeSocket();
            dispose();
            throw e;
        }
    }

    // Server Message를 수신해서 화면에 표시 (원본 ListenNetwork 유지)
    class ListenNetwork extends Thread {
        public void run() {
            try {
                while (true) {
                    String msg = dis.readUTF();
                    AppendText(msg + "\n");
                }
            } catch (IOException e) {
                if (!closed) AppendText("[안내] 서버 연결 종료\n");
            } finally {
                closeSocket();
                SwingUtilities.invokeLater(() -> {
                    btnSend.setEnabled(false);
                    txtInput.setEnabled(false);
                });
            }
        }
    }

	// 메시지를 입력 후 Send 버튼 또는  keyboard enter key를 치면 서버로(다른 사용자에게) 전송
	class Myaction implements ActionListener // 내부클래스로 액션 이벤트 처리 클래스
	{
		@Override
		public void actionPerformed(ActionEvent e) {
			// Send button을 누르거나 메시지 입력하고 Enter key 치면
			if (e.getSource() == btnSend || e.getSource() == txtInput) {
                String msg = txtInput.getText().trim();
                if (msg.isEmpty()) return;
                // 닉네임은 서버에서 붙이므로 채팅 내용만 전송
                SendMessage(msg);
                txtInput.setText("");
                txtInput.requestFocusInWindow();
                if ("/exit".equalsIgnoreCase(msg)) {
                    closeSocket();
                    dispose();
                }
			}
		}
	}

    // 화면에 출력: 네트워크 스레드의 GUI 갱신은 EDT에 맡김
    public void AppendText(String msg) {
        SwingUtilities.invokeLater(() -> {
            textArea.append(msg);
            textArea.setCaretPosition(textArea.getDocument().getLength());
        });
    }

    // Server에게 network로 전송
    public void SendMessage(String msg) {
        try {
            dos.writeUTF(msg);
            dos.flush();
        } catch (IOException e) {
            AppendText("[오류] 메시지 전송 실패\n");
            closeSocket();
            btnSend.setEnabled(false);
            txtInput.setEnabled(false);
        }
    }

    private void closeSocket() {
        closed = true;
        if (socket != null) {
            try { socket.close(); } catch (IOException ignored) { }
        }
    }
}
