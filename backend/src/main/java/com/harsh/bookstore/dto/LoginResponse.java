package com.harsh.bookstore.dto;


/**
 * LoginResponse — the JSON body returned by a successful POST /api/auth/login.
 *
 * SHAPE:
 *   {
 *     "token": "eyJhbGciOiJIUzI1NiJ9...",
 *     "user": {
 *       "id": 1,
 *       "firstName": "Harsh",
 *       "lastName": "Sharma",
 *       "email": "harsh@example.com"
 *     }
 *   }
 *
 * WHY BOTH FIELDS TOGETHER:
 *   The client needs the token to make authenticated requests AND the user
 *   profile to display a welcome message / personalise the UI. Returning
 *   both in one response avoids a second round-trip to GET /api/users/me
 *   (an endpoint we haven't built yet). One login call = everything needed.
 *
 * HOW THE CLIENT USES THE TOKEN:
 *   Store it in memory (or localStorage for a web app). Attach it to every
 *   subsequent request in the Authorization header:
 *       Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
 *   The JwtAuthFilter reads this header on every request and sets the
 *   security context if the token is valid.
 */
public class LoginResponse {

    /**
     * The signed JWT. Valid for 24 hours from issue time (spec FR-09).
     * After expiry the client must log in again — no refresh token exists
     * (spec §8, out of scope).
     */
    private String token;

    /**
     * The authenticated user's profile. Same shape as the registration
     * response — no password, no internal fields.
     */
    private UserDto user;


    // ==================================================================
    // CONSTRUCTOR
    // ==================================================================

    public LoginResponse() {
    }


    // ==================================================================
    // GETTERS AND SETTERS
    // ==================================================================

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public UserDto getUser() { return user; }
    public void setUser(UserDto user) { this.user = user; }
}
