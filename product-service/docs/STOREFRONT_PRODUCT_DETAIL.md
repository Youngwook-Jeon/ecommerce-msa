# Storefront product detail (Phase 0 contract)

Gateway: `GET /api/v1/product_service/public/products/{productId}`

## Visibility (PLP vs PDP)

| Product status | PLP / facets / search | PDP (`GET …/{id}`) | `purchasable` |
|----------------|----------------------|--------------------|---------------|
| `ACTIVE` | Yes | 200 | `true` |
| `INACTIVE` | No | 200 (preview / pre-launch) | `false` |
| `OUT_OF_STOCK` | No | 200 (display-only) | `false` |
| `DISCONTINUED` | No | 200 (display-only) | `false` |
| `DRAFT` | No | **404** | — |
| `DELETED` | No | **404** | — |
| Not found | No | **404** | — |

- **404 not 403** for draft/deleted/missing — avoids leaking existence to anonymous clients.
- **PLP stays `ACTIVE` only** — `INACTIVE` is reachable by direct URL, share link, or admin “View on store” (future); category pages are not cluttered with previews.
- **Category on PDP**: product load does **not** require `category.status = ACTIVE`. Category `DELETED` may still 404 (Phase 1). `INACTIVE` category is allowed when the product is viewable.
- **Variants on PDP**: only `ACTIVE` variants in the response (Phase 1). Product-level `INACTIVE` still returns PDP with `purchasable: false`.

Implementation: `StorefrontProductVisibilityPolicy` in `product-domain-application`.

## Response shape (200)

```json
{
  "id": "0194a1b2-…",
  "categoryId": 12,
  "name": "Example Sneaker",
  "description": "…",
  "brand": "Nike",
  "mainImageUrl": "https://…",
  "basePrice": 89.99,
  "status": "INACTIVE",
  "conditionType": "NEW",
  "purchasable": false,
  "listedInCatalog": false,
  "optionGroups": [
    {
      "productOptionGroupId": "…",
      "optionGroupId": "…",
      "groupKey": "color",
      "displayName": "Color",
      "stepOrder": 1,
      "required": true,
      "drivesVariantImages": true,
      "optionValues": [
        {
          "productOptionValueId": "…",
          "optionValueId": "…",
          "displayName": "Red",
          "priceDelta": 0,
          "isDefault": true,
          "images": [
            { "id": "…", "url": "https://…", "role": "MAIN", "sortOrder": 0 }
          ]
        }
      ]
    }
  ],
  "variants": [
    {
      "productVariantId": "…",
      "sku": "SKU-…",
      "stockQuantity": 0,
      "calculatedPrice": 89.99,
      "mainImageUrl": "https://…",
      "selectedProductOptionValueIds": ["…"]
    }
  ]
}
```

### Field notes

| Field | Purpose |
|-------|---------|
| `status` | Raw product status for UI badges (“Coming soon”, “Unavailable”) |
| `purchasable` | Derived; `false` when status ≠ `ACTIVE` (cart disabled on PDP) |
| `listedInCatalog` | Derived; `false` for `INACTIVE` etc.; breadcrumb “Back to category” only when `true` and category known |
| `groupKey` / `displayName` | From global `option_groups` / `option_values` |
| `images` | POV gallery when `drivesVariantImages`; else empty, fallback to variant/product image |

## Frontend

- Route: `/products/[productId]`
- API 404 → Next.js `notFound()`
- `INACTIVE` / non-purchasable: show PDP, disable add-to-cart, optional banner

## Phase 1+ (out of Phase 0)

- Repository query with `status IN (viewable set)` instead of `status = ACTIVE`
- Join option display names and POV images
- Controller + IT + `publicProductService.getPublicProductDetail`
