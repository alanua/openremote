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

import com.google.gwt.user.client.ui.UIObject;
import org.openremote.model.interop.Consumer;

public interface AppPanel {

    void setAutoHide(boolean autoHide);

    void setAutoHideOnHistoryEvents(boolean autoHide);

    void setModal(boolean modal);

    void setOpenCloseConsumer(Consumer<Boolean> openCloseConsumer);

    boolean isShowing();

    void show();

    void showRelativeTo(UIObject target);

    void toggle();

    void toggleRelativeTo(UIObject target);

    void showCenter();

    void hide();

    void resize();

}
