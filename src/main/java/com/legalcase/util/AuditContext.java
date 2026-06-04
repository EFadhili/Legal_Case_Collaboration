package com.legalcase.util;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AuditContext {

    private static final ThreadLocal<Long> currentUserId = new ThreadLocal<>();
    private static final ThreadLocal<String> currentUserIdentifier = new ThreadLocal<>();
    private static final ThreadLocal<String> currentUserName = new ThreadLocal<>();
    private static final ThreadLocal<String> currentIpAddress = new ThreadLocal<>();
    private static final ThreadLocal<String> currentUserAgent = new ThreadLocal<>();

    public static void setCurrentUser(Long userId, String userIdentifier, String userName) {
        currentUserId.set(userId);
        currentUserIdentifier.set(userIdentifier);
        currentUserName.set(userName);
    }

    public static void setRequestInfo(String ipAddress, String userAgent) {
        currentIpAddress.set(ipAddress);
        currentUserAgent.set(userAgent);
    }

    public static Long getCurrentUserId() {
        return currentUserId.get();
    }

    public static String getCurrentUserIdentifier() {
        return currentUserIdentifier.get();
    }

    public static String getCurrentUserName() {
        return currentUserName.get();
    }

    public static String getCurrentIpAddress() {
        return currentIpAddress.get();
    }

    public static String getCurrentUserAgent() {
        return currentUserAgent.get();
    }

    public static void clear() {
        currentUserId.remove();
        currentUserIdentifier.remove();
        currentUserName.remove();
        currentIpAddress.remove();
        currentUserAgent.remove();
    }
}