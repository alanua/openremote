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
package org.openremote.manager.rules;

import org.drools.core.ClassObjectFilter;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.time.InternalSchedulerService;
import org.drools.core.time.JobContext;
import org.drools.core.time.JobHandle;
import org.drools.core.time.impl.*;
import org.drools.template.ObjectDataCompiler;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.builder.ReleaseId;
import org.kie.api.builder.model.KieBaseModel;
import org.kie.api.builder.model.KieModuleModel;
import org.kie.api.builder.model.KieSessionModel;
import org.kie.api.conf.EqualityBehaviorOption;
import org.kie.api.conf.EventProcessingOption;
import org.kie.api.event.rule.*;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionConfiguration;
import org.kie.api.runtime.conf.ClockTypeOption;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.time.SessionClock;
import org.openremote.container.timer.TimerService;
import org.openremote.container.util.Util;
import org.openremote.manager.asset.AssetStorageService;
import org.openremote.manager.asset.ServerAsset;
import org.openremote.manager.concurrent.ManagerExecutorService;
import org.openremote.model.asset.AssetEvent;
import org.openremote.model.asset.AssetState;
import org.openremote.model.attribute.AttributeType;
import org.openremote.model.rules.Assets;
import org.openremote.model.rules.Ruleset;
import org.openremote.model.rules.Users;
import org.openremote.model.rules.template.TemplateFilter;
import org.openremote.model.util.Pair;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.openremote.model.attribute.Attribute.isAttributeTypeEqualTo;

public class RulesEngine<T extends Ruleset> {

    public static final Logger LOG = Logger.getLogger(RulesEngine.class.getName());

    // Separate logger for stats
    public static final Logger STATS_LOG = Logger.getLogger("org.openremote.rules.RulesEngineStats");

    // Separate logger for rules, available global in session
    public static final Logger RULES_LOG = Logger.getLogger("org.openremote.rules.Rules");

    private static Long counter = 1L;

    static final protected Util UTIL = new Util();

    final protected TimerService timerService;
    final protected ManagerExecutorService executorService;
    final protected AssetStorageService assetStorageService;
    final protected Supplier<Assets> assetsFacadeSupplier;
    final protected Supplier<Users> usersFacadeSupplier;
    final protected String id;//If globalRuleSet then null if tenantRuleSet then realmId if assetRuleSet then assetId
    final protected Function<RulesEngine, AgendaEventListener> rulesEngineListeners;
    final protected Consumer<Throwable> errorListener;

    protected final Map<Long, T> rulesets = new LinkedHashMap<>();
    protected String rulesetsDebug;
    protected KieSession knowledgeSession;
    protected KieServices kieServices;
    protected KieFileSystem kfs;
    // We need to be able to reference the KieModule dynamically generated for this engine
    // from the singleton KieRepository to do this we need a pom.xml file with a release ID - crazy drools!!
    protected ReleaseId releaseId;
    protected Future runningFuture;
    protected Throwable error;
    final protected Map<AssetState, FactHandle> assetStates = new HashMap<>();
    // This consumer is useful in testing, as we can't have a reliable event fact
    // count from Drools session (events are expired automatically))
    protected Consumer<AssetEvent> assetEventsConsumer;
    protected ScheduledFuture statsTimer;

    public RulesEngine(TimerService timerService,
                       ManagerExecutorService executorService,
                       AssetStorageService assetStorageService,
                       Supplier<Assets> assetsFacadeSupplier,
                       Supplier<Users> usersFacadeSupplier,
                       String id,
                       Function<RulesEngine, AgendaEventListener> rulesEngineListeners,
                       Consumer<Throwable> errorListener) {
        this.timerService = timerService;
        this.executorService = executorService;
        this.assetStorageService = assetStorageService;
        this.assetsFacadeSupplier = assetsFacadeSupplier;
        this.usersFacadeSupplier = usersFacadeSupplier;
        this.id = id;
        this.rulesEngineListeners = rulesEngineListeners;
        this.errorListener = errorListener;
    }

    protected synchronized static Long getNextCounter() {
        return counter++;
    }

    public synchronized Ruleset[] getAllRulesets() {
        return rulesets.values().stream().toArray(Ruleset[]::new);
    }

