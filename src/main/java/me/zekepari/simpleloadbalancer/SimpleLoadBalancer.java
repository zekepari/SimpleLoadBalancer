package me.zekepari.simpleloadbalancer;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

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
    private final Logger logger;

    private final List<RegisteredServer> tryServers = new ArrayList<>();

    @Inject
    private SimpleLoadBalancer(ProxyServer server, Logger logger) {
        this.proxyServer = server;
        this.logger = logger;
    }

    @Subscribe
    private void onProxyInitialization(ProxyInitializeEvent event) {
        tryServers.addAll(proxyServer.getAllServers());
        updateAllServers();
    }

    @Subscribe
    private void onServerPreConnection(ServerPreConnectEvent event) {
        Optional<RegisteredServer> optionalServer = proxyServer.getAllServers().stream().min(Comparator.comparingInt(server -> server.getPlayersConnected().size()));
        optionalServer.ifPresent(server -> event.setResult(ServerPreConnectEvent.ServerResult.allowed(server)));
        updateAllServers();
    }

    @Subscribe
    private void onKickFromServer(KickedFromServerEvent event) {
        if (event.kickedDuringServerConnect()) {
            return;
        }
        Optional<RegisteredServer> optionalServer = proxyServer.getAllServers().stream().filter(server -> server != event.getServer()).min(Comparator.comparingInt(server -> server.getPlayersConnected().size()));
        optionalServer.ifPresent(server -> event.setResult(KickedFromServerEvent.RedirectPlayer.create(server)));
        updateAllServers();
    }

    private void updateAllServers() {
        for (RegisteredServer server : tryServers) {
            server.ping().thenAccept(serverPing -> proxyServer.registerServer(server.getServerInfo())).exceptionally(result -> {
                proxyServer.unregisterServer(server.getServerInfo());
                return null;
            });
        }
    }
}
