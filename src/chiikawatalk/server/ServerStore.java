
package chiikawatalk.server;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/*
 * ServerStore 클래스
 *
 * 서버에서 사용하는 데이터를 파일로 저장하고
 * 서버가 재시작될 때 기존 데이터를 불러온다.
 *
 * 저장 대상
 * 1. 회원 계정 이름
 * 2. 비밀번호 검증을 위한 Salt와 Hash
 * 3. 채팅방 번호, 이름, 방장
 * 4. 채팅방 초대 사용자 목록
 * 5. 채팅 메시지 기록
 *
 * 현재 접속 중인 소켓이나 스레드는 저장하지 않는다.
 */
final class ServerStore {

    // 저장 파일의 형식을 확인하기 위한 식별값
    private static final int MAGIC = 0x43544B31;

    // 데이터를 저장할 파일 경로
    private final Path path;

    /*
     * 회원 계정 목록
     *
     * Key   : 사용자 이름
     * Value : Account 객체
     *
     * 대소문자를 구분하지 않고 계정을 검색한다.
     */
    private final Map<String, Account> accounts =
        new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    // 저장된 채팅방 목록
    final Vector<ChatRoom> rooms = new Vector<>();

    /*
     * Account 내부 클래스
     *
     * 회원 계정의 인증 정보를 저장한다.
     *
     * name : 사용자 이름
     * salt : 비밀번호 해시 생성에 사용하는 임의 값
     * hash : 비밀번호를 해시 처리한 결과
     *
     * 비밀번호 원문은 저장하지 않는다.
     */
    private static final class Account {

        final String name;
        final byte[] salt;
        final byte[] hash;

        Account(String name, byte[] salt, byte[] hash) {
            this.name = name;
            this.salt = salt;
            this.hash = hash;
        }
    }

    /*
     * ServerStore 생성자
     *
     * 지정한 경로에 저장 파일이 존재하면
     * load()를 호출하여 기존 데이터를 읽는다.
     *
     * 파일이 없으면 빈 데이터로 시작한다.
     */
    ServerStore(Path path) throws IOException {

        this.path = path;

        if (Files.exists(path)) {
            load();
        }
    }

    /*
     * 사용자 이름의 실제 저장 표기 반환
     *
     * 대소문자를 구분하지 않고 검색한 후
     * 등록된 이름을 반환한다.
     *
     * 계정이 없으면 null 반환
     */
    String canonicalName(String name) {

        Account a = accounts.get(name);

        return a == null ? null : a.name;
    }

    /*
     * 로그인 비밀번호 검사
     *
     * 입력한 비밀번호를 기존 Salt로 다시 해시하고,
     * 저장된 Hash와 비교한다.
     *
     * 일치하면 true, 다르면 false를 반환한다.
     */
    boolean matches(
            String name,
            String password) throws IOException {

        Account a = accounts.get(name);

        return a != null &&
            MessageDigest.isEqual(
                a.hash,
                hash(password, a.salt)
            );
    }

    /*
     * 신규 회원 계정 추가
     *
     * 1. 무작위 Salt 생성
     * 2. 비밀번호 해시 생성
     * 3. 계정 목록에 추가
     * 4. 파일에 저장
     *
     * 저장에 실패하면 추가한 계정을 제거한다.
     */
    void addAccount(
            String name,
            String password) throws IOException {

        // 비밀번호 해시에 사용할 16바이트 Salt 생성
        byte[] salt = new byte[16];

        new SecureRandom().nextBytes(salt);

        // 계정 정보 등록
        accounts.put(
            name,
            new Account(
                name,
                salt,
                hash(password, salt)
            )
        );

        try {

            // 변경된 계정 정보를 파일에 저장
            save();

        } catch (IOException e) {

            // 저장 실패 시 계정 등록 취소
            accounts.remove(name);
            throw e;
        }
    }

    /*
     * 비밀번호 해시 생성
     *
     * PBKDF2WithHmacSHA256 알고리즘 사용
     *
     * 반복 횟수 : 120000회
     * 해시 길이 : 256비트
     *
     * 같은 비밀번호라도 Salt가 다르면
     * 서로 다른 해시 결과가 생성된다.
     */
    private static byte[] hash(
            String password,
            byte[] salt) throws IOException {

        PBEKeySpec spec = new PBEKeySpec(
            password.toCharArray(),
            salt,
            120000,
            256
        );

        try {

            return SecretKeyFactory
                .getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .getEncoded();

        } catch (GeneralSecurityException e) {

            throw new IOException(
                "비밀번호 처리 실패",
                e
            );

        } finally {

            // 비밀번호 문자 배열 참조를 정리
            spec.clearPassword();
        }
    }

    /*
     * 저장 파일에서 데이터 개수 읽기
     *
     * 음수이거나 허용된 최대 개수를 초과하면
     * 저장 파일 형식 오류로 처리한다.
     *
     * 잘못된 파일로 인해 과도한 데이터를
     * 읽으려는 상황을 방지한다.
     */
    private int count(
            DataInputStream in,
            int max) throws IOException {

        int n = in.readInt();

        if (n < 0 || n > max) {
            throw new IOException("저장 파일 형식 오류");
        }

        return n;
    }

