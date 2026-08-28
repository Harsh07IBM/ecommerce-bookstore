package com.harsh.bookstore.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.harsh.bookstore.config.SecurityConfig;
import com.harsh.bookstore.dto.AddItemRequest;
import com.harsh.bookstore.dto.BasketItemDto;
import com.harsh.bookstore.dto.BasketResponse;
import com.harsh.bookstore.dto.UpdateItemRequest;
import com.harsh.bookstore.exception.BasketItemNotFoundException;
import com.harsh.bookstore.exception.BookNotFoundException;
import com.harsh.bookstore.exception.MaxQuantityExceededException;
import com.harsh.bookstore.exception.OutOfStockException;
import com.harsh.bookstore.repository.UserRepository;
import com.harsh.bookstore.service.BasketService;
import com.harsh.bookstore.service.JwtService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


/**
 * HTTP-layer tests for BasketController.
 *
 * All tests operate without a JWT (guest path) because basket endpoints are
 * permitAll(). This is sufficient to exercise the full HTTP translation layer.
 * The resolveIdentity helper uses the MockMvc-provided session id when no
 * Authentication principal is present.
 */
@WebMvcTest(value = BasketController.class,
        excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import(SecurityConfig.class)
class BasketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BasketService basketService;

    // Required by JwtAuthFilter (part of SecurityConfig)
    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;


    // ==================================================================
    // HELPERS
    // ==================================================================

    private BasketResponse emptyBasket() {
        BasketResponse r = new BasketResponse();
        r.setItems(List.of());
        r.setTotalItems(0);
        r.setBasketTotal(BigDecimal.ZERO);
        return r;
    }

    private BasketResponse basketWithOneItem() {
        BasketItemDto item = new BasketItemDto();
        item.setBookId(1L);
        item.setTitle("Clean Code");
        item.setAuthor("Robert C. Martin");
        item.setCoverImageUrl("https://covers.example.com/1.jpg");
        item.setUnitPrice(new BigDecimal("29.99"));
        item.setQuantity(2);
        item.setLineTotal(new BigDecimal("59.98"));

        BasketResponse r = new BasketResponse();
        r.setItems(List.of(item));
        r.setTotalItems(2);
        r.setBasketTotal(new BigDecimal("59.98"));
        return r;
    }


    // ==================================================================
    // GET /api/basket
    // ==================================================================

    @Test
    void getBasket_returns200_emptyBasket() throws Exception {
        when(basketService.getBasket(isNull(), anyString())).thenReturn(emptyBasket());

        mockMvc.perform(get("/api/basket"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.basketTotal").value(0));
    }


    // ==================================================================
    // POST /api/basket/items
    // ==================================================================

    @Test
    void addItem_returns200_withBasket() throws Exception {
        when(basketService.addItem(isNull(), anyString(), any(AddItemRequest.class)))
                .thenReturn(basketWithOneItem());

        AddItemRequest req = new AddItemRequest();
        req.setBookId(1L);
        req.setQuantity(2);

        mockMvc.perform(post("/api/basket/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].bookId").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].lineTotal").value(59.98))
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.basketTotal").value(59.98));
    }

    @Test
    void addItem_returns400_outOfStock() throws Exception {
        when(basketService.addItem(isNull(), anyString(), any(AddItemRequest.class)))
                .thenThrow(new OutOfStockException());

        AddItemRequest req = new AddItemRequest();
        req.setBookId(1L);
        req.setQuantity(1);

        mockMvc.perform(post("/api/basket/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("This book is currently out of stock"));
    }

    @Test
    void addItem_returns400_maxQuantityExceeded() throws Exception {
        when(basketService.addItem(isNull(), anyString(), any(AddItemRequest.class)))
                .thenThrow(new MaxQuantityExceededException());

        AddItemRequest req = new AddItemRequest();
        req.setBookId(1L);
        req.setQuantity(5);

        mockMvc.perform(post("/api/basket/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Maximum quantity per book is 7"));
    }

    @Test
    void addItem_returns404_bookNotFound() throws Exception {
        when(basketService.addItem(isNull(), anyString(), any(AddItemRequest.class)))
                .thenThrow(new BookNotFoundException(99L));

        AddItemRequest req = new AddItemRequest();
        req.setBookId(99L);
        req.setQuantity(1);

        mockMvc.perform(post("/api/basket/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void addItem_returns400_whenBookIdNull() throws Exception {
        // bookId is @NotNull — Bean Validation fires before the service
        AddItemRequest req = new AddItemRequest();
        req.setBookId(null);
        req.setQuantity(1);

        mockMvc.perform(post("/api/basket/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("bookId is required"));
    }


    // ==================================================================
    // PUT /api/basket/items/{bookId}
    // ==================================================================

    @Test
    void updateItem_returns200() throws Exception {
        when(basketService.updateItem(isNull(), anyString(), eq(1L), eq(3)))
                .thenReturn(basketWithOneItem());

        UpdateItemRequest req = new UpdateItemRequest();
        req.setQuantity(3);

        mockMvc.perform(put("/api/basket/items/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void updateItem_returns404_notInBasket() throws Exception {
        when(basketService.updateItem(isNull(), anyString(), eq(99L), anyInt()))
                .thenThrow(new BasketItemNotFoundException(99L));

        UpdateItemRequest req = new UpdateItemRequest();
        req.setQuantity(2);

        mockMvc.perform(put("/api/basket/items/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Book 99 is not in your basket"));
    }


    // ==================================================================
    // DELETE /api/basket/items/{bookId}
    // ==================================================================

    @Test
    void removeItem_returns200() throws Exception {
        when(basketService.removeItem(isNull(), anyString(), eq(1L)))
                .thenReturn(emptyBasket());

        mockMvc.perform(delete("/api/basket/items/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void removeItem_returns404_notInBasket() throws Exception {
        when(basketService.removeItem(isNull(), anyString(), eq(99L)))
                .thenThrow(new BasketItemNotFoundException(99L));

        mockMvc.perform(delete("/api/basket/items/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Book 99 is not in your basket"));
    }


    // ==================================================================
    // DELETE /api/basket
    // ==================================================================

    @Test
    void clearBasket_returns200_emptyResponse() throws Exception {
        when(basketService.clearBasket(isNull(), anyString())).thenReturn(emptyBasket());

        mockMvc.perform(delete("/api/basket"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.basketTotal").value(0));
    }
}
