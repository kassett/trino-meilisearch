CREATE TABLE movie_financials (
    movie_id BIGINT PRIMARY KEY,
    budget_millions DECIMAL(10, 2) NOT NULL,
    gross_millions DECIMAL(10, 2) NOT NULL
);

INSERT INTO movie_financials (movie_id, budget_millions, gross_millions) VALUES
    (1, 63.00, 467.20),
    (2, 19.00, 395.80),
    (3, 47.00, 203.40),
    (4, 154.60, 380.40);
