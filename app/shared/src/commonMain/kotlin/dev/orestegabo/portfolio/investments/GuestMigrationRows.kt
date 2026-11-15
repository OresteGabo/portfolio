package dev.orestegabo.portfolio.investments

/** Review projection only: preserve source references/order; validation and transfer remain with GuestMigration. */
internal fun migrationRows(snapshot: GuestPortfolio): List<MigrationRow> {
    val records = snapshot.portfolio
    val bondNamesById = buildMap<String, String> {
        for (purchase in records.purchases) {
            val id = purchase.text("id")
            // Match the original firstOrNull lookup even if a legacy snapshot repeats an ID.
            if (id !in this) put(id, purchase.text("bondName"))
        }
    }
    return buildList {
        for (purchase in records.purchases) {
            add(MigrationRow(purchase.text("id"), "bond", purchase.text("bondName"), purchase))
        }
        for (holding in records.holdings) {
            add(MigrationRow(holding.text("id"), "equity", holding.text("securityName"), holding))
        }
        for (receipt in snapshot.receipts) {
            val parentId = receipt.text("purchaseId")
            val bondName = bondNamesById[parentId].orEmpty()
            add(MigrationRow(receipt.text("id"), "receipt", "$bondName · ${receipt.text("dueDate")}", receipt, parentId))
        }
        for (document in records.documents) {
            add(MigrationRow(document.text("id"), "document", document.text("fileName"), document))
        }
        for (entry in snapshot.metadata.rows("cashLedger")) {
            add(MigrationRow("guest:cash:${entry.text("id")}", "cash", "${entry.text("kind")} · ${displayDate(entry.text("date"))}",
                entry, entry.text("holdingId").ifBlank { null }))
        }
    }
}

/** Local dates win; history supplies only the first matching item's text, even when it is blank. */
internal fun migrationDocumentDates(rows: List<MigrationRow>, previousItems: List<JsonRecord>): Map<String, String> {
    val previousDateById = buildMap<String, String> {
        for (item in previousItems) {
            val sourceId = item.text("sourceId")
            if (sourceId !in this) put(sourceId, item.text("documentDate"))
        }
    }
    return buildMap {
        for (row in rows) {
            if (row.kind == "document") {
                put(row.id, row.record.text("documentDate").ifBlank { previousDateById[row.id].orEmpty() })
            }
        }
    }
}
