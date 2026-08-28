package com.harsh.bookstore.service;

import com.harsh.bookstore.dto.AddItemRequest;
import com.harsh.bookstore.dto.BasketResponse;
import com.harsh.bookstore.entity.Basket;
import com.harsh.bookstore.entity.BasketItem;
import com.harsh.bookstore.entity.Book;
import com.harsh.bookstore.entity.Category;
import com.harsh.bookstore.exception.BasketItemNotFoundException;
import com.harsh.bookstore.exception.BookNotFoundException;
import com.harsh.bookstore.exception.MaxQuantityExceededException;
import com.harsh.bookstore.exception.OutOfStockException;
import com.harsh.bookstore.repository.BasketRepository;
import com.harsh.bookstore.repository.BookRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;


/**
 * Unit tests for BasketService.
 *
 * No Spring context — BasketService is instantiated directly with Mockito mocks.
 * BasketRepository.save() is stubbed to return its argument unchanged so that
 * we can assert on the returned BasketResponse without a real database.
 */
@ExtendWith(MockitoExtension.class)
class BasketServiceTest {

    @Mock
    private BasketRepository basketRepository;

    @Mock
    private BookRepository bookRepository;

    private BasketService basketService;

    // Identity constants used across tests
    private static final Long USER_ID = 1L;
    private static final String SESSION_ID = "test-session";


    @BeforeEach
    void setUp() {
        basketService = new BasketService(basketRepository, bookRepository);
    }


    // ==================================================================
    // HELPERS
    // ==================================================================

    /** Build a Book with the given id, price, and stock. */
    private Book book(Long id, BigDecimal price, int stock) {
        Category cat = new Category();
        cat.setName("Fiction");
        cat.setSlug("fiction");

        Book b = new Book();
        b.setId(id);
        b.setTitle("Book " + id);
        b.setAuthors(List.of("Author " + id));
        b.setCoverImageUrl("https://covers.example.com/" + id + ".jpg");
        b.setIsbn("000000000" + id);
        b.setDescription("desc");
        b.setLanguage("en");
        b.setCategory(cat);
        b.setPrice(price);
        b.setStockQuantity(stock);
        return b;
    }

    /** Build an empty basket keyed by userId. */
    private Basket emptyUserBasket() {
        Basket basket = new Basket();
        basket.setId(10L);
        basket.setUserId(USER_ID);
        return basket;
    }

    /** Build a basket that already contains one item. */
    private Basket basketWithItem(Book book, int quantity) {
        Basket basket = emptyUserBasket();
        BasketItem item = new BasketItem();
        item.setId(100L);
        item.setBasket(basket);
        item.setBook(book);
        item.setQuantity(quantity);
        basket.setItems(new ArrayList<>(List.of(item)));
        return basket;
    }

    /**
     * Stub basket lookup (strict) and save (lenient — not all tests call save).
     * Using lenient() on save avoids UnnecessaryStubbing errors in read-only tests.
     */
    private void stubUserBasket(Basket basket) {
        when(basketRepository.findByUserId(USER_ID)).thenReturn(Optional.of(basket));
        lenient().when(basketRepository.save(any(Basket.class)))
                 .thenAnswer(inv -> inv.getArgument(0));
    }

    /** Stub basket creation (first visit — nothing found in repository). */
    private void stubNewUserBasket() {
        when(basketRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        lenient().when(basketRepository.save(any(Basket.class)))
                 .thenAnswer(inv -> inv.getArgument(0));
    }


    // ==================================================================
    // getBasket
    // ==================================================================

    @Test
    void getBasket_emptyBasket() {
        stubUserBasket(emptyUserBasket());

        BasketResponse response = basketService.getBasket(USER_ID, null);

        assertThat(response.getItems()).isEmpty();
        assertThat(response.getTotalItems()).isZero();
        assertThat(response.getBasketTotal()).isEqualByComparingTo(BigDecimal.ZERO);
    }


    // ==================================================================
    // addItem
    // ==================================================================

    @Test
    void addItem_success() {
        Book book = book(1L, new BigDecimal("29.99"), 5);
        stubUserBasket(emptyUserBasket());
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        AddItemRequest req = new AddItemRequest();
        req.setBookId(1L);
        req.setQuantity(2);

        BasketResponse response = basketService.addItem(USER_ID, null, req);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(response.getItems().get(0).getLineTotal())
                .isEqualByComparingTo(new BigDecimal("59.98"));
        assertThat(response.getTotalItems()).isEqualTo(2);
        assertThat(response.getBasketTotal()).isEqualByComparingTo(new BigDecimal("59.98"));
    }

    @Test
    void addItem_sameBookIncrementsQuantity() {
        Book book = book(1L, new BigDecimal("10.00"), 10);
        Basket basket = basketWithItem(book, 2);
        stubUserBasket(basket);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        AddItemRequest req = new AddItemRequest();
        req.setBookId(1L);
        req.setQuantity(1);

        BasketResponse response = basketService.addItem(USER_ID, null, req);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getQuantity()).isEqualTo(3);
    }

