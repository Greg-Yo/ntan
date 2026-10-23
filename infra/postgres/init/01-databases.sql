-- Une base et un utilisateur par service (valeurs de développement uniquement).
CREATE USER tiers WITH PASSWORD 'tiers';
CREATE DATABASE tiers OWNER tiers;

CREATE USER catalog WITH PASSWORD 'catalog';
CREATE DATABASE catalog OWNER catalog;

CREATE USER sales WITH PASSWORD 'sales';
CREATE DATABASE sales OWNER sales;
