package com.harsh.bookstore.dto;


/**
 * GiftPointsResponse — response body for GET /api/users/me/gift-points.
 */
public class GiftPointsResponse {

    private int giftPoints;

    public GiftPointsResponse() {
    }

    public int getGiftPoints() { return giftPoints; }
    public void setGiftPoints(int giftPoints) { this.giftPoints = giftPoints; }
}
