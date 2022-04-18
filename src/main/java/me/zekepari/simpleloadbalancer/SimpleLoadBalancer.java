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
import java.util.Optional;
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

    private final ArrayList<RegisteredServer> servers = new ArrayList<>();
    private static RegisteredServer smallestServer;

    @Inject
    private SimpleLoadBalancer(ProxyServer server, Logger logger) {
        this.proxyServer = server;
        this.logger = logger;
    }

    @Subscribe
    private void onProxyInitialization(ProxyInitializeEvent event) {
        pingAllServers();
        updateSmallestServer();
    }

    @Subscribe
    private void onServerPreConnection(ServerPreConnectEvent event) {
        event.setResult(ServerPreConnectEvent.ServerResult.allowed(smallestServer));
    }

    @Subscribe
    private void onKickFromServer(KickedFromServerEvent event) {
        if (event.kickedDuringServerConnect()) {
            return;
        }
        event.setResult(KickedFromServerEvent.RedirectPlayer.create(smallestServer));
    }

    private void updateSmallestServer() {
        proxyServer.getScheduler()
                .buildTask(this, () -> {
                    Optional<RegisteredServer> minServer = servers.stream().min(Comparator.comparingInt(server -> server.getPlayersConnected().size()));
                    minServer.ifPresent(server -> smallestServer = server);
                })
                .repeat(400L, TimeUnit.MILLISECONDS)
                .schedule();
    }

    private void pingAllServers() {
        proxyServer.getScheduler()
                .buildTask(this, () -> {
                    for (RegisteredServer server : proxyServer.getAllServers()) {
                        server.ping().thenAccept(serverPing -> {
                            if (!servers.contains(server)) {
                                servers.add(server);
                                logger.info("Added " + server.getServerInfo().getName() + " to servers list.");
                            }
                        }).exceptionally(result -> {
                            if (servers.contains(server)) {
                                servers.remove(server);
                                logger.info("Removed " + server.getServerInfo().getName() + " from servers list.");
                            }
                            return null;
                        });
                    }
                })
                .repeat(2L, TimeUnit.SECONDS)
                .schedule();
    }
}
