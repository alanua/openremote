package org.openremote.test.rules

import elemental.json.Json
import org.openremote.manager.server.asset.AssetProcessingService
import org.openremote.manager.server.asset.AssetStorageService
import org.openremote.manager.server.asset.ServerAsset
import org.openremote.manager.server.rules.RulesEngine
import org.openremote.manager.server.rules.RulesService
import org.openremote.manager.server.rules.RulesetStorageService
import org.openremote.manager.server.setup.SetupService
import org.openremote.manager.server.setup.builtin.KeycloakDemoSetup
import org.openremote.manager.server.setup.builtin.ManagerDemoSetup
import org.openremote.manager.shared.rules.AssetRuleset
import org.openremote.manager.shared.rules.Ruleset.DeploymentStatus
import org.openremote.manager.shared.rules.TenantRuleset
import org.openremote.model.AttributeEvent
import org.openremote.model.AttributeType
import org.openremote.model.Meta
import org.openremote.model.MetaItem
import org.openremote.model.asset.AssetAttribute
import org.openremote.model.asset.AssetEvent
import org.openremote.model.asset.AssetMeta
import org.openremote.model.asset.AssetType
import org.openremote.test.ManagerContainerTrait
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import static org.openremote.manager.server.setup.builtin.ManagerDemoSetup.*
import static org.openremote.test.RulesTestUtil.attachRuleExecutionLogger

class BasicRulesProcessingTest extends Specification implements ManagerContainerTrait {

    List<String> globalEngineFiredRules = []
    List<String> masterEngineFiredRules = []
    List<String> customerAEngineFiredRules = []
    List<String> smartHomeEngineFiredRules = []
    List<String> apartment2EngineFiredRules = []
    List<String> apartment3EngineFiredRules = []

    def resetRuleExecutionLoggers() {
        globalEngineFiredRules.clear()
        masterEngineFiredRules.clear()
        customerAEngineFiredRules.clear()
        smartHomeEngineFiredRules.clear()
        apartment2EngineFiredRules.clear()
        apartment3EngineFiredRules.clear()
    }

    def assertNoRulesFired = {
        assert globalEngineFiredRules.size() == 0
        assert masterEngineFiredRules.size() == 0
        assert customerAEngineFiredRules.size() == 0
        assert smartHomeEngineFiredRules.size() == 0
        assert apartment2EngineFiredRules.size() == 0
        assert apartment3EngineFiredRules.size() == 0
    }

