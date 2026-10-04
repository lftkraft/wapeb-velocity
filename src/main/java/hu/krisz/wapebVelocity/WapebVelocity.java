package hu.krisz.wapebVelocity;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
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
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

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

    @Inject
    public WapebVelocity(ProxyServer proxyServer, Logger logger) {
        this.proxyServer = proxyServer;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        proxyServer.getChannelRegistrar().register(CHANNEL_IDENTIFIER);
        logger.info("[wapeB-Velocity] Plugin initialized and channel 'wapeb:main' registered!");
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

                if (("IPBAN".equalsIgnoreCase(action) || "TEMPIPBAN".equalsIgnoreCase(action)) && targetIp != null && !targetIp.trim().isEmpty()) {
                    for (Player p : proxyServer.getAllPlayers()) {
                        if (p.getRemoteAddress() != null && targetIp.equalsIgnoreCase(p.getRemoteAddress().getAddress().getHostAddress())) {
                            disconnectPlayer(p, reason, serverScope);
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
        try {
            int punishmentId = in.readInt();
            String sourceServerName = in.readUTF();
            String snapshotJson = in.readUTF();

            if (sourceServerName == null || sourceServerName.trim().isEmpty()) {
                sourceServerName = "global";
            }

            File serverDir = new File("plugins/wapeb-velocity/snapshots/" + sourceServerName);
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
