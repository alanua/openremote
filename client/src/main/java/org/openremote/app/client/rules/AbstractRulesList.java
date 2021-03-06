/*
 * Copyright 2017, OpenRemote Inc.
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
package org.openremote.app.client.rules;

import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Label;
import org.openremote.app.client.assets.browser.AssetBrowser;
import org.openremote.app.client.i18n.ManagerMessages;
import org.openremote.app.client.style.FormTableStyle;
import org.openremote.app.client.widget.Headline;
import org.openremote.app.client.widget.Hyperlink;
import org.openremote.model.rules.Ruleset;

import java.util.ArrayList;
import java.util.Arrays;

public abstract class AbstractRulesList<P extends RulesList.Presenter<R>, R extends Ruleset>
    extends Composite
    implements RulesList<P, R> {

    @UiField
    public ManagerMessages managerMessages;

    @UiField
    public HTMLPanel sidebarContainer;

    @UiField
    public HTMLPanel mainContent;

    @UiField
    public Headline headline;

    @UiField
    public Hyperlink createLink;

    @UiField
    public Label noRulesetsLabel;

    @UiField
    public RulesetTable.Style tableStyle;

    final protected RulesetTable<R> table;
    final AssetBrowser assetBrowser;
    P presenter;

    public AbstractRulesList(AssetBrowser assetBrowser, FormTableStyle formTableStyle) {
        this.assetBrowser = assetBrowser;

        initComposite();

        table = new RulesetTable<>(managerMessages, tableStyle, formTableStyle);
        table.getSelectionModel().addSelectionChangeHandler(event -> {
                R selected;
                if ((selected = table.getSelectedObject()) != null
                    && presenter != null) {
                    presenter.onRulesetSelected(selected);
                }
            }
        );
        table.setVisible(false);
        mainContent.add(table);
    }

    abstract protected void initComposite();

    @Override
    public void setPresenter(P presenter) {
        this.presenter = presenter;
        if (presenter != null) {
            assetBrowser.asWidget().removeFromParent();
            sidebarContainer.add(assetBrowser.asWidget());
        } else {
            onPresenterReset();
        }
    }

    protected void onPresenterReset() {
        sidebarContainer.clear();
        headline.setText(managerMessages.loadingDotdotdot());
        setCreateRulesetHistoryToken("");
        noRulesetsLabel.setVisible(false);
        table.setVisible(false);
        table.setRowData(new ArrayList<>());
        table.flush();
    }

    @Override
    public void setCreateRulesetHistoryToken(String token) {
        createLink.setTargetHistoryToken(token);
        createLink.setVisible(token != null && token.length() > 0);
    }

    @Override
    public void setRulesets(R[] rulesets) {
        noRulesetsLabel.setVisible(rulesets.length == 0);
        table.setVisible(rulesets.length > 0);
        table.setRowData(Arrays.asList(rulesets));
        table.flush();
    }
}
