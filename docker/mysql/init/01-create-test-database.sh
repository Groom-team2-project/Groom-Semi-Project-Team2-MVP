#!/bin/sh
set -eu

mysql --protocol=socket -uroot --password="${MYSQL_ROOT_PASSWORD}" <<EOSQL
CREATE DATABASE IF NOT EXISTS \`${MYSQL_TEST_DATABASE:-buying_mvp_test}\`
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
EOSQL
