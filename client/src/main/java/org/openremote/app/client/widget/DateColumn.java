/*
 * Copyright 2016, OpenRemote Inc.
 *
 * See the CONTRIBUTORS.txt file in the distribution for a
 * full listing of individual contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.openremote.app.client.widget;

import com.google.gwt.cell.client.Cell;
import com.google.gwt.i18n.client.DateTimeFormat;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.cellview.client.TextColumn;

import java.util.Date;

public abstract class DateColumn<T> extends TextColumn<T> {

    DateTimeFormat fmt = DateTimeFormat.getFormat(DateTimeFormat.PredefinedFormat.DATE_MEDIUM);

    public void setDateFormat(String pattern) {
        fmt = DateTimeFormat.getFormat(pattern);
    }

    @Override
    public void render(Cell.Context context, T object, SafeHtmlBuilder sb) {
        sb.appendHtmlConstant("<span style=\"white-space:nowrap;\">");
        super.render(context, object, sb);
        sb.appendHtmlConstant("</span>");
    }

    @Override
    public String getValue(T object) {
        return fmt.format(getDate(object));
    }

    abstract protected Date getDate(T object);

}
