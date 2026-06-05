# API Contract - TirTir Project

**Base URL:** `http://localhost:5001/api`

This document outlines the API endpoints designed for the TirTir Foundation Finder application.

## A. Auth
* **POST** `/api/auth/register` - Register a new user.
* **POST** `/api/auth/login` - Authenticate user and return token.

## B. Products (Planned)
* **GET** `/api/products` - Retrieve list of products.
* **GET** `/api/products/:id` - Retrieve product details by ID.

## C. Shades (Existing)
* **GET** `/api/shades?limit=50` - Retrieve list of foundation shades (supports pagination/filtering).
* **GET** `/api/shades/:id` *(Optional)* - Retrieve details of a specific shade.

## D. Shade Matching (WOW Feature)
Core feature for analyzing skin tone and suggesting products.

* **POST** `/api/foundation-match`
    * **Body:**
      ```json
      {
        "roiColors": [
          {"r": 255, "g": 220, "b": 200},
          {"r": 250, "g": 210, "b": 190},
          {"r": 245, "g": 200, "b": 180}
        ],
        "preferences": {
           "finish": "matte",
           "coverage": "high"
        }
      }
      ```
    * **Response:**
      ```json
      {
        "recommendedShade": { ... },
        "alternatives": [ ... ],
        "explain": {
          "undertone": "Neutral",
          "score": 98.5,
          "reasons": ["Matches skin brightness (L)", "Undertone compatible"]
        }
      }
      ```

## E. SkinAnalysis History
* **POST** `/api/skin-analyses` - Save a new analysis result.
* **GET** `/api/skin-analyses/me` - Get analysis history for the logged-in user.
* **DELETE** `/api/skin-analyses/:id` - Delete a specific analysis record.

## F. Feedback CRUD
* **POST** `/api/feedbacks` - Create a new feedback.
* **GET** `/api/feedbacks/me` - Get my feedbacks.
* **PATCH** `/api/feedbacks/:id` - Update a feedback.
* **DELETE** `/api/feedbacks/:id` - Delete a feedback.

## G. Cart/Order (Future)
* **POST** `/api/cart/items` - Add item to cart.
* **GET** `/api/cart` - View current cart.
* **POST** `/api/orders` - Place an order (Checkout).
* **GET** `/api/orders/me` - Get order history.