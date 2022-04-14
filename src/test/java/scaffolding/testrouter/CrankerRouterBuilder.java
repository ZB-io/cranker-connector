package scaffolding.testrouter;

import io.muserver.Mutils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static java.util.Collections.emptyList;

/**
 * Use {@link #crankerRouter()} to create a builder where you can configure your cranker router options.
 * The {@link #start()} method returns a {@link CrankerRouter} object which can be used to create handlers
 * that you add to your own Mu Server instance.
 */
public class CrankerRouterBuilder {

    private IPValidator ipValidator = IPValidator.AllowAll;
    private boolean discardClientForwardedHeaders = false;
    private boolean sendLegacyForwardedHeaders = false;
    private String viaValue = "muc";
    private final Set<String> doNotProxyHeaders = new HashSet<>();
    private int maxAttempts = 20;
    private long durationBetweenRetriesInMillis = 250;
    private long pingAfterWriteMillis = 10000;
    private long idleReadTimeoutMills = 60000;
    private List<ProxyListener> completionListeners = emptyList();

    /**
     * @return A new builder
     */
    public static CrankerRouterBuilder crankerRouter() {
        return new CrankerRouterBuilder();
    }

    /**
     * If true, then any <code>Forwarded</code> or <code>X-Forwarded-*</code> headers that are sent
     * from the client to this reverse proxy will be dropped (defaults to false). Set this to <code>true</code>
     * if you do not trust the client.
     *
     * @param discardClientForwardedHeaders <code>true</code> to ignore Forwarded headers from the client; otherwise <code>false</code>
     * @return This builder
     */
    public CrankerRouterBuilder withDiscardClientForwardedHeaders(boolean discardClientForwardedHeaders) {
        this.discardClientForwardedHeaders = discardClientForwardedHeaders;
        return this;
    }

    /**
     * Mucranker always sends <code>Forwarded</code> headers, however by default does not send the
     * non-standard <code>X-Forwarded-*</code> headers. Set this to <code>true</code> to enable
     * these legacy headers for older clients that rely on them.
     *
     * @param sendLegacyForwardedHeaders <code>true</code> to forward headers such as <code>X-Forwarded-Host</code>; otherwise <code>false</code>
     * @return This builder
     */
    public CrankerRouterBuilder withSendLegacyForwardedHeaders(boolean sendLegacyForwardedHeaders) {
        this.sendLegacyForwardedHeaders = sendLegacyForwardedHeaders;
        return this;
    }

    /**
     * The name to add as the <code>Via</code> header, which defaults to <code>muc</code>.
     *
     * @param viaName The name to add to the <code>Via</code> header.
     * @return This builder
     */
    public CrankerRouterBuilder withViaName(String viaName) {
        if (!viaName.matches("^[0-9a-zA-Z!#$%&'*+-.^_`|~:]+$")) {
            throw new IllegalArgumentException("Via names must be hostnames or HTTP header tokens");
        }
        this.viaValue = viaName;
        return this;
    }

    /**
     * Sets the idle timeout. If no messages are received within this time then the connection is closed.
     * <p>The default is 5 minutes.</p>
     *
     * @param duration The allowed timeout duration, or 0 to disable timeouts.
     * @param unit     The unit of the duration.
     * @return This builder
     */
    public CrankerRouterBuilder withIdleTimeout(long duration, TimeUnit unit) {
        if (duration < 0) {
            throw new IllegalArgumentException("The duration must be 0 or greater");
        }
        Mutils.notNull("unit", unit);
        this.idleReadTimeoutMills = unit.toMillis(duration);
        return this;
    }

    /**
     * Sets the amount of time to wait before sending a ping message if no messages having been sent.
     * <p>The default is 10 seconds.</p>
     *
     * @param duration The allowed timeout duration, or 0 to disable timeouts.
     * @param unit     The unit of the duration.
     * @return This builder
     */
    public CrankerRouterBuilder withPingSentAfterNoWritesFor(int duration, TimeUnit unit) {
        if (duration < 0) {
            throw new IllegalArgumentException("The duration must be 0 or greater");
        }
        Mutils.notNull("unit", unit);
        this.pingAfterWriteMillis = unit.toMillis(duration);
        return this;
    }

