package me.zekepari.simpleloadbalancer;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Plugin(
        id = "simpleloadbalancer",
        name = "SimpleLoadBalancer",
        version = "1.0",
        description = "Load balancing plugin for Velocity",
        authors = {"daf_t"}
)
public class SimpleLoadBalancer {

    private final ProxyServer proxyServer;

    private final List<RegisteredServer> servers = new ArrayList<>();
    static int counter = 0;

    @Inject
    private SimpleLoadBalancer(ProxyServer server) {
        this.proxyServer = server;
    }

    @Subscribe
    private void onProxyInitialization(ProxyInitializeEvent event) {
        updateAllServers();
    }

    @Subscribe
    private void onServerPreConnection(ServerPreConnectEvent event) {
        int highestCount = 0;
        int serverNumber = 0;

        for (RegisteredServer server : servers) {
            if (server.getPlayersConnected().size() > highestCount) {
                highestCount = server.getPlayersConnected().size();
            }
            serverNumber++;
        }

        int serverChoice = counter % servers.size();

        if (serverChoice == serverNumber) {
            serverChoice++;
            serverChoice %= servers.size();
        }

        counter++;

        RegisteredServer selectedServer = servers.get(serverChoice);
        event.setResult(ServerPreConnectEvent.ServerResult.allowed(selectedServer));
    }

    @Subscribe
    private void onKickFromServer(KickedFromServerEvent event) {
        if (event.kickedDuringServerConnect()) {
            return;
        }
        Optional<RegisteredServer> optionalServer = servers.stream().filter(server -> server != event.getServer()).min(Comparator.comparingInt(server -> server.getPlayersConnected().size()));
        optionalServer.ifPresent(server -> event.setResult(KickedFromServerEvent.RedirectPlayer.create(server)));
        updateAllServers();
    }

    @Subscribe
    private void onDisconnection(DisconnectEvent event) {
        updateAllServers();
    }

    private void updateAllServers() {
        for (RegisteredServer server : proxyServer.getAllServers()) {
            server.ping().thenAccept(serverPing -> servers.add(server)).exceptionally(result -> {
                servers.remove(server);
                return null;
            });
        }
    }
}
