/*******************************************************************************
 * Copyright (c) 2012, 2015 Original authors and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Original authors and others - initial API and implementation
 *     Dirk Fauth <dirk.fauth@googlemail.com> - Bug 457304
 ******************************************************************************/
package org.eclipse.nebula.widgets.nattable.extension.poi;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.eclipse.nebula.widgets.nattable.config.CellConfigAttributes;
import org.eclipse.nebula.widgets.nattable.config.IConfigRegistry;
import org.eclipse.nebula.widgets.nattable.export.ExportConfigAttributes;
import org.eclipse.nebula.widgets.nattable.export.ILayerExporter;
import org.eclipse.nebula.widgets.nattable.export.IOutputStreamProvider;
import org.eclipse.nebula.widgets.nattable.formula.FormulaParser;
import org.eclipse.nebula.widgets.nattable.layer.cell.ILayerCell;
import org.eclipse.nebula.widgets.nattable.painter.cell.CellPainterWrapper;
import org.eclipse.nebula.widgets.nattable.painter.cell.ICellPainter;
import org.eclipse.nebula.widgets.nattable.painter.cell.VerticalTextPainter;
import org.eclipse.nebula.widgets.nattable.painter.cell.decorator.CellPainterDecorator;
import org.eclipse.nebula.widgets.nattable.style.CellStyleAttributes;
import org.eclipse.nebula.widgets.nattable.style.CellStyleProxy;
import org.eclipse.nebula.widgets.nattable.style.DisplayMode;
import org.eclipse.nebula.widgets.nattable.style.HorizontalAlignmentEnum;
import org.eclipse.nebula.widgets.nattable.style.VerticalAlignmentEnum;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.widgets.Shell;

public abstract class PoiExcelExporter implements ILayerExporter {

    private final IOutputStreamProvider outputStreamProvider;

    private Map<ExcelCellStyleAttributes, CellStyle> xlCellStyles;

    protected Workbook xlWorkbook;
    protected int sheetNumber;
    protected Sheet xlSheet;
    protected Row xlRow;

    private boolean applyBackgroundColor = true;
    private boolean applyVerticalTextConfiguration = false;

    private String sheetname;

    private FormulaParser formulaParser;

    public PoiExcelExporter(IOutputStreamProvider outputStreamProvider) {
        this.outputStreamProvider = outputStreamProvider;
    }

    @Override
    public OutputStream getOutputStream(Shell shell) {
        return this.outputStreamProvider.getOutputStream(shell);
    }

    @Override
    public void exportBegin(OutputStream outputStream) throws IOException {
        this.xlCellStyles = new HashMap<ExcelCellStyleAttributes, CellStyle>();
        this.xlWorkbook = createWorkbook();
    }

    @Override
    public void exportEnd(OutputStream outputStream) throws IOException {
        this.xlWorkbook.write(outputStream);

        this.xlCellStyles = null;
        this.xlWorkbook = null;
        this.sheetNumber = 0;
        this.xlSheet = null;
        this.xlRow = null;
    }

    @Override
    public void exportLayerBegin(OutputStream outputStream, String layerName) throws IOException {
        this.sheetNumber++;
        if (layerName == null || layerName.length() == 0) {
            layerName = this.sheetname;

            if (layerName == null || layerName.length() == 0) {
                layerName = "Sheet" + this.sheetNumber; //$NON-NLS-1$
            }
        }
        this.xlSheet = this.xlWorkbook.createSheet(layerName);
    }

    @Override
    public void exportLayerEnd(OutputStream outputStream, String layerName) throws IOException {}

    @Override
    public void exportRowBegin(OutputStream outputStream, int rowPosition) throws IOException {
        this.xlRow = this.xlSheet.createRow(rowPosition);
    }

    @Override
    public void exportRowEnd(OutputStream outputStream, int rowPosition) throws IOException {}

