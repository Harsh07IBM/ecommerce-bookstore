package com.harsh.bookstore.controller;

import com.harsh.bookstore.dto.AddressRequest;
import com.harsh.bookstore.dto.AddressResponse;
import com.harsh.bookstore.entity.User;
import com.harsh.bookstore.service.AddressService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


/**
 * AddressController — HTTP entry point for the 4 delivery address endpoints (FEAT-07).
 *
 * AUTHENTICATION:
 *   All endpoints require a valid JWT. The existing anyRequest().authenticated()
 *   rule in SecurityConfig means Spring Security returns 401 before this
 *   controller is ever reached when no JWT is present — no null check on
 *   Authentication is required. The JwtAuthFilter places the User entity as the
 *   principal, so the cast to User is always safe.
 *
 * USERID EXTRACTION:
 *   Every method extracts userId via ((User) authentication.getPrincipal()).getId().
 *   This is the same pattern used by CheckoutController and is consistent with
 *   how BasketController works for authenticated requests.
 */
@RestController
@RequestMapping("/api/addresses")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }


    /**
     * GET /api/addresses
     * Returns all saved addresses for the authenticated user.
     * Returns [] when no addresses exist (AC-02).
     */
    @GetMapping
    public List<AddressResponse> listAddresses(Authentication authentication) {
        Long userId = ((User) authentication.getPrincipal()).getId();
        return addressService.listAddresses(userId);
    }


    /**
     * POST /api/addresses → 201 Created
     * Saves a new delivery address. If isDefault=true, demotes any prior default.
     * @Valid triggers Bean Validation before the service is called → 400 on failure.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AddressResponse saveAddress(@Valid @RequestBody AddressRequest req,
                                       Authentication authentication) {
        Long userId = ((User) authentication.getPrincipal()).getId();
        return addressService.saveAddress(userId, req);
    }


    /**
     * PUT /api/addresses/{id} → 200 OK
     * Full update of all fields. Ownership enforced in service (403/404).
     */
    @PutMapping("/{id}")
    public AddressResponse updateAddress(@PathVariable Long id,
                                          @Valid @RequestBody AddressRequest req,
                                          Authentication authentication) {
        Long userId = ((User) authentication.getPrincipal()).getId();
        return addressService.updateAddress(userId, id, req);
    }


    /**
     * DELETE /api/addresses/{id} → 204 No Content
     * Ownership and default-guard enforced in service (400/403/404).
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAddress(@PathVariable Long id,
                               Authentication authentication) {
        Long userId = ((User) authentication.getPrincipal()).getId();
        addressService.deleteAddress(userId, id);
    }
}
