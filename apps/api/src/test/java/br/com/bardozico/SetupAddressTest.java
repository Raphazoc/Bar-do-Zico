package br.com.bardozico;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class SetupAddressTest {
    @ParameterizedTest
    @ValueSource(strings={"https://192.168.0.8:8443","https://192.168.0.8:18443","https://localhost:8443",
        "https://bar-api.example.azurecontainerapps.io","https://bar.example:443/","https://[::1]:18443"})
    void acceptsLocalAndCloudHttps(String address) { assertTrue(SetupController.validAddress(address)); }

    @ParameterizedTest
    @ValueSource(strings={"http://192.168.0.8:8443","https://bar.example:80","https://bar.example:65536",
        "https://name:secret@bar.example","https://bar.example/path","https://bar.example?x=1",
        "https://bar.example#secret","https:///","not a url","https://bar.example:0"})
    void rejectsMalformedOrUnsupportedAddresses(String address) { assertFalse(SetupController.validAddress(address)); }
}
