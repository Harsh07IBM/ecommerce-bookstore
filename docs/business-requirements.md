# E-Commerce Bookstore — Business Requirements

**Document:** Business Requirements  
**Project:** AI-Assisted E-Commerce Bookstore  
**Status:** Revised 2026-08-24 — Admin capabilities removed from scope  
**Purpose:** Define the business requirements and expected behavior of the eCommerce bookstore before architecture, API design, and implementation.

---

# 1. Document Purpose

This document defines the business requirements for the E-Commerce Bookstore application.

It describes:

- The purpose and scope of the application.
- The users of the application.
- Customer capabilities.
- Product catalogue capabilities.
- Shopping and purchasing capabilities.
- Order capabilities.
- Payment-related capabilities.
- Recommendation and gift-point capabilities.
- Business rules.
- Assumptions.
- Open questions requiring clarification.

This document describes **what the business requires**, not how the system will technically implement those requirements.

Technical decisions such as:

- Programming language.
- Framework.
- Database implementation.
- API design.
- Class structure.
- Frontend framework.
- Authentication technology.
- Deployment architecture.

must be documented separately.

---

# 2. Project Overview

## 2.1 Business Objective

The objective is to build an eCommerce bookstore platform that allows customers to:

- View books.
- Browse the available book catalogue.
- Select books.
- Add books to a basket.
- Purchase selected books.
- Complete payment.
- View their order history.
- Purchase previously ordered books again.
- Receive recommendations based on their order history.
- Redeem available gift points during purchase.
- Cancel an order within the permitted cancellation period.

The capstone describes the application as an eCommerce platform for viewing, browsing, and purchasing books. It describes the use case as an online store where a customer can browse, select, and buy products.

The provided customer journey includes login, authentication, catalogue browsing, product selection, basket management, order history, recommendations, delivery address selection, payment, gift-point redemption, purchase confirmation, and order cancellation within 48 hours.

---

# 3. Scope

## 3.1 In Scope

The customer-facing scope includes:

- User login and authentication.
- Product catalogue browsing.
- Category-based catalogue browsing.
- Brand browsing.
- Product selection.
- Product details.
- Related product selection.
- Adding products to the basket.
- Order history.
- Buy Again functionality.
- Recommendations based on order history.
- Delivery address selection.
- Payment initiation.
- Payment method selection.
- Gift-point redemption.
- Payment confirmation.
- Purchase confirmation.
- Order cancellation within 48 hours.

The bookstore catalogue is populated by an **offline seed pipeline** — a script that pulls book metadata from a public source (Open Library) and writes it to a JSON file that the application loads on startup. There is no in-application administrative interface for maintaining the catalogue.

---

## 3.2 Out of Scope Unless Explicitly Approved

The following are not defined sufficiently by the supplied business requirements and must not be treated as confirmed requirements without a project decision:

- Real-world payment gateway integration.
- Real-time payment settlement.
- Refund processing.
- Shipping-provider integration.
- Email/SMS notification integration.
- Advanced AI-based recommendation algorithms.
- Multi-vendor marketplace functionality.
- International taxation rules.
- Complex warehouse management.
- Multi-store support.
- Subscription-based book purchasing.
- Digital rights management.
- E-book download/reading functionality.
- **Any in-application administrative interface** — no Admin user type, no admin login, no admin CRUD for books, categories, users, orders, or reviews. The supplied capstone requirements did not identify an Admin role, so it is explicitly excluded. Catalogue maintenance is performed exclusively via the offline seed pipeline described in §3.1.

If any of these become necessary, they must be added to the appropriate business requirements before implementation.

---

# 4. User Types

The project identifies the following user types:

1. Guest User
2. Registered User

The capstone architecture identifies exactly these two. No Admin role is defined (see §3.2).

The exact permissions of each user type are not completely defined by the supplied requirements and therefore require clarification where noted below.

---

# 5. Guest User

## 5.1 Confirmed Requirement

A Guest User is a recognized user type of the application.

## 5.2 Proposed Capabilities

The following capabilities are proposed for a Guest User:

- Access the bookstore.
- View available books.
- Browse categories.
- Browse products.
- View product details.
- View related products.