    @Test
    void addItem_outOfStock_throws() {
        Book book = book(1L, new BigDecimal("9.99"), 0);   // stock = 0
        stubUserBasket(emptyUserBasket());
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        AddItemRequest req = new AddItemRequest();
        req.setBookId(1L);
        req.setQuantity(1);

        assertThatThrownBy(() -> basketService.addItem(USER_ID, null, req))
                .isInstanceOf(OutOfStockException.class)
                .hasMessage("This book is currently out of stock");
    }

    @Test
    void addItem_exceedsMaxQuantity_throws() {
        Book book = book(1L, new BigDecimal("9.99"), 10);
        Basket basket = basketWithItem(book, 5);   // already has 5
        stubUserBasket(basket);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        AddItemRequest req = new AddItemRequest();
        req.setBookId(1L);
        req.setQuantity(3);   // 5 + 3 = 8 > 7

        assertThatThrownBy(() -> basketService.addItem(USER_ID, null, req))
                .isInstanceOf(MaxQuantityExceededException.class)
                .hasMessage("Maximum quantity per book is 7");
    }

    @Test
    void addItem_bookNotFound_throws() {
        stubUserBasket(emptyUserBasket());
        when(bookRepository.findById(99L)).thenReturn(Optional.empty());

        AddItemRequest req = new AddItemRequest();
        req.setBookId(99L);
        req.setQuantity(1);

        assertThatThrownBy(() -> basketService.addItem(USER_ID, null, req))
                .isInstanceOf(BookNotFoundException.class);
    }


    // ==================================================================
    // updateItem
    // ==================================================================

    @Test
    void updateItem_success() {
        Book book = book(1L, new BigDecimal("15.00"), 5);
        Basket basket = basketWithItem(book, 2);
        stubUserBasket(basket);

        BasketResponse response = basketService.updateItem(USER_ID, null, 1L, 4);

        assertThat(response.getItems().get(0).getQuantity()).isEqualTo(4);
    }

    @Test
    void updateItem_zeroQuantity_removesItem() {
        Book book = book(1L, new BigDecimal("15.00"), 5);
        Basket basket = basketWithItem(book, 2);
        stubUserBasket(basket);

        BasketResponse response = basketService.updateItem(USER_ID, null, 1L, 0);

        assertThat(response.getItems()).isEmpty();
        assertThat(response.getTotalItems()).isZero();
    }

    @Test
    void updateItem_itemNotFound_throws() {
        stubUserBasket(emptyUserBasket());

        assertThatThrownBy(() -> basketService.updateItem(USER_ID, null, 99L, 1))
                .isInstanceOf(BasketItemNotFoundException.class)
                .hasMessageContaining("99");
    }


    // ==================================================================
    // removeItem
    // ==================================================================

    @Test
    void removeItem_success() {
        Book book = book(1L, new BigDecimal("20.00"), 5);
        Basket basket = basketWithItem(book, 1);
        stubUserBasket(basket);

        BasketResponse response = basketService.removeItem(USER_ID, null, 1L);

        assertThat(response.getItems()).isEmpty();
    }

    @Test
    void removeItem_notFound_throws() {
        stubUserBasket(emptyUserBasket());

        assertThatThrownBy(() -> basketService.removeItem(USER_ID, null, 99L))
                .isInstanceOf(BasketItemNotFoundException.class)
                .hasMessageContaining("99");
    }


    // ==================================================================
    // clearBasket
    // ==================================================================

    @Test
    void clearBasket_success() {
        Book book = book(1L, new BigDecimal("10.00"), 5);
        Book book2 = book(2L, new BigDecimal("20.00"), 5);

        Basket basket = emptyUserBasket();
        BasketItem item1 = new BasketItem();
        item1.setId(1L);
        item1.setBasket(basket);
        item1.setBook(book);
        item1.setQuantity(2);
        BasketItem item2 = new BasketItem();
        item2.setId(2L);
        item2.setBasket(basket);
        item2.setBook(book2);
        item2.setQuantity(1);
        basket.setItems(new ArrayList<>(List.of(item1, item2)));

        stubUserBasket(basket);

        BasketResponse response = basketService.clearBasket(USER_ID, null);

        assertThat(response.getItems()).isEmpty();
        assertThat(response.getTotalItems()).isZero();
        assertThat(response.getBasketTotal()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
