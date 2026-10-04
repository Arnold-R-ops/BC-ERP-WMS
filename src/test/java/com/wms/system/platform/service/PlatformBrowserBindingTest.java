package com.wms.system.platform.service;

import com.wms.system.security.JwtProperties;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;

class PlatformBrowserBindingTest {
    PlatformBrowserBinding service() {
        var keys=new JwtProperties(); keys.setPlatformSecret("AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=");
        return new PlatformBrowserBinding(keys);
    }
    MockHttpServletRequest request(boolean secure) {
        var request=new MockHttpServletRequest(); request.setSecure(secure);
        request.addHeader("X-Platform-Browser-Init","1"); return request;
    }
    Cookie cookie(PlatformBrowserBinding service, MockHttpServletRequest request) {
        var response=new MockHttpServletResponse(); service.initialize(request,response);
        return response.getCookies()[0];
    }
    @Test void secureCookieIsOpaqueSignedHostOnlyHttpOnlyAndDoesNotRotateOnRepeat() {
        var service=service(); var request=request(true); var first=cookie(service,request);
        assertEquals("__Secure-bcwms-platform-browser",first.getName());
        assertTrue(first.isHttpOnly()); assertTrue(first.getSecure()); assertNull(first.getDomain());
        assertEquals("/api/platform",first.getPath()); assertEquals("Strict",first.getAttribute("SameSite"));
        request.setCookies(first); String hash=service.requireHash(request);
        assertEquals(64,hash.length()); assertNotEquals(first.getValue(),hash);
        assertEquals(first.getValue(),cookie(service,request).getValue());
        assertEquals(hash,service.requireHash(request));
    }
    @Test void browserGroupsDifferAndTamperingOrMissingCookieFailsClosed() {
        var service=service(); var a=request(true); var b=request(true);
        a.setCookies(cookie(service,a)); b.setCookies(cookie(service,b));
        assertNotEquals(service.requireHash(a),service.requireHash(b));
        var original=a.getCookies()[0];
        a.setCookies(new Cookie(original.getName(),(original.getValue().startsWith("X")?"Y":"X")+original.getValue().substring(1)));
        assertThrows(PlatformSessionException.class,()->service.requireHash(a));
        assertThrows(PlatformSessionException.class,()->service.requireHash(request(true)));
    }
    @Test void ambiguousCookiesAndCrossSiteInitializationAreRejected() {
        var service=service(); var request=request(true); var cookie=cookie(service,request);
        request.setCookies(cookie,cookie);
        assertThrows(PlatformSessionException.class,()->service.requireHash(request));
        request.setCookies(); request.addHeader("Sec-Fetch-Site","cross-site");
        assertThrows(PlatformSessionException.class,()->service.initialize(request,new MockHttpServletResponse()));
        var sameSite=request(true); sameSite.addHeader("Sec-Fetch-Site","same-site");
        assertThrows(PlatformSessionException.class,()->service.initialize(sameSite,new MockHttpServletResponse()));
    }
    @Test void httpExceptionRequiresLiteralLoopbackPeerAndHostNotForwardedHeaders() {
        var service=service(); var local=request(false); local.setServerName("127.0.0.1"); local.setRemoteAddr("127.0.0.1");
        Cookie first=cookie(service,local); assertFalse(first.getSecure());
        assertEquals("bcwms-platform-browser-loopback",first.getName());
        local.setRemoteAddr("192.0.2.2"); local.addHeader("X-Forwarded-For","127.0.0.1");
        assertTrue(cookie(service,local).getSecure());
    }
}