    @Override
    public void exportCell(
            OutputStream outputStream,
            Object exportDisplayValue,
            ILayerCell cell,
            IConfigRegistry configRegistry) throws IOException {

        int columnPosition = cell.getColumnPosition();
        int rowPosition = cell.getRowPosition();

        if (columnPosition != cell.getOriginColumnPosition()
                || rowPosition != cell.getOriginRowPosition()) {
            return;
        }

        Cell xlCell = this.xlRow.createCell(columnPosition);

        int columnSpan = cell.getColumnSpan();
        int rowSpan = cell.getRowSpan();
        if (columnSpan > 1 || rowSpan > 1) {
            int lastRow = rowPosition + rowSpan - 1;
            int lastColumn = columnPosition + columnSpan - 1;
            this.xlSheet.addMergedRegion(new CellRangeAddress(rowPosition, lastRow, columnPosition, lastColumn));
        }

        CellStyleProxy cellStyle = new CellStyleProxy(
                configRegistry,
                DisplayMode.NORMAL,
                cell.getConfigLabels().getLabels());
        Color fg = cellStyle.getAttributeValue(CellStyleAttributes.FOREGROUND_COLOR);
        Color bg = cellStyle.getAttributeValue(CellStyleAttributes.BACKGROUND_COLOR);
        org.eclipse.swt.graphics.Font font = cellStyle.getAttributeValue(CellStyleAttributes.FONT);
        FontData fontData = font.getFontData()[0];
        String dataFormat = null;

        int hAlign = HorizontalAlignmentEnum.getSWTStyle(cellStyle);
        int vAlign = VerticalAlignmentEnum.getSWTStyle(cellStyle);

        boolean vertical = this.applyVerticalTextConfiguration ? isVertical(configRegistry.getConfigAttribute(
                CellConfigAttributes.CELL_PAINTER,
                DisplayMode.NORMAL,
                cell.getConfigLabels().getLabels()))
                : false;

        if (exportDisplayValue == null)
            exportDisplayValue = ""; //$NON-NLS-1$

        if (exportDisplayValue instanceof Boolean) {
            xlCell.setCellValue((Boolean) exportDisplayValue);
        } else if (exportDisplayValue instanceof Calendar) {
            dataFormat = getDataFormatString(cell, configRegistry);
            xlCell.setCellValue((Calendar) exportDisplayValue);
        } else if (exportDisplayValue instanceof Date) {
            dataFormat = getDataFormatString(cell, configRegistry);
            xlCell.setCellValue((Date) exportDisplayValue);
        } else if (exportDisplayValue instanceof Number) {
            xlCell.setCellValue(((Number) exportDisplayValue).doubleValue());
        } else if (this.formulaParser != null) {
            // formula export is enabled, so we perform checks on the cell
            // values
            String cellValue = exportDisplayValue.toString();
            if (this.formulaParser.isFunction(cellValue)) {
                xlCell.setCellFormula(this.formulaParser.getFunctionOnly(cellValue));
            }
            else if (this.formulaParser.isNumber(cellValue)) {
                xlCell.setCellValue(new BigDecimal(cellValue).doubleValue());
            }
            else {
                xlCell.setCellValue(exportDisplayValue.toString());
            }
        } else {
            xlCell.setCellValue(exportDisplayValue.toString());
        }

        CellStyle xlCellStyle = getExcelCellStyle(fg, bg, fontData, dataFormat, hAlign, vAlign, vertical);
        xlCell.setCellStyle(xlCellStyle);
    }

    private boolean isVertical(ICellPainter cellPainter) {
        if (cellPainter instanceof VerticalTextPainter) {
            return true;
        } else if (cellPainter instanceof CellPainterWrapper) {
            return isVertical(((CellPainterWrapper) cellPainter).getWrappedPainter());
        } else if (cellPainter instanceof CellPainterDecorator) {
            return (isVertical(((CellPainterDecorator) cellPainter).getBaseCellPainter())
                    || isVertical(((CellPainterDecorator) cellPainter).getDecoratorCellPainter()));
        }
        return false;
    }

    private CellStyle getExcelCellStyle(Color fg, Color bg, FontData fontData, String dataFormat, int hAlign, int vAlign, boolean vertical) {

        CellStyle xlCellStyle = this.xlCellStyles.get(new ExcelCellStyleAttributes(fg, bg, fontData, dataFormat, hAlign, vAlign, vertical));

        if (xlCellStyle == null) {
            xlCellStyle = this.xlWorkbook.createCellStyle();

            if (this.applyBackgroundColor) {
                // Note: xl fill foreground = background
                setFillForegroundColor(xlCellStyle, bg);
                xlCellStyle.setFillPattern(CellStyle.SOLID_FOREGROUND);
            }

            Font xlFont = this.xlWorkbook.createFont();
            setFontColor(xlFont, fg);
            xlFont.setFontName(fontData.getName());
            xlFont.setFontHeightInPoints((short) fontData.getHeight());
            xlCellStyle.setFont(xlFont);

            if (vertical)
                xlCellStyle.setRotation((short) 90);

            switch (hAlign) {
                case SWT.CENTER:
                    xlCellStyle.setAlignment(CellStyle.ALIGN_CENTER);
                    break;
                case SWT.LEFT:
                    xlCellStyle.setAlignment(CellStyle.ALIGN_LEFT);
                    break;
                case SWT.RIGHT:
                    xlCellStyle.setAlignment(CellStyle.ALIGN_RIGHT);
                    break;
            }
            switch (vAlign) {
                case SWT.TOP:
                    xlCellStyle.setVerticalAlignment(CellStyle.VERTICAL_TOP);
                    break;
                case SWT.CENTER:
                    xlCellStyle.setVerticalAlignment(CellStyle.VERTICAL_CENTER);
                    break;
                case SWT.BOTTOM:
                    xlCellStyle.setVerticalAlignment(CellStyle.VERTICAL_BOTTOM);
                    break;
            }

            if (dataFormat != null) {
                CreationHelper createHelper = this.xlWorkbook.getCreationHelper();
                xlCellStyle.setDataFormat(createHelper.createDataFormat().getFormat(dataFormat));
            }

            this.xlCellStyles.put(
                    new ExcelCellStyleAttributes(fg, bg, fontData, dataFormat, hAlign, vAlign, vertical), xlCellStyle);
        }
        return xlCellStyle;
    }

