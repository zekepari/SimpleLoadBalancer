package me.zekepari.simpleloadbalancer;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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

    private final HashMap<RegisteredServer, Integer> servers = new HashMap<>();

    @Inject
    private SimpleLoadBalancer(ProxyServer server, Logger logger) {
        this.proxyServer = server;
        this.logger = logger;
    }

    @Subscribe
    private void onProxyInitialization(ProxyInitializeEvent event) {
        updateAllServers();
    }

    @Subscribe
    private void onServerPreConnection(ServerPreConnectEvent event) {
        event.setResult(ServerPreConnectEvent.ServerResult.allowed(getLowestServer(null)));
    }

    @Subscribe
    private void onServerConnection(ServerConnectedEvent event) {
        servers.replace(event.getServer(), servers.get(event.getServer()) + 1);
    }

    @Subscribe
    private void onKickFromServer(KickedFromServerEvent event) {
        servers.replace(event.getServer(), servers.get(event.getServer()) - 1);
        if (event.kickedDuringServerConnect()) {
            return;
        }
        event.setResult(KickedFromServerEvent.RedirectPlayer.create(getLowestServer(event.getServer())));
    }

    private RegisteredServer getLowestServer(RegisteredServer server) {
        Map.Entry<RegisteredServer, Integer> min = null;
        for (Map.Entry<RegisteredServer, Integer> entry : servers.entrySet()) {
            if (min == null || min.getValue() > entry.getValue() && entry.getKey() != server) {
                min = entry;
            }
        }
        assert min != null;
        return min.getKey();
    }

    private void updateAllServers() {
        proxyServer.getScheduler()
                .buildTask(this, () -> {
                    for (RegisteredServer server : proxyServer.getAllServers()) {
                        server.ping().thenAccept(serverPing -> {
                            if (!servers.containsKey(server)) {
                                servers.put(server, server.getPlayersConnected().size());
                                logger.info("Added " + server.getServerInfo().getName() + " to servers list.");
                            }
                        }).exceptionally(result -> {
                            if (servers.containsKey(server)) {
                                servers.remove(server);
                                logger.info("Removed " + server.getServerInfo().getName() + " from servers list.");
                            }
                            return null;
                        });
                    }
                })
                .repeat(5L, TimeUnit.SECONDS)
                .schedule();
    }
}
