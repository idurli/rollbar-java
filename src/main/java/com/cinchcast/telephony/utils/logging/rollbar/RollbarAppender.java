package com.cinchcast.telephony.utils.logging.rollbar;
//package com.muantech.rollbar.java;

import java.net.UnknownHostException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Map;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.MDC;
import org.apache.log4j.Priority;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.ThrowableInformation;
import org.json.JSONException;

import javax.net.ssl.*;

public class RollbarAppender extends AppenderSkeleton {

    private static final int DEFAULT_LOGS_LIMITS = 100;

    private static boolean init;
    private static LimitedQueue<String> LOG_BUFFER = new LimitedQueue<String>(DEFAULT_LOGS_LIMITS);

    private boolean enabled = true;
    private boolean onlyThrowable = true;
    private boolean logs = true;

    private Level threshold = Level.ERROR;

    private String apiKey;
    private String env;
    private String url = "https://api.rollbar.com/api/1/item/";
    private String proxyHost;
    private String proxyPort;
    private String context;
    private boolean avoidCertificate;

    @Override
    protected void append(final LoggingEvent event) {
        if (!enabled) return;

        try {

            // add to the LOG_BUFFER buffer
            LOG_BUFFER.add(this.layout.format(event).trim());

            if (!hasToNotify(event.getLevel())) return;

            boolean hasThrowable = thereIsThrowableIn(event);
            
            initNotifierIfNeeded();

            final Map<String, Object> context = getContext(event);

            if (hasThrowable) {
                RollbarNotifier.notify(event.getMessage().toString(), getThrowable(event), context);
            } else {
                RollbarNotifier.notify(event.getMessage().toString(), context);
            }

        } catch (Exception e) {
            LogLog.error("Error sending error notification! error=" + e.getClass().getName() + " with message=" + e.getMessage());
        }
    }

    private Map<String, Object> getContext(final LoggingEvent event) {

        @SuppressWarnings("unchecked")
        final Map<String, Object> context = MDC.getContext();
        context.put("LOG_BUFFER", new ArrayList<String>(LOG_BUFFER));

        return context;
    }

    public boolean hasToNotify(Priority priority) {
        return super.isAsSevereAsThreshold(priority);
    }

    private synchronized void initNotifierIfNeeded() throws JSONException, UnknownHostException {
        if (init) return;
        RollbarNotifier.init(url, apiKey, env);
        setDefaultContext();

        if(isAvoidCertificate()) {
            createTrustManager();
        }

        setProxy();
        init = true;
    }

    public void setApiKey(final String apiKey) {
        this.apiKey = apiKey;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public void setEnv(final String env) {
        this.env = env;
    }

    public boolean isOnlyThrowable() {
        return onlyThrowable;
    }

    public void setOnlyThrowable(boolean onlyThrowable) {
        this.onlyThrowable = onlyThrowable;
    }

    public void setUrl(final String url) {
        this.url = url;
    }

    public Level getNotifyLevel() {
        return threshold;
    }

    public void setLevel(String notifyLevel) {
        this.threshold = Level.toLevel(notifyLevel);
    }

    public boolean isLogs() {
        return logs;
    }

    public void setLogs(boolean logs) {
        this.logs = logs;
    }

    public void setLimit(int limit) {
        RollbarAppender.LOG_BUFFER = new LimitedQueue<String>(limit);
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public String getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(String proxyPort) {
        this.proxyPort = proxyPort;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public boolean isAvoidCertificate() {
        return avoidCertificate;
    }

    public void setAvoidCertificate(boolean avoidCertificate) {
        this.avoidCertificate = avoidCertificate;
    }

    private boolean thereIsThrowableIn(LoggingEvent loggingEvent) {
        return loggingEvent.getThrowableInformation() != null || loggingEvent.getMessage() instanceof Throwable;
    }

    private Throwable getThrowable(final LoggingEvent loggingEvent) {
        ThrowableInformation throwableInfo = loggingEvent.getThrowableInformation();
        if (throwableInfo != null) return throwableInfo.getThrowable();

        Object message = loggingEvent.getMessage();
        if (message instanceof Throwable) {
            return (Throwable) message;
        } else if (message instanceof String) {
            return new Exception((String) message);
        }

        return null;
    }

    @Override
    public void close() {}

    @Override
    public boolean requiresLayout() {
        return true;
    }

    private static class LimitedQueue<E> extends LinkedList<E> {

        private static final long serialVersionUID = 6557339882154255572L;

        private final int limit;

        public LimitedQueue(int limit) {
            this.limit = limit;
        }

        @Override
        public boolean add(E o) {
            super.add(o);
            while (size() > limit) {
                super.remove();
            }
            return true;
        }
    }

    private void setProxy() {
        if (this.proxyHost != null && this.proxyPort != null) {
            System.setProperty("http.proxyHost", this.getProxyHost());
            System.setProperty("http.proxyPort", this.getProxyPort());
            System.setProperty("https.proxyHost", this.getProxyHost());
            System.setProperty("https.proxyPort", this.getProxyPort());
        }
    }

    private void createTrustManager() {
        try {
            // Create a trust manager that does not validate certificate chains
            TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }

                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }
            }};

            // Install the all-trusting trust manager
            final SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

            // Create all-trusting host name verifier
            HostnameVerifier allHostsValid = new HostnameVerifier() {
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            };

            // Install the all-trusting host verifier
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
        } catch (Exception e) {
            LogLog.error("Exception creating trust manager.", e);
        }
    }

    private void setDefaultContext() {
        if(this.getContext() == null) {
            MDC.put("DefaultContext", "");
        } else {
            MDC.put(this.getContext(), "");
        }
    }
}
