package com.damso.match.api.admin.venue;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

/**
 * 서버가 떠있는 PC의 LAN IP 목록을 알려준다.
 * 손님 QR 코드 생성 시 정확한 접속 주소를 안내하기 위함.
 */
@RestController
@RequestMapping("/api/admin/host-info")
public class AdminHostInfoController {

    @GetMapping
    public Map<String, Object> hostInfo() {
        List<String> ips = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();
                if (iface.isLoopback() || !iface.isUp() || iface.isVirtual()) continue;
                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr.isLoopbackAddress()) continue;
                    if (addr instanceof Inet4Address) ips.add(addr.getHostAddress());
                }
            }
        } catch (Exception ignored) {}
        return Map.of("lanIps", ips);
    }
}