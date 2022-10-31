/*********************************************************************************
 *                                                                               *
 * The MIT License (MIT)                                                         *
 *                                                                               *
 * Copyright (c) 2015-2022 aoju.org and other contributors.                      *
 *                                                                               *
 * Permission is hereby granted, free of charge, to any person obtaining a copy  *
 * of this software and associated documentation files (the "Software"), to deal *
 * in the Software without restriction, including without limitation the rights  *
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell     *
 * copies of the Software, and to permit persons to whom the Software is         *
 * furnished to do so, subject to the following conditions:                      *
 *                                                                               *
 * The above copyright notice and this permission notice shall be included in    *
 * all copies or substantial portions of the Software.                           *
 *                                                                               *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR    *
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,      *
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE   *
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER        *
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, *
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN     *
 * THE SOFTWARE.                                                                 *
 *                                                                               *
 ********************************************************************************/
package org.aoju.bus.http.accord.platform;

import org.aoju.bus.core.Version;
import org.aoju.bus.core.lang.Algorithm;
import org.aoju.bus.core.lang.Charset;
import org.aoju.bus.core.lang.Normal;
import org.aoju.bus.http.Builder;
import org.aoju.bus.http.Protocol;
import org.aoju.bus.http.secure.BasicTrustRootIndex;
import org.aoju.bus.http.secure.CertificateChainCleaner;
import org.aoju.bus.http.secure.TrustRootIndex;
import org.aoju.bus.logger.Logger;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.cert.Certificate;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Android 5+
 *
 * @author Kimi Liu
 * @since Java 17+
 */
class AndroidPlatform extends Platform {

    private final Class<?> sslParametersClass;
    private final Class<?> sslSocketClass;
    private final Method setUseSessionTickets;
    private final Method setHostname;
    private final Method getAlpnSelectedProtocol;
    private final Method setAlpnProtocols;

    private final CloseGuard closeGuard = CloseGuard.get();

    AndroidPlatform(Class<?> sslParametersClass, Class<?> sslSocketClass, Method setUseSessionTickets,
                    Method setHostname, Method getAlpnSelectedProtocol, Method setAlpnProtocols) {
        this.sslParametersClass = sslParametersClass;
        this.sslSocketClass = sslSocketClass;
        this.setUseSessionTickets = setUseSessionTickets;
        this.setHostname = setHostname;
        this.getAlpnSelectedProtocol = getAlpnSelectedProtocol;
        this.setAlpnProtocols = setAlpnProtocols;
    }

    public static Platform buildIfSupported() {
        if (!Platform.isAndroid()) {
            return null;
        }

        // Attempt to find Android 5+ APIs.
        Class<?> sslParametersClass;
        Class<?> sslSocketClass;

        try {
            sslParametersClass = Class.forName("com.android.org.conscrypt.SSLParametersImpl");
            sslSocketClass = Class.forName("com.android.org.conscrypt.OpenSSLSocketImpl");
        } catch (ClassNotFoundException ignored) {
            return null; // Not an Android runtime.
        }

        try {
            Method setUseSessionTickets = sslSocketClass.getDeclaredMethod(
                    "setUseSessionTickets", boolean.class);
            Method setHostname = sslSocketClass.getMethod("setHostname", String.class);
            Method getAlpnSelectedProtocol = sslSocketClass.getMethod("getAlpnSelectedProtocol");
            Method setAlpnProtocols = sslSocketClass.getMethod("setAlpnProtocols", byte[].class);
            return new AndroidPlatform(sslParametersClass, sslSocketClass, setUseSessionTickets,
                    setHostname, getAlpnSelectedProtocol, setAlpnProtocols);
        } catch (NoSuchMethodException ignored) {
        }
        throw new IllegalStateException(
                "Expected Android API level 21+ but was " + Version.all());
    }


    @Override
    public void connectSocket(Socket socket, InetSocketAddress address,
                              int connectTimeout) throws IOException {
        try {
            socket.connect(address, connectTimeout);
        } catch (AssertionError e) {
            if (Builder.isAndroidGetsocknameError(e)) throw new IOException(e);
            throw e;
        } catch (ClassCastException e) {
            // On android 8.0, socket.connect throws a ClassCastException due to a bug
            throw e;
        }
    }

    @Override
    protected X509TrustManager trustManager(SSLSocketFactory sslSocketFactory) {
        Object context = readFieldOrNull(sslSocketFactory, sslParametersClass, "sslParameters");
        if (context == null) {
            // If that didn't work, try the Google Play Services SSL provider before giving up. This
            // must be loaded by the SSLSocketFactory's class loader.
            try {
                Class<?> gmsSslParametersClass = Class.forName(
                        "com.google.android.gms.org.conscrypt.SSLParametersImpl", false,
                        sslSocketFactory.getClass().getClassLoader());
                context = readFieldOrNull(sslSocketFactory, gmsSslParametersClass, "sslParameters");
            } catch (ClassNotFoundException e) {
                return super.trustManager(sslSocketFactory);
            }
        }

        X509TrustManager x509TrustManager = readFieldOrNull(
                context, X509TrustManager.class, "x509TrustManager");
        if (x509TrustManager != null) return x509TrustManager;

        return readFieldOrNull(context, X509TrustManager.class, "trustManager");
    }