## 5.3 Open Questions

The following must be clarified before final implementation:

- Can a guest add products to a basket?
- Can a guest maintain a basket without creating an account?
- Can a guest begin checkout?
- Can a guest purchase a book?
- Must a guest register/login before purchasing?
- Can a guest access recommendations?
- Can a guest access order history?

These are currently **Open Questions**, not confirmed business requirements.

---

# 6. Registered User

A Registered User is an authenticated customer.

## 6.1 Authentication

The user must be able to authenticate with the application.

The supplied customer journey explicitly includes:

- Login Page.
- User Authentication.

## 6.2 Catalogue Access

A registered user should be able to:

- Access the product catalogue.
- Select product categories.
- Browse products within categories.
- Browse brands.
- Select a product.
- View related products.

## 6.3 Shopping

A registered user should be able to:

- Select products.
- Add products to the basket.
- Review the basket.
- Proceed toward purchase.

## 6.4 Order History

A registered user should be able to:

- View previous orders.
- Select previously purchased products.
- Use the Buy Again functionality.

The supplied customer journey explicitly mentions order history with a Buy Again feature.

## 6.5 Recommendations

A registered user should receive recommendations based on their order history.

The supplied requirements explicitly state that recommendations are based on order history.

The exact recommendation algorithm is not specified and must be defined separately.

---

# 7. Product Catalogue

The bookstore must provide a catalogue through which customers can browse available books/products.

## 7.1 Catalogue Access

The customer should be able to access the product catalogue.

## 7.2 Category Browsing

The customer should be able to:

- Select a product category.
- Access the catalogue associated with that category.
- Browse products within that category.

## 7.3 Brand Browsing

The customer should be able to browse products by brand.

The capstone wireframe explicitly identifies "Browse the brands" as a catalogue capability.

## 7.4 Product Selection

The customer should be able to select an individual product from the catalogue.

## 7.5 Product Availability

Products displayed in the catalogue should indicate their availability where applicable.

The product-selection flow indicates that a product may be tagged with a tentative delivery date.

## 7.6 Related Products

When a customer selects a product, related products should be displayed for potential selection.

The exact rules for determining related products are not specified.

---

# 8. Product Information

The customer must be able to view sufficient information to make a product-selection decision.

The exact product attributes are not completely defined in the supplied business requirements and should therefore be finalized during the data-model analysis.

At minimum, the product concept must support the information necessary for:

- Product identification.
- Product selection.
- Catalogue display.
- Product purchasing.
- Pricing.
- Availability.
- Delivery information where applicable.

The final list of product attributes must be determined from the wireframes and data-model analysis.

---

# 9. Product Search and Filtering

The capstone workflow explicitly identifies search and filter as required feature examples during wireframe analysis.

## 9.1 Search

The customer should be able to search the bookstore catalogue.

## 9.2 Filtering

The catalogue should support appropriate filtering capabilities based on the actual wireframe requirements.

The exact filter attributes must be confirmed during wireframe analysis.

Possible filters must not be treated as confirmed requirements until they are supported by the wireframes or approved as additional business requirements.

---

# 10. Shopping Basket

The application must provide a shopping basket for selected products.

## 10.1 Add Product

A customer should be able to add one or more selected products to the basket.

## 10.2 Basket Review

The customer should be able to review products that have been added to the basket.

## 10.3 Basket Update

The customer should be able to update the basket as supported by the final UI requirements.

The supplied wireframe identifies the shopping basket and the ability to add products to the basket.

The exact quantity-update and item-removal behavior should be finalized during detailed feature specification.

## 10.4 Recommendations in Basket

The basket experience may display recommendations based on the customer's order history.

This behavior is explicitly mentioned in the supplied customer journey.

---

# 11. Order Management

The application must support customer order management.

## 11.1 Order Creation

A customer should be able to create an order from the selected products and complete the purchasing process.

## 11.2 Order History

An authenticated customer should be able to view their previous orders.

## 11.3 Buy Again

The customer should be able to select products from previous orders for purchasing again.

## 11.4 Order Cancellation

The customer should be able to cancel an order within 48 hours.

