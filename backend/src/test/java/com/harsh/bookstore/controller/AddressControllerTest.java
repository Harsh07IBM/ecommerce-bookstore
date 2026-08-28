package com.harsh.bookstore.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.harsh.bookstore.config.SecurityConfig;
import com.harsh.bookstore.dto.AddressRequest;
import com.harsh.bookstore.dto.AddressResponse;
import com.harsh.bookstore.entity.User;
import com.harsh.bookstore.exception.AddressAccessForbiddenException;
import com.harsh.bookstore.exception.AddressNotFoundException;
import com.harsh.bookstore.exception.DefaultAddressDeleteException;
import com.harsh.bookstore.repository.UserRepository;
import com.harsh.bookstore.service.AddressService;
import com.harsh.bookstore.service.JwtService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


/**
 * HTTP-layer tests for AddressController.
 *
 * AUTHENTICATION IN @WebMvcTest:
 *   Address endpoints require a JWT. We use Spring Security Test's
 *   SecurityMockMvcRequestPostProcessors.authentication(...) to inject a
 *   pre-built Authentication with our User principal directly — no need to
 *   mint a real JWT token in tests.
 *
 * 401 TESTS:
 *   Performed with no authentication() post-processor. Because all address
 *   endpoints fall under anyRequest().authenticated(), Spring Security returns
 *   401 before the controller method is ever invoked.
 */
@WebMvcTest(value = AddressController.class,
        excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import(SecurityConfig.class)
class AddressControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AddressService addressService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;


    // ==================================================================
    // HELPERS
    // ==================================================================

    /** Build a mock Authentication carrying a User with id=1L. */
    private UsernamePasswordAuthenticationToken userAuth() {
        User user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setPasswordHash("hash");
        return new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private AddressResponse addressResponse(Long id) {
        AddressResponse r = new AddressResponse();
        r.setId(id);
        r.setUserId(1L);
        r.setRecipientName("Test User");
        r.setPhoneNumber("9876543210");
        r.setLine1("1 Test Street");
        r.setCity("Mumbai");
        r.setState("Maharashtra");
        r.setPincode("400001");
        r.setDefault(false);
        return r;
    }

    private AddressRequest validRequest() {
        AddressRequest req = new AddressRequest();
        req.setRecipientName("Test User");
        req.setPhoneNumber("9876543210");
        req.setLine1("1 Test Street");
        req.setCity("Mumbai");
        req.setState("Maharashtra");
        req.setPincode("400001");
        req.setDefault(false);
        return req;
    }


    // ==================================================================
    // GET /api/addresses
    // ==================================================================

    @Test
    void listAddresses_returns200() throws Exception {
        when(addressService.listAddresses(1L))
                .thenReturn(List.of(addressResponse(10L), addressResponse(11L)));

        mockMvc.perform(get("/api/addresses")
                        .with(authentication(userAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(10));
    }

    @Test
    void listAddresses_returns401_noJwt() throws Exception {
        mockMvc.perform(get("/api/addresses"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listAddresses_returns200_emptyList() throws Exception {
        when(addressService.listAddresses(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/addresses")
                        .with(authentication(userAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }


    // ==================================================================
    // POST /api/addresses
    // ==================================================================

    @Test
    void saveAddress_returns201() throws Exception {
        when(addressService.saveAddress(eq(1L), any(AddressRequest.class)))
                .thenReturn(addressResponse(10L));

        mockMvc.perform(post("/api/addresses")
                        .with(authentication(userAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    void saveAddress_returns400_missingField() throws Exception {
        AddressRequest bad = validRequest();
        bad.setRecipientName("");  // @NotBlank fails

        mockMvc.perform(post("/api/addresses")
                        .with(authentication(userAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("recipientName is required"));
    }

    @Test
    void saveAddress_returns400_invalidPincode() throws Exception {
        AddressRequest bad = validRequest();
        bad.setPincode("12345");  // only 5 digits — @Pattern fails

        mockMvc.perform(post("/api/addresses")
                        .with(authentication(userAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("pincode must be exactly 6 numeric digits"));
    }

    @Test
    void saveAddress_returns400_invalidPhone() throws Exception {
        AddressRequest bad = validRequest();
        bad.setPhoneNumber("12345");  // only 5 digits — @Pattern fails

        mockMvc.perform(post("/api/addresses")
                        .with(authentication(userAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("phoneNumber must be exactly 10 numeric digits"));
    }


    // ==================================================================
    // PUT /api/addresses/{id}
    // ==================================================================

    @Test
    void updateAddress_returns200() throws Exception {
        when(addressService.updateAddress(eq(1L), eq(10L), any(AddressRequest.class)))
                .thenReturn(addressResponse(10L));

        mockMvc.perform(put("/api/addresses/10")
                        .with(authentication(userAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    void updateAddress_returns400_validation() throws Exception {
        AddressRequest bad = validRequest();
        bad.setPincode("ABC");  // not digits — @Pattern fails

        mockMvc.perform(put("/api/addresses/10")
                        .with(authentication(userAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateAddress_returns403() throws Exception {
        when(addressService.updateAddress(eq(1L), eq(10L), any(AddressRequest.class)))
                .thenThrow(new AddressAccessForbiddenException());

        mockMvc.perform(put("/api/addresses/10")
                        .with(authentication(userAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value("You do not have permission to access this address"));
    }

    @Test
    void updateAddress_returns404() throws Exception {
        when(addressService.updateAddress(eq(1L), eq(10L), any(AddressRequest.class)))
                .thenThrow(new AddressNotFoundException(10L));

        mockMvc.perform(put("/api/addresses/10")
                        .with(authentication(userAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Address not found: 10"));
    }


    // ==================================================================
    // DELETE /api/addresses/{id}
    // ==================================================================

    @Test
    void deleteAddress_returns204() throws Exception {
        doNothing().when(addressService).deleteAddress(1L, 10L);

        mockMvc.perform(delete("/api/addresses/10")
                        .with(authentication(userAuth())))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteAddress_returns400_defaultGuard() throws Exception {
        doThrow(new DefaultAddressDeleteException())
                .when(addressService).deleteAddress(1L, 10L);

        mockMvc.perform(delete("/api/addresses/10")
                        .with(authentication(userAuth())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Cannot delete the default address while other addresses exist"));
    }

    @Test
    void deleteAddress_returns403() throws Exception {
        doThrow(new AddressAccessForbiddenException())
                .when(addressService).deleteAddress(1L, 10L);

        mockMvc.perform(delete("/api/addresses/10")
                        .with(authentication(userAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteAddress_returns404() throws Exception {
        doThrow(new AddressNotFoundException(10L))
                .when(addressService).deleteAddress(1L, 10L);

        mockMvc.perform(delete("/api/addresses/10")
                        .with(authentication(userAuth())))
                .andExpect(status().isNotFound());
    }
}
