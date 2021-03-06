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
package org.openremote.manager.setup;

import com.vividsolutions.jts.geom.GeometryFactory;
import org.openremote.agent.protocol.macro.MacroAction;
import org.openremote.agent.protocol.macro.MacroProtocol;
import org.openremote.agent.protocol.timer.TimerValue;
import org.openremote.container.Container;
import org.openremote.manager.asset.AssetStorageService;
import org.openremote.manager.asset.ServerAsset;
import org.openremote.manager.datapoint.AssetDatapointService;
import org.openremote.manager.persistence.ManagerPersistenceService;
import org.openremote.manager.rules.RulesetStorageService;
import org.openremote.manager.security.ManagerIdentityService;
import org.openremote.model.asset.AssetAttribute;
import org.openremote.model.attribute.*;
import org.openremote.model.value.Values;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.openremote.agent.protocol.macro.MacroProtocol.META_MACRO_ACTION_INDEX;
import static org.openremote.agent.protocol.timer.TimerConfiguration.initTimerConfiguration;
import static org.openremote.agent.protocol.timer.TimerProtocol.META_TIMER_VALUE_LINK;
import static org.openremote.model.asset.AssetMeta.*;
import static org.openremote.model.asset.AssetType.*;
import static org.openremote.model.asset.agent.ProtocolConfiguration.initProtocolConfiguration;
import static org.openremote.model.attribute.AttributeType.*;

public abstract class AbstractManagerSetup implements Setup {

    final protected ManagerPersistenceService persistenceService;
    final protected ManagerIdentityService identityService;
    final protected AssetStorageService assetStorageService;
    final protected AssetDatapointService assetDatapointService;
    final protected RulesetStorageService rulesetStorageService;
    final protected GeometryFactory geometryFactory = new GeometryFactory();
    final protected SetupService setupService;

    public AbstractManagerSetup(Container container) {
        this.persistenceService = container.getService(ManagerPersistenceService.class);
        this.identityService = container.getService(ManagerIdentityService.class);
        this.assetStorageService = container.getService(AssetStorageService.class);
        this.assetDatapointService = container.getService(AssetDatapointService.class);
        this.rulesetStorageService = container.getService(RulesetStorageService.class);
        this.setupService = container.getService(SetupService.class);
    }

    // ################################ Demo apartment with complex scenes ###################################

    protected ServerAsset createDemoApartment(ServerAsset parent, String name) {
        ServerAsset apartment = new ServerAsset(name, RESIDENCE, parent);
        apartment.setLocation(parent.getLocation());
        apartment.setAttributes(
            new AssetAttribute("alarmEnabled", AttributeType.BOOLEAN, Values.create(false))
                .setMeta(new Meta(
                    new MetaItem(LABEL, Values.create("Alarm enabled")),
                    new MetaItem(DESCRIPTION, Values.create("Send notifications when presence is detected")),
                    new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                    new MetaItem(ACCESS_RESTRICTED_WRITE, Values.create(true)),
                    new MetaItem(RULE_STATE, Values.create(true)),
                    new MetaItem(SHOW_ON_DASHBOARD, Values.create(true))
                )),
            new AssetAttribute("presenceDetected", AttributeType.BOOLEAN)
                .setMeta(new Meta(
                    new MetaItem(LABEL, Values.create("Presence detected")),
                    new MetaItem(DESCRIPTION, Values.create("Presence detected in any room")),
                    new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                    new MetaItem(RULE_STATE, Values.create(true)),
                    new MetaItem(RULE_EVENT, Values.create(true)),
                    new MetaItem(STORE_DATA_POINTS, Values.create(true)),
                    new MetaItem(SHOW_ON_DASHBOARD, Values.create(true))
                )),
            new AssetAttribute("vacationUntil", TIMESTAMP_MILLIS)
                .setMeta(new Meta(
                    new MetaItem(LABEL, Values.create("Vacation until")),
                    new MetaItem(DESCRIPTION, Values.create("Vacation mode enabled until")),
                    new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                    new MetaItem(ACCESS_RESTRICTED_WRITE, Values.create(true)),
                    new MetaItem(RULE_STATE, Values.create(true))
                )),
            new AssetAttribute("autoSceneSchedule", AttributeType.BOOLEAN, Values.create(true))
                .setMeta(
                    new MetaItem(LABEL, Values.create("Automatic scene schedule")),
                    new MetaItem(DESCRIPTION, Values.create("Predict presence and automatically adjust scene schedule")),
                    new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                    new MetaItem(ACCESS_RESTRICTED_WRITE, Values.create(true)),
                    new MetaItem(RULE_STATE, Values.create(true))
                ),
            new AssetAttribute("lastExecutedScene", AttributeType.STRING, Values.create("HOME"))
                .setMeta(
                    new MetaItem(LABEL, Values.create("Last executed scene")),
                    new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                    new MetaItem(ACCESS_RESTRICTED_WRITE, Values.create(true)),
                    new MetaItem(READ_ONLY, Values.create(true)),
                    new MetaItem(RULE_STATE, Values.create(true)),
                    new MetaItem(SHOW_ON_DASHBOARD, Values.create(true))
                ),
            new AssetAttribute("lastDetectedScene", AttributeType.STRING, Values.create("home"))
                .setMeta(
                    new MetaItem(LABEL, Values.create("Last detecteded scene by rules")),
                    new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                    new MetaItem(ACCESS_RESTRICTED_WRITE, Values.create(true)),
                    new MetaItem(READ_ONLY, Values.create(true)),
                    new MetaItem(RULE_STATE, Values.create(true)),
                    new MetaItem(SHOW_ON_DASHBOARD, Values.create(true))
                ),
            new AssetAttribute("smartStart", AttributeType.BOOLEAN, Values.create(false))
                .setMeta(
                    new MetaItem(LABEL, Values.create("Enable global smart start")),
                    new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                    new MetaItem(ACCESS_RESTRICTED_WRITE, Values.create(true)),
                    new MetaItem(RULE_STATE, Values.create(true)),
                    new MetaItem(SHOW_ON_DASHBOARD, Values.create(true))
                )
        );
        return apartment;
    }

