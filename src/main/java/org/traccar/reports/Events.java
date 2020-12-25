/*
 * Copyright 2016 - 2018 Anton Tananaev (anton@traccar.org)
 * Copyright 2016 - 2018 Andrey Kunitsyn (andrey@traccar.org)
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
package org.traccar.reports;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

import org.apache.poi.ss.util.WorkbookUtil;
import org.traccar.Context;
import org.traccar.config.Keys;
import org.traccar.model.Device;
import org.traccar.model.Event;
import org.traccar.model.Geofence;
import org.traccar.model.Group;
import org.traccar.model.Maintenance;
import org.traccar.reports.model.DeviceReport;

public final class Events {

    private Events() {
    }

    public static Collection<Event> getObjects(long userId, Collection<Long> deviceIds, Collection<Long> groupIds,
            Collection<String> types, Collection<String> subTypes, HashMap<String, Date> fromTo) throws SQLException {
        ReportUtils.checkPeriodLimit(fromTo.get("from"), fromTo.get("to"));
        ArrayList<Event> result = new ArrayList<>();
        Collection<Event> events;
        if (types.isEmpty() || types.contains(Event.ALL_EVENTS)) {
             events = Context.getDataManager().getEventsForMultiple(
                     ReportUtils.getDeviceList(deviceIds, groupIds),
                     new ArrayList<String>(), new ArrayList<String>(), fromTo.get("from"), fromTo.get("to"));
        } else {
            events = Context.getDataManager().getEventsForMultiple(
                    ReportUtils.getDeviceList(deviceIds, groupIds), types,
                    subTypes, fromTo.get("from"), fromTo.get("to"));
        }

        for (Event event : events) {
            long geofenceId = event.getGeofenceId();
            long maintenanceId = event.getMaintenanceId();
            if ((geofenceId == 0 || Context.getGeofenceManager().checkItemPermission(userId, geofenceId))
                    && (maintenanceId == 0
                    || Context.getMaintenancesManager().checkItemPermission(userId, maintenanceId))) {
                checkEventTimeout(result, event);
            }
        }
//        for (long deviceId: ReportUtils.getDeviceList(deviceIds, groupIds)) {
//            Context.getPermissionsManager().checkDevice(userId, deviceId);
//            Collection<Event> events = Context.getDataManager().getEvents(deviceId, from, to);
//            boolean all = types.isEmpty() || types.contains(Event.ALL_EVENTS);
//            for (Event event : events) {
//                if (all || types.contains(event.getType())) {
//                    long geofenceId = event.getGeofenceId();
//                    long maintenanceId = event.getMaintenanceId();
//                    if ((geofenceId == 0 || Context.getGeofenceManager().checkItemPermission(userId, geofenceId))
//                            && (maintenanceId == 0
//                            || Context.getMaintenancesManager().checkItemPermission(userId, maintenanceId))) {
//                        checkEventTimeout(result, event);
//                    }
//                }
//            }
//        }
        return result;
    }

    public static void getExcel(OutputStream outputStream,
            long userId, Collection<Long> deviceIds, Collection<Long> groupIds,
            Collection<String> types, Collection<String> subTypes, HashMap<String, Date> fromTo)
            throws SQLException, IOException {
        ReportUtils.checkPeriodLimit(fromTo.get("from"), fromTo.get("to"));
        ArrayList<DeviceReport> devicesEvents = new ArrayList<>();
        ArrayList<String> sheetNames = new ArrayList<>();
        HashMap<Long, String> geofenceNames = new HashMap<>();
        HashMap<Long, String> maintenanceNames = new HashMap<>();
        for (long deviceId: ReportUtils.getDeviceList(deviceIds, groupIds)) {
            Context.getPermissionsManager().checkDevice(userId, deviceId);
            Collection<Event> events = Context.getDataManager().getEvents(deviceId,
                    fromTo.get("from"), fromTo.get("to"));
            boolean all = types.isEmpty() || types.contains(Event.ALL_EVENTS);
            for (Iterator<Event> iterator = events.iterator(); iterator.hasNext();) {
                Event event = iterator.next();
                if (all || types.contains(event.getType())) {
                    long geofenceId = event.getGeofenceId();
                    long maintenanceId = event.getMaintenanceId();
                    if (geofenceId != 0) {
                        if (Context.getGeofenceManager().checkItemPermission(userId, geofenceId)) {
                            Geofence geofence = Context.getGeofenceManager().getById(geofenceId);
                            if (geofence != null) {
                                geofenceNames.put(geofenceId, geofence.getName());
                            }
                        } else {
                            iterator.remove();
                        }
                    } else if (maintenanceId != 0) {
                        if (Context.getMaintenancesManager().checkItemPermission(userId, maintenanceId)) {
                            Maintenance maintenance = Context.getMaintenancesManager().getById(maintenanceId);
                            if (maintenance != null) {
                                maintenanceNames.put(maintenanceId, maintenance.getName());
                            }
                        } else {
                            iterator.remove();
                        }
                    }
                } else {
                    iterator.remove();
                }
            }
            ArrayList<Event> result = new ArrayList<>();
            for (Event event : events) {
                checkEventTimeout(result, event);
            }
            if (result.size() > 0) {
                DeviceReport deviceEvents = new DeviceReport();
                Device device = Context.getIdentityManager().getById(deviceId);
                deviceEvents.setDevice(device);
                deviceEvents.setDeviceName(device.getName());
                sheetNames.add(WorkbookUtil.createSafeSheetName(deviceEvents.getDeviceName()));
                if (device.getGroupId() != 0) {
                    Group group = Context.getGroupsManager().getById(device.getGroupId());
                    if (group != null) {
                        deviceEvents.setGroupName(group.getName());
                    }
                }
                deviceEvents.setObjects(result);
                devicesEvents.add(deviceEvents);
            }
        }
        if (devicesEvents.size() == 0) {
            throw new IllegalArgumentException("No events for the given devices and time period.");
        }
        String templatePath = Context.getConfig().getString("report.templatesPath",
                "templates/export/");
        try (InputStream inputStream = new FileInputStream(templatePath + "/events.xlsx")) {
            org.jxls.common.Context jxlsContext = ReportUtils.initializeContext(userId);
            jxlsContext.putVar("devices", devicesEvents);
            jxlsContext.putVar("sheetNames", sheetNames);
            jxlsContext.putVar("geofenceNames", geofenceNames);
            jxlsContext.putVar("maintenanceNames", maintenanceNames);
            jxlsContext.putVar("from", fromTo.get("from"));
            jxlsContext.putVar("to", fromTo.get("to"));
            ReportUtils.processTemplateWithSheets(inputStream, outputStream, jxlsContext);
        }
    }

    private static void checkEventTimeout(ArrayList<Event> result, Event event) {
        boolean dontIgnore = true;
        if (event.getType().equalsIgnoreCase(Event.TYPE_DEVICE_ONLINE)) {
            for (int i = result.size() - 1; i >= Math.max(result.size() - 5, 0); i--) {
                Event ev = result.get(i);
                long diffInSeconds = Math.abs(ev.getServerTime().getTime() - event.getServerTime().getTime()) / 1000;
                if ((ev.getType().equalsIgnoreCase(Event.TYPE_DEVICE_OFFLINE)
                        || ev.getType().equalsIgnoreCase(Event.TYPE_DEVICE_UNKNOWN))
                        && (diffInSeconds < Context.getConfig().getInteger(Keys.CHECK_EVENT_TIMEOUT_VALUE))) {
                    result.remove(ev);
                    dontIgnore = false;
                }
            }
        }
        if (dontIgnore) {
            result.add(event);
        }
    }
}
