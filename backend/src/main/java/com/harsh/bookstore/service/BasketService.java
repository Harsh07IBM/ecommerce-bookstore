package com.harsh.bookstore.service;

import com.harsh.bookstore.dto.AddItemRequest;
import com.harsh.bookstore.dto.BasketItemDto;
import com.harsh.bookstore.dto.BasketResponse;
import com.harsh.bookstore.entity.Basket;
import com.harsh.bookstore.entity.BasketItem;
import com.harsh.bookstore.entity.Book;
import com.harsh.bookstore.exception.BasketItemNotFoundException;
import com.harsh.bookstore.exception.BookNotFoundException;
import com.harsh.bookstore.exception.MaxQuantityExceededException;
import com.harsh.bookstore.exception.OutOfStockException;
import com.harsh.bookstore.repository.BasketRepository;
import com.harsh.bookstore.repository.BookRepository;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;


/**
 * BasketService — all business rules for the shopping basket (FEAT-06).
 *
 * IDENTITY MODEL:
 *   Every public method receives two nullable identity parameters:
 *     userId    — non-null for JWT-authenticated users
 *     sessionId — non-null for guests (HttpSession.getId())
 *   Exactly one is non-null per call. resolveBasket() selects the correct
 *   lookup/create path based on which one is present.
 *
 * PERSISTENCE:
 *   Because Basket has cascade = ALL and orphanRemoval = true on its items
 *   collection, a single basketRepository.save(basket) at the end of each
 *   mutating method is sufficient to persist all item additions, updates,
 *   and deletions — no separate item repository is needed.
 */
@Service
public class BasketService {

    private final BasketRepository basketRepository;
    private final BookRepository bookRepository;

    public BasketService(BasketRepository basketRepository,
                         BookRepository bookRepository) {
        this.basketRepository = basketRepository;
        this.bookRepository = bookRepository;
    }


    // ==================================================================
    // PUBLIC API
    // ==================================================================

    /**
     * Return the caller's current basket.
     * Creates an empty basket if none exists yet (first visit).
     */
    public BasketResponse getBasket(Long userId, String sessionId) {
        Basket basket = resolveBasket(userId, sessionId);
        return toResponse(basket);
    }


    /**
     * Add a book to the basket (or increment its quantity if already present).
     *
     * Business rules enforced (spec §2):
     *   BR-04 — book must be in stock (stockQuantity > 0)
     *   BR-05 — resulting quantity for this book must not exceed 7
     *   BR-03 — maximum per-book quantity is 7
     *
     * @throws BookNotFoundException        if bookId does not exist
     * @throws OutOfStockException          if the book's stock is 0
     * @throws MaxQuantityExceededException if adding would push quantity above 7
     */
    public BasketResponse addItem(Long userId, String sessionId, AddItemRequest req) {
        Basket basket = resolveBasket(userId, sessionId);

        Book book = bookRepository.findById(req.getBookId())
                .orElseThrow(() -> new BookNotFoundException(req.getBookId()));

        if (book.getStockQuantity() == 0) {
            throw new OutOfStockException();
        }

        Optional<BasketItem> existing = basket.getItems().stream()
                .filter(i -> i.getBook().getId().equals(req.getBookId()))
                .findFirst();

        if (existing.isPresent()) {
            int newQty = existing.get().getQuantity() + req.getQuantity();
            if (newQty > 7) {
                throw new MaxQuantityExceededException();
            }
            existing.get().setQuantity(newQty);
        } else {
            BasketItem item = new BasketItem();
            item.setBasket(basket);
            item.setBook(book);
            item.setQuantity(req.getQuantity());
            basket.getItems().add(item);
        }

        basketRepository.save(basket);
        return toResponse(basket);
    }


    /**
     * Set the quantity of a book that is already in the basket.
     * A quantity of 0 removes the item entirely (BR-07).
     *
     * @throws BasketItemNotFoundException if the book is not in the basket
     */
    public BasketResponse updateItem(Long userId, String sessionId,
                                     Long bookId, int quantity) {
        Basket basket = resolveBasket(userId, sessionId);

        BasketItem item = basket.getItems().stream()
                .filter(i -> i.getBook().getId().equals(bookId))
                .findFirst()
                .orElseThrow(() -> new BasketItemNotFoundException(bookId));

        if (quantity == 0) {
            basket.getItems().remove(item);
        } else {
            item.setQuantity(quantity);
        }

        basketRepository.save(basket);
        return toResponse(basket);
    }


    /**
     * Remove a specific book from the basket entirely.
     *
     * @throws BasketItemNotFoundException if the book is not in the basket (BR-06)
     */
    public BasketResponse removeItem(Long userId, String sessionId, Long bookId) {
        Basket basket = resolveBasket(userId, sessionId);

        BasketItem item = basket.getItems().stream()
                .filter(i -> i.getBook().getId().equals(bookId))
                .findFirst()
                .orElseThrow(() -> new BasketItemNotFoundException(bookId));

        basket.getItems().remove(item);
        basketRepository.save(basket);
        return toResponse(basket);
    }


    /**
     * Remove all items from the basket.
     */
    public BasketResponse clearBasket(Long userId, String sessionId) {
        Basket basket = resolveBasket(userId, sessionId);
        basket.getItems().clear();
        basketRepository.save(basket);
        return toResponse(basket);
    }


    // ==================================================================
    // PRIVATE HELPERS
    // ==================================================================

    /**
     * Find the caller's existing basket, or create and persist a new empty one.
     *
     * INVARIANT: exactly one of userId / sessionId is non-null per basket row.
     * This method enforces that by only setting the relevant field when creating.
     */
    private Basket resolveBasket(Long userId, String sessionId) {
        if (userId != null) {
            return basketRepository.findByUserId(userId)
                    .orElseGet(() -> {
                        Basket b = new Basket();
                        b.setUserId(userId);
                        return basketRepository.save(b);
                    });
        } else {
            return basketRepository.findBySessionId(sessionId)
                    .orElseGet(() -> {
                        Basket b = new Basket();
                        b.setSessionId(sessionId);
                        return basketRepository.save(b);
                    });
        }
    }


    /**
     * Map a Basket entity to the API response shape.
     *
     * lineTotal  = unitPrice × quantity  (exact, using BigDecimal arithmetic)
     * totalItems = sum of all quantities
     * basketTotal = sum of all lineTotals
     */
    private BasketResponse toResponse(Basket basket) {
        List<BasketItemDto> items = basket.getItems().stream()
                .map(item -> {
                    Book book = item.getBook();
                    String author = (book.getAuthors() == null || book.getAuthors().isEmpty())
                            ? ""
                            : book.getAuthors().get(0);

                    BigDecimal lineTotal = book.getPrice()
                            .multiply(BigDecimal.valueOf(item.getQuantity()));

                    BasketItemDto dto = new BasketItemDto();
                    dto.setBookId(book.getId());
                    dto.setTitle(book.getTitle());
                    dto.setAuthor(author);
                    dto.setCoverImageUrl(book.getCoverImageUrl());
                    dto.setUnitPrice(book.getPrice());
                    dto.setQuantity(item.getQuantity());
                    dto.setLineTotal(lineTotal);
                    return dto;
                })
                .toList();

        int totalItems = items.stream()
                .mapToInt(BasketItemDto::getQuantity)
                .sum();

        BigDecimal basketTotal = items.stream()
                .map(BasketItemDto::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BasketResponse response = new BasketResponse();
        response.setItems(items);
        response.setTotalItems(totalItems);
        response.setBasketTotal(basketTotal);
        return response;
    }
}
