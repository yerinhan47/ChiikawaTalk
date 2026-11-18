
package chiikawatalk.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/*
 * ClientService 클래스
 *
 * 서버에 접속한 클라이언트 한 명을 담당하는 스레드이다.
 *
 * 주요 기능
 * 1. 클라이언트와 소켓 통신
 * 2. 회원가입 및 로그인 요청 처리
 * 3. 친구 목록과 채팅방 목록 요청 처리
 * 4. 채팅방 생성, 입장, 퇴장 명령 처리
 * 5. 채팅 메시지와 이모티콘 전송 요청 처리
 * 6. 연결 종료 시 사용자 정보 정리
 *
 * 교수님이 제공한 UserService 구조를
 * 별도의 ClientService 클래스로 분리한 형태이다.
 */
public class ClientService extends Thread {

    // 클라이언트로부터 데이터를 수신하기 위한 입력 스트림
    private InputStream is;

    // 클라이언트에게 데이터를 전송하기 위한 출력 스트림
    private OutputStream os;

    // UTF 형식의 문자열을 읽기 위한 입력 스트림
    private DataInputStream dis;

    // UTF 형식의 문자열을 보내기 위한 출력 스트림
    private DataOutputStream dos;

    // 현재 클라이언트와 연결된 소켓
    private final Socket client_socket;

    // 클라이언트를 관리하는 서버 객체
    private final ChatServer server;

    // 로그인한 사용자의 이름
    private String UserName = "";

    // 현재 사용자가 입장한 채팅방
    private ChatRoom currentRoom;

    // 사용자가 선택한 캐릭터 ID
    private String characterId = "";

    /*
     * 생성자
     *
     * server        : 현재 실행 중인 채팅 서버
     * client_socket : 접속한 클라이언트의 소켓
     */
    public ClientService(
            ChatServer server,
            Socket client_socket) {

        this.server = server;
        this.client_socket = client_socket;
    }

    // 현재 사용자 이름 반환
    public String getUserName() {
        return UserName;
    }

    // 로그인 성공 시 사용자 이름 설정
    void setUserName(String name) {
        UserName = name;
    }

    // 현재 입장한 채팅방 반환
    public ChatRoom getCurrentRoom() {
        return currentRoom;
    }

    // 사용자가 선택한 캐릭터 ID 반환
    public String getCharacterId() {
        return characterId;
    }

    /*
     * 현재 채팅방과 캐릭터 정보 설정
     *
     * 채팅방에 입장할 때는 방과 캐릭터를 지정하고,
     * 퇴장할 때는 null과 빈 문자열로 초기화한다.
     */
    void setCurrentRoom(ChatRoom room, String character) {
        currentRoom = room;
        characterId = character;
    }

    /*
     * 선택한 캐릭터가 지원되는 캐릭터인지 확인
     *
     * CHIIKAWA : 치이카와
     * HACHIWARE : 하치와레
     * USAGI : 우사기
     */
    static boolean validCharacter(String id) {
        return "CHIIKAWA".equals(id)
                || "HACHIWARE".equals(id)
                || "USAGI".equals(id);
    }

    /*
     * 전송하려는 이모티콘이 지원되는지 확인
     *
     * 클라이언트가 임의의 이모티콘 ID를 보내는 것을
     * 방지하기 위해 허용된 ID만 검사한다.
     */
    static boolean validEmoji(String id) {
        return "chiikawa_happy".equals(id)
                || "hachiware_love".equals(id)
                || "usagi_surprise".equals(id);
    }

    /*
     * 특정 클라이언트에게 메시지 전송
     *
     * writeUTF()를 사용하여 문자열을 전송한다.
     * synchronized로 동일 사용자에 대한
     * 여러 스레드의 동시 전송을 방지한다.
     *
     * 전송 성공 : true
     * 전송 실패 : false
     */
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

    /*
     * 서버에 접속한 모든 사용자에게 메시지 전송
     *
     * ChatServer가 관리하는 사용자 목록을 가져와
     * 각 사용자에게 동일한 메시지를 전송한다.
     *
     * 일반 채팅은 방 단위로 전송하므로
     * 이 메서드는 전체 공지 등에 사용할 수 있다.
     */
    public void WriteAll(String str) {

        for (ClientService user : server.getUsers()) {
            user.WriteOne(str);
        }

        // 서버 콘솔에도 전송 내용 출력
        server.AppendText(str);
    }

    /*
     * 현재 채팅방에 메시지 전송 요청
     *
     * 실제 메시지 저장과 참여자에게 전송하는 작업은
     * ChatServer의 sendChat()에서 처리한다.
     */
    public void WriteRoom(String str) {
        server.sendChat(this, str);
    }

