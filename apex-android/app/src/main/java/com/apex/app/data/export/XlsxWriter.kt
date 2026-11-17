package com.apex.app.data.export

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Una celda: texto o número. */
sealed class Cell {
    data class Str(val v: String) : Cell()
    data class Num(val v: Double) : Cell()
}

/** Una hoja del Excel: nombre + filas (cada fila es una lista de celdas). */
data class Sheet(val name: String, val rows: List<List<Cell>>)

/**
 * Escritor mínimo de archivos .xlsx (OOXML) sin librerías externas.
 * Genera un zip con las partes necesarias para que Excel/Sheets lo abra.
 */
object XlsxWriter {

    fun build(sheets: List<Sheet>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zip ->
            put(zip, "[Content_Types].xml", contentTypes(sheets.size))
            put(zip, "_rels/.rels", rootRels())
            put(zip, "xl/workbook.xml", workbook(sheets))
            put(zip, "xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
            sheets.forEachIndexed { i, s ->
                put(zip, "xl/worksheets/sheet${i + 1}.xml", sheetXml(s))
            }
        }
        return bos.toByteArray()
    }

    private fun put(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun contentTypes(n: Int): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
        sb.append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
        sb.append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        sb.append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
        for (i in 1..n) {
            sb.append("<Override PartName=\"/xl/worksheets/sheet$i.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
        }
        sb.append("</Types>")
        return sb.toString()
    }

    private fun rootRels(): String =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
            "</Relationships>"

    private fun workbook(sheets: List<Sheet>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>")
        sheets.forEachIndexed { i, s ->
            val safe = escape(s.name.take(31))
            sb.append("<sheet name=\"$safe\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>")
        }
        sb.append("</sheets></workbook>")
        return sb.toString()
    }

    private fun workbookRels(n: Int): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
        for (i in 1..n) {
            sb.append("<Relationship Id=\"rId$i\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet$i.xml\"/>")
        }
        sb.append("</Relationships>")
        return sb.toString()
    }

    private fun sheetXml(sheet: Sheet): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>")
        sheet.rows.forEachIndexed { r, row ->
            val rowNum = r + 1
            sb.append("<row r=\"$rowNum\">")
            row.forEachIndexed { c, cell ->
                val ref = "${colName(c)}$rowNum"
                when (cell) {
                    is Cell.Num -> sb.append("<c r=\"$ref\"><v>${cell.v}</v></c>")
                    is Cell.Str -> sb.append("<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${escape(cell.v)}</t></is></c>")
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun colName(index: Int): String {
        var n = index
        val sb = StringBuilder()
        while (true) {
            sb.insert(0, ('A' + n % 26))
            n = n / 26 - 1
            if (n < 0) break
        }
        return sb.toString()
    }

    private fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
