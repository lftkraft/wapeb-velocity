package hu.krisz.wapebVelocity;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Plugin(
        id = "wapeb-velocity",
        name = "wapeb-velocity",
        version = "1.0.1",
        description = "wapeb's official velocity plugin",
        url = "https://coolnw.eu",
        authors = {"krisz"}
)
public class WapebVelocity {

    public static final MinecraftChannelIdentifier CHANNEL_IDENTIFIER = MinecraftChannelIdentifier.from("wapeb:main");

    private final ProxyServer proxyServer;
    private final Logger logger;
    private final Path dataDirectory;

    private boolean snapshotEnabled = true;
    private String retentionString = "1h";
    private long retentionMillis = 3600000L;

    @Inject
    public WapebVelocity(ProxyServer proxyServer, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        loadConfig();
        proxyServer.getChannelRegistrar().register(CHANNEL_IDENTIFIER);
        startRetentionCleanupTask();
        logger.info("[wapeB-Velocity] Plugin initialized and channel 'wapeb:main' registered!");
    }

    private void loadConfig() {
        try {
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
            }
            Path configFile = dataDirectory.resolve("config.yml");
            if (!Files.exists(configFile)) {
                try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.yml")) {
                    if (in != null) {
                        Files.copy(in, configFile);
                    } else {
                        Files.writeString(configFile, "# wapeB-Velocity Configuration\n\nchat-snapshot:\n  enabled: true\n  retention: \"1h\"\n", StandardCharsets.UTF_8);
                    }
                }
            }

