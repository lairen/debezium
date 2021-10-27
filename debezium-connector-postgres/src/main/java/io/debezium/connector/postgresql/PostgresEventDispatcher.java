/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.debezium.connector.postgresql;

import io.debezium.connector.base.ChangeEventQueue;
import io.debezium.connector.postgresql.connection.LogicalDecodingMessage;
import io.debezium.heartbeat.Heartbeat;
import io.debezium.jdbc.JdbcConnection;
import io.debezium.pipeline.DataChangeEvent;
import io.debezium.pipeline.EventDispatcher;
import io.debezium.pipeline.source.spi.EventMetadataProvider;
import io.debezium.pipeline.spi.ChangeEventCreator;
import io.debezium.pipeline.spi.OffsetContext;
import io.debezium.pipeline.spi.Partition;
import io.debezium.schema.DataCollectionFilters;
import io.debezium.schema.DataCollectionId;
import io.debezium.schema.DatabaseSchema;
import io.debezium.schema.TopicSelector;
import io.debezium.util.SchemaNameAdjuster;
import org.apache.kafka.connect.source.SourceRecord;

public class PostgresEventDispatcher<T extends DataCollectionId> extends EventDispatcher<T> {
    private final ChangeEventQueue<DataChangeEvent> queue;
    private final LogicalDecodingMessageMonitor logicalDecodingMessageMonitor;

    public PostgresEventDispatcher(PostgresConnectorConfig connectorConfig, TopicSelector<T> topicSelector,
                                   DatabaseSchema<T> schema, ChangeEventQueue<DataChangeEvent> queue, DataCollectionFilters.DataCollectionFilter<T> filter,
                                   ChangeEventCreator changeEventCreator, EventMetadataProvider metadataProvider, SchemaNameAdjuster schemaNameAdjuster) {
        this(connectorConfig, topicSelector, schema, queue, filter, changeEventCreator, null, metadataProvider,
                null, schemaNameAdjuster, null);
    }

    public PostgresEventDispatcher(PostgresConnectorConfig connectorConfig, TopicSelector<T> topicSelector,
                                   DatabaseSchema<T> schema, ChangeEventQueue<DataChangeEvent> queue, DataCollectionFilters.DataCollectionFilter<T> filter,
                                   ChangeEventCreator changeEventCreator, EventMetadataProvider metadataProvider,
                                   Heartbeat heartbeat, SchemaNameAdjuster schemaNameAdjuster) {
        this(connectorConfig, topicSelector, schema, queue, filter, changeEventCreator, null, metadataProvider,
                heartbeat, schemaNameAdjuster, null);
    }

    public PostgresEventDispatcher(PostgresConnectorConfig connectorConfig, TopicSelector<T> topicSelector,
                                   DatabaseSchema<T> schema, ChangeEventQueue<DataChangeEvent> queue, DataCollectionFilters.DataCollectionFilter<T> filter,
                                   ChangeEventCreator changeEventCreator, InconsistentSchemaHandler<T> inconsistentSchemaHandler,
                                   EventMetadataProvider metadataProvider, Heartbeat customHeartbeat, SchemaNameAdjuster schemaNameAdjuster,
                                   JdbcConnection jdbcConnection) {
        super(connectorConfig, topicSelector, schema, queue, filter, changeEventCreator, inconsistentSchemaHandler, metadataProvider,
                customHeartbeat, schemaNameAdjuster, jdbcConnection);
        this.queue = queue;
        this.logicalDecodingMessageMonitor = new LogicalDecodingMessageMonitor(connectorConfig, this::enqueueLogicalDecodingMessage);
    }

    public void dispatchLogicalDecodingMessage(Partition partition, OffsetContext offset, Long decodeTimestamp,
                                               LogicalDecodingMessage message)
            throws InterruptedException {
        logicalDecodingMessageMonitor.logicalDecodingMessageEvent(partition, offset, decodeTimestamp, message);
    }

    private void enqueueLogicalDecodingMessage(SourceRecord record) throws InterruptedException {
        queue.enqueue(new DataChangeEvent(record));
    }
}