    protected ServerAsset createDemoApartmentRoom(ServerAsset apartment, String name) {
        ServerAsset room = new ServerAsset(name, ROOM, apartment);
        room.setLocation(apartment.getLocation());
        return room;
    }

    protected void addDemoApartmentRoomMotionSensor(ServerAsset room, boolean shouldBeLinked, Supplier<MetaItem[]> agentLinker) {
        room.addAttributes(
            new AssetAttribute("motionSensor", NUMBER)
                .setMeta(
                    new MetaItem(LABEL, Values.create("Motion sensor")),
                    new MetaItem(DESCRIPTION, Values.create("Greater than zero when motion is sensed")),
                    new MetaItem(READ_ONLY, Values.create(true)),
                    new MetaItem(RULE_STATE, Values.create(true)),
                    new MetaItem(RULE_EVENT, Values.create(true)),
                    new MetaItem(STORE_DATA_POINTS, Values.create(true))
                ).addMeta(shouldBeLinked ? agentLinker.get() : null),
            new AssetAttribute("presenceDetected", AttributeType.BOOLEAN)
                .setMeta(
                    new MetaItem(LABEL, Values.create("Presence detected")),
                    new MetaItem(DESCRIPTION, Values.create("Someone is moving or resting in the room")),
                    new MetaItem(RULE_STATE, Values.create(true)),
                    new MetaItem(RULE_EVENT, Values.create(true)),
                    new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                    new MetaItem(STORE_DATA_POINTS, Values.create(true)),
                    new MetaItem(SHOW_ON_DASHBOARD, Values.create(true))
                ),
            new AssetAttribute("firstPresenceDetected", TIMESTAMP_MILLIS)
                .setMeta(
                    new MetaItem(LABEL, Values.create("First time someone was present in the room")),
                    new MetaItem(RULE_STATE, Values.create(true)),
                    new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true))
                ),
            new AssetAttribute("lastPresenceDetected", TIMESTAMP_MILLIS)
                .setMeta(
                    new MetaItem(LABEL, Values.create("Last time someone was present in the room")),
                    new MetaItem(RULE_STATE, Values.create(true)),
                    new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true))
                )
        );
    }

    protected void addDemoApartmentRoomCO2Sensor(ServerAsset room, boolean shouldBeLinked, Supplier<MetaItem[]> agentLinker) {
        room.addAttributes(
            new AssetAttribute("co2Level", CO2_PPM)
                .setMeta(
                    new MetaItem(LABEL, Values.create("CO2 level")),
                    new MetaItem(RULE_STATE, Values.create(true)),
                    new MetaItem(RULE_EVENT, Values.create(true)),
                    new MetaItem(RULE_EVENT_EXPIRES, Values.create("16m")),
                    new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                    new MetaItem(READ_ONLY, Values.create(true)),
                    new MetaItem(STORE_DATA_POINTS, Values.create(true)),
                    new MetaItem(SHOW_ON_DASHBOARD, Values.create(true)),
                    new MetaItem(FORMAT, Values.create("%4d ppm"))
                ).addMeta(shouldBeLinked ? agentLinker.get() : null)
        );
    }

    protected void addDemoApartmentRoomHumiditySensor(ServerAsset room, boolean shouldBeLinked, Supplier<MetaItem[]> agentLinker) {
        room.addAttributes(
            new AssetAttribute("humidity", HUMIDITY_PERCENTAGE)
                .setMeta(
                    new MetaItem(LABEL, Values.create("Humidity")),
                    new MetaItem(RULE_STATE, Values.create(true)),
                    new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                    new MetaItem(READ_ONLY, Values.create(true)),
                    new MetaItem(STORE_DATA_POINTS, Values.create(true)),
                    new MetaItem(SHOW_ON_DASHBOARD, Values.create(true)),
                    new MetaItem(FORMAT, Values.create("%3d %%"))
                ).addMeta(shouldBeLinked ? agentLinker.get() : null)
        );
    }

    protected void addDemoApartmentRoomThermometer(ServerAsset room,
                                                   boolean shouldBeLinked,
                                                   Supplier<MetaItem[]> agentLinker) {
        room.addAttributes(
            new AssetAttribute("currentTemperature", TEMPERATURE_CELCIUS)
                .setMeta(
                    new MetaItem(LABEL, Values.create("Current temperature")),
                    new MetaItem(RULE_STATE, Values.create(true)),
                    new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                    new MetaItem(READ_ONLY, Values.create(true)),
                    new MetaItem(STORE_DATA_POINTS, Values.create(true)),
                    new MetaItem(SHOW_ON_DASHBOARD, Values.create(true)),
                    new MetaItem(FORMAT, Values.create("%0.1f C"))
                ).addMeta(shouldBeLinked ? agentLinker.get() : null)
        );
    }

    protected void addDemoApartmentTemperatureControl(ServerAsset room,
                                                      boolean shouldBeLinked,
                                                      Supplier<MetaItem[]> agentLinker) {
        room.addAttributes(
            new AssetAttribute("targetTemperature", TEMPERATURE_CELCIUS)
                .setMeta(
                    new MetaItem(LABEL, Values.create("Target temperature")),
                    new MetaItem(RULE_STATE, Values.create(true)),
                    new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                    new MetaItem(ACCESS_RESTRICTED_WRITE, Values.create(true)),
                    new MetaItem(SHOW_ON_DASHBOARD, Values.create(true)),
                    new MetaItem(FORMAT, Values.create("%0.1f C"))
                ).addMeta(shouldBeLinked ? agentLinker.get() : null)
        );
    }

    protected void addDemoApartmentSmartSwitch(ServerAsset room,
                                               String switchAttributePrefix,
                                               String switchLabelPrefix,
                                               boolean shouldBeLinked,
                                               // Integer represents attribute number:
                                               // 0 = On/Off
                                               // 1 = Start time
                                               // 2 = Stop time
                                               // 3 = Smart enabled
                                               Function<Integer, MetaItem[]> agentLinker) {

        room.addAttributes(
            // On/Off
            new AssetAttribute(switchAttributePrefix + "OnOff", BOOLEAN)
                .setMeta(
                    new MetaItem(LABEL, Values.create(switchLabelPrefix + " on/off")),
                    new MetaItem(ACCESS_RESTRICTED_WRITE, Values.create(true)),
                    new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                    new MetaItem(RULE_STATE, Values.create(true)),
                    new MetaItem(RULE_EVENT, Values.create(true))
                ).addMeta(shouldBeLinked ? agentLinker.apply(0) : null),
            // Start time
            new AssetAttribute(switchAttributePrefix + "StartTime", TIMESTAMP_SECONDS)
                .setMeta(
                    new MetaItem(LABEL, Values.create(switchLabelPrefix + " earliest start time")),
                    new MetaItem(ACCESS_RESTRICTED_WRITE, Values.create(true)),
                    new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                    new MetaItem(RULE_STATE, Values.create(true)),
                    new MetaItem(RULE_EVENT, Values.create(true))
                ).addMeta(shouldBeLinked ? agentLinker.apply(1) : null),
            // Stop time
            new AssetAttribute(switchAttributePrefix + "StopTime", TIMESTAMP_SECONDS)
                .setMeta(
                    new MetaItem(LABEL, Values.create(switchLabelPrefix + " latest stop time")),
                    new MetaItem(ACCESS_RESTRICTED_WRITE, Values.create(true)),
                    new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                    new MetaItem(RULE_STATE, Values.create(true)),
                    new MetaItem(RULE_EVENT, Values.create(true))
                ).addMeta(shouldBeLinked ? agentLinker.apply(2) : null),
            // Smart enabled
            new AssetAttribute(switchAttributePrefix + "SmartEnabled", BOOLEAN)
                .setMeta(
                    new MetaItem(LABEL, Values.create(switchLabelPrefix + " smart control enabled")),
                    new MetaItem(ACCESS_RESTRICTED_WRITE, Values.create(true)),
                    new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                    new MetaItem(RULE_STATE, Values.create(true)),
                    new MetaItem(RULE_EVENT, Values.create(true))
                ).addMeta(shouldBeLinked ? agentLinker.apply(3) : null)
        );
    }

    protected void addDemoApartmentVentilation(ServerAsset apartment,
                                               boolean shouldBeLinked,
                                               Supplier<MetaItem[]> agentLinker) {
        apartment.addAttributes(
            new AssetAttribute("ventilationLevel", NUMBER)
                .setMeta(
                    new MetaItem(LABEL, Values.create("Ventilation level")),
                    new MetaItem(RANGE_MIN, Values.create(0)),
                    new MetaItem(RANGE_MAX, Values.create(255)),
                    new MetaItem(RULE_STATE, Values.create(true)),
                    new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                    new MetaItem(ACCESS_RESTRICTED_WRITE, Values.create(true)),
                    new MetaItem(FORMAT, Values.create("%d"))
                ).addMeta(shouldBeLinked ? agentLinker.get() : null),
            new AssetAttribute("ventilationAuto", BOOLEAN)
                .setMeta(
                    new MetaItem(LABEL, Values.create("Ventilation auto")),
                    new MetaItem(RULE_STATE, Values.create(true)),
                    new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                    new MetaItem(ACCESS_RESTRICTED_WRITE, Values.create(true))
                )
        );
    }

    public class Scene {

        final String attributeName;
        final String attributeLabel;
        final String internalName;
        final String startTime;
        final boolean alarmEnabled;
        final double targetTemperature;

        public Scene(String attributeName,
                     String attributeLabel,
                     String internalName,
                     String startTime,
                     boolean alarmEnabled,
                     double targetTemperature) {
            this.attributeName = attributeName;
            this.attributeLabel = attributeLabel;
            this.internalName = internalName;
            this.startTime = startTime;
            this.alarmEnabled = alarmEnabled;
            this.targetTemperature = targetTemperature;
        }

        AssetAttribute createMacroAttribute(ServerAsset apartment, ServerAsset... rooms) {
            AssetAttribute attribute = initProtocolConfiguration(new AssetAttribute(attributeName), MacroProtocol.PROTOCOL_NAME)
                .addMeta(new MetaItem(LABEL, Values.create(attributeLabel)));
            attribute.getMeta().add(
                new MacroAction(new AttributeState(new AttributeRef(apartment.getId(), "alarmEnabled"), Values.create(alarmEnabled))).toMetaItem()
            );
            for (ServerAsset room : rooms) {
                if (room.hasAttribute("targetTemperature")) {
                    attribute.getMeta().add(
                        new MacroAction(new AttributeState(new AttributeRef(room.getId(), "targetTemperature"), Values.create(targetTemperature))).toMetaItem()
                    );
                }
            }
            attribute.getMeta().add(
                new MacroAction(new AttributeState(new AttributeRef(apartment.getId(), "lastExecutedScene"), Values.create(internalName))).toMetaItem()
            );
            return attribute;
        }

        AssetAttribute[] createTimerAttributes(ServerAsset apartment) {
            List<AssetAttribute> attributes = new ArrayList<>();
            for (DayOfWeek dayOfWeek : DayOfWeek.values()) {
                // "MONDAY" => "Monday"
                String dayName = dayOfWeek.name().substring(0, 1) + dayOfWeek.name().substring(1).toLowerCase(Locale.ROOT);
                // "0 0 7 ? *" => "0 0 7 ? * MON *"
                String timePattern = startTime + " " + dayOfWeek.name().substring(0, 3).toUpperCase(Locale.ROOT) + " *";
                attributes.add(
                    initTimerConfiguration(new AssetAttribute(attributeName + dayName), timePattern,
                        new AttributeState(apartment.getId(), attributeName, Values.create("REQUEST_START")))
                        .addMeta(new MetaItem(LABEL, Values.create(attributeLabel + " trigger " + dayName)))
                );
            }
            return attributes.toArray(new AssetAttribute[attributes.size()]);
        }
    }

    protected ServerAsset createDemoApartmentSceneAgent(ServerAsset apartment, Scene[] scenes, ServerAsset... rooms) {
        ServerAsset agent = new ServerAsset("Scene Agent", AGENT, apartment);
        agent.setLocation(apartment.getLocation());
        for (Scene scene : scenes) {
            agent.addAttributes(scene.createMacroAttribute(apartment, rooms));
        }
        for (Scene scene : scenes) {
            agent.addAttributes(scene.createTimerAttributes(apartment));
        }

        addDemoApartmentSceneEnableDisableTimer(apartment, agent, scenes);

        return agent;
    }

    protected void addDemoApartmentSceneEnableDisableTimer(ServerAsset apartment, ServerAsset agent, Scene[] scenes) {
        AssetAttribute enableAllMacro = initProtocolConfiguration(new AssetAttribute("enableSceneTimer"), MacroProtocol.PROTOCOL_NAME)
            .addMeta(new MetaItem(LABEL, Values.create("Enable scene timer")));
        for (Scene scene : scenes) {
            for (DayOfWeek dayOfWeek : DayOfWeek.values()) {
                String sceneAttributeName = scene.attributeName + "Enabled" + dayOfWeek;
                enableAllMacro.getMeta().add(
                    new MacroAction(new AttributeState(new AttributeRef(apartment.getId(), sceneAttributeName), Values.create(true))).toMetaItem()
                );
            }
        }
        enableAllMacro.getMeta().add(
            new MacroAction(new AttributeState(new AttributeRef(apartment.getId(), "sceneTimerEnabled"), Values.create(true))).toMetaItem()
        );
        agent.addAttributes(enableAllMacro);

        AssetAttribute disableAllMacro = initProtocolConfiguration(new AssetAttribute("disableSceneTimer"), MacroProtocol.PROTOCOL_NAME)
            .addMeta(new MetaItem(LABEL, Values.create("Disable scene timer")));
        for (Scene scene : scenes) {
            for (DayOfWeek dayOfWeek : DayOfWeek.values()) {
                String sceneAttributeName = scene.attributeName + "Enabled" + dayOfWeek;
                disableAllMacro.getMeta().add(
                    new MacroAction(new AttributeState(new AttributeRef(apartment.getId(), sceneAttributeName), Values.create(false))).toMetaItem()
                );
            }
        }
        disableAllMacro.getMeta().add(
            new MacroAction(new AttributeState(new AttributeRef(apartment.getId(), "sceneTimerEnabled"), Values.create(false))).toMetaItem()
        );
        agent.addAttributes(disableAllMacro);
    }

    protected void linkDemoApartmentWithSceneAgent(ServerAsset apartment, ServerAsset agent, Scene[] scenes) {
        for (Scene scene : scenes) {
            apartment.addAttributes(
                new AssetAttribute(scene.attributeName, AttributeType.STRING, Values.create(AttributeExecuteStatus.READY.name()))
                    .setMeta(
                        new MetaItem(LABEL, Values.create(scene.attributeLabel)),
                        new MetaItem(ACCESS_RESTRICTED_WRITE, Values.create(true)),
                        new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                        new MetaItem(EXECUTABLE, Values.create(true)),
                        new MetaItem(AGENT_LINK, new AttributeRef(agent.getId(), scene.attributeName).toArrayValue())
                    ),
                new AssetAttribute(scene.attributeName + "AlarmEnabled", AttributeType.BOOLEAN)
                    .setMeta(
                        new MetaItem(LABEL, Values.create(scene.attributeLabel + " alarm enabled")),
                        new MetaItem(ACCESS_RESTRICTED_WRITE, Values.create(true)),
                        new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                        new MetaItem(META_MACRO_ACTION_INDEX, Values.create(0)),
                        new MetaItem(AGENT_LINK, new AttributeRef(agent.getId(), scene.attributeName).toArrayValue())
                    ),
                new AssetAttribute(scene.attributeName + "TargetTemperature", AttributeType.NUMBER)
                    .setMeta(
                        new MetaItem(LABEL, Values.create(scene.attributeLabel + " target temperature")),
                        new MetaItem(ACCESS_RESTRICTED_WRITE, Values.create(true)),
                        new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                        new MetaItem(META_MACRO_ACTION_INDEX, Values.create(1)),
                        new MetaItem(AGENT_LINK, new AttributeRef(agent.getId(), scene.attributeName).toArrayValue())
                    )
            );
            for (DayOfWeek dayOfWeek : DayOfWeek.values()) {
                // "MONDAY" => "Monday"
                String dayName = dayOfWeek.name().substring(0, 1) + dayOfWeek.name().substring(1).toLowerCase(Locale.ROOT);
                apartment.addAttributes(
                    new AssetAttribute(scene.attributeName + "Time" + dayOfWeek.name(), AttributeType.STRING)
                        .setMeta(
                            new MetaItem(LABEL, Values.create(scene.attributeLabel + " time " + dayName)),
                            new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                            new MetaItem(ACCESS_RESTRICTED_WRITE, Values.create(true)),
                            new MetaItem(RULE_STATE, Values.create(true)),
                            new MetaItem(META_TIMER_VALUE_LINK, Values.create(TimerValue.TIME.toString())),
                            new MetaItem(AGENT_LINK, new AttributeRef(agent.getId(), scene.attributeName + dayName).toArrayValue())
                        ),
                    new AssetAttribute(scene.attributeName + "Enabled" + dayOfWeek.name(), AttributeType.BOOLEAN)
                        .setMeta(
                            new MetaItem(LABEL, Values.create(scene.attributeLabel + " enabled " + dayName)),
                            new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                            new MetaItem(ACCESS_RESTRICTED_WRITE, Values.create(true)),
                            new MetaItem(META_TIMER_VALUE_LINK, Values.create(TimerValue.ENABLED.toString())),
                            new MetaItem(AGENT_LINK, new AttributeRef(agent.getId(), scene.attributeName + dayName).toArrayValue())
                        )
                );
            }
        }
        apartment.addAttributes(
            new AssetAttribute("sceneTimerEnabled", AttributeType.BOOLEAN, Values.create(true))
                .setMeta(
                    new MetaItem(LABEL, Values.create("Scene timer enabled")),
                    new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                    new MetaItem(ACCESS_RESTRICTED_WRITE, Values.create(true)),
                    new MetaItem(SHOW_ON_DASHBOARD, Values.create(true)),
                    new MetaItem(READ_ONLY, Values.create(true))
                ),
            new AssetAttribute("enableSceneTimer", AttributeType.STRING, Values.create(AttributeExecuteStatus.READY.name()))
                .setMeta(
                    new MetaItem(LABEL, Values.create("Enable scene timer")),
                    new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                    new MetaItem(ACCESS_RESTRICTED_WRITE, Values.create(true)),
                    new MetaItem(EXECUTABLE, Values.create(true)),
                    new MetaItem(AGENT_LINK, new AttributeRef(agent.getId(), "enableSceneTimer").toArrayValue())
                ),
            new AssetAttribute("disableSceneTimer", AttributeType.STRING, Values.create(AttributeExecuteStatus.READY.name()))
                .setMeta(
                    new MetaItem(LABEL, Values.create("Disable scene timer")),
                    new MetaItem(ACCESS_RESTRICTED_READ, Values.create(true)),
                    new MetaItem(ACCESS_RESTRICTED_WRITE, Values.create(true)),
                    new MetaItem(EXECUTABLE, Values.create(true)),
                    new MetaItem(AGENT_LINK, new AttributeRef(agent.getId(), "disableSceneTimer").toArrayValue())
                )
        );
    }
}
