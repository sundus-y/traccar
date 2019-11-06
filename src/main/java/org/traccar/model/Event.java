/*
 * Copyright 2016 - 2017 Anton Tananaev (anton@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.model;

import org.apache.commons.lang.WordUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.Context;
import org.traccar.Main;
import org.traccar.database.QueryIgnore;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.traccar.helper.UnitsConverter.kphFromKnots;

public class Event extends Message {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public Event(String type, long deviceId, long positionId) {
        this(type, deviceId);
        setPositionId(positionId);
    }

    public Event(String type, long deviceId) {
        setType(type);
        setDeviceId(deviceId);
        this.serverTime = new Date();
    }

    public Event() {
    }

    public static final String ALL_EVENTS = "allEvents";

    public static final String TYPE_COMMAND_RESULT = "commandResult";

    public static final String TYPE_DEVICE_ONLINE = "deviceOnline";
    public static final String TYPE_DEVICE_UNKNOWN = "deviceUnknown";
    public static final String TYPE_DEVICE_OFFLINE = "deviceOffline";

    public static final String TYPE_DEVICE_MOVING = "deviceMoving";
    public static final String TYPE_DEVICE_STOPPED = "deviceStopped";

    public static final String TYPE_DEVICE_OVERSPEED = "deviceOverspeed";
    public static final String TYPE_DEVICE_FUEL_DROP = "deviceFuelDrop";

    public static final String TYPE_GEOFENCE_ENTER = "geofenceEnter";
    public static final String TYPE_GEOFENCE_EXIT = "geofenceExit";

    public static final String TYPE_ALARM = "alarm";

    public static final String TYPE_IGNITION_ON = "ignitionOn";
    public static final String TYPE_IGNITION_OFF = "ignitionOff";

    public static final String TYPE_MAINTENANCE = "maintenance";

    public static final String TYPE_TEXT_MESSAGE = "textMessage";

    public static final String TYPE_DRIVER_CHANGED = "driverChanged";

    private Date serverTime;

    public Date getServerTime() {
        return serverTime;
    }

    public void setServerTime(Date serverTime) {
        this.serverTime = serverTime;
    }

    private long positionId;

    public long getPositionId() {
        return positionId;
    }

    public void setPositionId(long positionId) {
        this.positionId = positionId;
    }

    private long geofenceId = 0;

    public long getGeofenceId() {
        return geofenceId;
    }

    public void setGeofenceId(long geofenceId) {
        this.geofenceId = geofenceId;
    }

    private long maintenanceId = 0;

    public long getMaintenanceId() {
        return maintenanceId;
    }

    public void setMaintenanceId(long maintenanceId) {
        this.maintenanceId = maintenanceId;
    }

    @QueryIgnore
    public String getTypeForExcel() {
        String text = Arrays.stream(WordUtils.capitalize(getType())
                .split("(?=\\p{Upper})"))
                .collect(Collectors.joining(" "));
        if (getType().equalsIgnoreCase("commandResult")) {
            text = " Command result: " + getString("result");
        } else if (getType().equalsIgnoreCase("alarm")) {
            String alarmKey = getString("alarm");
            alarmKey = "alarm"
                    + Character.toUpperCase(alarmKey.charAt(0))
                    + alarmKey.substring(1, alarmKey.length());
            text += " - " + alarmMap.get(alarmKey);
        } else if (getType().equalsIgnoreCase("deviceOverspeed")) {
            Double speed = Math.ceil(kphFromKnots(getDouble("speed")));
            Double limit = Math.ceil(kphFromKnots(getDouble("speedLimit")));
            text += " - Speed: " + speed + "km/h";
            text += " (Speed Limit: " + limit + "km/h)";
        }
        return text;
    }

    public boolean isDuplicateSpeeding() {
        try {
            Collection<Event> recentEvents = Context.getDataManager()
                    .getRecentSpeedingEvents(this.getId(), this.getDeviceId(), this.getServerTime());
            return !recentEvents.isEmpty();
        } catch (SQLException e) {
            LOGGER.error("Error Finding Duplicate Speeding Events", e);
            return true;
        }
    }

    private boolean smsNotificationSent;

    public boolean getSmsNotificationSent() {
        return this.smsNotificationSent;
    }

    public void setSmsNotificationSent(boolean smsNotificationSent) {
        this.smsNotificationSent = smsNotificationSent;
    }

    private Map<String, String> alarmMap = new LinkedHashMap<String, String>() {{
        put("alarmGeneral", "General");
        put("alarmSos", "SOS");
        put("alarmVibration", "Vibration");
        put("alarmMovement", "Movement");
        put("alarmLowspeed", "Low Speed");
        put("alarmOverspeed", "Overspeed");
        put("alarmFallDown", "Fall Down");
        put("alarmLowPower", "Low Power");
        put("alarmLowBattery", "Power Cut (L)");
        put("alarmFault", "Fault");
        put("alarmPowerOff", "Power Disconnected");
        put("alarmPowerOn", "Power Connected");
        put("alarmDoor", "Door");
        put("alarmLock", "Lock");
        put("alarmUnlock", "Unlock");
        put("alarmGeofence", "Geofence");
        put("alarmGeofenceEnter", "Geofence Enter");
        put("alarmGeofenceExit", "Geofence Exit");
        put("alarmGpsAntennaCut", "GPS Antenna Cut");
        put("alarmAccident", "Accident");
        put("alarmTow", "Tow");
        put("alarmIdle", "Idle");
        put("alarmHighRpm", "High RPM");
        put("alarmHardAcceleration", "Hard Acceleration");
        put("alarmHardBraking", "Hard Braking");
        put("alarmHardCornering", "Hard Cornering");
        put("alarmLaneChange", "Lane Change");
        put("alarmFatigueDriving", "Fatigue Driving");
        put("alarmPowerCut", "Power Cut");
        put("alarmPowerDisconnected", "Power Cut (D)");
        put("alarmPowerRestored", "Power Restored");
        put("alarmJamming", "Jamming");
        put("alarmTemperature", "Temperature");
        put("alarmParking", "Parking");
        put("alarmShock", "Shock");
        put("alarmBonnet", "Bonnet");
        put("alarmFootBrake", "Foot Brake");
        put("alarmFuelLeak", "Fuel Leak");
        put("alarmTampering", "Tampering");
        put("alarmRemoving", "Removing");
    }};

}
