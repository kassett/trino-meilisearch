CREATE TABLE movie_reviews (
    movie_id BIGINT PRIMARY KEY,
    critic_score INTEGER NOT NULL,
    review_count INTEGER NOT NULL
);

INSERT INTO movie_reviews (movie_id, critic_score, review_count) VALUES
    (1, 88, 241),
    (2, 96, 198),
    (3, 94, 302),
    (4, 97, 411);