    public String getId() {
        return id;
    }

    public boolean isRunning() {
        return runningFuture != null;
    }

    public boolean isError() {
        return error != null;
    }

    public Throwable getError() {
        return error;
    }

    protected void setError(Throwable error) {
        this.error = error;
        if (errorListener != null)
            errorListener.accept(error);
    }

    public KieSession getKnowledgeSession() {
        return knowledgeSession;
    }

    public SessionClock getSessionClock() {
        KieSession session = knowledgeSession;
        if (session != null) {
            return session.getSessionClock();
        }

        return null;
    }

    public synchronized boolean isEmpty() {
        return rulesets.isEmpty();
    }

    public void setAssetEventsConsumer(Consumer<AssetEvent> assetEventsConsumer) {
        this.assetEventsConsumer = assetEventsConsumer;
    }

    protected void setGlobal(String identifier, Object object) {
        try {
            knowledgeSession.setGlobal(identifier, object);
        } catch (Throwable t) {
            // Ignore, Drools complains if the DRL doesn't declare the global, but it works
        }
    }

    /**
     * Adds the ruleset to the engine by first stopping the engine and
     * then deploying new rules and then restarting the engine.
     * <p>
     * If engine is in an error state (one of the rulesets failed to deploy)
     * then the engine will not restart, inspect then with {@link #isRunning()}
     * and {@link #isError()}.
     */
    public synchronized void addRuleset(T ruleset, boolean forceUpdate) {
        if (ruleset == null || ruleset.getRules() == null || ruleset.getRules().isEmpty()) {
            // Assume it's a success if deploying an empty ruleset
            LOG.finest("Ruleset is empty so no rules to deploy");
            return;
        }

        if (kfs == null) {
            initialiseEngine();
        }

        T existingRuleset = rulesets.get(ruleset.getId());

        if (!forceUpdate && existingRuleset != null && existingRuleset.getVersion() == ruleset.getVersion()) {
            LOG.fine("Ruleset version already deployed so ignoring");
            return;
        }

        if (isRunning()) {
            stop();
        }

        // Check if ruleset is already deployed (maybe an older version)
        if (existingRuleset != null) {
            // Remove this old rules file
            kfs.delete("src/main/resources/" + ruleset.getId());
            //noinspection SuspiciousMethodCalls
            rulesets.remove(existingRuleset);
            updateRulesetsDebug();
        }

        LOG.info("Adding ruleset: " + ruleset);

        boolean addSuccessful = false;
        setError(null);

        try {
            // If the ruleset references a template asset, compile it as a template
            String drl = ruleset.getTemplateAssetId() != null
                ? compileTemplate(ruleset.getTemplateAssetId(), ruleset.getRules())
                : ruleset.getRules();

            LOG.finest("Final ruleset DRL: " + drl);

            // ID will be unique within the scope of a rules engine as ruleset will all be of same type
            kfs.write("src/main/resources/" + ruleset.getId() + ".drl", drl);

            // Unload the rules string from the ruleset we don't need it anymore and don't want it using memory
            ruleset.setRules(null);
            KieBuilder kieBuilder = kieServices.newKieBuilder(kfs).buildAll();

            if (kieBuilder.getResults().hasMessages(Message.Level.ERROR)) {
                Collection<Message> errors = kieBuilder.getResults().getMessages(Message.Level.ERROR);
                LOG.severe("Error in ruleset: " + ruleset);
                for (Message error : errors) {
                    LOG.severe(error.getText());
                }
                LOG.fine(drl);
                // If compilation failed, remove rules from FileSystem so it won't fail on next pass here if any
                kfs.delete("src/main/resources/" + ruleset.getId());
            } else {
                addSuccessful = true;
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error in ruleset: " + ruleset, e);
            setError(e);
            // If compilation failed, remove rules from FileSystem so it won't fail on next pass here if any
            kfs.delete("src/main/resources/" + ruleset.getId());
        }

        if (!addSuccessful) {
            setError(new RuntimeException("Ruleset contains an error: " + ruleset));

            // Update status of each ruleset
            rulesets.forEach((id, rd) -> {
                if (rd.getDeploymentStatus() == Ruleset.DeploymentStatus.DEPLOYED) {
                    rd.setDeploymentStatus(Ruleset.DeploymentStatus.READY);
                }
            });
        }

        // Add new ruleset
        ruleset.setDeploymentStatus(addSuccessful ? Ruleset.DeploymentStatus.DEPLOYED : Ruleset.DeploymentStatus.FAILED);
        rulesets.put(ruleset.getId(), ruleset);
        updateRulesetsDebug();

        if (addSuccessful) {
            start();
        }
    }

    protected synchronized void removeRuleset(Ruleset ruleset) {
        if (kfs == null) {
            return;
        }

        T matchedRuleset = rulesets.get(ruleset.getId());
        if (matchedRuleset == null) {
            LOG.finer("Ruleset cannot be retracted as it was never deployed: " + ruleset);
            return;
        }

        if (isRunning()) {
            stop();
        }

        // Remove this old rules file
        kfs.delete("src/main/resources/" + ruleset.getId());
        rulesets.remove(ruleset.getId());
        updateRulesetsDebug();

        // Update status of each ruleset
        boolean anyFailed = rulesets
            .values()
            .stream()
            .anyMatch(rd -> rd.getDeploymentStatus() == Ruleset.DeploymentStatus.FAILED);

        if (!anyFailed) {
            setError(null);
            rulesets.forEach((id, rd) -> {
                if (rd.getDeploymentStatus() == Ruleset.DeploymentStatus.READY) {
                    rd.setDeploymentStatus(Ruleset.DeploymentStatus.DEPLOYED);
                }
            });
        }

        if (!isError() && !isEmpty()) {
            start();
        }
    }

    protected void initialiseEngine() {
        // Initialise
        kieServices = KieServices.Factory.get();
        KieModuleModel kieModuleModel = kieServices.newKieModuleModel();

        String versionId = getNextCounter().toString();
        releaseId = kieServices.newReleaseId("org.openremote", "openremote-kiemodule", versionId);
        KieBaseModel kieBaseModel = kieModuleModel.newKieBaseModel("OpenRemoteKModule");

        kieBaseModel
            .setDefault(true)
            .setEqualsBehavior(EqualityBehaviorOption.EQUALITY)
            .setEventProcessingMode(EventProcessingOption.STREAM)
            .newKieSessionModel("ksession1")
            .setDefault(true)
            .setType(KieSessionModel.KieSessionType.STATEFUL);
        kfs = kieServices.newKieFileSystem();
        kfs.generateAndWritePomXML(releaseId);
        kfs.writeKModuleXML(kieModuleModel.toXML());

        LOG.info("Initialised rules for deployment '" + getId() + "':" + kieBaseModel.toString());
    }

    protected synchronized void start() {
        if (isRunning()) {
            return;
        }

        if (isError()) {
            LOG.fine("Cannot start rules engine as an error occurred during initialisation");
            return;
        }

        if (isEmpty()) {
            LOG.finest("No rulesets loaded so nothing to start");
            return;
        }

        LOG.info("Starting: " + this);

        // Note each rule engine has its' own KieModule which are stored in a singleton register by drools
        // we need to ensure we get the right module here otherwise we could be using the wrong rules
        KieContainer kieContainer = kieServices.newKieContainer(releaseId);

        KieSessionConfiguration kieSessionConfiguration = kieServices.newKieSessionConfiguration();

        // Which clock to use ("pseudo" for testing, "realtime" otherwise)
        switch (timerService.getClock()) {
            case PSEUDO:
                kieSessionConfiguration.setOption(ClockTypeOption.get("pseudo"));
                break;
            default:
                kieSessionConfiguration.setOption(ClockTypeOption.get("realtime"));
        }

        try {
            knowledgeSession = kieContainer.newKieSession(kieSessionConfiguration);

            // If the pseudo clock is enabled (we run a test environment?) then set current
            // time on startup of session, as real time is used in offset calculations for
            // automatic event expiration in Drools (probably a design mistake)
            if (timerService.getClock() == TimerService.Clock.PSEUDO) {
                ((PseudoClockScheduler) knowledgeSession.getSessionClock())
                    .setStartupTime(timerService.getCurrentTimeMillis());
            }

            setGlobal("assets", assetsFacadeSupplier.get());
            setGlobal("users", usersFacadeSupplier.get());
            setGlobal("LOG", RULES_LOG);

            // TODO Still need this UTIL?
            setGlobal("util", UTIL);

            knowledgeSession.addEventListener(new RuleExecutionLogger(this::toString, RULES_LOG));

            AgendaEventListener eventListener = rulesEngineListeners != null ? rulesEngineListeners.apply(this) : null;
            if (eventListener != null) {
                knowledgeSession.addEventListener(eventListener);
            }

            knowledgeSession.addEventListener(new RuleRuntimeEventListener() {
                @Override
                public void objectInserted(ObjectInsertedEvent event) {
                    RULES_LOG.finest("+++ On " + RulesEngine.this + ", fact inserted: " + event.getObject());
                }

                @Override
                public void objectUpdated(ObjectUpdatedEvent event) {
                    RULES_LOG.finest("^^^ On " + RulesEngine.this + ", fact updated: " + event.getObject());
                }

                @Override
                public void objectDeleted(ObjectDeletedEvent event) {
                    RULES_LOG.finest("--- On " + RulesEngine.this + ", fact deleted: " + event.getOldObject());
                }
            });

            // Start engine in active mode
            fireUntilHalt();

            // Insert initial asset states
            try {
                insertInitialAssetStates();
            } catch (Exception ex) {
                // This is called in a background timer thread, we must log here or the exception is swallowed
                LOG.log(Level.SEVERE, "On " + this + ", inserting initial asset states failed", ex);
                setError(ex);
                stop();
            }

            // Start a background stats printer if INFO level logging is enabled
            if (STATS_LOG.isLoggable(Level.INFO) || STATS_LOG.isLoggable(Level.FINEST)) {
                if (STATS_LOG.isLoggable(Level.FINEST)) {
                    LOG.info("On " + this + ", enabling periodic statistics output at INFO level every 30 seconds on category: " + STATS_LOG.getName());
                } else {
                    LOG.info("On " + this + ", enabling periodic full memory dump at FINEST level every 30 seconds on category: " + STATS_LOG.getName());
                }
                statsTimer = executorService.scheduleAtFixedRate(this::printSessionStats, 3, 30, TimeUnit.SECONDS);
            }
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "On " + this + ", creating the knowledge session failed", ex);
            setError(ex);
            stop();
        }
    }