    @Override
    public void configureTlsExtensions(
            SSLSocket sslSocket, String hostname, List<Protocol> protocols) {
        if (!sslSocketClass.isInstance(sslSocket)) {
            return; // No TLS extensions if the socket class is custom.
        }
        try {
            // 启用SNI和会话票据
            if (hostname != null) {
                setUseSessionTickets.invoke(sslSocket, true);
                // This is SSLParameters.setServerNames() in API 24+.
                setHostname.invoke(sslSocket, hostname);
            }

            // 启用 ALPN
            setAlpnProtocols.invoke(sslSocket, concatLengthPrefixed(protocols));
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public String getSelectedProtocol(SSLSocket socket) {
        if (!sslSocketClass.isInstance(socket)) {
            return null;
        }
        try {
            byte[] alpnResult = (byte[]) getAlpnSelectedProtocol.invoke(socket);
            return alpnResult != null ? new String(alpnResult, Charset.UTF_8) : null;
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public Object getStackTraceForCloseable(String closer) {
        return closeGuard.createAndOpen(closer);
    }

    @Override
    public void logCloseableLeak(String message, Object stackTrace) {
        boolean reported = closeGuard.warnIfOpen(stackTrace);
        if (!reported) {
            // 无法通过近距离观察报告。作为最后的努力，把它发送到记录器.
            Logger.warn(message, null);
        }
    }

    @Override
    public boolean isCleartextTrafficPermitted(String hostname) {
        try {
            Class<?> networkPolicyClass = Class.forName("android.security.NetworkSecurityPolicy");
            Method getInstanceMethod = networkPolicyClass.getMethod("getInstance");
            Object networkSecurityPolicy = getInstanceMethod.invoke(null);
            return api24IsCleartextTrafficPermitted(hostname, networkPolicyClass, networkSecurityPolicy);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            return super.isCleartextTrafficPermitted(hostname);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new AssertionError("unable to determine cleartext support", e);
        }
    }

    private boolean api24IsCleartextTrafficPermitted(String hostname, Class<?> networkPolicyClass,
                                                     Object networkSecurityPolicy) throws InvocationTargetException, IllegalAccessException {
        try {
            Method isCleartextTrafficPermittedMethod = networkPolicyClass
                    .getMethod("isCleartextTrafficPermitted", String.class);
            return (boolean) isCleartextTrafficPermittedMethod.invoke(networkSecurityPolicy, hostname);
        } catch (NoSuchMethodException e) {
            return api23IsCleartextTrafficPermitted(hostname, networkPolicyClass, networkSecurityPolicy);
        }
    }

    private boolean api23IsCleartextTrafficPermitted(String hostname, Class<?> networkPolicyClass,
                                                     Object networkSecurityPolicy) throws InvocationTargetException, IllegalAccessException {
        try {
            Method isCleartextTrafficPermittedMethod = networkPolicyClass
                    .getMethod("isCleartextTrafficPermitted");
            return (boolean) isCleartextTrafficPermittedMethod.invoke(networkSecurityPolicy);
        } catch (NoSuchMethodException e) {
            return super.isCleartextTrafficPermitted(hostname);
        }
    }

    public CertificateChainCleaner buildCertificateChainCleaner(X509TrustManager trustManager) {
        try {
            Class<?> extensionsClass = Class.forName("android.net.http.X509TrustManagerExtensions");
            Constructor<?> constructor = extensionsClass.getConstructor(X509TrustManager.class);
            Object extensions = constructor.newInstance(trustManager);
            Method checkServerTrusted = extensionsClass.getMethod(
                    "checkServerTrusted", X509Certificate[].class, String.class, String.class);
            return new AndroidCertificateChainCleaner(extensions, checkServerTrusted);
        } catch (Exception e) {
            return super.buildCertificateChainCleaner(trustManager);
        }
    }

    @Override
    public TrustRootIndex buildTrustRootIndex(X509TrustManager trustManager) {
        try {
            Method method = trustManager.getClass().getDeclaredMethod(
                    "findTrustAnchorByIssuerAndSignature", X509Certificate.class);
            method.setAccessible(true);
            return new CustomTrustRootIndex(trustManager, method);
        } catch (NoSuchMethodException e) {
            return super.buildTrustRootIndex(trustManager);
        }
    }

    /**
     * X509TrustManagerExtensions是在API 17 (Android 4.2, 2012年底发布)中添加到Android的。
     * 这是在Android上获得干净链的最好方法，因为它使用与TLS握手相同的代码
     */
    static class AndroidCertificateChainCleaner extends CertificateChainCleaner {
        private final Object x509TrustManagerExtensions;
        private final Method checkServerTrusted;

        AndroidCertificateChainCleaner(Object x509TrustManagerExtensions, Method checkServerTrusted) {
            this.x509TrustManagerExtensions = x509TrustManagerExtensions;
            this.checkServerTrusted = checkServerTrusted;
        }

        @Override
        public List<Certificate> clean(List<Certificate> chain, String hostname)
                throws SSLPeerUnverifiedException {
            try {
                X509Certificate[] certificates = chain.toArray(new X509Certificate[chain.size()]);
                return (List<Certificate>) checkServerTrusted.invoke(
                        x509TrustManagerExtensions, certificates, Algorithm.RSA, hostname);
            } catch (InvocationTargetException e) {
                SSLPeerUnverifiedException exception = new SSLPeerUnverifiedException(e.getMessage());
                exception.initCause(e);
                throw exception;
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            }
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof AndroidCertificateChainCleaner; // All instances are equivalent.
        }

        @Override
        public int hashCode() {
            return 0;
        }
    }

    /**
     * 提供对内部dalvik.system.CloseGuard的访问。CloseGuard类。
     * Android将其与android.os.StrictMode结合使用。
     * 严格模式报告泄漏的java.io.Closeable
     */
    static class CloseGuard {
        private final Method getMethod;
        private final Method openMethod;
        private final Method warnIfOpenMethod;

        CloseGuard(Method getMethod, Method openMethod, Method warnIfOpenMethod) {
            this.getMethod = getMethod;
            this.openMethod = openMethod;
            this.warnIfOpenMethod = warnIfOpenMethod;
        }

        static CloseGuard get() {
            Method getMethod;
            Method openMethod;
            Method warnIfOpenMethod;

            try {
                Class<?> closeGuardClass = Class.forName("dalvik.system.CloseGuard");
                getMethod = closeGuardClass.getMethod(Normal.GET);
                openMethod = closeGuardClass.getMethod("open", String.class);
                warnIfOpenMethod = closeGuardClass.getMethod("warnIfOpen");
            } catch (Exception ignored) {
                getMethod = null;
                openMethod = null;
                warnIfOpenMethod = null;
            }
            return new CloseGuard(getMethod, openMethod, warnIfOpenMethod);
        }

        Object createAndOpen(String closer) {
            if (null != getMethod) {
                try {
                    Object closeGuardInstance = getMethod.invoke(null);
                    openMethod.invoke(closeGuardInstance, closer);
                    return closeGuardInstance;
                } catch (Exception ignored) {
                }
            }
            return null;
        }

        boolean warnIfOpen(Object closeGuardInstance) {
            boolean reported = false;
            if (null != closeGuardInstance) {
                try {
                    warnIfOpenMethod.invoke(closeGuardInstance);
                    reported = true;
                } catch (Exception ignored) {
                }
            }
            return reported;
        }
    }

    /**
     * 利用Android实现细节的受信任根证书的索引。
     * 这个类的初始化速度可能比{@link BasicTrustRootIndex}快得多，
     * 因为它不需要加载和索引受信任的CA证书
     * 这个类使用API 14中添加到Android的API (Android 4.0, 2011年10月发布)。
     * 这个类不应该在Android API 17或更好的版本中使用，因为这些版本由
     * {@link AndroidPlatform.AndroidCertificateChainCleaner}提供更好的服务。
     */
    static class CustomTrustRootIndex implements TrustRootIndex {
        private final X509TrustManager trustManager;
        private final Method findByIssuerAndSignatureMethod;

        CustomTrustRootIndex(X509TrustManager trustManager, Method findByIssuerAndSignatureMethod) {
            this.findByIssuerAndSignatureMethod = findByIssuerAndSignatureMethod;
            this.trustManager = trustManager;
        }

        @Override
        public X509Certificate findByIssuerAndSignature(X509Certificate cert) {
            try {
                TrustAnchor trustAnchor = (TrustAnchor) findByIssuerAndSignatureMethod.invoke(
                        trustManager, cert);
                return trustAnchor != null
                        ? trustAnchor.getTrustedCert()
                        : null;
            } catch (IllegalAccessException e) {
                throw new AssertionError("unable to get issues and signature", e);
            } catch (InvocationTargetException e) {
                return null;
            }
        }

        @Override
        public boolean equals(Object object) {
            if (object == this) {
                return true;
            }
            if (!(object instanceof CustomTrustRootIndex)) {
                return false;
            }
            CustomTrustRootIndex that = (CustomTrustRootIndex) object;
            return trustManager.equals(that.trustManager)
                    && findByIssuerAndSignatureMethod.equals(that.findByIssuerAndSignatureMethod);
        }

        @Override
        public int hashCode() {
            return trustManager.hashCode() + 31 * findByIssuerAndSignatureMethod.hashCode();
        }
    }

}