    /*
     * 저장 파일 불러오기
     *
     * 서버 실행 시 기존 데이터를 복원한다.
     *
     * 처리 순서
     * 1. 파일 식별값 검사
     * 2. 회원 계정 읽기
     * 3. 채팅방 정보 읽기
     * 4. 초대 사용자 목록 읽기
     * 5. 채팅 기록 읽기
     */
    private void load() throws IOException {

        try (DataInputStream in =
                new DataInputStream(
                    new BufferedInputStream(
                        Files.newInputStream(path)
                    )
                )) {

            // 저장 파일의 형식 확인
            if (in.readInt() != MAGIC) {
                throw new IOException("저장 파일 버전 오류");
            }

            // 저장된 회원 계정 개수
            int n = count(in, 100000);

            // 회원 계정 정보 복원
            for (int i = 0; i < n; i++) {

                String name = in.readUTF();

                byte[] salt = new byte[16];
                byte[] hash = new byte[32];

                // Salt와 Hash를 파일에서 읽기
                in.readFully(salt);
                in.readFully(hash);

                accounts.put(
                    name,
                    new Account(name, salt, hash)
                );
            }

            // 저장된 채팅방 개수
            int nr = count(in, 100);

            // 채팅방 정보 복원
            for (int i = 0; i < nr; i++) {

                int id = in.readInt();

                String name = in.readUTF();
                String owner = in.readUTF();

                // 채팅방 입장이 허용된 사용자 목록
                Set<String> allowed =
                    new TreeSet<>(
                        String.CASE_INSENSITIVE_ORDER
                    );

                // 초대 사용자 수 읽기
                int na = count(in, 101);

                for (int j = 0; j < na; j++) {
                    allowed.add(in.readUTF());
                }

                // 채팅방 객체 복원
                ChatRoom room = new ChatRoom(
                    id,
                    name,
                    owner,
                    allowed
                );

                // 채팅방의 저장된 메시지 개수
                int nh = count(
                    in,
                    ChatRoom.HISTORY_LIMIT
                );

                // 채팅 기록 복원
                for (int j = 0; j < nh; j++) {
                    room.getHistory().add(in.readUTF());
                }

                rooms.add(room);
            }
        }
    }

    /*
     * 서버 데이터 저장
     *
     * 회원 계정과 채팅방 정보를
     * 바이너리 파일로 저장한다.
     *
     * 저장 순서
     * 1. 파일 식별값
     * 2. 회원 계정 정보
     * 3. 채팅방 정보
     * 4. 초대 사용자 목록
     * 5. 채팅 기록
     *
     * 임시 파일에 먼저 기록한 후
     * 기존 저장 파일을 교체하는 방식이다.
     */
    void save() throws IOException {

        // 저장 경로의 상위 폴더 생성
        Files.createDirectories(
            path.toAbsolutePath().getParent()
        );

        // 저장 도중 기존 파일이 손상되지 않도록 임시 파일 생성
        Path tmp = Files.createTempFile(
            path.toAbsolutePath().getParent(),
            "chat-",
            ".tmp"
        );

        try {

            try (DataOutputStream out =
                    new DataOutputStream(
                        new BufferedOutputStream(
                            Files.newOutputStream(tmp)
                        )
                    )) {

                // 저장 파일 식별값 기록
                out.writeInt(MAGIC);

                // 회원 계정 개수 기록
                out.writeInt(accounts.size());

                // 회원 계정 정보 저장
                for (Account a : accounts.values()) {

                    out.writeUTF(a.name);
                    out.write(a.salt);
                    out.write(a.hash);
                }

                // 채팅방 개수 기록
                out.writeInt(rooms.size());

                // 각 채팅방의 정보 저장
                for (ChatRoom room : rooms) {

                    // 채팅방 번호, 이름, 방장
                    out.writeInt(room.getRoomId());
                    out.writeUTF(room.getRoomName());
                    out.writeUTF(room.getOwner());

                    // 입장 권한이 있는 사용자 목록
                    Set<String> allowed = room.getAllowed();

                    out.writeInt(allowed.size());

                    for (String name : allowed) {
                        out.writeUTF(name);
                    }

                    // 채팅방에 저장된 메시지 목록
                    out.writeInt(
                        room.getHistory().size()
                    );

                    for (String msg : room.getHistory()) {
                        out.writeUTF(msg);
                    }
                }
            }

            /*
             * 임시 파일을 실제 저장 파일로 이동
             *
             * ATOMIC_MOVE를 우선 사용하여
             * 가능한 경우 파일 교체를 원자적으로 처리한다.
             *
             * 파일 시스템이 지원하지 않으면
             * 일반 파일 교체 방식으로 처리한다.
             */
            try {

                Files.move(
                    tmp,
                    path,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                );

            } catch (AtomicMoveNotSupportedException e) {

                Files.move(
                    tmp,
                    path,
                    StandardCopyOption.REPLACE_EXISTING
                );
            }

        } finally {

            // 임시 파일이 남아 있으면 삭제
            Files.deleteIfExists(tmp);
        }
    }
}
