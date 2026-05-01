package com.legalcase.enums;

/**
 * Defines the user roles in the Legal Case Management system.
 * Each role has different permissions.
 */
public enum Role {

    /**
     * ADMIN: Full system access. Can manage users, all cases, system settings.
     * Example: System administrators, IT managers.
     */
    ADMIN,

    /**
     * LAWYER: Can create and manage cases, upload documents, use AI assistant.
     * Example: Attorneys, legal associates.
     */
    LAWYER,

    /**
     * STAFF: Limited access. Can view assigned cases, add comments, complete tasks.
     * Example: Paralegals, legal assistants, support staff.
     */
    STAFF
}