    /**
     * <p>When a request is made for a route that has no connectors connected currently, the router will wait for a while
     * to see if a connector will connect that can service the request.</p>
     * <p>This is important because if there are a burst of requests for a route, there might be just a few milliseconds
     * gap where there is no connector available, so there is no point sending an error back to the client if it would
     * be find after a short period.</p>
     * <p>This setting controls how many retry attempts are made, and how much time there there are between checks.
     * By default, <code>maxAttempts</code> is 20 and <code>durationBetweenRetriesInMillis</code> is 250ms, meaning a
     * request will wait up to 5 seconds before returning a <code>503 Service Unavailable</code> to the client.</p>
     *
     * @param maxAttempts                    The maximum number of times to check if a route is available
     * @param durationBetweenRetriesInMillis The time in milliseconds to wait between each check
     * @return This builder
     */
    public CrankerRouterBuilder withConnectorAcquireAttempts(int maxAttempts, long durationBetweenRetriesInMillis) {
        this.maxAttempts = maxAttempts;
        this.durationBetweenRetriesInMillis = durationBetweenRetriesInMillis;
        return this;
    }

    /**
     * <p>Specifies whether or not to send the original <code>Host</code> header to the target server.</p>
     * <p>Reverse proxies are generally supposed to forward the original <code>Host</code> header to target
     * servers, however there are cases (particularly where you are proxying to HTTPS servers) that the
     * Host needs to match the Host of the SSL certificate (in which case you may see SNI-related errors).</p>
     *
     * @param sendHostToTarget If <code>true</code> (which is the default) the <code>Host</code> request
     *                         header will be sent to the target; if <code>false</code> then the host header
     *                         will be based on the target's URL.
     * @return This builder
     */
    public CrankerRouterBuilder proxyHostHeader(boolean sendHostToTarget) {
        if (sendHostToTarget) {
            doNotProxyHeaders.remove("host");
        } else {
            doNotProxyHeaders.add("host");
        }
        return this;
    }

    /**
     * Sets the IP validator for service registration requests. Defaults to {@link IPValidator#AllowAll}
     *
     * @param ipValidator The validator to use.
     * @return This builder
     */
    public CrankerRouterBuilder withRegistrationIpValidator(IPValidator ipValidator) {
        Mutils.notNull("ipValidator", ipValidator);
        this.ipValidator = ipValidator;
        return this;
    }

    /**
     * Registers proxy listeners to be called before, during and after requests are processed.
     *
     * @param proxyListeners The listeners to add.
     * @return This builder
     */
    public CrankerRouterBuilder withProxyListeners(List<ProxyListener> proxyListeners) {
        Mutils.notNull("proxyListeners", proxyListeners);
        this.completionListeners = proxyListeners;
        return this;
    }

    /**
     * @return A newly created CrankerRouter object
     */
    public CrankerRouter start() {
        Set<String> doNotProxy = new HashSet<>(CrankerMuHandler.REPRESSED);
        doNotProxyHeaders.forEach(h -> doNotProxy.add(h.toLowerCase()));
        WebSocketFarm webSocketFarm = new WebSocketFarm(maxAttempts, durationBetweenRetriesInMillis);
        webSocketFarm.start();
        List<ProxyListener> completionListeners = this.completionListeners.isEmpty() ? emptyList() : new ArrayList<>(this.completionListeners);
        DarkModeManager darkModeManager = new DarkModeManagerImpl(webSocketFarm);
        return new CrankerRouterImpl(ipValidator, discardClientForwardedHeaders,
            sendLegacyForwardedHeaders, viaValue, doNotProxy, webSocketFarm, idleReadTimeoutMills, pingAfterWriteMillis, completionListeners, darkModeManager);
    }
}
