package chiikawatalk.desktop;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * 닉네임별 데스크톱 캐릭터 창 관리. 이후 ChiikawaTalk의 MEMBER/LEFT와 연동 가능.
 */
public class DesktopPetManager {
    private final Map<String, DesktopPetWindow> pets = new LinkedHashMap<>();
    private volatile boolean visible = true;
    private volatile boolean movementEnabled = true;

    public void addPet(String nickname, String characterId) {
        onEdt(() -> {
            removePetInternal(nickname);
            try {
                DesktopPetWindow window = new DesktopPetWindow(
                    nickname, characterId, pets.size(), () -> removePet(nickname));
                pets.put(nickname, window);
                window.setMovementEnabled(movementEnabled);
                window.setVisible(visible);
            } catch (IOException | IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(null, ex.getMessage(),
                    "캐릭터 표시 오류", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    public void ensurePet(String nickname, String characterId) {
        onEdt(() -> {
            if (!pets.containsKey(nickname)) addPet(nickname, characterId);
        });
    }

    public void showMessage(String nickname, String message) {
        onEdt(() -> {
            DesktopPetWindow pet = pets.get(nickname);
            if (pet != null) pet.showChatBubble(nickname, message);
        });
    }

    public void hideBubbles() {
        onEdt(() -> {
            for (DesktopPetWindow pet : pets.values()) pet.hideBubble();
        });
    }

    public void removePet(String nickname) {
        onEdt(() -> removePetInternal(nickname));
    }

    private void removePetInternal(String nickname) {
        DesktopPetWindow existing = pets.remove(nickname);
        if (existing != null) existing.closePet();
    }

    public void clear() {
        onEdt(() -> {
            for (DesktopPetWindow pet : pets.values()) pet.closePet();
            pets.clear();
        });
    }

    public void setPetsVisible(boolean enabled) {
        visible = enabled;
        onEdt(() -> {
            for (DesktopPetWindow pet : pets.values()) pet.setVisible(enabled);
        });
    }

    public boolean isPetsVisible() { return visible; }

    public void setMovementEnabled(boolean enabled) {
        movementEnabled = enabled;
        onEdt(() -> {
            for (DesktopPetWindow pet : pets.values()) pet.setMovementEnabled(enabled);
        });
    }

    public boolean isMovementEnabled() { return movementEnabled; }

    private static void onEdt(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) task.run();
        else SwingUtilities.invokeLater(task);
    }
}
