package br.com.bardozico.android;

import org.junit.Test;
import static org.junit.Assert.*;

public class ConnectionAddressTest {
    @Test public void acceptsResidentialAndCloudOrigins() {
        for (String url : new String[]{"https://192.168.0.8:8443", "https://192.168.0.8:18443", "https://bar.example", "https://bar.example:443/"})
            assertTrue(url, ConnectionAddress.valid(url));
    }
    @Test public void rejectsUnsafeOrigins() {
        for (String url : new String[]{"http://bar.example", "https://bar.example:0", "https://bar.example:65536", "https://u:p@bar.example", "https://bar.example/path", "https://bar.example?x=1", "https://bar.example#x", "not a url"})
            assertFalse(url, ConnectionAddress.valid(url));
        assertFalse(ConnectionAddress.valid(null));
    }
    @Test public void identifiesOnlyTheOldPlaceholder() {
        assertTrue(ConnectionAddress.placeholder("https://bar-api.example.azurecontainerapps.io"));
        assertFalse(ConnectionAddress.placeholder("https://my-real-app.azurecontainerapps.io"));
        assertFalse(ConnectionAddress.placeholder("https://bar-api.example.azurecontainerapps.io.attacker.example"));
        assertFalse(ConnectionAddress.placeholder(null));
    }
    @Test public void comparesTheActualOriginAndRetainsCloudAddresses() {
        assertTrue(ConnectionAddress.sameOrigin("https://bar.example", "https://BAR.example:443/mesas"));
        assertFalse(ConnectionAddress.sameOrigin("https://bar.example", "http://bar.example/mesas"));
        assertFalse(ConnectionAddress.sameOrigin("https://bar.example", "https://bar.example:8443"));
        assertFalse(ConnectionAddress.sameOrigin("https://bar.example", "https://u:p@bar.example"));
        assertFalse(ConnectionAddress.sameOrigin("https://bar.example", "https://bar.example.attacker.example"));
    }
    @Test public void opensSettingsOnlyFromAnIntentionalTapInTheConfiguredServer() {
        String address="https://bar.example";
        assertTrue(ConnectionAddress.settingsNavigation(address,address+"/mesas","bardozico://settings",true,true));
        assertFalse(ConnectionAddress.settingsNavigation(address,address,"bardozico://settings",false,true));
        assertFalse(ConnectionAddress.settingsNavigation(address,address,"bardozico://settings",true,false));
        assertFalse(ConnectionAddress.settingsNavigation(address,"https://other.example","bardozico://settings",true,true));
        assertFalse(ConnectionAddress.settingsNavigation(address,null,"bardozico://settings",true,true));
        assertFalse(ConnectionAddress.settingsNavigation(address,address,"bardozico://settings?address=https://other.example",true,true));
        assertFalse(ConnectionAddress.settingsNavigation(address,address,"bardozico://settings/other",true,true));
    }
}
