CREATE DATABASE ecodb_order;
CREATE DATABASE ecodb_product;

-- Debezium CDC (logical replication). Table-level GRANTs are applied after Flyway:
--   deployment/docker/scripts/grant-debezium-outbox.sh
CREATE ROLE debezium WITH REPLICATION LOGIN PASSWORD 'debezium';
GRANT CONNECT ON DATABASE ecodb_product TO debezium;