    /*
     * 클라이언트 스레드 실행
     *
     * ClientService.start()가 호출되면 실행된다.
     *
     * 처리 순서
     * 1. 입출력 스트림 생성
     * 2. 로그인 또는 회원가입 요청 수신
     * 3. 서버에서 계정 인증
     * 4. 채팅방 목록 전송
     * 5. 클라이언트 명령어 반복 처리
     * 6. 연결 종료 시 소켓 및 사용자 정보 정리
     */
    @Override
    public void run() {

        try {

            // 소켓으로부터 입력 스트림 생성
            is = client_socket.getInputStream();
            dis = new DataInputStream(is);

            // 소켓으로부터 출력 스트림 생성
            os = client_socket.getOutputStream();
            dos = new DataOutputStream(os);

            // 클라이언트가 처음 전송한 로그인 요청 수신
            String line1 = dis.readUTF();

            // 회원가입 요청인지 확인
            boolean signup = line1.startsWith("/signup ");

            // 로그인과 회원가입 이외의 명령은 거부
            if (!signup && !line1.startsWith("/login ")) {
                WriteOne("AUTH_ERROR|로그인 형식을 확인하세요.");
                return;
            }

            /*
             * 사용자 이름과 비밀번호 분리
             *
             * 회원가입: /signup 이름|비밀번호
             * 로그인:   /login 이름|비밀번호
             *
             * | 문자를 기준으로 두 값을 분리한다.
             */
            String[] credentials =
                line1.substring(signup ? 8 : 7).split("\\|", -1);

            if (credentials.length != 2) {
                WriteOne("AUTH_ERROR|로그인 형식을 확인하세요.");
                return;
            }

            String name = credentials[0].trim();
            String password = credentials[1];

            /*
             * 입력값 유효성 검사
             *
             * 사용자 이름: 1~20글자
             * 비밀번호: 6~64글자
             * 줄바꿈 문자 사용 금지
             */
            if (name.isEmpty()
                    || name.length() > 20
                    || name.contains("\n")
                    || name.contains("\r")
                    || password.length() < 6
                    || password.length() > 64
                    || password.contains("\n")
                    || password.contains("\r")) {

                WriteOne(
                    "AUTH_ERROR|별명 1~20글자, 비밀번호 6~64글자를 입력하세요."
                );
                return;
            }

            /*
             * 서버에 인증 요청
             *
             * 회원가입이면 새 계정을 저장하고,
             * 로그인이면 기존 계정과 비밀번호를 검사한다.
             */
            try {
                if (!server.authenticate(
                        this, name, password, signup)) {
                    return;
                }

            } catch (IOException e) {
                WriteOne("AUTH_ERROR|계정 저장 또는 인증 처리 실패");
                return;
            }

            // 로그인 성공 후 입장 가능한 채팅방 목록 전송
            server.sendRooms(this);

            /*
             * 클라이언트 명령어 처리 반복문
             *
             * 클라이언트가 종료하거나
             * 소켓 연결이 끊어질 때까지 실행한다.
             */
            while (true) {

                // 클라이언트 메시지 수신
                String msg = dis.readUTF().trim();

                // 빈 메시지는 처리하지 않음
                if (msg.isEmpty()) {
                    continue;
                }

                // 클라이언트 종료 요청
                if ("/exit".equalsIgnoreCase(msg)) {
                    break;
                }

                // 친구 목록 요청
                if (msg.equals("/friends")) {

                    server.sendFriends(this);

                // 채팅방 목록 요청
                } else if (msg.equals("/rooms")) {

                    server.sendRooms(this);

                /*
                 * 채팅방 생성 요청
                 *
                 * 형식: /create 방이름|초대사용자1|초대사용자2
                 */
                } else if (msg.startsWith("/create ")) {

                    server.createRoom(
                        this,
                        msg.substring(8).trim()
                    );

                /*
                 * 채팅방 입장 요청
                 *
                 * 형식: /join 방번호 캐릭터ID
                 *
                 * 예: /join 1 CHIIKAWA
                 */
                } else if (msg.startsWith("/join ")) {

                    String[] parts = msg.split("\\s+", 3);

                    try {

                        if (parts.length != 3) {
                            throw new NumberFormatException();
                        }

                        server.joinRoom(
                            this,
                            Integer.parseInt(parts[1]),
                            parts[2]
                        );

                    } catch (NumberFormatException e) {

                        WriteOne(
                            "ERROR|입장 형식: /join 방번호 캐릭터ID"
                        );
                    }

                // 현재 채팅방 퇴장 요청
                } else if (msg.equals("/leave")) {

                    server.leaveRoom(this, true);

                /*
                 * 이모티콘 전송 요청
                 *
                 * 형식: /emoji 이모티콘ID
                 */
                } else if (msg.startsWith("/emoji ")) {

                    server.sendEmoji(
                        this,
                        msg.substring(7).trim()
                    );

                /*
                 * 일반 채팅 메시지 전송 요청
                 *
                 * 형식: /chat 메시지
                 */
                } else if (msg.startsWith("/chat ")) {

                    WriteRoom(msg.substring(6));

                // 정의되지 않은 명령어 처리
                } else if (msg.startsWith("/")) {

                    WriteOne("ERROR|지원하지 않는 명령어입니다.");

                /*
                 * 별도의 명령어가 없는 일반 문자열도
                 * 현재 채팅방에 전송한다.
                 */
                } else {

                    WriteRoom(msg);
                }
            }

        } catch (IOException e) {

            // 통신 오류나 소켓 종료 시 finally에서 정리

        } finally {

            // 소켓 연결 종료
            closeSocket();

            // 서버의 접속 목록과 채팅방에서 사용자 제거
            server.disconnect(this);
        }
    }

    /*
     * 클라이언트 소켓 종료
     *
     * 소켓이 이미 종료되었더라도
     * IOException이 발생하면 무시한다.
     */
    private void closeSocket() {

        try {
            client_socket.close();

        } catch (IOException ignored) {
        }
    }
}
