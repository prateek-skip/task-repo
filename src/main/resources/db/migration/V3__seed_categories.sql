-- Categories are reference data owned by the catalog team, so they ship with
-- the schema rather than through the API. Fixed ids keep environments aligned.
INSERT INTO categories (id, slug, name) VALUES
    ('0192f3a0-0000-7000-8000-000000000001', 'running-shoes',    'Running Shoes'),
    ('0192f3a0-0000-7000-8000-000000000002', 'training-shoes',   'Training Shoes'),
    ('0192f3a0-0000-7000-8000-000000000003', 'lifestyle-shoes',  'Lifestyle Shoes'),
    ('0192f3a0-0000-7000-8000-000000000004', 'tops',             'Tops'),
    ('0192f3a0-0000-7000-8000-000000000005', 'bottoms',          'Bottoms'),
    ('0192f3a0-0000-7000-8000-000000000006', 'jackets',          'Jackets'),
    ('0192f3a0-0000-7000-8000-000000000007', 'accessories',      'Accessories'),
    ('0192f3a0-0000-7000-8000-000000000008', 'equipment',        'Equipment');