The capstone explicitly states:

> Cancel Order Within 48 hrs

The exact cancellation rules require clarification.

### Open Questions

- Is cancellation measured from order creation time?
- Does cancellation depend on order status?
- Can an order be cancelled after it has been shipped?
- What happens to the payment after cancellation?
- Is a refund automatically initiated?
- Are partially fulfilled orders cancellable?

These must be defined before implementation.

---

# 12. Checkout and Delivery

The payment and purchase flow includes delivery-address selection.

## 12.1 Delivery Address

The customer should be able to select an address for delivery during the purchasing process.

## 12.2 Tentative Delivery Date

The product-selection flow indicates that products may display a tentative delivery date.

The exact calculation and business rules for delivery dates are not defined.

## 12.3 Delivery Rules — Open Questions

The following require clarification:

- Can a customer maintain multiple addresses?
- Can a customer add a new address during checkout?
- Can a customer edit an existing address?
- How is the tentative delivery date calculated?
- Are delivery charges applicable?
- Are delivery charges product-specific?
- Are delivery charges location-specific?

---

# 13. Payment

The application must support the payment portion of the purchasing flow.

## 13.1 Initiate Payment

The customer should be able to initiate payment using an appropriate payment option.

## 13.2 Payment Methods

The supplied payment wireframe identifies:

- Credit card.
- Debit card.

Additional payment methods should not be added unless approved.

## 13.3 Complete Payment

The customer should be able to complete the payment process.

## 13.4 Payment Confirmation

The application should provide confirmation of the payment result.

## 13.5 Purchase Confirmation

After successful purchase completion, the application should display a purchase confirmation.

The supplied purchase-confirmation wireframe indicates that the message should communicate purchase completion.

---

# 14. Gift Points

The purchasing flow includes the ability to redeem gift points.

## 14.1 Gift Point Redemption

A customer should be able to redeem eligible gift points during the payment process.

## 14.2 Gift Point Rules

The following rules are not defined and must be clarified:

- How are gift points earned?
- How many points does a customer receive?
- What is the monetary value of a point?
- Is there a maximum number of points that can be redeemed?
- Can all products be purchased using gift points?
- Do gift points expire?
- What happens to redeemed points if an order is cancelled?

Until these rules are approved, only the general capability of gift-point redemption should be considered confirmed.

---

# 15. Recommendations

The application should provide recommendations based on a customer's order history.

## 15.1 Recommendation Context

Recommendations may appear:

- During the customer shopping experience.
- Within the shopping basket.
- Based on previously purchased products.

The supplied requirements explicitly identify recommendations based on order history.

## 15.2 Recommendation Logic

The actual recommendation algorithm is not defined.

Possible approaches may be evaluated during the technical design phase, but the business requirement should first establish what the recommendation feature is expected to accomplish.

---

# 16. Purchase Confirmation

After a successful purchase, the application must provide a confirmation to the customer.

The confirmation should indicate that the purchase has been successfully completed.

The exact contents of the confirmation screen should be derived from the supplied wireframe.

---

# 17. Business Rules

The following business rules are explicitly identified or strongly implied by the supplied requirements.

## BR-001 — Authentication

The application supports user login and authentication.

## BR-002 — Category-Based Catalogue

Customers can access the product catalogue through product categories.

## BR-003 — Brand Browsing

Customers can browse products by brand.

## BR-004 — Related Products

Related products can be presented when a customer selects a product.

## BR-005 — Basket

Customers can add selected products to a basket.

## BR-006 — Order History

Authenticated customers can access their order history.

## BR-007 — Buy Again

Customers can use previous orders to purchase products again.

## BR-008 — Recommendations

Recommendations are based on customer order history.

## BR-009 — Delivery Address

The purchasing flow requires the customer to select an address for delivery.

## BR-010 — Payment

Customers must complete payment as part of the purchase flow.

## BR-011 — Gift Points

Customers can redeem eligible gift points during the payment flow.

## BR-012 — Payment Confirmation

The application provides confirmation of payment.

## BR-013 — Purchase Confirmation

The application provides confirmation after purchase completion.

## BR-014 — Order Cancellation

