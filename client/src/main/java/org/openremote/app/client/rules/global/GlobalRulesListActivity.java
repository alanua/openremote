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
package org.openremote.app.client.rules.global;

import org.openremote.app.client.Environment;
import org.openremote.app.client.assets.AssetBrowsingActivity;
import org.openremote.app.client.assets.browser.AssetBrowser;
import org.openremote.app.client.assets.browser.AssetBrowserSelection;
import org.openremote.app.client.mvp.AcceptsView;
import org.openremote.app.client.mvp.AppActivity;
import org.openremote.app.client.rules.RulesModule;
import org.openremote.model.event.bus.EventBus;
import org.openremote.model.event.bus.EventRegistration;
import org.openremote.model.rules.GlobalRuleset;
import org.openremote.model.rules.RulesetResource;

import javax.inject.Inject;
import java.util.Collection;

public class GlobalRulesListActivity
    extends AssetBrowsingActivity<GlobalRulesListPlace>
    implements GlobalRulesList.Presenter {

    final GlobalRulesList view;
    final GlobalRulesetArrayMapper globalRulesetArrayMapper;
    final RulesetResource rulesetResource;

    @Inject
    public GlobalRulesListActivity(Environment environment,
                                   AssetBrowser.Presenter assetBrowserPresenter,
                                   GlobalRulesList view,
                                   GlobalRulesetArrayMapper globalRulesetArrayMapper,
                                   RulesetResource rulesetResource) {
        super(environment, assetBrowserPresenter);
        this.view = view;
        this.globalRulesetArrayMapper = globalRulesetArrayMapper;
        this.rulesetResource = rulesetResource;
    }

    @Override
    protected AppActivity<GlobalRulesListPlace> init(GlobalRulesListPlace place) {
        return this;
    }

    @Override
    public void start(AcceptsView container, EventBus eventBus, Collection<EventRegistration> registrations) {
        view.setPresenter(this);
        container.setWidget(view.asWidget());

        registrations.add(eventBus.register(
            AssetBrowserSelection.class,
            RulesModule.createDefaultNavigationListener(environment)
        ));

        environment.getApp().getRequests().sendAndReturn(
            globalRulesetArrayMapper,
            rulesetResource::getGlobalRulesets,
            200,
            view::setRulesets
        );

        view.setCreateRulesetHistoryToken(
            environment.getPlaceHistoryMapper().getToken(new GlobalRulesEditorPlace())
        );

        assetBrowserPresenter.clearSelection();
    }

    @Override
    public void onStop() {
        super.onStop();
        view.setPresenter(null);
    }

    @Override
    public void onRulesetSelected(GlobalRuleset ruleset) {
        environment.getPlaceController().goTo(new GlobalRulesEditorPlace(ruleset.getId()));
    }
}
