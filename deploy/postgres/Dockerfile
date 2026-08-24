FROM postgres:18.6

ARG PGAUDIT_VERSION=18.0

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        build-essential \
        ca-certificates \
        git \
        libkrb5-dev \
        postgresql-server-dev-18 \
    && git clone --branch "${PGAUDIT_VERSION}" --depth 1 https://github.com/pgaudit/pgaudit.git /tmp/pgaudit \
    && make -C /tmp/pgaudit USE_PGXS=1 \
    && make -C /tmp/pgaudit install USE_PGXS=1 \
    && rm -rf /tmp/pgaudit /var/lib/apt/lists/* \
    && mkdir -p /var/log/postgresql \
    && chown -R postgres:postgres /var/log/postgresql

USER postgres