            List<String> lines = Files.readAllLines(configFile, StandardCharsets.UTF_8);
            boolean inSnapshot = false;
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("#") || trimmed.isEmpty()) continue;
                if (trimmed.startsWith("chat-snapshot:")) {
                    inSnapshot = true;
                    continue;
                }
                if (inSnapshot) {
                    if (!line.startsWith(" ") && !line.startsWith("\t")) {
                        inSnapshot = false;
                    } else if (trimmed.startsWith("enabled:")) {
                        this.snapshotEnabled = Boolean.parseBoolean(trimmed.substring("enabled:".length()).trim());
                    } else if (trimmed.startsWith("retention:")) {
                        this.retentionString = trimmed.substring("retention:".length()).trim().replace("\"", "").replace("'", "");
                    }
                }
            }
            this.retentionMillis = parseTime(this.retentionString);
            logger.info("[wapeB-Velocity] Config loaded! Snapshots enabled: {}, retention: '{}'", snapshotEnabled, retentionString);
        } catch (Exception e) {
            logger.error("[wapeB-Velocity] Failed to load config.yml", e);
        }
    }

    private void startRetentionCleanupTask() {
        proxyServer.getScheduler()
                .buildTask(this, this::runSnapshotCleanup)
                .repeat(15, TimeUnit.MINUTES)
                .schedule();
    }

    private void runSnapshotCleanup() {
        if (!snapshotEnabled || retentionMillis <= 0) return;

        File snapshotsBase = new File(dataDirectory.toFile(), "snapshots");
        if (!snapshotsBase.exists() || !snapshotsBase.isDirectory()) return;

        long now = System.currentTimeMillis();
        int deletedCount = 0;

        File[] serverDirs = snapshotsBase.listFiles(File::isDirectory);
        if (serverDirs != null) {
            for (File serverDir : serverDirs) {
                File[] snapshotFiles = serverDir.listFiles((dir, name) -> name.endsWith(".json"));
                if (snapshotFiles != null) {
                    for (File f : snapshotFiles) {
                        if (now - f.lastModified() > retentionMillis) {
                            if (f.delete()) {
                                deletedCount++;
                            }
                        }
                    }
                }
            }
        }

        if (deletedCount > 0) {
            logger.info("[wapeB-Velocity] Retention cleanup deleted {} expired chat snapshot(s).", deletedCount);
        }
    }

    public static long parseTime(String input) {
        if (input == null || input.trim().isEmpty() || input.equals("0")) return -1;
        input = input.trim().toLowerCase();

        long totalMillis = 0;
        StringBuilder number = new StringBuilder();

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (Character.isDigit(c)) {
                number.append(c);
            } else {
                if (number.length() == 0) continue;
                long value = Long.parseLong(number.toString());
                number.setLength(0);

                switch (c) {
                    case 's': totalMillis += value * 1000L; break;
                    case 'm': totalMillis += value * 60L * 1000L; break;
                    case 'h': totalMillis += value * 60L * 60L * 1000L; break;
                    case 'd': totalMillis += value * 24L * 60L * 60L * 1000L; break;
                    case 'w': totalMillis += value * 7L * 24L * 60L * 60L * 1000L; break;
                    default: break;
                }
            }
        }
        if (number.length() > 0 && totalMillis == 0) {
            long val = Long.parseLong(number.toString());
            totalMillis = val * 60L * 60L * 1000L;
        }
        return totalMillis > 0 ? totalMillis : -1;
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(CHANNEL_IDENTIFIER)) {
            return;
        }

        if (!(event.getSource() instanceof ServerConnection)) {
            event.setResult(PluginMessageEvent.ForwardResult.handled());
            return;
        }

        event.setResult(PluginMessageEvent.ForwardResult.handled());

        byte[] data = event.getData();
        ByteArrayDataInput in = ByteStreams.newDataInput(data);

        String subChannel = in.readUTF();
        if ("PUNISH_BROADCAST".equalsIgnoreCase(subChannel)) {
            handlePunishBroadcast(data, in);
        } else if ("CHAT_SNAPSHOT".equalsIgnoreCase(subChannel)) {
            handleChatSnapshot(in);
        }
    }

    private void handlePunishBroadcast(byte[] rawData, ByteArrayDataInput in) {
        try {
            int punishmentId = in.readInt();
            String action = in.readUTF();
            String targetUuidStr = in.readUTF();
            String targetName = in.readUTF();
            String targetIp = in.readUTF();
            String reason = in.readUTF();
            String executor = in.readUTF();
            String serverScope = in.readUTF();
            long duration = in.readLong();
            boolean silent = in.readBoolean();
            String sourceServerName = in.readUTF();
            String broadcastMessage = in.readUTF();

            logger.info("[wapeB-Velocity] Broadcast received from '{}' (ID: {}) -> Action: {} | Target: {} | Server: {} | Silent: {}",
                    sourceServerName, punishmentId, action, targetName, serverScope, silent);

            UUID targetUuid = null;
            if (targetUuidStr != null && !targetUuidStr.trim().isEmpty()) {
                try {
                    targetUuid = UUID.fromString(targetUuidStr);
                } catch (IllegalArgumentException ignored) {
                }
            }

            boolean isBanOrKick = "BAN".equalsIgnoreCase(action) || "TEMPBAN".equalsIgnoreCase(action) 
                    || "IPBAN".equalsIgnoreCase(action) || "TEMPIPBAN".equalsIgnoreCase(action) 
                    || "KICK".equalsIgnoreCase(action);

            if (isBanOrKick) {
                Optional<Player> onlinePlayer = targetUuid != null ? proxyServer.getPlayer(targetUuid) : proxyServer.getPlayer(targetName);
                onlinePlayer.ifPresent(player -> disconnectPlayer(player, reason, serverScope));

                if ("IPBAN".equalsIgnoreCase(action) || "TEMPIPBAN".equalsIgnoreCase(action)) {
                    String cleanTargetIp = targetIp != null ? targetIp.replace("/", "").trim() : "";
                    if (!cleanTargetIp.isEmpty() && !cleanTargetIp.equalsIgnoreCase("null") && !cleanTargetIp.equalsIgnoreCase("unknown")) {
                        for (Player p : proxyServer.getAllPlayers()) {
                            if (p.getRemoteAddress() != null && p.getRemoteAddress().getAddress() != null) {
                                String playerIp = p.getRemoteAddress().getAddress().getHostAddress();
                                if (cleanTargetIp.equalsIgnoreCase(playerIp)) {
                                    disconnectPlayer(p, reason, serverScope);
                                }
                            }
                        }
                    }
                }
            }

            for (RegisteredServer server : proxyServer.getAllServers()) {
                if (!server.getServerInfo().getName().equalsIgnoreCase(sourceServerName)) {
                    server.sendPluginMessage(CHANNEL_IDENTIFIER, rawData);
                }
            }
        } catch (Exception e) {
            logger.error("[wapeB-Velocity] Error handling punish broadcast", e);
        }
    }

    private void disconnectPlayer(Player player, String reason, String serverScope) {
        boolean isGlobal = serverScope == null || serverScope.equalsIgnoreCase("global") || serverScope.equalsIgnoreCase("all");

        if (isGlobal) {
            player.disconnect(LegacyComponentSerializer.legacyAmpersand().deserialize("&cYou have been disconnected: " + reason));
        } else {
            Optional<ServerConnection> currentServer = player.getCurrentServer();
            if (currentServer.isPresent()) {
                String currentServerName = currentServer.get().getServerInfo().getName();
                boolean serverMatch = false;
                for (String s : serverScope.split(",")) {
                    if (s.trim().equalsIgnoreCase(currentServerName)) {
                        serverMatch = true;
                        break;
                    }
                }
                if (serverMatch) {
                    player.disconnect(LegacyComponentSerializer.legacyAmpersand().deserialize("&cYou have been disconnected from " + currentServerName + ": " + reason));
                }
            }
        }
    }

    private void handleChatSnapshot(ByteArrayDataInput in) {
        if (!snapshotEnabled) return;
        try {
            int punishmentId = in.readInt();
            String sourceServerName = in.readUTF();
            String snapshotJson = in.readUTF();

            if (sourceServerName == null || sourceServerName.trim().isEmpty()) {
                sourceServerName = "global";
            }
            sourceServerName = sourceServerName.replaceAll("[^a-zA-Z0-9_-]", "_");

            File serverDir = new File(new File(dataDirectory.toFile(), "snapshots"), sourceServerName);
            if (!serverDir.exists()) {
                serverDir.mkdirs();
            }

            File snapshotFile = new File(serverDir, punishmentId + ".json");
            try (FileWriter writer = new FileWriter(snapshotFile, StandardCharsets.UTF_8)) {
                writer.write(snapshotJson);
            }

            logger.info("[wapeB-Velocity] Chat snapshot received from server '{}' for punishment #{} -> saved to {}",
                    sourceServerName, punishmentId, snapshotFile.getPath());
        } catch (Exception e) {
            logger.error("[wapeB-Velocity] Error saving chat snapshot", e);
        }
    }

    private Component parseComponent(String message) {
        try {
            if (message.contains("<") && message.contains(">")) {
                return MiniMessage.miniMessage().deserialize(message);
            }
        } catch (Exception ignored) {
        }
        return LegacyComponentSerializer.legacyAmpersand().deserialize(message.replace("§", "&"));
    }
}