    /**
     *
     * @param cell
     *            The cell for which the date format needs to be determined.
     * @param configRegistry
     *            The ConfigRegistry needed to retrieve the configuration.
     * @return The date format that should be used to format Date or Calendar
     *         values in the export.
     */
    protected String getDataFormatString(ILayerCell cell, IConfigRegistry configRegistry) {
        String dataFormat = configRegistry.getConfigAttribute(
                ExportConfigAttributes.DATE_FORMAT,
                DisplayMode.NORMAL,
                cell.getConfigLabels().getLabels());
        if (dataFormat == null) {
            dataFormat = "m/d/yy h:mm"; //$NON-NLS-1$
        }
        return dataFormat;
    }

    /**
     *
     * @param applyBackgroundColor
     *            <code>true</code> to apply the background color set in the
     *            NatTable to the exported Excel. This also includes white
     *            background and header background color. <code>false</code> if
     *            the background color should not be set on export.
     */
    public void setApplyBackgroundColor(boolean applyBackgroundColor) {
        this.applyBackgroundColor = applyBackgroundColor;
    }

    /**
     * Configure this exporter whether it should check for vertical text
     * configuration in NatTable and apply the corresponding rotation style
     * attribute in the export, or not.
     * <p>
     * Note: As showing text vertically in NatTable is not a style information
     * but a configured via painter implementation, the check whether text is
     * showed vertically needs to be done via reflection. Therefore setting this
     * value to <code>true</code> could cause performance issues. As vertical
     * text is not the default case and the effect on performance might be
     * negative, the default value for this configuration is <code>false</code>.
     * If vertical text (e.g. column headers) should also be exported
     * vertically, you need to set this value to <code>true</code>.
     *
     * @param inspectVertical
     *            <code>true</code> to configure this exporter to check for
     *            vertical text configuration and apply the rotation style for
     *            the export, <code>false</code> to always use the regular text
     *            direction, regardless of vertical rendered text in NatTable.
     */
    public void setApplyVerticalTextConfiguration(boolean inspectVertical) {
        this.applyVerticalTextConfiguration = inspectVertical;
    }

    protected abstract Workbook createWorkbook();

    protected abstract void setFillForegroundColor(CellStyle xlCellStyle, Color swtColor);

    protected abstract void setFontColor(Font xlFont, Color swtColor);

    @Override
    public Object getResult() {
        return this.outputStreamProvider.getResult();
    }

    /**
     *
     * @param sheetname
     *            The name that should be set as sheet name in the resulting
     *            Excel file. Setting this value to <code>null</code> will
     *            result in a sheet name following the pattern <i>Sheet +
     *            &lt;sheet number&gt;</i>
     */
    public void setSheetname(String sheetname) {
        this.sheetname = sheetname;
    }

    /**
     * Configure the {@link FormulaParser} that should be used to determine
     * whether formulas should be exported or not. If <code>null</code> is set,
     * formulas and cell values of type string will be simply exported as
     * string. If a valid {@link FormulaParser} is set, cell values will get
     * inspected so that number values are converted to numbers and formulas
     * will be exported as formulas.
     *
     * @param formulaParser
     *            The {@link FormulaParser} that should be used to determine
     *            whether cell values should be interpreted as formulas or
     *            <code>null</code> to disable formula export handling.
     *
     * @since 1.4
     */
    public void setFormulaParser(FormulaParser formulaParser) {
        this.formulaParser = formulaParser;
    }

}
