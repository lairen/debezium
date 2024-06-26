/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.connector.mariadb.antlr.listener;

import java.util.List;

import org.antlr.v4.runtime.tree.ParseTreeListener;

import io.debezium.antlr.AntlrDdlParser;
import io.debezium.connector.mariadb.antlr.MariaDbAntlrDdlParser;
import io.debezium.ddl.parser.mariadb.generated.MariaDBParser;
import io.debezium.ddl.parser.mariadb.generated.MariaDBParserBaseListener;
import io.debezium.relational.Column;
import io.debezium.relational.TableEditor;
import io.debezium.relational.TableId;
import io.debezium.text.ParsingException;

/**
 * Parser listener that parses ALTER VIEW statements.
 *
 * @author Chris Cranford
 */
public class AlterViewParserListener extends MariaDBParserBaseListener {

    private final MariaDbAntlrDdlParser parser;
    private final List<ParseTreeListener> listeners;

    private TableEditor tableEditor;
    private ViewSelectedColumnsParserListener selectColumnsListener;

    public AlterViewParserListener(MariaDbAntlrDdlParser parser, List<ParseTreeListener> listeners) {
        this.parser = parser;
        this.listeners = listeners;
    }

    @Override
    public void enterAlterView(MariaDBParser.AlterViewContext ctx) {
        if (!parser.skipViews()) {
            TableId tableId = parser.parseQualifiedTableId(ctx.fullId());

            tableEditor = parser.databaseTables().editTable(tableId);
            if (tableEditor == null) {
                throw new ParsingException(null, "Trying to alter view " + tableId.toString()
                        + ", which does not exist. Query:" + AntlrDdlParser.getText(ctx));
            }
            // alter view will override existing columns for a new one
            tableEditor.columnNames().forEach(tableEditor::removeColumn);
            // create new columns just with specified name for now
            if (ctx.uidList() != null) {
                ctx.uidList().uid().stream().map(parser::parseName).forEach(columnName -> {
                    tableEditor.addColumn(Column.editor().name(columnName).create());
                });
            }
            selectColumnsListener = new ViewSelectedColumnsParserListener(tableEditor, parser);
            listeners.add(selectColumnsListener);
        }
        super.enterAlterView(ctx);
    }

    @Override
    public void exitAlterView(MariaDBParser.AlterViewContext ctx) {
        parser.runIfNotNull(() -> {
            tableEditor.addColumns(selectColumnsListener.getSelectedColumns());
            // Make sure that the table's character set has been set ...
            if (!tableEditor.hasDefaultCharsetName()) {
                tableEditor.setDefaultCharsetName(parser.currentDatabaseCharset());
            }
            parser.databaseTables().overwriteTable(tableEditor.create());
            listeners.remove(selectColumnsListener);
        }, tableEditor);
        // signal view even if it was skipped
        parser.signalAlterView(parser.parseQualifiedTableId(ctx.fullId()), null, ctx);
        super.exitAlterView(ctx);
    }
}