    def "Check scoped firing of rules"() {
        given: "expected conditions"
        def conditions = new PollingConditions(timeout: 10, initialDelay: 0.5, delay: 0.5)

        and: "the container is started"
        def serverPort = findEphemeralPort()
        def container = startContainerWithoutDemoRules(defaultConfig(serverPort), defaultServices())
        def managerDemoSetup = container.getService(SetupService.class).getTaskOfType(ManagerDemoSetup.class)
        def keycloakDemoSetup = container.getService(SetupService.class).getTaskOfType(KeycloakDemoSetup.class)
        def rulesService = container.getService(RulesService.class)
        def rulesetStorageService = container.getService(RulesetStorageService.class)
        def assetProcessingService = container.getService(AssetProcessingService.class)

        and: "some test rulesets have been imported"
        def rulesImport = new BasicRulesImport(rulesetStorageService, keycloakDemoSetup, managerDemoSetup)

        expect: "the rules engines to be ready"
        new PollingConditions(initialDelay: 3, timeout: 20, delay: 1).eventually {
            rulesImport.assertEnginesReady(rulesService, keycloakDemoSetup, managerDemoSetup)
        }

        and: "the demo attributes marked with RULE_STATE = true meta should be inserted into the engines"
        conditions.eventually {
            assert rulesService.assetStates.size() == DEMO_RULE_STATES_GLOBAL
            assert rulesImport.globalEngine.assetStates.size() == DEMO_RULE_STATES_GLOBAL
            assert rulesImport.masterEngine.assetStates.size() == 0
            assert rulesImport.customerAEngine.assetStates.size() == DEMO_RULE_STATES_CUSTOMER_A
            assert rulesImport.apartment2Engine.assetStates.size() == DEMO_RULE_STATES_APARTMENT_2
            assert rulesImport.apartment3Engine.assetStates.size() == DEMO_RULE_STATES_APARTMENT_3
        }

        when: "rule execution loggers are attached to the engines"
        attachRuleExecutionLogger(rulesImport.globalEngine, globalEngineFiredRules)
        attachRuleExecutionLogger(rulesImport.masterEngine, masterEngineFiredRules)
        attachRuleExecutionLogger(rulesImport.customerAEngine, customerAEngineFiredRules)
        attachRuleExecutionLogger(rulesImport.apartment2Engine, apartment2EngineFiredRules)
        attachRuleExecutionLogger(rulesImport.apartment3Engine, apartment3EngineFiredRules)

        and: "an attribute event is pushed into the system for an attribute with RULE_STATE meta set to true"
        def apartment2LivingRoomPresenceDetectedChange = new AttributeEvent(
                managerDemoSetup.apartment2LivingroomId, "presenceDetected", Json.create(true)
        )
        assetProcessingService.sendAttributeEvent(apartment2LivingRoomPresenceDetectedChange)

        then: "the rule engines in scope should fire the 'All' and 'All changed' rules"
        conditions.eventually {
            def expectedFiredRules = ["All", "All changed"]
            assert globalEngineFiredRules.size() == 2
            assert globalEngineFiredRules.containsAll(expectedFiredRules)
            assert masterEngineFiredRules.size() == 0
            assert customerAEngineFiredRules.size() == 2
            assert customerAEngineFiredRules.containsAll(expectedFiredRules)
            assert smartHomeEngineFiredRules.size() == 0
            assert apartment2EngineFiredRules.size() == 2
            assert apartment2EngineFiredRules.containsAll(expectedFiredRules)
            assert apartment3EngineFiredRules.size() == 0
        }

        when: "an attribute event is pushed into the system for an attribute with RULE_STATE meta set to false"
        resetRuleExecutionLoggers()
        def apartment2LivingroomLightSwitchChange = new AttributeEvent(
                managerDemoSetup.apartment2LivingroomId, "lightSwitch", Json.create(false)
        )
        assetProcessingService.sendAttributeEvent(apartment2LivingroomLightSwitchChange)

        then: "no rule engines should have fired after a few seconds"
        new PollingConditions(initialDelay: 3).eventually assertNoRulesFired

        when: "an attribute event is pushed into the system for an attribute with no RULE_STATE meta"
        resetRuleExecutionLoggers()
        def apartment2LivingRoomWindowOpenChange = new AttributeEvent(
                managerDemoSetup.apartment2LivingroomId, "windowOpen", Json.create(true)
        )
        assetProcessingService.sendAttributeEvent(apartment2LivingRoomWindowOpenChange)

        then: "no rule engines should have fired after a few seconds"
        new PollingConditions(initialDelay: 3).eventually assertNoRulesFired

        when: "an old (stale) attribute event is pushed into the system"
        resetRuleExecutionLoggers()
        assetProcessingService.sendAttributeEvent(apartment2LivingRoomPresenceDetectedChange)

        then: "no rule engines should have fired after a few seconds"
        new PollingConditions(initialDelay: 3).eventually assertNoRulesFired

        when: "an attribute event with the same value as current value is pushed into the system"
        resetRuleExecutionLoggers()
        apartment2LivingRoomPresenceDetectedChange = new AttributeEvent(
                managerDemoSetup.apartment2LivingroomId, "presenceDetected", Json.create(true)
        )
        assetProcessingService.sendAttributeEvent(apartment2LivingRoomPresenceDetectedChange)

        then: "the rule engines in scope should fire the 'All' rule but not the 'All changed' rule"
        conditions.eventually {
            assert globalEngineFiredRules.size() == 1
            assert globalEngineFiredRules[0] == "All"
            assert masterEngineFiredRules.size() == 0
            assert customerAEngineFiredRules.size() == 1
            assert customerAEngineFiredRules[0] == "All"
            assert smartHomeEngineFiredRules.size() == 0
            assert apartment2EngineFiredRules.size() == 1
            assert apartment2EngineFiredRules[0] == "All"
            assert apartment3EngineFiredRules.size() == 0
        }

        when: "a LHS filtering test rule definition is loaded into the smart home asset"
        resetRuleExecutionLoggers()
        def assetRuleset = new AssetRuleset(
                "Some smart home asset rules",
                managerDemoSetup.smartHomeId,
                getClass().getResource("/org/openremote/test/rules/BasicSmartHomeMatchAllAssetStates.drl").text
        )
        rulesetStorageService.merge(assetRuleset)
        RulesEngine smartHomeEngine = null

        then: "the smart home rule engine should have ben created, loaded the new rule definition and facts and started"
        conditions.eventually {
            smartHomeEngine = rulesService.assetDeployments.get(managerDemoSetup.smartHomeId)
            assert smartHomeEngine != null
            assert smartHomeEngine.isRunning()
            assert smartHomeEngine.allRulesets.length == 1
            assert smartHomeEngine.allRulesets[0].enabled
            assert smartHomeEngine.allRulesets[0].name == "Some smart home asset rules"
            assert smartHomeEngine.allRulesets[0].deploymentStatus == DeploymentStatus.DEPLOYED
            assert smartHomeEngine.assetStates.size() == DEMO_RULE_STATES_SMART_HOME
            assert smartHomeEngine.knowledgeSession.factCount == DEMO_RULE_STATES_SMART_HOME
        }

        when: "the engine counters are reset and the smart home engine logger is attached"
        resetRuleExecutionLoggers()
        attachRuleExecutionLogger(smartHomeEngine, smartHomeEngineFiredRules)

        and: "an attribute event occurs"
        apartment2LivingRoomPresenceDetectedChange = new AttributeEvent(
                managerDemoSetup.apartment2LivingroomId, "presenceDetected", Json.create(false)
        )
        assetProcessingService.sendAttributeEvent(apartment2LivingRoomPresenceDetectedChange)

        then: "the engines in scope should have fired the matched rules"
        conditions.eventually {
            assert globalEngineFiredRules.size() == 2
            assert globalEngineFiredRules.containsAll(["All", "All changed"])
            assert customerAEngineFiredRules.size() == 2
            assert customerAEngineFiredRules.containsAll(["All", "All changed"])
            assert smartHomeEngineFiredRules.size() == 5
            assert smartHomeEngineFiredRules.containsAll(["Living Room All", "Current Asset Update", "Parent Type Residence", "Asset Type Room", "Boolean Attributes"])
            assert apartment2EngineFiredRules.size() == 2
            assert apartment2EngineFiredRules.containsAll(["All", "All changed"])
            assert apartment3EngineFiredRules.size() == 0
        }

        cleanup: "the server should be stopped"
        stopContainer(container)
    }

