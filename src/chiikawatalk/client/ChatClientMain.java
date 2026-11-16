package chiikawatalk.client;

import chiikawatalk.common.AppConfig;
import javax.swing.JOptionPane;
import java.io.IOException;

//ChatClientMain.java
//Java Client 시작

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

public class ChatClientMain extends JFrame {

	private JPanel contentPane;
	private JTextField txtUserName;
	private JTextField txtIpAddress;
	private JTextField txtPortNumber;

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					ChatClientMain frame = new ChatClientMain();
					frame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Create the frame.
	 */
	public ChatClientMain() {
		setTitle("치이카와 톡 - 접속");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100, 100, 254, 321);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(contentPane);
		contentPane.setLayout(null);
		
		JLabel lblNewLabel = new JLabel("User Name");
		lblNewLabel.setBounds(12, 39, 82, 33);
		contentPane.add(lblNewLabel);
		
		txtUserName = new JTextField();
		txtUserName.setHorizontalAlignment(SwingConstants.CENTER);
		txtUserName.setBounds(101, 39, 116, 33);
		contentPane.add(txtUserName);
		txtUserName.setColumns(10);
		
		JLabel lblIpAddress = new JLabel("IP Address");
		lblIpAddress.setBounds(12, 100, 82, 33);
		contentPane.add(lblIpAddress);
		
		txtIpAddress = new JTextField();
		txtIpAddress.setHorizontalAlignment(SwingConstants.CENTER);
		txtIpAddress.setText(AppConfig.HOST);
		txtIpAddress.setColumns(10);
		txtIpAddress.setBounds(101, 100, 116, 33);
		contentPane.add(txtIpAddress);
		
		JLabel lblPortNumber = new JLabel("Port Number");
		lblPortNumber.setBounds(12, 163, 82, 33);
		contentPane.add(lblPortNumber);
		
		txtPortNumber = new JTextField();
		txtPortNumber.setText(String.valueOf(AppConfig.PORT));
		txtPortNumber.setHorizontalAlignment(SwingConstants.CENTER);
		txtPortNumber.setColumns(10);
		txtPortNumber.setBounds(101, 163, 116, 33);
		contentPane.add(txtPortNumber);
		
		JButton btnConnect = new JButton("Connect");
		btnConnect.setBounds(12, 223, 205, 38);
		contentPane.add(btnConnect);
		Myaction action = new Myaction();
		btnConnect.addActionListener(action);
		txtUserName.addActionListener(action);
		txtIpAddress.addActionListener(action);
		txtPortNumber.addActionListener(action);
	}
	class Myaction implements ActionListener // 내부클래스로 액션 이벤트 처리 클래스
	{
		@Override
		public void actionPerformed(ActionEvent e) {
			String username = txtUserName.getText().trim();
			String ip_addr = txtIpAddress.getText().trim();
			String port_no = txtPortNumber.getText().trim();
			
			// 닉네임과 접속 정보 검사 (추가!)
            if (username.isEmpty() || username.length() > 20 || ip_addr.isEmpty()) {
                JOptionPane.showMessageDialog(ChatClientMain.this, "닉네임(1~20글자)과 IP를 입력하세요.");
                return;
            }
            try {
                int port = Integer.parseInt(port_no);
                if (port < 1 || port > 65535) throw new NumberFormatException();
                ChatClientView view = new ChatClientView(username, ip_addr, port_no, ChatClientMain.this);
                setVisible(false);
                view.setVisible(true);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(ChatClientMain.this, "포트 번호를 확인하세요.");
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(ChatClientMain.this, "접속 실패: " + ex.getMessage());
            }
		}
	}
}