    protected synchronized void fireUntilHalt() {
        // Block a background thread
        runningFuture = executorService.getRulesExecutor().submit(() -> {
            boolean stoppedOnError = false;
            try {
                if (knowledgeSession != null) {
                    knowledgeSession.fireUntilHalt();
                } else {
                    LOG.log(Level.SEVERE, "On " + RulesEngine.this + ", can't fire until halt, no knowledge session exists");
                }
            } catch (Exception ex) {
                // Errors in rule RHS
                LOG.log(Level.SEVERE, "On " + RulesEngine.this + ", error firing rules", ex);
                stoppedOnError = true;
            } finally {
                if (stoppedOnError) {
                    // TODO This should be configurable, RHS should not throw exception in production?
                    // Keep running if stopped firing because of a RHS error
                    runningFuture.cancel(true);
                    runningFuture = null;
                    fireUntilHalt();
                }
            }
        });
    }

    protected synchronized void stop() {
        if (!isRunning()) {
            return;
        }
        LOG.info("Stopping: " + this);

        // Clear out fact handles because the session they belong to is gone
        synchronized (assetStates) {
            for (AssetState assetState : new HashSet<>(assetStates.keySet())) {
                if (assetStates.get(assetState) != null) {
                    assetStates.put(assetState, null);
                }
            }
        }

        if (knowledgeSession != null) {
            try {
                knowledgeSession.halt();
                knowledgeSession.dispose();
                knowledgeSession = null;
                LOG.fine("On " + this + ", knowledge session disposed");

            } finally {
                if (statsTimer != null) {
                    statsTimer.cancel(true);
                    statsTimer = null;
                }
                runningFuture.cancel(true);
                runningFuture = null;
            }
        }
    }

