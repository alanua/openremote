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
package org.openremote.app.client.app;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.AcceptsOneWidget;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import org.openremote.app.client.i18n.ManagerMessages;
import org.openremote.app.client.style.WidgetStyle;

import javax.inject.Inject;
import java.util.logging.Logger;

public class AppViewImpl extends Composite implements AppView {

    private static final Logger LOG = Logger.getLogger(AppViewImpl.class.getName());

    interface UI extends UiBinder<FlowPanel, AppViewImpl> {
    }

    private UI ui = GWT.create(UI.class);

    @UiField
    ManagerMessages managerMessages;

    @UiField
    WidgetStyle widgetStyle;

    @UiField
    SimplePanel header;

    @UiField
    SimplePanel content;

    @UiField
    SimplePanel footer;

    Presenter presenter;

    @Inject
    public AppViewImpl() {
        initWidget(ui.createAndBindUi(this));
    }

    @Override
    public void setPresenter(Presenter presenter) {
        this.presenter = presenter;
    }

    @Override
    public AcceptsOneWidget getHeaderPanel() {
        return header;
    }

    @Override
    public AcceptsOneWidget getContentPanel() {
        return content;
    }

    @Override
    public AcceptsOneWidget getFooterPanel() {
        return footer;
    }
}
