package chiikawatalk.client;

import chiikawatalk.common.AppConfig;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import java.util.Arrays;
import javax.swing.*;

// 교수님 Main/View 분리 유지: 인증이 끝난 소켓을 View에 전달한다.
public class ChatClientMain extends JFrame {
    private final JTextField txtUserName = new JTextField(16);
    private final JPasswordField txtPassword = new JPasswordField(16);
    private final JTextField txtIpAddress = new JTextField(AppConfig.HOST, 16);
    private final JTextField txtPortNumber = new JTextField(String.valueOf(AppConfig.PORT), 16);
    private final JButton btnLogin = new JButton("로그인");
    private final JButton btnSignup = new JButton("회원가입");
    private final JLabel status = new JLabel("별명과 비밀번호를 입력하세요.", SwingConstants.CENTER);
    private boolean connecting;

    public static void main(String[] args) {
        EventQueue.invokeLater(() -> new ChatClientMain().setVisible(true));
    }
    public ChatClientMain() {
        setTitle("치이카와 톡 - 로그인");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(new Color(255, 244, 214));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 18, 15, 18));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(7, 5, 7, 5); g.fill = GridBagConstraints.HORIZONTAL;
        JLabel title = new JLabel("치이카와 톡", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        title.setIcon(CharacterSelectDialog.loadIcon("characters/chiikawa.png", 64));
        g.gridx=0; g.gridy=0; g.gridwidth=2; panel.add(title,g);
        row(panel,g,1,"별명",txtUserName); row(panel,g,2,"비밀번호",txtPassword);
        row(panel,g,3,"서버 IP",txtIpAddress); row(panel,g,4,"포트",txtPortNumber);
        g.gridx=0; g.gridy=5; g.gridwidth=2;
        JPanel buttons = new JPanel(new GridLayout(1,2,8,0)); buttons.setOpaque(false);
        buttons.add(btnLogin); buttons.add(btnSignup); panel.add(buttons,g);
        g.gridy=6; panel.add(status,g);
        setContentPane(panel); pack(); setSize(390,365); setLocationRelativeTo(null);
        setResizable(false);
        Myaction action = new Myaction();
        btnLogin.addActionListener(action); btnSignup.addActionListener(action);
        txtPassword.addActionListener(action); txtUserName.addActionListener(action);
    }
    private void row(JPanel p, GridBagConstraints g, int y, String label, JComponent field) {
        g.gridy=y; g.gridx=0; g.gridwidth=1; p.add(new JLabel(label),g);
        g.gridx=1; p.add(field,g);
    }
    class Myaction implements ActionListener {
        public void actionPerformed(ActionEvent e) { connect(e.getSource()==btnSignup); }
    }
    private void setBusy(boolean busy) {
        connecting=busy; btnLogin.setEnabled(!busy); btnSignup.setEnabled(!busy);
        txtUserName.setEnabled(!busy); txtPassword.setEnabled(!busy);
        txtIpAddress.setEnabled(!busy); txtPortNumber.setEnabled(!busy);
    }
    private void connect(boolean signup) {
        if(connecting) return;
        String name=txtUserName.getText().trim(), host=txtIpAddress.getText().trim();
        char[] chars=txtPassword.getPassword(); String password=new String(chars); Arrays.fill(chars,'\0');
        if(name.isEmpty() || name.length()>20 || name.contains("|") || name.contains("\n") || name.contains("\r") || host.isEmpty()
                || password.length()<6 || password.length()>64 || password.contains("|") || password.contains("\n") || password.contains("\r")) {
            status.setText("별명 1~20자, 비밀번호 6~64자 (| 제외)"); return;
        }
        final int port;
        try { port=Integer.parseInt(txtPortNumber.getText().trim()); if(port<1 || port>65535) throw new NumberFormatException(); }
        catch(NumberFormatException e) { status.setText("포트 번호를 확인하세요."); return; }
        setBusy(true); status.setText(signup?"회원가입 중...":"로그인 중...");
        // 명시적인 접속/수신 제한 시간은 두지 않는다. GUI는 별도 작업으로 유지한다.
        new SwingWorker<Connection,Void>() {
            protected Connection doInBackground() throws Exception {
                Socket socket=new Socket();
                try {
                    socket.connect(new java.net.InetSocketAddress(host,port));
                    DataInputStream in=new DataInputStream(socket.getInputStream());
                    DataOutputStream out=new DataOutputStream(socket.getOutputStream());
                    out.writeUTF((signup?"/signup ":"/login ")+name+"|"+password); out.flush();
                    String reply=in.readUTF();
                    if(!reply.startsWith("AUTH_OK|")) throw new IOException(reply.startsWith("AUTH_ERROR|")?reply.substring(11):"서버 인증 응답 오류");
                    return new Connection(socket,in,out,reply.substring(8));
                } catch(Exception e) { try {socket.close();} catch(IOException ignored){} throw e; }
            }
            protected void done() {
                setBusy(false); Connection conn=null;
                try {
                    conn=get();
                    ChatClientView view=new ChatClientView(conn.name,conn.socket,conn.in,conn.out,ChatClientMain.this);
                    txtPassword.setText(""); status.setText("다시 로그인할 수 있습니다.");
                    setVisible(false); view.setVisible(true);
                } catch(Exception e) {
                    if(conn!=null) try {conn.socket.close();} catch(IOException ignored){}
                    Throwable cause=e.getCause()==null?e:e.getCause();
                    status.setText("로그인 실패");
                    JOptionPane.showMessageDialog(ChatClientMain.this,cause.getMessage(),"접속 실패",JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
    private record Connection(Socket socket, DataInputStream in, DataOutputStream out, String name) {}
}