    protected synchronized void insertInitialAssetStates() {
        synchronized (assetStates) {
            LOG.info("On " + this + ", inserting initial asset states: " + assetStates.size());
            for (AssetState assetState : new HashSet<>(assetStates.keySet())) {
                updateAssetState(assetState, true);
            }
        }
    }

    protected synchronized void updateAssetState(AssetState assetState) {
        updateAssetState(assetState, false);
    }

    protected synchronized void updateAssetState(AssetState assetState, boolean initialImport) {
        synchronized (assetStates) {

            // Check if fact already exists using equals(), this will deduplicate asset state writes
            if (!initialImport && assetStates.containsKey(assetState)) {
                RULES_LOG.finest("On " + this + ", update is the same as existing, ignoring: " + assetState);
                return;
            }

            RULES_LOG.finest("On " + this + ", updating: " + assetState);

            // Do we have to remove an old fact first
            FactHandle oldFactHandle = null;
            if (!initialImport) {
                // Get the old asset state
                AssetState oldAssetState = assetStates.keySet()
                    .stream()
                    .filter(au -> au.attributeRefsEqual(assetState))
                    .findFirst()
                    .orElse(null);

                // Remove the old asset state and get its fact handle, so the fact can be removed too
                oldFactHandle = oldAssetState != null ? assetStates.remove(oldAssetState) : null;
            }

            if (!isRunning()) {
                RULES_LOG.finest("On " + this + ", engine is in error state or not running, storing for later update: " + assetState);
                assetStates.put(assetState, null);
                return;
            }

            deleteAndInsertInKnowledgeSession(oldFactHandle, assetState, factHandle -> {
                // Store the new asset state and the fact handle
                synchronized (assetStates) {
                    assetStates.put(assetState, factHandle);
                }
            });
        }
    }

