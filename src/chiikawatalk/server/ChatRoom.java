package chiikawatalk.server;

import java.util.Vector;
import java.util.Set;
import java.util.TreeSet;

// 방 번호, 이름, 참여자를 관리하는 클래스
public class ChatRoom {
    private final int roomId;
    private final String roomName;
    private final Vector<ClientService> users = new Vector<>();

    private final String owner;
    private final Vector<String> history = new Vector<>();
    static final int HISTORY_LIMIT = 1000;
    private final Set<String> allowed = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    public ChatRoom(int roomId, String roomName, String owner, Set<String> invited) {
        this.roomId = roomId;
        this.roomName = roomName;
        this.owner = owner;
        allowed.add(owner);
        allowed.addAll(invited);
    }
    Set<String> getAllowed() { return new TreeSet<>(allowed); }
    Vector<String> getHistory() { return history; }
    public String getOwner() { return owner; }
    public boolean canJoin(String nickname) { return allowed.contains(nickname); }
    public int getRoomId() { return roomId; }
    public String getRoomName() { return roomName; }
    public void addUser(ClientService user) { if (!users.contains(user)) users.add(user); }
    public void removeUser(ClientService user) { users.remove(user); }
    public int getUserCount() { return users.size(); }
    public ClientService[] getUsers() { return users.toArray(new ClientService[0]); }
}
