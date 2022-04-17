package me.zekepari.simpleloadbalancer;

import com.google.inject.Inject;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import java.util.*;

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

    private final List<RegisteredServer> servers = new ArrayList<>();

    @Inject
    private SimpleLoadBalancer(ProxyServer server, Logger logger) {
        this.proxyServer = server;
        this.logger = logger;
    }

    @Subscribe
    private void onProxyInitialization(ProxyInitializeEvent event) {
        for (String serverName : proxyServer.getConfiguration().getAttemptConnectionOrder()) {
            proxyServer.getServer(serverName).ifPresent(servers::add);
        }
    }

    @Subscribe
    private void onKickedFromServer(KickedFromServerEvent event) {
        if (event.kickedDuringServerConnect()) {
            return;
        }

        getLowestServer().ifPresent(registeredServer -> event.setResult(KickedFromServerEvent.RedirectPlayer.create(registeredServer)));
    }

    @Subscribe
    private void onServerPreConnection(ServerPreConnectEvent event) {
        event.setResult(ServerPreConnectEvent.ServerResult.allowed(getLowestServer().get()));
    }

    private Optional<RegisteredServer> getLowestServer() {
        return proxyServer.getAllServers().stream().filter(servers::contains).min(Comparator.comparingInt(server -> server.getPlayersConnected().size()));
    }
}
