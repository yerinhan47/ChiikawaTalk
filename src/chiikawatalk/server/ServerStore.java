package chiikawatalk.server;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

// 서버 계정과 채팅방을 파일에 저장. 접속 중인 소켓은 저장하지 않는다.
final class ServerStore {
    private static final int MAGIC = 0x43544B31;
    private final Path path;
    private final Map<String, Account> accounts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    final Vector<ChatRoom> rooms = new Vector<>();
    private static final class Account {
        final String name; final byte[] salt, hash;
        Account(String name, byte[] salt, byte[] hash) { this.name=name; this.salt=salt; this.hash=hash; }
    }
    ServerStore(Path path) throws IOException {
        this.path = path;
        if (Files.exists(path)) load();
    }
    String canonicalName(String name) { Account a = accounts.get(name); return a == null ? null : a.name; }
    boolean matches(String name, String password) throws IOException {
        Account a = accounts.get(name);
        return a != null && MessageDigest.isEqual(a.hash, hash(password, a.salt));
    }
    void addAccount(String name, String password) throws IOException {
        byte[] salt = new byte[16]; new SecureRandom().nextBytes(salt);
        accounts.put(name, new Account(name, salt, hash(password, salt)));
        try { save(); } catch (IOException e) { accounts.remove(name); throw e; }
    }
    private static byte[] hash(String password, byte[] salt) throws IOException {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 120000, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch (GeneralSecurityException e) { throw new IOException("비밀번호 처리 실패", e); }
        finally { spec.clearPassword(); }
    }
    private int count(DataInputStream in, int max) throws IOException {
        int n=in.readInt(); if(n<0 || n>max) throw new IOException("저장 파일 형식 오류"); return n;
    }
    private void load() throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            if(in.readInt()!=MAGIC) throw new IOException("저장 파일 버전 오류");
            int n=count(in,100000);
            for(int i=0;i<n;i++) {
                String name=in.readUTF(); byte[] salt=new byte[16], hash=new byte[32];
                in.readFully(salt); in.readFully(hash); accounts.put(name,new Account(name,salt,hash));
            }
            int nr=count(in,100);
            for(int i=0;i<nr;i++) {
                int id=in.readInt(); String name=in.readUTF(), owner=in.readUTF();
                Set<String> allowed=new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
                int na=count(in,101); for(int j=0;j<na;j++) allowed.add(in.readUTF());
                ChatRoom room=new ChatRoom(id,name,owner,allowed);
                int nh=count(in,ChatRoom.HISTORY_LIMIT); for(int j=0;j<nh;j++) room.getHistory().add(in.readUTF());
                rooms.add(room);
            }
        }
    }
    void save() throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path tmp=Files.createTempFile(path.toAbsolutePath().getParent(), "chat-", ".tmp");
        try {
            try (DataOutputStream out=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp)))) {
                out.writeInt(MAGIC); out.writeInt(accounts.size());
                for(Account a:accounts.values()) { out.writeUTF(a.name); out.write(a.salt); out.write(a.hash); }
                out.writeInt(rooms.size());
                for(ChatRoom room:rooms) {
                    out.writeInt(room.getRoomId()); out.writeUTF(room.getRoomName()); out.writeUTF(room.getOwner());
                    Set<String> allowed=room.getAllowed(); out.writeInt(allowed.size()); for(String name:allowed) out.writeUTF(name);
                    out.writeInt(room.getHistory().size()); for(String msg:room.getHistory()) out.writeUTF(msg);
                }
            }
            try { Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
            catch(AtomicMoveNotSupportedException e) { Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(tmp); }
    }
}
