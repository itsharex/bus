/*********************************************************************************
 *                                                                               *
 * The MIT License (MIT)                                                         *
 *                                                                               *
 * Copyright (c) 2015-2021 aoju.org and other contributors.                      *
 *                                                                               *
 * Permission is hereby granted, free of charge, to any person obtaining a copy  *
 * of this software and associated documentation files (the "Software"), to deal *
 * in the Software without restriction, including without limitation the rights  *
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell     *
 * copies of the Software, and to permit persons to whom the Software is         *
 * furnished to do so, subject to the following conditions:                      *
 *                                                                               *
 * The above copyright notice and this permission notice shall be included in    *
 * all copies or substantial portions of the Software.                           *
 *                                                                               *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR    *
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,      *
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE   *
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER        *
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, *
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN     *
 * THE SOFTWARE.                                                                 *
 *                                                                               *
 ********************************************************************************/
package org.aoju.bus.office.support.excel;

import org.aoju.bus.core.toolkit.StringKit;
import org.aoju.bus.office.support.excel.cell.CellEditor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Excel中的行{@link Row}封装工具类
 *
 * @author Kimi Liu
 * @version 6.2.9
 * @since JDK 1.8+
 */
public class RowKit {
    /**
     * 获取已有行或创建新行
     *
     * @param sheet    Excel表
     * @param rowIndex 行号
     * @return {@link Row}
     */
    public static Row getOrCreateRow(Sheet sheet, int rowIndex) {
        Row row = sheet.getRow(rowIndex);
        if (null == row) {
            row = sheet.createRow(rowIndex);
        }
        return row;
    }

    /**
     * 读取一行
     *
     * @param row        行
     * @param cellEditor 单元格编辑器
     * @return 单元格值列表
     */
    public static List<Object> readRow(Row row, CellEditor cellEditor) {
        return readRow(row, 0, Short.MAX_VALUE, cellEditor);
    }

    /**
     * 读取一行
     *
     * @param row                 行
     * @param startCellNumInclude 起始单元格号，0开始（包含）
     * @param endCellNumInclude   结束单元格号，0开始（包含）
     * @param cellEditor          单元格编辑器
     * @return 单元格值列表
     */
    public static List<Object> readRow(Row row, int startCellNumInclude, int endCellNumInclude, CellEditor cellEditor) {
        if (null == row) {
            return new ArrayList<>(0);
        }
        final short rowLength = row.getLastCellNum();
        if (rowLength < 0) {
            return Collections.emptyList();
        }

        final int size = Math.min(endCellNumInclude + 1, rowLength);
        final List<Object> cellValues = new ArrayList<>(size);
        Object cellValue;
        boolean isAllNull = true;
        for (int i = startCellNumInclude; i < size; i++) {
            cellValue = CellKit.getCellValue(CellKit.getCell(row, i), cellEditor);
            isAllNull &= StringKit.emptyIfString(cellValue);
            cellValues.add(cellValue);
        }

        if (isAllNull) {
            // 如果每个元素都为空，则定义为空行
            return Collections.emptyList();
        }
        return cellValues;
    }

    /**
     * 写一行数据，无样式，非标题
     *
     * @param row     行
     * @param rowData 一行的数据
     */
    public static void writeRow(Row row, Iterable<?> rowData) {
        writeRow(row, rowData, null, false);
    }

    /**
     * 写一行数据
     *
     * @param row      行
     * @param rowData  一行的数据
     * @param styleSet 单元格样式集,包括日期等样式
     * @param isHeader 是否为标题行
     */
    public static void writeRow(Row row, Iterable<?> rowData, StyleSet styleSet, boolean isHeader) {
        int i = 0;
        Cell cell;
        for (Object value : rowData) {
            cell = row.createCell(i);
            CellKit.setCellValue(cell, value, styleSet, isHeader);
            i++;
        }
    }

    /**
     * 插入行
     *
     * @param sheet        工作表
     * @param startRow     插入的起始行
     * @param insertNumber 插入的行数
     */
    public static void insertRow(Sheet sheet, int startRow, int insertNumber) {
        if (insertNumber <= 0) {
            return;
        }
        // 插入位置的行，如果插入的行不存在则创建新行
        Row sourceRow = Optional.ofNullable(sheet.getRow(startRow)).orElseGet(() -> sheet.createRow(insertNumber));
        // 从插入行开始到最后一行向下移动
        sheet.shiftRows(startRow, sheet.getLastRowNum(), insertNumber, true, false);

        // 填充移动后留下的空行
        IntStream.range(startRow, startRow + insertNumber).forEachOrdered(i -> {
            Row row = sheet.createRow(i);
            row.setHeightInPoints(sourceRow.getHeightInPoints());
            short lastCellNum = sourceRow.getLastCellNum();
            IntStream.range(0, lastCellNum).forEachOrdered(j -> {
                Cell cell = row.createCell(j);
                cell.setCellStyle(sourceRow.getCell(j).getCellStyle());
            });
        });
    }

    /**
     * 从工作表中删除指定的行
     * 修复sheet.shiftRows删除行时会拆分合并的单元格的问题
     *
     * @param row 需要删除的行
     */
    public static void removeRow(Row row) {
        if (null == row) {
            return;
        }
        int rowIndex = row.getRowNum();
        Sheet sheet = row.getSheet();
        int lastRow = sheet.getLastRowNum();
        if (rowIndex >= 0 && rowIndex < lastRow) {
            List<CellRangeAddress> updateMergedRegions = new ArrayList<>();
            // 找出需要调整的合并单元格
            IntStream.range(0, sheet.getNumMergedRegions())
                    .forEach(i -> {
                        CellRangeAddress mr = sheet.getMergedRegion(i);
                        if (!mr.containsRow(rowIndex)) {
                            return;
                        }
                        // 缩减以后变成单个单元格则删除合并单元格
                        if (mr.getFirstRow() == mr.getLastRow() - 1 && mr.getFirstColumn() == mr.getLastColumn()) {
                            return;
                        }
                        updateMergedRegions.add(mr);
                    });

            // 将行上移
            sheet.shiftRows(rowIndex + 1, lastRow, -1);

            // 找出删除行所在的合并单元格
            List<Integer> removeMergedRegions = IntStream.range(0, sheet.getNumMergedRegions())
                    .filter(i -> updateMergedRegions.stream().
                            anyMatch(umr -> CellRangeUtil.contains(umr, sheet.getMergedRegion(i))))
                    .boxed()
                    .collect(Collectors.toList());

            sheet.removeMergedRegions(removeMergedRegions);
            updateMergedRegions.forEach(mr -> {
                mr.setLastRow(mr.getLastRow() - 1);
                sheet.addMergedRegion(mr);
            });
            sheet.validateMergedRegions();
        }
        if (rowIndex == lastRow) {
            Row removingRow = sheet.getRow(rowIndex);
            if (null != removingRow) {
                sheet.removeRow(removingRow);
            }
        }
    }

}
