# SimpleLoadBalancer
SimpleLoadBalancer calculates the least occupied server every 400ms instead of connection/disconnection events.

Unlike other load balancers, SimpleLoadBalancer detects when a server is offline and will not send players to it.
## Drawbacks
The speed at which players are redirected from a shutting down server is below 400ms. This means they often won't be put into the least occupied server.