An order can be cancelled within 48 hours.

The exact conditions surrounding this rule require clarification.

---

# 18. Business Requirements vs Technical Requirements

This document intentionally avoids defining implementation details.

For example:

### Business Requirement

> Customer can search for books.

### Technical Design

> Implement a REST endpoint for searching books.

### Implementation

> Create a Spring Boot controller, service, repository, DTOs, and database query.

These belong to different project artifacts.

The business requirements define **what** the business needs.

The architecture, specifications, plans, designs, and implementation
documents define **how the system will satisfy those needs**.

---

# 19. Assumptions

The following are current assumptions and must be validated before
implementation.

## A-001 — Book Catalogue

The application will require an initial catalogue of books/products. The source of the initial catalogue is public book metadata (Open Library), fetched by an offline seed script.

## A-002 — Catalogue Data

External/public book metadata may be used to populate an initial
catalogue, subject to licensing and suitability.

This is a data-acquisition decision rather than a business requirement.

## A-003 — Catalogue Maintenance

Ongoing changes to the catalogue (adding, updating, or removing books) are performed by re-running the offline seed script. There is no in-application administrative interface. This assumption replaces the previously proposed Admin role, which is not supported by the supplied capstone requirements.

## A-004 — Payment

The supplied requirements describe payment behavior but do not define
a production payment gateway.

The final payment implementation approach must be decided separately.

## A-005 — Recommendations

Recommendations are based on order history, but the algorithm is not
specified.

---

# 20. Open Questions

The following questions must be resolved before finalizing the
corresponding specifications.

## Product and Catalogue

1. Is the platform selling physical books, eBooks, or both? — **Resolved 2026-08-24: physical books only.**
2. What exactly does "brand" represent for a book?
3. What product attributes are required?
4. What search fields must be supported?
5. What filters must be supported?
6. What determines a related product?

## Users

7. What exactly can a Guest User do?
8. Can a Guest User add products to a basket?
9. Can a Guest User purchase?
10. Is authentication mandatory before checkout?

## Orders

11. What exactly does the 48-hour cancellation rule mean?
12. Which order statuses allow cancellation?
13. What happens to payment after cancellation?
14. Is refund processing required?

## Delivery

15. How is the tentative delivery date calculated?
16. Are delivery charges required?
17. Can customers maintain multiple delivery addresses?

## Payment

18. Is payment simulated or connected to a real payment gateway?
19. Which payment methods are required?
20. What happens when payment fails?
21. Is refund processing required?

## Gift Points

22. How are gift points earned?
23. What is the value of a gift point?
24. What are the redemption limits?
25. Do gift points expire?
26. What happens to redeemed points after cancellation?

## Recommendations

27. What exactly should recommendations be based on?
28. How many recommendations should be displayed?
29. Is a simple rule-based recommendation sufficient?

## Catalogue Population

30. Where will the initial book catalogue come from? — **Resolved 2026-08-24: Open Library, via an offline Python seed script.**
31. What data fields must be imported?
32. Who maintains the catalogue after initial population? — **Resolved 2026-08-24: no in-application maintenance. Re-run the seed script to refresh the catalogue.**
33. Are external book metadata sources permitted for this project? — **Resolved 2026-08-24: yes — Open Library is used.**

---

# 21. Requirement Classification

To avoid accidentally treating assumptions as official business
requirements, every requirement should be classified as one of:

| Classification | Meaning |
|---|---|
| **Confirmed** | Explicitly supported by the supplied capstone requirements or wireframes |
| **Proposed** | Suggested to make the application operational but not explicitly supplied |
| **Open Question** | Not sufficiently defined and requires a business decision |
| **Out of Scope** | Explicitly excluded or not required unless later approved |

This classification must be maintained as the project evolves.

---

# 22. Traceability Principle

Every implemented feature should be traceable back to a business
requirement.

The expected relationship is:

```text
Business Requirement
        ↓
Feature Specification
        ↓
Implementation Plan
        ↓
Technical Design
        ↓
Implementation
        ↓
Testing
        ↓
Verification
```

Any code that does not trace back to a business requirement must be
justified during review or treated as scope creep.
