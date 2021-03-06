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
package org.openremote.app.client.rules.tenant;

import org.openremote.app.client.Environment;
import org.openremote.app.client.TenantMapper;
import org.openremote.app.client.assets.AssetBrowsingActivity;
import org.openremote.app.client.assets.browser.AssetBrowser;
import org.openremote.app.client.assets.browser.AssetBrowserSelection;
import org.openremote.app.client.mvp.AcceptsView;
import org.openremote.app.client.mvp.AppActivity;
import org.openremote.app.client.rules.RulesModule;
import org.openremote.model.event.bus.EventBus;
import org.openremote.model.event.bus.EventRegistration;
import org.openremote.model.rules.RulesetResource;
import org.openremote.model.rules.TenantRuleset;
import org.openremote.model.security.TenantResource;

import javax.inject.Inject;
import java.util.Collection;

public class TenantRulesListActivity
    extends AssetBrowsingActivity<TenantRulesListPlace>
    implements TenantRulesList.Presenter {

    final TenantRulesList view;
    final TenantMapper tenantMapper;
    final TenantResource tenantResource;
    final TenantRulesetArrayMapper tenantRulesetArrayMapper;
    final RulesetResource rulesetResource;

    String realmId;

    @Inject
    public TenantRulesListActivity(Environment environment,
                                   AssetBrowser.Presenter assetBrowserPresenter,
                                   TenantRulesList view,
                                   TenantMapper tenantMapper,
                                   TenantResource tenantResource,
                                   TenantRulesetArrayMapper tenantRulesetArrayMapper,
                                   RulesetResource rulesetResource) {
        super(environment, assetBrowserPresenter);
        this.view = view;
        this.tenantMapper = tenantMapper;
        this.tenantResource = tenantResource;
        this.tenantRulesetArrayMapper = tenantRulesetArrayMapper;
        this.rulesetResource = rulesetResource;
    }

    @Override
    protected AppActivity<TenantRulesListPlace> init(TenantRulesListPlace place) {
        this.realmId = place.getRealmId();
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

        if (realmId != null) {
            assetBrowserPresenter.selectTenant(realmId);

            environment.getApp().getRequests().sendAndReturn(
                tenantMapper,
                params -> tenantResource.getForRealmId(params, realmId),
                200,
                tenant -> {
                    view.setRealmLabel(tenant.getDisplayName());
                    view.setCreateRulesetHistoryToken(
                        environment.getPlaceHistoryMapper().getToken(new TenantRulesEditorPlace(realmId))
                    );
                }
            );

            environment.getApp().getRequests().sendAndReturn(
                tenantRulesetArrayMapper,
                params -> rulesetResource.getTenantRulesets(params, realmId),
                200,
                view::setRulesets
            );
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        view.setPresenter(null);
    }

    @Override
    public void onRulesetSelected(TenantRuleset ruleset) {
        environment.getPlaceController().goTo(new TenantRulesEditorPlace(realmId, ruleset.getId()));
    }
}