package com.legalcase.enums;

public enum Role {

    /**
     * ADMIN: Full system access. Can manage users, all cases, system settings.
     * Can promote STAFF users to ADMIN.
     * Can view all cases, audit logs, and manage all users.
     */
    ADMIN,

    /**
     * STAFF: Default role for all registered users.
     * Can create cases (automatically becomes case lawyer).
     * Can be added to cases as STAFF or promoted to case lawyer.
     * Cannot access admin functions.
     */
    STAFF
}