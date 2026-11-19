package de.savas.businessinsightapi.application.insight;

import de.savas.businessinsightapi.common.error.BadRequestException;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataSourceConnectionPolicyTest {

    private final DataSourceConnectionPolicy defaults = new DataSourceConnectionPolicy(DataSourceConnectionProperties.defaults());

    private static void allowed(DataSourceConnectionPolicy policy, String ip) {
        assertThatCode(() -> policy.assertAllowedAddress(InetAddress.getByName(ip))).as(ip).doesNotThrowAnyException();
    }

    private static void refused(DataSourceConnectionPolicy policy, String ip) {
        assertThatThrownBy(() -> policy.assertAllowedAddress(InetAddress.getByName(ip))).as(ip)
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void defaultsAllowPublicAndPrivateButNotLocalOrMetadataAddresses() {
        allowed(defaults, "93.184.216.34");
        allowed(defaults, "10.1.2.3");
        allowed(defaults, "172.20.0.5");
        allowed(defaults, "192.168.1.10");
        allowed(defaults, "2001:db8::1");

        refused(defaults, "127.0.0.1");
        refused(defaults, "127.5.5.5");
        refused(defaults, "::1");
        refused(defaults, "0.0.0.0");
        refused(defaults, "::");
        refused(defaults, "169.254.169.254");
        refused(defaults, "fe80::1");
        refused(defaults, "224.0.0.1");
        refused(defaults, "::ffff:127.0.0.1");
        refused(defaults, "::ffff:169.254.169.254");
    }

    @Test
    void privateNetworksAndLoopbackFollowTheSettings() {
        var publicOnly = new DataSourceConnectionPolicy(new DataSourceConnectionProperties(null, false, false));
        refused(publicOnly, "10.1.2.3");
        refused(publicOnly, "172.31.255.1");
        refused(publicOnly, "192.168.0.1");
        refused(publicOnly, "100.64.0.1");
        refused(publicOnly, "fd00::1");
        allowed(publicOnly, "100.128.0.1");
        allowed(publicOnly, "8.8.8.8");

        var dev = new DataSourceConnectionPolicy(new DataSourceConnectionProperties(null, true, true));
        allowed(dev, "127.0.0.1");
        allowed(dev, "::1");
        refused(dev, "0.0.0.0");
        refused(dev, "169.254.169.254");
    }

    @Test
    void resolvedNamesAreChecked() {
        assertThatThrownBy(() -> defaults.assertAllowedTarget("localhost")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void envPrefixIsConfigurableButNeverEmpty() {
        var custom = new DataSourceConnectionPolicy(new DataSourceConnectionProperties("BI_DB_", null, null));
        assertThatCode(() -> custom.validateEnvSecretRef("env:BI_DB_ERP")).doesNotThrowAnyException();
        assertThatThrownBy(() -> custom.validateEnvSecretRef("env:DS_SECRET_ERP")).isInstanceOf(BadRequestException.class);

        var blank = new DataSourceConnectionPolicy(new DataSourceConnectionProperties("  ", null, null));
        assertThatThrownBy(() -> blank.validateEnvSecretRef("env:JWT_SECRET")).isInstanceOf(BadRequestException.class);
        assertThatCode(() -> blank.validateEnvSecretRef(" env:DS_SECRET_ERP ")).doesNotThrowAnyException();
    }
}