    def "Handle asset create, update, delete"() {
        given: "expected conditions"
        def conditions = new PollingConditions(timeout: 10, initialDelay: 0.5, delay: 0.5)

        and: "the container is started"
        def serverPort = findEphemeralPort()
        def container = startContainerWithoutDemoRules(defaultConfig(serverPort), defaultServices())
        def managerDemoSetup = container.getService(SetupService.class).getTaskOfType(ManagerDemoSetup.class)
        def keycloakDemoSetup = container.getService(SetupService.class).getTaskOfType(KeycloakDemoSetup.class)
        def rulesService = container.getService(RulesService.class)
        def rulesetStorageService = container.getService(RulesetStorageService.class)
        def assetStorageService = container.getService(AssetStorageService.class)

        and: "some test rulesets have been imported"
        def rulesImport = new BasicRulesImport(rulesetStorageService, keycloakDemoSetup, managerDemoSetup)

        expect: "the rules engines to be ready"
        new PollingConditions(initialDelay: 3, timeout: 20, delay: 1).eventually {
            rulesImport.assertEnginesReady(rulesService, keycloakDemoSetup, managerDemoSetup)
        }

        when: "rule execution loggers are attached to the engines"
        attachRuleExecutionLogger(rulesImport.globalEngine, globalEngineFiredRules)
        attachRuleExecutionLogger(rulesImport.masterEngine, masterEngineFiredRules)
        attachRuleExecutionLogger(rulesImport.customerAEngine, customerAEngineFiredRules)
        attachRuleExecutionLogger(rulesImport.apartment2Engine, apartment2EngineFiredRules)
        attachRuleExecutionLogger(rulesImport.apartment3Engine, apartment3EngineFiredRules)

        and: "a Kitchen room asset is inserted into apartment that contains a RULE_STATE = true meta flag"
        resetRuleExecutionLoggers()
        def apartment2 = assetStorageService.find(managerDemoSetup.apartment2Id)
        def asset = new ServerAsset(apartment2)
        asset.setRealmId(keycloakDemoSetup.customerATenant.getId())
        asset.setType(AssetType.ROOM)
        asset.setName("Kitchen")
        def attributes = [
                new AssetAttribute("testString", AttributeType.STRING, Json.create("test"))
                        .setMeta(new Meta()
                        .add(new MetaItem(AssetMeta.RULE_STATE, Json.create(true)))
                )
        ]
        asset.setAttributeList(attributes)
        asset = assetStorageService.merge(asset)

        then: "after a few seconds the engines in scope should have facts and rules should have fired"
        conditions.eventually {
            assert rulesService.assetStates.size() == DEMO_RULE_STATES_GLOBAL + 1
            assert rulesImport.globalEngine.assetStates.size() == DEMO_RULE_STATES_GLOBAL + 1
            assert rulesImport.masterEngine.assetStates.size() == 0
            assert rulesImport.customerAEngine.assetStates.size() == DEMO_RULE_STATES_CUSTOMER_A + 1
            assert rulesImport.apartment2Engine.assetStates.size() == DEMO_RULE_STATES_APARTMENT_2 + 1
            assert rulesImport.apartment3Engine.assetStates.size() == DEMO_RULE_STATES_APARTMENT_3
            def expectedFiredRules = ["All", "All changed"]
            assert globalEngineFiredRules.size() == 2
            assert globalEngineFiredRules.containsAll(expectedFiredRules)
            assert masterEngineFiredRules.size() == 0
            assert customerAEngineFiredRules.size() == 2
            assert customerAEngineFiredRules.containsAll(expectedFiredRules)
            assert smartHomeEngineFiredRules.size() == 0
            assert apartment2EngineFiredRules.size() == 2
            assert apartment2EngineFiredRules.containsAll(expectedFiredRules)
            assert apartment3EngineFiredRules.size() == 0
        }

        when: "the Kitchen room asset is modified to add a new attribute but RULE_STATE = true meta is not changed"
        resetRuleExecutionLoggers()
        attributes = [
                new AssetAttribute("testString", AttributeType.STRING, Json.create("test"))
                        .setMeta(
                        new Meta()
                                .add(new MetaItem(AssetMeta.RULE_STATE, Json.create(true)))
                ),
                new AssetAttribute("testInteger", AttributeType.INTEGER, Json.create(0))
        ]
        asset.setAttributeList(attributes)
        asset = assetStorageService.merge(asset)

        then: "after a few seconds the fact count shouldn't change and no rules should have fired"
        new PollingConditions(initialDelay: 3).eventually {
            assert rulesService.assetStates.size() == DEMO_RULE_STATES_GLOBAL + 1
            assert rulesImport.globalEngine.assetStates.size() == DEMO_RULE_STATES_GLOBAL + 1
            assert rulesImport.masterEngine.assetStates.size() == 0
            assert rulesImport.customerAEngine.assetStates.size() == DEMO_RULE_STATES_CUSTOMER_A + 1
            assert rulesImport.apartment2Engine.assetStates.size() == DEMO_RULE_STATES_APARTMENT_2 + 1
            assert rulesImport.apartment3Engine.assetStates.size() == DEMO_RULE_STATES_APARTMENT_3
            assert globalEngineFiredRules.size() == 0
            assert customerAEngineFiredRules.size() == 0
            assert smartHomeEngineFiredRules.size() == 0
            assert apartment2EngineFiredRules.size() == 0
            assert apartment3EngineFiredRules.size() == 0
        }

        when: "the Kitchen room asset is modified to set the RULE_STATE to false"
        attributes = [
                new AssetAttribute("testString", AttributeType.STRING, Json.create("test"))
                        .setMeta(
                        new Meta()
                                .add(new MetaItem(AssetMeta.RULE_STATE, Json.create(false)))
                ),
                new AssetAttribute("testInteger", AttributeType.INTEGER, Json.create(0))
        ]
        asset.setAttributeList(attributes)
        asset = assetStorageService.merge(asset)

        then: "the facts should be removed from the rule engines and no rules should have fired"
        conditions.eventually {
            assert rulesService.assetStates.size() == DEMO_RULE_STATES_GLOBAL
            assert rulesImport.globalEngine.assetStates.size() == DEMO_RULE_STATES_GLOBAL
            assert rulesImport.masterEngine.assetStates.size() == 0
            assert rulesImport.customerAEngine.assetStates.size() == DEMO_RULE_STATES_CUSTOMER_A
            assert rulesImport.apartment2Engine.assetStates.size() == DEMO_RULE_STATES_APARTMENT_2
            assert rulesImport.apartment3Engine.assetStates.size() == DEMO_RULE_STATES_APARTMENT_3
            assert globalEngineFiredRules.size() == 0
            assert customerAEngineFiredRules.size() == 0
            assert smartHomeEngineFiredRules.size() == 0
            assert apartment2EngineFiredRules.size() == 0
            assert apartment3EngineFiredRules.size() == 0
        }

        when: "the Kitchen room asset is modified to set all attributes to RULE_STATE = true"
        resetRuleExecutionLoggers()
        attributes = [
                new AssetAttribute("testString", AttributeType.STRING, Json.create("test"))
                        .setMeta(
                        new Meta()
                                .add(new MetaItem(AssetMeta.RULE_STATE, Json.create(true)))
                ),
                new AssetAttribute("testInteger", AttributeType.INTEGER, Json.create(0))
                        .setMeta(
                        new Meta()
                                .add(new MetaItem(AssetMeta.RULE_STATE, Json.create(true)))
                )
        ]
        asset.setAttributeList(attributes)
        asset = assetStorageService.merge(asset)

        then: "the facts should be added to the rule engines and rules should have fired"
        conditions.eventually {
            assert rulesService.assetStates.size() == DEMO_RULE_STATES_GLOBAL + 2
            assert rulesImport.globalEngine.assetStates.size() == DEMO_RULE_STATES_GLOBAL + 2
            assert rulesImport.masterEngine.assetStates.size() == 0
            assert rulesImport.customerAEngine.assetStates.size() == DEMO_RULE_STATES_CUSTOMER_A + 2
            assert rulesImport.apartment2Engine.assetStates.size() == DEMO_RULE_STATES_APARTMENT_2 + 2
            assert rulesImport.apartment3Engine.assetStates.size() == DEMO_RULE_STATES_APARTMENT_3
            def expectedFiredRules = ["All", "All changed"]
            assert globalEngineFiredRules.size() == 4
            assert globalEngineFiredRules.containsAll(expectedFiredRules)
            assert masterEngineFiredRules.size() == 0
            assert customerAEngineFiredRules.size() == 4
            assert customerAEngineFiredRules.containsAll(expectedFiredRules)
            assert smartHomeEngineFiredRules.size() == 0
            assert apartment2EngineFiredRules.size() == 4
            assert apartment2EngineFiredRules.containsAll(expectedFiredRules)
            assert apartment3EngineFiredRules.size() == 0
        }

        when: "the Kitchen room asset is deleted"
        resetRuleExecutionLoggers()
        assetStorageService.delete(asset.getId())

        then: "the facts should be removed from the rule engines and no rules should have fired"
        conditions.eventually {
            assert rulesService.assetStates.size() == DEMO_RULE_STATES_GLOBAL
            assert rulesImport.globalEngine.assetStates.size() == DEMO_RULE_STATES_GLOBAL
            assert rulesImport.masterEngine.assetStates.size() == 0
            assert rulesImport.customerAEngine.assetStates.size() == DEMO_RULE_STATES_CUSTOMER_A
            assert rulesImport.apartment2Engine.assetStates.size() == DEMO_RULE_STATES_APARTMENT_2
            assert rulesImport.apartment3Engine.assetStates.size() == DEMO_RULE_STATES_APARTMENT_3
            assert globalEngineFiredRules.size() == 0
            assert customerAEngineFiredRules.size() == 0
            assert smartHomeEngineFiredRules.size() == 0
            assert apartment2EngineFiredRules.size() == 0
            assert apartment3EngineFiredRules.size() == 0
        }

        cleanup: "the server should be stopped"
        stopContainer(container)
    }

