package chiikawatalk.server;

import java.util.Vector;

// 방 번호, 이름, 참여자를 관리하는 클래스
public class ChatRoom {
    private final int roomId;
    private final String roomName;
    private final Vector<ClientService> users = new Vector<>();

    public ChatRoom(int roomId, String roomName) {
        this.roomId = roomId;
        this.roomName = roomName;
    }
    public int getRoomId() { return roomId; }
    public String getRoomName() { return roomName; }
    public void addUser(ClientService user) { if (!users.contains(user)) users.add(user); }
    public void removeUser(ClientService user) { users.remove(user); }
    public int getUserCount() { return users.size(); }
    public ClientService[] getUsers() { return users.toArray(new ClientService[0]); }
}
