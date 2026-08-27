package com.harsh.bookstore.dto;


/**
 * UserDto — the outward-facing shape of a User as returned by the API.
 *
 * WHAT IS DELIBERATELY ABSENT:
 *   - passwordHash — a BCrypt hash must never appear in any response.
 *     By omitting the field entirely, it is structurally impossible to
 *     accidentally include it: Jackson only serialises fields that exist
 *     on the class, so there is nothing to accidentally include.
 *   - createdAt — internal audit field; not relevant to the client.
 *
 * WHERE THIS IS USED:
 *   1. POST /api/auth/register response body  (201 Created)
 *   2. Nested inside LoginResponse as the `user` field  (200 OK)
 *
 * This DTO is the only user-related type that ever crosses the HTTP boundary
 * outward. The User entity and RegisterRequest/LoginRequest stay internal.
 */
public class UserDto {

    private Long id;
    private String firstName;
    private String lastName;
    private String email;


    // ==================================================================
    // CONSTRUCTOR
    // ==================================================================

    public UserDto() {
    }


    // ==================================================================
    // GETTERS AND SETTERS
    // ==================================================================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