    def "Stop processing when engine in error state"() {
        given: "expected conditions"
        def conditions = new PollingConditions(timeout: 10, delay: 0.5)

        and: "the container is started"
        def serverPort = findEphemeralPort()
        def container = startContainerWithoutDemoRules(defaultConfig(serverPort), defaultServices())
        def managerDemoSetup = container.getService(SetupService.class).getTaskOfType(ManagerDemoSetup.class)
        def keycloakDemoSetup = container.getService(SetupService.class).getTaskOfType(KeycloakDemoSetup.class)
        def rulesService = container.getService(RulesService.class)
        def rulesetStorageService = container.getService(RulesetStorageService.class)
        def assetProcessingService = container.getService(AssetProcessingService.class)
        def assetStorageService = container.getService(AssetStorageService.class)

        and: "some test rulesets have been imported"
        def rulesImport = new BasicRulesImport(rulesetStorageService, keycloakDemoSetup, managerDemoSetup)

        expect: "the rules engines to be ready"
        new PollingConditions(initialDelay: 3, timeout: 20, delay: 1).eventually {
            rulesImport.assertEnginesReady(rulesService, keycloakDemoSetup, managerDemoSetup)
        }

        when: "rule execution loggers are attached to the engines"
        attachRuleExecutionLogger(rulesImport.globalEngine, globalEngineFiredRules)
        attachRuleExecutionLogger(rulesImport.masterEngine, masterEngineFiredRules)
        attachRuleExecutionLogger(rulesImport.customerAEngine, customerAEngineFiredRules)
        attachRuleExecutionLogger(rulesImport.apartment2Engine, apartment2EngineFiredRules)
        attachRuleExecutionLogger(rulesImport.apartment3Engine, apartment3EngineFiredRules)

        and: "a broken RHS rule is loaded into the customerA engine"
        def ruleset = new TenantRuleset(
                "Some broken test rules",
                keycloakDemoSetup.customerATenant.id,
                getClass().getResource("/org/openremote/test/rules/BasicBrokenRules.drl").text
        )
        rulesetStorageService.merge(ruleset)

        then: "the customerA engine should not run and the rule engine status should indicate the issue"
        conditions.eventually {
            assert rulesImport.customerAEngine.allRulesets.length == 2
            assert rulesImport.customerAEngine.running == false
            assert rulesImport.customerAEngine.isError()
            assert rulesImport.customerAEngine.error instanceof RuntimeException
            assert rulesImport.customerAEngine.allRulesets[0].enabled
            assert rulesImport.customerAEngine.allRulesets[0].name == "Some customerA tenant demo rules"
            assert rulesImport.customerAEngine.allRulesets[0].deploymentStatus == DeploymentStatus.READY
            assert rulesImport.customerAEngine.allRulesets[1].enabled
            assert rulesImport.customerAEngine.allRulesets[1].name == "Some broken test rules"
            assert rulesImport.customerAEngine.allRulesets[1].deploymentStatus == DeploymentStatus.FAILED
        }

        when: "an attribute event occurs"
        def apartment2LivingRoomPresenceDetectedChange = new AttributeEvent(
                managerDemoSetup.apartment2LivingroomId, "presenceDetected", Json.create(true)
        )
        assetProcessingService.sendAttributeEvent(apartment2LivingRoomPresenceDetectedChange)

        then: "the broken rules engine should prevent a database update"
        new PollingConditions(initialDelay: 3).eventually {
            def asset = assetStorageService.find(managerDemoSetup.apartment2LivingroomId, true)
            assert !asset.getAttribute("presenceDetected").get().getValueAsBoolean()
        }

        cleanup: "the server should be stopped"
        stopContainer(container)
    }
}
