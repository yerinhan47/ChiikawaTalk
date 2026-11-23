
package chiikawatalk.server;

import java.util.Vector;
import java.util.Set;
import java.util.TreeSet;

/*
 * ChatRoom 클래스
 *
 * 하나의 채팅방에 대한 정보를 관리한다.
 *
 * 주요 기능
 * 1. 채팅방 번호 및 이름 관리
 * 2. 채팅방 생성자(방장) 정보 관리
 * 3. 채팅방에 참여 중인 사용자 관리
 * 4. 초대받은 사용자만 입장할 수 있도록 권한 검사
 * 5. 채팅 기록 보관
 */
public class ChatRoom {

    // 채팅방을 구분하는 고유 번호
    private final int roomId;

    // 채팅방 이름
    private final String roomName;

    // 현재 채팅방에 입장한 사용자 목록
    private final Vector<ClientService> users = new Vector<>();

    // 채팅방을 생성한 사용자 이름
    private final String owner;

    // 채팅방에서 주고받은 메시지 기록
    private final Vector<String> history = new Vector<>();

    // 채팅 기록 최대 보관 개수
    static final int HISTORY_LIMIT = 1000;

    // 채팅방 입장이 허용된 사용자 목록
    // 대소문자를 구분하지 않고 사용자 이름 비교
    private final Set<String> allowed =
        new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    /*
     * 채팅방 생성자
     *
     * roomId   : 채팅방 번호
     * roomName : 채팅방 이름
     * owner    : 채팅방 생성자
     * invited  : 초대받은 사용자 목록
     */
    public ChatRoom(
            int roomId,
            String roomName,
            String owner,
            Set<String> invited) {

        this.roomId = roomId;
        this.roomName = roomName;
        this.owner = owner;

        // 방장은 자동으로 입장 권한을 가진다.
        allowed.add(owner);

        // 초대받은 사용자들에게 입장 권한 부여
        allowed.addAll(invited);
    }

    // 입장이 허용된 사용자 목록의 복사본 반환
    Set<String> getAllowed() {
        return new TreeSet<>(allowed);
    }

    // 저장된 채팅 기록 반환
    Vector<String> getHistory() {
        return history;
    }

    // 채팅방 생성자 이름 반환
    public String getOwner() {
        return owner;
    }

    // 해당 사용자가 채팅방에 입장할 수 있는지 확인
    public boolean canJoin(String nickname) {
        return allowed.contains(nickname);
    }

    // 채팅방 번호 반환
    public int getRoomId() {
        return roomId;
    }

    // 채팅방 이름 반환
    public String getRoomName() {
        return roomName;
    }

    /*
     * 채팅방에 사용자 추가
     *
     * 동일한 사용자가 중복으로 등록되지 않도록 검사한다.
     */
    public void addUser(ClientService user) {
        if (!users.contains(user)) {
            users.add(user);
        }
    }

    // 채팅방에서 사용자 제거
    public void removeUser(ClientService user) {
        users.remove(user);
    }

    // 현재 채팅방 참여자 수 반환
    public int getUserCount() {
        return users.size();
    }

    /*
     * 현재 참여자 목록을 배열로 반환
     *
     * 서버에서 특정 채팅방의 모든 사용자에게
     * 메시지를 전송할 때 사용한다.
     */
    public ClientService[] getUsers() {
        return users.toArray(new ClientService[0]);
    }
}