    protected synchronized void deleteAndInsertInKnowledgeSession(FactHandle oldFactHandle,
                                                                  AssetState assetState,
                                                                  Consumer<FactHandle> factHandleConsumer) {
        // Atomically delete the old fact and insert a new fact, this adds to asynchronous work queue of rules engine thread
        knowledgeSession.submit(session -> {
            if (oldFactHandle != null) {
                session.delete(oldFactHandle);
            }
            FactHandle factHandle = session.insert(assetState);
            if (factHandleConsumer != null) {
                factHandleConsumer.accept(factHandle);
            }
        });
    }

    protected synchronized void retractAssetState(AssetState assetState) {
        synchronized (assetStates) {
            RULES_LOG.finest("On " + this + ", retracting: " + assetState);
            // If there already is a fact in working memory for this attribute then delete it
            AssetState update = assetStates.keySet()
                .stream()
                .filter(au -> au.attributeRefsEqual(assetState))
                .findFirst()
                .orElse(null);

            // Always remove from asset states
            FactHandle factHandle = update != null ? assetStates.remove(update) : null;

            if (factHandle != null) {
                if (isRunning()) {
                    try {
                        // ... retract it from working memory ...
                        knowledgeSession.delete(factHandle);
                    } catch (Exception e) {
                        RULES_LOG.warning("On " + this + ", failed to retract fact: " + update);
                    }
                }
            }
        }
    }

    protected synchronized void insertAssetEvent(long expirationOffset, AssetEvent assetEvent) {
        if (!isRunning()) {
            RULES_LOG.fine("On " + this + ", engine is in error state or not running, ignoring: " + assetEvent);
            return;
        }
        FactHandle factHandle = knowledgeSession.insert(assetEvent);
        if (factHandle != null) {
            scheduleExpiration(assetEvent, factHandle, expirationOffset);
        }
        if (assetEventsConsumer != null) {
            assetEventsConsumer.accept(assetEvent);
        }
    }

