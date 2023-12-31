package cn.xor7.iseeyou;

import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import top.leavesmc.leaves.entity.Photographer;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * @author MC_XiaoHei
 */
public class EventListener implements Listener {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd@HH-mm-ss");
    @Setter
    private static Double pauseRecordingOnHighSpeedThresholdPerTickSquared;

    @EventHandler
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) throws IOException {
        Player player = event.getPlayer();
        String playerUniqueId = player.getUniqueId().toString();
        if (!ISeeYou.getToml().data.shouldRecordPlayer(player)) {
            return;
        }
        if (ISeeYou.getToml().data.pauseInsteadOfStopRecordingOnPlayerQuit && ISeeYou.getPhotographers().containsKey(playerUniqueId)) {
            Photographer photographer = ISeeYou.getPhotographers().get(playerUniqueId);
            photographer.resumeRecording();
            photographer.setFollowPlayer(player);
            return;
        }
        String prefix = player.getName();
        if (prefix.length() > 10) {
            prefix = prefix.substring(0, 10);
        }
        if (prefix.startsWith(".")) { // fix Floodgate
            prefix = prefix.replace(".", "_");
        }
        Photographer photographer = Bukkit
                .getPhotographerManager()
                .createPhotographer(
                        (prefix + "_" + UUID.randomUUID().toString().replaceAll("-", "")).substring(0, 16),
                        player.getLocation());
        if (photographer == null) {
            throw new RuntimeException(
                    "Error on create photographer for player: {name: " + player.getName() + " , UUID:" + playerUniqueId + "}");
        }

        LocalDateTime currentTime = LocalDateTime.now();
        String recordPath = ISeeYou.getToml().data.recordPath
                .replace("${name}", player.getName())
                .replace("${uuid}", playerUniqueId);
        new File(recordPath).mkdirs();
        File recordFile = new File(recordPath + "/" + currentTime.format(DATE_FORMATTER) + ".mcpr");
        if (recordFile.exists()) {
            recordFile.delete();
        }
        recordFile.createNewFile();
        photographer.setRecordFile(recordFile);

        ISeeYou.getPhotographers().put(playerUniqueId, photographer);
        photographer.setFollowPlayer(player);
    }

    @EventHandler
    public void onPlayerMove(@NotNull PlayerMoveEvent event) {
        Photographer photographer = ISeeYou.getPhotographers().get(event.getPlayer().getUniqueId().toString());
        Vector velocity = event.getPlayer().getVelocity();
        if (ISeeYou.getToml().data.pauseRecordingOnHighSpeed.enable &&
                Math.pow(velocity.getX(), 2) + Math.pow(velocity.getZ(), 2) > pauseRecordingOnHighSpeedThresholdPerTickSquared &&
                !ISeeYou.getHighSpeedPausedPhotographers().contains(photographer)) {
            photographer.pauseRecording();
            ISeeYou.getHighSpeedPausedPhotographers().add(photographer);
        }
        photographer.resumeRecording();
        photographer.setFollowPlayer(event.getPlayer());
        ISeeYou.getHighSpeedPausedPhotographers().remove(photographer);
    }

    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        Photographer photographer = ISeeYou.getPhotographers().get(event.getPlayer().getUniqueId().toString());
        ISeeYou.getHighSpeedPausedPhotographers().remove(photographer);
        if (photographer == null) {
            return;
        }
        if (ISeeYou.getToml().data.pauseInsteadOfStopRecordingOnPlayerQuit) {
            photographer.resumeRecording();
        } else {
            photographer.stopRecording();
            ISeeYou.getPhotographers().remove(event.getPlayer().getUniqueId().toString());
        }
    }
}
