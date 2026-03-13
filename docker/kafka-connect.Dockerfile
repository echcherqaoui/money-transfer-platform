ARG CONFLUENT_VERSION

FROM confluentinc/cp-kafka-connect:${CONFLUENT_VERSION}

ARG DEBEZIUM_VERSION

USER root

# Install Debezium Postgres Connector
RUN confluent-hub install --no-prompt debezium/debezium-connector-postgresql:${DEBEZIUM_VERSION} && \
    chmod -R o+r /usr/share/confluent-hub-components # Ensure the user (1000) can read the new files

USER 1000