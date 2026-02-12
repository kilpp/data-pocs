CREATE TABLE IF NOT EXISTS sales_transactions (
    id SERIAL PRIMARY KEY,
    salesman_id VARCHAR(50) NOT NULL,
    salesman_name VARCHAR(200) NOT NULL,
    city VARCHAR(100) NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    email VARCHAR(200) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sales_created_at ON sales_transactions(created_at);
CREATE INDEX idx_sales_city ON sales_transactions(city);
CREATE INDEX idx_sales_salesman ON sales_transactions(salesman_id);
