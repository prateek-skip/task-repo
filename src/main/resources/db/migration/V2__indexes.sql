-- One composite index per (filter, sort) shape the API exposes, each ending in
-- id so keyset pagination "(sort_key, id) > (?, ?)" is fully index-served.
--
-- status leads every index because every public read filters on it with an
-- equality predicate. A partial index "WHERE status = 'ACTIVE'" would be
-- smaller, but the planner can only use it when the literal is visible, and
-- prepared statements bind status as a parameter.

-- category + price range, ordered by price (price_desc scans it backwards)
CREATE INDEX ix_products_status_cat_price_id
    ON products (status, category_id, price, id);

-- price range across all categories
CREATE INDEX ix_products_status_price_id
    ON products (status, price, id);

-- newest in a category
CREATE INDEX ix_products_status_cat_created_id
    ON products (status, category_id, created_at DESC, id DESC);

-- newest across all categories
CREATE INDEX ix_products_status_created_id
    ON products (status, created_at DESC, id DESC);
