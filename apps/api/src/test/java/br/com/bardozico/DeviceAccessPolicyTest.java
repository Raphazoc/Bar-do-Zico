package br.com.bardozico;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;

class DeviceAccessPolicyTest {
    @Test void localDeviceProfilesRefuseForwardedIdentity() {
        assertThrows(IllegalStateException.class,()->new DeviceAccess(true,true,"framework"));
        assertThrows(IllegalStateException.class,()->new DeviceAccess(true,true,"native"));
        assertTrue(new DeviceAccess(true,true,"none").enabled());
    }
    @Test void authenticatedCloudDoesNotEnableLocalPrivileges() {
        assertFalse(new DeviceAccess(false,true,"framework").enabled());
    }
    @Test void headersCannotTurnAnExternalConnectionIntoTheComputer() {
        var policy=new DeviceAccess(true,true,"NONE");
        var request=new MockHttpServletRequest();request.setRemoteAddr("192.168.0.40");
        request.addHeader("X-Forwarded-For","127.0.0.1");request.addHeader("User-Agent","Desktop");
        assertTrue(policy.attendance(request));
        request.setRemoteAddr("127.0.0.1");assertFalse(policy.attendance(request));
        request.addHeader("User-Agent","BarDoZicoAndroid/0.2.2");
        // Cada requisição Android nativa chega com um único User-Agent.
        var android=new MockHttpServletRequest();android.setRemoteAddr("127.0.0.1");
        android.addHeader("User-Agent","BarDoZicoAndroid/0.2.2");assertTrue(policy.attendance(android));
    }
}
