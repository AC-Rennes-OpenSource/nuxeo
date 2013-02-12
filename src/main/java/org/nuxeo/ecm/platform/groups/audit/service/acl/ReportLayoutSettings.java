/*
 * (C) Copyright 2013 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Martin Pernollet
 */

package org.nuxeo.ecm.platform.groups.audit.service.acl;

public class ReportLayoutSettings {
    public enum SpanMode {
        NONE, COLUMN_OVERFLOW_ON_NEXT_SHEETS
    }

    protected int userHeaderHeight;

    protected int userHeaderRotation;

    protected int aclHeaderHeight;

    protected int aclHeaderRotation;

    protected double aclColumnWidth;

    protected double fileTreeColumnWidth;

    protected int defaultRowHeight;

    protected int freezePaneRowSplit;

    protected int aclHeaderCommentColSpan;

    protected int aclHeaderCommentRowSpan;

    protected int aclHeaderFontSize;

    protected int treeLineCursorRowStart;

    protected int splitPaneX;

    protected int splitPaneY;

    protected SpanMode spanMode = SpanMode.NONE;

    protected String logoImageFile;

    protected int zoomRatioNumerator = 1;

    protected int zoomRatioDenominator = 1;

    protected int pageSize = 1000; // -1 for no paging

    public int getUserHeaderHeight() {
        return userHeaderHeight;
    }

    public void setUserHeaderHeight(int userHeaderHeight) {
        this.userHeaderHeight = userHeaderHeight;
    }

    public int getUserHeaderRotation() {
        return userHeaderRotation;
    }

    public void setUserHeaderRotation(int userHeaderRotation) {
        this.userHeaderRotation = userHeaderRotation;
    }

    public int getAclHeaderHeight() {
        return aclHeaderHeight;
    }

    public void setAclHeaderHeight(int aclHeaderHeight) {
        this.aclHeaderHeight = aclHeaderHeight;
    }

    public int getAclHeaderRotation() {
        return aclHeaderRotation;
    }

    public void setAclHeaderRotation(int aclHeaderRotation) {
        this.aclHeaderRotation = aclHeaderRotation;
    }

    public double getAclColumnWidth() {
        return aclColumnWidth;
    }

    public void setAclColumnWidth(double aclColumnWidth) {
        this.aclColumnWidth = aclColumnWidth;
    }

    public double getFileTreeColumnWidth() {
        return fileTreeColumnWidth;
    }

    public void setFileTreeColumnWidth(double fileTreeColumnWidth) {
        this.fileTreeColumnWidth = fileTreeColumnWidth;
    }

    public int getDefaultRowHeight() {
        return defaultRowHeight;
    }

    public void setDefaultRowHeight(int defaultRowHeight) {
        this.defaultRowHeight = defaultRowHeight;
    }

    public int getFreezePaneRowSplit() {
        return freezePaneRowSplit;
    }

    public void setFreezePaneRowSplit(int freezePaneRowSplit) {
        this.freezePaneRowSplit = freezePaneRowSplit;
    }

    public int getAclHeaderCommentColSpan() {
        return aclHeaderCommentColSpan;
    }

    public void setAclHeaderCommentColSpan(int aclHeaderCommentColSpan) {
        this.aclHeaderCommentColSpan = aclHeaderCommentColSpan;
    }

    public int getAclHeaderCommentRowSpan() {
        return aclHeaderCommentRowSpan;
    }

    public void setAclHeaderCommentRowSpan(int aclHeaderCommentRowSpan) {
        this.aclHeaderCommentRowSpan = aclHeaderCommentRowSpan;
    }

    public int getAclHeaderFontSize() {
        return aclHeaderFontSize;
    }

    public void setAclHeaderFontSize(int aclHeaderFontSize) {
        this.aclHeaderFontSize = aclHeaderFontSize;
    }

    public int getTreeLineCursorRowStart() {
        return treeLineCursorRowStart;
    }

    public void setTreeLineCursorRowStart(int treeLineCursorRowStart) {
        this.treeLineCursorRowStart = treeLineCursorRowStart;
    }

    public int getSplitPaneX() {
        return splitPaneX;
    }

    public void setSplitPaneX(int splitPaneX) {
        this.splitPaneX = splitPaneX;
    }

    public int getSplitPaneY() {
        return splitPaneY;
    }

    public void setSplitPaneY(int splitPaneY) {
        this.splitPaneY = splitPaneY;
    }

    public SpanMode getSpanMode() {
        return spanMode;
    }

    public void setSpanMode(SpanMode spanMode) {
        this.spanMode = spanMode;
    }

    public String getLogoImageFile() {
        return logoImageFile;
    }

    public void setLogoImageFile(String logoImageFile) {
        this.logoImageFile = logoImageFile;
    }

    public int getZoomRatioNumerator() {
        return zoomRatioNumerator;
    }

    public void setZoomRatioNumerator(int zoomRatioNumerator) {
        this.zoomRatioNumerator = zoomRatioNumerator;
    }

    public int getZoomRatioDenominator() {
        return zoomRatioDenominator;
    }

    public void setZoomRatioDenominator(int zoomRatioDenominator) {
        this.zoomRatioDenominator = zoomRatioDenominator;
    }

    public int getPageSize() {
        return pageSize;
    }

    /** Page size of the page provider, i.e. input data processing. Use -1 to disable paging */
    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}
