package de.savas.businessinsightapi.application.insight;

import de.savas.businessinsightapi.common.error.BadRequestException;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * Validates what a datasource points at: the host and database name (checked when saved, so nothing can be injected
 * into the JDBC URL), the network address the host resolves to (checked before every connection, against SSRF), and
 * which environment variables a {@code secretRef} may read.
 */
@Component
public class DataSourceConnectionPolicy {

    /** DNS names and IPv4 literals: dot-separated labels of letters, digits and inner hyphens. */
    private static final Pattern HOST = Pattern.compile(
            "^(?=.{1,253}$)[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?(\\.[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*$");
    /** No URL or connection-string syntax (/ ? & ; = \ spaces quotes braces). */
    private static final Pattern DATABASE_NAME = Pattern.compile("^[A-Za-z0-9_][A-Za-z0-9_.$-]{0,127}$");
    private static final Pattern ENV_NAME = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    private static final Pattern IPV4_LITERAL = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

    private final DataSourceConnectionProperties properties;

    public DataSourceConnectionPolicy(DataSourceConnectionProperties properties) {
        this.properties = properties;
    }

    public String envSecretPrefix() {
        return properties.envSecretPrefix();
    }

    /** Checks the host's syntax; an IP literal is also checked against the address rules right away. */
    public String validateHost(String host) {
        String value = host == null ? "" : host.trim();
        if (!HOST.matcher(value).matches()) {
            throw new BadRequestException("host must be a DNS name or an IPv4 address");
        }
        if (IPV4_LITERAL.matcher(value).matches()) {
            try {
                assertAllowedAddress(InetAddress.getByName(value));
            } catch (UnknownHostException e) {
                throw new BadRequestException("host must be a DNS name or an IPv4 address");
            }
        }
        return value;
    }

    public String validateDatabaseName(String databaseName) {
        String value = databaseName == null ? "" : databaseName.trim();
        if (!DATABASE_NAME.matcher(value).matches()) {
            throw new BadRequestException("databaseName may contain only letters, digits and _ . $ - (max 128)");
        }
        return value;
    }

    /** Validates an {@code env:VAR} reference and returns it normalized. */
    public String validateEnvSecretRef(String secretRef) {
        String ref = secretRef == null ? "" : secretRef.trim();
        String name = ref.startsWith("env:") ? ref.substring(4) : "";
        if (!ENV_NAME.matcher(name).matches() || !name.startsWith(envSecretPrefix()) || name.length() == envSecretPrefix().length()) {
            throw new BadRequestException("secretRef must look like env:" + envSecretPrefix()
                    + "NAME (only variables starting with " + envSecretPrefix() + " can be used); send a password to store it encrypted");
        }
        return ref;
    }

    /** Resolves the host and refuses to connect when any of its addresses is not allowed. */
    public void assertAllowedTarget(String host) {
        validateHost(host);
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host.trim());
        } catch (UnknownHostException e) {
            throw new BadRequestException("Unknown host: " + host.trim());
        }
        for (InetAddress address : addresses) {
            assertAllowedAddress(address);
        }
    }

    void assertAllowedAddress(InetAddress address) {
        // 0.0.0.0 reaches the local machine; link-local includes cloud metadata endpoints (169.254.169.254).
        if (address.isAnyLocalAddress() || address.isLinkLocalAddress() || address.isMulticastAddress()) {
            throw notAllowed();
        }
        if (address.isLoopbackAddress() && !properties.allowLoopback()) {
            throw notAllowed();
        }
        if (isPrivate(address) && !properties.allowPrivateNetworks()) {
            throw notAllowed();
        }
    }

    private static boolean isPrivate(InetAddress address) {
        if (address.isSiteLocalAddress()) {
            return true; // 10/8, 172.16/12, 192.168/16 (and deprecated fec0::/10)
        }
        byte[] b = address.getAddress();
        if (address instanceof Inet4Address) {
            return (b[0] & 0xff) == 100 && (b[1] & 0xc0) == 64; // 100.64.0.0/10 (carrier-grade NAT)
        }
        return address instanceof Inet6Address && (b[0] & 0xfe) == 0xfc; // fc00::/7 (unique local)
    }

    private static BadRequestException notAllowed() {
        return new BadRequestException("This host's address is not allowed for datasource connections");
    }
}