    /**
     * Use the internal scheduling of Drools to expire events, so we can coordinate with the internal clock.
     * Yes, this is a hack.
     */
    protected void scheduleExpiration(AssetEvent assetEvent, FactHandle factHandle, long expirationOffset) {
        if (!isRunning())
            return;
        InternalSchedulerService sessionScheduler = knowledgeSession.getSessionClock();
        JobHandle jobHandle = new JDKTimerService.JDKJobHandle(assetEvent.getId().hashCode());
        class AssetEventExpireJobContext implements JobContext {
            public JobHandle handle;

            @Override
            public void setJobHandle(JobHandle jobHandle) {
                this.handle = jobHandle;
            }

            @Override
            public JobHandle getJobHandle() {
                return handle;
            }

            @Override
            public InternalWorkingMemory getWorkingMemory() {
                return null;
            }
        }
        TimerJobInstance timerJobInstance = new DefaultTimerJobInstance(
            ctx -> {
                RULES_LOG.finest("On " + RulesEngine.this + ", fact expired, deleting: " + assetEvent);
                synchronized (RulesEngine.this) {
                    knowledgeSession.delete(factHandle);
                }
            },
            new AssetEventExpireJobContext(),
            new PointInTimeTrigger(knowledgeSession.getSessionClock().getCurrentTime() + expirationOffset, null, null),
            jobHandle,
            sessionScheduler
        );
        sessionScheduler.internalSchedule(timerJobInstance);

    }

    protected String compileTemplate(String templateAssetId, String rules) {
        ServerAsset templateAsset = assetStorageService.find(templateAssetId, true);

        if (templateAsset == null)
            throw new IllegalStateException("Template asset not found: " + templateAssetId);

        List<TemplateFilter> filters = templateAsset.getAttributesStream()
            .filter(isAttributeTypeEqualTo(AttributeType.RULES_TEMPLATE_FILTER))
            .map(attribute -> new Pair<>(attribute.getName(), attribute.getValue()))
            .filter(pair -> pair.key.isPresent() && pair.value.isPresent())
            .map(pair -> new Pair<>(pair.key.get(), pair.value.get()))
            .map(pair -> TemplateFilter.fromModelValue(pair.key, pair.value))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());

        LOG.fine("Rendering rules template with filters: " + filters);

        ObjectDataCompiler converter = new ObjectDataCompiler();
        InputStream is = new ByteArrayInputStream(rules.getBytes(StandardCharsets.UTF_8));
        return converter.compile(filters, is);
    }

    protected synchronized void updateRulesetsDebug() {
        rulesetsDebug = Arrays.toString(rulesets.values().stream().map(rd ->
            rd.getClass().getSimpleName()
                + " - "
                + rd.getName()
                + ": "
                + rd.getDeploymentStatus()).toArray(String[]::new)
        );
    }

    protected synchronized void printSessionStats() {
        if (knowledgeSession == null) {
            return;
        }
        Collection<?> assetStateFacts = knowledgeSession.getObjects(new ClassObjectFilter(AssetState.class));
        Collection<?> assetEventFacts = knowledgeSession.getObjects(new ClassObjectFilter(AssetEvent.class));
        Collection<?> customFacts = knowledgeSession.getObjects(object ->
            !AssetState.class.isAssignableFrom(object.getClass()) && !AssetEvent.class.isAssignableFrom(object.getClass())
        );
        long total = assetStateFacts.size() + assetEventFacts.size() + customFacts.size();
        STATS_LOG.info("On " + this + ", in memory facts are Total: " + total
            + ", AssetState: " + assetStateFacts.size()
            + ", AssetEvent: " + assetEventFacts.size()
            + ", Custom: " + customFacts.size());

        /* TODO Remove internal debug
        STATS_LOG.info("On " + this + ", managed are: "
            + "AssetState (no fact handle): " + assetStates.entrySet().stream().filter(entry -> entry.getValue() == null).count()
            + ", AssetState (with fact handle): " + assetStates.entrySet().stream().filter(entry -> entry.getValue() != null).count());
        */

        // Additional details if FINEST is enabled
        if (STATS_LOG.isLoggable(Level.FINEST)) {
            for (Object assetStateFact : assetStateFacts) {
                STATS_LOG.finest("On " + this + ", fact: " + assetStateFact);
            }
            for (Object assetEventFact : assetEventFacts) {
                STATS_LOG.finest("On " + this + ", fact: " + assetEventFact);
            }
            for (Object customFact : customFacts) {
                STATS_LOG.finest("On " + this + ", fact: " + customFact);
            }
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
            "id='" + id + '\'' +
            ", running='" + isRunning() + '\'' +
            ", error='" + error + '\'' +
            ", rulesets='" + rulesetsDebug + '\'' +
            '}';
    }
}
