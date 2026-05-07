/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.zzih.rudder.service.download;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFRow;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

/**
 * SXSSF 流式 .xlsx 写出。内存里只保留滑动窗口 ({@link SXSSFWorkbook} 默认 100 行),
 * 老行被刷到磁盘临时文件,所以总行数大不爆内存。
 *
 * <p>表头:加粗 + 浅灰底 + 全边框,数据区冻结首行。
 *
 * <p>数字 / 布尔走类型化单元格(Excel 里看到的就是数字 / TRUE/FALSE),其余 toString 成文本。
 * 列宽用固定值(无法 autosize 流式 sheet 的已 flush 行),用户在 Excel 里可手拖。
 *
 * <p>>1,048,575 数据行(Excel 单 sheet 上限 1,048,576 含表头)自动开新 sheet。
 */
public class ExcelDownloadWriter implements ResultDownloadWriter {

    /** Excel 单 sheet 行上限是 1,048,576。预留 1 行给表头。 */
    private static final int MAX_ROWS_PER_SHEET = 1_048_575;
    /** SXSSF 滑动窗口大小;超过这个数老行被 flush 到磁盘临时文件。 */
    private static final int WINDOW_SIZE = 100;
    /** 列宽:1/256 字符宽。20 字符 ≈ 5120,留点余量取 6000。 */
    private static final int COLUMN_WIDTH = 6000;

    private final OutputStream out;
    private final SXSSFWorkbook workbook;
    private final CellStyle headerStyle;
    private final CellStyle dataStyle;

    private List<String> columns;
    private SXSSFSheet currentSheet;
    private int sheetIndex = 0;
    private int rowInSheet = 0;

    public ExcelDownloadWriter(OutputStream out) {
        this.out = out;
        this.workbook = new SXSSFWorkbook(WINDOW_SIZE);
        this.workbook.setCompressTempFiles(true);
        this.headerStyle = buildHeaderStyle();
        this.dataStyle = buildDataStyle();
    }

    @Override
    public void writeHeader(List<String> columns) throws IOException {
        this.columns = columns;
        startNewSheet();
    }

    @Override
    public void writeRow(Map<String, Object> row) throws IOException {
        if (rowInSheet >= MAX_ROWS_PER_SHEET) {
            startNewSheet();
        }
        SXSSFRow r = currentSheet.createRow(rowInSheet + 1);
        for (int i = 0; i < columns.size(); i++) {
            Cell cell = r.createCell(i);
            cell.setCellStyle(dataStyle);
            Object v = row.get(columns.get(i));
            setCellValue(cell, v);
        }
        rowInSheet++;
    }

    @Override
    public void close() throws IOException {
        try {
            workbook.write(out);
        } finally {
            workbook.dispose();
            workbook.close();
        }
    }

    private void startNewSheet() {
        sheetIndex++;
        rowInSheet = 0;
        currentSheet = workbook.createSheet(sheetIndex == 1 ? "Result" : "Result_" + sheetIndex);
        currentSheet.createFreezePane(0, 1);
        Row header = currentSheet.createRow(0);
        for (int i = 0; i < columns.size(); i++) {
            Cell c = header.createCell(i);
            c.setCellValue(columns.get(i));
            c.setCellStyle(headerStyle);
            currentSheet.setColumnWidth(i, COLUMN_WIDTH);
        }
    }

    private CellStyle buildHeaderStyle() {
        CellStyle s = workbook.createCellStyle();
        Font f = workbook.createFont();
        f.setBold(true);
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        return s;
    }

    private CellStyle buildDataStyle() {
        CellStyle s = workbook.createCellStyle();
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        return s;
    }

    private static void setCellValue(Cell cell, Object v) {
        if (v == null) {
            cell.setBlank();
        } else if (v instanceof Number n) {
            cell.setCellValue(n.doubleValue());
        } else if (v instanceof Boolean b) {
            cell.setCellValue(b);
        } else {
            cell.setCellValue(v.toString());
        }
    }
}
