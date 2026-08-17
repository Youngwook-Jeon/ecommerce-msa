CREATE DATABASE ecodb_order;
CREATE DATABASE ecodb_product;
CREATE DATABASE ecodb_payment;

-- Debezium CDC (logical replication). Table-level GRANTs are applied after Flyway:
--   deployment/docker/scripts/setup-debezium.sh
CREATE ROLE debezium WITH REPLICATION LOGIN PASSWORD 'debezium';
GRANT CONNECT ON DATABASE ecodb_product TO debezium;
GRANT CONNECT ON DATABASE ecodb_order TO debezium;
GRANT CONNECT ON DATABASE ecodb_payment TO debezium;